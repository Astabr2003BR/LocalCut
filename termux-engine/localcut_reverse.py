from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


@dataclass(frozen=True)
class ReverseChunk:
    start_us: int
    end_us: int

    @property
    def duration_us(self) -> int:
        return self.end_us - self.start_us


class ReverseChunkPlanner:
    MIN_CHUNK_DURATION_US = 250_000
    MAX_CHUNK_DURATION_US = 5_000_000
    DEFAULT_CHUNK_DURATION_US = 2_000_000
    MAX_DURATION_US = 10 * 60 * 1_000_000

    @classmethod
    def plan(
        cls,
        duration_us: int,
        chunk_duration_us: int = DEFAULT_CHUNK_DURATION_US,
    ) -> list[ReverseChunk]:
        duration_us = int(duration_us)
        chunk_duration_us = int(chunk_duration_us)
        if duration_us <= 0:
            raise ValueError("REVERSE_DURATION_INVALID")
        if duration_us > cls.MAX_DURATION_US:
            raise ValueError("REVERSE_DURATION_TOO_LONG")
        if not (cls.MIN_CHUNK_DURATION_US <= chunk_duration_us <= cls.MAX_CHUNK_DURATION_US):
            raise ValueError("REVERSE_CHUNK_DURATION_INVALID")

        source_chunks: list[ReverseChunk] = []
        start_us = 0
        while start_us < duration_us:
            end_us = min(duration_us, start_us + chunk_duration_us)
            source_chunks.append(ReverseChunk(start_us=start_us, end_us=end_us))
            start_us = end_us

        if not source_chunks:
            raise RuntimeError("REVERSE_PLAN_EMPTY")
        if source_chunks[0].start_us != 0 or source_chunks[-1].end_us != duration_us:
            raise RuntimeError("REVERSE_PLAN_COVERAGE_INVALID")
        if any(left.end_us != right.start_us for left, right in zip(source_chunks, source_chunks[1:])):
            raise RuntimeError("REVERSE_PLAN_GAP")

        return list(reversed(source_chunks))


@dataclass(frozen=True)
class ReverseExecutionResult:
    output_path: Path
    duration_us: int
    chunk_count: int


class ReverseFfmpegExecutor:
    def __init__(self, ffmpeg_path: str | Path | None = None, timeout_seconds: int = 180):
        self.ffmpeg_path = str(ffmpeg_path) if ffmpeg_path is not None else shutil.which("ffmpeg")
        self.timeout_seconds = int(timeout_seconds)
        if not (1 <= self.timeout_seconds <= 900):
            raise ValueError("REVERSE_TIMEOUT_INVALID")

    @property
    def available(self) -> bool:
        return bool(self.ffmpeg_path)

    def reverse(
        self,
        *,
        input_path: str | Path,
        output_path: str | Path,
        duration_us: int,
        chunk_duration_us: int = ReverseChunkPlanner.DEFAULT_CHUNK_DURATION_US,
        include_audio: bool = True,
    ) -> ReverseExecutionResult:
        if not self.ffmpeg_path:
            raise RuntimeError("FFMPEG_UNAVAILABLE")

        source = Path(input_path)
        output = Path(output_path)
        if source.is_symlink() or not source.is_file():
            raise FileNotFoundError("REVERSE_INPUT_NOT_FOUND")
        if output.is_symlink():
            raise ValueError("REVERSE_OUTPUT_INVALID")
        if source.resolve() == output.resolve(strict=False):
            raise ValueError("REVERSE_OUTPUT_INVALID")

        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        if output.exists():
            if not output.is_file():
                raise ValueError("REVERSE_OUTPUT_INVALID")
            output.unlink()

        chunks = ReverseChunkPlanner.plan(duration_us, chunk_duration_us)
        workspace = Path(tempfile.mkdtemp(prefix=".reverse-", dir=output.parent))
        try:
            chunk_paths: list[Path] = []
            for index, chunk in enumerate(chunks):
                chunk_path = workspace / f"chunk-{index:06d}.mp4"
                command = [
                    self.ffmpeg_path,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-nostdin",
                    "-y",
                    "-ss",
                    self._seconds(chunk.start_us),
                    "-t",
                    self._seconds(chunk.duration_us),
                    "-i",
                    str(source),
                    "-map",
                    "0:v:0",
                ]
                if include_audio:
                    command += ["-map", "0:a?", "-vf", "reverse", "-af", "areverse"]
                else:
                    command += ["-vf", "reverse", "-an"]
                command += [
                    "-sn",
                    "-dn",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "veryfast",
                    "-crf",
                    "20",
                    "-pix_fmt",
                    "yuv420p",
                ]
                if include_audio:
                    command += ["-c:a", "aac", "-b:a", "192k"]
                command += [
                    "-movflags",
                    "+faststart",
                    "-avoid_negative_ts",
                    "make_zero",
                    str(chunk_path),
                ]
                self._run(command, cwd=workspace)
                if chunk_path.is_symlink() or not chunk_path.is_file() or chunk_path.stat().st_size <= 0:
                    raise RuntimeError("REVERSE_CHUNK_OUTPUT_INVALID")
                chunk_paths.append(chunk_path)

            concat_path = workspace / "concat.txt"
            concat_path.write_text(
                "".join(f"file '{path.name}'\n" for path in chunk_paths),
                encoding="utf-8",
            )
            final_temp = workspace / "final.mp4"
            self._run(
                [
                    self.ffmpeg_path,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-nostdin",
                    "-y",
                    "-f",
                    "concat",
                    "-safe",
                    "1",
                    "-i",
                    concat_path.name,
                    "-c",
                    "copy",
                    "-movflags",
                    "+faststart",
                    str(final_temp),
                ],
                cwd=workspace,
            )
            if final_temp.is_symlink() or not final_temp.is_file() or final_temp.stat().st_size <= 0:
                raise RuntimeError("REVERSE_OUTPUT_INVALID")

            os.replace(final_temp, output)
            os.chmod(output, 0o600)
            return ReverseExecutionResult(
                output_path=output,
                duration_us=int(duration_us),
                chunk_count=len(chunks),
            )
        except Exception:
            try:
                output.unlink()
            except FileNotFoundError:
                pass
            raise
        finally:
            shutil.rmtree(workspace, ignore_errors=True)

    def _run(self, command: list[str], *, cwd: Path) -> None:
        try:
            completed = subprocess.run(
                command,
                cwd=str(cwd),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
                shell=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError("REVERSE_FFMPEG_TIMEOUT") from exc
        except OSError as exc:
            raise RuntimeError("REVERSE_FFMPEG_EXEC_FAILED") from exc
        if completed.returncode != 0:
            raise RuntimeError("REVERSE_FFMPEG_FAILED")

    @staticmethod
    def _seconds(value_us: int) -> str:
        return f"{int(value_us) / 1_000_000.0:.6f}"
