#!/usr/bin/env python3
import argparse
import hmac
import json
import os
from pathlib import Path
import secrets
import socketserver
import threading
import time

PROTOCOL_VERSION = 1
ENGINE_VERSION = "0.2.0"
PROJECT_ID = "localcut"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 43127
CAPABILITIES = ("PING", "GET_STATUS", "GET_CAPABILITIES")
TOKEN_MIN_LENGTH = 32
TOKEN_MAX_LENGTH = 256
MAX_REQUEST_BYTES = 4096
PAIRING_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
PAIRING_CODE_LENGTH = 12
PAIRING_TTL_SECONDS = 300
PAIRING_MAX_ATTEMPTS = 5
DEFAULT_CREDENTIALS_FILE = Path.home() / ".localcut-engine" / "credentials.json"


def _validate_token(token: str) -> str:
    token = token.strip()
    if not (TOKEN_MIN_LENGTH <= len(token) <= TOKEN_MAX_LENGTH):
        raise ValueError("ENGINE_TOKEN_INVALID")
    if not all(ch.isalnum() or ch in "-._~" for ch in token):
        raise ValueError("ENGINE_TOKEN_INVALID")
    return token


def generate_token() -> str:
    return secrets.token_urlsafe(32)


def generate_pairing_code() -> str:
    return "".join(secrets.choice(PAIRING_CODE_ALPHABET) for _ in range(PAIRING_CODE_LENGTH))


def _normalize_pairing_code(raw: str) -> str:
    if not isinstance(raw, str):
        return ""
    return raw.replace("-", "").replace(" ", "").strip().upper()


def format_pairing_code(code: str) -> str:
    normalized = _normalize_pairing_code(code)
    return "-".join(normalized[index:index + 4] for index in range(0, len(normalized), 4))


class PairingWindow:
    def __init__(
        self,
        code: str,
        ttl_seconds: int = PAIRING_TTL_SECONDS,
        max_attempts: int = PAIRING_MAX_ATTEMPTS,
        clock=time.monotonic,
    ):
        normalized = _normalize_pairing_code(code)
        if len(normalized) != PAIRING_CODE_LENGTH or any(ch not in PAIRING_CODE_ALPHABET for ch in normalized):
            raise ValueError("PAIRING_CODE_INVALID")
        if not (1 <= int(ttl_seconds) <= 600):
            raise ValueError("PAIRING_TTL_INVALID")
        if not (1 <= int(max_attempts) <= 20):
            raise ValueError("PAIRING_ATTEMPTS_INVALID")
        self._code = normalized
        self._clock = clock
        self._expires_at = clock() + int(ttl_seconds)
        self._remaining_attempts = int(max_attempts)
        self._active = True
        self._lock = threading.Lock()

    def claim(self, supplied_code: str) -> str:
        with self._lock:
            if not self._active:
                return "PAIRING_NOT_AVAILABLE"
            if self._clock() >= self._expires_at:
                self._active = False
                return "PAIRING_EXPIRED"
            supplied = _normalize_pairing_code(supplied_code)
            if hmac.compare_digest(supplied, self._code):
                self._active = False
                return "PAIRING_OK"
            self._remaining_attempts -= 1
            if self._remaining_attempts <= 0:
                self._active = False
                return "PAIRING_LOCKED"
            return "PAIRING_INVALID"


