import assert from "node:assert/strict";
import test from "node:test";
import { bridgeCapabilities } from "../src/protocol.ts";
import {
  buildToolInputSchema,
  controllerManagementToolNames,
  controllerToolNames,
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
