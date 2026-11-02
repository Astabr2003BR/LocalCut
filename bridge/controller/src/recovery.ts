import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import type { DeviceRecord } from "./store.ts";

const RECOVERY_TOKEN_VERSION = 1;
const RECOVERY_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1_000;
const TOKEN_SEGMENT = /^[A-Za-z0-9_-]+$/;

export type DeviceRecoveryClaims = {
  version: 1;
  deviceId: string;
  controllerId: string;
  publicKeySha256: string;
  expiresAtEpochMs: number;
};

export class DeviceRecoveryTokenService {
  private secret: Buffer;

  constructor(secret: string) {
    if (secret.length < 16) throw new Error("RECOVERY_SECRET_TOO_SHORT");
    this.secret = Buffer.from(secret, "utf8");
  }

  issue(device: DeviceRecord, controllerId: string, nowEpochMs: number): string {
    if (!device.deviceId || !controllerId || !device.publicKeySpkiBase64) {
      throw new Error("RECOVERY_CLAIMS_INVALID");
    }
    const claims: DeviceRecoveryClaims = {
      version: RECOVERY_TOKEN_VERSION,
      deviceId: device.deviceId,
      controllerId,
      publicKeySha256: publicKeySha256(device.publicKeySpkiBase64),
      expiresAtEpochMs: nowEpochMs + RECOVERY_TOKEN_TTL_MS,
    };
    const payload = Buffer.from(JSON.stringify(claims), "utf8").toString("base64url");
    return `${payload}.${this.sign(payload)}`;
  }

  verify(token: string, controllerId: string, nowEpochMs: number): DeviceRecoveryClaims {
    if (!token || token.length > 4_096) throw new Error("RECOVERY_TOKEN_INVALID");
    const parts = token.split(".");
    if (parts.length !== 2 || !parts.every(part => TOKEN_SEGMENT.test(part))) {
      throw new Error("RECOVERY_TOKEN_INVALID");
    }
    const [payload, signature] = parts;
    const expected = this.sign(payload);
    const actualBytes = Buffer.from(signature, "utf8");
    const expectedBytes = Buffer.from(expected, "utf8");
    if (actualBytes.length !== expectedBytes.length || !timingSafeEqual(actualBytes, expectedBytes)) {
      throw new Error("RECOVERY_TOKEN_INVALID");
    }
    let raw: unknown;
    try {
      raw = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    } catch {
      throw new Error("RECOVERY_TOKEN_INVALID");
    }
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("RECOVERY_TOKEN_INVALID");
    const value = raw as Record<string, unknown>;
    if (
      value.version !== RECOVERY_TOKEN_VERSION ||
      typeof value.deviceId !== "string" || value.deviceId.length === 0 || value.deviceId.length > 128 ||
      typeof value.controllerId !== "string" || value.controllerId.length === 0 || value.controllerId.length > 256 ||
      typeof value.publicKeySha256 !== "string" || !/^[a-f0-9]{64}$/.test(value.publicKeySha256) ||
      typeof value.expiresAtEpochMs !== "number" || !Number.isSafeInteger(value.expiresAtEpochMs)
    ) {
      throw new Error("RECOVERY_TOKEN_INVALID");
    }
    if (value.controllerId !== controllerId) throw new Error("RECOVERY_TOKEN_INVALID");
    if (value.expiresAtEpochMs <= nowEpochMs) throw new Error("RECOVERY_TOKEN_EXPIRED");
    return {
      version: RECOVERY_TOKEN_VERSION,
      deviceId: value.deviceId,
      controllerId: value.controllerId,
      publicKeySha256: value.publicKeySha256,
      expiresAtEpochMs: value.expiresAtEpochMs,
    };
  }

  private sign(payload: string): string {
    return createHmac("sha256", this.secret).update(payload, "utf8").digest("base64url");
  }
}

export function publicKeySha256(publicKeySpkiBase64: string): string {
  if (!publicKeySpkiBase64 || publicKeySpkiBase64.length > 16_384) {
    throw new Error("DEVICE_IDENTITY_INVALID");
  }
  let bytes: Buffer;
  try {
    bytes = Buffer.from(publicKeySpkiBase64, "base64");
  } catch {
    throw new Error("DEVICE_IDENTITY_INVALID");
  }
  if (bytes.length === 0) throw new Error("DEVICE_IDENTITY_INVALID");
  return createHash("sha256").update(bytes).digest("hex");
}
