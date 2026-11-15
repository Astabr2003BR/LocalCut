import { assertAttachmentMediaSignature } from "./attachmentMediaSignature.ts";
import type { BridgeResultEnvelope } from "./protocol.ts";

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function integer(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}
export function validExportReviewPayload(value: unknown): value is { jobId: string; sourceRevision: number } {
  return record(value) && Object.keys(value).length === 2 &&
    typeof value.jobId === "string" && value.jobId.length > 0 && value.jobId.length <= 128 && integer(value.sourceRevision);
}

// Read SOF dimensions from the bounded JPEG header. No general-purpose decoder or
// arbitrary file access. Reject malformed segment sizes and dimensions mismatches.
function jpegDimensions(bytes: Buffer): [number, number] | null {
  let i = 2;
  while (i < bytes.length - 2) {
    if (bytes[i++] !== 0xff) return null;
    while (i < bytes.length && bytes[i] === 0xff) i++;
    const marker = bytes[i++];
    if (marker === 0xda || marker === 0xd9 || i + 2 > bytes.length) return null;
    const length = bytes.readUInt16BE(i);
    if (length < 2 || i + length > bytes.length) return null;
    if ([0xc0, 0xc1, 0xc2].includes(marker)) {
      if (length < 8) return null;
      return [bytes.readUInt16BE(i + 5), bytes.readUInt16BE(i + 3)];
    }
    i += length;
  }
  return null;
}
type Frame = { requestedUs: number; width: number; height: number; mimeType: "image/jpeg"; base64: string };
function parseFrame(value: unknown): Frame | null {
  if (!record(value) || !integer(value.requestedUs) || !integer(value.width) || value.width < 1 || value.width > 320 ||
      !integer(value.height) || value.height < 1 || value.height > 320 || value.mimeType !== "image/jpeg" ||
      typeof value.base64 !== "string" || value.base64.length > Math.ceil(65536 / 3) * 4) return null;
  const bytes = Buffer.from(value.base64, "base64");
  if (bytes.length < 4 || bytes.length > 65536 || bytes.toString("base64") !== value.base64) return null;
  try { assertAttachmentMediaSignature("image/jpeg", bytes); } catch { return null; }
  const size = jpegDimensions(bytes);
  if (!size || size[0] !== value.width || size[1] !== value.height) return null;
  return { requestedUs: value.requestedUs, width: value.width, height: value.height, mimeType: "image/jpeg", base64: value.base64 };
}
function error(code: string, retryable = false) {
  const metadata = { success: false, code, retryable };
  return { isError: true, structuredContent: metadata, content: [{ type: "text" as const, text: JSON.stringify(metadata) }] };
}
export function exportReviewToolResult(response: BridgeResultEnvelope, projectId: string, payload: unknown) {
  if (!validExportReviewPayload(payload)) return error("INVALID_ARGUMENT");
  if (!response.success) return error(response.code ?? "INTERNAL_ERROR", response.retryable);
  const result = response.result;
  if (!record(result) || result.jobId !== payload.jobId || result.sourceProjectId !== projectId ||
      result.sourceRevision !== payload.sourceRevision || result.source !== "rendered_mp4" || result.frameAccurate !== false ||
      typeof result.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(result.sha256) ||
      !Array.isArray(result.frames) || result.frames.length < 1 || result.frames.length > 3) return error("PREVIEW_FAILED");
  const frames: Frame[] = [];
  for (const raw of result.frames) {
    const frame = parseFrame(raw);
    if (!frame || (frames.length > 0 && frame.requestedUs <= frames[frames.length - 1].requestedUs)) return error("PREVIEW_FAILED");
    frames.push(frame);
  }
  const metadata = { success: true, jobId: payload.jobId, sourceProjectId: projectId, sourceRevision: payload.sourceRevision,
    sha256: result.sha256, source: "rendered_mp4", frameAccurate: false, visualQualityVerified: false, audioSyncVerified: false,
    frames: frames.map(({ base64, ...frame }, imageIndex) => ({ ...frame, imageIndex })) };
  return { isError: false, structuredContent: metadata,
    content: [{ type: "text" as const, text: JSON.stringify(metadata) },
      ...frames.map(frame => ({ type: "image" as const, data: frame.base64, mimeType: frame.mimeType }))] };
}
