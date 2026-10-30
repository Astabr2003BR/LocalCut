import { createPublicKey, timingSafeEqual, verify } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { pathToFileURL } from "node:url";
import { createMcpHandler } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import { WebSocketServer, type WebSocket } from "ws";
import { BrowserControlSessionService } from "./browserControl.ts";
import { createBrowserControlHttpHandler } from "./browserControlHttp.ts";
import { CommandRouter, DeviceRegistry } from "./commandRouter.ts";
import { WebSocketDeviceChannel, type DeviceSocket } from "./deviceChannel.ts";
import { createLocalCutMcpServer } from "./mcp.ts";
import { LocalCutOAuthService } from "./oauth.ts";
import { PairingService } from "./pairing.ts";
import { DeviceRecoveryTokenService, publicKeySha256 } from "./recovery.ts";
import { DeviceSessionService, SqliteSessionStore, type DeviceSessionStore } from "./session.ts";
import { SqliteBridgeStore, type BridgeStore } from "./store.ts";

const MAX_JSON_BODY_BYTES = 64 * 1024;
const MAX_HANDOFF_QUERY_BYTES = 8 * 1024;
const MOBILE_CLAIM_MAX_AGE_MS = 60 * 1_000;
const MOBILE_CLAIM_PREFIX = "LOCALCUT_MOBILE_CLAIM_V1";
const SESSION_RECOVERY_MAX_AGE_MS = 60 * 1_000;
const SESSION_RECOVERY_PREFIX = "LOCALCUT_SESSION_RECOVERY_V1";
const DEFAULT_PUBLIC_BASE_URL = "https://localcut-bridge-v1.onrender.com";
const ANDROID_ASSET_LINKS = [{
  relation: ["delegate_permission/common.handle_all_urls"],
  target: {
    namespace: "android_app",
    package_name: "com.localcut.editor",
    sha256_cert_fingerprints: [
      "B7:C1:27:CE:71:09:BF:4B:BB:CD:A3:96:EB:93:75:EA:99:DF:28:0F:75:95:FB:ED:98:37:73:4E:B8:47:6F:91",
    ],
  },
}];

export type ControllerServerOptions = {
  controllerApiToken: string;
  controllerId: string;
  publicBaseUrl?: string;
  store: BridgeStore;
  pairing: PairingService;
  sessions: DeviceSessionService;
  nowEpochMs?: () => number;
  remoteRandomToken?: () => string;
  registry?: DeviceRegistry;
  router?: CommandRouter;
};

export type ControllerServerRuntime = {
  server: Server;
  listen(port: number, host?: string): Promise<void>;
  close(): Promise<void>;
  address(): ReturnType<Server["address"]>;
};

