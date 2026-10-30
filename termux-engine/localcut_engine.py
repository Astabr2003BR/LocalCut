#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import socketserver
import threading
import time

from localcut_jobs import FfprobeMediaAnalyzer, JobStore, MediaSpool

PROTOCOL_VERSION = 1
ENGINE_VERSION = "0.4.0"
PROJECT_ID = "localcut"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 43127
CAPABILITIES = ("PING", "GET_STATUS", "GET_CAPABILITIES")
TOKEN_MIN_LENGTH = 32
TOKEN_MAX_LENGTH = 256
MAX_REQUEST_BYTES = 16_384
PAIRING_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
PAIRING_CODE_LENGTH = 12
PAIRING_TTL_SECONDS = 300
PAIRING_MAX_ATTEMPTS = 5
DEFAULT_ENGINE_DIR = Path.home() / ".localcut-engine"
DEFAULT_CREDENTIALS_FILE = DEFAULT_ENGINE_DIR / "credentials.json"
DEFAULT_JOBS_FILE = DEFAULT_ENGINE_DIR / "jobs.sqlite3"
DEFAULT_SPOOL_DIR = DEFAULT_ENGINE_DIR / "spool"
ERROR_CODE_RE = re.compile(r"^[A-Z0-9_]{3,96}$")


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


def _error_code(error: BaseException, default: str = "TERMUX_ENGINE_FAILED") -> str:
    message = str(error)
    return message if ERROR_CODE_RE.fullmatch(message) else default


def _safe_int(value, code: str, minimum: int = 0, maximum: int | None = None) -> int:
    if isinstance(value, bool):
        raise ValueError(code)
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(code) from exc
    if result < minimum or (maximum is not None and result > maximum):
        raise ValueError(code)
    return result


