import assert from "node:assert/strict";
import test from "node:test";
import { startLocalCutBridgeE2eFixture } from "./e2e-device-fixture.ts";

test("pair -> WSS -> revision-safe edit -> undo -> idempotent export -> disconnect", async t => {
  const fx = await startLocalCutBridgeE2eFixture();
  t.after(() => fx.close());
  await fx.pairAndConnect();

  const initial = await fx.execute({
    messageId: "state-1",
    capability: "get_project_state",
  });
  assert.equal(initial.success, true);
  assert.equal(initial.projectRevision, 4);
  assert.deepEqual(initial.result, {
    projectId: "localcut-e2e-project",
    revision: 4,
    playheadUs: 0,
    clipStartUs: 0,
  });

  const seek = await fx.execute({
    messageId: "seek-1",
    capability: "seek",
    payload: { positionUs: 1_500_000 },
  });
  assert.equal(seek.success, true);
  assert.equal(seek.projectRevision, 4, "seek must not mutate project revision");

  const move = await fx.execute({
    messageId: "move-1",
    capability: "move_clip",
    expectedRevision: 4,
    payload: { clipId: "clip-1", startUs: 2_000_000 },
  });
  assert.equal(move.success, true);
  assert.equal(move.projectRevision, 5);

  const stale = await fx.execute({
    messageId: "move-stale",
    capability: "move_clip",
    expectedRevision: 4,
    payload: { clipId: "clip-1", startUs: 3_000_000 },
  });
  assert.equal(stale.success, false);
  assert.equal(stale.code, "REVISION_CONFLICT");
  assert.equal(stale.projectRevision, 5);

  const undo = await fx.execute({
    messageId: "undo-1",
    capability: "undo",
    expectedRevision: 5,
  });
  assert.equal(undo.success, true);
  assert.equal(undo.projectRevision, 6);

  const exportFirst = await fx.execute({
    messageId: "export-1",
    capability: "export_project",
    expectedRevision: 6,
    payload: { profile: "1080p" },
  });
  const exportRetry = await fx.execute({
    messageId: "export-1",
    capability: "export_project",
    expectedRevision: 6,
    payload: { profile: "1080p" },
  });
  assert.deepEqual(exportRetry, exportFirst);
  assert.equal(fx.device.executionsFor("export-1"), 1, "same messageId must execute on device exactly once");
  assert.deepEqual(exportFirst.result, { jobId: "export-job-e2e-1", state: "queued" });

  fx.device.disconnect();
  await new Promise(resolve => setTimeout(resolve, 20));
  const afterDisconnect = await fx.execute({
    messageId: "state-offline",
    capability: "get_project_state",
  });
  assert.equal(afterDisconnect.success, false);
  assert.equal(afterDisconnect.code, "DEVICE_OFFLINE");
  assert.equal(afterDisconnect.retryable, true);
});