class EngineCredentialStore:
    def __init__(self, path: str | Path = DEFAULT_CREDENTIALS_FILE):
        self.path = Path(path).expanduser()

    def load(self) -> str | None:
        if not self.path.exists():
            return None
        if self.path.is_symlink() or not self.path.is_file():
            raise ValueError("ENGINE_CREDENTIAL_FILE_INVALID")
        raw = json.loads(self.path.read_text(encoding="utf-8"))
        if not isinstance(raw, dict) or raw.get("version") != 1:
            raise ValueError("ENGINE_CREDENTIAL_FILE_INVALID")
        token = _validate_token(raw.get("token", ""))
        os.chmod(self.path, 0o600)
        return token

    def load_or_create(self) -> str:
        existing = self.load()
        if existing is not None:
            return existing
        token = generate_token()
        self._persist(token)
        return token

    def _persist(self, token: str) -> None:
        token = _validate_token(token)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path.parent, 0o700)
        temp_path = self.path.with_name(f".{self.path.name}.{secrets.token_hex(8)}.tmp")
        payload = json.dumps({"version": 1, "token": token}, separators=(",", ":")).encode("utf-8")
        fd = None
        try:
            fd = os.open(temp_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb", closefd=True) as handle:
                fd = None
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp_path, self.path)
            os.chmod(self.path, 0o600)
        finally:
            if fd is not None:
                os.close(fd)
            try:
                temp_path.unlink()
            except FileNotFoundError:
                pass


def _response(ok: bool, request_id: str | None, **fields) -> bytes:
    payload = {"ok": ok, "protocolVersion": PROTOCOL_VERSION}
    if request_id:
        payload["requestId"] = request_id
    payload.update(fields)
    return (json.dumps(payload, separators=(",", ":")) + "\n").encode("utf-8")


class LocalCutTermuxEngineServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, auth_token: str, pairing_window: PairingWindow | None = None):
        self.auth_token = _validate_token(auth_token)
        self.pairing_window = pairing_window
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
        if request.get("protocolVersion") != PROTOCOL_VERSION:
            self.wfile.write(_response(False, request_id, code="PROTOCOL_MISMATCH"))
            return
        if request.get("projectId") != PROJECT_ID:
            self.wfile.write(_response(False, request_id, code="PROJECT_MISMATCH"))
            return

        operation = request.get("operation")
        if operation == "PAIR":
            pairing_window = self.server.pairing_window
            if pairing_window is None:
                self.wfile.write(_response(False, request_id, code="PAIRING_NOT_AVAILABLE"))
                return
            result = pairing_window.claim(request.get("pairingCode", ""))
            if result != "PAIRING_OK":
                self.wfile.write(_response(False, request_id, code=result))
                return
            self.wfile.write(_response(True, request_id, token=self.server.auth_token))
            return

        supplied_token = request.get("token")
        if not isinstance(supplied_token, str) or not hmac.compare_digest(supplied_token, self.server.auth_token):
            self.wfile.write(_response(False, request_id, code="AUTH_INVALID"))
            return

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


def create_server(
    host: str,
    port: int,
    token: str,
    pairing_window: PairingWindow | None = None,
) -> LocalCutTermuxEngineServer:
    if host != DEFAULT_HOST:
        raise ValueError("ENGINE_BIND_LOOPBACK_ONLY")
    if not (1 <= int(port) <= 65535):
        raise ValueError("ENGINE_PORT_INVALID")
    validated_token = _validate_token(token)
    return LocalCutTermuxEngineServer(
        (host, int(port)),
        LocalCutTermuxEngineHandler,
        validated_token,
        pairing_window,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut Termux Agent Engine V1")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--pair", action="store_true", help="Open one short-lived, one-time pairing window")
    parser.add_argument("--credentials-file", default=str(DEFAULT_CREDENTIALS_FILE))
    args = parser.parse_args()

    env_token = os.environ.get("LOCALCUT_ENGINE_TOKEN", "").strip()
    store = EngineCredentialStore(args.credentials_file)
    if env_token:
        token = _validate_token(env_token)
    elif args.pair:
        token = store.load_or_create()
    else:
        token = store.load()
        if token is None:
            raise SystemExit("ENGINE_NOT_INITIALIZED: start once with --pair")

    pairing_window = None
    if args.pair:
        pairing_code = generate_pairing_code()
        pairing_window = PairingWindow(pairing_code)
        print(f"LOCALCUT_PAIRING_CODE={format_pairing_code(pairing_code)}", flush=True)

    server = create_server(args.host, args.port, token, pairing_window=pairing_window)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
