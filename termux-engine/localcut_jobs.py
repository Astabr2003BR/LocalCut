from __future__ import annotations

import hashlib
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import threading
import time
import uuid

PROJECT_ID = "localcut"
ALLOWED_JOB_TYPES = {"ANALYZE_MEDIA"}
JOB_STATUSES = {"QUEUED", "RUNNING", "WAITING_APPROVAL", "SUCCEEDED", "FAILED", "CANCELLED"}
TERMINAL_STATUSES = {"SUCCEEDED", "FAILED", "CANCELLED"}
JOB_ID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")
IDEMPOTENCY_RE = re.compile(r"^[A-Za-z0-9._:-]{1,160}$")
DEFAULT_MAX_STAGE_BYTES = 4 * 1024 * 1024 * 1024
_AUTO_FFPROBE = object()


def _now_ms() -> int:
    return int(time.time() * 1000)


def _compact_json(value) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def _parse_json(value: str | None):
    if value is None:
        return None
    return json.loads(value)


def _validate_job_id(job_id: str) -> str:
    if not isinstance(job_id, str) or not JOB_ID_RE.fullmatch(job_id):
        raise ValueError("JOB_ID_INVALID")
    try:
        uuid.UUID(job_id)
    except ValueError as exc:
        raise ValueError("JOB_ID_INVALID") from exc
    return job_id.lower()


def _validate_project_id(project_id: str) -> str:
    if project_id != PROJECT_ID:
        raise ValueError("PROJECT_MISMATCH")
    return project_id


def _validate_job_type(job_type: str) -> str:
    if job_type not in ALLOWED_JOB_TYPES:
        raise ValueError("JOB_TYPE_NOT_ALLOWED")
    return job_type


def _validate_idempotency_key(key: str) -> str:
    if not isinstance(key, str) or not IDEMPOTENCY_RE.fullmatch(key):
        raise ValueError("IDEMPOTENCY_KEY_INVALID")
    return key


def _validate_input(input_data: dict) -> dict:
    if not isinstance(input_data, dict):
        raise ValueError("JOB_INPUT_INVALID")
    encoded = _compact_json(input_data).encode("utf-8")
    if len(encoded) > 16_384:
        raise ValueError("JOB_INPUT_TOO_LARGE")
    return input_data


