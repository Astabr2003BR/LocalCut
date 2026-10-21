import { createPublicKey, randomBytes, randomInt, verify } from "node:crypto";
import type { BridgeStore, PairingRecord } from "./store.ts";

const PAIRING_TTL_MS = 10 * 60 * 1_000;

export type PairingStartResult = {
  code: string;
  deviceId: string;
  expiresAtEpochMs: number;
};

export type PairingClaimResult = {
  deviceId: string;
  challengeBase64: string;
  expiresAtEpochMs: number;
};

export type PairingStatus = {
  state: "PENDING" | "CLAIMED" | "PAIRED" | "EXPIRED";
  deviceId: string;
  expiresAtEpochMs: number;
};

export type PairingServiceOptions = {
  codeGenerator?: () => string;
  challengeGenerator?: () => Buffer;
};

export class PairingService {
  private store: BridgeStore;
  private codeGenerator: () => string;
  private challengeGenerator: () => Buffer;

  constructor(store: BridgeStore, options: PairingServiceOptions = {}) {
    this.store = store;
    this.codeGenerator = options.codeGenerator ?? (() => randomInt(0, 1_000_000).toString().padStart(6, "0"));
    this.challengeGenerator = options.challengeGenerator ?? (() => randomBytes(32));
  }

  start(deviceId: string, publicKeySpkiBase64: string, nowEpochMs: number): PairingStartResult {
    if (!deviceId || !publicKeySpkiBase64) throw new Error("PAIRING_INVALID_DEVICE");
    const existing = this.store.getDevice(deviceId);
    if (existing?.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");
    if (existing && existing.publicKeySpkiBase64 !== publicKeySpkiBase64) {
      throw new Error("DEVICE_IDENTITY_MISMATCH");
    }
    if (!existing) {
      this.store.upsertDevice({
        deviceId,
        publicKeySpkiBase64,
        controllerId: null,
        revokedAtEpochMs: null,
      });
    }

    const code = this.allocateCode();
    const pairing: PairingRecord = {
      code,
      deviceId,
      expiresAtEpochMs: nowEpochMs + PAIRING_TTL_MS,
      claimedBy: null,
      challengeBase64: null,
      completedAtEpochMs: null,
    };
    this.store.createPairing(pairing);
    this.store.appendAudit("pairing_started", deviceId, "{}", nowEpochMs);
    return { code, deviceId, expiresAtEpochMs: pairing.expiresAtEpochMs };
  }

  claim(code: string, controllerId: string, nowEpochMs: number): PairingClaimResult {
    if (!controllerId) throw new Error("PAIRING_CONTROLLER_REQUIRED");
    const pairing = this.requirePairing(code);
    this.requireNotExpired(pairing, nowEpochMs);
    if (pairing.completedAtEpochMs != null || pairing.claimedBy != null) {
      throw new Error("PAIRING_ALREADY_CLAIMED");
    }
    const device = this.store.getDevice(pairing.deviceId);
    if (!device || device.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");

    const challengeBase64 = this.challengeGenerator().toString("base64");
    const claimed: PairingRecord = {
      ...pairing,
      claimedBy: controllerId,
      challengeBase64,
    };
    this.store.updatePairing(claimed);
    this.store.appendAudit("pairing_claimed", pairing.deviceId, "{}", nowEpochMs);
    return {
      deviceId: pairing.deviceId,
      challengeBase64,
      expiresAtEpochMs: pairing.expiresAtEpochMs,
    };
  }

  complete(code: string, signatureBase64: string, nowEpochMs: number): { deviceId: string; controllerId: string } {
    const pairing = this.requirePairing(code);
    this.requireNotExpired(pairing, nowEpochMs);
    if (pairing.completedAtEpochMs != null) throw new Error("PAIRING_ALREADY_COMPLETED");
    if (!pairing.claimedBy || !pairing.challengeBase64) throw new Error("PAIRING_NOT_CLAIMED");

    const device = this.store.getDevice(pairing.deviceId);
    if (!device || device.revokedAtEpochMs != null) throw new Error("DEVICE_REVOKED");

    const publicKey = createPublicKey({
      key: Buffer.from(device.publicKeySpkiBase64, "base64"),
      format: "der",
      type: "spki",
    });
    const valid = verify(
      "sha256",
      Buffer.from(pairing.challengeBase64, "base64"),
      publicKey,
      Buffer.from(signatureBase64, "base64"),
    );
    if (!valid) throw new Error("PAIRING_SIGNATURE_INVALID");

    this.store.bindDevice(pairing.deviceId, pairing.claimedBy);
    this.store.updatePairing({ ...pairing, completedAtEpochMs: nowEpochMs });
    this.store.appendAudit("pairing_completed", pairing.deviceId, "{}", nowEpochMs);
    return { deviceId: pairing.deviceId, controllerId: pairing.claimedBy };
  }

  status(code: string, nowEpochMs: number): PairingStatus {
    const pairing = this.requirePairing(code);
    let state: PairingStatus["state"];
    if (pairing.completedAtEpochMs != null) state = "PAIRED";
    else if (nowEpochMs > pairing.expiresAtEpochMs) state = "EXPIRED";
    else if (pairing.claimedBy != null) state = "CLAIMED";
    else state = "PENDING";
    return { state, deviceId: pairing.deviceId, expiresAtEpochMs: pairing.expiresAtEpochMs };
  }

  unpairDevice(deviceId: string, nowEpochMs: number): void {
    this.store.unbindDevice(deviceId);
    this.store.appendAudit("device_unpaired", deviceId, "{}", nowEpochMs);
  }

  revokeDevice(deviceId: string, nowEpochMs: number): void {
    this.store.revokeDevice(deviceId, nowEpochMs);
    this.store.appendAudit("device_revoked", deviceId, "{}", nowEpochMs);
  }

  private allocateCode(): string {
    for (let attempt = 0; attempt < 20; attempt += 1) {
      const code = this.codeGenerator();
      if (!/^\d{6}$/.test(code)) throw new Error("PAIRING_CODE_GENERATOR_INVALID");
      if (!this.store.getPairing(code)) return code;
    }
    throw new Error("PAIRING_CODE_EXHAUSTED");
  }

  private requirePairing(code: string): PairingRecord {
    const pairing = this.store.getPairing(code);
    if (!pairing) throw new Error("PAIRING_NOT_FOUND");
    return pairing;
  }

  private requireNotExpired(pairing: PairingRecord, nowEpochMs: number): void {
    if (nowEpochMs > pairing.expiresAtEpochMs) throw new Error("PAIRING_EXPIRED");
  }
}
