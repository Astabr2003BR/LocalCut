import { randomBytes, randomUUID } from "node:crypto";
import type { CommandRouter } from "./commandRouter.ts";
import type { BridgeCommandEnvelope } from "./protocol.ts";
import { PreviewReviewCoordinator, type PreviewReviewInput, type PreviewReviewResult } from "./previewReview.ts";

export const MAX_CORRECTION_OPERATIONS = 8;
export const MAX_CORRECTION_ROUNDS = 3;
const TICKET_TTL_MS = 5 * 60_000;
const MAX_TICKETS = 64;
type Router = Pick<CommandRouter, "execute">;
type Authorization = () => string | null;
type SuccessfulReview = Extract<PreviewReviewResult, { success: true }>;
type Failure = { success: false; code: string; retryable: boolean };
type Ticket = { id: string; input: PreviewReviewInput; rounds: number; expiresAt: number };
type Evidence = { review: SuccessfulReview; reviewId: string | null; remainingRounds: number };
type ReviewResult = ({ success: true } & Evidence) | Failure;
export type CorrectionResult =
  | ({ success: true; applied: true; revision: number; verification: "captured" } & Evidence)
  | { success: true; applied: true; revision: number; verification: "unavailable"; code: string }
  | (Failure & { applied: false | "unknown" });

export type CorrectionInput = { deviceId: string; projectId: string; expectedRevision: number; reviewId: string; operations: Record<string, unknown>[] };

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
function id(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= 256;
}
const finite = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);
const integer = (value: unknown): value is number => finite(value) && Number.isSafeInteger(value) && value >= 0;
const numericKeys = ["x", "y", "scale", "rotationDeg", "opacity", "fontSizePx", "fillArgb", "zIndex"];
function fields(value: Record<string, unknown>, allowed: string[]): boolean {
  return Object.keys(value).every(key => allowed.includes(key));
}
function numbers(value: unknown): boolean {
  return record(value) && Object.keys(value).length <= 32 && Object.entries(value).every(([key, val]) => id(key) && finite(val));
}

// Restrict automatic corrections to visual changes. Android remains authoritative for
// effect registry, clip/overlay ownership, locks, numeric ranges and atomic history.
export function validCorrectionOperations(operations: unknown): operations is Record<string, unknown>[] {
  if (!Array.isArray(operations) || operations.length < 1 || operations.length > MAX_CORRECTION_OPERATIONS) return false;
  if (!operations.every(op => {
    if (!record(op)) return false;
    const transform = ["x", "y", "scale", "rotationDeg", "opacity"];
    switch (op.op) {
      case "patchClip":
        return id(op.clipId) && fields(op, ["op", "clipId", ...transform]) &&
          transform.some(key => key in op) && transform.every(key => !(key in op) || finite(op[key]));
      case "patchText": {
        const keys = [...numericKeys, "text"];
        return id(op.overlayId) && fields(op, ["op", "overlayId", ...keys]) &&
          keys.some(key => key in op) && numericKeys.every(key => !(key in op) || finite(op[key])) &&
          (!("text" in op) || (typeof op.text === "string" && op.text.trim().length > 0 && op.text.length <= 500));
      }
      case "addEffect":
        return fields(op, ["op", "clipId", "effectId", "typeId", "numbers"]) &&
          id(op.clipId) && id(op.effectId) && id(op.typeId) && (!("numbers" in op) || numbers(op.numbers));
      case "patchEffect":
        return fields(op, ["op", "clipId", "effectId", "numbers", "enabled"]) &&
          id(op.clipId) && id(op.effectId) &&
          (!("enabled" in op) || typeof op.enabled === "boolean") &&
          (!("numbers" in op) || numbers(op.numbers)) &&
          ("enabled" in op || (record(op.numbers) && Object.keys(op.numbers).length > 0));
      case "setEffectKeyframe":
        return fields(op, ["op", "clipId", "effectId", "parameter", "offsetUs", "value"]) &&
          id(op.clipId) && id(op.effectId) && id(op.parameter) && integer(op.offsetUs) && finite(op.value);
      default: return false;
    }
  })) return false;
  try { return Buffer.byteLength(JSON.stringify({ operations }), "utf8") <= 8192; } catch { return false; }
}
function failure(code: string, retryable = false): Failure { return { success: false, code, retryable }; }

export class ReviewCorrectionCoordinator {
  private readonly tickets = new Map<string, Ticket>(); // latest evidence per device; no image bytes retained
  private readonly busy = new Set<string>();
  private readonly previews: PreviewReviewCoordinator;
  private readonly now: () => number;
  constructor(previews: PreviewReviewCoordinator, now: () => number = Date.now) { this.previews = previews; this.now = now; }

  private evidence(input: PreviewReviewInput, review: SuccessfulReview, rounds: number): Evidence {
    this.tickets.delete(input.deviceId);
    const remainingRounds = MAX_CORRECTION_ROUNDS - rounds;
    if (remainingRounds <= 0) return { review, reviewId: null, remainingRounds: 0 };
    const ticket: Ticket = { id: randomUUID(), input: { ...input, positionsUs: [...input.positionsUs] }, rounds, expiresAt: this.now() + TICKET_TTL_MS };
    for (const [key, old] of this.tickets) if (old.expiresAt <= this.now()) this.tickets.delete(key);
    while (this.tickets.size >= MAX_TICKETS) {
      const oldest = this.tickets.keys().next().value;
      if (oldest === undefined) break;
      this.tickets.delete(oldest);
    }
    this.tickets.set(input.deviceId, ticket);
    return { review, reviewId: ticket.id, remainingRounds };
  }

