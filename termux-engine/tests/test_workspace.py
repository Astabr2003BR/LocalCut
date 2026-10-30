import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_workspace import WorkspaceAccess


class WorkspaceAccessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-b", "feature/termux-workspace"], cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Test"], cwd=self.root, check=True)
        subprocess.run(["git", "remote", "add", "origin", "https://github.com/Astabr2003BR/LocalCut.git"], cwd=self.root, check=True)
        (self.root / "src").mkdir()
        (self.root / "src" / "demo.txt").write_text("alpha\nbeta\n", encoding="utf-8")
        (self.root / ".env").write_text("SECRET=yes\n", encoding="utf-8")
        subprocess.run(["git", "add", "src/demo.txt"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-m", "init"], cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        self.access = WorkspaceAccess(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def test_list_and_read_hide_sensitive_files(self):
        listed = self.access.list_files(limit=50)
        paths = [item["path"] for item in listed["files"]]
        self.assertIn("src/demo.txt", paths)
        self.assertNotIn(".env", paths)
        read = self.access.read_file("src/demo.txt")
        self.assertEqual("alpha\nbeta\n", read["content"])
        self.assertEqual(hashlib.sha256(b"alpha\nbeta\n").hexdigest(), read["sha256"])
        with self.assertRaisesRegex(ValueError, "WORKSPACE_PATH_DENIED"):
            self.access.read_file(".env")

    def test_traversal_and_symlink_escape_are_denied(self):
        with self.assertRaisesRegex(ValueError, "WORKSPACE_PATH_INVALID"):
            self.access.read_file("../outside.txt")
        outside = self.root.parent / f"outside-{os.getpid()}.txt"
        outside.write_text("nope", encoding="utf-8")
        try:
            (self.root / "src" / "link.txt").symlink_to(outside)
            with self.assertRaisesRegex(ValueError, "WORKSPACE_PATH_OUTSIDE|WORKSPACE_SYMLINK_DENIED"):
                self.access.read_file("src/link.txt")
        finally:
            outside.unlink(missing_ok=True)

    def test_patch_requires_hash_and_unique_target(self):
        current = self.access.read_file("src/demo.txt")
        changed = self.access.apply_text_patch("src/demo.txt", current["sha256"], "beta", "gamma")
        self.assertTrue(changed["changed"])
        self.assertEqual("alpha\ngamma\n", (self.root / "src" / "demo.txt").read_text())
        with self.assertRaisesRegex(ValueError, "WORKSPACE_REVISION_CONFLICT"):
            self.access.apply_text_patch("src/demo.txt", current["sha256"], "gamma", "delta")
        (self.root / "src" / "demo.txt").write_text("x\nx\n", encoding="utf-8")
        current = self.access.read_file("src/demo.txt")
        with self.assertRaisesRegex(ValueError, "WORKSPACE_PATCH_TARGET_AMBIGUOUS"):
            self.access.apply_text_patch("src/demo.txt", current["sha256"], "x", "y")

    def test_protected_branch_blocks_write(self):
        subprocess.run(["git", "checkout", "-b", "main"], cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        current = self.access.read_file("src/demo.txt")
        with self.assertRaisesRegex(ValueError, "WORKSPACE_PROTECTED_BRANCH"):
            self.access.apply_text_patch("src/demo.txt", current["sha256"], "beta", "gamma")

    def test_wrong_repository_blocks_write(self):
        subprocess.run(["git", "remote", "set-url", "origin", "https://github.com/example/Other.git"], cwd=self.root, check=True)
        current = self.access.read_file("src/demo.txt")
        with self.assertRaisesRegex(ValueError, "WORKSPACE_REPOSITORY_MISMATCH"):
            self.access.apply_text_patch("src/demo.txt", current["sha256"], "beta", "gamma")

    def test_status_reports_branch_head_and_dirty(self):
        status = self.access.status()
        self.assertEqual("feature/termux-workspace", status["branch"])
        self.assertEqual(40, len(status["head"]))
        self.assertTrue(status["dirty"])  # .env is intentionally untracked


if __name__ == "__main__":
    unittest.main()
