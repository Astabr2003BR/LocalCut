import type { BridgeCommandEnvelope, BridgeResultEnvelope } from "./protocol.ts";
import type { DeviceChannel } from "./commandRouter.ts";

export interface DeviceSocket {
  readyState: number;
  send(data: string): void;
  close(code?: number, reason?: string): void;
  on(event: "message", listener: (data: Buffer | string) => void): this;
  on(event: "close", listener: (code: number, reason: Buffer) => void): this;
  on(event: "error", listener: (error: Error) => void): this;
}

type Pending = {
  resolve: (result: BridgeResultEnvelope) => void;
  reject: (error: Error) => void;
};

export class WebSocketDeviceChannel implements DeviceChannel {
  private socket: DeviceSocket;
  private pending = new Map<string, Pending>();
  private closed = false;

  constructor(socket: DeviceSocket) {
    this.socket = socket;
    socket.on("message", data => this.handleMessage(data));
    socket.on("close", () => this.failAll(new Error("DEVICE_CHANNEL_CLOSED")));
    socket.on("error", () => this.failAll(new Error("DEVICE_CHANNEL_CLOSED")));
  }

  send(command: BridgeCommandEnvelope): Promise<BridgeResultEnvelope> {
    if (this.closed || this.socket.readyState !== 1) {
      return Promise.reject(new Error("DEVICE_CHANNEL_CLOSED"));
    }
    if (this.pending.has(command.messageId)) {
      return Promise.reject(new Error("DUPLICATE_PENDING_MESSAGE_ID"));
    }

    return new Promise<BridgeResultEnvelope>((resolve, reject) => {
      this.pending.set(command.messageId, { resolve, reject });
      try {
        this.socket.send(JSON.stringify(command));
      } catch (error) {
        this.pending.delete(command.messageId);
        reject(error instanceof Error ? error : new Error("DEVICE_SEND_FAILED"));
      }
    });
  }

  close(reason = "controller_close"): void {
    if (this.closed) return;
    this.closed = true;
    this.failAll(new Error("DEVICE_CHANNEL_CLOSED"));
    try {
      this.socket.close(1000, reason.slice(0, 120));
    } catch {
      // Channel is already terminal; socket close errors are non-actionable here.
    }
  }

  private handleMessage(data: Buffer | string): void {
    if (this.closed) return;
    let parsed: unknown;
    try {
      const text = typeof data === "string" ? data : data.toString("utf8");
      parsed = JSON.parse(text);
    } catch {
      return;
    }
    if (!isBridgeResult(parsed)) return;
    const entry = this.pending.get(parsed.messageId);
    if (!entry) return;
    this.pending.delete(parsed.messageId);
    entry.resolve(parsed);
  }

  private failAll(error: Error): void {
    this.closed = true;
    const entries = [...this.pending.values()];
    this.pending.clear();
    for (const entry of entries) entry.reject(error);
  }
}

function isBridgeResult(value: unknown): value is BridgeResultEnvelope {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.messageId === "string" &&
    typeof candidate.success === "boolean" &&
    (candidate.code === null || typeof candidate.code === "string") &&
    (candidate.projectRevision === null || typeof candidate.projectRevision === "number") &&
    typeof candidate.retryable === "boolean" &&
    Object.prototype.hasOwnProperty.call(candidate, "result");
}
