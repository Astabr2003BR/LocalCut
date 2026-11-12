import assert from "node:assert/strict";
import test from "node:test";
import {
  bridgeCapabilities,
  projectWriteCapabilities,
  revisionRequiredCapabilities,
  type BridgeCommandEnvelope,
  validateControllerCommand,
} from "../src/protocol.ts";
import { controllerToolNames, securityScopesForCapability } from "../src/mcp.ts";

function command(capability: "animate_character" | "animate_scene", expectedRevision: number | null): BridgeCommandEnvelope {
  return {
    protocolVersion: 1,
    messageId: `msg-${capability}`,
    kind: "command",
    deviceId: "device-1",
    projectId: "project-1",
    expectedRevision,
    issuedAtEpochMs: 1_000,
    expiresAtEpochMs: 31_000,
    nonce: `nonce-${capability}`,
    capability,
    payload: {},
  };
}

test("character and scene animation are authenticated revision-protected write capabilities", () => {
  for (const capability of ["animate_character", "animate_scene"] as const) {
    assert.ok(bridgeCapabilities.includes(capability));
    assert.ok(controllerToolNames().includes(capability));
    assert.ok(projectWriteCapabilities.has(capability));
    assert.ok(revisionRequiredCapabilities.has(capability));
    assert.deepEqual(securityScopesForCapability(capability), ["localcut:write"]);
    assert.throws(() => validateControllerCommand(command(capability, null)), /REVISION_REQUIRED/);
    assert.doesNotThrow(() => validateControllerCommand(command(capability, 7)));
  }
});
