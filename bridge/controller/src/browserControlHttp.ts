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
import {
  createTransientMediaHttpHandler,
  type TransientMediaStore,
} from "./transientMediaHttp.ts";

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
  transientMediaStore?: TransientMediaStore;
};

export function createBrowserControlHttpHandler(options: BrowserControlHttpOptions) {
  const nowEpochMs = options.nowEpochMs ?? Date.now;
  const randomToken = options.randomToken ?? (() => randomBytes(24).toString("base64url"));
  const publicBaseUrl = options.publicBaseUrl.replace(/\/$/, "");
  const transientMediaHandler = createTransientMediaHttpHandler(
    { nowEpochMs },
    options.transientMediaStore,
  );

  return async function handleBrowserControl(
    req: IncomingMessage,
    res: ServerResponse,
    url: URL,
  ): Promise<boolean> {
    if (await transientMediaHandler(req, res, url)) return true;

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
        projectId: "__localcut_active_project__",
        capability: "get_device_status",
        payload: {},
        expectedRevision: null,
      });
      const result = await options.router.execute(options.controllerId, command);
      if (!result.success) {
        json(res, 502, result);
        return true;
      }
      json(res, 200, result);
      return true;
    }

    if (req.method === "POST" && url.pathname === "/remote/api/command") {
      const session = verifyBrowserSession(req, options.browserSessions);
      if (!session) {
        json(res, 401, { code: "REMOTE_SESSION_INVALID" });
        return true;
      }
      const csrf = req.headers["x-csrf-token"];
      if (typeof csrf !== "string" || csrf !== session.csrfToken) {
        json(res, 403, { code: "REMOTE_CSRF_INVALID" });
        return true;
      }
      const body = await readJson(req);
      const capabilityValue = requiredBodyString(body, "capability", 128);
      if (!isBridgeCapability(capabilityValue)) {
        json(res, 400, { code: "CAPABILITY_NOT_ALLOWED" });
        return true;
      }
      const projectId = requiredBodyString(body, "projectId", 256);
      const expectedRevisionRaw = body.expectedRevision;
      const expectedRevision = expectedRevisionRaw === null || expectedRevisionRaw === undefined ? null :
        Number.isSafeInteger(expectedRevisionRaw) && (expectedRevisionRaw as number) >= 0 ? expectedRevisionRaw as number :
        NaN;
      if (Number.isNaN(expectedRevision)) {
        json(res, 400, { code: "INVALID_ARGUMENT" });
        return true;
      }
      if (revisionRequiredCapabilities.has(capabilityValue) && expectedRevision === null) {
        json(res, 400, { code: "REVISION_REQUIRED" });
        return true;
      }
      const payload = isRecord(body.payload) ? body.payload : {};
      const command = makeCommand({
        deviceId: session.deviceId,
        projectId,
        capability: capabilityValue,
        payload,
        expectedRevision,
      });
      const result = await options.router.execute(options.controllerId, command);
      json(res, result.success ? 200 : statusForBridgeResult(result), result);
      return true;
    }

    return false;
  };
}

function makeCommand(input: {
  deviceId: string;
  projectId: string;
  capability: BridgeCommandEnvelope["capability"];
  payload: Record<string, unknown>;
  expectedRevision: number | null;
}): BridgeCommandEnvelope {
  const now = Date.now();
  return {
    protocolVersion: 1,
    messageId: randomUUID(),
    kind: "command",
    deviceId: input.deviceId,
    projectId: input.projectId,
    expectedRevision: input.expectedRevision,
    issuedAtEpochMs: now,
    expiresAtEpochMs: now + COMMAND_TTL_MS,
    nonce: randomBytes(18).toString("base64url"),
    capability: input.capability,
    payload: input.payload,
  };
}

