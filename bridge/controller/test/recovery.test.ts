import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { PairingService } from "../src/pairing.ts";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

const RECOVERY_PREFIX = "LOCALCUT_SESSION_RECOVERY_V1";

async function createFixture(nowEpochMs = 1_000) {
  const store = new InMemoryBridgeStore();
  const pairing = new PairingService(store, {
    codeGenerator: () => "503901",
    challengeGenerator: () => Buffer.from("0123456789abcdef0123456789abcdef"),
  });
  const sessions = new DeviceSessionService(store, new InMemorySessionStore());
  let now = nowEpochMs;
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
  if (!address || typeof address === "string") throw new Error("missing test address");
  return {
    store,
    sessions,
    runtime,
    baseUrl: `http://127.0.0.1:${address.port}`,
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

async function pairDevice(baseUrl: string) {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const deviceId = "lc_background_device";

  const started = await post(baseUrl, "/v1/pair/start", { deviceId, publicKeySpkiBase64 });
  assert.equal(started.status, 200);
  const code = (await started.json() as { code: string }).code;

  const claimed = await post(baseUrl, "/v1/pair/claim", { code }, "controller-secret");
  assert.equal(claimed.status, 200);

  const status = await post(baseUrl, "/v1/pair/status", { code });
  const statusJson = await status.json() as { challengeBase64: string };
  const signatureBase64 = sign(
    "sha256",
    Buffer.from(statusJson.challengeBase64, "base64"),
    privateKey,
  ).toString("base64");

  const completed = await post(baseUrl, "/v1/pair/status", { code, signatureBase64 });
  assert.equal(completed.status, 200);
  const credential = await completed.json() as {
    accessToken: string;
    refreshToken: string;
    recoveryToken?: string;
  };
  return { deviceId, publicKeySpkiBase64, privateKey, credential };
}

test("paired session includes a durable recovery token", async t => {
  const fx = await createFixture();
  t.after(() => fx.runtime.close());

  const paired = await pairDevice(fx.baseUrl);

  assert.equal(typeof paired.credential.recoveryToken, "string");
  assert.ok((paired.credential.recoveryToken ?? "").length >= 32);
});

test("device can recover automatically after the relay loses its ephemeral sqlite state", async t => {
  const before = await createFixture();
  t.after(() => before.runtime.close());
  const paired = await pairDevice(before.baseUrl);
  const recoveryToken = paired.credential.recoveryToken;
  assert.equal(typeof recoveryToken, "string");

  const after = await createFixture(2_000);
  t.after(() => after.runtime.close());

  const nonceBase64 = Buffer.alloc(32, 9).toString("base64");
  const payload = `${RECOVERY_PREFIX}\n${paired.deviceId}\n2000\n${nonceBase64}\n${recoveryToken}`;
  const signatureBase64 = sign("sha256", Buffer.from(payload, "utf8"), paired.privateKey).toString("base64");

  const recovered = await post(after.baseUrl, "/v1/session/recover", {
    deviceId: paired.deviceId,
    publicKeySpkiBase64: paired.publicKeySpkiBase64,
    recoveryToken,
    issuedAtEpochMs: 2_000,
    nonceBase64,
    signatureBase64,
  });

  assert.equal(recovered.status, 200);
  const body = await recovered.json() as {
    accessToken: string;
    refreshToken: string;
    recoveryToken: string;
  };
  assert.ok(body.accessToken.length >= 8);
  assert.ok(body.refreshToken.length >= 8);
  assert.ok(body.recoveryToken.length >= 32);
  assert.equal(after.store.getDevice(paired.deviceId)?.controllerId, "controller-1");
  assert.equal(after.sessions.authenticateAccess(body.accessToken, 2_000)?.deviceId, paired.deviceId);
});

test("recovery rejects a tampered recovery token", async t => {
  const before = await createFixture();
  t.after(() => before.runtime.close());
  const paired = await pairDevice(before.baseUrl);
  const original = paired.credential.recoveryToken;
  assert.equal(typeof original, "string");

  const after = await createFixture(2_000);
  t.after(() => after.runtime.close());
  const recoveryToken = `${original}tampered`;
  const nonceBase64 = Buffer.alloc(32, 7).toString("base64");
  const payload = `${RECOVERY_PREFIX}\n${paired.deviceId}\n2000\n${nonceBase64}\n${recoveryToken}`;
  const signatureBase64 = sign("sha256", Buffer.from(payload, "utf8"), paired.privateKey).toString("base64");

  const recovered = await post(after.baseUrl, "/v1/session/recover", {
    deviceId: paired.deviceId,
    publicKeySpkiBase64: paired.publicKeySpkiBase64,
    recoveryToken,
    issuedAtEpochMs: 2_000,
    nonceBase64,
    signatureBase64,
  });

  assert.notEqual(recovered.status, 200);
  assert.equal(after.store.getDevice(paired.deviceId), null);
});
