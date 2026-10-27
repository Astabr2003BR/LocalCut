import { randomBytes, randomUUID } from "node:crypto";
import type { BridgeCommandEnvelope } from "./protocol.ts";

export const termuxToolNames = [
  "get_termux_workspace_status",
  "list_termux_workspace_files",
  "read_termux_workspace_file",
  "apply_termux_text_patch",
  "run_termux_agent_operation",
  "commit_termux_workspace",
  "push_termux_workspace",
  "get_termux_agent_task",
] as const;

export type TermuxToolName = (typeof termuxToolNames)[number];

const TERMUX_MARKER_VERSION = 1;
const COMMAND_TTL_MS = 30_000;

const READ_TOOLS = new Set<TermuxToolName>([
  "get_termux_workspace_status",
  "list_termux_workspace_files",
  "read_termux_workspace_file",
  "get_termux_agent_task",
]);

const OPERATION_BY_TOOL: Record<TermuxToolName, string> = {
  get_termux_workspace_status: "GET_WORKSPACE_STATUS",
  list_termux_workspace_files: "LIST_WORKSPACE_FILES",
  read_termux_workspace_file: "READ_WORKSPACE_FILE",
  apply_termux_text_patch: "APPLY_TEXT_PATCH",
  run_termux_agent_operation: "RUN_AGENT_OPERATION",
  commit_termux_workspace: "COMMIT_WORKSPACE",
  push_termux_workspace: "PUSH_WORKSPACE",
  get_termux_agent_task: "GET_TASK",
};

export function termuxToolScope(tool: TermuxToolName): "localcut:read" | "localcut:write" {
  return READ_TOOLS.has(tool) ? "localcut:read" : "localcut:write";
}

export function buildTermuxCarrierCommand(
  tool: TermuxToolName,
  input: { deviceId: string; projectId: string } & Record<string, unknown>,
  nowEpochMs: number = Date.now(),
): BridgeCommandEnvelope {
  const payload: Record<string, unknown> = {
    localcutTermux: {
      version: TERMUX_MARKER_VERSION,
      operation: OPERATION_BY_TOOL[tool],
      ...termuxPayloadFor(tool, input),
    },
  };
  return {
    protocolVersion: 1,
    messageId: randomUUID(),
    kind: "command",
    deviceId: input.deviceId,
    projectId: input.projectId,
    expectedRevision: null,
    issuedAtEpochMs: nowEpochMs,
    expiresAtEpochMs: nowEpochMs + COMMAND_TTL_MS,
    nonce: randomBytes(18).toString("base64url"),
    capability: READ_TOOLS.has(tool) ? "get_device_status" : "cancel_export",
    payload,
  };
}

function termuxPayloadFor(
  tool: TermuxToolName,
  input: Record<string, unknown>,
): Record<string, unknown> {
  switch (tool) {
    case "get_termux_workspace_status":
      return {};
    case "list_termux_workspace_files":
      return { path: input.path ?? "", limit: input.limit ?? 200 };
    case "read_termux_workspace_file":
      return { path: input.path };
    case "apply_termux_text_patch":
      return {
        path: input.path,
        expectedSha256: input.expectedSha256,
        oldText: input.oldText,
        newText: input.newText,
      };
    case "run_termux_agent_operation":
      return { agentOperation: input.operation };
    case "commit_termux_workspace":
      return {
        expectedBranch: input.expectedBranch,
        message: input.message,
        paths: input.paths,
      };
    case "push_termux_workspace":
      return { expectedBranch: input.expectedBranch };
    case "get_termux_agent_task":
      return { taskId: input.taskId };
  }
}