def _validate_descriptor(input_data: dict) -> dict:
    if not isinstance(input_data, dict):
        raise ValueError("JOB_INPUT_INVALID")
    asset_id = input_data.get("assetId")
    mime_type = input_data.get("mimeType")
    if not isinstance(asset_id, str) or not (1 <= len(asset_id) <= 160):
        raise ValueError("ASSET_ID_INVALID")
    if not isinstance(mime_type, str) or not (1 <= len(mime_type) <= 160):
        raise ValueError("MEDIA_TYPE_INVALID")
    display_name = input_data.get("displayName")
    if display_name is not None and (not isinstance(display_name, str) or len(display_name) > 512):
        raise ValueError("MEDIA_DISPLAY_NAME_INVALID")
    size_bytes = input_data.get("sizeBytes")
    if size_bytes is not None:
        size_bytes = _safe_int(size_bytes, "MEDIA_SIZE_INVALID", 0)
    duration_us = input_data.get("sourceDurationUs")
    if duration_us is not None:
        duration_us = _safe_int(duration_us, "MEDIA_DURATION_INVALID", 0)
    return {
        "assetId": asset_id,
        "displayName": display_name,
        "mimeType": mime_type,
        "sizeBytes": size_bytes,
        "sourceDurationUs": duration_us,
    }


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
    return (json.dumps(payload, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")


class LocalCutTermuxEngineServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        address,
        handler,
        auth_token: str,
        pairing_window: PairingWindow | None = None,
        job_store: JobStore | None = None,
        media_spool: MediaSpool | None = None,
        analyzer: FfprobeMediaAnalyzer | None = None,
    ):
        self.auth_token = _validate_token(auth_token)
        self.pairing_window = pairing_window
        self.job_store = job_store
        self.media_spool = media_spool
        self.analyzer = analyzer
        super().__init__(address, handler, bind_and_activate=True)

    def capabilities(self) -> list[str]:
        result = list(CAPABILITIES)
        if self.job_store is not None:
            result.extend(["CREATE_JOB", "GET_JOB", "CANCEL_JOB", "ANALYZE_MEDIA"])
        if self.job_store is not None and self.media_spool is not None:
            result.append("STAGE_MEDIA")
        if self.analyzer is not None and bool(getattr(self.analyzer, "available", False)):
            result.append("FFPROBE")
        return result


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
            self._handle_pair(request_id, request)
            return

        supplied_token = request.get("token")
        if not isinstance(supplied_token, str) or not hmac.compare_digest(supplied_token, self.server.auth_token):
            self.wfile.write(_response(False, request_id, code="AUTH_INVALID"))
            return

        try:
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
                self.wfile.write(_response(True, request_id, capabilities=self.server.capabilities()))
            elif operation == "CREATE_JOB":
                self._handle_create_job(request_id, request)
            elif operation == "GET_JOB":
                self._handle_get_job(request_id, request)
            elif operation == "CANCEL_JOB":
                self._handle_cancel_job(request_id, request)
            elif operation == "STAGE_MEDIA":
                self._handle_stage_media(request_id, request)
            else:
                self.wfile.write(_response(False, request_id, code="OPERATION_NOT_ALLOWED"))
        except (ValueError, KeyError, FileNotFoundError, RuntimeError) as error:
            self.wfile.write(_response(False, request_id, code=_error_code(error)))

    def _handle_pair(self, request_id: str, request: dict) -> None:
        pairing_window = self.server.pairing_window
        if pairing_window is None:
            self.wfile.write(_response(False, request_id, code="PAIRING_NOT_AVAILABLE"))
            return
        result = pairing_window.claim(request.get("pairingCode", ""))
        if result != "PAIRING_OK":
            self.wfile.write(_response(False, request_id, code=result))
            return
        self.wfile.write(_response(True, request_id, token=self.server.auth_token))

    def _require_jobs(self) -> JobStore:
        if self.server.job_store is None:
            raise ValueError("OPERATION_NOT_ALLOWED")
        return self.server.job_store

    def _handle_create_job(self, request_id: str, request: dict) -> None:
        store = self._require_jobs()
        job = store.create_job(
            project_id=PROJECT_ID,
            job_type=request.get("jobType"),
            idempotency_key=request.get("idempotencyKey"),
            input_data=request.get("input"),
        )
        if job["status"] == "QUEUED" and job["type"] == "ANALYZE_MEDIA":
            mode = job["input"].get("mode", "DESCRIPTOR")
            if mode == "DESCRIPTOR":
                job = self._execute_descriptor(job)
            elif mode != "MEDIA":
                job = store.update_job(
                    job["id"],
                    status="FAILED",
                    progress=100,
                    error={"code": "ANALYZE_MODE_INVALID", "message": "Unsupported analyze mode"},
                    log={"operation": "ANALYZE_MEDIA", "status": "FAILED"},
                )
        self.wfile.write(_response(True, request_id, job=job))

    def _execute_descriptor(self, job: dict) -> dict:
        store = self._require_jobs()
        try:
            store.update_job(
                job["id"],
                status="RUNNING",
                progress=25,
                log={"operation": "ANALYZE_MEDIA", "status": "RUNNING"},
            )
            descriptor = _validate_descriptor(job["input"])
            return store.update_job(
                job["id"],
                status="SUCCEEDED",
                progress=100,
                output={"analysisMode": "descriptor-v1", "asset": descriptor},
                log={"operation": "ANALYZE_MEDIA", "status": "SUCCEEDED", "exitCode": 0},
            )
        except (ValueError, RuntimeError) as error:
            return store.update_job(
                job["id"],
                status="FAILED",
                progress=100,
                error={"code": _error_code(error, "ANALYZE_MEDIA_FAILED"), "message": "Descriptor analysis failed"},
                log={"operation": "ANALYZE_MEDIA", "status": "FAILED"},
            )

    def _handle_get_job(self, request_id: str, request: dict) -> None:
        job = self._require_jobs().get_job(request.get("jobId"))
        self.wfile.write(_response(True, request_id, job=job))

    def _handle_cancel_job(self, request_id: str, request: dict) -> None:
        job = self._require_jobs().cancel_job(request.get("jobId"))
        self.wfile.write(_response(True, request_id, job=job))

    def _handle_stage_media(self, request_id: str, request: dict) -> None:
        store = self._require_jobs()
        spool = self.server.media_spool
        if spool is None:
            raise ValueError("OPERATION_NOT_ALLOWED")
        job_id = request.get("jobId")
        job = store.get_job(job_id)
        if job["type"] != "ANALYZE_MEDIA" or job["input"].get("mode") != "MEDIA":
            raise ValueError("MEDIA_JOB_INVALID")
        if job["status"] != "QUEUED":
            raise ValueError("JOB_NOT_QUEUED")
        content_length = _safe_int(request.get("contentLength"), "MEDIA_LENGTH_INVALID", 1)
        transfer = spool.stage_bytes(job_id, content_length, self._body_chunks(content_length))
        store.update_job(
            job_id,
            status="RUNNING",
            progress=35,
            log={"operation": "STAGE_MEDIA", "status": "SUCCEEDED", "bytes": transfer["sizeBytes"]},
        )
        job = self._execute_staged_analysis(job_id, transfer)
        public_transfer = {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]}
        self.wfile.write(_response(True, request_id, job=job, transfer=public_transfer))

    def _execute_staged_analysis(self, job_id: str, transfer: dict) -> dict:
        store = self._require_jobs()
        analyzer = self.server.analyzer
        if analyzer is None:
            return store.update_job(
                job_id,
                status="FAILED",
                progress=100,
                error={"code": "FFPROBE_UNAVAILABLE", "message": "ffprobe is not available"},
                log={"operation": "ANALYZE_MEDIA", "status": "FAILED"},
            )
        try:
            analysis = analyzer.analyze(self.server.media_spool.path_for(job_id))
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return current
            return store.update_job(
                job_id,
                status="SUCCEEDED",
                progress=100,
                output={
                    "analysisMode": "ffprobe-v1",
                    "transfer": {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]},
                    "analysis": analysis,
                },
                log={"operation": "ANALYZE_MEDIA", "status": "SUCCEEDED", "exitCode": 0},
            )
        except (ValueError, KeyError, FileNotFoundError, RuntimeError) as error:
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return current
            return store.update_job(
                job_id,
                status="FAILED",
                progress=100,
                error={"code": _error_code(error, "ANALYZE_MEDIA_FAILED"), "message": "Media analysis failed"},
                log={"operation": "ANALYZE_MEDIA", "status": "FAILED"},
            )

    def _body_chunks(self, content_length: int):
        remaining = content_length
        while remaining > 0:
            chunk = self.rfile.read(min(64 * 1024, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            yield chunk


def create_server(
    host: str,
    port: int,
    token: str,
    pairing_window: PairingWindow | None = None,
    job_store: JobStore | None = None,
    media_spool: MediaSpool | None = None,
    analyzer: FfprobeMediaAnalyzer | None = None,
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
        job_store,
        media_spool,
        analyzer,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut Termux Agent Engine")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--pair", action="store_true", help="Open one short-lived, one-time pairing window")
    parser.add_argument("--credentials-file", default=str(DEFAULT_CREDENTIALS_FILE))
    parser.add_argument("--jobs-file", default=str(DEFAULT_JOBS_FILE))
    parser.add_argument("--spool-dir", default=str(DEFAULT_SPOOL_DIR))
    args = parser.parse_args()

    env_token = os.environ.get("LOCALCUT_ENGINE_TOKEN", "").strip()
    credential_store = EngineCredentialStore(args.credentials_file)
    if env_token:
        token = _validate_token(env_token)
    elif args.pair:
        token = credential_store.load_or_create()
    else:
        token = credential_store.load()
        if token is None:
            raise SystemExit("ENGINE_NOT_INITIALIZED: start once with --pair")

    pairing_window = None
    if args.pair:
        pairing_code = generate_pairing_code()
        pairing_window = PairingWindow(pairing_code)
        print(f"LOCALCUT_PAIRING_CODE={format_pairing_code(pairing_code)}", flush=True)

    job_store = JobStore(args.jobs_file)
    recovered = job_store.recover_interrupted_jobs()
    if recovered:
        print(f"LOCALCUT_RECOVERED_INTERRUPTED_JOBS={recovered}", flush=True)
    media_spool = MediaSpool(args.spool_dir)
    analyzer = FfprobeMediaAnalyzer()

    server = create_server(
        args.host,
        args.port,
        token,
        pairing_window=pairing_window,
        job_store=job_store,
        media_spool=media_spool,
        analyzer=analyzer,
    )
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