export function createControllerServer(options: ControllerServerOptions): ControllerServerRuntime {
  if (!options.controllerApiToken) throw new Error("CONTROLLER_API_TOKEN_REQUIRED");
  if (!options.controllerId) throw new Error("CONTROLLER_ID_REQUIRED");

  const nowEpochMs = options.nowEpochMs ?? Date.now;
  const publicBaseUrl = options.publicBaseUrl ?? DEFAULT_PUBLIC_BASE_URL;
  const registry = options.registry ?? new DeviceRegistry();
  const router = options.router ?? new CommandRouter(options.store, registry);
  const mobileClaimNonces = new Map<string, number>();
  const recoveryNonces = new Map<string, number>();
  const recoveryTokens = new DeviceRecoveryTokenService(options.controllerApiToken);
  const isDeviceAuthorized = (deviceId: string) => {
    const device = options.store.getDevice(deviceId);
    return !!device && device.controllerId === options.controllerId && device.revokedAtEpochMs == null;
  };
  const oauth = new LocalCutOAuthService({
    issuerBaseUrl: publicBaseUrl,
    signingSecret: options.controllerApiToken,
    nowEpochMs,
    isDeviceAuthorized,
  });
  const browserSessions = new BrowserControlSessionService({
    signingSecret: `${options.controllerApiToken}:browser-control`,
    nowEpochMs,
    isDeviceAuthorized,
    ...(options.remoteRandomToken ? { randomToken: options.remoteRandomToken } : {}),
  });
  const browserControlHandler = createBrowserControlHttpHandler({
    sessions: options.sessions,
    browserSessions,
    router,
    controllerId: options.controllerId,
    publicBaseUrl,
    nowEpochMs,
    ...(options.remoteRandomToken ? { randomToken: options.remoteRandomToken } : {}),
  });
  const wss = new WebSocketServer({ noServer: true, clientTracking: false });
  const mcpHandler = createMcpHandler(() => createLocalCutMcpServer(
    router,
    options.controllerId,
    {
      claimPairing(code) {
        const claimed = options.pairing.claim(code, options.controllerId, nowEpochMs());
        return {
          claimed: true,
          deviceId: claimed.deviceId,
          expiresAtEpochMs: claimed.expiresAtEpochMs,
        };
      },
      revokeDevice(deviceId) {
        options.pairing.revokeDevice(deviceId, nowEpochMs());
        options.sessions.revoke(deviceId);
        registry.get(deviceId)?.close("device_revoked");
        return { revoked: true, deviceId };
      },
    },
  ));
  const nodeMcpHandler = toNodeHandler(mcpHandler);

  const server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url ?? "/", "http://localcut.invalid");
      if (await browserControlHandler(req, res, url)) return;

      if (req.method === "GET" && url.pathname === "/health") {
        return sendJson(res, 200, { ok: true, service: "localcut-bridge-controller" });
      }

      if (req.method === "GET" && url.pathname === "/.well-known/assetlinks.json") {
        return sendJson(res, 200, ANDROID_ASSET_LINKS);
      }

      if (req.method === "GET" && url.pathname === "/.well-known/oauth-protected-resource") {
        return sendJson(res, 200, {
          resource: oauth.resource,
          authorization_servers: [oauth.issuerBaseUrl],
          scopes_supported: [...oauth.scopes],
        });
      }

      if (req.method === "GET" && url.pathname === "/.well-known/oauth-authorization-server") {
        return sendJson(res, 200, {
          issuer: oauth.issuerBaseUrl,
          authorization_endpoint: `${oauth.issuerBaseUrl}/oauth/authorize`,
          token_endpoint: `${oauth.issuerBaseUrl}/oauth/token`,
          response_types_supported: ["code"],
          grant_types_supported: ["authorization_code", "refresh_token"],
          token_endpoint_auth_methods_supported: ["none"],
          code_challenge_methods_supported: ["S256"],
          scopes_supported: [...oauth.scopes],
          authorization_response_iss_parameter_supported: true,
        });
      }

      if (req.method === "GET" && url.pathname === "/oauth/authorize") {
        const pending = oauth.beginAuthorization({
          responseType: requiredSearchParam(url, "response_type", 32),
          clientId: requiredSearchParam(url, "client_id", 256),
          redirectUri: requiredSearchParam(url, "redirect_uri", 2_048),
          state: requiredSearchParam(url, "state", 2_048),
          codeChallenge: requiredSearchParam(url, "code_challenge", 256),
          codeChallengeMethod: requiredSearchParam(url, "code_challenge_method", 32),
          resource: requiredSearchParam(url, "resource", 2_048),
          scope: requiredSearchParam(url, "scope", 512),
        });
        const completionUrl = new URL("/oauth/complete", oauth.issuerBaseUrl);
        completionUrl.searchParams.set("requestId", pending.requestId);
        const deepLink = new URL("localcut://oauth/approve");
        deepLink.searchParams.set("requestId", pending.requestId);
        return sendHtml(res, 200, authorizationPage(deepLink.toString(), pending.expiresAtEpochMs));
      }

      if (req.method === "GET" && url.pathname === "/oauth/complete") {
        const requestId = requiredSearchParam(url, "requestId", 256);
        const completed = oauth.completeAuthorization(requestId);
        const callback = new URL(completed.redirectUri);
        callback.searchParams.set("code", completed.code);
        callback.searchParams.set("state", completed.state);
        callback.searchParams.set("iss", oauth.issuerBaseUrl);
        return sendRedirect(res, callback.toString());
      }

      if (url.pathname === "/mcp") {
        const token = bearerToken(req);
        try {
          if (!token) throw new Error("OAUTH_TOKEN_INVALID");
          oauth.verifyAccessToken(token, ["localcut:read", "localcut:write"]);
        } catch {
          return sendMcpAuthRequired(res, oauth.issuerBaseUrl);
        }
        await nodeMcpHandler(req, res);
        return;
      }

      if (req.method === "POST" && url.pathname === "/oauth/token") {
        const form = await readForm(req);
        const grantType = requiredFormString(form, "grant_type", 64);
        const result = grantType === "authorization_code" ?
          oauth.exchangeAuthorizationCode({
            grantType,
            code: requiredFormString(form, "code", 512),
            clientId: requiredFormString(form, "client_id", 256),
            redirectUri: requiredFormString(form, "redirect_uri", 2_048),
            codeVerifier: requiredFormString(form, "code_verifier", 256),
            resource: requiredFormString(form, "resource", 2_048),
          }) :
          grantType === "refresh_token" ?
            oauth.exchangeRefreshToken({
              grantType,
              refreshToken: requiredFormString(form, "refresh_token", 8_192),
              clientId: requiredFormString(form, "client_id", 256),
              resource: requiredFormString(form, "resource", 2_048),
            }) :
            (() => { throw new Error("OAUTH_GRANT_INVALID"); })();
        return sendJson(res, 200, {
          access_token: result.accessToken,
          refresh_token: result.refreshToken,
          token_type: result.tokenType,
          expires_in: result.expiresIn,
          scope: result.scope,
        });
      }

      if (req.method === "GET" && url.pathname === "/open") {
        if (url.search.length === 0 || !url.searchParams.get("action")) {
          return sendJson(res, 400, { code: "HANDOFF_ACTION_MISSING" });
        }
        if (Buffer.byteLength(url.search, "utf8") > MAX_HANDOFF_QUERY_BYTES) {
          return sendJson(res, 414, { code: "HANDOFF_URI_TOO_LONG" });
        }
        return sendRedirect(res, `localcut://command${url.search}`);
      }

      if (req.method !== "POST") {
        return sendJson(res, 404, { code: "NOT_FOUND" });
      }

      if (url.pathname === "/v1/oauth/device-approve") {
        const token = bearerToken(req);
        const session = token ? options.sessions.authenticateAccess(token, nowEpochMs()) : null;
        if (!session) {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
        const body = await readJson(req);
        const requestId = requiredString(body, "requestId", 256);
        oauth.approveAuthorization(requestId, session.deviceId);
        const completionUrl = new URL("/oauth/complete", oauth.issuerBaseUrl);
        completionUrl.searchParams.set("requestId", requestId);
        return sendJson(res, 200, { approved: true, completionUrl: completionUrl.toString() });
      }

      if (url.pathname === "/v1/pair/start") {
        const body = await readJson(req);
        const deviceId = requiredString(body, "deviceId", 128);
        const publicKeySpkiBase64 = requiredString(body, "publicKeySpkiBase64", 4_096);
        const result = options.pairing.start(deviceId, publicKeySpkiBase64, nowEpochMs());
        return sendJson(res, 200, result);
      }

      if (url.pathname === "/v1/pair/claim") {
        if (!isControllerAuthorized(req, options.controllerApiToken)) {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
        const body = await readJson(req);
        const code = requiredString(body, "code", 32);
        const result = options.pairing.claim(code, options.controllerId, nowEpochMs());
        return sendJson(res, 200, result);
      }

      if (url.pathname === "/v1/pair/mobile-claim") {
        const body = await readJson(req);
        const code = requiredString(body, "code", 32);
        const issuedAtEpochMs = requiredInteger(body, "issuedAtEpochMs");
        const nonceBase64 = requiredString(body, "nonceBase64", 256);
        const signatureBase64 = requiredString(body, "signatureBase64", 16_384);
        const now = nowEpochMs();
        if (Math.abs(now - issuedAtEpochMs) > MOBILE_CLAIM_MAX_AGE_MS) {
          throw new Error("PAIRING_MOBILE_CLAIM_EXPIRED");
        }
        const nonce = decodeBase64(nonceBase64, "PAIRING_MOBILE_CLAIM_NONCE_INVALID");
        if (nonce.length < 16 || nonce.length > 64) {
          throw new Error("PAIRING_MOBILE_CLAIM_NONCE_INVALID");
        }
        pruneNonces(mobileClaimNonces, now);
        const replayKey = `${code}:${nonceBase64}`;
        if (mobileClaimNonces.has(replayKey)) {
          throw new Error("PAIRING_MOBILE_CLAIM_REPLAY");
        }
        const pairing = options.store.getPairing(code);
        if (!pairing) throw new Error("PAIRING_NOT_FOUND");
        const device = options.store.getDevice(pairing.deviceId);
        if (!device || device.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");
        const publicKey = createPublicKey({
          key: Buffer.from(device.publicKeySpkiBase64, "base64"),
          format: "der",
          type: "spki",
        });
        const signedPayload = Buffer.from(
          `${MOBILE_CLAIM_PREFIX}\n${code}\n${issuedAtEpochMs}\n${nonceBase64}`,
          "utf8",
        );
        const valid = verify(
          "sha256",
          signedPayload,
          publicKey,
          Buffer.from(signatureBase64, "base64"),
        );
        if (!valid) throw new Error("PAIRING_SIGNATURE_INVALID");
        mobileClaimNonces.set(replayKey, now + MOBILE_CLAIM_MAX_AGE_MS);
        const result = options.pairing.claim(code, options.controllerId, now);
        return sendJson(res, 200, {
          claimed: true,
          deviceId: result.deviceId,
          challengeBase64: result.challengeBase64,
          expiresAtEpochMs: result.expiresAtEpochMs,
        });
      }

      if (url.pathname === "/v1/pair/status") {
        const body = await readJson(req);
        const code = requiredString(body, "code", 32);
        const signatureBase64 = optionalString(body, "signatureBase64", 16_384);
        if (signatureBase64) {
          const now = nowEpochMs();
          const completed = options.pairing.complete(code, signatureBase64, now);
          const issued = options.sessions.issue(completed.deviceId, now);
          const device = options.store.getDevice(completed.deviceId);
          if (!device || device.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");
          const recoveryToken = recoveryTokens.issue(device, options.controllerId, now);
          return sendJson(res, 200, { state: "PAIRED", ...issued, recoveryToken });
        }
        const status = options.pairing.status(code, nowEpochMs());
        const pairing = options.store.getPairing(code);
        return sendJson(res, 200, {
          ...status,
          challengeBase64: status.state === "CLAIMED" ? pairing?.challengeBase64 ?? null : null,
        });
      }

      if (url.pathname === "/v1/session/refresh") {
        const body = await readJson(req);
        const refreshToken = requiredString(body, "refreshToken", 4_096);
        try {
          const now = nowEpochMs();
          const refreshed = options.sessions.refresh(refreshToken, now);
          const device = options.store.getDevice(refreshed.deviceId);
          if (!device || device.revokedAtEpochMs != null) throw new Error("AUTH_REQUIRED");
          const recoveryToken = recoveryTokens.issue(device, options.controllerId, now);
          return sendJson(res, 200, { ...refreshed, recoveryToken });
        } catch {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
      }

      if (url.pathname === "/v1/session/recover") {
        const body = await readJson(req);
        const deviceId = requiredString(body, "deviceId", 128);
        const publicKeySpkiBase64 = requiredString(body, "publicKeySpkiBase64", 16_384);
        const recoveryToken = requiredString(body, "recoveryToken", 4_096);
        const issuedAtEpochMs = requiredInteger(body, "issuedAtEpochMs");
        const nonceBase64 = requiredString(body, "nonceBase64", 256);
        const signatureBase64 = requiredString(body, "signatureBase64", 16_384);
        const now = nowEpochMs();
        if (Math.abs(now - issuedAtEpochMs) > SESSION_RECOVERY_MAX_AGE_MS) {
          throw new Error("RECOVERY_PROOF_EXPIRED");
        }
        const nonce = decodeBase64(nonceBase64, "RECOVERY_NONCE_INVALID");
        if (nonce.length < 16 || nonce.length > 64) throw new Error("RECOVERY_NONCE_INVALID");
        pruneNonces(recoveryNonces, now);
        const replayKey = `${deviceId}:${nonceBase64}`;
        if (recoveryNonces.has(replayKey)) throw new Error("RECOVERY_REPLAY");

        const claims = recoveryTokens.verify(recoveryToken, options.controllerId, now);
        if (claims.deviceId !== deviceId) throw new Error("RECOVERY_TOKEN_INVALID");
        if (claims.publicKeySha256 !== publicKeySha256(publicKeySpkiBase64)) {
          throw new Error("DEVICE_IDENTITY_MISMATCH");
        }
        let publicKey;
        try {
          publicKey = createPublicKey({
            key: Buffer.from(publicKeySpkiBase64, "base64"),
            format: "der",
            type: "spki",
          });
        } catch {
          throw new Error("DEVICE_IDENTITY_INVALID");
        }
        const signedPayload = Buffer.from(
          `${SESSION_RECOVERY_PREFIX}\n${deviceId}\n${issuedAtEpochMs}\n${nonceBase64}\n${recoveryToken}`,
          "utf8",
        );
        let valid = false;
        try {
          valid = verify(
            "sha256",
            signedPayload,
            publicKey,
            Buffer.from(signatureBase64, "base64"),
          );
        } catch {
          valid = false;
        }
        if (!valid) throw new Error("RECOVERY_SIGNATURE_INVALID");

        const existing = options.store.getDevice(deviceId);
        if (existing?.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");
        if (existing && existing.publicKeySpkiBase64 !== publicKeySpkiBase64) {
          throw new Error("DEVICE_IDENTITY_MISMATCH");
        }
        if (existing?.controllerId != null && existing.controllerId !== options.controllerId) {
          throw new Error("AUTH_REQUIRED");
        }

        recoveryNonces.set(replayKey, now + SESSION_RECOVERY_MAX_AGE_MS);
        if (!existing) {
          options.store.upsertDevice({
            deviceId,
            publicKeySpkiBase64,
            controllerId: options.controllerId,
            revokedAtEpochMs: null,
          });
        } else if (existing.controllerId == null) {
          options.store.bindDevice(deviceId, options.controllerId);
        }
        const issued = options.sessions.issue(deviceId, now);
        const restoredDevice = options.store.getDevice(deviceId);
        if (!restoredDevice) throw new Error("INTERNAL_ERROR");
        const nextRecoveryToken = recoveryTokens.issue(restoredDevice, options.controllerId, now);
        options.store.appendAudit("session_recovered", deviceId, "{}", now);
        return sendJson(res, 200, { ...issued, recoveryToken: nextRecoveryToken });
      }

      if (url.pathname === "/v1/device/unpair-self") {
        const token = bearerToken(req);
        const session = token ? options.sessions.authenticateAccess(token, nowEpochMs()) : null;
        if (!session) {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
        options.pairing.unpairDevice(session.deviceId, nowEpochMs());
        options.sessions.revoke(session.deviceId);
        registry.get(session.deviceId)?.close("device_unpaired");
        return sendJson(res, 200, { unpaired: true, deviceId: session.deviceId });
      }

      if (url.pathname === "/v1/device/revoke") {
        if (!isControllerAuthorized(req, options.controllerApiToken)) {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
        const body = await readJson(req);
        const deviceId = requiredString(body, "deviceId", 128);
        options.pairing.revokeDevice(deviceId, nowEpochMs());
        options.sessions.revoke(deviceId);
        registry.get(deviceId)?.close("device_revoked");
        return sendJson(res, 200, { revoked: true, deviceId });
      }

      return sendJson(res, 404, { code: "NOT_FOUND" });
    } catch (error) {
      const code = safeErrorCode(error);
      const status = code === "BODY_TOO_LARGE" ? 413 :
        code.includes("NOT_FOUND") ? 404 :
        code.includes("REVOKED") ? 403 :
        code === "AUTH_REQUIRED" ? 401 : 400;
      return sendJson(res, status, { code });
    }
  });

  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url ?? "/", "http://localcut.invalid");
    if (url.pathname !== "/v1/device/connect") {
      socket.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    const token = bearerToken(req);
    const session = token ? options.sessions.authenticateAccess(token, nowEpochMs()) : null;
    if (!session) {
      socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, ws => {
      attachDeviceSocket(session.deviceId, ws, registry);
    });
  });

  let closePromise: Promise<void> | null = null;
  return {
    server,
    listen(port: number, host = "0.0.0.0") {
      return new Promise<void>((resolve, reject) => {
        const onError = (error: Error) => {
          server.off("listening", onListening);
          reject(error);
        };
        const onListening = () => {
          server.off("error", onError);
          resolve();
        };
        server.once("error", onError);
        server.once("listening", onListening);
        server.listen(port, host);
      });
    },
    close() {
      if (closePromise) return closePromise;
      registry.closeAll("controller_shutdown");
      wss.close();
      closePromise = new Promise<void>((resolve, reject) => {
        server.close(error => error ? reject(error) : resolve());
      });
      return closePromise;
    },
    address() {
      return server.address();
    },
  };
}

function attachDeviceSocket(deviceId: string, ws: WebSocket, registry: DeviceRegistry): void {
  const channel = new WebSocketDeviceChannel(ws as unknown as DeviceSocket);
  registry.register(deviceId, channel);
  ws.once("close", () => registry.unregister(deviceId, channel));
}

function isControllerAuthorized(req: IncomingMessage, expectedToken: string): boolean {
  const actual = bearerToken(req);
  if (!actual) return false;
  const actualBuffer = Buffer.from(actual, "utf8");
  const expectedBuffer = Buffer.from(expectedToken, "utf8");
  if (actualBuffer.length !== expectedBuffer.length) return false;
  return timingSafeEqual(actualBuffer, expectedBuffer);
}

function bearerToken(req: IncomingMessage): string | null {
  const authorization = req.headers.authorization;
  if (!authorization?.startsWith("Bearer ")) return null;
  const token = authorization.slice("Bearer ".length).trim();
  return token || null;
}

async function readJson(req: IncomingMessage): Promise<Record<string, unknown>> {
  const body = await readBody(req);
  if (body.length === 0) return {};
  const parsed = JSON.parse(body.toString("utf8")) as unknown;
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("INVALID_JSON_BODY");
  return parsed as Record<string, unknown>;
}

async function readForm(req: IncomingMessage): Promise<URLSearchParams> {
  const body = await readBody(req);
  return new URLSearchParams(body.toString("utf8"));
}

async function readBody(req: IncomingMessage): Promise<Buffer> {
  const contentLength = Number(req.headers["content-length"] ?? 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_JSON_BODY_BYTES) {
    throw new Error("BODY_TOO_LARGE");
  }
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > MAX_JSON_BODY_BYTES) throw new Error("BODY_TOO_LARGE");
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

function requiredSearchParam(url: URL, key: string, maxLength: number): string {
  const value = url.searchParams.get(key);
  if (!value || value.length > maxLength) throw new Error("INVALID_ARGUMENT");
  return value;
}

function requiredFormString(form: URLSearchParams, key: string, maxLength: number): string {
  const value = form.get(key);
  if (!value || value.length > maxLength) throw new Error("INVALID_ARGUMENT");
  return value;
}

function requiredString(body: Record<string, unknown>, key: string, maxLength: number): string {
  const value = body[key];
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new Error("INVALID_ARGUMENT");
  }
  return value;
}

function requiredInteger(body: Record<string, unknown>, key: string): number {
  const value = body[key];
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new Error("INVALID_ARGUMENT");
  }
  return value;
}

