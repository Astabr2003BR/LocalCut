import assert from "node:assert/strict";
import test from "node:test";
import { BrowserControlSessionService } from "../src/browserControl.ts";

function fixture() {
  let now = 1_000_000;
  let sequence = 0;
  let authorized = true;
  const service = new BrowserControlSessionService({
    signingSecret: "controller-secret-for-browser-control-tests",
    nowEpochMs: () => now,
    randomToken: () => `token_${++sequence}_${"x".repeat(24)}`,
    isDeviceAuthorized: deviceId => authorized && deviceId === "device-1",
  });
  return {
    service,
    advance(ms: number) { now += ms; },
    revoke() { authorized = false; },
  };
}

test("browser authorization is bound to the initiating browser and completes only after device approval", () => {
  const { service } = fixture();
  const pending = service.beginAuthorization("browser-binding-1");

  assert.throws(
    () => service.completeAuthorization(pending.requestId, "browser-binding-1"),
    /REMOTE_AUTH_NOT_APPROVED/,
  );
  assert.throws(
    () => service.approveAuthorization(pending.requestId, "device-2"),
    /REMOTE_DEVICE_UNAUTHORIZED/,
  );

  service.approveAuthorization(pending.requestId, "device-1");
  assert.throws(
    () => service.completeAuthorization(pending.requestId, "browser-binding-2"),
    /REMOTE_BROWSER_BINDING_INVALID/,
  );

  const issued = service.completeAuthorization(pending.requestId, "browser-binding-1");
  assert.equal(issued.deviceId, "device-1");
  assert.ok(issued.sessionToken.length > 40);
  assert.ok(issued.csrfToken.length > 20);
  assert.ok(issued.expiresAtEpochMs > 1_000_000);

  assert.throws(
    () => service.completeAuthorization(pending.requestId, "browser-binding-1"),
    /REMOTE_AUTH_REQUEST_INVALID/,
  );
});

test("signed browser session is scoped to the approved device and validates CSRF", () => {
  const { service } = fixture();
  const pending = service.beginAuthorization("browser-binding-1");
  service.approveAuthorization(pending.requestId, "device-1");
  const issued = service.completeAuthorization(pending.requestId, "browser-binding-1");

  const session = service.verifySession(issued.sessionToken);
  assert.equal(session.deviceId, "device-1");
  assert.equal(session.csrfToken, issued.csrfToken);

  service.assertCsrf(issued.sessionToken, issued.csrfToken);
  assert.throws(() => service.assertCsrf(issued.sessionToken, "wrong"), /REMOTE_CSRF_INVALID/);

  const tampered = `${issued.sessionToken.slice(0, -1)}${issued.sessionToken.endsWith("a") ? "b" : "a"}`;
  assert.throws(() => service.verifySession(tampered), /REMOTE_SESSION_INVALID/);
});

test("authorization requests and browser sessions expire and device revocation invalidates a session", () => {
  const fx = fixture();
  const first = fx.service.beginAuthorization("browser-binding-1");
  fx.advance(6 * 60 * 1_000);
  assert.throws(() => fx.service.approveAuthorization(first.requestId, "device-1"), /REMOTE_AUTH_REQUEST_EXPIRED/);

  const second = fx.service.beginAuthorization("browser-binding-2");
  fx.service.approveAuthorization(second.requestId, "device-1");
  const issued = fx.service.completeAuthorization(second.requestId, "browser-binding-2");

  fx.revoke();
  assert.throws(() => fx.service.verifySession(issued.sessionToken), /REMOTE_DEVICE_UNAUTHORIZED/);
});