function authenticateDevice(
  req: IncomingMessage,
  sessions: DeviceSessionService,
  nowEpochMs: number,
) {
  const auth = req.headers.authorization;
  if (!auth?.startsWith("Bearer ")) return null;
  return sessions.authenticateAccess(auth.slice("Bearer ".length), nowEpochMs);
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

function requiredQuery(url: URL, name: string, max: number): string {
  const value = url.searchParams.get(name);
  if (!value || value.length > max) throw new Error("INVALID_ARGUMENT");
  return value;
}

async function readJson(req: IncomingMessage): Promise<Record<string, unknown>> {
  const contentLength = Number(req.headers["content-length"] ?? 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_BODY_BYTES) throw new Error("BODY_TOO_LARGE");
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > MAX_BODY_BYTES) throw new Error("BODY_TOO_LARGE");
    chunks.push(buffer);
  }
  const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  if (!isRecord(parsed)) throw new Error("INVALID_ARGUMENT");
  return parsed;
}

function requiredBodyString(body: Record<string, unknown>, name: string, max: number): string {
  const value = body[name];
  if (typeof value !== "string" || value.length === 0 || value.length > max) throw new Error("INVALID_ARGUMENT");
  return value;
}

function cookie(req: IncomingMessage, name: string): string | null {
  const raw = req.headers.cookie ?? "";
  for (const part of raw.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

function setCookie(res: ServerResponse, name: string, value: string, maxAgeSeconds: number, path = "/"): void {
  res.setHeader("set-cookie", `${name}=${encodeURIComponent(value)}; Path=${path}; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAgeSeconds}`);
}

function clearCookie(res: ServerResponse, name: string): void {
  res.setHeader("set-cookie", `${name}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0`);
}

function json(res: ServerResponse, status: number, value: unknown): true {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-length": Buffer.byteLength(body),
  });
  res.end(body);
  return true;
}

function html(res: ServerResponse, status: number, body: string): true {
  res.writeHead(status, {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'",
    "content-length": Buffer.byteLength(body),
  });
  res.end(body);
  return true;
}

function redirect(res: ServerResponse, location: string): true {
  res.writeHead(302, {
    location,
    "cache-control": "no-store",
    "content-length": "0",
  });
  res.end();
  return true;
}

function errorCode(error: unknown): string {
  return error instanceof Error && /^[A-Z0-9_]{3,64}$/.test(error.message) ? error.message : "INTERNAL_ERROR";
}

function statusForBridgeResult(result: BridgeResultEnvelope): number {
  if (result.code === "DEVICE_OFFLINE") return 503;
  if (result.code === "REVISION_CONFLICT") return 409;
  if (result.code === "AUTH_REQUIRED" || result.code === "PAIRING_REQUIRED") return 401;
  return 400;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function unauthorizedPage(): string {
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>LocalCut</title></head><body><h1>LocalCut</h1><p>Autorize este navegador pelo app LocalCut para continuar.</p></body></html>`;
}

function panelPage(csrfToken: string): string {
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>LocalCut Remote</title><style>body{font-family:sans-serif;margin:24px;max-width:720px}button,input,textarea{font:inherit;margin:4px 0;padding:8px;width:100%;box-sizing:border-box}pre{white-space:pre-wrap;background:#111;color:#eee;padding:12px;min-height:160px}</style></head><body><h1>LocalCut Remote</h1><p>Controle autenticado do dispositivo pareado.</p><button id="contexts">Ler contexto ativo</button><input id="project" placeholder="projectId"><input id="revision" placeholder="expectedRevision"><input id="cap" placeholder="capability"><textarea id="payload" placeholder='{"key":"value"}'></textarea><button id="send">Executar</button><pre id="out"></pre><script>const csrf=${JSON.stringify(csrfToken)};const out=document.getElementById('out');document.getElementById('contexts').onclick=async()=>{const r=await fetch('/remote/api/contexts');out.textContent=await r.text()};document.getElementById('send').onclick=async()=>{let payload={};try{payload=JSON.parse(document.getElementById('payload').value||'{}')}catch{out.textContent='JSON inválido';return}const raw=document.getElementById('revision').value;const body={projectId:document.getElementById('project').value,capability:document.getElementById('cap').value,payload,expectedRevision:raw===''?null:Number(raw)};const r=await fetch('/remote/api/command',{method:'POST',headers:{'content-type':'application/json','x-csrf-token':csrf},body:JSON.stringify(body)});out.textContent=await r.text()}</script></body></html>`;
}
