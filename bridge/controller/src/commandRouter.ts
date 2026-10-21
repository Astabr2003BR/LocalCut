import type { BridgeStore } from "./store.ts";
import {
  type BridgeCommandEnvelope,
  type BridgeResultEnvelope,
  validateControllerCommand,
} from "./protocol.ts";

export interface DeviceChannel {
  send(command: BridgeCommandEnvelope): Promise<BridgeResultEnvelope>;
  close(reason?: string): void;
}

export class DeviceRegistry {
  private channels = new Map<string, DeviceChannel>();

  register(deviceId: string, channel: DeviceChannel): void {
    const previous = this.channels.get(deviceId);
    if (previous && previous !== channel) previous.close("replaced_by_new_session");
    this.channels.set(deviceId, channel);
  }

  unregister(deviceId: string, channel: DeviceChannel): void {
    if (this.channels.get(deviceId) === channel) this.channels.delete(deviceId);
  }

  get(deviceId: string): DeviceChannel | null {
    return this.channels.get(deviceId) ?? null;
  }
}

export type CommandRouterOptions = {
  timeoutMs?: number;
  completedCacheSize?: number;
};

export class CommandRouter {
  private store: BridgeStore;
  private registry: DeviceRegistry;
  private timeoutMs: number;
  private completedCacheSize: number;
  private inFlight = new Map<string, Promise<BridgeResultEnvelope>>();
  private completed = new Map<string, BridgeResultEnvelope>();

  constructor(store: BridgeStore, registry: DeviceRegistry, options: CommandRouterOptions = {}) {
    this.store = store;
    this.registry = registry;
    this.timeoutMs = options.timeoutMs ?? 15_000;
    this.completedCacheSize = options.completedCacheSize ?? 1_000;
    if (this.timeoutMs <= 0) throw new Error("timeoutMs must be positive");
    if (this.completedCacheSize <= 0) throw new Error("completedCacheSize must be positive");
  }

  async execute(controllerId: string, command: BridgeCommandEnvelope): Promise<BridgeResultEnvelope> {
    const authError = this.authorize(controllerId, command);
    if (authError) return authError;

    try {
      validateControllerCommand(command);
    } catch (error) {
      const message = error instanceof Error ? error.message : "INVALID_ARGUMENT";
      const code = message === "PROTOCOL_UNSUPPORTED" || message === "CAPABILITY_NOT_ALLOWED"
        ? message
        : "INVALID_ARGUMENT";
      return failure(command.messageId, code, false);
    }

    const key = `${command.deviceId}:${command.messageId}`;
    const prior = this.completed.get(key);
    if (prior) return structuredClone(prior);
    const pending = this.inFlight.get(key);
    if (pending) return structuredClone(await pending);

    const channel = this.registry.get(command.deviceId);
    if (!channel) return failure(command.messageId, "DEVICE_OFFLINE", true);

    const work = this.sendWithTimeout(channel, command);
    this.inFlight.set(key, work);
    try {
      const result = await work;
      if (result.messageId !== command.messageId) {
        return failure(command.messageId, "INTERNAL_ERROR", true);
      }
      this.rememberCompleted(key, result);
      return structuredClone(result);
    } catch {
      return failure(command.messageId, "DEVICE_OFFLINE", true);
    } finally {
      if (this.inFlight.get(key) === work) this.inFlight.delete(key);
    }
  }

  private authorize(controllerId: string, command: BridgeCommandEnvelope): BridgeResultEnvelope | null {
    const device = this.store.getDevice(command.deviceId);
    if (!device || device.revokedAtEpochMs != null || device.controllerId !== controllerId) {
      return failure(command.messageId, "AUTH_REQUIRED", false);
    }
    return null;
  }

  private async sendWithTimeout(
    channel: DeviceChannel,
    command: BridgeCommandEnvelope,
  ): Promise<BridgeResultEnvelope> {
    let timeout: ReturnType<typeof setTimeout> | undefined;
    try {
      return await Promise.race([
        channel.send(command),
        new Promise<BridgeResultEnvelope>((_resolve, reject) => {
          timeout = setTimeout(() => reject(new Error("COMMAND_TIMEOUT")), this.timeoutMs);
        }),
      ]);
    } finally {
      if (timeout) clearTimeout(timeout);
    }
  }

  private rememberCompleted(key: string, result: BridgeResultEnvelope): void {
    this.completed.set(key, structuredClone(result));
    while (this.completed.size > this.completedCacheSize) {
      const oldest = this.completed.keys().next().value as string | undefined;
      if (oldest === undefined) break;
      this.completed.delete(oldest);
    }
  }
}

function failure(messageId: string, code: string, retryable: boolean): BridgeResultEnvelope {
  return {
    messageId,
    success: false,
    code,
    projectRevision: null,
    result: {},
    retryable,
  };
}
