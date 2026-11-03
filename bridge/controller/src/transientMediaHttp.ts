import type { IncomingMessage, ServerResponse } from "node:http";

export const DEFAULT_TRANSIENT_MEDIA_TTL_MS = 5 * 60 * 1_000;
export const DEFAULT_TRANSIENT_MEDIA_MAX_BYTES = 20 * 1024 * 1024;
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

export type TransientMediaStageResult = {
  downloadPath: string;
  expiresAtEpochMs: number;
};

export type TransientMediaTakeResult = {
  contentType: string;
  bytes: Buffer;
};

export type TransientMediaStore = {
  readonly maxBytes: number;
  stage(token: string, contentType: string, bytes: Buffer | Uint8Array): TransientMediaStageResult;
  take(token: string): TransientMediaTakeResult | null;
};

export function createTransientMediaStore(options: TransientMediaHttpOptions = {}): TransientMediaStore {
  const nowEpochMs = options.nowEpochMs ?? Date.now;
  const ttlMs = options.ttlMs ?? DEFAULT_TRANSIENT_MEDIA_TTL_MS;
  const maxBytes = options.maxBytes ?? DEFAULT_TRANSIENT_MEDIA_MAX_BYTES;
  const entries = new Map<string, TransientMediaEntry>();

  if (!Number.isSafeInteger(ttlMs) || ttlMs <= 0) throw new Error("INVALID_TTL");
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) throw new Error("INVALID_MAX_BYTES");

  return {
    maxBytes,
    stage(token, rawContentType, inputBytes) {
      if (!TOKEN_PATTERN.test(token)) throw new Error("MEDIA_TOKEN_INVALID");
      const contentType = normalizedContentType(rawContentType);
      if (!isAllowedTransientMediaType(contentType)) throw new Error("MEDIA_TYPE_UNSUPPORTED");

      const bytes = Buffer.from(inputBytes);
      if (bytes.length === 0) throw new Error("MEDIA_BODY_EMPTY");
      if (bytes.length > maxBytes) throw new Error("BODY_TOO_LARGE");

      const now = nowEpochMs();
      pruneExpired(entries, now);
      if (entries.has(token)) throw new Error("MEDIA_TOKEN_ALREADY_USED");

      const expiresAtEpochMs = now + ttlMs;
      entries.set(token, { contentType, bytes, expiresAtEpochMs });
      return {
        downloadPath: `${MEDIA_PATH_PREFIX}${token}`,
        expiresAtEpochMs,
      };
    },
    take(token) {
      if (!TOKEN_PATTERN.test(token)) return null;
      const now = nowEpochMs();
      pruneExpired(entries, now);
      const entry = entries.get(token);
      if (!entry || entry.expiresAtEpochMs <= now) {
        entries.delete(token);
        return null;
      }
      entries.delete(token);
      return {
        contentType: entry.contentType,
        bytes: Buffer.from(entry.bytes),
      };
    },
  };
}

export function createTransientMediaHttpHandler(
  options: TransientMediaHttpOptions = {},
  store: TransientMediaStore = createTransientMediaStore(options),
) {
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

    if (req.method === "GET") {
      const entry = store.take(token);
      if (!entry) {
        return sendJson(res, 404, { code: "MEDIA_NOT_FOUND" });
      }

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
      allow: "GET",
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

export function isAllowedTransientMediaType(contentType: string): boolean {
  return contentType.startsWith("image/") ||
    contentType.startsWith("audio/") ||
    contentType.startsWith("video/");
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
