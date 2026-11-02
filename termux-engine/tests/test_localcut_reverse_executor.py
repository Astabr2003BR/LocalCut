import json
import os
from pathlib import Path
import stat
import tempfile
import unittest

import localcut_reverse


class ReverseFfmpegExecutorTest(unittest.TestCase):
    def _make_fake_ffmpeg(self, root: Path, fail: bool = False) -> tuple[Path, Path]:
        log_path = root / "ffmpeg-log.jsonl"
        script = root / "fake-ffmpeg"
        script.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, pathlib, sys\n"
            "args = sys.argv[1:]\n"
            "with open(os.environ['LOCALCUT_FAKE_FFMPEG_LOG'], 'a', encoding='utf-8') as h:\n"
            "    h.write(json.dumps(args) + '\\n')\n"
            + (
                "sys.exit(9)\n"
                if fail
                else "out = pathlib.Path(args[-1])\n"
                "out.write_bytes(b'final' if 'concat' in args else b'chunk')\n"
                "sys.exit(0)\n"
            )
        )
        script.chmod(script.stat().st_mode | stat.S_IEXEC)
        return script, log_path

    def test_executor_reverses_chunks_then_concats_into_requested_output(self):
        executor_type = getattr(localcut_reverse, "ReverseFfmpegExecutor", None)
        self.assertIsNotNone(executor_type, "ReverseFfmpegExecutor must be implemented")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fake, log_path = self._make_fake_ffmpeg(root)
            source = root / "input video.mp4"
            source.write_bytes(b"source")
            output = root / "output.mp4"
            old_log = os.environ.get("LOCALCUT_FAKE_FFMPEG_LOG")
            os.environ["LOCALCUT_FAKE_FFMPEG_LOG"] = str(log_path)
            try:
                result = executor_type(ffmpeg_path=str(fake), timeout_seconds=5).reverse(
                    input_path=source,
                    output_path=output,
                    duration_us=5_500_000,
                    chunk_duration_us=2_000_000,
                    include_audio=True,
                )
            finally:
                if old_log is None:
                    os.environ.pop("LOCALCUT_FAKE_FFMPEG_LOG", None)
                else:
                    os.environ["LOCALCUT_FAKE_FFMPEG_LOG"] = old_log

            self.assertEqual(b"final", output.read_bytes())
            self.assertEqual(3, result.chunk_count)
            self.assertEqual(5_500_000, result.duration_us)
            calls = [json.loads(line) for line in log_path.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(4, len(calls))
            self.assertEqual(
                ["4.000000", "2.000000", "0.000000"],
                [call[call.index("-ss") + 1] for call in calls[:3]],
            )
            self.assertTrue(all("reverse" in call for call in calls[:3]))
            self.assertTrue(all("areverse" in call for call in calls[:3]))
            self.assertIn("concat", calls[-1])
            self.assertIn("copy", calls[-1])

    def test_executor_fails_closed_and_does_not_publish_partial_output(self):
        executor_type = getattr(localcut_reverse, "ReverseFfmpegExecutor", None)
        self.assertIsNotNone(executor_type, "ReverseFfmpegExecutor must be implemented")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fake, log_path = self._make_fake_ffmpeg(root, fail=True)
            source = root / "input.mp4"
            source.write_bytes(b"source")
            output = root / "output.mp4"
            old_log = os.environ.get("LOCALCUT_FAKE_FFMPEG_LOG")
            os.environ["LOCALCUT_FAKE_FFMPEG_LOG"] = str(log_path)
            try:
                with self.assertRaisesRegex(RuntimeError, "REVERSE_FFMPEG_FAILED"):
                    executor_type(ffmpeg_path=str(fake), timeout_seconds=5).reverse(
                        input_path=source,
                        output_path=output,
                        duration_us=1_000_000,
                        include_audio=False,
                    )
            finally:
                if old_log is None:
                    os.environ.pop("LOCALCUT_FAKE_FFMPEG_LOG", None)
                else:
                    os.environ["LOCALCUT_FAKE_FFMPEG_LOG"] = old_log
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
