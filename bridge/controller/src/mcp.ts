import { randomBytes, randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/server";
import * as z from "zod/v4";
import type { CommandRouter } from "./commandRouter.ts";
import {
  bridgeCapabilities,
  type BridgeCapability,
  type BridgeCommandEnvelope,
  revisionRequiredCapabilities,
} from "./protocol.ts";

const routingFields = {
  deviceId: z.string().min(1).max(128),
  projectId: z.string().min(1).max(256),
  payload: z.record(z.string(), z.unknown()).default({}),
};

export function controllerToolNames(): string[] {
  return [...bridgeCapabilities];
}

export function buildToolInputSchema(capability: BridgeCapability) {
  if (revisionRequiredCapabilities.has(capability)) {
    return z.object({
      ...routingFields,
      expectedRevision: z.number().int().nonnegative(),
    }).strict();
  }
  return z.object({
    ...routingFields,
    expectedRevision: z.number().int().nonnegative().optional(),
  }).strict();
}

export function createLocalCutMcpServer(router: CommandRouter, controllerId: string): McpServer {
  if (!controllerId) throw new Error("controllerId is required");
  const server = new McpServer({ name: "LocalCut Controller", version: "0.1.0" });

  for (const capability of bridgeCapabilities) {
    const inputSchema = buildToolInputSchema(capability);
    server.registerTool(
      capability,
      {
        title: titleFor(capability),
        description: descriptionFor(capability),
        inputSchema,
      },
      async input => {
        const parsed = inputSchema.parse(input);
        const now = Date.now();
        const command: BridgeCommandEnvelope = {
          protocolVersion: 1,
          messageId: randomUUID(),
          kind: "command",
          deviceId: parsed.deviceId,
          projectId: parsed.projectId,
          expectedRevision: parsed.expectedRevision ?? null,
          issuedAtEpochMs: now,
          expiresAtEpochMs: now + 30_000,
          nonce: randomBytes(18).toString("base64url"),
          capability,
          payload: parsed.payload,
        };
        const result = await router.execute(controllerId, command);
        return {
          content: [{ type: "text" as const, text: JSON.stringify(result) }],
          structuredContent: { ...result },
          isError: !result.success,
        };
      },
    );
  }

  return server;
}

function titleFor(capability: BridgeCapability): string {
  return `LocalCut: ${capability.replaceAll("_", " ")}`;
}

function descriptionFor(capability: BridgeCapability): string {
  if (revisionRequiredCapabilities.has(capability)) {
    return `Execute LocalCut '${capability}'. Read current project state first and pass its expectedRevision. Commands are TTL-bound and routed only to the paired device.`;
  }
  return `Execute LocalCut '${capability}' on the paired device through the authenticated Bridge.`;
}
