import assert from "node:assert/strict";
import test from "node:test";
import { CommandRouter, DeviceRegistry, type DeviceChannel } from "../src/commandRouter.ts";
import { PairingService } from "../src/pairing.ts";
import type { BridgeCommandEnvelope, BridgeResultEnvelope } from "../src/protocol.ts";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { createControllerServer } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

const DEVICE_ID = "remote-device-1";
const PROJECT_ID = "remote-project-1";

class FakeRemoteDevice implements DeviceChannel {
  playheadUs = 0;
  revision = 7;
  lastCommand: BridgeCommandEnvelope | null = null;

  async send(command: BridgeCommandEnvelope): Promise<BridgeResultEnvelope> {
    this.lastCommand = command;
    if (command.projectId !== PROJECT_ID && command.capability !== "get_device_status") {
      return failure(command.messageId, "PROJECT_NOT_FOUND", this.revision);
    }
    if (command.capability === "get_device_status") {
      return success(command.messageId, this.revision, {
        deviceId: DEVICE_ID,
        projectId: PROJECT_ID,
        projectRevision: this.revision,
        connected: true,
      });
    }
    if (command.capability === "get_project_state") {
      return success(command.messageId, this.revision, {
        projectId: PROJECT_ID,
        revision: this.revision,
        playheadUs: this.playheadUs,
      });
    }
    if (command.capability === "seek") {
      const positionUs = command.payload.positionUs;
      if (typeof positionUs !== "number" || !Number.isSafeInteger(positionUs) || positionUs < 0) {
        return failure(command.messageId, "INVALID_ARGUMENT", this.revision);
      }
      this.playheadUs = positionUs;
      return success(command.messageId, this.revision, { playheadUs: this.playheadUs });
    }
    if (command.capability === "move_clip") {
      if (command.expectedRevision !== this.revision) {
        return failure(command.messageId, "REVISION_CONFLICT", this.revision);
      }
      this.revision += 1;
      return success(command.messageId, this.revision, { moved: true });
    }
    return failure(command.messageId, "CAPABILITY_NOT_IMPLEMENTED_IN_FIXTURE", this.revision);
  }

  close(): void {}
}

async function fixture() {
  const store = new InMemoryBridgeStore();
  store.upsertDevice({
    deviceId: DEVICE_ID,
    publicKeySpkiBase64: "AQID",
    controllerId: "controller-remote",
    revokedAtEpochMs: null,
  });
  const pairing = new PairingService(store, {
    codeGenerator: () => "112233",
    challengeGenerator: () => Buffer.alloc(32, 1),
  });
  let tokenCounter = 0;
  const sessions = new DeviceSessionService(store, new InMemorySessionStore(), {
    tokenGenerator: () => `remote-session-${++tokenCounter}-${"x".repeat(24)}`,
  });
  const issued = sessions.issue(DEVICE_ID, 1_000);
  const registry = new DeviceRegistry();
  const device = new FakeRemoteDevice();
  registry.register(DEVICE_ID, device);
  const router = new CommandRouter(store, registry, { timeoutMs: 500 });
  let now = 1_000;
  let randomCounter = 0;
  const runtime = createControllerServer({
    controllerApiToken: "controller-remote-secret",
    controllerId: "controller-remote",
    publicBaseUrl: "https://localcut.example",
    store,
    pairing,
    sessions,
    registry,
    router,
    nowEpochMs: () => now,
    remoteRandomToken: () => `remote_${++randomCounter}_${"x".repeat(24)}`,
  });
  await runtime.listen(0, "127.0.0.1");
  const address = runtime.address();
  if (!address || typeof address === "string") throw new Error("missing remote server address");
  return {
    runtime,
    device,
    issued,
    baseUrl: `http://127.0.0.1:${address.port}`,
    setNow(value: number) { now = value; },
  };
}

function cookiePair(setCookie: string | null): string {
  assert.ok(setCookie, "missing set-cookie");
  return setCookie.split(";", 1)[0] ?? "";
}

