export const bridgeCapabilities = [
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
  "seek",
  "select_clip",
  "split_clip",
  "trim_clip",
  "move_clip",
  "delete_clip",
  "duplicate_clip",
  "set_transform",
  "set_volume",
  "set_keyframes",
  "add_text",
  "project_edit",
  "add_transition",
  "import_media",
  "undo",
  "redo",
  "export_project",
  "cancel_export",
] as const;

export type BridgeCapability = (typeof bridgeCapabilities)[number];

export const projectWriteCapabilities = new Set<BridgeCapability>([
  "split_clip",
  "trim_clip",
  "move_clip",
  "delete_clip",
  "duplicate_clip",
  "set_transform",
  "set_volume",
  "set_keyframes",
  "add_text",
  "project_edit",
  "add_transition",
  "import_media",
  "undo",
  "redo",
]);

export const revisionRequiredCapabilities = new Set<BridgeCapability>([
  ...projectWriteCapabilities,
  "export_project",
]);

export type BridgeCommandEnvelope = {
  protocolVersion: 1;
  messageId: string;
  kind: "command";
  deviceId: string;
  projectId: string;
  expectedRevision: number | null;
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  nonce: string;
  capability: BridgeCapability;
  payload: Record<string, unknown>;
};

export type BridgeResultEnvelope = {
  messageId: string;
  success: boolean;
  code: string | null;
  projectRevision: number | null;
  result: unknown;
  retryable: boolean;
};

export type DeviceHello = {
  kind: "device_hello";
  protocolVersion: 1;
  deviceId: string;
  accessToken: string;
};

export function isBridgeCapability(value: string): value is BridgeCapability {
  return (bridgeCapabilities as readonly string[]).includes(value);
}

export function validateControllerCommand(command: BridgeCommandEnvelope): void {
  if (command.protocolVersion !== 1) throw new Error("PROTOCOL_UNSUPPORTED");
  if (!isBridgeCapability(command.capability)) throw new Error("CAPABILITY_NOT_ALLOWED");
  if (!command.messageId || !command.deviceId || !command.projectId || !command.nonce) {
    throw new Error("INVALID_ARGUMENT");
  }
  if (revisionRequiredCapabilities.has(command.capability) && command.expectedRevision === null) {
    throw new Error("REVISION_REQUIRED");
  }
  if (command.expiresAtEpochMs <= command.issuedAtEpochMs) throw new Error("INVALID_EXPIRY");
}
