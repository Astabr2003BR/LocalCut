import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { PairingService } from "../src/pairing.ts";
import { InMemorySessionStore, DeviceSessionService } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

async function fixture() {
  const store = new InMemoryBridgeStore();
  const pairing = new PairingService(store, {
    codeGenerator: () => "123456",
    challengeGenerator: () => Buffer.from("0123456789abcdef0123456789abcdef"),
  });
  const sessions = new DeviceSessionService(store, new InMemorySessionStore(), {
    tokenGenerator: (() => {
      const values = ["access-token-123", "refresh-token-123"];
      return () => values.shift() ?? "fallback-token-123";
    })(),
  });
  let now = 1_000;
  const runtime = createControllerServer({
    controllerApiToken: "controller-secret",
    controllerId: "controller-1",
    store,
    pairing,
    sessions,
    nowEpochMs: () => now,
  });
  await runtime.listen(0, "127.0.0.1");
  const address = runtime.address();
  if (!address || typeof address === "string") throw new Error("missing test server address");
  const baseUrl = `http://127.0.0.1:${address.port}`;
  return {
    store,
    pairing,
    sessions,
    runtime,
    baseUrl,
    setNow(value: number) { now = value; },
  };
}

async function post(baseUrl: string, path: string, body: unknown, token?: string) {
  return fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
}

test("health endpoint is public and controller claim is authenticated", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  const health = await fetch(`${fx.baseUrl}/health`);
  assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { ok: true, service: "localcut-bridge-controller" });

  const unauthorized = await post(fx.baseUrl, "/v1/pair/claim", { code: "123456" });
  assert.equal(unauthorized.status, 401);
});

test("HTTP pairing flow exposes challenge then issues device session after valid signature", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");

  const started = await post(fx.baseUrl, "/v1/pair/start", {
    deviceId: "lc_device",
    publicKeySpkiBase64,
  });
  assert.equal(started.status, 200);
  assert.equal((await started.json() as { code: string }).code, "123456");

  const claimed = await post(
    fx.baseUrl,
    "/v1/pair/claim",
    { code: "123456" },
    "controller-secret",
  );
  assert.equal(claimed.status, 200);

  const status = await post(fx.baseUrl, "/v1/pair/status", { code: "123456" });
  const statusJson = await status.json() as { state: string; challengeBase64: string };
  assert.equal(statusJson.state, "CLAIMED");
  assert.ok(statusJson.challengeBase64);

  const signatureBase64 = sign(
    "sha256",
    Buffer.from(statusJson.challengeBase64, "base64"),
    privateKey,
  ).toString("base64");
  const completed = await post(fx.baseUrl, "/v1/pair/status", {
    code: "123456",
    signatureBase64,
  });
  assert.equal(completed.status, 200);
  const completedJson = await completed.json() as {
    state: string;
    accessToken: string;
    refreshToken: string;
  };
  assert.equal(completedJson.state, "PAIRED");
  assert.equal(completedJson.accessToken, "access-token-123");
  assert.equal(completedJson.refreshToken, "refresh-token-123");
});

test("session refresh endpoint rotates credential and rejects unknown refresh token", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  fx.store.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const first = fx.sessions.issue("lc_device", 1_000);

  const refreshed = await post(fx.baseUrl, "/v1/session/refresh", {
    refreshToken: first.refreshToken,
  });
  assert.equal(refreshed.status, 200);

  const invalid = await post(fx.baseUrl, "/v1/session/refresh", { refreshToken: "not-valid" });
  assert.equal(invalid.status, 401);
});

test("device self-unpair requires its access token, invalidates sessions, and permits later re-pair", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  fx.store.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const issued = fx.sessions.issue("lc_device", 1_000);

  const unauthorized = await post(fx.baseUrl, "/v1/device/unpair-self", {});
  assert.equal(unauthorized.status, 401);

  const unpaired = await post(
    fx.baseUrl,
    "/v1/device/unpair-self",
    {},
    issued.accessToken,
  );
  assert.equal(unpaired.status, 200);
  assert.deepEqual(await unpaired.json(), { unpaired: true, deviceId: "lc_device" });

  const device = fx.store.getDevice("lc_device");
  assert.equal(device?.controllerId, null);
  assert.equal(device?.revokedAtEpochMs, null, "self-unpair must not permanently revoke the device identity");
  assert.equal(fx.sessions.authenticateAccess(issued.accessToken, 1_000), null);
  assert.throws(() => fx.sessions.refresh(issued.refreshToken, 1_000), /REFRESH_TOKEN_INVALID/);

  const restarted = fx.pairing.start("lc_device", "AQID", 2_000);
  assert.equal(restarted.deviceId, "lc_device");
});
