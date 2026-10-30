import hashlib
import json
import socket
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_agent import ApprovalStore, DeveloperAgentPolicy
from localcut_agent_rpc import create_agent_server


class FakeExecutor:
    def __init__(self):
        self.calls = []

    def execute(self, operation):
        self.calls.append(operation)
        return {"operation": operation, "exitCode": 0, "stdout": "ok", "stderr": "", "truncated": False}


class FakeNotifications:
    def __init__(self):
        self.calls = []

    def approval_required(self, approval):
        self.calls.append(approval)
        return True


class AgentRpcTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-b", "feature/agent-rpc"], cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Test"], cwd=self.root, check=True)
        subprocess.run(["git", "remote", "add", "origin", "https://github.com/Astabr2003BR/LocalCut.git"], cwd=self.root, check=True)
        (self.root / "demo.txt").write_text("before\n", encoding="utf-8")
        subprocess.run(["git", "add", "demo.txt"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-m", "init"], cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        self.token = "t" * 43
        self.policy = DeveloperAgentPolicy(self.temp.name)
        self.executor = FakeExecutor()
        self.notifications = FakeNotifications()
        self.approvals = ApprovalStore(self.root / "approvals.sqlite3")
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        self.port = probe.getsockname()[1]
        probe.close()
        self.server = create_agent_server(
            "127.0.0.1", self.port, self.token, self.policy, self.executor, self.approvals, self.notifications,
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    def exchange(self, operation, **fields):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "request-12345678",
            "token": self.token,
            "operation": operation,
        }
        payload.update(fields)
        with socket.create_connection(("127.0.0.1", self.port), timeout=2) as sock:
            sock.sendall((json.dumps(payload, separators=(",", ":")) + "\n").encode())
            return json.loads(sock.makefile("rb").readline().decode())

    def test_safe_operation_executes_only_typed_handler(self):
        result = self.exchange("RUN_AGENT_OPERATION", agentOperation="GIT_STATUS")
        self.assertTrue(result["ok"])
        self.assertEqual("SUCCEEDED", result["status"])
        self.assertEqual(["GIT_STATUS"], self.executor.calls)

    def test_workspace_read_and_patch_are_typed_and_revision_checked(self):
        caps = self.exchange("GET_AGENT_CAPABILITIES")
        self.assertIn("READ_WORKSPACE_FILE", caps["capabilities"])
        self.assertIn("APPLY_TEXT_PATCH", caps["capabilities"])
        status = self.exchange("GET_WORKSPACE_STATUS")
        self.assertEqual("feature/agent-rpc", status["workspace"]["branch"])
        read = self.exchange("READ_WORKSPACE_FILE", path="demo.txt")
        self.assertTrue(read["ok"])
        digest = hashlib.sha256(b"before\n").hexdigest()
        self.assertEqual(digest, read["file"]["sha256"])
        patched = self.exchange(
            "APPLY_TEXT_PATCH",
            path="demo.txt",
            expectedSha256=digest,
            oldText="before",
            newText="after",
        )
        self.assertTrue(patched["ok"])
        self.assertEqual("after\n", (self.root / "demo.txt").read_text())
        stale = self.exchange(
            "APPLY_TEXT_PATCH",
            path="demo.txt",
            expectedSha256=digest,
            oldText="after",
            newText="again",
        )
        self.assertFalse(stale["ok"])
        self.assertEqual("WORKSPACE_REVISION_CONFLICT", stale["code"])

    def test_workspace_path_escape_and_secrets_fail_closed(self):
        (self.root / ".env").write_text("SECRET=yes\n", encoding="utf-8")
        traversal = self.exchange("READ_WORKSPACE_FILE", path="../outside.txt")
        self.assertFalse(traversal["ok"])
        self.assertEqual("WORKSPACE_PATH_INVALID", traversal["code"])
        secret = self.exchange("READ_WORKSPACE_FILE", path=".env")
        self.assertFalse(secret["ok"])
        self.assertEqual("WORKSPACE_PATH_DENIED", secret["code"])

    def test_critical_operation_waits_for_approval_and_does_not_execute(self):
        result = self.exchange(
            "RUN_AGENT_OPERATION",
            agentOperation="MERGE_MAIN",
            taskId="task-12345678",
            input={"branch": "feature/localcut-x"},
        )
        self.assertTrue(result["ok"])
        self.assertEqual("WAITING_APPROVAL", result["status"])
        self.assertEqual([], self.executor.calls)
        self.assertEqual(1, len(self.notifications.calls))
        pending = self.exchange("GET_PENDING_APPROVALS")
        self.assertEqual(1, len(pending["approvals"]))
        approval_id = pending["approvals"][0]["id"]
        denied = self.exchange("RESOLVE_APPROVAL", approvalId=approval_id, decision="DENY")
        self.assertEqual("DENIED", denied["approval"]["status"])

    def test_arbitrary_exec_and_bad_auth_fail_closed(self):
        result = self.exchange("RUN_AGENT_OPERATION", agentOperation="EXEC")
        self.assertFalse(result["ok"])
        self.assertEqual("AGENT_OPERATION_NOT_ALLOWED", result["code"])
        bad = self.exchange("PING_AGENT", token="x" * 43)
        self.assertFalse(bad["ok"])
        self.assertEqual("AUTH_INVALID", bad["code"])

    def test_non_loopback_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "ENGINE_BIND_LOOPBACK_ONLY"):
            create_agent_server(
                "0.0.0.0", 43128, self.token, self.policy, self.executor, self.approvals, self.notifications,
            )


if __name__ == "__main__":
    unittest.main()
