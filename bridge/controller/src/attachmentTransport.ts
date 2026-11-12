import { randomBytes } from "node:crypto";
import {
  isAllowedTransientMediaType,
  type TransientMediaStore,
} from "./transientMediaHttp.ts";

export type OpenAIFileParam = {
  download_url: string;
  file_id: string;
  mime_type?: string;
  file_name?: string;
};

export type AttachmentStageResult = {
  fileId: string;
  fileName: string | null;
  mimeType: string;
  sizeBytes: number;
  expiresAtEpochMs: number;
  relayUrl: string;
  handoffUrl: string;
};

type FetchImpl = (input: string | URL, init?: RequestInit) => Promise<Response>;

export type AttachmentStagerOptions = {
  publicBaseUrl: string;
  transientMediaStore: TransientMediaStore;
  fetchImpl?: FetchImpl;
  tokenGenerator?: () => string;
};

export function createAttachmentStager(options: AttachmentStagerOptions) {
  const publicBaseUrl = normalizePublicBaseUrl(options.publicBaseUrl);
  const fetchImpl = options.fetchImpl ?? fetch;
  const tokenGenerator = options.tokenGenerator ?? (() => randomBytes(32).toString("base64url"));

  return async function stageAttachment(file: OpenAIFileParam): Promise<AttachmentStageResult> {
    const sourceUrl = validateSourceUrl(file.download_url);

    let response: Response;
    try {
      response = await fetchImpl(sourceUrl, {
        method: "GET",
        redirect: "follow",
        headers: { accept: "image/*,audio/*,video/*,application/octet-stream" },
      });
    } catch {
      throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
    }

    if (!response.ok) throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
    if (response.url) validateSourceUrl(response.url);

    const mimeType = resolveMediaType(
      normalizedContentType(response.headers.get("content-type") ?? ""),
      normalizedContentType(file.mime_type ?? ""),
    );
    const bytes = await readBoundedResponseBody(response, options.transientMediaStore.maxBytes);
    if (bytes.length === 0) throw new Error("ATTACHMENT_BODY_EMPTY");

    const token = tokenGenerator();
    let staged;
    try {
      staged = options.transientMediaStore.stage(token, mimeType, bytes);
    } catch (error) {
      if (error instanceof Error && error.message === "BODY_TOO_LARGE") {
        throw new Error("ATTACHMENT_TOO_LARGE");
      }
      throw error;
    }

    const relayUrl = new URL(staged.downloadPath, publicBaseUrl).toString();
    const handoff = new URL("/open", publicBaseUrl);
    handoff.searchParams.set("action", "import_url");
    handoff.searchParams.set("url", relayUrl);
    handoff.searchParams.set("returnTo", "chatgpt");

    return {
      fileId: file.file_id,
      fileName: file.file_name ?? null,
      mimeType,
      sizeBytes: bytes.length,
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
  return url;
}

function resolveMediaType(responseMime: string, declaredMime: string): string {
  const responseAllowed = isAllowedTransientMediaType(responseMime);
  const declaredAllowed = isAllowedTransientMediaType(declaredMime);

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
  if (contentLengthRaw) {
    const contentLength = Number(contentLengthRaw);
    if (Number.isFinite(contentLength) && contentLength > maxBytes) {
      throw new Error("ATTACHMENT_TOO_LARGE");
    }
  }

  if (!response.body) return Buffer.alloc(0);
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
        await reader.cancel();
        throw new Error("ATTACHMENT_TOO_LARGE");
      }
      chunks.push(chunk);
    }
  } catch (error) {
    if (error instanceof Error && error.message === "ATTACHMENT_TOO_LARGE") throw error;
    throw new Error("ATTACHMENT_DOWNLOAD_FAILED");
  }

  return Buffer.concat(chunks, total);
}
