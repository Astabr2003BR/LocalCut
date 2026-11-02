import hashlib
import json
from pathlib import Path
import socket
from types import SimpleNamespace
import tempfile
import threading
import time
import unittest
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_audio_extension import create_audio_toolchain_server
from localcut_engine import generate_token
from localcut_jobs import JobStore, MediaSpool
from localcut_outputs import GeneratedMediaSpool


class FakeAudioExtractExecutor:
    available = True

    def extract(self, *, input_path, output_path, duration_us):
        source = Path(input_path).read_bytes()
        Path(output_path).write_bytes(b"audio:" + source)
        return SimpleNamespace(
            output_path=Path(output_path),
            duration_us=duration_us,
        )


class BlockingAudioExtractExecutor(FakeAudioExtractExecutor):
    def __init__(self):
        self.started = threading.Event()
        self.release = threading.Event()

    def extract(self, **kwargs):
        self.started.set()
        if not self.release.wait(timeout=3):
            raise RuntimeError("TEST_AUDIO_EXTRACT_RELEASE_TIMEOUT")
        return super().extract(**kwargs)


class AudioExtractJobIntegrationTest(unittest.TestCase):
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
        self.server = create_audio_toolchain_server(
            "127.0.0.1",
            self.port,
            self.token,
            job_store=self.store,
            media_spool=self.media_spool,
            toolchain_capabilities=("FFMPEG",),
            audio_extract_executor=FakeAudioExtractExecutor(),
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
            "requestId": "audio-extract-12345678",
            "token": self.token,
            "operation": operation,
        }
        payload.update(fields)
        return self.exchange(payload)

    def create_extract_job(self, *, idempotency_key="asset-audio-extract-v1"):
        return self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey=idempotency_key,
            input={
                "mode": "EXTRACT_AUDIO",
                "assetId": "asset-video",
                "displayName": "input.mp4",
                "mimeType": "video/mp4",
                "sizeBytes": 5,
                "sourceDurationUs": 2_000_000,
            },
        )

    def stage_extract_job(self, job_id):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "audio-stage-12345678",
            "token": self.token,
            "operation": "STAGE_MEDIA",
            "jobId": job_id,
            "contentLength": 5,
        }
        return self.exchange(payload, b"abcde")

    def download_generated_media(self, job_id):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "audio-download-12345678",
            "token": self.token,
            "operation": "GET_JOB",
            "jobId": job_id,
            "includeGeneratedMedia": True,
        }
        raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
        with socket.create_connection(("127.0.0.1", self.port), timeout=3) as sock:
            sock.sendall(raw)
            stream = sock.makefile("rb")
            header = json.loads(stream.readline().decode())
            if not header.get("ok"):
                return header, b""
            body = stream.read(header["contentLength"])
            return header, body

    def wait_for_terminal_job(self, job_id, timeout=3.0):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            response = self.request("GET_JOB", jobId=job_id)
            self.assertTrue(response["ok"])
            job = response["job"]
            if job["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}:
                return job
            time.sleep(0.02)
        self.fail("audio extract job did not reach a terminal state")

    def test_extract_audio_stages_video_and_publishes_m4a_metadata(self):
        capabilities = self.request("GET_CAPABILITIES")
        self.assertTrue(capabilities["ok"])
        self.assertIn("EXTRACT_AUDIO", capabilities["capabilities"])
        self.assertIn("DOWNLOAD_GENERATED_MEDIA", capabilities["capabilities"])

        created = self.create_extract_job()
        self.assertTrue(created["ok"])
        self.assertEqual("QUEUED", created["job"]["status"])
        job_id = created["job"]["id"]

        staged = self.stage_extract_job(job_id)
        self.assertTrue(staged["ok"])
        self.assertEqual("RUNNING", staged["job"]["status"])

        completed = self.wait_for_terminal_job(job_id)
        self.assertEqual("SUCCEEDED", completed["status"])
        output = completed["output"]
        self.assertEqual("extract-audio-v1", output["transformMode"])
        self.assertEqual(2_000_000, output["durationUs"])
        self.assertEqual("audio/mp4", output["media"]["mimeType"])
        self.assertGreater(output["media"]["sizeBytes"], 0)
        self.assertEqual(64, len(output["media"]["sha256"]))
        self.assertNotIn("path", json.dumps(output).lower())
        self.assertEqual(output["media"], self.output_spool.metadata(job_id))

        header, body = self.download_generated_media(job_id)
        self.assertTrue(header["ok"])
        self.assertTrue(header["binaryBody"])
        self.assertEqual("audio/mp4", header["media"]["mimeType"])
        self.assertEqual(b"audio:abcde", body)
        self.assertEqual(header["contentLength"], len(body))
        self.assertEqual(header["sha256"], hashlib.sha256(body).hexdigest())
        self.assertNotIn("path", json.dumps(header).lower())

    def test_stage_returns_running_while_audio_worker_is_busy(self):
        blocking = BlockingAudioExtractExecutor()
        self.server.audio_extract_executor = blocking
        created = self.create_extract_job(idempotency_key="asset-audio-background")
        self.assertTrue(created["ok"])
        job_id = created["job"]["id"]
        result = {}

        staging = threading.Thread(
            target=lambda: result.setdefault("response", self.stage_extract_job(job_id)),
            daemon=True,
        )
        staging.start()
        try:
            self.assertTrue(blocking.started.wait(timeout=1))
            staging.join(timeout=0.5)
            self.assertFalse(staging.is_alive(), "STAGE_MEDIA waited for audio extraction")
            self.assertEqual("RUNNING", result["response"]["job"]["status"])
        finally:
            blocking.release.set()
            staging.join(timeout=2)

        self.assertEqual("SUCCEEDED", self.wait_for_terminal_job(job_id)["status"])

    def test_cancelled_audio_job_does_not_publish_or_keep_generated_media(self):
        blocking = BlockingAudioExtractExecutor()
        self.server.audio_extract_executor = blocking
        created = self.create_extract_job(idempotency_key="asset-audio-cancel")
        job_id = created["job"]["id"]
        staged = self.stage_extract_job(job_id)
        self.assertEqual("RUNNING", staged["job"]["status"])
        self.assertTrue(blocking.started.wait(timeout=1))

        cancelled = self.request("CANCEL_JOB", jobId=job_id)
        self.assertTrue(cancelled["ok"])
        self.assertEqual("CANCELLED", cancelled["job"]["status"])
        blocking.release.set()

        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            if not (self.output_spool.root / job_id).exists():
                break
            time.sleep(0.02)
        self.assertFalse((self.output_spool.root / job_id).exists())
        header, body = self.download_generated_media(job_id)
        self.assertFalse(header["ok"])
        self.assertEqual("GENERATED_MEDIA_NOT_READY", header["code"])
        self.assertEqual(b"", body)

    def test_extract_audio_validation_rejects_non_video_before_job_creation(self):
        invalid = self.request(
            "CREATE_JOB",
            jobType="ANALYZE_MEDIA",
            idempotencyKey="asset-audio-invalid",
            input={
                "mode": "EXTRACT_AUDIO",
                "assetId": "asset-audio",
                "mimeType": "audio/mp4",
                "sizeBytes": 5,
                "sourceDurationUs": 2_000_000,
            },
        )
        self.assertFalse(invalid["ok"])
        self.assertEqual("EXTRACT_AUDIO_REQUIRES_VIDEO", invalid["code"])


class GeneratedMediaSpoolAudioTest(unittest.TestCase):
    def test_audio_profile_keeps_reverse_video_profile_backward_compatible(self):
        with tempfile.TemporaryDirectory() as temp:
            spool = GeneratedMediaSpool(temp, max_bytes=1024)

            video_id = "123e4567-e89b-12d3-a456-426614174000"
            video_path = spool.prepare_output(video_id)
            self.assertEqual("output.mp4", video_path.name)
            video_path.write_bytes(b"video")
            self.assertEqual("video/mp4", spool.finalize(video_id)["mimeType"])
            self.assertEqual(b"video", b"".join(spool.open_chunks(video_id)))

            audio_id = "223e4567-e89b-12d3-a456-426614174000"
            audio_path = spool.prepare_output(audio_id, mime_type="audio/mp4")
            self.assertEqual("output.m4a", audio_path.name)
            audio_path.write_bytes(b"audio")
            self.assertEqual("audio/mp4", spool.finalize(audio_id, mime_type="audio/mp4")["mimeType"])
            self.assertEqual("audio/mp4", spool.metadata(audio_id)["mimeType"])
            self.assertEqual(b"audio", b"".join(spool.open_chunks(audio_id)))


if __name__ == "__main__":
    unittest.main()
