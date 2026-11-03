import type { IncomingMessage, ServerResponse } from "node:http";

const DEFAULT_TTL_MS = 5 * 60 * 1_000;
const DEFAULT_MAX_BYTES = 20 * 1024 * 1024;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const MEDIA_PATH_PREFIX = "/v1/mobile-media/";

type TransientMediaEntry = {
  contentType: string;
  bytes: Buffer;
  expiresAtEpochMs: number;
};

export type TransientMediaHttpOptions = {
  nowEpochMs?: () => number;
  ttlMs?: number;
  maxBytes?: number;
};

export function createTransientMediaHttpHandler(options: TransientMediaHttpOptions = {}) {
  const nowEpochMs = options.nowEpochMs ?? Date.now;
  const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
  const maxBytes = options.maxBytes ?? DEFAULT_MAX_BYTES;
  const entries = new Map<string, TransientMediaEntry>();

  if (!Number.isSafeInteger(ttlMs) || ttlMs <= 0) throw new Error("INVALID_TTL");
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) throw new Error("INVALID_MAX_BYTES");

  return async function handleTransientMedia(
    req: IncomingMessage,
    res: ServerResponse,
    url: URL,
  ): Promise<boolean> {
    if (!url.pathname.startsWith(MEDIA_PATH_PREFIX)) return false;

    const token = url.pathname.slice(MEDIA_PATH_PREFIX.length);
    if (!TOKEN_PATTERN.test(token)) {
      return sendJson(res, 400, { code: "MEDIA_TOKEN_INVALID" });
    }

    const now = nowEpochMs();
    pruneExpired(entries, now);

    if (req.method === "POST") {
      const contentType = normalizedContentType(req.headers["content-type"]);
      if (!isAllowedMediaType(contentType)) {
        return sendJson(res, 415, { code: "MEDIA_TYPE_UNSUPPORTED" });
      }
      if (entries.has(token)) {
        return sendJson(res, 409, { code: "MEDIA_TOKEN_ALREADY_USED" });
      }

      let bytes: Buffer;
      try {
        bytes = await readRawBody(req, maxBytes);
      } catch (error) {
        if (error instanceof Error && error.message === "BODY_TOO_LARGE") {
          return sendJson(res, 413, { code: "BODY_TOO_LARGE" });
        }
        throw error;
      }
      if (bytes.length === 0) {
        return sendJson(res, 400, { code: "MEDIA_BODY_EMPTY" });
      }

      const expiresAtEpochMs = now + ttlMs;
      entries.set(token, { contentType, bytes, expiresAtEpochMs });
      return sendJson(res, 201, {
        downloadPath: `${MEDIA_PATH_PREFIX}${token}`,
        expiresAtEpochMs,
      });
    }

    if (req.method === "GET") {
      const entry = entries.get(token);
      if (!entry || entry.expiresAtEpochMs <= now) {
        entries.delete(token);
        return sendJson(res, 404, { code: "MEDIA_NOT_FOUND" });
      }

      entries.delete(token);
      res.writeHead(200, {
        "content-type": entry.contentType,
        "content-length": entry.bytes.length,
        "cache-control": "no-store",
        "x-content-type-options": "nosniff",
      });
      res.end(entry.bytes);
      return true;
    }

    res.writeHead(405, {
      allow: "GET, POST",
      "cache-control": "no-store",
      "content-length": "0",
      "x-content-type-options": "nosniff",
    });
    res.end();
    return true;
  };
}

function normalizedContentType(value: string | string[] | undefined): string {
  const raw = Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
  return raw.split(";", 1)[0]?.trim().toLowerCase() ?? "";
}

function isAllowedMediaType(contentType: string): boolean {
  return contentType.startsWith("image/") ||
    contentType.startsWith("audio/") ||
    contentType.startsWith("video/");
}

async function readRawBody(req: IncomingMessage, maxBytes: number): Promise<Buffer> {
  const contentLength = Number(req.headers["content-length"] ?? 0);
  if (Number.isFinite(contentLength) && contentLength > maxBytes) {
    throw new Error("BODY_TOO_LARGE");
  }

  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > maxBytes) throw new Error("BODY_TOO_LARGE");
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

function pruneExpired(entries: Map<string, TransientMediaEntry>, nowEpochMs: number): void {
  for (const [token, entry] of entries) {
    if (entry.expiresAtEpochMs <= nowEpochMs) entries.delete(token);
  }
}

function sendJson(res: ServerResponse, status: number, value: unknown): true {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(body);
  return true;
}
