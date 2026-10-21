import assert from "node:assert/strict";
import test from "node:test";
import { CommandRouter, DeviceRegistry, type DeviceChannel } from "../src/commandRouter.ts";
import type { BridgeCommandEnvelope, BridgeResultEnvelope } from "../src/protocol.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

function command(messageId = "msg-1"): BridgeCommandEnvelope {
  return {
    protocolVersion: 1,
    messageId,
    kind: "command",
    deviceId: "lc_device",
    projectId: "project-1",
    expectedRevision: null,
    issuedAtEpochMs: 1_000,
    expiresAtEpochMs: 31_000,
    nonce: `nonce-${messageId}`,
    capability: "get_project_state",
    payload: {},
  };
}

function success(messageId: string): BridgeResultEnvelope {
  return {
    messageId,
    success: true,
    code: null,
    projectRevision: 7,
    result: { projectId: "project-1" },
    retryable: false,
  };
}

function pairedStore() {
  const store = new InMemoryBridgeStore();
  store.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  return store;
}

test("offline device returns stable DEVICE_OFFLINE result", async () => {
  const router = new CommandRouter(pairedStore(), new DeviceRegistry());

  const result = await router.execute("controller-1", command());

  assert.equal(result.success, false);
  assert.equal(result.code, "DEVICE_OFFLINE");
  assert.equal(result.retryable, true);
});

test("same message id is executed once and reuses original result", async () => {
  let sends = 0;
  const registry = new DeviceRegistry();
  registry.register("lc_device", {
    async send(cmd) {
      sends += 1;
      return success(cmd.messageId);
    },
    close() {},
  });
  const router = new CommandRouter(pairedStore(), registry);

  const first = await router.execute("controller-1", command("same"));
  const retry = await router.execute("controller-1", command("same"));

  assert.equal(first.messageId, "same");
  assert.deepEqual(retry, first);
  assert.equal(sends, 1);
});

test("mismatched response correlation is rejected", async () => {
  const registry = new DeviceRegistry();
  registry.register("lc_device", {
    async send() {
      return success("wrong-id");
    },
    close() {},
  });
  const router = new CommandRouter(pairedStore(), registry);

  const result = await router.execute("controller-1", command("expected-id"));

  assert.equal(result.success, false);
  assert.equal(result.code, "INTERNAL_ERROR");
});

test("command timeout is bounded and retryable", async () => {
  const registry = new DeviceRegistry();
  registry.register("lc_device", {
    async send() {
      return await new Promise<BridgeResultEnvelope>(() => {});
    },
    close() {},
  });
  const router = new CommandRouter(pairedStore(), registry, { timeoutMs: 10 });

  const result = await router.execute("controller-1", command("timeout"));

  assert.equal(result.success, false);
  assert.equal(result.code, "DEVICE_OFFLINE");
  assert.equal(result.retryable, true);
});

test("one active device session replaces and closes the old channel", () => {
  const registry = new DeviceRegistry();
  let firstClosed = 0;
  const first: DeviceChannel = { async send(cmd) { return success(cmd.messageId); }, close() { firstClosed += 1; } };
  const second: DeviceChannel = { async send(cmd) { return success(cmd.messageId); }, close() {} };

  registry.register("lc_device", first);
  registry.register("lc_device", second);

  assert.equal(firstClosed, 1);
  assert.equal(registry.get("lc_device"), second);
});

test("registry shutdown closes every active device route once and clears routing", () => {
  const registry = new DeviceRegistry();
  const closed: string[] = [];
  const shared: DeviceChannel = {
    async send(cmd) { return success(cmd.messageId); },
    close(reason) { closed.push(`shared:${reason ?? ""}`); },
  };
  const other: DeviceChannel = {
    async send(cmd) { return success(cmd.messageId); },
    close(reason) { closed.push(`other:${reason ?? ""}`); },
  };

  registry.register("lc_device", shared);
  registry.register("lc_device_alias", shared);
  registry.register("lc_other", other);
  registry.closeAll("controller_shutdown");

  assert.deepEqual(closed.sort(), [
    "other:controller_shutdown",
    "shared:controller_shutdown",
  ]);
  assert.equal(registry.get("lc_device"), null);
  assert.equal(registry.get("lc_device_alias"), null);
  assert.equal(registry.get("lc_other"), null);
});

test("controller cannot route to a device it does not own or that is revoked", async () => {
  const store = pairedStore();
  const registry = new DeviceRegistry();
  registry.register("lc_device", { async send(cmd) { return success(cmd.messageId); }, close() {} });
  const router = new CommandRouter(store, registry);

  const forbidden = await router.execute("other-controller", command("forbidden"));
  assert.equal(forbidden.code, "AUTH_REQUIRED");

  store.revokeDevice("lc_device", 5_000);
  const revoked = await router.execute("controller-1", command("revoked"));
  assert.equal(revoked.code, "AUTH_REQUIRED");
});
