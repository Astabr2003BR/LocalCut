import { createHash, randomBytes } from "node:crypto";
import { isIP } from "node:net";
import { fetchPinnedAttachment } from "./attachmentPinnedFetch.ts";
import { assertAttachmentMediaSignature } from "./attachmentMediaSignature.ts";
import { assertPortablePackageDirectory, PORTABLE_PACKAGE_MIME } from "./portablePackagePreflight.ts";
import {
  isAllowedTransientMediaType,
  type TransientMediaStore,
} from "./transientMediaHttp.ts";

export type OpenAIFileParam = {
  download_url: string;
  file_id: string;
  mime_type?: string;
  file_name?: string;
  expected_sha256?: string;
};

export type AttachmentStageResult = {
  fileId: string;
  fileName: string | null;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  expiresAtEpochMs: number;
  relayUrl: string;
  handoffUrl: string;
};

type FetchImpl = (input: string | URL, init?: RequestInit) => Promise<Response>;

export type AttachmentStagerOptions = {
  publicBaseUrl: string;
  transientMediaStore: TransientMediaStore;
  fetchImpl?: FetchImpl;
  allowedSourceOrigins?: readonly string[];
  tokenGenerator?: () => string;
};

export function createAttachmentStager(options: AttachmentStagerOptions) {
  const publicBaseUrl = normalizePublicBaseUrl(options.publicBaseUrl);
  const fetchImpl = options.fetchImpl ?? fetchPinnedAttachment;
  const allowedOrigins = options.allowedSourceOrigins === undefined ? undefined : new Set(
    options.allowedSourceOrigins.map((value) => {
      const url = validateSourceUrl(value);
      if (url.pathname !== "/" || url.search || url.hostname.endsWith(".")) {
        throw new Error("ATTACHMENT_SOURCE_ORIGIN_INVALID");
      }
      return url.origin;
    }),
  );
  const checkOrigin = (url: URL) => {
    if (allowedOrigins && !allowedOrigins.has(url.origin)) {
      throw new Error("ATTACHMENT_SOURCE_NOT_ALLOWED");
    }
  };
  const tokenGenerator = options.tokenGenerator ?? (() => randomBytes(32).toString("base64url"));

  return async function stageAttachment(file: OpenAIFileParam, packageOptions?: { authorize: () => void }): Promise<AttachmentStageResult> {
    const packageMode = packageOptions !== undefined;
    packageOptions?.authorize();
    if (packageMode && (normalizedContentType(file.mime_type ?? "") !== PORTABLE_PACKAGE_MIME ||
        !file.file_name?.toLowerCase().endsWith(".localcut"))) throw new Error("PACKAGE_ATTACHMENT_TYPE_INVALID");
    const expected = file.expected_sha256;
    if (expected !== undefined && (typeof expected !== "string" || !/^[a-f0-9]{64}$/.test(expected))) {
      throw new Error("ATTACHMENT_HASH_INVALID");
    }
    const sourceUrl = validateSourceUrl(file.download_url);
    checkOrigin(sourceUrl);

    let response: Response;
    try {
      response = await fetchImpl(sourceUrl, {
        method: "GET",
        redirect: "error",
        signal: AbortSignal.timeout(30_000),
        headers: { accept: packageMode ? PORTABLE_PACKAGE_MIME + ",application/zip,application/octet-stream" : "image/*,audio/*,video/*,application/octet-stream" },
      });
    } catch {
      throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
    }

    let mimeType: string;
    let bytes: Buffer;
    try {
      if (response.status !== 200 || response.headers.has("content-range")) {
        throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
      }
      const encoding = response.headers.get("content-encoding");
      if (encoding && encoding.trim().toLowerCase() !== "identity") {
        throw new Error("ATTACHMENT_CONTENT_ENCODING_UNSUPPORTED");
      }
      if (response.url) {
        const finalUrl = validateSourceUrl(response.url);
        checkOrigin(finalUrl);
        if (finalUrl.origin !== sourceUrl.origin) throw new Error("ATTACHMENT_SOURCE_NOT_ALLOWED");
      }

      const responseMime = normalizedContentType(response.headers.get("content-type") ?? "");
      if (packageMode) {
        if (![PORTABLE_PACKAGE_MIME, "application/zip", "application/octet-stream"].includes(responseMime)) {
          throw new Error("PACKAGE_ATTACHMENT_TYPE_INVALID");
        }
        mimeType = PORTABLE_PACKAGE_MIME;
      } else {
        mimeType = resolveMediaType(responseMime, normalizedContentType(file.mime_type ?? ""));
      }
      bytes = await readBoundedResponseBody(response, Math.min(options.transientMediaStore.maxBytes, packageMode ? 20 * 1024 * 1024 : Number.MAX_SAFE_INTEGER));
      if (bytes.length === 0) throw new Error("ATTACHMENT_BODY_EMPTY");
      if (packageMode) assertPortablePackageDirectory(bytes);
      else assertAttachmentMediaSignature(mimeType, bytes);
    } catch (error) {
      // Release the upstream connection even when headers fail validation.
      // Cancellation is best effort and must not mask the original error.
      if (response.body && !response.body.locked) {
        void response.body.cancel().catch(() => {});
      }
      throw error;
    }

    const sha256 = createHash("sha256").update(bytes).digest("hex");
    if (expected !== undefined && expected !== sha256) {
      throw new Error("ATTACHMENT_HASH_MISMATCH");
    }
    // OAuth may expire or be revoked while the external download runs.
    packageOptions?.authorize();
    const token = tokenGenerator();
    let staged;
    try {
      staged = options.transientMediaStore.stage(token, mimeType, bytes, packageOptions?.authorize);
    } catch (error) {
      if (error instanceof Error && error.message === "BODY_TOO_LARGE") {
        throw new Error("ATTACHMENT_TOO_LARGE");
      }
      throw error;
    }

    const relayUrl = new URL(staged.downloadPath, publicBaseUrl).toString();
    const handoff = new URL("/open", publicBaseUrl);
    handoff.searchParams.set("action", packageMode ? "open_package" : "import_url");
    handoff.searchParams.set("url", relayUrl);
    if (packageMode) {
      handoff.searchParams.set("sha256", sha256);
      handoff.searchParams.set("bytes", String(bytes.length));
    }
    handoff.searchParams.set("returnTo", "chatgpt");

    return {
      fileId: file.file_id,
      fileName: file.file_name ?? null,
      mimeType,
      sizeBytes: bytes.length,
      sha256,
      expiresAtEpochMs: staged.expiresAtEpochMs,
      relayUrl,
      handoffUrl: handoff.toString(),
    };
  };
}

function normalizePublicBaseUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("PUBLIC_BASE_URL_INVALID");
  }
  if (url.protocol !== "https:" || url.username || url.password) {
    throw new Error("PUBLIC_BASE_URL_INVALID");
  }
  url.pathname = "/";
  url.search = "";
  url.hash = "";
  return url;
}

function validateSourceUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("ATTACHMENT_HTTPS_REQUIRED");
  }
  if (url.protocol !== "https:" || url.username || url.password) {
    throw new Error("ATTACHMENT_HTTPS_REQUIRED");
  }
  // Reject literal IPs (including WHATWG-normalized IPv4 aliases), local names
  // and nonstandard ports before fetch. This is defense in depth, not a DNS
  // rebinding defense: a public hostname may still resolve to a private IP.
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  if (url.port || isIP(host.replace(/^\[|\]$/g, "")) !== 0 ||
      !host.includes(".") || host === "localhost" ||
      /\.(localhost|local|internal|home\.arpa)$/.test(host) ||
      url.hash) {
    throw new Error("ATTACHMENT_SOURCE_NOT_ALLOWED");
  }
  return url;
}

function resolveMediaType(responseMime: string, declaredMime: string): string {
  const responseAllowed = responseMime !== PORTABLE_PACKAGE_MIME && isAllowedTransientMediaType(responseMime);
  const declaredAllowed = declaredMime !== PORTABLE_PACKAGE_MIME && isAllowedTransientMediaType(declaredMime);

  if (responseAllowed) {
    if (declaredAllowed && mediaFamily(responseMime) !== mediaFamily(declaredMime)) {
      throw new Error("ATTACHMENT_MIME_MISMATCH");
    }
    return responseMime;
  }

  if ((responseMime === "" || responseMime === "application/octet-stream") && declaredAllowed) {
    return declaredMime;
  }

  throw new Error("ATTACHMENT_MEDIA_TYPE_UNSUPPORTED");
}

function mediaFamily(contentType: string): string {
  return contentType.split("/", 1)[0] ?? "";
}

function normalizedContentType(value: string): string {
  return value.split(";", 1)[0]?.trim().toLowerCase() ?? "";
}

async function readBoundedResponseBody(response: Response, maxBytes: number): Promise<Buffer> {
  const contentLengthRaw = response.headers.get("content-length");
  let declaredLength: number | undefined;
  if (contentLengthRaw !== null) {
    if (!/^\d+$/.test(contentLengthRaw)) throw new Error("ATTACHMENT_LENGTH_INVALID");
    declaredLength = Number(contentLengthRaw);
    if (!Number.isSafeInteger(declaredLength) || declaredLength > maxBytes) {
      throw new Error("ATTACHMENT_TOO_LARGE");
    }
  }

  if (!response.body) {
    if (declaredLength !== undefined && declaredLength !== 0) throw new Error("ATTACHMENT_LENGTH_MISMATCH");
    return Buffer.alloc(0);
  }
  const reader = response.body.getReader();
  const chunks: Buffer[] = [];
  let total = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = Buffer.from(value);
      total += chunk.length;
      if (total > maxBytes) {
        throw new Error("ATTACHMENT_TOO_LARGE");
      }
      if (declaredLength !== undefined && total > declaredLength) throw new Error("ATTACHMENT_LENGTH_MISMATCH");
      chunks.push(chunk);
    }
  } catch (error) {
    void reader.cancel().catch(() => {});
    if (error instanceof Error && ["ATTACHMENT_TOO_LARGE", "ATTACHMENT_LENGTH_MISMATCH"].includes(error.message)) throw error;
    throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
  } finally {
    reader.releaseLock();
  }

  if (declaredLength !== undefined && total !== declaredLength) throw new Error("ATTACHMENT_LENGTH_MISMATCH");
  return Buffer.concat(chunks, total);
}
