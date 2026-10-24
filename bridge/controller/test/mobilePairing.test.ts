import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { PairingService } from "../src/pairing.ts";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

const CLAIM_PREFIX = "LOCALCUT_MOBILE_CLAIM_V1";

function claimMessage(code: string, issuedAtEpochMs: number, nonceBase64: string): Buffer {
  return Buffer.from(`${CLAIM_PREFIX}\n${code}\n${issuedAtEpochMs}\n${nonceBase64}`, "utf8");
}

async function fixture() {
  const store = new InMemoryBridgeStore();
  const pairing = new PairingService(store, {
    codeGenerator: () => "123456",
    challengeGenerator: () => Buffer.from("0123456789abcdef0123456789abcdef"),
  });
  const sessions = new DeviceSessionService(store, new InMemorySessionStore());
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
  return {
    store,
    pairing,
    runtime,
    baseUrl: `http://127.0.0.1:${address.port}`,
    setNow(value: number) { now = value; },
  };
}

async function post(baseUrl: string, path: string, body: unknown) {
  return fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

async function startPairing(baseUrl: string, publicKeySpkiBase64: string) {
  const response = await post(baseUrl, "/v1/pair/start", {
    deviceId: "lc_mobile_device",
    publicKeySpkiBase64,
  });
  assert.equal(response.status, 200);
  return response.json() as Promise<{ code: string }>;
}

test("mobile claim verifies the pending device signature before claiming", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const started = await startPairing(fx.baseUrl, publicKeySpkiBase64);
  const issuedAtEpochMs = 1_000;
  const nonceBase64 = Buffer.from("0123456789abcdef0123456789abcdef").toString("base64");
  const signatureBase64 = sign(
    "sha256",
    claimMessage(started.code, issuedAtEpochMs, nonceBase64),
    privateKey,
  ).toString("base64");

  const response = await post(fx.baseUrl, "/v1/pair/mobile-claim", {
    code: started.code,
    issuedAtEpochMs,
    nonceBase64,
    signatureBase64,
  });
  assert.equal(response.status, 200);
  const body = await response.json() as { claimed: boolean; deviceId: string; challengeBase64: string };
  assert.equal(body.claimed, true);
  assert.equal(body.deviceId, "lc_mobile_device");
  assert.ok(body.challengeBase64);

  const pairing = fx.store.getPairing(started.code);
  assert.equal(pairing?.claimedBy, "controller-1");
  assert.equal(pairing?.challengeBase64, body.challengeBase64);
});

test("mobile claim rejects an invalid device signature without claiming", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const deviceKeys = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const attackerKeys = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = deviceKeys.publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const started = await startPairing(fx.baseUrl, publicKeySpkiBase64);
  const nonceBase64 = Buffer.from("abcdef0123456789abcdef0123456789").toString("base64");
  const signatureBase64 = sign(
    "sha256",
    claimMessage(started.code, 1_000, nonceBase64),
    attackerKeys.privateKey,
  ).toString("base64");

  const response = await post(fx.baseUrl, "/v1/pair/mobile-claim", {
    code: started.code,
    issuedAtEpochMs: 1_000,
    nonceBase64,
    signatureBase64,
  });
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { code: "PAIRING_SIGNATURE_INVALID" });
  assert.equal(fx.store.getPairing(started.code)?.claimedBy, null);
});

test("mobile claim rejects stale timestamps", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const started = await startPairing(fx.baseUrl, publicKeySpkiBase64);
  fx.setNow(70_001);
  const nonceBase64 = Buffer.from("fedcba9876543210fedcba9876543210").toString("base64");
  const signatureBase64 = sign(
    "sha256",
    claimMessage(started.code, 1_000, nonceBase64),
    privateKey,
  ).toString("base64");

  const response = await post(fx.baseUrl, "/v1/pair/mobile-claim", {
    code: started.code,
    issuedAtEpochMs: 1_000,
    nonceBase64,
    signatureBase64,
  });
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { code: "PAIRING_MOBILE_CLAIM_EXPIRED" });
  assert.equal(fx.store.getPairing(started.code)?.claimedBy, null);
});

test("mobile claim rejects replay of the same signed nonce", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const started = await startPairing(fx.baseUrl, publicKeySpkiBase64);
  const nonceBase64 = Buffer.from("00112233445566778899aabbccddeeff").toString("base64");
  const body = {
    code: started.code,
    issuedAtEpochMs: 1_000,
    nonceBase64,
    signatureBase64: sign(
      "sha256",
      claimMessage(started.code, 1_000, nonceBase64),
      privateKey,
    ).toString("base64"),
  };

  const first = await post(fx.baseUrl, "/v1/pair/mobile-claim", body);
  assert.equal(first.status, 200);
  const replay = await post(fx.baseUrl, "/v1/pair/mobile-claim", body);
  assert.equal(replay.status, 400);
  assert.deepEqual(await replay.json(), { code: "PAIRING_MOBILE_CLAIM_REPLAY" });
});