class JobStore:
    def __init__(self, path: str | Path):
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path.parent, 0o700)
        self._lock = threading.RLock()
        self._initialize()

    @contextmanager
    def _connect(self):
        conn = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        try:
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA foreign_keys=ON")
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA synchronous=FULL")
            yield conn
        finally:
            conn.close()

    def _initialize(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY,
                    project_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL UNIQUE,
                    created_at INTEGER NOT NULL,
                    started_at INTEGER,
                    finished_at INTEGER,
                    progress INTEGER NOT NULL,
                    input_json TEXT NOT NULL,
                    output_json TEXT,
                    error_json TEXT,
                    logs_json TEXT NOT NULL
                )
                """
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status)")
        os.chmod(self.path, 0o600)

    def create_job(self, project_id: str, job_type: str, idempotency_key: str, input_data: dict) -> dict:
        _validate_project_id(project_id)
        _validate_job_type(job_type)
        key = _validate_idempotency_key(idempotency_key)
        normalized_input = _validate_input(input_data)
        now = _now_ms()
        with self._lock, self._connect() as conn:
            conn.execute("BEGIN IMMEDIATE")
            existing = conn.execute("SELECT * FROM jobs WHERE idempotency_key=?", (key,)).fetchone()
            if existing is not None:
                existing_job = self._row_to_job(existing)
                if (
                    existing_job["projectId"] != project_id
                    or existing_job["type"] != job_type
                    or existing_job["input"] != normalized_input
                ):
                    conn.execute("ROLLBACK")
                    raise ValueError("IDEMPOTENCY_CONFLICT")
                conn.execute("COMMIT")
                return existing_job
            job_id = str(uuid.uuid4())
            conn.execute(
                """
                INSERT INTO jobs(
                    id, project_id, type, status, idempotency_key, created_at,
                    started_at, finished_at, progress, input_json, output_json,
                    error_json, logs_json
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    job_id,
                    project_id,
                    job_type,
                    "QUEUED",
                    key,
                    now,
                    None,
                    None,
                    0,
                    _compact_json(normalized_input),
                    None,
                    None,
                    "[]",
                ),
            )
            row = conn.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
            conn.execute("COMMIT")
            return self._row_to_job(row)

    def get_job(self, job_id: str) -> dict:
        normalized_id = _validate_job_id(job_id)
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM jobs WHERE id=?", (normalized_id,)).fetchone()
        if row is None:
            raise KeyError("JOB_NOT_FOUND")
        return self._row_to_job(row)

    def update_job(
        self,
        job_id: str,
        *,
        status: str | None = None,
        progress: int | None = None,
        output: dict | None = None,
        error: dict | None = None,
        log: dict | None = None,
    ) -> dict:
        normalized_id = _validate_job_id(job_id)
        if status is not None and status not in JOB_STATUSES:
            raise ValueError("JOB_STATUS_INVALID")
        if progress is not None and not (0 <= int(progress) <= 100):
            raise ValueError("JOB_PROGRESS_INVALID")
        now = _now_ms()
        with self._lock, self._connect() as conn:
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT * FROM jobs WHERE id=?", (normalized_id,)).fetchone()
            if row is None:
                conn.execute("ROLLBACK")
                raise KeyError("JOB_NOT_FOUND")
            current = self._row_to_job(row)
            next_status = status or current["status"]
            if current["status"] in TERMINAL_STATUSES and next_status != current["status"]:
                conn.execute("ROLLBACK")
                raise ValueError("JOB_ALREADY_TERMINAL")
            started_at = current["startedAt"]
            if next_status == "RUNNING" and started_at is None:
                started_at = now
            finished_at = current["finishedAt"]
            if next_status in TERMINAL_STATUSES and finished_at is None:
                finished_at = now
            next_progress = current["progress"] if progress is None else int(progress)
            if next_progress < current["progress"]:
                conn.execute("ROLLBACK")
                raise ValueError("JOB_PROGRESS_REGRESSION")
            next_output = current["output"] if output is None else output
            next_error = current["error"] if error is None else error
            logs = list(current["logs"])
            if log is not None:
                safe_log = dict(log)
                safe_log.setdefault("timestamp", now)
                logs.append(safe_log)
                logs = logs[-200:]
            conn.execute(
                """
                UPDATE jobs SET status=?, started_at=?, finished_at=?, progress=?,
                    output_json=?, error_json=?, logs_json=? WHERE id=?
                """,
                (
                    next_status,
                    started_at,
                    finished_at,
                    next_progress,
                    None if next_output is None else _compact_json(next_output),
                    None if next_error is None else _compact_json(next_error),
                    _compact_json(logs),
                    normalized_id,
                ),
            )
            updated = conn.execute("SELECT * FROM jobs WHERE id=?", (normalized_id,)).fetchone()
            conn.execute("COMMIT")
            return self._row_to_job(updated)

    def cancel_job(self, job_id: str) -> dict:
        job = self.get_job(job_id)
        if job["status"] in TERMINAL_STATUSES:
            return job
        return self.update_job(
            job_id,
            status="CANCELLED",
            error={"code": "JOB_CANCELLED", "message": "Task cancelled by client"},
            log={"operation": "CANCEL_JOB", "status": "CANCELLED"},
        )

    def recover_interrupted_jobs(self) -> int:
        with self._lock, self._connect() as conn:
            rows = conn.execute("SELECT id FROM jobs WHERE status='RUNNING'").fetchall()
        for row in rows:
            self.update_job(
                row["id"],
                status="FAILED",
                error={
                    "code": "ENGINE_RESTARTED_DURING_JOB",
                    "message": "Engine restarted while task was running",
                },
                log={"operation": "RECOVER", "status": "FAILED", "exitCode": None},
            )
        return len(rows)

    @staticmethod
    def _row_to_job(row: sqlite3.Row) -> dict:
        return {
            "id": row["id"],
            "projectId": row["project_id"],
            "type": row["type"],
            "status": row["status"],
            "idempotencyKey": row["idempotency_key"],
            "createdAt": row["created_at"],
            "startedAt": row["started_at"],
            "finishedAt": row["finished_at"],
            "progress": row["progress"],
            "input": _parse_json(row["input_json"]),
            "output": _parse_json(row["output_json"]),
            "error": _parse_json(row["error_json"]),
            "logs": _parse_json(row["logs_json"]),
        }


class MediaSpool:
    def __init__(self, root: str | Path, max_bytes: int = DEFAULT_MAX_STAGE_BYTES):
        self.root = Path(root).expanduser()
        self.max_bytes = int(max_bytes)
        if self.max_bytes <= 0:
            raise ValueError("MEDIA_MAX_BYTES_INVALID")
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.root, 0o700)

    def stage_bytes(self, job_id: str, content_length: int, chunks) -> dict:
        normalized_id = _validate_job_id(job_id)
        content_length = int(content_length)
        if content_length <= 0:
            raise ValueError("MEDIA_LENGTH_INVALID")
        if content_length > self.max_bytes:
            raise ValueError("MEDIA_TOO_LARGE")
        job_dir = self.root / normalized_id
        job_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(job_dir, 0o700)
        target = job_dir / "input.bin"
        temp = job_dir / f".input.{uuid.uuid4().hex}.part"
        digest = hashlib.sha256()
        written = 0
        try:
            fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb", closefd=True) as handle:
                for chunk in chunks:
                    if not isinstance(chunk, (bytes, bytearray, memoryview)):
                        raise ValueError("MEDIA_CHUNK_INVALID")
                    if not chunk:
                        continue
                    written += len(chunk)
                    if written > content_length or written > self.max_bytes:
                        raise ValueError("MEDIA_LENGTH_MISMATCH")
                    data = bytes(chunk)
                    handle.write(data)
                    digest.update(data)
                handle.flush()
                os.fsync(handle.fileno())
            if written != content_length:
                raise ValueError("MEDIA_LENGTH_MISMATCH")
            os.replace(temp, target)
            os.chmod(target, 0o600)
            return {"path": str(target), "sizeBytes": written, "sha256": digest.hexdigest()}
        except OSError as exc:
            raise RuntimeError("MEDIA_STORAGE_WRITE_FAILED") from exc
        finally:
            try:
                temp.unlink()
            except FileNotFoundError:
                pass

    def path_for(self, job_id: str) -> Path:
        normalized_id = _validate_job_id(job_id)
        path = self.root / normalized_id / "input.bin"
        if not path.is_file():
            raise FileNotFoundError("MEDIA_NOT_STAGED")
        return path

    def remove(self, job_id: str) -> None:
        normalized_id = _validate_job_id(job_id)
        shutil.rmtree(self.root / normalized_id, ignore_errors=True)


