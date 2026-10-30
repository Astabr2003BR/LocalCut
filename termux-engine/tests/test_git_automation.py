from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_agent import DeveloperAgentPolicy, SafeAgentExecutor


class GitAutomationTest(unittest.TestCase):
    def _init_repo(self, root: Path, branch: str = "feature/auto") -> None:
        subprocess.run(["git", "init", "-b", branch], cwd=root, check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=root, check=True)
        subprocess.run(["git", "config", "user.name", "Test"], cwd=root, check=True)
        subprocess.run(["git", "remote", "add", "origin", "https://github.com/Astabr2003BR/LocalCut.git"], cwd=root, check=True)
        (root / "demo.txt").write_text("before\n", encoding="utf-8")
        subprocess.run(["git", "add", "demo.txt"], cwd=root, check=True)
        subprocess.run(["git", "commit", "-m", "init"], cwd=root, check=True, stdout=subprocess.DEVNULL)

    def test_commit_only_explicit_file_on_allowed_branch(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self._init_repo(root)
            (root / "demo.txt").write_text("after\n", encoding="utf-8")
            (root / "other.txt").write_text("untracked\n", encoding="utf-8")
            executor = SafeAgentExecutor(DeveloperAgentPolicy(root))

            result = executor.execute("GIT_COMMIT", {
                "expectedBranch": "feature/auto",
                "message": "test: automatic patch",
                "paths": ["demo.txt"],
            })

            self.assertEqual(0, result["exitCode"])
            changed = subprocess.check_output(
                ["git", "show", "--name-only", "--pretty=format:", "HEAD"],
                cwd=root,
                text=True,
            ).splitlines()
            self.assertEqual(["demo.txt"], [item for item in changed if item])
            self.assertTrue((root / "other.txt").exists())

    def test_commit_rejects_protected_branch_and_path_traversal(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self._init_repo(root, "main")
            executor = SafeAgentExecutor(DeveloperAgentPolicy(root))
            with self.assertRaisesRegex(ValueError, "AGENT_PROTECTED_BRANCH"):
                executor.execute("GIT_COMMIT", {
                    "expectedBranch": "main",
                    "message": "test",
                    "paths": ["demo.txt"],
                })

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self._init_repo(root)
            executor = SafeAgentExecutor(DeveloperAgentPolicy(root))
            with self.assertRaisesRegex(ValueError, "AGENT_PATH_INVALID"):
                executor.execute("GIT_COMMIT", {
                    "expectedBranch": "feature/auto",
                    "message": "test",
                    "paths": ["../secret"],
                })

    def test_push_is_nonforce_and_targets_expected_feature_branch(self):
        calls = []

        class Result:
            returncode = 0
            stdout = b""
            stderr = b""

        def runner(argv, **kwargs):
            calls.append((list(argv), kwargs))
            if argv == ["git", "branch", "--show-current"]:
                return type("R", (), {"returncode": 0, "stdout": b"feature/auto\n", "stderr": b""})()
            if argv == ["git", "remote", "get-url", "origin"]:
                return type("R", (), {"returncode": 0, "stdout": b"https://github.com/Astabr2003BR/LocalCut.git\n", "stderr": b""})()
            return Result()

        with tempfile.TemporaryDirectory() as td:
            executor = SafeAgentExecutor(DeveloperAgentPolicy(td), runner=runner)
            result = executor.execute("GIT_PUSH", {"expectedBranch": "feature/auto"})
            self.assertEqual(0, result["exitCode"])

        push_calls = [call for call in calls if call[0][:2] == ["git", "push"]]
        self.assertEqual(1, len(push_calls))
        argv, kwargs = push_calls[0]
        self.assertEqual(["git", "push", "origin", "HEAD:refs/heads/feature/auto"], argv)
        self.assertNotIn("--force", argv)
        self.assertFalse(kwargs["shell"])


if __name__ == "__main__":
    unittest.main()
