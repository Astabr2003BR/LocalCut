from __future__ import annotations

import hmac
import json
import re
import socketserver

from localcut_agent import ApprovalStore, DeveloperAgentPolicy, SafeAgentExecutor, TermuxNotificationSink
from localcut_workspace import WorkspaceAccess

PROTOCOL_VERSION = 1
PROJECT_ID = "localcut"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_AGENT_PORT = 43128
MAX_REQUEST_BYTES = 65_536
TASK_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{8,128}$")
TOKEN_MIN_LENGTH = 32
TOKEN_MAX_LENGTH = 256


def _validate_token(token: str) -> str:
    if not isinstance(token, str):
        raise ValueError("ENGINE_TOKEN_INVALID")
    token = token.strip()
    if not (TOKEN_MIN_LENGTH <= len(token) <= TOKEN_MAX_LENGTH):
        raise ValueError("ENGINE_TOKEN_INVALID")
    if not all(ch.isalnum() or ch in "-._~" for ch in token):
        raise ValueError("ENGINE_TOKEN_INVALID")
    return token


def _response(ok: bool, request_id: str | None, **fields) -> bytes:
    payload = {"ok": ok, "protocolVersion": PROTOCOL_VERSION}
    if request_id:
        payload["requestId"] = request_id
    payload.update(fields)
    return (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


class LocalCutAgentRpcServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, token: str, policy: DeveloperAgentPolicy,
                 executor: SafeAgentExecutor, approvals: ApprovalStore,
                 notifications: TermuxNotificationSink):
        self.auth_token = _validate_token(token)
        self.policy = policy
        self.executor = executor
        self.approvals = approvals
        self.notifications = notifications
        self.workspace = WorkspaceAccess(policy.workspace)
        super().__init__(address, handler, bind_and_activate=True)

    def capabilities(self) -> list[str]:
        return [
            "PING_AGENT",
            "GET_AGENT_CAPABILITIES",
            "GET_WORKSPACE_STATUS",
            "LIST_WORKSPACE_FILES",
            "READ_WORKSPACE_FILE",
            "APPLY_TEXT_PATCH",
            "RUN_AGENT_OPERATION",
            "GET_PENDING_APPROVALS",
            "RESOLVE_APPROVAL",
        ]


class LocalCutAgentRpcHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        raw = self.rfile.readline(MAX_REQUEST_BYTES + 1)
        if not raw or len(raw) > MAX_REQUEST_BYTES or not raw.endswith(b"\n"):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return
        try:
            request = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return
        if not isinstance(request, dict):
            self.wfile.write(_response(False, None, code="REQUEST_INVALID"))
            return
        request_id = request.get("requestId")
        if not isinstance(request_id, str) or not TASK_ID_RE.fullmatch(request_id):
            self.wfile.write(_response(False, None, code="REQUEST_ID_INVALID"))
            return
        if request.get("protocolVersion") != PROTOCOL_VERSION:
            self.wfile.write(_response(False, request_id, code="PROTOCOL_MISMATCH"))
            return
        if request.get("projectId") != PROJECT_ID:
            self.wfile.write(_response(False, request_id, code="PROJECT_MISMATCH"))
            return
        token = request.get("token")
        if not isinstance(token, str) or not hmac.compare_digest(token, self.server.auth_token):
            self.wfile.write(_response(False, request_id, code="AUTH_INVALID"))
            return

        operation = request.get("operation")
        try:
            if operation == "PING_AGENT":
                self.wfile.write(_response(True, request_id, status="READY"))
            elif operation == "GET_AGENT_CAPABILITIES":
                self.wfile.write(_response(True, request_id, capabilities=self.server.capabilities()))
            elif operation == "GET_WORKSPACE_STATUS":
                self.wfile.write(_response(True, request_id, workspace=self.server.workspace.status()))
            elif operation == "LIST_WORKSPACE_FILES":
                prefix = request.get("path", "")
                limit = request.get("limit", 200)
                self.wfile.write(_response(True, request_id, **self.server.workspace.list_files(prefix, limit)))
            elif operation == "READ_WORKSPACE_FILE":
                file_result = self.server.workspace.read_file(request.get("path"))
                self.wfile.write(_response(True, request_id, file=file_result))
            elif operation == "APPLY_TEXT_PATCH":
                file_result = self.server.workspace.apply_text_patch(
                    request.get("path"),
                    request.get("expectedSha256"),
                    request.get("oldText"),
                    request.get("newText"),
                )
                self.wfile.write(_response(True, request_id, file=file_result))
            elif operation == "RUN_AGENT_OPERATION":
                self._run_agent_operation(request_id, request)
            elif operation == "GET_PENDING_APPROVALS":
                self.wfile.write(_response(True, request_id, approvals=self.server.approvals.pending()))
            elif operation == "RESOLVE_APPROVAL":
                approval = self.server.approvals.resolve(request.get("approvalId"), request.get("decision"))
                self.wfile.write(_response(True, request_id, approval=approval))
            else:
                self.wfile.write(_response(False, request_id, code="OPERATION_NOT_ALLOWED"))
        except (ValueError, KeyError, RuntimeError) as error:
            code = str(error)
            if not re.fullmatch(r"[A-Z0-9_]{3,96}", code):
                code = "AGENT_OPERATION_FAILED"
            self.wfile.write(_response(False, request_id, code=code))

    def _run_agent_operation(self, request_id: str, request: dict) -> None:
        agent_operation = request.get("agentOperation")
        classification = self.server.policy.classify(agent_operation)
        payload = request.get("input", {})
        if not isinstance(payload, dict):
            raise ValueError("AGENT_INPUT_INVALID")
        if classification == "SAFE":
            result = self.server.executor.execute(agent_operation, payload) if payload else self.server.executor.execute(agent_operation)
            status = "SUCCEEDED" if result["exitCode"] == 0 else "FAILED"
            self.wfile.write(_response(True, request_id, status=status, result=result))
            return

        task_id = request.get("taskId")
        if not isinstance(task_id, str) or not TASK_ID_RE.fullmatch(task_id):
            raise ValueError("AGENT_TASK_ID_INVALID")
        approval = self.server.approvals.request(
            task_id,
            agent_operation,
            payload,
            {"repository": self.server.policy.repository, "projectId": PROJECT_ID},
        )
        notified = self.server.notifications.approval_required(approval)
        self.wfile.write(_response(
            True,
            request_id,
            status="WAITING_APPROVAL",
            approval=approval,
            notificationSent=notified,
        ))


def create_agent_server(host: str, port: int, token: str, policy: DeveloperAgentPolicy,
                        executor: SafeAgentExecutor, approvals: ApprovalStore,
                        notifications: TermuxNotificationSink) -> LocalCutAgentRpcServer:
    if host != DEFAULT_HOST:
        raise ValueError("ENGINE_BIND_LOOPBACK_ONLY")
    if not (1 <= int(port) <= 65535):
        raise ValueError("ENGINE_PORT_INVALID")
    return LocalCutAgentRpcServer(
        (host, int(port)), LocalCutAgentRpcHandler, token, policy, executor, approvals, notifications,
    )
