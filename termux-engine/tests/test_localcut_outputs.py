import tempfile
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_outputs import GeneratedMediaSpool


JOB_ID = "123e4567-e89b-12d3-a456-426614174000"


class GeneratedMediaSpoolTest(unittest.TestCase):
    def test_prepares_private_fixed_output_path_and_finalizes_digest(self):
        with tempfile.TemporaryDirectory() as td:
            spool = GeneratedMediaSpool(Path(td) / "generated", max_bytes=1024)
            path = spool.prepare_output(JOB_ID)
            self.assertEqual("output.mp4", path.name)
            self.assertEqual(JOB_ID, path.parent.name)
            self.assertFalse(path.exists())

            path.write_bytes(b"generated-video")
            result = spool.finalize(JOB_ID)

            self.assertEqual(len(b"generated-video"), result["sizeBytes"])
            self.assertEqual(64, len(result["sha256"]))
            self.assertEqual("video/mp4", result["mimeType"])
            self.assertNotIn(str(path), result)

    def test_open_chunks_reads_only_generated_file_and_remove_cleans_job_dir(self):
        with tempfile.TemporaryDirectory() as td:
            spool = GeneratedMediaSpool(Path(td) / "generated", max_bytes=1024)
            path = spool.prepare_output(JOB_ID)
            path.write_bytes(b"abcdefgh")
            spool.finalize(JOB_ID)

            chunks = list(spool.open_chunks(JOB_ID, chunk_size=3))
            self.assertEqual(b"abcdefgh", b"".join(chunks))
            self.assertTrue(all(len(chunk) <= 3 for chunk in chunks))

            spool.remove(JOB_ID)
            self.assertFalse(path.parent.exists())

    def test_rejects_invalid_job_id_symlink_oversize_and_unfinalized_output(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td) / "generated"
            spool = GeneratedMediaSpool(root, max_bytes=4)
            with self.assertRaisesRegex(ValueError, "JOB_ID_INVALID"):
                spool.prepare_output("../../etc/passwd")

            path = spool.prepare_output(JOB_ID)
            with self.assertRaisesRegex(FileNotFoundError, "GENERATED_MEDIA_NOT_READY"):
                spool.finalize(JOB_ID)

            path.write_bytes(b"12345")
            with self.assertRaisesRegex(ValueError, "GENERATED_MEDIA_TOO_LARGE"):
                spool.finalize(JOB_ID)

            path.unlink()
            target = root / "outside.mp4"
            target.write_bytes(b"ok")
            path.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "GENERATED_MEDIA_INVALID"):
                spool.finalize(JOB_ID)

    def test_chunk_size_is_bounded(self):
        with tempfile.TemporaryDirectory() as td:
            spool = GeneratedMediaSpool(Path(td) / "generated", max_bytes=1024)
            path = spool.prepare_output(JOB_ID)
            path.write_bytes(b"abc")
            spool.finalize(JOB_ID)
            with self.assertRaisesRegex(ValueError, "GENERATED_MEDIA_CHUNK_SIZE_INVALID"):
                list(spool.open_chunks(JOB_ID, chunk_size=0))
            with self.assertRaisesRegex(ValueError, "GENERATED_MEDIA_CHUNK_SIZE_INVALID"):
                list(spool.open_chunks(JOB_ID, chunk_size=2 * 1024 * 1024))


if __name__ == "__main__":
    unittest.main()