  async review(router: Router, controllerId: string, input: PreviewReviewInput, auth: Authorization): Promise<ReviewResult> {
    if (auth() !== input.deviceId) return failure("AUTH_REQUIRED");
    if (this.busy.has(input.deviceId)) return failure("RATE_LIMITED", true);
    this.busy.add(input.deviceId);
    try {
      // A new review invalidates previous evidence even if collection fails.
      this.tickets.delete(input.deviceId);
      const result = await this.previews.collect(router, controllerId, input, auth, this.now);
      if (!result.success) return result;
      if (auth() !== input.deviceId) return failure("AUTH_REQUIRED");
      return { success: true, ...this.evidence(input, result, 0) };
    } finally { this.busy.delete(input.deviceId); }
  }

  async apply(router: Router, controllerId: string, input: CorrectionInput, auth: Authorization): Promise<CorrectionResult> {
    const rejected = (code: string, retryable = false): CorrectionResult => ({ ...failure(code, retryable), applied: false });
    const uncertain = (code: string): CorrectionResult => ({ ...failure(code), applied: "unknown" });
    if (auth() !== input.deviceId) return rejected("AUTH_REQUIRED");
    if (!id(input.deviceId) || !id(input.projectId) || !id(input.reviewId) || !integer(input.expectedRevision) ||
        !validCorrectionOperations(input.operations)) return rejected("INVALID_ARGUMENT");
    if (this.busy.has(input.deviceId)) return rejected("RATE_LIMITED", true);
    const ticket = this.tickets.get(input.deviceId);
    if (!ticket || ticket.id !== input.reviewId || ticket.expiresAt <= this.now() ||
        ticket.input.projectId !== input.projectId || ticket.input.expectedRevision !== input.expectedRevision) {
      return rejected("REVIEW_REQUIRED");
    }
    this.busy.add(input.deviceId);
    this.tickets.delete(input.deviceId); // consume before dispatch; never auto-resend a possibly committed edit
    try {
      const issued = this.now();
      const command: BridgeCommandEnvelope = {
        protocolVersion: 1, messageId: randomUUID(), kind: "command",
        deviceId: input.deviceId, projectId: input.projectId, expectedRevision: input.expectedRevision,
        issuedAtEpochMs: issued, expiresAtEpochMs: issued + 30_000, nonce: randomBytes(18).toString("base64url"),
        capability: "project_edit", payload: { operations: input.operations },
      };
      if (auth() !== input.deviceId) return rejected("AUTH_REQUIRED");
      const result = await router.execute(controllerId, command);
      if (auth() !== input.deviceId) return uncertain("AUTH_REQUIRED");
      if (result.messageId !== command.messageId) return uncertain("INTERNAL_ERROR");
      if (!result.success) {
        // Transport errors do not establish whether Android committed the write.
        if (["DEVICE_OFFLINE", "INTERNAL_ERROR", "COMMAND_EXPIRED"].includes(result.code ?? "INTERNAL_ERROR")) {
          return uncertain(result.code ?? "INTERNAL_ERROR");
        }
        return rejected(result.code ?? "INTERNAL_ERROR");
      }
      if (!integer(result.projectRevision) || result.projectRevision <= input.expectedRevision ||
          !record(result.result) || result.result.applied !== true) return uncertain("INTERNAL_ERROR");
      const revision = result.projectRevision;
      const next: PreviewReviewInput = { ...ticket.input, expectedRevision: revision };
      // A successful mutation remains successful even if post-edit capture fails.
      let verification: PreviewReviewResult;
      try { verification = await this.previews.collect(router, controllerId, next, auth, this.now); }
      catch { return { success: true, applied: true, revision, verification: "unavailable", code: "PREVIEW_FAILED" }; }
      if (auth() !== input.deviceId) return { success: true, applied: true, revision, verification: "unavailable", code: "AUTH_REQUIRED" };
      if (!verification.success) return { success: true, applied: true, revision, verification: "unavailable", code: verification.code };
      return { success: true, applied: true, revision, verification: "captured", ...this.evidence(next, verification, ticket.rounds + 1) };
    } catch { return uncertain("INTERNAL_ERROR"); }
    finally { this.busy.delete(input.deviceId); }
  }
}

export function correctionToolResult(result: ReviewResult | CorrectionResult) {
  if (!result.success || !("review" in result)) {
    return { isError: !result.success, structuredContent: result,
      content: [{ type: "text" as const, text: JSON.stringify(result) }] };
  }
  const { review, ...rest } = result;
  const metadata = { ...rest, projectId: review.projectId, revision: review.revision,
    source: "editor_preview", frameAccurate: false, playheadMoved: true,
    frames: review.frames.map(({ base64, ...frame }, imageIndex) => ({ ...frame, imageIndex })) };
  return { isError: false, structuredContent: metadata,
    content: [{ type: "text" as const, text: JSON.stringify(metadata) },
      ...review.frames.map(frame => ({ type: "image" as const, data: frame.base64, mimeType: frame.mimeType }))] };
}
