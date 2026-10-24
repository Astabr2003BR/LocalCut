import { generateKeyPairSync, sign } from "node:crypto";
import WebSocket from "ws";
import { CommandRouter, DeviceRegistry } from "../src/commandRouter.ts";
import { PairingService } from "../src/pairing.ts";
import type { BridgeCapability, BridgeCommandEnvelope, BridgeResultEnvelope } from "../src/protocol.ts";
import { DeviceSessionService, InMemorySessionStore } from "../src/session.ts";
import { createControllerServer, type ControllerServerRuntime } from "../src/server.ts";
import { InMemoryBridgeStore } from "../src/store.ts";

const CONTROLLER_ID = "controller-e2e";
const CONTROLLER_TOKEN = "controller-secret-e2e";
const DEVICE_ID = "localcut-e2e-device";
const PROJECT_ID = "localcut-e2e-project";
const PAIRING_CODE = "246810";

export type E2eCommandOptions = {
  messageId: string;
  capability: BridgeCapability;
  expectedRevision?: number | null;
  payload?: Record<string, unknown>;
};

export type LocalCutBridgeE2eFixture = {
  runtime: ControllerServerRuntime;
  router: CommandRouter;
  device: FakeLocalCutDevice;
  pairAndConnect(): Promise<void>;
  execute(options: E2eCommandOptions): Promise<BridgeResultEnvelope>;
  close(): Promise<void>;
};

export async function startLocalCutBridgeE2eFixture(): Promise<LocalCutBridgeE2eFixture> {
  const store = new InMemoryBridgeStore();
  const pairing = new PairingService(store, {
    codeGenerator: () => PAIRING_CODE,
    challengeGenerator: () => Buffer.from("localcut-e2e-challenge-32-bytes!!").subarray(0, 32),
  });
  let tokenCounter = 0;
  const sessions = new DeviceSessionService(store, new InMemorySessionStore(), {
    tokenGenerator: () => `e2e-token-${++tokenCounter}-${"x".repeat(24)}`,
  });
  const registry = new DeviceRegistry();
  const router = new CommandRouter(store, registry, { timeoutMs: 2_000 });
  let nowEpochMs = 10_000;
  const runtime = createControllerServer({
    controllerApiToken: CONTROLLER_TOKEN,
    controllerId: CONTROLLER_ID,
    store,
    pairing,
    sessions,
    registry,
    router,
    nowEpochMs: () => nowEpochMs,
  });
  await runtime.listen(0, "127.0.0.1");
  const address = runtime.address();
  if (!address || typeof address === "string") throw new Error("E2E_SERVER_ADDRESS_MISSING");
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const wsUrl = `ws://127.0.0.1:${address.port}/v1/device/connect`;
  const device = new FakeLocalCutDevice(baseUrl, wsUrl);

  return {
    runtime,
    router,
    device,
    async pairAndConnect() {
      await device.pairAndConnect(CONTROLLER_TOKEN);
      nowEpochMs += 1_000;
    },
    execute(options) {
      const command: BridgeCommandEnvelope = {
        protocolVersion: 1,
        messageId: options.messageId,
        kind: "command",
        deviceId: DEVICE_ID,
        projectId: PROJECT_ID,
        expectedRevision: options.expectedRevision ?? null,
        issuedAtEpochMs: nowEpochMs,
        expiresAtEpochMs: nowEpochMs + 30_000,
        nonce: `nonce-${options.messageId}`,
        capability: options.capability,
        payload: options.payload ?? {},
      };
      return router.execute(CONTROLLER_ID, command);
    },
    async close() {
      device.disconnect();
      await runtime.close();
    },
  };
}

export class FakeLocalCutDevice {
  private baseUrl: string;
  private wsUrl: string;
  private socket: WebSocket | null = null;
  private revision = 4;
  private playheadUs = 0;
  private clipStartUs = 0;
  private previousClipStartUs: number | null = null;
  private exportJobId: string | null = null;
  private executionCount = new Map<string, number>();

  constructor(baseUrl: string, wsUrl: string) {
    this.baseUrl = baseUrl;
    this.wsUrl = wsUrl;
  }

  get currentRevision(): number {
    return this.revision;
  }

  executionsFor(messageId: string): number {
    return this.executionCount.get(messageId) ?? 0;
  }

