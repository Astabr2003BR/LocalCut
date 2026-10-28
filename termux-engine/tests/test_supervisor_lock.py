from pathlib import Path
import tempfile
import unittest

import sys
import types


_original_engine = sys.modules.get("localcut_engine")
_original_jobs = sys.modules.get("localcut_jobs")
_original_supervisor = sys.modules.get("localcut_supervisor")

engine = types.ModuleType("localcut_engine")
engine.DEFAULT_CREDENTIALS_FILE = Path("credentials.json")
engine.DEFAULT_ENGINE_DIR = Path(".")
engine.DEFAULT_HOST = "127.0.0.1"
engine.DEFAULT_JOBS_FILE = Path("jobs.sqlite3")
engine.DEFAULT_PORT = 43127
engine.DEFAULT_SPOOL_DIR = Path("spool")
engine.EngineCredentialStore = object
engine.PairingWindow = object
engine.create_server = lambda *a, **k: None
engine.format_pairing_code = lambda x: x
engine.generate_pairing_code = lambda: "23456789ABCD"
engine._validate_token = lambda x: x

jobs = types.ModuleType("localcut_jobs")
jobs.FfprobeMediaAnalyzer = object
jobs.JobStore = object
jobs.MediaSpool = object

try:
    sys.modules["localcut_engine"] = engine
    sys.modules["localcut_jobs"] = jobs
    from localcut_supervisor import InstanceLock
finally:
    if _original_engine is None:
        sys.modules.pop("localcut_engine", None)
    else:
        sys.modules["localcut_engine"] = _original_engine

    if _original_jobs is None:
        sys.modules.pop("localcut_jobs", None)
    else:
        sys.modules["localcut_jobs"] = _original_jobs

    if _original_supervisor is None:
        sys.modules.pop("localcut_supervisor", None)
    else:
        sys.modules["localcut_supervisor"] = _original_supervisor


class InstanceLockTest(unittest.TestCase):
    def test_prevents_duplicate_supervisor_and_releases_cleanly(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "state" / "supervisor.lock"
            first = InstanceLock(path)
            second = InstanceLock(path)
            first.acquire()
            try:
                with self.assertRaisesRegex(RuntimeError, "ENGINE_ALREADY_RUNNING"):
                    second.acquire()
            finally:
                first.release()
            second.acquire()
            second.release()
            self.assertEqual(0o600, path.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
