import assert from "node:assert/strict";
import test from "node:test";
import { PreviewReviewCoordinator } from "../src/previewReview.ts";
import { ReviewCorrectionCoordinator, correctionToolResult, validCorrectionOperations, type CorrectionInput } from "../src/reviewCorrection.ts";
import type { BridgeCommandEnvelope, BridgeResultEnvelope } from "../src/protocol.ts";

const request = { deviceId: "d", projectId: "p", expectedRevision: 7, positionsUs: [0, 1_000_000] };
const png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jfZsAAAAASUVORK5CYII=";
const operations = [{ op: "patchClip", clipId: "v", opacity: 0.8 }];
function fixture() {
  let revision = 7, position = 0, time = 1000, writes = 0, unavailable = false, offline = false, authorized = true;
  const coordinator = new ReviewCorrectionCoordinator(new PreviewReviewCoordinator(), () => time);
  const router = { async execute(_owner: string, command: BridgeCommandEnvelope): Promise<BridgeResultEnvelope> {
    const reply = (result: unknown): BridgeResultEnvelope => ({ messageId: command.messageId, success: true, code: null, projectRevision: revision, result, retryable: false });
    const fail = (code: string, retryable = false): BridgeResultEnvelope => ({ messageId: command.messageId, success: false, code, projectRevision: revision, result: {}, retryable });
    if (command.expectedRevision !== revision) return fail("REVISION_CONFLICT", true);
    if (command.capability === "project_edit") {
      writes++;
      assert.ok(validCorrectionOperations(command.payload.operations));
      revision++;
      return offline ? fail("DEVICE_OFFLINE", true) : reply({ applied: true });
    }
    if (command.capability === "get_project_state") return reply({ projectId: "p", revision, durationUs: 2_000_000 });
    if (command.capability === "seek") { position = Number(command.payload.positionUs); return reply({ positionUs: position }); }
    assert.equal(command.capability, "get_preview_frame");
    return unavailable ? fail("PREVIEW_FAILED") : reply({ projectId: "p", revision, positionUs: position, width: 1, height: 1, mimeType: "image/png", base64: png });
  }};
  const auth = () => authorized ? "d" : null;
  return { coordinator, router, auth, writes: () => writes, revision: () => revision,
    expire: () => { time += 300_001; }, change: () => { revision++; }, unavailable: () => { unavailable = true; },
    offline: () => { offline = true; }, revoke: () => { authorized = false; },
    review: () => coordinator.review(router, "owner", { ...request, expectedRevision: revision }, auth),
    apply: (input: CorrectionInput) => coordinator.apply(router, "owner", input, auth) };
}
async function ticket(f: ReturnType<typeof fixture>): Promise<CorrectionInput> {
  const result = await f.review();
  assert.ok(result.success);
  assert.ok(result.reviewId);
  return { deviceId: "d", projectId: "p", expectedRevision: result.review.revision, reviewId: result.reviewId, operations };
}

test("review correction applies one atomic batch and captures new revision at same positions", async () => {
  const f = fixture(), input = await ticket(f);
  const result = await f.apply(input);
  assert.ok(result.success && result.verification === "captured");
  assert.equal(result.revision, 8);
  assert.equal(result.remainingRounds, 2);
  assert.equal(f.writes(), 1);
  assert.deepEqual(result.review.frames.map(frame => frame.requestedUs), request.positionsUs);
  const tool = correctionToolResult(result);
  assert.equal(tool.content.filter(part => part.type === "image").length, 2);
  assert.equal(JSON.stringify(tool.structuredContent).includes(png), false);
  assert.deepEqual(await f.apply(input), { success: false, applied: false, code: "REVIEW_REQUIRED", retryable: false });
  assert.equal(f.writes(), 1);
});

test("three corrections per chain and no fourth ticket", async () => {
  const f = fixture();
  let input = await ticket(f);
  for (let round = 1; round <= 3; round++) {
    const result = await f.apply(input);
    assert.ok(result.success && result.verification === "captured");
    assert.equal(result.remainingRounds, 3 - round);
    if (round === 3) assert.equal(result.reviewId, null);
    else { assert.ok(result.reviewId); input = { ...input, reviewId: result.reviewId, expectedRevision: result.revision }; }
  }
  assert.equal(f.writes(), 3);
});

test("wrong device/project/revision, expired ticket and stale Android revision never edit", async () => {
  const f = fixture(), input = await ticket(f);
  for (const change of [{ deviceId: "foreign" }, { projectId: "other" }, { expectedRevision: 8 }, { reviewId: "wrong" }]) {
    assert.equal((await f.apply({ ...input, ...change })).success, false);
  }
  f.expire();
  assert.equal((await f.apply(input)).success, false);
  const fresh = await ticket(f);
  f.change();
  const result = await f.apply(fresh);
  assert.ok(!result.success && result.code === "REVISION_CONFLICT");
  assert.equal(f.writes(), 0);
});

test("invalid and destructive operations never consume valid ticket or reach Android", async () => {
  const f = fixture(), input = await ticket(f);
  for (const bad of [[{ op: "deleteClip", clipId: "v" }], [{ op: "patchClip", clipId: "v", volume: 0 }],
      [{ op: "patchText", overlayId: "t", durationUs: 1 }], [{ op: "patchEffect", clipId: "v", effectId: "e", numbers: { amount: NaN } }],
      [{ op: "patchClip", clipId: "v" }], Array(9).fill(operations[0])]) {
    assert.equal(validCorrectionOperations(bad), false);
    assert.equal((await f.apply({ ...input, operations: bad })).success, false);
  }
  assert.equal(f.writes(), 0);
  assert.equal((await f.apply(input)).success, true);
});

test("committed edit with failed verification reports applied, not rollback or retry", async () => {
  const f = fixture(), input = await ticket(f);
  f.unavailable();
  const result = await f.apply(input);
  assert.deepEqual(result, { success: true, applied: true, revision: 8, verification: "unavailable", code: "PREVIEW_FAILED" });
  assert.equal(f.writes(), 1);
  assert.equal((await f.apply(input)).success, false);
});

test("lost edit response is ambiguous and never automatically resends", async () => {
  const f = fixture(), input = await ticket(f);
  f.offline();
  assert.deepEqual(await f.apply(input), { success: false, applied: "unknown", code: "DEVICE_OFFLINE", retryable: false });
  assert.equal(f.revision(), 8);
  assert.equal((await f.apply(input)).success, false);
  assert.equal(f.writes(), 1);
});

test("concurrent and revoked requests cannot reuse evidence; new review replaces old receipt", async () => {
  const f = fixture(), old = await ticket(f), input = await ticket(f);
  assert.equal((await f.apply(old)).success, false);
  const first = f.apply(input);
  const second = await f.apply(input);
  assert.ok(!second.success && second.code === "RATE_LIMITED");
  assert.equal((await first).success, true);
  const next = await ticket(f);
  f.revoke();
  assert.ok(!(await f.apply(next)).success);
  assert.equal(f.writes(), 1);
});
