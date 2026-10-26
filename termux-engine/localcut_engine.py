#!/usr/bin/env python3
import argparse
import hmac
import json
import os
import secrets
import socketserver

PROTOCOL_VERSION = 1
ENGINE_VERSION = "0.1.0"
PROJECT_ID = "localcut"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 43127
CAPABILITIES = ("PING", "GET_STATUS", "GET_CAPABILITIES")
TOKEN_MIN_LENGTH = 32
TOKEN_MAX_LENGTH = 256
MAX_REQUEST_BYTES = 4096


def _validate_token(token: str) -> str:
    token = token.strip()
    if not (TOKEN_MIN_LENGTH <= len(token) <= TOKEN_MAX_LENGTH):
        raise ValueError("ENGINE_TOKEN_INVALID")
    if not all(ch.isalnum() or ch in "-._~" for ch in token):
        raise ValueError("ENGINE_TOKEN_INVALID")
    return token


def generate_token() -> str:
    return secrets.token_urlsafe(32)


def _response(ok: bool, request_id: str | None, **fields) -> bytes:
    payload = {"ok": ok, "protocolVersion": PROTOCOL_VERSION}
    if request_id:
        payload["requestId"] = request_id
    payload.update(fields)
    return (json.dumps(payload, separators=(",", ":")) + "\n").encode("utf-8")


class LocalCutTermuxEngineServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, auth_token: str):
        self.auth_token = _validate_token(auth_token)
        super().__init__(address, handler, bind_and_activate=True)


class LocalCutTermuxEngineHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        raw = self.rfile.readline(MAX_REQUEST_BYTES + 1)
        if not raw or len(raw) > MAX_REQUEST_BYTES or not raw.endswith(b"\n"):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return
        try:
            request = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return
        if not isinstance(request, dict):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return

        request_id = request.get("requestId")
        if not isinstance(request_id, str) or not (8 <= len(request_id) <= 128):
            self.wfile.write(_response(False, None, code="REQUEST_ID_INVALID"))
            return

        supplied_token = request.get("token")
        if not isinstance(supplied_token, str) or not hmac.compare_digest(supplied_token, self.server.auth_token):
            self.wfile.write(_response(False, request_id, code="AUTH_INVALID"))
            return
        if request.get("protocolVersion") != PROTOCOL_VERSION:
            self.wfile.write(_response(False, request_id, code="PROTOCOL_MISMATCH"))
            return
        if request.get("projectId") != PROJECT_ID:
            self.wfile.write(_response(False, request_id, code="PROJECT_MISMATCH"))
            return

        operation = request.get("operation")
        if operation == "PING":
            self.wfile.write(_response(True, request_id, status="READY"))
        elif operation == "GET_STATUS":
            self.wfile.write(_response(
                True,
                request_id,
                engineVersion=ENGINE_VERSION,
                projectId=PROJECT_ID,
                status="READY",
                pid=os.getpid(),
            ))
        elif operation == "GET_CAPABILITIES":
            self.wfile.write(_response(True, request_id, capabilities=list(CAPABILITIES)))
        else:
            self.wfile.write(_response(False, request_id, code="OPERATION_NOT_ALLOWED"))


def create_server(host: str, port: int, token: str) -> LocalCutTermuxEngineServer:
    if host != DEFAULT_HOST:
        raise ValueError("ENGINE_BIND_LOOPBACK_ONLY")
    if not (1 <= int(port) <= 65535):
        raise ValueError("ENGINE_PORT_INVALID")
    validated_token = _validate_token(token)
    return LocalCutTermuxEngineServer((host, int(port)), LocalCutTermuxEngineHandler, validated_token)


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut Termux Agent Engine V1")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--generate-token", action="store_true")
    args = parser.parse_args()
    if args.generate_token:
        print(generate_token())
        return
    token = os.environ.get("LOCALCUT_ENGINE_TOKEN", "")
    server = create_server(args.host, args.port, token)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
