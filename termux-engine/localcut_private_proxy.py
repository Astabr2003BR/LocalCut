#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ipaddress
from pathlib import Path
import re
import select
import socket
import socketserver
import subprocess
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


def _can_bind(host: str) -> bool:
    if not _is_rfc1918(host):
        return False
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind((host, 0))
        return True
    except OSError:
        return False
    finally:
        sock.close()


def _dedupe(values):
    seen = set()
    result = []
    for value in values:
        value = value.strip()
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def _cached_candidate(state_file: Path | None) -> list[str]:
    if state_file is None:
        return []
    try:
        value = state_file.read_text(encoding="utf-8").strip()
    except OSError:
        return []
    return [value] if value else []


def _route_candidates() -> list[str]:
    candidates: list[str] = []
    for host in ("1.1.1.1", "8.8.8.8", "192.168.1.1"):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.connect((host, 53))
            candidates.append(sock.getsockname()[0])
        except OSError:
            pass
        finally:
            sock.close()
    return candidates


def _hostname_candidates() -> list[str]:
    candidates: list[str] = []
    try:
        candidates.extend(socket.gethostbyname_ex(socket.gethostname())[2])
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            candidates.append(info[4][0])
    except OSError:
        pass
    return candidates


def _command_candidates() -> list[str]:
    candidates: list[str] = []
    commands = (
        ("ip", "-o", "-4", "addr", "show"),
        ("hostname", "-I"),
    )
    for argv in commands:
        try:
            completed = subprocess.run(
                argv,
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=2,
            )
        except (OSError, subprocess.SubprocessError):
            continue
        candidates.extend(re.findall(r"(?<![0-9])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9])", completed.stdout))
    return candidates


def _proc_candidates() -> list[str]:
    path = Path("/proc/net/fib_trie")
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return []
    return re.findall(r"(?<![0-9])(?:10(?:\.[0-9]{1,3}){3}|192\.168(?:\.[0-9]{1,3}){2}|172\.(?:1[6-9]|2[0-9]|3[01])(?:\.[0-9]{1,3}){2})(?![0-9])", text)


def _persist_private_ipv4(state_file: Path | None, host: str) -> None:
    if state_file is None:
        return
    state_file.parent.mkdir(parents=True, exist_ok=True)
    temp = state_file.with_suffix(state_file.suffix + ".tmp")
    temp.write_text(host + "\n", encoding="utf-8")
    temp.chmod(0o600)
    temp.replace(state_file)


def discover_private_ipv4(state_file: Path | None = None) -> str:
    candidates = _dedupe(
        _cached_candidate(state_file)
        + _route_candidates()
        + _hostname_candidates()
        + _command_candidates()
        + _proc_candidates()
    )
    for candidate in candidates:
        if _can_bind(candidate):
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
    parser.add_argument("--state-file", default=None)
    parser.add_argument("--media-port", type=int, default=DEFAULT_MEDIA_PROXY_PORT)
    parser.add_argument("--agent-port", type=int, default=DEFAULT_AGENT_PROXY_PORT)
    args = parser.parse_args()
    state_file = Path(args.state_file).expanduser() if args.state_file else None
    bind_host = args.bind_host or discover_private_ipv4(state_file)
    media, agent = create_proxy_servers(bind_host, args.media_port, args.agent_port)
    _persist_private_ipv4(state_file, bind_host)
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
