import assert from "node:assert/strict";
import test from "node:test";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

function fixture() {
  const devices = new InMemoryBridgeStore();
  devices.upsertDevice({
    deviceId: "lc_device",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const sessions = new InMemorySessionStore();
  const service = new DeviceSessionService(devices, sessions, {
    tokenGenerator: (() => {
      const values = ["access-token", "refresh-token", "access-token-2", "refresh-token-2"];
      return () => values.shift() ?? "fallback-token";
    })(),
  });
  return { devices, sessions, service };
}

test("issued raw tokens are authenticatable but store keeps only hashes", () => {
  const { sessions, service } = fixture();
  const issued = service.issue("lc_device", 1_000);

  assert.equal(issued.accessToken, "access-token");
  assert.equal(issued.refreshToken, "refresh-token");
  assert.equal(service.authenticateAccess("access-token", 2_000)?.deviceId, "lc_device");
  const stored = sessions.getByDeviceId("lc_device")!;
  assert.notEqual(stored.accessTokenHash, "access-token");
  assert.notEqual(stored.refreshTokenHash, "refresh-token");
});

test("expired access token and revoked device are rejected", () => {
  const { devices, service } = fixture();
  const issued = service.issue("lc_device", 1_000);

  assert.equal(service.authenticateAccess(issued.accessToken, issued.accessExpiresAtEpochMs + 1), null);
  devices.revokeDevice("lc_device", 2_000);
  assert.equal(service.authenticateAccess(issued.accessToken, 2_001), null);
});

test("refresh rotates both tokens and invalidates old access token", () => {
  const { service } = fixture();
  const first = service.issue("lc_device", 1_000);
  const second = service.refresh(first.refreshToken, 2_000);

  assert.equal(second.accessToken, "access-token-2");
  assert.equal(service.authenticateAccess(first.accessToken, 2_001), null);
  assert.equal(service.authenticateAccess(second.accessToken, 2_001)?.deviceId, "lc_device");
});

test("unknown refresh token is rejected", () => {
  const { service } = fixture();
  assert.throws(() => service.refresh("unknown", 2_000), /REFRESH_TOKEN_INVALID/);
});
