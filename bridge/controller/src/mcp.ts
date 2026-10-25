import { randomBytes, randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/server";
import * as z from "zod/v4";
import type { CommandRouter, DeviceRegistry } from "./commandRouter.ts";
import {
  bridgeCapabilities,
  type BridgeCapability,
  type BridgeCommandEnvelope,
  revisionRequiredCapabilities,
} from "./protocol.ts";

const MAX_PREVIEW_BASE64_CHARS = 2_000_000;
const ACTIVE_PROJECT_SENTINEL = "__localcut_active_project__";
const COMMAND_TTL_MS = 30_000;
const READ_ONLY_CAPABILITIES = new Set<BridgeCapability>([
  "get_device_status",
  "get_project_state",
  "get_timeline_summary",
  "list_tracks",
  "list_clips",
  "get_selected_clip",
  "get_playhead",
  "get_export_status",
  "get_errors",
  "get_preview_frame",
]);

const routingFields = {
  deviceId: z.string().min(1).max(128),
  projectId: z.string().min(1).max(256),
  payload: z.record(z.string(), z.unknown()).default({}),
};

type DeviceIdSource = Pick<DeviceRegistry, "listDeviceIds">;

export interface ControllerAdminActions {
  claimPairing(code: string): Promise<unknown> | unknown;
  revokeDevice(deviceId: string): Promise<unknown> | unknown;
}

export function controllerToolNames(): string[] {
  return [...bridgeCapabilities];
}

export function controllerManagementToolNames(): string[] {
  return ["claim_pairing", "discover_active_contexts", "revoke_device"];
}

export function securityScopesForCapability(capability: BridgeCapability): [string] {
  return [READ_ONLY_CAPABILITIES.has(capability) ? "localcut:read" : "localcut:write"];
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

export async function discoverActiveContexts(
  router: CommandRouter,
  devices: DeviceIdSource,
  controllerId: string,
  nowEpochMs: () => number = Date.now,
): Promise<{ contexts: Array<{ deviceId: string; projectId: string; projectRevision: number; state: string }> }> {
  const contexts: Array<{ deviceId: string; projectId: string; projectRevision: number; state: string }> = [];

  for (const deviceId of devices.listDeviceIds()) {
    const now = nowEpochMs();
    const command: BridgeCommandEnvelope = {
      protocolVersion: 1,
      messageId: randomUUID(),
      kind: "command",
      deviceId,
      projectId: ACTIVE_PROJECT_SENTINEL,
      expectedRevision: null,
      issuedAtEpochMs: now,
      expiresAtEpochMs: now + COMMAND_TTL_MS,
      nonce: randomBytes(18).toString("base64url"),
      capability: "get_device_status",
      payload: {},
    };
    const response = await router.execute(controllerId, command);
    if (!response.success || !isRecord(response.result)) continue;

    const projectId = response.result.projectId;
    const state = response.result.state;
    const projectRevision = response.projectRevision;
    if (
      typeof projectId !== "string" || projectId.length === 0 ||
      typeof state !== "string" || state.length === 0 ||
      typeof projectRevision !== "number" || !Number.isSafeInteger(projectRevision) || projectRevision < 0
    ) {
      continue;
    }

    contexts.push({ deviceId, projectId, projectRevision, state });
  }

  return { contexts };
}

export function createLocalCutMcpServer(
  router: CommandRouter,
  controllerId: string,
  admin?: ControllerAdminActions,
): McpServer {
  if (!controllerId) throw new Error("controllerId is required");
  const server = new McpServer({ name: "LocalCut Controller", version: "0.1.0" });

  const discoverySchema = z.object({}).strict();
  server.registerTool(
    "discover_active_contexts",
    {
      title: "LocalCut: discover active contexts",
      description: "Discover currently connected LocalCut devices and their active project routing metadata. This is read-only and should be called before other tools when deviceId/projectId are unknown.",
      inputSchema: discoverySchema,
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
      _meta: oauthToolMeta(["localcut:read"]),
    },
    async (input: unknown) => {
      discoverySchema.parse(input);
      try {
        return toolResult(await discoverActiveContexts(router, router, controllerId), false);
      } catch (error) {
        return toolResult({ code: safeAdminError(error) }, true);
      }
    },
  );

  for (const capability of bridgeCapabilities) {
    const inputSchema = buildToolInputSchema(capability);
    const scopes = securityScopesForCapability(capability);
    const readOnly = scopes[0] === "localcut:read";
    server.registerTool(
      capability,
      {
        title: titleFor(capability),
        description: descriptionFor(capability),
        inputSchema,
        annotations: {
          readOnlyHint: readOnly,
          destructiveHint: capability === "delete_clip",
          openWorldHint: false,
        },
        _meta: oauthToolMeta(scopes),
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
          expiresAtEpochMs: now + COMMAND_TTL_MS,
          nonce: randomBytes(18).toString("base64url"),
          capability,
          payload: parsed.payload,
        };
        const result = await router.execute(controllerId, command);
        return toolResultForCapability(capability, result, !result.success);
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
        annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: false },
        _meta: oauthToolMeta(["localcut:write"]),
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
        annotations: { readOnlyHint: false, destructiveHint: true, openWorldHint: false },
        _meta: oauthToolMeta(["localcut:write"]),
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

export function toolResultForCapability(
  capability: BridgeCapability,
  value: unknown,
  isError: boolean,
) {
  const base = toolResult(value, isError);
  const image = previewImageContent(capability, value, isError);
  if (!image) return base;
  return {
    ...base,
    content: [...base.content, image],
  };
}

function oauthToolMeta(scopes: readonly string[]): Record<string, unknown> {
  return {
    securitySchemes: [{ type: "oauth2", scopes: [...scopes] }],
  };
}

function toolResult(value: unknown, isError: boolean) {
  const structured = isRecord(value) ? value : { value };
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value) }],
    structuredContent: structured,
    isError,
  };
}

function previewImageContent(
  capability: BridgeCapability,
  value: unknown,
  isError: boolean,
): { type: "image"; data: string; mimeType: string } | null {
  if (capability !== "get_preview_frame" || isError || !isRecord(value) || value.success !== true) {
    return null;
  }
  const result = value.result;
  if (!isRecord(result)) return null;
  const mimeType = result.mimeType;
  const base64 = result.base64;
  if (mimeType !== "image/jpeg" && mimeType !== "image/png") return null;
  if (typeof base64 !== "string" || base64.length === 0 || base64.length > MAX_PREVIEW_BASE64_CHARS) {
    return null;
  }
  return {
    type: "image",
    data: base64,
    mimeType,
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
  if (capability === "get_preview_frame") {
    return "Capture the latest bounded LocalCut preview frame from the paired APK so an agent can visually inspect the current edit. The response includes image content plus frame metadata.";
  }
  if (revisionRequiredCapabilities.has(capability)) {
    return `Execute LocalCut '${capability}'. Read current project state first and pass its expectedRevision. Commands are TTL-bound and routed only to the paired device.`;
  }
  return `Execute LocalCut '${capability}' on the paired device through the authenticated Bridge.`;
}
