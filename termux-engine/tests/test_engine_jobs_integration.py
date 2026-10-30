import json
import socket
import tempfile
import threading
import time
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_creative_server import create_toolchain_server
from localcut_engine import generate_token
from localcut_jobs import JobStore, MediaSpool


class FakeAnalyzer:
    available = True

    def analyze(self, path):
        data = Path(path).read_bytes()
        return {"formatName": "fake", "durationUs": 2_000_000, "sizeBytes": len(data), "streams": []}


class FakeTrackingAnalyzer:
    available = True

    def analyze(self, path, options=None):
        data = Path(path).read_bytes()
        return {
            "trackingMode": "fake-global-motion-v1",
            "framesDecoded": 12,
            "sampleEvery": options.sample_every,
            "maxCorners": options.max_corners,
            "frameRate": 30.0,
            "sizeBytes": len(data),
            "samples": [
                {
                    "frameIndex": options.sample_every,
                    "timeUs": 100_000,
                    "dx": 2.0,
                    "dy": 1.0,
                    "rotationDeg": 0.0,
                    "scale": 1.0,
                    "confidence": 1.0,
                },
            ],
        }


class BlockingTrackingAnalyzer(FakeTrackingAnalyzer):
    def __init__(self):
        self.started = threading.Event()
        self.release = threading.Event()

    def analyze(self, path, options=None):
        self.started.set()
        if not self.release.wait(timeout=3):
            raise RuntimeError("TEST_TRACKING_RELEASE_TIMEOUT")
        return super().analyze(path, options=options)


class EngineJobsIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.store = JobStore(root / "jobs.sqlite3")
        self.spool = MediaSpool(root / "spool", max_bytes=1024 * 1024)
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
            media_spool=self.spool,
            analyzer=FakeAnalyzer(),
            toolchain_capabilities=("OPENCV",),
            tracking_analyzer=FakeTrackingAnalyzer(),
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
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
            "requestId": "request-12345678",
            "token": self.token,
            "operation": operation,
        }
        payload.update(fields)
        return self.exchange(payload)

    def wait_for_terminal_job(self, job_id, timeout=3.0):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            response = self.request("GET_JOB", jobId=job_id)
            self.assertTrue(response["ok"])
            job = response["job"]
            if job["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}:
                return job
            time.sleep(0.02)
        self.fail("job did not reach a terminal state")

    def create_tracking_job(self, idempotency_key="asset-3-tracking-v1"):
        return self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey=idempotency_key,
            input={
                "mode": "TRACKING",
                "assetId": "asset-3",
                "mimeType": "video/mp4",
                "sizeBytes": 5,
                "maxFrames": 120,
                "sampleEvery": 3,
                "maxCorners": 80,
            },
        )

    def stage_tracking_job(self, job_id):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "track-12345678",
            "token": self.token,
            "operation": "STAGE_MEDIA",
            "jobId": job_id,
            "contentLength": 5,
        }
        return self.exchange(payload, b"abcde")

    def test_capabilities_include_jobs_ffprobe_and_tracking_when_available(self):
        result = self.request("GET_CAPABILITIES")
        self.assertTrue(result["ok"])
        for expected in (
            "CREATE_JOB",
            "GET_JOB",
            "CANCEL_JOB",
            "STAGE_MEDIA",
            "ANALYZE_MEDIA",
            "FFPROBE",
            "ANALYZE_TRACKING",
            "OPENCV",
            "OPENCV_GLOBAL_MOTION",
        ):
            self.assertIn(expected, result["capabilities"])

    def test_descriptor_analyze_job_is_idempotent_and_succeeds(self):
        fields = {
            "jobType": "ANALYZE_MEDIA",
            "idempotencyKey": "asset-1-descriptor-v1",
            "input": {
                "mode": "DESCRIPTOR",
                "assetId": "asset-1",
                "displayName": "vídeo teste.mp4",
                "mimeType": "video/mp4",
                "sizeBytes": 123,
                "sourceDurationUs": 2_000_000,
            },
        }
        first = self.request("CREATE_JOB", **fields)
        second = self.request("CREATE_JOB", **fields)
        self.assertTrue(first["ok"])
        self.assertEqual("SUCCEEDED", first["job"]["status"])
        self.assertEqual(100, first["job"]["progress"])
        self.assertEqual("descriptor-v1", first["job"]["output"]["analysisMode"])
        self.assertEqual(first["job"]["id"], second["job"]["id"])

    def test_media_job_stages_bytes_then_runs_analyzer(self):
        created = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="asset-2-media-v1",
            input={"mode": "MEDIA", "assetId": "asset-2", "mimeType": "video/mp4", "sizeBytes": 5},
        )
        self.assertEqual("QUEUED", created["job"]["status"])
        job_id = created["job"]["id"]
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "stage-12345678",
            "token": self.token,
            "operation": "STAGE_MEDIA",
            "jobId": job_id,
            "contentLength": 5,
        }
        staged = self.exchange(payload, b"abcde")
        self.assertTrue(staged["ok"])
        self.assertEqual("SUCCEEDED", staged["job"]["status"])
        self.assertEqual(5, staged["job"]["output"]["analysis"]["sizeBytes"])
        self.assertEqual(64, len(staged["transfer"]["sha256"]))

    def test_tracking_mode_stages_media_and_runs_only_typed_opencv_analyzer(self):
        created = self.create_tracking_job()
        self.assertTrue(created["ok"])
        self.assertEqual("QUEUED", created["job"]["status"])
        self.assertEqual(3, created["job"]["input"]["sampleEvery"])
        job_id = created["job"]["id"]

        staged = self.stage_tracking_job(job_id)

        self.assertTrue(staged["ok"])
        self.assertEqual("RUNNING", staged["job"]["status"])
        completed = self.wait_for_terminal_job(job_id)
        self.assertEqual("SUCCEEDED", completed["status"])
        self.assertEqual("opencv-global-motion-v1", completed["output"]["analysisMode"])
        self.assertEqual(3, completed["output"]["analysis"]["sampleEvery"])
        self.assertEqual(80, completed["output"]["analysis"]["maxCorners"])
        self.assertEqual(5, completed["output"]["analysis"]["sizeBytes"])
        self.assertEqual(64, len(completed["output"]["transfer"]["sha256"]))

    def test_tracking_stage_returns_while_background_analyzer_is_busy(self):
        blocking = BlockingTrackingAnalyzer()
        self.server.tracking_analyzer = blocking
        created = self.create_tracking_job(idempotency_key="asset-3-tracking-background")
        self.assertTrue(created["ok"])
        job_id = created["job"]["id"]
        result = {}
        staging = threading.Thread(
            target=lambda: result.setdefault("response", self.stage_tracking_job(job_id)),
            daemon=True,
        )
        staging.start()
        try:
            self.assertTrue(blocking.started.wait(timeout=1))
            staging.join(timeout=0.5)
            self.assertFalse(staging.is_alive(), "STAGE_MEDIA waited for the tracking analyzer")
            self.assertEqual("RUNNING", result["response"]["job"]["status"])
        finally:
            blocking.release.set()
            staging.join(timeout=2)

        completed = self.wait_for_terminal_job(job_id)
        self.assertEqual("SUCCEEDED", completed["status"])

    def test_tracking_options_fail_closed_before_job_creation(self):
        invalid = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="asset-4-tracking-invalid",
            input={
                "mode": "TRACKING",
                "assetId": "asset-4",
                "mimeType": "video/mp4",
                "sizeBytes": 5,
                "sampleEvery": 0,
            },
        )
        self.assertFalse(invalid["ok"])
        self.assertEqual("TRACKING_SAMPLE_EVERY_INVALID", invalid["code"])

    def test_idempotency_conflict_and_path_traversal_fail_closed(self):
        self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="same-key",
            input={"mode": "DESCRIPTOR", "assetId": "a", "mimeType": "video/mp4", "sizeBytes": 1},
        )
        conflict = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="same-key",
            input={"mode": "DESCRIPTOR", "assetId": "b", "mimeType": "video/mp4", "sizeBytes": 2},
        )
        self.assertFalse(conflict["ok"])
        self.assertEqual("IDEMPOTENCY_CONFLICT", conflict["code"])
        traversal = self.request("GET_JOB", jobId="../../etc/passwd")
        self.assertFalse(traversal["ok"])
        self.assertEqual("JOB_ID_INVALID", traversal["code"])


if __name__ == "__main__":
    unittest.main()