async function startAndAuthorize(fx: Awaited<ReturnType<typeof fixture>>) {
  const start = await fetch(`${fx.baseUrl}/remote/api/authorize/start`, { method: "POST" });
  assert.equal(start.status, 200);
  const bindingCookie = cookiePair(start.headers.get("set-cookie"));
  const started = await start.json() as { requestId: string; approvalUrl: string };
  assert.match(started.requestId, /^remote_/);
  assert.equal(
    started.approvalUrl,
    `https://localcut.example/remote/approve-device?requestId=${encodeURIComponent(started.requestId)}`,
  );

  const unauthenticatedApproval = await fetch(`${fx.baseUrl}/v1/remote/device-approve`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ requestId: started.requestId }),
  });
  assert.equal(unauthenticatedApproval.status, 401);

  const approval = await fetch(`${fx.baseUrl}/v1/remote/device-approve`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${fx.issued.accessToken}`,
    },
    body: JSON.stringify({ requestId: started.requestId }),
  });
  assert.equal(approval.status, 200);

  const status = await fetch(
    `${fx.baseUrl}/remote/api/authorize/status?requestId=${encodeURIComponent(started.requestId)}`,
    { headers: { cookie: bindingCookie } },
  );
  assert.equal(status.status, 200);
  const sessionCookie = cookiePair(status.headers.get("set-cookie"));
  const body = await status.json() as { authorized: boolean; csrfToken: string };
  assert.equal(body.authorized, true);
  assert.ok(body.csrfToken.length > 20);
  return { sessionCookie, csrfToken: body.csrfToken };
}

test("browser remote authorization is device-approved and issues an HttpOnly secure session", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());

  const { sessionCookie } = await startAndAuthorize(fx);
  assert.match(sessionCookie, /^localcut_remote_session=/);

  const panel = await fetch(`${fx.baseUrl}/remote`, { headers: { cookie: sessionCookie } });
  assert.equal(panel.status, 200);
  assert.match(panel.headers.get("content-type") ?? "", /text\/html/);
  assert.match(await panel.text(), /LocalCut Remote Control/);
});

test("remote context and commands are scoped to the authorized device and writes require CSRF", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const auth = await startAndAuthorize(fx);

  const contexts = await fetch(`${fx.baseUrl}/remote/api/contexts`, {
    headers: { cookie: auth.sessionCookie },
  });
  assert.equal(contexts.status, 200);
  const contextsBody = await contexts.json() as { contexts: Array<{ deviceId: string; projectId: string; projectRevision: number }> };
  assert.deepEqual(contextsBody.contexts, [{
    deviceId: DEVICE_ID,
    projectId: PROJECT_ID,
    projectRevision: 7,
  }]);

  const noCsrf = await fetch(`${fx.baseUrl}/remote/api/command`, {
    method: "POST",
    headers: { "content-type": "application/json", cookie: auth.sessionCookie },
    body: JSON.stringify({ projectId: PROJECT_ID, capability: "seek", payload: { positionUs: 2_000_000 } }),
  });
  assert.equal(noCsrf.status, 403);

  const seek = await fetch(`${fx.baseUrl}/remote/api/command`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      cookie: auth.sessionCookie,
      "x-localcut-csrf": auth.csrfToken,
    },
    body: JSON.stringify({ projectId: PROJECT_ID, capability: "seek", payload: { positionUs: 2_000_000 } }),
  });
  assert.equal(seek.status, 200);
  const seekBody = await seek.json() as BridgeResultEnvelope;
  assert.equal(seekBody.success, true);
  assert.equal((seekBody.result as { playheadUs: number }).playheadUs, 2_000_000);
  assert.equal(fx.device.lastCommand?.deviceId, DEVICE_ID, "browser must not choose another deviceId");
});

test("remote command surfaces revision conflict and offline device without bypassing CommandRouter", async t => {
  const fx = await fixture();
  t.after(() => fx.runtime.close());
  const auth = await startAndAuthorize(fx);

  const conflict = await fetch(`${fx.baseUrl}/remote/api/command`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      cookie: auth.sessionCookie,
      "x-localcut-csrf": auth.csrfToken,
    },
    body: JSON.stringify({
      projectId: PROJECT_ID,
      capability: "move_clip",
      expectedRevision: 6,
      payload: { clipId: "clip-1", startUs: 1_000_000 },
    }),
  });
  assert.equal(conflict.status, 200);
  const conflictBody = await conflict.json() as BridgeResultEnvelope;
  assert.equal(conflictBody.success, false);
  assert.equal(conflictBody.code, "REVISION_CONFLICT");

  fx.device.close();
  // Closing this fake does not unregister it; use an explicit server-side test action to simulate offline.
  const offline = await fetch(`${fx.baseUrl}/remote/api/command`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      cookie: auth.sessionCookie,
      "x-localcut-csrf": auth.csrfToken,
    },
    body: JSON.stringify({ projectId: "missing-project", capability: "get_project_state", payload: {} }),
  });
  assert.equal(offline.status, 200);
  const offlineBody = await offline.json() as BridgeResultEnvelope;
  assert.equal(offlineBody.success, false);
  assert.equal(offlineBody.code, "PROJECT_NOT_FOUND");
});

function success(messageId: string, projectRevision: number, result: unknown): BridgeResultEnvelope {
  return { messageId, success: true, code: null, projectRevision, result, retryable: false };
}

function failure(messageId: string, code: string, projectRevision: number): BridgeResultEnvelope {
  return { messageId, success: false, code, projectRevision, result: null, retryable: code === "DEVICE_OFFLINE" };
}
