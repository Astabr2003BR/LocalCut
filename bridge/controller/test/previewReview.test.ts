import assert from "node:assert/strict";
import test from "node:test";
import { PreviewReviewCoordinator, previewReviewToolResult, type PreviewReviewInput } from "../src/previewReview.ts";
import type { BridgeCommandEnvelope, BridgeResultEnvelope } from "../src/protocol.ts";

const input: PreviewReviewInput = { deviceId: "device-1", projectId: "p", expectedRevision: 7, positionsUs: [0, 1_000_000, 2_000_000] };
const png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jfZsAAAAASUVORK5CYII=";
const ok = (command: BridgeCommandEnvelope, result: unknown, projectRevision = 7): BridgeResultEnvelope =>
  ({ messageId: command.messageId, success: true, code: null, projectRevision, result, retryable: false });
function fixture(override?: (command: BridgeCommandEnvelope, position: number) => BridgeResultEnvelope | undefined) {
  const commands: BridgeCommandEnvelope[] = [];
  let position = 0;
  const router = { async execute(controllerId: string, command: BridgeCommandEnvelope) {
    assert.equal(controllerId, "owner");
    commands.push(command);
    const custom = override?.(command, position);
    if (custom) return custom;
    if (command.capability === "get_project_state") return ok(command, { projectId: "p", revision: 7, durationUs: 3_000_000 });
    if (command.capability === "seek") { position = Number(command.payload.positionUs); return ok(command, { positionUs: position }); }
    assert.equal(command.capability, "get_preview_frame");
    return ok(command, { projectId: "p", revision: 7, positionUs: position, width: 1, height: 1, mimeType: "image/png", base64: png });
  }};
  return { router, commands };
}
const run = (f: ReturnType<typeof fixture>, request = input, auth = () => "device-1", coordinator = new PreviewReviewCoordinator()) =>
  coordinator.collect(f.router, "owner", request, auth, () => 1000, async () => {});

test("ordered multi-frame review checks revision, bounds and emits each image only once", async () => {
  const f = fixture();
  const result = await run(f);
  assert.equal(result.success, true);
  if (!result.success) return;
  assert.deepEqual(result.frames.map(frame => frame.positionUs), input.positionsUs);
  assert.deepEqual(f.commands.map(c => c.capability), ["get_project_state", "seek", "get_preview_frame", "seek", "get_preview_frame", "seek", "get_preview_frame", "get_project_state"]);
  assert.ok(f.commands.every(c => c.expectedRevision === 7 && c.deviceId === input.deviceId && c.projectId === "p"));
  assert.equal(new Set(f.commands.map(c => c.messageId)).size, f.commands.length);
  const tool = previewReviewToolResult(result);
  assert.equal(tool.content.filter(c => c.type === "image").length, 3);
  assert.equal(JSON.stringify(tool.structuredContent).includes(png), false);
  assert.equal(JSON.stringify(tool.content[0]).includes(png), false);
});

test("invalid input, foreign OAuth device and out-of-range positions fail before seek", async () => {
  for (const positionsUs of [[], [0], [0, 0], [1, 0], [0, -1], [0, 1.1], [0, 1, 2, 3, 4]]) {
    const f = fixture();
    assert.equal((await run(f, { ...input, positionsUs })).success, false);
    assert.equal(f.commands.length, 0);
  }
  const foreign = fixture();
  assert.deepEqual(await run(foreign, input, () => "device-2"), { success: false, code: "AUTH_REQUIRED", retryable: false });
  assert.equal(foreign.commands.length, 0);
  const out = fixture();
  assert.equal((await run(out, { ...input, positionsUs: [0, 3_000_000] })).success, false);
  assert.deepEqual(out.commands.map(c => c.capability), ["get_project_state"]);
});

test("revision change after one image discards the entire review", async () => {
  const f = fixture((command, position) => command.capability === "get_preview_frame" && position === 1_000_000
    ? ok(command, { projectId: "p", revision: 8 }, 8) : undefined);
  const result = await run(f);
  assert.deepEqual(result, { success: false, code: "REVISION_CONFLICT", retryable: true });
  assert.equal(previewReviewToolResult(result).content.length, 1);
});

test("retryable preview not ready is bounded, then succeeds or fails closed", async () => {
  let attempts = 0;
  const f = fixture(command => command.capability === "get_preview_frame" && ++attempts === 1
    ? { messageId: command.messageId, success: false, code: "PREVIEW_FAILED", projectRevision: 7, result: {}, retryable: true } : undefined);
  assert.equal((await run(f)).success, true);
  assert.equal(attempts, 4);
  const bad = fixture((command, position) => command.capability === "get_preview_frame"
    ? ok(command, { projectId: "p", revision: 7, positionUs: position, width: 1, height: 1, mimeType: "image/png", base64: "AQID" }) : undefined);
  assert.deepEqual(await run(bad), { success: false, code: "PREVIEW_FAILED", retryable: true });
  assert.equal(bad.commands.filter(c => c.capability === "get_preview_frame").length, 3);
});

test("revocation during collection stops before further commands and never returns images", async () => {
  let authorized = true;
  const f = fixture(command => {
    if (command.capability === "seek") authorized = false;
    return undefined;
  });
  assert.deepEqual(await run(f, input, () => authorized ? "device-1" : ""), { success: false, code: "AUTH_REQUIRED", retryable: false });
  assert.deepEqual(f.commands.map(c => c.capability), ["get_project_state", "seek"]);
});

test("concurrent review of same device is rejected; guard released after completion", async () => {
  const coordinator = new PreviewReviewCoordinator();
  const f = fixture();
  const first = run(f, input, () => "device-1", coordinator);
  const concurrent = await run(f, input, () => "device-1", coordinator);
  assert.deepEqual(concurrent, { success: false, code: "RATE_LIMITED", retryable: true });
  assert.equal((await first).success, true);
  assert.equal((await run(f, input, () => "device-1", coordinator)).success, true);
});

test("final project revision and total time budget are rechecked", async () => {
  let reads = 0;
  const changed = fixture(command => command.capability === "get_project_state" && ++reads === 2
    ? ok(command, { projectId: "p", revision: 8, durationUs: 3_000_000 }, 8) : undefined);
  assert.deepEqual(await run(changed), { success: false, code: "REVISION_CONFLICT", retryable: true });
  const slow = fixture();
  let now = 1000;
  const result = await new PreviewReviewCoordinator().collect(slow.router, "owner", input, () => "device-1",
    () => { now += 20_000; return now; }, async () => {});
  assert.deepEqual(result, { success: false, code: "REVIEW_TIMEOUT", retryable: true });
  assert.equal(slow.commands.length, 1);
});
