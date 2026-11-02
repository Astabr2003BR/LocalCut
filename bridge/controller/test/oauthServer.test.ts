import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";
import { PairingService } from "../src/pairing.ts";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

const PUBLIC_BASE = "https://localcut-bridge-v1.onrender.com";
const RESOURCE = `${PUBLIC_BASE}/mcp`;
const CLIENT_ID = "localcut-chatgpt";
const REDIRECT_URI = "https://chatgpt.com/connector_platform_oauth_redirect";
const VERIFIER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~abc";
const CHALLENGE = createHash("sha256").update(VERIFIER, "ascii").digest("base64url");

async function fixture() {
  const store = new InMemoryBridgeStore();
  store.upsertDevice({
    deviceId: "device-1",
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-1",
    revokedAtEpochMs: null,
  });
  const pairing = new PairingService(store);
  const sessions = new DeviceSessionService(store, new InMemorySessionStore());
  const deviceSession = sessions.issue("device-1", 1_000_000);
  let now = 1_000_000;
  const runtime = createControllerServer({
    controllerApiToken: "controller-secret-for-tests",
    controllerId: "controller-1",
    publicBaseUrl: PUBLIC_BASE,
    store,
    pairing,
    sessions,
    nowEpochMs: () => now,
  });
  await runtime.listen(0, "127.0.0.1");
  const address = runtime.address();
  if (!address || typeof address === "string") throw new Error("missing test server address");
  return {
    runtime,
    deviceSession,
    baseUrl: `http://127.0.0.1:${address.port}`,
    setNow(value: number) { now = value; },
  };
}

function authorizeUrl(baseUrl: string) {
  const url = new URL(`${baseUrl}/oauth/authorize`);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", CLIENT_ID);
  url.searchParams.set("redirect_uri", REDIRECT_URI);
  url.searchParams.set("state", "state-123");
  url.searchParams.set("code_challenge", CHALLENGE);
  url.searchParams.set("code_challenge_method", "S256");
  url.searchParams.set("resource", RESOURCE);
  url.searchParams.set("scope", "localcut:read localcut:write");
  return url.toString();
}

function requestIdFromHtml(html: string): string {
  const match = html.match(/localcut:\/\/oauth\/approve\?requestId=([^&"']+)/);
  assert.ok(match, "authorization page must contain LocalCut approval deep link");
  return decodeURIComponent(match[1]);
}

test("OAuth discovery publishes protected resource and authorization-server metadata", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  const resource = await fetch(`${fx.baseUrl}/.well-known/oauth-protected-resource`);
  assert.equal(resource.status, 200);
  assert.deepEqual(await resource.json(), {
    resource: RESOURCE,
    authorization_servers: [PUBLIC_BASE],
    scopes_supported: ["localcut:read", "localcut:write"],
  });

  const auth = await fetch(`${fx.baseUrl}/.well-known/oauth-authorization-server`);
  assert.equal(auth.status, 200);
  const metadata = await auth.json() as Record<string, unknown>;
  assert.equal(metadata.issuer, PUBLIC_BASE);
  assert.equal(metadata.authorization_endpoint, `${PUBLIC_BASE}/oauth/authorize`);
  assert.equal(metadata.token_endpoint, `${PUBLIC_BASE}/oauth/token`);
  assert.deepEqual(metadata.token_endpoint_auth_methods_supported, ["none"]);
  assert.deepEqual(metadata.code_challenge_methods_supported, ["S256"]);
  assert.equal(metadata.authorization_response_iss_parameter_supported, true);
});

test("MCP unauthenticated request advertises OAuth protected-resource challenge", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  const response = await fetch(`${fx.baseUrl}/mcp`, { method: "POST" });
  assert.equal(response.status, 401);
  const challenge = response.headers.get("www-authenticate") ?? "";
  assert.match(challenge, /Bearer/);
  assert.match(challenge, /resource_metadata="https:\/\/localcut-bridge-v1\.onrender\.com\/\.well-known\/oauth-protected-resource"/);
});

test("OAuth authorization is approved by paired device then exchanged with PKCE", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  const authorize = await fetch(authorizeUrl(fx.baseUrl));
  assert.equal(authorize.status, 200);
  assert.match(authorize.headers.get("content-type") ?? "", /text\/html/);
  const requestId = requestIdFromHtml(await authorize.text());

  const unauthorizedApproval = await fetch(`${fx.baseUrl}/v1/oauth/device-approve`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ requestId }),
  });
  assert.equal(unauthorizedApproval.status, 401);

  const approval = await fetch(`${fx.baseUrl}/v1/oauth/device-approve`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${fx.deviceSession.accessToken}`,
    },
    body: JSON.stringify({ requestId }),
  });
  assert.equal(approval.status, 200);
  const approved = await approval.json() as { completionUrl: string };
  assert.match(approved.completionUrl, new RegExp(`/oauth/complete\\?requestId=${requestId}`));

  const completeLocalUrl = new URL(approved.completionUrl);
  completeLocalUrl.protocol = "http:";
  completeLocalUrl.hostname = "127.0.0.1";
  completeLocalUrl.port = new URL(fx.baseUrl).port;
  const completed = await fetch(completeLocalUrl, { redirect: "manual" });
  assert.equal(completed.status, 302);
  const callback = new URL(completed.headers.get("location") ?? "");
  assert.equal(`${callback.origin}${callback.pathname}`, REDIRECT_URI);
  assert.equal(callback.searchParams.get("state"), "state-123");
  assert.equal(callback.searchParams.get("iss"), PUBLIC_BASE);
  const code = callback.searchParams.get("code");
  assert.ok(code);

  const tokenBody = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code_verifier: VERIFIER,
    resource: RESOURCE,
  });
  const token = await fetch(`${fx.baseUrl}/oauth/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: tokenBody,
  });
  assert.equal(token.status, 200);
  const tokenJson = await token.json() as Record<string, unknown>;
  assert.equal(tokenJson.token_type, "Bearer");
  assert.equal(tokenJson.scope, "localcut:read localcut:write");
  assert.equal(typeof tokenJson.access_token, "string");
  assert.equal(typeof tokenJson.refresh_token, "string");
});

test("OAuth authorize rejects a callback outside the ChatGPT allowlist", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const url = new URL(authorizeUrl(fx.baseUrl));
  url.searchParams.set("redirect_uri", "https://evil.example/callback");
  const response = await fetch(url);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { code: "OAUTH_REDIRECT_INVALID" });
});
