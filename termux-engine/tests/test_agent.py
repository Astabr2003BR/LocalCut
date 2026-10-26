import os
from pathlib import Path
import tempfile
import unittest

from localcut_agent import ApprovalStore, DeveloperAgentPolicy, SafeAgentExecutor, TermuxNotificationSink


class AgentPolicyTest(unittest.TestCase):
    def test_safe_operations_are_typed_and_critical_are_gated(self):
        with tempfile.TemporaryDirectory() as td:
            policy = DeveloperAgentPolicy(td)
            self.assertEqual("SAFE", policy.classify("GIT_STATUS"))
            self.assertEqual("REQUIRES_APPROVAL", policy.classify("MERGE_MAIN"))
            with self.assertRaisesRegex(ValueError, "AGENT_OPERATION_REQUIRES_APPROVAL"):
                policy.command_for("MERGE_MAIN")
            with self.assertRaisesRegex(ValueError, "AGENT_OPERATION_NOT_ALLOWED"):
                policy.classify("EXEC")

    def test_protected_and_unknown_branches_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            policy = DeveloperAgentPolicy(td)
            self.assertEqual("feature/localcut-x", policy.validate_branch("feature/localcut-x"))
            with self.assertRaisesRegex(ValueError, "AGENT_PROTECTED_BRANCH"):
                policy.validate_branch("main")
            with self.assertRaisesRegex(ValueError, "AGENT_BRANCH_NOT_ALLOWED"):
                policy.validate_branch("random")

    def test_executor_does_not_inherit_secret_like_environment(self):
        with tempfile.TemporaryDirectory() as td:
            DeveloperAgentPolicy(td)
            old = os.environ.get("SECRET_TOKEN")
            os.environ["SECRET_TOKEN"] = "must-not-leak"
            try:
                env = SafeAgentExecutor._safe_env()
                self.assertNotIn("SECRET_TOKEN", env)
            finally:
                if old is None:
                    os.environ.pop("SECRET_TOKEN", None)
                else:
                    os.environ["SECRET_TOKEN"] = old


class ApprovalStoreTest(unittest.TestCase):
    def test_critical_request_is_persistent_idempotent_and_requires_explicit_decision(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "state" / "approvals.sqlite3"
            store = ApprovalStore(path)
            first = store.request("job-1", "MERGE_MAIN", {"branch": "feature/x"}, {"commit": "abc123"})
            second = store.request("job-1", "MERGE_MAIN", {"branch": "feature/x"}, {"commit": "abc123"})
            self.assertEqual(first["id"], second["id"])
            self.assertEqual("PENDING", first["status"])
            self.assertEqual(1, len(store.pending()))
            reopened = ApprovalStore(path)
            self.assertEqual(first["id"], reopened.pending()[0]["id"])
            denied = reopened.resolve(first["id"], "DENY")
            self.assertEqual("DENIED", denied["status"])
            self.assertEqual([], reopened.pending())
            self.assertEqual(0o600, path.stat().st_mode & 0o777)

    def test_noncritical_operation_cannot_create_approval(self):
        with tempfile.TemporaryDirectory() as td:
            store = ApprovalStore(Path(td) / "approvals.sqlite3")
            with self.assertRaisesRegex(ValueError, "APPROVAL_NOT_REQUIRED"):
                store.request("job-2", "GIT_STATUS", {}, {})


class NotificationTest(unittest.TestCase):
    def test_notification_uses_fixed_argv_without_shell(self):
        calls = []

        class Result:
            returncode = 0

        def runner(argv, **kwargs):
            calls.append((argv, kwargs))
            return Result()

        sink = TermuxNotificationSink(binary="termux-notification", runner=runner)
        ok = sink.approval_required({"operation": "MERGE_MAIN"})
        self.assertTrue(ok)
        argv, kwargs = calls[0]
        self.assertEqual("termux-notification", argv[0])
        self.assertFalse(kwargs["shell"])
        self.assertNotIn("sh", argv[:1])


if __name__ == "__main__":
    unittest.main()
