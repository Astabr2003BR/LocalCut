import assert from "node:assert/strict";
import test from "node:test";
import { LocalCutOAuthService, pkceS256Challenge } from "../src/oauth.ts";

const ISSUER = "https://localcut.example";
const RESOURCE = `${ISSUER}/mcp`;
const CLIENT_ID = "localcut-chatgpt";
const REDIRECT_URI = "https://chatgpt.com/connector_platform_oauth_redirect";
const VERIFIER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~abc";

function fixture() {
  let now = 1_000_000;
  let sequence = 0;
  const service = new LocalCutOAuthService({
    issuerBaseUrl: ISSUER,
    signingSecret: "controller-secret-for-tests",
    nowEpochMs: () => now,
    randomToken: () => `token_${++sequence}_${"x".repeat(24)}`,
    isDeviceAuthorized: deviceId => deviceId === "device-1",
  });
  return {
    service,
    advance(ms: number) { now += ms; },
  };
}

function begin(service: LocalCutOAuthService) {
  return service.beginAuthorization({
    responseType: "code",
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    state: "state-123",
    codeChallenge: pkceS256Challenge(VERIFIER),
    codeChallengeMethod: "S256",
    resource: RESOURCE,
    scope: "localcut:read localcut:write",
  });
}

test("paired device approval completes authorization-code PKCE and yields scoped access", () => {
  const { service } = fixture();
  const pending = begin(service);
  service.approveAuthorization(pending.requestId, "device-1");
  const completed = service.completeAuthorization(pending.requestId);

  assert.equal(completed.state, "state-123");
  assert.equal(completed.redirectUri, REDIRECT_URI);

  const tokens = service.exchangeAuthorizationCode({
    grantType: "authorization_code",
    code: completed.code,
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    codeVerifier: VERIFIER,
    resource: RESOURCE,
  });
  assert.equal(tokens.tokenType, "Bearer");
  assert.equal(tokens.scope, "localcut:read localcut:write");
  assert.ok(tokens.accessToken.length > 20);
  assert.ok(tokens.refreshToken.length > 20);

  const claims = service.verifyAccessToken(tokens.accessToken, ["localcut:read"]);
  assert.equal(claims.deviceId, "device-1");
  assert.deepEqual(claims.scopes, ["localcut:read", "localcut:write"]);
});

test("authorization code is single-use and rejects a wrong PKCE verifier", () => {
  const { service } = fixture();
  const first = begin(service);
  service.approveAuthorization(first.requestId, "device-1");
  const firstCode = service.completeAuthorization(first.requestId).code;
  assert.throws(() => service.exchangeAuthorizationCode({
    grantType: "authorization_code",
    code: firstCode,
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    codeVerifier: `${VERIFIER}wrong`,
    resource: RESOURCE,
  }), /OAUTH_PKCE_INVALID/);

  const second = begin(service);
  service.approveAuthorization(second.requestId, "device-1");
  const secondCode = service.completeAuthorization(second.requestId).code;
  service.exchangeAuthorizationCode({
    grantType: "authorization_code",
    code: secondCode,
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    codeVerifier: VERIFIER,
    resource: RESOURCE,
  });
  assert.throws(() => service.exchangeAuthorizationCode({
    grantType: "authorization_code",
    code: secondCode,
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    codeVerifier: VERIFIER,
    resource: RESOURCE,
  }), /OAUTH_CODE_INVALID/);
});

test("pending authorization expires and only a currently authorized device can approve", () => {
  const fx = fixture();
  const pending = begin(fx.service);
  assert.throws(() => fx.service.approveAuthorization(pending.requestId, "device-2"), /OAUTH_DEVICE_UNAUTHORIZED/);

  fx.advance(6 * 60 * 1_000);
  assert.throws(() => fx.service.approveAuthorization(pending.requestId, "device-1"), /OAUTH_REQUEST_EXPIRED/);
});

test("authorization request fails closed for unsupported redirect resource scope and PKCE mode", () => {
  const { service } = fixture();
  const base = {
    responseType: "code",
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    state: "state-123",
    codeChallenge: pkceS256Challenge(VERIFIER),
    codeChallengeMethod: "S256",
    resource: RESOURCE,
    scope: "localcut:read localcut:write",
  };
  assert.throws(() => service.beginAuthorization({ ...base, redirectUri: "https://evil.example/callback" }), /OAUTH_REDIRECT_INVALID/);
  assert.throws(() => service.beginAuthorization({ ...base, resource: "https://evil.example/mcp" }), /OAUTH_RESOURCE_INVALID/);
  assert.throws(() => service.beginAuthorization({ ...base, scope: "localcut:admin" }), /OAUTH_SCOPE_INVALID/);
  assert.throws(() => service.beginAuthorization({ ...base, codeChallengeMethod: "plain" }), /OAUTH_PKCE_REQUIRED/);
});

test("refresh token renews access and device revocation invalidates both access and refresh", () => {
  let authorized = true;
  let now = 1_000_000;
  let sequence = 0;
  const service = new LocalCutOAuthService({
    issuerBaseUrl: ISSUER,
    signingSecret: "controller-secret-for-tests",
    nowEpochMs: () => now,
    randomToken: () => `token_${++sequence}_${"x".repeat(24)}`,
    isDeviceAuthorized: deviceId => authorized && deviceId === "device-1",
  });
  const pending = begin(service);
  service.approveAuthorization(pending.requestId, "device-1");
  const code = service.completeAuthorization(pending.requestId).code;
  const issued = service.exchangeAuthorizationCode({
    grantType: "authorization_code",
    code,
    clientId: CLIENT_ID,
    redirectUri: REDIRECT_URI,
    codeVerifier: VERIFIER,
    resource: RESOURCE,
  });

  now += 10_000;
  const refreshed = service.exchangeRefreshToken({
    grantType: "refresh_token",
    refreshToken: issued.refreshToken,
    clientId: CLIENT_ID,
    resource: RESOURCE,
  });
  assert.ok(refreshed.accessToken.length > 20);
  service.verifyAccessToken(refreshed.accessToken, ["localcut:write"]);

  authorized = false;
  assert.throws(() => service.verifyAccessToken(refreshed.accessToken, ["localcut:read"]), /OAUTH_DEVICE_UNAUTHORIZED/);
  assert.throws(() => service.exchangeRefreshToken({
    grantType: "refresh_token",
    refreshToken: refreshed.refreshToken,
    clientId: CLIENT_ID,
    resource: RESOURCE,
  }), /OAUTH_DEVICE_UNAUTHORIZED/);
});
