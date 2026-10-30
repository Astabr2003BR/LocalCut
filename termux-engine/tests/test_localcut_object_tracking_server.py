import socket
import sys
from pathlib import Path
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_creative_server import (
    _execute_object_tracking_job,
    _validate_object_tracking_input,
    create_toolchain_server,
)
from localcut_engine import generate_token


class _FakeObjectTracker:
    available = True

    def __init__(self):
        self.calls = []

    def analyze(self, path, roi, options):
        self.calls.append((path, roi, options))
        return {
            "trackingMode": "opencv-object-position-v1",
            "framesDecoded": 4,
            "frameWidth": 320,
            "frameHeight": 240,
            "frameRate": 30.0,
            "samples": [
                {
                    "frameIndex": 1,
                    "timeUs": 33333,
                    "dxNorm": 0.01,
                    "dyNorm": 0.02,
                    "centerXNorm": 0.45,
                    "centerYNorm": 0.55,
                    "confidence": 0.9,
                }
            ],
        }


class _FakeStore:
    def __init__(self):
        self.job = {
            "id": "job-1",
            "type": "ANALYZE_MEDIA",
            "status": "RUNNING",
            "progress": 20,
            "input": {
                "mode": "OBJECT_TRACKING",
                "maxFrames": 120,
                "sampleEvery": 2,
                "maxCorners": 80,
                "roi": {"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4},
            },
        }

    def get_job(self, _job_id):
        return self.job

    def update_job(self, _job_id, **changes):
        self.job = {**self.job, **changes}
        return self.job


class ObjectTrackingServerTest(unittest.TestCase):
    def free_port(self):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
        probe.close()
        return port

    def test_validator_normalizes_bounded_roi_and_options(self):
        normalized = _validate_object_tracking_input(
            {
                "mode": "OBJECT_TRACKING",
                "assetId": "asset-1",
                "displayName": "clip.mp4",
                "mimeType": "video/mp4",
                "sizeBytes": 12,
                "sourceDurationUs": 1_000_000,
                "maxFrames": 120,
                "sampleEvery": 2,
                "maxCorners": 80,
                "roi": {"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4},
            }
        )
        self.assertEqual("OBJECT_TRACKING", normalized["mode"])
        self.assertEqual(120, normalized["maxFrames"])
        self.assertEqual({"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4}, normalized["roi"])

        invalid = dict(normalized)
        invalid["roi"] = {"x": 0.9, "y": 0.2, "width": 0.2, "height": 0.4}
        with self.assertRaises(ValueError):
            _validate_object_tracking_input(invalid)

    def test_server_advertises_object_tracking_only_when_real_backend_is_available(self):
        tracker = _FakeObjectTracker()
        server = create_toolchain_server(
            "127.0.0.1",
            self.free_port(),
            generate_token(),
            toolchain_capabilities=(),
            object_tracking_analyzer=tracker,
        )
        try:
            capabilities = server.capabilities()
            self.assertIn("ANALYZE_OBJECT_TRACKING", capabilities)
            self.assertIn("OBJECT_TRACKING_POSITION", capabilities)
            self.assertNotIn("OPENCV_GLOBAL_MOTION", capabilities)
        finally:
            server.server_close()

    def test_worker_uses_roi_and_publishes_distinct_analysis_mode(self):
        tracker = _FakeObjectTracker()
        store = _FakeStore()
        server = SimpleNamespace(
            job_store=store,
            media_spool=SimpleNamespace(path_for=lambda _job_id: Path("/tmp/staged-video.mp4")),
            object_tracking_analyzer=tracker,
        )

        _execute_object_tracking_job(server, "job-1", {"sizeBytes": 12, "sha256": "a" * 64})

        self.assertEqual("SUCCEEDED", store.job["status"])
        self.assertEqual("opencv-object-position-v1", store.job["output"]["analysisMode"])
        self.assertEqual(0.1, tracker.calls[0][1].x)
        self.assertEqual(120, tracker.calls[0][2].max_frames)


if __name__ == "__main__":
    unittest.main()
