from __future__ import annotations

from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys
import time
import uuid

PROJECT_ID = "localcut"
ALLOWED_REPOSITORY = "Astabr2003BR/LocalCut"
APPROVAL_STATUSES = {"WAITING_APPROVAL", "APPROVED", "DENIED"}
CRITICAL_OPERATIONS = {
    "MERGE_MAIN",
    "FORCE_PUSH",
    "DELETE_PROJECT",
    "DELETE_REPOSITORY",
    "DELETE_DATABASE",
    "DELETE_PRODUCTION_DATA",
    "DROP_DATABASE",
    "DROP_TABLE",
    "CHANGE_APK_SIGNING",
    "CHANGE_KEYSTORE",
    "ALTER_SECRETS",
    "REVOKE_CREDENTIALS",
    "PUBLISH_PLAY_STORE",
    "PUBLISH_PRODUCTION_RELEASE",
    "OVERWRITE_CRITICAL_BACKUP",
    "DESTRUCTIVE_MIGRATION",
}
SAFE_OPERATIONS = {
    "GIT_STATUS",
    "GIT_DIFF",
    "RUN_TERMUX_TESTS",
    "RUN_ANDROID_UNIT_TESTS",
    "BUILD_DEBUG_APK",
}
SECRET_KEYS = {
    "token", "secret", "password", "authorization", "apikey", "api_key",
    "keystorepassword", "keystore_password", "privatekey", "private_key",
}
OP_RE = re.compile(r"^[A-Z0-9_]{3,96}$")
BRANCH_RE = re.compile(r"^[A-Za-z0-9._/-]{1,200}$")
MAX_CAPTURE_BYTES = 1_048_576
MAX_CONTEXT_DEPTH = 5
MAX_CONTEXT_ITEMS = 200
MAX_CONTEXT_STRING = 2048


def _now_ms() -> int:
    return int(time.time() * 1000)


def _json(value) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def _sanitize_value(value, depth: int = 0):
    if depth > MAX_CONTEXT_DEPTH:
        return "[TRUNCATED]"
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        return value[:MAX_CONTEXT_STRING]
    if isinstance(value, dict):
        clean = {}
        for index, (key, item) in enumerate(value.items()):
            if index >= MAX_CONTEXT_ITEMS:
                break
            if not isinstance(key, str) or len(key) > 120:
                continue
            normalized = key.replace("-", "_").lower()
            if normalized in SECRET_KEYS:
                continue
            clean[key] = _sanitize_value(item, depth + 1)
        return clean
    if isinstance(value, (list, tuple)):
        return [_sanitize_value(item, depth + 1) for item in list(value)[:MAX_CONTEXT_ITEMS]]
    return str(value)[:MAX_CONTEXT_STRING]


class DeveloperAgentPolicy:
    def __init__(self, workspace: str | Path, repository: str = ALLOWED_REPOSITORY):
        if repository != ALLOWED_REPOSITORY:
            raise ValueError("AGENT_REPOSITORY_NOT_ALLOWED")
        self.workspace = Path(workspace).expanduser().resolve()
        self.repository = repository
        if not self.workspace.is_dir():
            raise ValueError("AGENT_WORKSPACE_INVALID")

    def classify(self, operation: str) -> str:
        if not isinstance(operation, str) or not OP_RE.fullmatch(operation):
            raise ValueError("AGENT_OPERATION_INVALID")
        if operation in CRITICAL_OPERATIONS:
            return "REQUIRES_APPROVAL"
        if operation in SAFE_OPERATIONS:
            return "SAFE"
        raise ValueError("AGENT_OPERATION_NOT_ALLOWED")

    def command_for(self, operation: str) -> list[str]:
        if self.classify(operation) != "SAFE":
            raise ValueError("AGENT_OPERATION_REQUIRES_APPROVAL")
        commands = {
            "GIT_STATUS": ["git", "status", "--short", "--branch"],
            "GIT_DIFF": ["git", "diff", "--", "."],
            "RUN_TERMUX_TESTS": [sys.executable, "-m", "unittest", "discover", "-s", "termux-engine/tests", "-p", "test_*.py"],
            "RUN_ANDROID_UNIT_TESTS": ["./gradlew", "testDebugUnitTest"],
            "BUILD_DEBUG_APK": ["./gradlew", "assembleDebug"],
        }
        return list(commands[operation])

    def validate_branch(self, branch: str) -> str:
        if not isinstance(branch, str) or not BRANCH_RE.fullmatch(branch):
            raise ValueError("AGENT_BRANCH_INVALID")
        if branch in {"main", "master"}:
            raise ValueError("AGENT_PROTECTED_BRANCH")
        if not branch.startswith(("feature/", "fix/", "chore/", "test/")):
            raise ValueError("AGENT_BRANCH_NOT_ALLOWED")
        return branch


