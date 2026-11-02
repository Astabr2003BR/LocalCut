import assert from "node:assert/strict";
import test from "node:test";
import {
  buildTermuxCarrierCommand,
  termuxToolNames,
  termuxToolScope,
} from "../src/termuxMcpProtocol.ts";

test("termux tools expose a fixed allowlist", () => {
  assert.deepEqual(termuxToolNames, [
    "get_termux_workspace_status",
    "list_termux_workspace_files",
    "read_termux_workspace_file",
    "apply_termux_text_patch",
    "run_termux_agent_operation",
    "commit_termux_workspace",
    "push_termux_workspace",
    "get_termux_agent_task",
  ]);
});

test("read and write tools use non-destructive reserved carriers", () => {
  const read = buildTermuxCarrierCommand(
    "read_termux_workspace_file",
    { deviceId: "d1", projectId: "p1", path: "README.md" },
    1_000,
  );
  assert.equal(read.capability, "get_device_status");
  assert.equal(read.expectedRevision, null);
  assert.equal(termuxToolScope("read_termux_workspace_file"), "localcut:read");
  assert.deepEqual(read.payload, {
    localcutTermux: {
      version: 1,
      operation: "READ_WORKSPACE_FILE",
      path: "README.md",
    },
  });

  const write = buildTermuxCarrierCommand(
    "apply_termux_text_patch",
    {
      deviceId: "d1",
      projectId: "p1",
      path: "README.md",
      expectedSha256: "a".repeat(64),
      oldText: "before",
      newText: "after",
    },
    2_000,
  );
  assert.equal(write.capability, "cancel_export");
  assert.equal(write.expectedRevision, null);
  assert.equal(termuxToolScope("apply_termux_text_patch"), "localcut:write");
  assert.equal((write.payload.localcutTermux as Record<string, unknown>).operation, "APPLY_TEXT_PATCH");
});


test("commit and push use dedicated write payloads", () => {
  const commit = buildTermuxCarrierCommand(
    "commit_termux_workspace",
    {
      deviceId: "d1",
      projectId: "p1",
      expectedBranch: "feature/localcut-bridge-v1",
      message: "feat: automatic patch",
      paths: ["README.md"],
    },
    3_000,
  );
  assert.equal(commit.capability, "cancel_export");
  assert.deepEqual(commit.payload, {
    localcutTermux: {
      version: 1,
      operation: "COMMIT_WORKSPACE",
      expectedBranch: "feature/localcut-bridge-v1",
      message: "feat: automatic patch",
      paths: ["README.md"],
    },
  });
  const push = buildTermuxCarrierCommand(
    "push_termux_workspace",
    { deviceId: "d1", projectId: "p1", expectedBranch: "feature/localcut-bridge-v1" },
    4_000,
  );
  assert.equal(push.capability, "cancel_export");
  assert.equal((push.payload.localcutTermux as Record<string, unknown>).operation, "PUSH_WORKSPACE");
});
