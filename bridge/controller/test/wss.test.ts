import assert from "node:assert/strict";
import test from "node:test";
import WebSocket from "ws";
import { CommandRouter, DeviceRegistry } from "../src/commandRouter.ts";
import type { BridgeCommandEnvelope } from "../src/protocol.ts";
import { InMemorySessionStore, DeviceSessionService } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { PairingService } from "../src/pairing.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

async function fixture() {
  const store = new InMemoryBridgeStore();
  store.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const sessionStore = new InMemorySessionStore();
  const sessions = new DeviceSessionService(store, sessionStore, {
    tokenGenerator: (() => {
      const values = ["access-token-123", "refresh-token-123"];
      return () => values.shift() ?? "fallback-token-123";
    })(),
  });
  const issued = sessions.issue("lc_device", 1_000);
  const registry = new DeviceRegistry();
  const router = new CommandRouter(store, registry, { timeoutMs: 2_000 });
  const runtime = createControllerServer({
    controllerApiToken: "controller-secret",
    controllerId: "controller-1",
    store,
    pairing: new PairingService(store),
    sessions,
    registry,
    router,
    nowEpochMs: () => 2_000,
  });
  await runtime.listen(0, "127.0.0.1");
  const address = runtime.address();
  if (!address || typeof address === "string") throw new Error("missing address");
  return {
    store,
    sessions,
    registry,
    router,
    runtime,
    issued,
    wsUrl: `ws://127.0.0.1:${address.port}/v1/device/connect`,
  };
}

function command(messageId: string): BridgeCommandEnvelope {
  return {
    protocolVersion: 1,
    messageId,
    kind: "command",
    deviceId: "lc_device",
    projectId: "project-1",
    expectedRevision: null,
    issuedAtEpochMs: 2_000,
    expiresAtEpochMs: 32_000,
    nonce: `nonce-${messageId}`,
    capability: "get_project_state",
    payload: {},
  };
}

function openSocket(url: string, token: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url, { headers: { authorization: `Bearer ${token}` } });
    ws.once("open", () => resolve(ws));
    ws.once("error", reject);
  });
}

test("authenticated WSS device receives routed command and returns result", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const ws = await openSocket(fx.wsUrl, fx.issued.accessToken);
  t.after(() => ws.close());

  ws.on("message", raw => {
    const incoming = JSON.parse(raw.toString()) as BridgeCommandEnvelope;
    ws.send(JSON.stringify({
      protocolVersion: 1,
      messageId: incoming.messageId,
      success: true,
      code: null,
      projectRevision: 9,
      retryable: false,
      result: { projectId: incoming.projectId, revision: 9 },
    }));
  });

  const result = await fx.router.execute("controller-1", command("msg-1"));
  assert.equal(result.success, true);
  assert.equal(result.projectRevision, 9);
  assert.deepEqual(result.result, { projectId: "project-1", revision: 9 });
});

test("WSS rejects missing or invalid access token", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  await assert.rejects(openSocket(fx.wsUrl, "invalid-token"));
});

test("revoking device closes active route and rejects later controller commands", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const ws = await openSocket(fx.wsUrl, fx.issued.accessToken);
  t.after(() => ws.close());

  fx.store.revokeDevice("lc_device", 3_000);
  fx.sessions.revoke("lc_device");
  fx.registry.get("lc_device")?.close("revoked");

  const result = await fx.router.execute("controller-1", command("after-revoke"));
  assert.equal(result.success, false);
  assert.equal(result.code, "AUTH_REQUIRED");
});