class SafeAgentExecutor:
    def __init__(self, policy: DeveloperAgentPolicy, timeout_seconds: int = 900):
        self.policy = policy
        self.timeout_seconds = int(timeout_seconds)
        if not (1 <= self.timeout_seconds <= 3600):
            raise ValueError("AGENT_TIMEOUT_INVALID")

    def execute(self, operation: str) -> dict:
        argv = self.policy.command_for(operation)
        env = self._safe_env()
        try:
            completed = subprocess.run(
                argv,
                cwd=self.policy.workspace,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=False,
                timeout=self.timeout_seconds,
                check=False,
                shell=False,
                env=env,
            )
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError("AGENT_OPERATION_TIMEOUT") from exc
        except OSError as exc:
            raise RuntimeError("AGENT_OPERATION_EXEC_FAILED") from exc
        return {
            "operation": operation,
            "exitCode": completed.returncode,
            "stdout": completed.stdout[:MAX_CAPTURE_BYTES].decode("utf-8", errors="replace"),
            "stderr": completed.stderr[:MAX_CAPTURE_BYTES].decode("utf-8", errors="replace"),
            "truncated": len(completed.stdout) > MAX_CAPTURE_BYTES or len(completed.stderr) > MAX_CAPTURE_BYTES,
        }

    @staticmethod
    def _safe_env() -> dict[str, str]:
        allowed = {
            "PATH", "HOME", "TMPDIR", "LANG", "LC_ALL", "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT",
        }
        return {key: value for key, value in os.environ.items() if key in allowed}


