from __future__ import annotations

from dataclasses import dataclass


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
