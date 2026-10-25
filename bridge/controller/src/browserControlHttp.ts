import { randomBytes, randomUUID } from "node:crypto";
import type { IncomingMessage, ServerResponse } from "node:http";
import { BrowserControlSessionService } from "./browserControl.ts";
import type { CommandRouter } from "./commandRouter.ts";
import {
  isBridgeCapability,
  revisionRequiredCapabilities,
  type BridgeCommandEnvelope,
  type BridgeResultEnvelope,
} from "./protocol.ts";
import type { DeviceSessionService } from "./session.ts";

const MAX_BODY_BYTES = 64 * 1024;
const BINDING_COOKIE = "localcut_remote_binding";
const SESSION_COOKIE = "localcut_remote_session";
const COMMAND_TTL_MS = 30_000;

export type BrowserControlHttpOptions = {
  sessions: DeviceSessionService;
  browserSessions: BrowserControlSessionService;
  router: CommandRouter;
  controllerId: string;
  publicBaseUrl: string;
  nowEpochMs?: () => number;
  randomToken?: () => string;
};

export function createBrowserControlHttpHandler(options: BrowserControlHttpOptions) {
  const nowEpochMs = options.nowEpochMs ?? Date.now;
  const randomToken = options.randomToken ?? (() => randomBytes(24).toString("base64url"));
  const publicBaseUrl = options.publicBaseUrl.replace(/\/$/, "");

  return async function handleBrowserControl(
    req: IncomingMessage,
    res: ServerResponse,
    url: URL,
  ): Promise<boolean> {
    if (req.method === "GET" && url.pathname === "/remote/approve-device") {
      const requestId = requiredQuery(url, "requestId", 256);
      const deepLink = new URL("localcut://remote/approve");
      deepLink.searchParams.set("requestId", requestId);
      return redirect(res, deepLink.toString());
    }

    if (req.method === "POST" && url.pathname === "/v1/remote/device-approve") {
      const session = authenticateDevice(req, options.sessions, nowEpochMs());
      if (!session) {
        json(res, 401, { code: "AUTH_REQUIRED" });
        return true;
      }
      const body = await readJson(req);
      const requestId = requiredBodyString(body, "requestId", 256);
      options.browserSessions.approveAuthorization(requestId, session.deviceId);
      json(res, 200, { approved: true });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/remote/api/authorize/start") {
      const binding = randomToken();
      const pending = options.browserSessions.beginAuthorization(binding);
      setCookie(res, BINDING_COOKIE, binding, 5 * 60);
      const approvalUrl = new URL("/remote/approve-device", publicBaseUrl);
      approvalUrl.searchParams.set("requestId", pending.requestId);
      json(res, 200, {
        requestId: pending.requestId,
        approvalUrl: approvalUrl.toString(),
        expiresAtEpochMs: pending.expiresAtEpochMs,
      });
      return true;
    }

    if (req.method === "GET" && url.pathname === "/remote/api/authorize/status") {
      const requestId = requiredQuery(url, "requestId", 256);
      const binding = cookie(req, BINDING_COOKIE);
      if (!binding) {
        json(res, 401, { code: "REMOTE_BROWSER_BINDING_INVALID" });
        return true;
      }
      try {
        const issued = options.browserSessions.completeAuthorization(requestId, binding);
        setCookie(res, SESSION_COOKIE, issued.sessionToken, 30 * 24 * 60 * 60, "/remote");
        clearCookie(res, BINDING_COOKIE);
        json(res, 200, {
          authorized: true,
          csrfToken: issued.csrfToken,
          deviceId: issued.deviceId,
          expiresAtEpochMs: issued.expiresAtEpochMs,
        });
      } catch (error) {
        const code = errorCode(error);
        if (code === "REMOTE_AUTH_NOT_APPROVED") {
          json(res, 202, { authorized: false });
        } else {
          json(res, code.includes("EXPIRED") ? 410 : 401, { code });
        }
      }
      return true;
    }

    if (req.method === "GET" && url.pathname === "/remote") {
      const session = verifyBrowserSession(req, options.browserSessions);
      if (!session) {
        html(res, 401, unauthorizedPage());
        return true;
      }
      html(res, 200, panelPage(session.csrfToken));
      return true;
    }

    if (req.method === "GET" && url.pathname === "/remote/api/contexts") {
      const session = verifyBrowserSession(req, options.browserSessions);
      if (!session) {
        json(res, 401, { code: "REMOTE_SESSION_INVALID" });
        return true;
      }
      const command = makeCommand({
        deviceId: session.deviceId,
        projectId: "__active__",
        capability: "get_device_status",
        expectedRevision: null,
        payload: {},
        nowEpochMs: nowEpochMs(),
      });
      const result = await options.router.execute(options.controllerId, command);
      if (!result.success) {
        json(res, 200, { contexts: [], error: result.code });
        return true;
      }
      const context = activeContextFromResult(session.deviceId, result);
      json(res, 200, { contexts: context ? [context] : [] });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/remote/api/command") {
      const sessionToken = cookie(req, SESSION_COOKIE);
      if (!sessionToken) {
        json(res, 401, { code: "REMOTE_SESSION_INVALID" });
        return true;
      }
      let session;
      try {
        session = options.browserSessions.assertCsrf(
          sessionToken,
          headerValue(req.headers["x-localcut-csrf"]),
        );
      } catch (error) {
        const code = errorCode(error);
        json(res, code === "REMOTE_CSRF_INVALID" ? 403 : 401, { code });
        return true;
      }

      const body = await readJson(req);
      const projectId = requiredBodyString(body, "projectId", 256);
      const capabilityRaw = requiredBodyString(body, "capability", 128);
      if (!isBridgeCapability(capabilityRaw)) {
        json(res, 400, { code: "CAPABILITY_NOT_ALLOWED" });
        return true;
      }
      const payloadRaw = body.payload ?? {};
      if (!isRecord(payloadRaw)) {
        json(res, 400, { code: "INVALID_ARGUMENT" });
        return true;
      }
      const expectedRevisionRaw = body.expectedRevision;
      const expectedRevision = expectedRevisionRaw == null
        ? null
        : typeof expectedRevisionRaw === "number" && Number.isSafeInteger(expectedRevisionRaw) && expectedRevisionRaw >= 0
          ? expectedRevisionRaw
          : undefined;
      if (expectedRevision === undefined) {
        json(res, 400, { code: "INVALID_ARGUMENT" });
        return true;
      }
      if (revisionRequiredCapabilities.has(capabilityRaw) && expectedRevision === null) {
        json(res, 400, { code: "REVISION_REQUIRED" });
        return true;
      }

      const command = makeCommand({
        deviceId: session.deviceId,
        projectId,
        capability: capabilityRaw,
        expectedRevision,
        payload: payloadRaw,
        nowEpochMs: nowEpochMs(),
      });
      const result = await options.router.execute(options.controllerId, command);
      json(res, 200, result);
      return true;
    }

    return false;
  };
}