class FfprobeMediaAnalyzer:
    def __init__(self, ffprobe_path=_AUTO_FFPROBE, timeout_seconds: int = 30):
        self.ffprobe_path = shutil.which("ffprobe") if ffprobe_path is _AUTO_FFPROBE else ffprobe_path
        self.timeout_seconds = int(timeout_seconds)
        if not (1 <= self.timeout_seconds <= 300):
            raise ValueError("FFPROBE_TIMEOUT_INVALID")

    @property
    def available(self) -> bool:
        return bool(self.ffprobe_path)

    def analyze(self, path: str | Path) -> dict:
        if not self.ffprobe_path:
            raise RuntimeError("FFPROBE_UNAVAILABLE")
        media_path = Path(path)
        if not media_path.is_file():
            raise FileNotFoundError("MEDIA_NOT_STAGED")
        try:
            completed = subprocess.run(
                [
                    self.ffprobe_path,
                    "-v", "error",
                    "-show_format",
                    "-show_streams",
                    "-of", "json",
                    str(media_path),
                ],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
                shell=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError("FFPROBE_TIMEOUT") from exc
        except OSError as exc:
            raise RuntimeError("FFPROBE_EXEC_FAILED") from exc
        if completed.returncode != 0:
            raise RuntimeError("FFPROBE_FAILED")
        if len(completed.stdout.encode("utf-8")) > 1_048_576:
            raise RuntimeError("FFPROBE_OUTPUT_TOO_LARGE")
        try:
            raw = json.loads(completed.stdout)
        except json.JSONDecodeError as exc:
            raise RuntimeError("FFPROBE_OUTPUT_INVALID") from exc
        if not isinstance(raw, dict):
            raise RuntimeError("FFPROBE_OUTPUT_INVALID")
        return self._normalize(raw)

    @classmethod
    def _normalize(cls, raw: dict) -> dict:
        fmt = raw.get("format") if isinstance(raw.get("format"), dict) else {}
        streams = raw.get("streams") if isinstance(raw.get("streams"), list) else []
        duration_us = cls._seconds_to_us(fmt.get("duration"))
        size_bytes = cls._to_int(fmt.get("size"))
        bit_rate = cls._to_int(fmt.get("bit_rate"))
        normalized_streams = []
        for stream in streams[:64]:
            if not isinstance(stream, dict):
                continue
            item = {
                "codecType": stream.get("codec_type"),
                "codecName": stream.get("codec_name"),
            }
            for source, target in (
                ("width", "width"),
                ("height", "height"),
                ("sample_rate", "sampleRate"),
                ("channels", "channels"),
            ):
                value = cls._to_int(stream.get(source))
                if value is not None:
                    item[target] = value
            frame_rate = cls._fraction_to_float(stream.get("avg_frame_rate"))
            if frame_rate is not None:
                item["frameRate"] = frame_rate
            normalized_streams.append(item)
        return {
            "formatName": fmt.get("format_name"),
            "durationUs": duration_us,
            "sizeBytes": size_bytes,
            "bitRate": bit_rate,
            "streams": normalized_streams,
        }

    @staticmethod
    def _seconds_to_us(value):
        try:
            seconds = float(value)
        except (TypeError, ValueError):
            return None
        if seconds < 0:
            return None
        return int(round(seconds * 1_000_000))

    @staticmethod
    def _to_int(value):
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return None
        return parsed if parsed >= 0 else None

    @staticmethod
    def _fraction_to_float(value):
        if not isinstance(value, str) or "/" not in value:
            return None
        left, right = value.split("/", 1)
        try:
            numerator = float(left)
            denominator = float(right)
        except ValueError:
            return None
        if denominator == 0:
            return None
        result = numerator / denominator
        return result if result >= 0 else None
