import json
import os
import stat
import tempfile
import unittest
from pathlib import Path

from localcut_jobs import JobStore, MediaSpool, FfprobeMediaAnalyzer


class JobStoreContractTest(unittest.TestCase):
    def test_idempotent_analyze_job_persists_and_recovers(self):
        with tempfile.TemporaryDirectory() as td:
            db = Path(td) / "jobs.sqlite3"
            store = JobStore(db)
            first = store.create_job(
                project_id="localcut",
                job_type="ANALYZE_MEDIA",
                idempotency_key="asset-1-analysis-v1",
                input_data={"assetId": "asset-1", "mimeType": "video/mp4", "sizeBytes": 12},
            )
            second = store.create_job(
                project_id="localcut",
                job_type="ANALYZE_MEDIA",
                idempotency_key="asset-1-analysis-v1",
                input_data={"assetId": "asset-1", "mimeType": "video/mp4", "sizeBytes": 12},
            )
            self.assertEqual(first["id"], second["id"])
            self.assertEqual("QUEUED", first["status"])
            store.update_job(first["id"], status="RUNNING", progress=25)
            recovered = JobStore(db)
            count = recovered.recover_interrupted_jobs()
            self.assertEqual(1, count)
            job = recovered.get_job(first["id"])
            self.assertEqual("FAILED", job["status"])
            self.assertEqual("ENGINE_RESTARTED_DURING_JOB", job["error"]["code"])

    def test_cancel_only_changes_non_terminal_job(self):
        with tempfile.TemporaryDirectory() as td:
            store = JobStore(Path(td) / "jobs.sqlite3")
            job = store.create_job("localcut", "ANALYZE_MEDIA", "cancel-1", {"assetId": "a"})
            cancelled = store.cancel_job(job["id"])
            self.assertEqual("CANCELLED", cancelled["status"])
            again = store.cancel_job(job["id"])
            self.assertEqual("CANCELLED", again["status"])


class MediaSpoolTest(unittest.TestCase):
    def test_stage_uses_generated_path_and_verifies_size(self):
        with tempfile.TemporaryDirectory() as td:
            spool = MediaSpool(Path(td) / "spool", max_bytes=1024)
            payload = b"hello-media"
            result = spool.stage_bytes("123e4567-e89b-12d3-a456-426614174000", len(payload), [payload])
            path = Path(result["path"])
            self.assertTrue(path.is_file())
            self.assertEqual(payload, path.read_bytes())
            self.assertEqual("input.bin", path.name)
            self.assertNotIn("..", str(path))
            self.assertEqual(len(payload), result["sizeBytes"])
            self.assertEqual(64, len(result["sha256"]))

    def test_stage_rejects_oversize_and_wrong_length(self):
        with tempfile.TemporaryDirectory() as td:
            spool = MediaSpool(Path(td) / "spool", max_bytes=4)
            with self.assertRaisesRegex(ValueError, "MEDIA_TOO_LARGE"):
                spool.stage_bytes("123e4567-e89b-12d3-a456-426614174000", 5, [b"12345"])
            spool = MediaSpool(Path(td) / "spool2", max_bytes=20)
            with self.assertRaisesRegex(ValueError, "MEDIA_LENGTH_MISMATCH"):
                spool.stage_bytes("123e4567-e89b-12d3-a456-426614174000", 5, [b"123"])


class FfprobeAnalyzerTest(unittest.TestCase):
    def test_ffprobe_runs_without_shell_and_normalizes_output(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            media = root / "input file.mp4"
            media.write_bytes(b"fake")
            args_log = root / "args.json"
            fake = root / "ffprobe"
            fake.write_text(
                "#!/usr/bin/env python3\n"
                "import json, os, sys\n"
                f"open({str(args_log)!r}, 'w').write(json.dumps(sys.argv[1:]))\n"
                "print(json.dumps({'format': {'format_name':'mov,mp4','duration':'1.25','size':'4','bit_rate':'64000'}, 'streams':[{'codec_type':'video','codec_name':'h264','width':1920,'height':1080,'avg_frame_rate':'30/1'}]}))\n",
                encoding="utf-8",
            )
            fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
            analyzer = FfprobeMediaAnalyzer(ffprobe_path=str(fake), timeout_seconds=5)
            result = analyzer.analyze(media)
            self.assertEqual(1_250_000, result["durationUs"])
            self.assertEqual(1920, result["streams"][0]["width"])
            args = json.loads(args_log.read_text())
            self.assertIn(str(media), args)
            self.assertNotIn("shell", args)

    def test_ffprobe_unavailable_fails_typed(self):
        analyzer = FfprobeMediaAnalyzer(ffprobe_path=None)
        with self.assertRaisesRegex(RuntimeError, "FFPROBE_UNAVAILABLE"):
            analyzer.analyze(Path("missing.mp4"))


if __name__ == "__main__":
    unittest.main()
