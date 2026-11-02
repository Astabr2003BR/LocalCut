import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { InMemoryBridgeStore } from "../src/store.ts";
import { PairingService } from "../src/pairing.ts";

function fixture() {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const store = new InMemoryBridgeStore();
  const pairing = new PairingService(store, {
    codeGenerator: () => "123456",
    challengeGenerator: () => Buffer.from("0123456789abcdef0123456789abcdef", "utf8"),
  });
  return { store, pairing, privateKey, publicKeySpkiBase64 };
}

test("pairing code expires after ten minutes and can be claimed only once", () => {
  const { pairing, publicKeySpkiBase64 } = fixture();
  const started = pairing.start("lc_device", publicKeySpkiBase64, 1_000);
  assert.equal(started.code, "123456");
  assert.equal(started.expiresAtEpochMs, 601_000);

  const claimed = pairing.claim("123456", "controller-1", 2_000);
  assert.equal(claimed.deviceId, "lc_device");
  assert.ok(claimed.challengeBase64.length > 10);
  assert.throws(() => pairing.claim("123456", "controller-1", 3_000), /PAIRING_ALREADY_CLAIMED/);

  const expired = new PairingService(new InMemoryBridgeStore(), {
    codeGenerator: () => "654321",
    challengeGenerator: () => Buffer.alloc(32, 7),
  });
  expired.start("lc_other", publicKeySpkiBase64, 1_000);
  assert.throws(() => expired.claim("654321", "controller-1", 601_001), /PAIRING_EXPIRED/);
});

test("device challenge must verify against registered P-256 public key", () => {
  const { pairing, privateKey, publicKeySpkiBase64 } = fixture();
  pairing.start("lc_device", publicKeySpkiBase64, 1_000);
  const claimed = pairing.claim("123456", "controller-1", 2_000);
  const challenge = Buffer.from(claimed.challengeBase64, "base64");
  const signature = sign("sha256", challenge, privateKey).toString("base64");

  const completed = pairing.complete("123456", signature, 3_000);
  assert.equal(completed.deviceId, "lc_device");
  assert.equal(completed.controllerId, "controller-1");
  assert.equal(pairing.status("123456", 3_000).state, "PAIRED");
});

test("invalid signature is rejected without completing pairing", () => {
  const { pairing, publicKeySpkiBase64 } = fixture();
  pairing.start("lc_device", publicKeySpkiBase64, 1_000);
  pairing.claim("123456", "controller-1", 2_000);

  assert.throws(
    () => pairing.complete("123456", Buffer.from("bad-signature").toString("base64"), 3_000),
    /PAIRING_SIGNATURE_INVALID/,
  );
  assert.equal(pairing.status("123456", 3_000).state, "CLAIMED");
});

test("revoked device cannot create a new pairing", () => {
  const { store, pairing, publicKeySpkiBase64 } = fixture();
  store.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64,
    revokedAtEpochMs: 500,
  });

  assert.throws(() => pairing.start("lc_device", publicKeySpkiBase64, 1_000), /DEVICE_REVOKED/);
});
