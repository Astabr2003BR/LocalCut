import assert from "node:assert/strict";
import test from "node:test";
import { CommandRouter, DeviceRegistry } from "../src/commandRouter.ts";
import { bridgeCapabilities } from "../src/protocol.ts";
import { InMemoryBridgeStore } from "../src/store.ts";
import {
  buildToolInputSchema,
  controllerManagementToolNames,
  controllerToolNames,
  securityScopesForCapability,
  toolResultForCapability,
} from "../src/mcp.ts";

test("MCP exposes only allowlisted LocalCut edit tools plus explicit management tools", () => {
  assert.deepEqual(controllerToolNames().sort(), [...bridgeCapabilities].sort());
  assert.deepEqual(
    controllerManagementToolNames().sort(),
    ["claim_pairing", "discover_active_contexts", "revoke_device"].sort(),
  );
});

test("MCP OAuth scopes classify reads separately from editor actions", () => {
  assert.deepEqual(securityScopesForCapability("get_project_state"), ["localcut:read"]);
  assert.deepEqual(securityScopesForCapability("get_preview_frame"), ["localcut:read"]);
  assert.deepEqual(securityScopesForCapability("seek"), ["localcut:write"]);
  assert.deepEqual(securityScopesForCapability("move_clip"), ["localcut:write"]);
  assert.deepEqual(securityScopesForCapability("export_project"), ["localcut:write"]);
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

test("active context discovery probes the live paired device and returns routing metadata", async () => {
  const module = await import("../src/mcp.ts");
  type DiscoverActiveContexts = (
    router: CommandRouter,
    registry: DeviceRegistry,
    controllerId: string,
    nowEpochMs?: () => number,
  ) => Promise<unknown>;
  const discover = (module as unknown as { discoverActiveContexts?: DiscoverActiveContexts })
    .discoverActiveContexts;

  assert.equal(typeof discover, "function");
  if (!discover) return;

  const store = new InMemoryBridgeStore();
  store.upsertDevice({
    deviceId: "device-1",
    publicKeySpkiBase64: "test-key",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const registry = new DeviceRegistry();
  registry.register("device-1", {
    close() {},
    async send(command) {
      assert.equal(command.capability, "get_device_status");
      assert.equal(command.projectId, "__localcut_active_project__");
      return {
        messageId: command.messageId,
        success: true,
        code: null,
        projectRevision: 5,
        result: {
          state: "READY",
          projectId: "project-1",
          revision: 5,
        },
        retryable: false,
      };
    },
  });
  const router = new CommandRouter(store, registry, { timeoutMs: 1_000 });

  const result = await discover(router, registry, "controller-1", () => 1_000);

  assert.deepEqual(result, {
    contexts: [
      {
        deviceId: "device-1",
        projectId: "project-1",
        projectRevision: 5,
        state: "READY",
      },
    ],
  });
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
