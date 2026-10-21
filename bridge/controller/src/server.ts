import { timingSafeEqual } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { pathToFileURL } from "node:url";
import { createMcpHandler } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import { WebSocketServer, type WebSocket } from "ws";
import { CommandRouter, DeviceRegistry } from "./commandRouter.ts";
import { WebSocketDeviceChannel, type DeviceSocket } from "./deviceChannel.ts";
import { createLocalCutMcpServer } from "./mcp.ts";
import { PairingService } from "./pairing.ts";
import { DeviceSessionService, SqliteSessionStore, type DeviceSessionStore } from "./session.ts";
import { SqliteBridgeStore, type BridgeStore } from "./store.ts";

const MAX_JSON_BODY_BYTES = 64 * 1024;
const MAX_HANDOFF_QUERY_BYTES = 8 * 1024;
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
  store: BridgeStore;
  pairing: PairingService;
  sessions: DeviceSessionService;
  nowEpochMs?: () => number;
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
  const registry = options.registry ?? new DeviceRegistry();
  const router = options.router ?? new CommandRouter(options.store, registry);
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
      if (req.method === "GET" && url.pathname === "/health") {
        return sendJson(res, 200, { ok: true, service: "localcut-bridge-controller" });
      }

      if (req.method === "GET" && url.pathname === "/.well-known/assetlinks.json") {
        return sendJson(res, 200, ANDROID_ASSET_LINKS);
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

      if (url.pathname === "/mcp") {
        if (!isControllerAuthorized(req, options.controllerApiToken)) {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
        await nodeMcpHandler(req, res);
        return;
      }

      if (req.method !== "POST") {
        return sendJson(res, 404, { code: "NOT_FOUND" });
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

      if (url.pathname === "/v1/pair/status") {
        const body = await readJson(req);
        const code = requiredString(body, "code", 32);
        const signatureBase64 = optionalString(body, "signatureBase64", 16_384);
        if (signatureBase64) {
          const completed = options.pairing.complete(code, signatureBase64, nowEpochMs());
          const issued = options.sessions.issue(completed.deviceId, nowEpochMs());
          return sendJson(res, 200, { state: "PAIRED", ...issued });
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
          const refreshed = options.sessions.refresh(refreshToken, nowEpochMs());
          return sendJson(res, 200, refreshed);
        } catch {
          return sendJson(res, 401, { code: "AUTH_REQUIRED" });
        }
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
        code.includes("REVOKED") ? 403 : 400;
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
  if (chunks.length === 0) return {};
  const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("INVALID_JSON_BODY");
  return parsed as Record<string, unknown>;
}

function requiredString(body: Record<string, unknown>, key: string, maxLength: number): string {
  const value = body[key];
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
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
  const port = Number(process.env.PORT ?? "3000");
  if (!Number.isInteger(port) || port <= 0 || port > 65_535) throw new Error("PORT_INVALID");

  const store = new SqliteBridgeStore(dbPath);
  const sessionStore: DeviceSessionStore = new SqliteSessionStore(dbPath);
  const pairing = new PairingService(store);
  const sessions = new DeviceSessionService(store, sessionStore);
  const runtime = createControllerServer({
    controllerApiToken,
    controllerId,
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
