from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess


@dataclass(frozen=True)
class AudioExtractExecutionResult:
    output_path: Path
    duration_us: int


class AudioExtractFfmpegExecutor:
    MAX_DURATION_US = 10 * 60 * 1_000_000

    def __init__(self, ffmpeg_path: str | Path | None = None, timeout_seconds: int = 180):
        self.ffmpeg_path = str(ffmpeg_path) if ffmpeg_path is not None else shutil.which("ffmpeg")
        self.timeout_seconds = int(timeout_seconds)
        if not (1 <= self.timeout_seconds <= 900):
            raise ValueError("EXTRACT_AUDIO_TIMEOUT_INVALID")

    @property
    def available(self) -> bool:
        return bool(self.ffmpeg_path)

    def extract(
        self,
        *,
        input_path: str | Path,
        output_path: str | Path,
        duration_us: int,
    ) -> AudioExtractExecutionResult:
        if not self.ffmpeg_path:
            raise RuntimeError("FFMPEG_UNAVAILABLE")
        duration_us = int(duration_us)
        if duration_us <= 0:
            raise ValueError("EXTRACT_AUDIO_DURATION_INVALID")
        if duration_us > self.MAX_DURATION_US:
            raise ValueError("EXTRACT_AUDIO_DURATION_TOO_LONG")

        source = Path(input_path)
        output = Path(output_path)
        if source.is_symlink() or not source.is_file():
            raise FileNotFoundError("EXTRACT_AUDIO_INPUT_NOT_FOUND")
        if output.is_symlink():
            raise ValueError("EXTRACT_AUDIO_OUTPUT_INVALID")
        if source.resolve() == output.resolve(strict=False):
            raise ValueError("EXTRACT_AUDIO_OUTPUT_INVALID")

        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        if output.exists():
            if not output.is_file():
                raise ValueError("EXTRACT_AUDIO_OUTPUT_INVALID")
            output.unlink()

        command = [
            self.ffmpeg_path,
            "-hide_banner",
            "-loglevel",
            "error",
            "-nostdin",
            "-y",
            "-i",
            str(source),
            "-map",
            "0:a:0",
            "-vn",
            "-sn",
            "-dn",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-movflags",
            "+faststart",
            str(output),
        ]
        try:
            completed = subprocess.run(
                command,
                cwd=str(output.parent),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
                shell=False,
            )
        except subprocess.TimeoutExpired as exc:
            self._remove_output(output)
            raise RuntimeError("EXTRACT_AUDIO_FFMPEG_TIMEOUT") from exc
        except OSError as exc:
            self._remove_output(output)
            raise RuntimeError("EXTRACT_AUDIO_FFMPEG_EXEC_FAILED") from exc

        if completed.returncode != 0:
            self._remove_output(output)
            raise RuntimeError("EXTRACT_AUDIO_FFMPEG_FAILED")
        if output.is_symlink() or not output.is_file() or output.stat().st_size <= 0:
            self._remove_output(output)
            raise RuntimeError("EXTRACT_AUDIO_OUTPUT_INVALID")

        os.chmod(output, 0o600)
        return AudioExtractExecutionResult(output_path=output, duration_us=duration_us)

    @staticmethod
    def _remove_output(path: Path) -> None:
        try:
            path.unlink()
        except FileNotFoundError:
            pass
