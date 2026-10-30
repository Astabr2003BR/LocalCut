import type { McpServer } from "@modelcontextprotocol/server";
import * as z from "zod/v4";
import type { CommandRouter } from "./commandRouter.ts";
import {
  buildTermuxCarrierCommand,
  termuxToolNames,
  termuxToolScope,
  type TermuxToolName,
} from "./termuxMcpProtocol.ts";

const routing = {
  deviceId: z.string().min(1).max(128),
  projectId: z.string().min(1).max(256),
};

const branch = z.string().regex(/^(?:feature|fix|chore|test)\/[A-Za-z0-9._/-]{1,180}$/);
const workspacePath = z.string().min(1).max(512).refine(value => {
  const normalized = value.replaceAll("\\", "/");
  return !normalized.startsWith("/") && !normalized.split("/").some(part => !part || part === "." || part === "..");
}, "path must stay inside workspace");

const agentOperations = z.enum([
  "GIT_STATUS",
  "GIT_DIFF",
  "RUN_TERMUX_TESTS",
  "RUN_ANDROID_UNIT_TESTS",
  "BUILD_DEBUG_APK",
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
]);

const schemas = {
  get_termux_workspace_status: z.object({ ...routing }).strict(),
  list_termux_workspace_files: z.object({
    ...routing,
    path: z.string().max(512).default(""),
    limit: z.number().int().min(1).max(500).default(200),
  }).strict(),
  read_termux_workspace_file: z.object({
    ...routing,
    path: z.string().min(1).max(512),
  }).strict(),
  apply_termux_text_patch: z.object({
    ...routing,
    path: z.string().min(1).max(512),
    expectedSha256: z.string().regex(/^[0-9a-f]{64}$/),
    oldText: z.string().min(1).max(12_000),
    newText: z.string().max(12_000),
  }).strict(),
  run_termux_agent_operation: z.object({
    ...routing,
    operation: agentOperations,
  }).strict(),
  commit_termux_workspace: z.object({
    ...routing,
    expectedBranch: branch,
    message: z.string().min(1).max(160).refine(value => !/[\u0000-\u001f]/.test(value), "message contains control characters"),
    paths: z.array(workspacePath).min(1).max(50),
  }).strict(),
  push_termux_workspace: z.object({
    ...routing,
    expectedBranch: branch,
  }).strict(),
  get_termux_agent_task: z.object({
    ...routing,
    taskId: z.string().regex(/^[A-Za-z0-9._:-]{8,128}$/),
  }).strict(),
} satisfies Record<TermuxToolName, z.ZodType>;

const descriptions: Record<TermuxToolName, string> = {
  get_termux_workspace_status: "Read the LocalCut Termux workspace branch, HEAD and dirty state through the paired Android device.",
  list_termux_workspace_files: "List bounded non-sensitive files inside the authorized LocalCut Termux workspace.",
  read_termux_workspace_file: "Read one bounded UTF-8 file from the authorized LocalCut Termux workspace, including its SHA-256 revision.",
  apply_termux_text_patch: "Apply one exact SHA-256-guarded text replacement to an authorized LocalCut workspace file. Protected branches, secrets and paths outside the workspace are rejected.",
  run_termux_agent_operation: "Run one typed LocalCut development operation in Termux. Critical operations never execute directly and become WAITING_APPROVAL with an Android/Termux notification.",
  commit_termux_workspace: "Commit only an explicit allowlisted set of changed workspace files on the current protected-safe feature/fix/chore/test branch.",
  push_termux_workspace: "Push the current allowed LocalCut feature/fix/chore/test branch to origin without force.",
  get_termux_agent_task: "Read the current state/result of a previously submitted LocalCut Termux task.",
};

export function registerTermuxTools(
  server: McpServer,
  router: CommandRouter,
  controllerId: string,
): void {
  for (const tool of termuxToolNames) {
    const schema = schemas[tool];
    const scope = termuxToolScope(tool);
    server.registerTool(
      tool,
      {
        title: `LocalCut: ${tool.replaceAll("_", " ")}`,
        description: descriptions[tool],
        inputSchema: schema,
        annotations: {
          readOnlyHint: scope === "localcut:read",
          destructiveHint: tool === "run_termux_agent_operation",
          openWorldHint: false,
        },
        _meta: {
          securitySchemes: [{ type: "oauth2", scopes: [scope] }],
        },
      },
      async (input: unknown) => {
        const parsed = schema.parse(input) as { deviceId: string; projectId: string } & Record<string, unknown>;
        const command = buildTermuxCarrierCommand(tool, parsed);
        const result = await router.execute(controllerId, command);
        return termuxToolResult(result, !result.success);
      },
    );
  }
}

function termuxToolResult(value: unknown, isError: boolean) {
  const structured = isRecord(value) ? value : { value };
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value) }],
    structuredContent: structured,
    isError,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