function makeCommand(input: {
  deviceId: string;
  projectId: string;
  capability: BridgeCommandEnvelope["capability"];
  expectedRevision: number | null;
  payload: Record<string, unknown>;
  nowEpochMs: number;
}): BridgeCommandEnvelope {
  const messageId = randomUUID();
  return {
    protocolVersion: 1,
    messageId,
    kind: "command",
    deviceId: input.deviceId,
    projectId: input.projectId,
    expectedRevision: input.expectedRevision,
    issuedAtEpochMs: input.nowEpochMs,
    expiresAtEpochMs: input.nowEpochMs + COMMAND_TTL_MS,
    nonce: randomUUID(),
    capability: input.capability,
    payload: input.payload,
  };
}

function activeContextFromResult(
  fallbackDeviceId: string,
  result: BridgeResultEnvelope,
): { deviceId: string; projectId: string; projectRevision: number } | null {
  if (!isRecord(result.result)) return null;
  const deviceId = typeof result.result.deviceId === "string" ? result.result.deviceId : fallbackDeviceId;
  const projectId = result.result.projectId;
  const revision = result.result.projectRevision ?? result.projectRevision;
  if (deviceId !== fallbackDeviceId || typeof projectId !== "string" || !projectId) return null;
  if (typeof revision !== "number" || !Number.isSafeInteger(revision) || revision < 0) return null;
  return { deviceId, projectId, projectRevision: revision };
}

function authenticateDevice(
  req: IncomingMessage,
  sessions: DeviceSessionService,
  nowEpochMs: number,
) {
  const authorization = req.headers.authorization;
  if (!authorization?.startsWith("Bearer ")) return null;
  const token = authorization.slice("Bearer ".length).trim();
  return token ? sessions.authenticateAccess(token, nowEpochMs) : null;
}

function verifyBrowserSession(req: IncomingMessage, sessions: BrowserControlSessionService) {
  const token = cookie(req, SESSION_COOKIE);
  if (!token) return null;
  try {
    return sessions.verifySession(token);
  } catch {
    return null;
  }
}

function cookie(req: IncomingMessage, name: string): string | null {
  const raw = req.headers.cookie;
  if (!raw) return null;
  for (const part of raw.split(";")) {
    const index = part.indexOf("=");
    if (index <= 0) continue;
    const key = part.slice(0, index).trim();
    if (key !== name) continue;
    const value = part.slice(index + 1).trim();
    return value || null;
  }
  return null;
}