class ApprovalStore:
    def __init__(self, path: str | Path):
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path.parent, 0o700)
        self._initialize()

    @contextmanager
    def _connect(self):
        connection = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        try:
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("PRAGMA synchronous=FULL")
            yield connection
        finally:
            connection.close()

    def _initialize(self):
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS approvals (
                    id TEXT PRIMARY KEY,
                    job_id TEXT NOT NULL UNIQUE,
                    project_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    status TEXT NOT NULL,
                    requested_at INTEGER NOT NULL,
                    resolved_at INTEGER,
                    payload_json TEXT NOT NULL,
                    checkpoint_json TEXT NOT NULL
                )
                """
            )
        os.chmod(self.path, 0o600)

    def request(self, job_id: str, operation: str, payload: dict, checkpoint: dict) -> dict:
        if not isinstance(job_id, str) or len(job_id) > 128 or not job_id:
            raise ValueError("APPROVAL_JOB_ID_INVALID")
        if operation not in CRITICAL_OPERATIONS:
            raise ValueError("APPROVAL_NOT_REQUIRED")
        if not isinstance(payload, dict) or not isinstance(checkpoint, dict):
            raise ValueError("APPROVAL_PAYLOAD_INVALID")
        safe_payload = _sanitize_value(payload)
        safe_checkpoint = _sanitize_value(checkpoint)
        with self._connect() as connection:
            existing = connection.execute("SELECT * FROM approvals WHERE job_id=?", (job_id,)).fetchone()
            if existing is not None:
                return self._row(existing)
            approval_id = str(uuid.uuid4())
            connection.execute(
                "INSERT INTO approvals(id,job_id,project_id,operation,status,requested_at,resolved_at,payload_json,checkpoint_json) VALUES(?,?,?,?,?,?,?,?,?)",
                (
                    approval_id,
                    job_id,
                    PROJECT_ID,
                    operation,
                    "WAITING_APPROVAL",
                    _now_ms(),
                    None,
                    _json(safe_payload),
                    _json(safe_checkpoint),
                ),
            )
            row = connection.execute("SELECT * FROM approvals WHERE id=?", (approval_id,)).fetchone()
            return self._row(row)

    def pending(self) -> list[dict]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM approvals WHERE status='WAITING_APPROVAL' ORDER BY requested_at ASC"
            ).fetchall()
        return [self._row(row) for row in rows]

    def resolve(self, approval_id: str, decision: str) -> dict:
        if decision not in {"APPROVE", "DENY"}:
            raise ValueError("APPROVAL_DECISION_INVALID")
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute("SELECT * FROM approvals WHERE id=?", (approval_id,)).fetchone()
            if row is None:
                connection.execute("ROLLBACK")
                raise KeyError("APPROVAL_NOT_FOUND")
            current = self._row(row)
            if current["status"] != "WAITING_APPROVAL":
                connection.execute("COMMIT")
                return current
            status = "APPROVED" if decision == "APPROVE" else "DENIED"
            connection.execute("UPDATE approvals SET status=?, resolved_at=? WHERE id=?", (status, _now_ms(), approval_id))
            updated = connection.execute("SELECT * FROM approvals WHERE id=?", (approval_id,)).fetchone()
            connection.execute("COMMIT")
            return self._row(updated)

    @staticmethod
    def _row(row: sqlite3.Row) -> dict:
        return {
            "id": row["id"],
            "jobId": row["job_id"],
            "projectId": row["project_id"],
            "operation": row["operation"],
            "status": row["status"],
            "requestedAt": row["requested_at"],
            "resolvedAt": row["resolved_at"],
            "payload": json.loads(row["payload_json"]),
            "checkpoint": json.loads(row["checkpoint_json"]),
        }


class TermuxNotificationSink:
    def __init__(self, binary: str | None = None, runner=subprocess.run):
        self.binary = shutil.which("termux-notification") if binary is None else binary
        self.runner = runner

    @property
    def available(self) -> bool:
        return bool(self.binary)

    def approval_required(self, approval: dict) -> bool:
        if not self.binary:
            return False
        title = "LocalCut — aprovação necessária"
        content = f"{approval['operation']} • abra o LocalCut para aprovar ou negar"
        argv = [self.binary, "--id", "localcut-approval", "--title", title, "--content", content]
        try:
            result = self.runner(
                argv,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=10,
                check=False,
                shell=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            return False
        return getattr(result, "returncode", 1) == 0


class DeveloperAgentController:
    def __init__(
        self,
        policy: DeveloperAgentPolicy,
        executor: SafeAgentExecutor,
        approval_store: ApprovalStore,
        notifier: TermuxNotificationSink | None = None,
    ):
        self.policy = policy
        self.executor = executor
        self.approval_store = approval_store
        self.notifier = notifier

    def request(
        self,
        job_id: str,
        operation: str,
        *,
        payload: dict | None = None,
        checkpoint: dict | None = None,
    ) -> dict:
        classification = self.policy.classify(operation)
        if classification == "SAFE":
            return {"status": "READY", "result": self.executor.execute(operation)}
        approval = self.approval_store.request(job_id, operation, payload or {}, checkpoint or {})
        if self.notifier is not None:
            self.notifier.approval_required(approval)
        return {"status": "WAITING_APPROVAL", "approval": approval}
