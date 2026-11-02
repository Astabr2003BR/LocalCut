import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_reverse import ReverseChunkPlanner


class ReverseChunkPlannerTest(unittest.TestCase):
    def test_short_clip_is_one_chunk(self):
        chunks = ReverseChunkPlanner.plan(duration_us=1_500_000, chunk_duration_us=2_000_000)
        self.assertEqual([(0, 1_500_000)], [(c.start_us, c.end_us) for c in chunks])

    def test_long_clip_is_emitted_in_reverse_chunk_order_without_gaps(self):
        chunks = ReverseChunkPlanner.plan(duration_us=5_500_000, chunk_duration_us=2_000_000)
        self.assertEqual(
            [(4_000_000, 5_500_000), (2_000_000, 4_000_000), (0, 2_000_000)],
            [(c.start_us, c.end_us) for c in chunks],
        )
        source_order = sorted(chunks, key=lambda c: c.start_us)
        self.assertEqual(0, source_order[0].start_us)
        self.assertEqual(5_500_000, source_order[-1].end_us)
        self.assertTrue(all(a.end_us == b.start_us for a, b in zip(source_order, source_order[1:])))
        self.assertEqual(5_500_000, sum(c.duration_us for c in chunks))

    def test_chunk_duration_is_bounded_for_mobile_memory(self):
        chunks = ReverseChunkPlanner.plan(duration_us=10_000_000, chunk_duration_us=1_000_000)
        self.assertEqual(10, len(chunks))
        self.assertTrue(all(c.duration_us <= 1_000_000 for c in chunks))

    def test_invalid_duration_chunk_size_and_excessive_clip_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "REVERSE_DURATION_INVALID"):
            ReverseChunkPlanner.plan(0)
        with self.assertRaisesRegex(ValueError, "REVERSE_CHUNK_DURATION_INVALID"):
            ReverseChunkPlanner.plan(1_000_000, chunk_duration_us=100_000)
        with self.assertRaisesRegex(ValueError, "REVERSE_CHUNK_DURATION_INVALID"):
            ReverseChunkPlanner.plan(1_000_000, chunk_duration_us=10_000_000)
        with self.assertRaisesRegex(ValueError, "REVERSE_DURATION_TOO_LONG"):
            ReverseChunkPlanner.plan(ReverseChunkPlanner.MAX_DURATION_US + 1)


if __name__ == "__main__":
    unittest.main()
