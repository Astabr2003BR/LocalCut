#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ipaddress
import select
import socket
import socketserver
import threading
import time

DEFAULT_MEDIA_PROXY_PORT = 43227
DEFAULT_AGENT_PROXY_PORT = 43228
DEFAULT_MEDIA_TARGET_PORT = 43127
DEFAULT_AGENT_TARGET_PORT = 43128
LOOPBACK_HOST = "127.0.0.1"
MAX_CONNECTIONS = 16
IDLE_TIMEOUT_SECONDS = 930
BUFFER_SIZE = 64 * 1024


def _is_rfc1918(host: str) -> bool:
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return False
    if address.version != 4:
        return False
    return any(address in network for network in (
        ipaddress.ip_network("10.0.0.0/8"),
        ipaddress.ip_network("172.16.0.0/12"),
        ipaddress.ip_network("192.168.0.0/16"),
    ))


def discover_private_ipv4() -> str:
    candidates: list[str] = []
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 53))
        candidates.append(sock.getsockname()[0])
    except OSError:
        pass
    finally:
        sock.close()

    try:
        candidates.extend(socket.gethostbyname_ex(socket.gethostname())[2])
    except OSError:
        pass

    for candidate in candidates:
        if _is_rfc1918(candidate):
            return candidate
    raise RuntimeError("PRIVATE_IPV4_NOT_FOUND")


class _PrivateRelayServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, bind_address, target_address):
        host, port = bind_address
        if not _is_rfc1918(host):
            raise ValueError("PROXY_BIND_PRIVATE_IPV4_REQUIRED")
        if not (1 <= int(port) <= 65535):
            raise ValueError("PROXY_PORT_INVALID")
        target_host, target_port = target_address
        if target_host != LOOPBACK_HOST:
            raise ValueError("PROXY_TARGET_LOOPBACK_ONLY")
        if not (1 <= int(target_port) <= 65535):
            raise ValueError("PROXY_TARGET_PORT_INVALID")
        self.target_address = (target_host, int(target_port))
        self.connection_slots = threading.BoundedSemaphore(MAX_CONNECTIONS)
        super().__init__((host, int(port)), _PrivateRelayHandler, bind_and_activate=True)


class _PrivateRelayHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        if not self.server.connection_slots.acquire(blocking=False):
            return
        try:
            self._relay()
        finally:
            self.server.connection_slots.release()

    def _relay(self) -> None:
        upstream = socket.create_connection(self.server.target_address, timeout=5)
        try:
            client = self.request
            client.setblocking(False)
            upstream.setblocking(False)
            peers = {client: upstream, upstream: client}
            active = {client, upstream}
            last_activity = time.monotonic()
            while active:
                timeout = max(0.0, IDLE_TIMEOUT_SECONDS - (time.monotonic() - last_activity))
                if timeout == 0.0:
                    break
                readable, _, exceptional = select.select(list(active), [], list(active), timeout)
                if exceptional:
                    break
                if not readable:
                    break
                for source in readable:
                    try:
                        data = source.recv(BUFFER_SIZE)
                    except (BlockingIOError, InterruptedError):
                        continue
                    if not data:
                        active.discard(source)
                        target = peers[source]
                        try:
                            target.shutdown(socket.SHUT_WR)
                        except OSError:
                            pass
                        continue
                    last_activity = time.monotonic()
                    target = peers[source]
                    view = memoryview(data)
                    while view:
                        try:
                            sent = target.send(view)
                            view = view[sent:]
                        except (BlockingIOError, InterruptedError):
                            select.select([], [target], [], 1.0)
        finally:
            upstream.close()


def create_proxy_servers(bind_host: str, media_proxy_port: int, agent_proxy_port: int,
                         media_target_port: int = DEFAULT_MEDIA_TARGET_PORT,
                         agent_target_port: int = DEFAULT_AGENT_TARGET_PORT):
    media = _PrivateRelayServer((bind_host, media_proxy_port), (LOOPBACK_HOST, media_target_port))
    try:
        agent = _PrivateRelayServer((bind_host, agent_proxy_port), (LOOPBACK_HOST, agent_target_port))
    except Exception:
        media.server_close()
        raise
    return media, agent


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut UserLAnd private-network relay")
    parser.add_argument("--bind-host", default=None)
    parser.add_argument("--media-port", type=int, default=DEFAULT_MEDIA_PROXY_PORT)
    parser.add_argument("--agent-port", type=int, default=DEFAULT_AGENT_PROXY_PORT)
    args = parser.parse_args()
    bind_host = args.bind_host or discover_private_ipv4()
    media, agent = create_proxy_servers(bind_host, args.media_port, args.agent_port)
    agent_thread = threading.Thread(target=agent.serve_forever, daemon=True, name="localcut-agent-private-proxy")
    agent_thread.start()
    print(f"LOCALCUT_PRIVATE_PROXY_READY={bind_host}:{args.media_port},agent:{args.agent_port}", flush=True)
    try:
        media.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        media.shutdown()
        agent.shutdown()
        media.server_close()
        agent.server_close()
        agent_thread.join(timeout=2)


if __name__ == "__main__":
    main()