function optionalString(body: Record<string, unknown>, key: string, maxLength: number): string | null {
  const value = body[key];
  if (value == null) return null;
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new Error("INVALID_ARGUMENT");
  }
  return value;
}

function decodeBase64(value: string, errorCode: string): Buffer {
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value) || value.length % 4 !== 0) {
    throw new Error(errorCode);
  }
  return Buffer.from(value, "base64");
}

function pruneNonces(nonces: Map<string, number>, nowEpochMs: number): void {
  for (const [key, expiresAtEpochMs] of nonces) {
    if (expiresAtEpochMs < nowEpochMs) nonces.delete(key);
  }
}

function authorizationPage(deepLink: string, expiresAtEpochMs: number): string {
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Autorizar LocalCut</title></head><body><main><h1>Autorizar ChatGPT no LocalCut</h1><p>Confirme esta conexão no aplicativo LocalCut já pareado.</p><p><a href="${escapeHtml(deepLink)}">Abrir LocalCut e autorizar</a></p><p>Solicitação válida até ${new Date(expiresAtEpochMs).toISOString()}.</p></main></body></html>`;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("\"", "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function sendMcpAuthRequired(res: ServerResponse, issuerBaseUrl: string): void {
  if (res.writableEnded) return;
  const body = JSON.stringify({ code: "AUTH_REQUIRED" });
  res.writeHead(401, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "www-authenticate": `Bearer resource_metadata="${issuerBaseUrl}/.well-known/oauth-protected-resource"`,
  });
  res.end(body);
}

function sendRedirect(res: ServerResponse, location: string): void {
  if (res.writableEnded) return;
  res.writeHead(302, {
    location,
    "content-length": "0",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end();
}

function sendHtml(res: ServerResponse, status: number, body: string): void {
  if (res.writableEnded) return;
  res.writeHead(status, {
    "content-type": "text/html; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
  });
  res.end(body);
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  if (res.writableEnded) return;
  const encoded = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(encoded),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(encoded);
}

function safeErrorCode(error: unknown): string {
  if (!(error instanceof Error)) return "INVALID_ARGUMENT";
  const message = error.message;
  if (/^[A-Z0-9_]{3,64}$/.test(message)) return message;
  return "INVALID_ARGUMENT";
}

export function startControllerServerFromEnv(): ControllerServerRuntime {
  const dbPath = process.env.LOCALCUT_BRIDGE_DB_PATH ?? "./localcut-bridge.sqlite";
  const controllerApiToken = process.env.CONTROLLER_API_TOKEN ?? "";
  const controllerId = process.env.CONTROLLER_ID ?? "";
  const publicBaseUrl = process.env.PUBLIC_BASE_URL ?? DEFAULT_PUBLIC_BASE_URL;
  const port = Number(process.env.PORT ?? "3000");
  if (!Number.isInteger(port) || port <= 0 || port > 65_535) throw new Error("PORT_INVALID");

  const store = new SqliteBridgeStore(dbPath);
  const sessionStore: DeviceSessionStore = new SqliteSessionStore(dbPath);
  const pairing = new PairingService(store);
  const sessions = new DeviceSessionService(store, sessionStore);
  const runtime = createControllerServer({
    controllerApiToken,
    controllerId,
    publicBaseUrl,
    store,
    pairing,
    sessions,
  });
  void runtime.listen(port).then(() => {
    console.log(`LocalCut Bridge controller listening on port ${port}`);
  });
  return runtime;
}

const entryUrl = process.argv[1] ? pathToFileURL(process.argv[1]).href : null;
if (entryUrl === import.meta.url) {
  startControllerServerFromEnv();
}
