import assert from "node:assert/strict";
import test from "node:test";
import { exportReviewToolResult, validExportReviewPayload } from "../src/exportReview.ts";
import type { BridgeResultEnvelope } from "../src/protocol.ts";

// Minimal bounded JPEG header fixture, SOF0 width=1 height=1; not a decoded image.
const jpeg = Buffer.from("ffd8ffc0000b080001000101011100ffd9", "hex").toString("base64");
const payload = { jobId: "job", sourceRevision: 7 };
const frame = { requestedUs: 0, width: 1, height: 1, mimeType: "image/jpeg", base64: jpeg };
const result = { ...payload, sourceProjectId: "p", sha256: "a".repeat(64), source: "rendered_mp4", frameAccurate: false,
  frames: [frame, { ...frame, requestedUs: 1000000 }] };
const response = (value: unknown): BridgeResultEnvelope => ({ messageId: "m", success: true, code: null, projectRevision: 8, result: value, retryable: false });
test("export evidence returns images once, with exact source revision even when live revision differs", () => {
  const out = exportReviewToolResult(response(result), "p", payload);
  assert.equal(out.isError, false);
  assert.equal(out.content.filter(part => part.type === "image").length, 2);
  assert.equal(JSON.stringify(out.structuredContent).includes(jpeg), false);
  assert.equal(JSON.stringify(out.content[0]).includes(jpeg), false);
});
test("rejects foreign project, job, revision, invalid hash, malformed JPEG, dimensions, ordering and oversized samples", () => {
  for (const change of [{ sourceProjectId: "other" }, { jobId: "other" }, { sourceRevision: 8 },
    { sha256: "bad" }, { frames: [] }, { frames: [frame, frame] }, { frames: [frame, frame, frame, frame] },
    { frames: [{ ...frame, width: 2 }] }, { frames: [{ ...frame, base64: "AQID" }] },
    { frames: [{ ...frame, base64: "A".repeat(90000) }] }, { frames: [{ ...frame, base64: jpeg + "\n" }] }]) {
    const out = exportReviewToolResult(response({ ...result, ...change }), "p", payload);
    assert.equal(out.isError, true);
    assert.equal(out.content.length, 1);
  }
});
test("payload requires only export job and safe source revision; failures never emit images", () => {
  assert.equal(validExportReviewPayload(payload), true);
  for (const invalid of [{}, { ...payload, path: "/private" }, { ...payload, sourceRevision: -1 }, { ...payload, sourceRevision: 1.2 }]) assert.equal(validExportReviewPayload(invalid), false);
  const out = exportReviewToolResult({ ...response(result), success: false, code: "PREVIEW_FAILED" }, "p", payload);
  assert.equal(out.isError, true);
  assert.equal(out.content.length, 1);
});
