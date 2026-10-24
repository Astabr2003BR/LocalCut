import assert from "node:assert/strict";
import test from "node:test";
import { bridgeCapabilities } from "../src/protocol.ts";
import {
  buildToolInputSchema,
  controllerManagementToolNames,
  controllerToolNames,
  toolResultForCapability,
} from "../src/mcp.ts";

test("MCP exposes only allowlisted LocalCut edit tools plus explicit management tools", () => {
  assert.deepEqual(controllerToolNames().sort(), [...bridgeCapabilities].sort());
  assert.deepEqual(controllerManagementToolNames().sort(), ["claim_pairing", "revoke_device"].sort());
});

test("project writes and export require expectedRevision", () => {
  const move = buildToolInputSchema("move_clip");
  const exportProject = buildToolInputSchema("export_project");

  assert.equal(move.safeParse({ deviceId: "lc", projectId: "p", payload: {} }).success, false);
  assert.equal(exportProject.safeParse({ deviceId: "lc", projectId: "p", payload: {} }).success, false);
  assert.equal(
    move.safeParse({ deviceId: "lc", projectId: "p", expectedRevision: 7, payload: {} }).success,
    true,
  );
});

test("read tools do not invent a revision precondition", () => {
  const read = buildToolInputSchema("get_project_state");
  const parsed = read.safeParse({ deviceId: "lc", projectId: "p", payload: {} });
  assert.equal(parsed.success, true);
});

test("tool schema rejects blank routing identifiers and negative revisions", () => {
  const move = buildToolInputSchema("move_clip");
  assert.equal(
    move.safeParse({ deviceId: "", projectId: "p", expectedRevision: 1, payload: {} }).success,
    false,
  );
  assert.equal(
    move.safeParse({ deviceId: "lc", projectId: "p", expectedRevision: -1, payload: {} }).success,
    false,
  );
});

test("preview frame tool result includes MCP image content and keeps structured metadata", () => {
  const value = {
    messageId: "m1",
    success: true,
    code: null,
    projectRevision: 9,
    result: {
      mimeType: "image/jpeg",
      base64: "AQIDBA==",
      width: 640,
      height: 360,
      positionUs: 2_500_000,
    },
    retryable: false,
  };

  const result = toolResultForCapability("get_preview_frame", value, false);

  assert.equal(result.isError, false);
  assert.deepEqual(result.structuredContent, value);
  assert.equal(result.content[0].type, "text");
  assert.deepEqual(result.content[1], {
    type: "image",
    data: "AQIDBA==",
    mimeType: "image/jpeg",
  });
});

test("preview frame does not emit image content for unsupported mime or failed result", () => {
  const unsupported = toolResultForCapability(
    "get_preview_frame",
    { success: true, result: { mimeType: "text/plain", base64: "abc" } },
    false,
  );
  const failed = toolResultForCapability(
    "get_preview_frame",
    { success: false, result: { mimeType: "image/jpeg", base64: "AQID" } },
    true,
  );

  assert.equal(unsupported.content.length, 1);
  assert.equal(failed.content.length, 1);
});