function setCookie(
  res: ServerResponse,
  name: string,
  value: string,
  maxAgeSeconds: number,
  path = "/remote",
): void {
  appendSetCookie(
    res,
    `${name}=${value}; Path=${path}; Max-Age=${maxAgeSeconds}; HttpOnly; Secure; SameSite=Strict`,
  );
}

function clearCookie(res: ServerResponse, name: string): void {
  appendSetCookie(res, `${name}=; Path=/remote; Max-Age=0; HttpOnly; Secure; SameSite=Strict`);
}

function appendSetCookie(res: ServerResponse, value: string): void {
  const current = res.getHeader("set-cookie");
  if (!current) {
    res.setHeader("set-cookie", value);
  } else if (Array.isArray(current)) {
    res.setHeader("set-cookie", [...current.map(String), value]);
  } else {
    res.setHeader("set-cookie", [String(current), value]);
  }
}

function headerValue(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function requiredQuery(url: URL, key: string, maxLength: number): string {
  const value = url.searchParams.get(key);
  if (!value || value.length > maxLength) throw new Error("INVALID_ARGUMENT");
  return value;
}

function requiredBodyString(body: Record<string, unknown>, key: string, maxLength: number): string {
  const value = body[key];
  if (typeof value !== "string" || !value || value.length > maxLength) throw new Error("INVALID_ARGUMENT");
  return value;
}

async function readJson(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > MAX_BODY_BYTES) throw new Error("BODY_TOO_LARGE");
    chunks.push(buffer);
  }
  if (!chunks.length) return {};
  const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  if (!isRecord(parsed)) throw new Error("INVALID_ARGUMENT");
  return parsed;
}

function redirect(res: ServerResponse, location: string): true {
  res.writeHead(302, {
    location,
    "cache-control": "no-store",
    "content-length": "0",
    "x-content-type-options": "nosniff",
  });
  res.end();
  return true;
}

function json(res: ServerResponse, status: number, value: unknown): void {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(body);
}

function html(res: ServerResponse, status: number, body: string): void {
  res.writeHead(status, {
    "content-type": "text/html; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
  });
  res.end(body);
}

function unauthorizedPage(): string {
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LocalCut Remote Control</title></head><body><main><h1>LocalCut Remote Control</h1><p>Este navegador ainda não foi autorizado.</p></main></body></html>`;
}

function panelPage(csrfToken: string): string {
  const csrf = JSON.stringify(csrfToken).replaceAll("<", "\\u003c");
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LocalCut Remote Control</title><style>body{font-family:system-ui;background:#0b0b0d;color:#f5f5f5;margin:0;padding:24px}main{max-width:760px;margin:auto}button,input,textarea,select{font:inherit;padding:10px;margin:4px 0;background:#18181c;color:#fff;border:1px solid #444;border-radius:8px}button{cursor:pointer}.card{padding:16px;border:1px solid #333;border-radius:12px;margin:12px 0}pre{white-space:pre-wrap;word-break:break-word}</style></head><body><main><h1>LocalCut Remote Control</h1><div class="card"><button id="refresh">Atualizar projeto</button><pre id="state">Carregando…</pre></div><div class="card"><label>Posição do playhead (µs)</label><br><input id="position" inputmode="numeric" value="0"><button id="seek">Mover playhead</button></div><pre id="result"></pre><script>const csrf=${csrf};let context=null;async function contexts(){const r=await fetch('/remote/api/contexts');const j=await r.json();context=j.contexts?.[0]??null;document.getElementById('state').textContent=JSON.stringify(j,null,2);return context}async function command(capability,payload,expectedRevision=null){if(!context)await contexts();if(!context)throw new Error('DEVICE_OFFLINE');const r=await fetch('/remote/api/command',{method:'POST',headers:{'content-type':'application/json','x-localcut-csrf':csrf},body:JSON.stringify({projectId:context.projectId,capability,expectedRevision,payload})});const j=await r.json();document.getElementById('result').textContent=JSON.stringify(j,null,2);if(j.projectRevision!=null&&context)context.projectRevision=j.projectRevision;return j}document.getElementById('refresh').onclick=contexts;document.getElementById('seek').onclick=()=>command('seek',{positionUs:Number(document.getElementById('position').value)}).catch(e=>document.getElementById('result').textContent=e.message);contexts();</script></main></body></html>`;
}

function errorCode(error: unknown): string {
  if (error instanceof Error && /^[A-Z0-9_]{3,64}$/.test(error.message)) return error.message;
  return "INVALID_ARGUMENT";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
