import json
from pathlib import Path
import socket
from types import SimpleNamespace
import tempfile
import threading
import unittest
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_creative_server import create_toolchain_server
from localcut_engine import generate_token
from localcut_jobs import JobStore, MediaSpool
from localcut_outputs import GeneratedMediaSpool


class FakeReverseExecutor:
    available = True

    def reverse(
        self,
        *,
        input_path,
        output_path,
        duration_us,
        chunk_duration_us,
        include_audio=True,
    ):
        source = Path(input_path).read_bytes()
        Path(output_path).write_bytes(b"reversed:" + source[::-1])
        return SimpleNamespace(
            output_path=Path(output_path),
            duration_us=duration_us,
            chunk_count=2,
        )


class ReverseJobIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.store = JobStore(root / "jobs.sqlite3")
        self.media_spool = MediaSpool(root / "spool", max_bytes=1024 * 1024)
        self.output_spool = GeneratedMediaSpool(root / "outputs", max_bytes=1024 * 1024)
        self.token = generate_token()
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        self.port = probe.getsockname()[1]
        probe.close()
        self.server = create_toolchain_server(
            "127.0.0.1",
            self.port,
            self.token,
            job_store=self.store,
            media_spool=self.media_spool,
            toolchain_capabilities=("FFMPEG",),
            reverse_executor=FakeReverseExecutor(),
            generated_media_spool=self.output_spool,
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        if hasattr(self, "server"):
            self.server.shutdown()
            self.server.server_close()
        if hasattr(self, "thread"):
            self.thread.join(timeout=2)
        self.temp.cleanup()

    def exchange(self, payload, body=b""):
        raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
        with socket.create_connection(("127.0.0.1", self.port), timeout=3) as sock:
            sock.sendall(raw)
            if body:
                sock.sendall(body)
            return json.loads(sock.makefile("rb").readline().decode())

    def request(self, operation, **fields):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "reverse-12345678",
            "token": self.token,
            "operation": operation,
        }
        payload.update(fields)
        return self.exchange(payload)

    def test_reverse_job_stages_input_and_publishes_only_generated_media_metadata(self):
        capabilities = self.request("GET_CAPABILITIES")
        self.assertTrue(capabilities["ok"])
        self.assertIn("REVERSE_MEDIA", capabilities["capabilities"])

        created = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="asset-reverse-v1",
            input={
                "mode": "REVERSE",
                "assetId": "asset-reverse",
                "displayName": "input.mp4",
                "mimeType": "video/mp4",
                "sizeBytes": 5,
                "sourceDurationUs": 2_000_000,
                "chunkDurationUs": 1_000_000,
            },
        )
        self.assertTrue(created["ok"])
        self.assertEqual("QUEUED", created["job"]["status"])
        job_id = created["job"]["id"]

        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "reverse-stage-12345678",
            "token": self.token,
            "operation": "STAGE_MEDIA",
            "jobId": job_id,
            "contentLength": 5,
        }
        staged = self.exchange(payload, b"abcde")

        self.assertTrue(staged["ok"])
        self.assertEqual("SUCCEEDED", staged["job"]["status"])
        output = staged["job"]["output"]
        self.assertEqual("reverse-v1", output["transformMode"])
        self.assertEqual(2, output["chunkCount"])
        self.assertEqual("video/mp4", output["media"]["mimeType"])
        self.assertGreater(output["media"]["sizeBytes"], 0)
        self.assertEqual(64, len(output["media"]["sha256"]))
        self.assertNotIn("path", json.dumps(output).lower())
        self.assertEqual(output["media"], self.output_spool.metadata(job_id))

    def test_reverse_validation_fails_before_job_creation(self):
        invalid = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="asset-reverse-invalid",
            input={
                "mode": "REVERSE",
                "assetId": "asset-reverse",
                "mimeType": "video/mp4",
                "sizeBytes": 5,
                "sourceDurationUs": 0,
            },
        )
        self.assertFalse(invalid["ok"])
        self.assertEqual("REVERSE_DURATION_INVALID", invalid["code"])


if __name__ == "__main__":
    unittest.main()
