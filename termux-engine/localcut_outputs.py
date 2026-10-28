from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil

from localcut_jobs import _validate_job_id


DEFAULT_MAX_GENERATED_BYTES = 4 * 1024 * 1024 * 1024
MAX_CHUNK_BYTES = 1024 * 1024


class GeneratedMediaSpool:
    def __init__(self, root: str | Path, max_bytes: int = DEFAULT_MAX_GENERATED_BYTES):
        self.root = Path(root).expanduser()
        self.max_bytes = int(max_bytes)
        if self.max_bytes <= 0:
            raise ValueError("GENERATED_MEDIA_MAX_BYTES_INVALID")
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.root, 0o700)

    def prepare_output(self, job_id: str) -> Path:
        normalized_id = _validate_job_id(job_id)
        job_dir = self.root / normalized_id
        job_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(job_dir, 0o700)
        output = job_dir / "output.mp4"
        metadata = job_dir / "output.json"
        for path in (output, metadata):
            try:
                path.unlink()
            except FileNotFoundError:
                pass
        return output

    def finalize(self, job_id: str) -> dict:
        normalized_id = _validate_job_id(job_id)
        job_dir = self.root / normalized_id
        output = job_dir / "output.mp4"
        if output.is_symlink():
            raise ValueError("GENERATED_MEDIA_INVALID")
        if not output.is_file():
            raise FileNotFoundError("GENERATED_MEDIA_NOT_READY")

        stat_result = output.stat()
        if stat_result.st_size <= 0:
            raise ValueError("GENERATED_MEDIA_EMPTY")
        if stat_result.st_size > self.max_bytes:
            raise ValueError("GENERATED_MEDIA_TOO_LARGE")

        digest = hashlib.sha256()
        with output.open("rb") as handle:
            while True:
                chunk = handle.read(256 * 1024)
                if not chunk:
                    break
                digest.update(chunk)
        result = {
            "sizeBytes": stat_result.st_size,
            "sha256": digest.hexdigest(),
            "mimeType": "video/mp4",
        }
        self._write_metadata(job_dir / "output.json", result)
        os.chmod(output, 0o600)
        return dict(result)

    def metadata(self, job_id: str) -> dict:
        normalized_id = _validate_job_id(job_id)
        job_dir = self.root / normalized_id
        output = job_dir / "output.mp4"
        metadata_path = job_dir / "output.json"
        if output.is_symlink() or metadata_path.is_symlink():
            raise ValueError("GENERATED_MEDIA_INVALID")
        if not output.is_file() or not metadata_path.is_file():
            raise FileNotFoundError("GENERATED_MEDIA_NOT_READY")
        try:
            raw = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ValueError("GENERATED_MEDIA_METADATA_INVALID") from exc
        if not isinstance(raw, dict):
            raise ValueError("GENERATED_MEDIA_METADATA_INVALID")
        size_bytes = raw.get("sizeBytes")
        sha256 = raw.get("sha256")
        mime_type = raw.get("mimeType")
        if (
            not isinstance(size_bytes, int)
            or size_bytes <= 0
            or size_bytes > self.max_bytes
            or not isinstance(sha256, str)
            or len(sha256) != 64
            or any(ch not in "0123456789abcdef" for ch in sha256)
            or mime_type != "video/mp4"
        ):
            raise ValueError("GENERATED_MEDIA_METADATA_INVALID")
        if output.stat().st_size != size_bytes:
            raise ValueError("GENERATED_MEDIA_SIZE_CHANGED")
        return {"sizeBytes": size_bytes, "sha256": sha256, "mimeType": mime_type}

    def open_chunks(self, job_id: str, chunk_size: int = 64 * 1024):
        chunk_size = int(chunk_size)
        if not (1 <= chunk_size <= MAX_CHUNK_BYTES):
            raise ValueError("GENERATED_MEDIA_CHUNK_SIZE_INVALID")
        normalized_id = _validate_job_id(job_id)
        metadata = self.metadata(normalized_id)
        output = self.root / normalized_id / "output.mp4"
        remaining = metadata["sizeBytes"]
        with output.open("rb") as handle:
            while remaining > 0:
                chunk = handle.read(min(chunk_size, remaining))
                if not chunk:
                    raise RuntimeError("GENERATED_MEDIA_TRUNCATED")
                remaining -= len(chunk)
                yield chunk
        if remaining != 0:
            raise RuntimeError("GENERATED_MEDIA_TRUNCATED")

    def remove(self, job_id: str) -> None:
        normalized_id = _validate_job_id(job_id)
        shutil.rmtree(self.root / normalized_id, ignore_errors=True)

    @staticmethod
    def _write_metadata(path: Path, value: dict) -> None:
        temp = path.with_name(".output.json.tmp")
        payload = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
        try:
            fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb", closefd=True) as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp, path)
            os.chmod(path, 0o600)
        finally:
            try:
                temp.unlink()
            except FileNotFoundError:
                pass
