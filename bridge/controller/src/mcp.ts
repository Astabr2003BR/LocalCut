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

export interface ControllerAdminActions {
  claimPairing(code: string): Promise<unknown> | unknown;
  revokeDevice(deviceId: string): Promise<unknown> | unknown;
}

export function controllerToolNames(): string[] {
  return [...bridgeCapabilities];
}

export function controllerManagementToolNames(): string[] {
  return ["claim_pairing", "revoke_device"];
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

export function createLocalCutMcpServer(
  router: CommandRouter,
  controllerId: string,
  admin?: ControllerAdminActions,
): McpServer {
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
      async (input: unknown) => {
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
        return toolResult(result, !result.success);
      },
    );
  }

  if (admin) {
    const claimSchema = z.object({ code: z.string().regex(/^\d{6}$/) }).strict();
    server.registerTool(
      "claim_pairing",
      {
        title: "LocalCut: claim pairing",
        description: "Claim the 6-digit pairing code shown by a LocalCut APK. The APK will independently sign the server challenge before pairing completes.",
        inputSchema: claimSchema,
      },
      async input => {
        const parsed = claimSchema.parse(input);
        try {
          return toolResult(await admin.claimPairing(parsed.code), false);
        } catch (error) {
          return toolResult({ code: safeAdminError(error) }, true);
        }
      },
    );

    const revokeSchema = z.object({ deviceId: z.string().min(1).max(128) }).strict();
    server.registerTool(
      "revoke_device",
      {
        title: "LocalCut: revoke device",
        description: "Revoke a paired LocalCut device and terminate its active controller session.",
        inputSchema: revokeSchema,
      },
      async input => {
        const parsed = revokeSchema.parse(input);
        try {
          return toolResult(await admin.revokeDevice(parsed.deviceId), false);
        } catch (error) {
          return toolResult({ code: safeAdminError(error) }, true);
        }
      },
    );
  }

  return server;
}

function toolResult(value: unknown, isError: boolean) {
  const structured = isRecord(value) ? value : { value };
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value) }],
    structuredContent: structured,
    isError,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function safeAdminError(error: unknown): string {
  if (error instanceof Error && /^[A-Z0-9_]{3,64}$/.test(error.message)) return error.message;
  return "INTERNAL_ERROR";
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