  async pairAndConnect(controllerToken: string): Promise<void> {
    const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const publicKeySpkiBase64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");

    const started = await postJson(this.baseUrl, "/v1/pair/start", {
      deviceId: DEVICE_ID,
      publicKeySpkiBase64,
    });
    requireStatus(started, 200, "PAIR_START_FAILED");
    const startedBody = await started.json() as { code: string };
    if (startedBody.code !== PAIRING_CODE) throw new Error("PAIR_CODE_UNEXPECTED");

    const claimed = await postJson(
      this.baseUrl,
      "/v1/pair/claim",
      { code: PAIRING_CODE },
      controllerToken,
    );
    requireStatus(claimed, 200, "PAIR_CLAIM_FAILED");

    const status = await postJson(this.baseUrl, "/v1/pair/status", { code: PAIRING_CODE });
    requireStatus(status, 200, "PAIR_STATUS_FAILED");
    const statusBody = await status.json() as { state: string; challengeBase64: string | null };
    if (statusBody.state !== "CLAIMED" || !statusBody.challengeBase64) {
      throw new Error("PAIR_CHALLENGE_MISSING");
    }

    const signatureBase64 = sign(
      "sha256",
      Buffer.from(statusBody.challengeBase64, "base64"),
      privateKey,
    ).toString("base64");
    const completed = await postJson(this.baseUrl, "/v1/pair/status", {
      code: PAIRING_CODE,
      signatureBase64,
    });
    requireStatus(completed, 200, "PAIR_COMPLETE_FAILED");
    const completedBody = await completed.json() as { state: string; accessToken: string };
    if (completedBody.state !== "PAIRED" || !completedBody.accessToken) {
      throw new Error("PAIR_SESSION_MISSING");
    }

    await this.open(completedBody.accessToken);
  }

  disconnect(): void {
    this.socket?.close(1000, "e2e_complete");
    this.socket = null;
  }

  private open(accessToken: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(this.wsUrl, {
        headers: { authorization: `Bearer ${accessToken}` },
      });
      this.socket = socket;
      socket.once("open", () => resolve());
      socket.once("error", reject);
      socket.on("message", raw => {
        void this.handleCommand(socket, raw.toString());
      });
    });
  }

  private async handleCommand(socket: WebSocket, raw: string): Promise<void> {
    const command = JSON.parse(raw) as BridgeCommandEnvelope;
    this.executionCount.set(command.messageId, this.executionsFor(command.messageId) + 1);
    const result = this.executeLocally(command);
    socket.send(JSON.stringify(result));
  }

  private executeLocally(command: BridgeCommandEnvelope): BridgeResultEnvelope {
    if (command.projectId !== PROJECT_ID) {
      return failure(command.messageId, "PROJECT_NOT_FOUND", this.revision);
    }

    switch (command.capability) {
      case "get_project_state":
        return success(command.messageId, this.revision, {
          projectId: PROJECT_ID,
          revision: this.revision,
          playheadUs: this.playheadUs,
          clipStartUs: this.clipStartUs,
        });
      case "seek": {
        const positionUs = asNonNegativeInteger(command.payload.positionUs);
        if (positionUs === null) return failure(command.messageId, "INVALID_ARGUMENT", this.revision);
        this.playheadUs = positionUs;
        return success(command.messageId, this.revision, { playheadUs: this.playheadUs });
      }
      case "move_clip": {
        const conflict = this.requireRevision(command);
        if (conflict) return conflict;
        const startUs = asNonNegativeInteger(command.payload.startUs);
        if (startUs === null) return failure(command.messageId, "INVALID_ARGUMENT", this.revision);
        this.previousClipStartUs = this.clipStartUs;
        this.clipStartUs = startUs;
        this.revision += 1;
        return success(command.messageId, this.revision, { clipId: "clip-1", startUs });
      }
      case "undo": {
        const conflict = this.requireRevision(command);
        if (conflict) return conflict;
        if (this.previousClipStartUs === null) return failure(command.messageId, "NOTHING_TO_UNDO", this.revision);
        const restored = this.previousClipStartUs;
        this.previousClipStartUs = this.clipStartUs;
        this.clipStartUs = restored;
        this.revision += 1;
        return success(command.messageId, this.revision, { undone: true, clipStartUs: this.clipStartUs });
      }
      case "export_project": {
        const conflict = this.requireRevision(command);
        if (conflict) return conflict;
        if (!this.exportJobId) this.exportJobId = "export-job-e2e-1";
        return success(command.messageId, this.revision, { jobId: this.exportJobId, state: "queued" });
      }
      default:
        return failure(command.messageId, "CAPABILITY_NOT_IMPLEMENTED_IN_FIXTURE", this.revision);
    }
  }

  private requireRevision(command: BridgeCommandEnvelope): BridgeResultEnvelope | null {
    if (command.expectedRevision !== this.revision) {
      return failure(command.messageId, "REVISION_CONFLICT", this.revision);
    }
    return null;
  }
}

async function postJson(baseUrl: string, path: string, body: unknown, token?: string): Promise<Response> {
  return fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
}

function requireStatus(response: Response, expected: number, code: string): void {
  if (response.status !== expected) throw new Error(`${code}:${response.status}`);
}

function asNonNegativeInteger(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function success(messageId: string, projectRevision: number, result: unknown): BridgeResultEnvelope {
  return {
    messageId,
    success: true,
    code: null,
    projectRevision,
    result,
    retryable: false,
  };
}

function failure(messageId: string, code: string, projectRevision: number): BridgeResultEnvelope {
  return {
    messageId,
    success: false,
    code,
    projectRevision,
    result: {},
    retryable: false,
  };
}
