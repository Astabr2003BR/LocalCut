import { randomBytes, randomUUID } from "node:crypto";
import { setTimeout as sleep } from "node:timers/promises";
import { assertAttachmentMediaSignature } from "./attachmentMediaSignature.ts";
import type { CommandRouter } from "./commandRouter.ts";
import type { BridgeCapability, BridgeCommandEnvelope, BridgeResultEnvelope } from "./protocol.ts";

export const MAX_REVIEW_FRAMES = 4;
const MAX_FRAME_BYTES = 1_048_576;
const MAX_DRIFT_US = 250_000;
const MAX_EDGE = 720;
const REVIEW_BUDGET_MS = 25_000;
const MAX_ATTEMPTS = 3;

export type PreviewReviewInput = {
  deviceId: string;
  projectId: string;
  expectedRevision: number;
  positionsUs: number[];
};
type Frame = { requestedUs: number; positionUs: number; width: number; height: number; mimeType: "image/jpeg" | "image/png"; base64: string };
export type PreviewReviewResult =
  | { success: true; projectId: string; revision: number; frames: Frame[] }
  | { success: false; code: string; retryable: boolean };

function fail(code: string, retryable = false): PreviewReviewResult {
  return { success: false, code, retryable };
}
function record(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function nonnegative(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}
function validInput(input: PreviewReviewInput): boolean {
  return typeof input.deviceId === "string" && input.deviceId.length > 0 && input.deviceId.length <= 128 &&
    typeof input.projectId === "string" && input.projectId.length > 0 && input.projectId.length <= 256 &&
    nonnegative(input.expectedRevision) && Array.isArray(input.positionsUs) &&
    input.positionsUs.length >= 2 && input.positionsUs.length <= MAX_REVIEW_FRAMES &&
    input.positionsUs.every((p, i, all) => nonnegative(p) && (i === 0 || p > all[i - 1]));
}
function frameFrom(value: unknown, input: PreviewReviewInput, requestedUs: number): Frame | null {
  if (!record(value) || value.projectId !== input.projectId || value.revision !== input.expectedRevision ||
      !nonnegative(value.positionUs) || Math.abs(value.positionUs - requestedUs) > MAX_DRIFT_US ||
      !nonnegative(value.width) || value.width < 1 || value.width > MAX_EDGE ||
      !nonnegative(value.height) || value.height < 1 || value.height > MAX_EDGE ||
      (value.mimeType !== "image/jpeg" && value.mimeType !== "image/png") ||
      typeof value.base64 !== "string" || value.base64.length === 0 ||
      value.base64.length > Math.ceil(MAX_FRAME_BYTES / 3) * 4) return null;
  const bytes = Buffer.from(value.base64, "base64");
  if (bytes.length === 0 || bytes.length > MAX_FRAME_BYTES || bytes.toString("base64") !== value.base64) return null;
  try { assertAttachmentMediaSignature(value.mimeType, bytes); } catch { return null; }
  if (value.mimeType === "image/png" &&
      (bytes.readUInt32BE(16) !== value.width || bytes.readUInt32BE(20) !== value.height)) return null;
  return { requestedUs, positionUs: value.positionUs, width: value.width, height: value.height,
    mimeType: value.mimeType, base64: value.base64 };
}

export class PreviewReviewCoordinator {
  private readonly active = new Set<string>();
  // One coordinator per controller runtime, not one per MCP request.
  async collect(router: Pick<CommandRouter, "execute">, controllerId: string, input: PreviewReviewInput,
    authorizedDeviceId: () => string | null, now: () => number = Date.now,
    wait: (ms: number) => Promise<unknown> = sleep): Promise<PreviewReviewResult> {
    if (!validInput(input)) return fail("INVALID_ARGUMENT");
    if (authorizedDeviceId() !== input.deviceId) return fail("AUTH_REQUIRED");
    if (this.active.has(input.deviceId)) return fail("RATE_LIMITED", true);
    this.active.add(input.deviceId);
    const deadline = now() + REVIEW_BUDGET_MS;
    const send = async (capability: BridgeCapability, payload: Record<string, unknown> = {}): Promise<BridgeResultEnvelope> => {
      const issuedAtEpochMs = now();
      if (authorizedDeviceId() !== input.deviceId) throw new Error("AUTH_REQUIRED");
      if (issuedAtEpochMs >= deadline) throw new Error("REVIEW_TIMEOUT");
      const command: BridgeCommandEnvelope = {
        protocolVersion: 1, messageId: randomUUID(), kind: "command",
        deviceId: input.deviceId, projectId: input.projectId, expectedRevision: input.expectedRevision,
        issuedAtEpochMs, expiresAtEpochMs: deadline, nonce: randomBytes(18).toString("base64url"),
        capability, payload,
      };
      const result = await router.execute(controllerId, command);
      if (authorizedDeviceId() !== input.deviceId) throw new Error("AUTH_REQUIRED");
      if (now() >= deadline) throw new Error("REVIEW_TIMEOUT");
      if (result.messageId !== command.messageId) throw new Error("INTERNAL_ERROR");
      if (result.success && result.projectRevision !== input.expectedRevision) throw new Error("REVISION_CONFLICT");
      return result;
    };
    const checkProject = async () => {
      const state = await send("get_project_state");
      if (!state.success) return state;
      if (!record(state.result) || state.result.projectId !== input.projectId ||
          state.result.revision !== input.expectedRevision || !nonnegative(state.result.durationUs)) {
        throw new Error("REVISION_CONFLICT");
      }
      return state;
    };
    try {
      const initial = await checkProject();
      if (!initial.success) return fail(initial.code ?? "INTERNAL_ERROR", initial.retryable);
      const duration = (initial.result as { durationUs: number }).durationUs;
      if (input.positionsUs.some(position => position >= duration)) return fail("INVALID_ARGUMENT");
      const frames: Frame[] = [];
      for (const positionUs of input.positionsUs) {
        const seek = await send("seek", { positionUs });
        if (!seek.success) return fail(seek.code ?? "INTERNAL_ERROR", seek.retryable);
        let frame: Frame | null = null;
        for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
          const response = await send("get_preview_frame");
          if (!response.success) {
            if (response.code !== "PREVIEW_FAILED" || !response.retryable) return fail(response.code ?? "INTERNAL_ERROR", response.retryable);
          } else {
            if (!record(response.result) || response.result.projectId !== input.projectId ||
                response.result.revision !== input.expectedRevision) return fail("REVISION_CONFLICT", true);
            frame = frameFrom(response.result, input, positionUs);
            if (frame && frame.positionUs < duration &&
                (frames.length === 0 || frame.positionUs > frames[frames.length - 1].positionUs)) break;
            frame = null;
          }
          if (attempt + 1 < MAX_ATTEMPTS) await wait(200);
        }
        if (!frame) return fail("PREVIEW_FAILED", true);
        frames.push(frame);
      }
      const final = await checkProject();
      if (!final.success) return fail(final.code ?? "INTERNAL_ERROR", final.retryable);
      return { success: true, projectId: input.projectId, revision: input.expectedRevision, frames };
    } catch (error) {
      const code = error instanceof Error && ["AUTH_REQUIRED", "REVIEW_TIMEOUT", "REVISION_CONFLICT"].includes(error.message)
        ? error.message : "INTERNAL_ERROR";
      return fail(code, code === "REVIEW_TIMEOUT" || code === "REVISION_CONFLICT");
    } finally {
      this.active.delete(input.deviceId);
    }
  }
}

// Image bytes appear once, in MCP image parts, never duplicated into text/structured metadata.
export function previewReviewToolResult(result: PreviewReviewResult) {
  if (!result.success) return {
    isError: true, content: [{ type: "text" as const, text: JSON.stringify(result) }], structuredContent: result,
  };
  const metadata = { success: true, projectId: result.projectId, revision: result.revision,
    source: "editor_preview" as const, frameAccurate: false, playheadMoved: true,
    frames: result.frames.map(({ base64, ...frame }, index) => ({ ...frame, imageIndex: index })) };
  return { isError: false, structuredContent: metadata,
    content: [{ type: "text" as const, text: JSON.stringify(metadata) },
      ...result.frames.map(frame => ({ type: "image" as const, data: frame.base64, mimeType: frame.mimeType }))] };
}
