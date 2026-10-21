import { DatabaseSync } from "node:sqlite";

export type DeviceRecord = {
  deviceId: string;
  publicKeySpkiBase64: string;
  controllerId?: string | null;
  revokedAtEpochMs?: number | null;
};

export type PairingRecord = {
  code: string;
  deviceId: string;
  expiresAtEpochMs: number;
  claimedBy?: string | null;
  challengeBase64?: string | null;
  completedAtEpochMs?: number | null;
};

export interface BridgeStore {
  getDevice(deviceId: string): DeviceRecord | null;
  upsertDevice(device: DeviceRecord): void;
  revokeDevice(deviceId: string, nowEpochMs: number): void;
  bindDevice(deviceId: string, controllerId: string): void;
  getPairing(code: string): PairingRecord | null;
  createPairing(pairing: PairingRecord): void;
  updatePairing(pairing: PairingRecord): void;
  appendAudit(eventType: string, deviceId: string | null, metadataJson: string, nowEpochMs: number): void;
}

export class InMemoryBridgeStore implements BridgeStore {
  private devices = new Map<string, DeviceRecord>();
  private pairings = new Map<string, PairingRecord>();
  private audit: Array<{ eventType: string; deviceId: string | null; metadataJson: string; nowEpochMs: number }> = [];

  getDevice(deviceId: string): DeviceRecord | null {
    const value = this.devices.get(deviceId);
    return value ? { ...value } : null;
  }

  upsertDevice(device: DeviceRecord): void {
    this.devices.set(device.deviceId, { ...device });
  }

  revokeDevice(deviceId: string, nowEpochMs: number): void {
    const current = this.devices.get(deviceId);
    if (!current) throw new Error("DEVICE_NOT_FOUND");
    this.devices.set(deviceId, { ...current, revokedAtEpochMs: nowEpochMs });
  }

  bindDevice(deviceId: string, controllerId: string): void {
    const current = this.devices.get(deviceId);
    if (!current) throw new Error("DEVICE_NOT_FOUND");
    this.devices.set(deviceId, { ...current, controllerId });
  }

  getPairing(code: string): PairingRecord | null {
    const value = this.pairings.get(code);
    return value ? { ...value } : null;
  }

  createPairing(pairing: PairingRecord): void {
    if (this.pairings.has(pairing.code)) throw new Error("PAIRING_CODE_COLLISION");
    this.pairings.set(pairing.code, { ...pairing });
  }

  updatePairing(pairing: PairingRecord): void {
    if (!this.pairings.has(pairing.code)) throw new Error("PAIRING_NOT_FOUND");
    this.pairings.set(pairing.code, { ...pairing });
  }

  appendAudit(eventType: string, deviceId: string | null, metadataJson: string, nowEpochMs: number): void {
    this.audit.push({ eventType, deviceId, metadataJson: metadataJson.slice(0, 2_000), nowEpochMs });
    if (this.audit.length > 1_000) this.audit.splice(0, this.audit.length - 1_000);
  }
}

export class SqliteBridgeStore implements BridgeStore {
  private db: DatabaseSync;

  constructor(path: string) {
    this.db = new DatabaseSync(path);
    this.db.exec(`
      PRAGMA journal_mode=WAL;
      PRAGMA foreign_keys=ON;
      CREATE TABLE IF NOT EXISTS devices (
        device_id TEXT PRIMARY KEY,
        public_key_spki_base64 TEXT NOT NULL,
        controller_id TEXT,
        revoked_at_epoch_ms INTEGER
      ) STRICT;
      CREATE TABLE IF NOT EXISTS pairings (
        code TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        expires_at_epoch_ms INTEGER NOT NULL,
        claimed_by TEXT,
        challenge_base64 TEXT,
        completed_at_epoch_ms INTEGER,
        FOREIGN KEY(device_id) REFERENCES devices(device_id)
      ) STRICT;
      CREATE TABLE IF NOT EXISTS audit (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event_type TEXT NOT NULL,
        device_id TEXT,
        metadata_json TEXT NOT NULL,
        created_at_epoch_ms INTEGER NOT NULL
      ) STRICT;
      CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit(created_at_epoch_ms);
    `);
  }

  getDevice(deviceId: string): DeviceRecord | null {
    const row = this.db.prepare(`
      SELECT device_id, public_key_spki_base64, controller_id, revoked_at_epoch_ms
      FROM devices WHERE device_id = ?
    `).get(deviceId) as Record<string, unknown> | undefined;
    if (!row) return null;
    return {
      deviceId: String(row.device_id),
      publicKeySpkiBase64: String(row.public_key_spki_base64),
      controllerId: row.controller_id == null ? null : String(row.controller_id),
      revokedAtEpochMs: row.revoked_at_epoch_ms == null ? null : Number(row.revoked_at_epoch_ms),
    };
  }

  upsertDevice(device: DeviceRecord): void {
    this.db.prepare(`
      INSERT INTO devices(device_id, public_key_spki_base64, controller_id, revoked_at_epoch_ms)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(device_id) DO UPDATE SET
        public_key_spki_base64=excluded.public_key_spki_base64,
        controller_id=excluded.controller_id,
        revoked_at_epoch_ms=excluded.revoked_at_epoch_ms
    `).run(
      device.deviceId,
      device.publicKeySpkiBase64,
      device.controllerId ?? null,
      device.revokedAtEpochMs ?? null,
    );
  }

  revokeDevice(deviceId: string, nowEpochMs: number): void {
    const result = this.db.prepare("UPDATE devices SET revoked_at_epoch_ms = ? WHERE device_id = ?")
      .run(nowEpochMs, deviceId);
    if (Number(result.changes) !== 1) throw new Error("DEVICE_NOT_FOUND");
  }

  bindDevice(deviceId: string, controllerId: string): void {
    const result = this.db.prepare("UPDATE devices SET controller_id = ? WHERE device_id = ? AND revoked_at_epoch_ms IS NULL")
      .run(controllerId, deviceId);
    if (Number(result.changes) !== 1) throw new Error("DEVICE_NOT_FOUND_OR_REVOKED");
  }

  getPairing(code: string): PairingRecord | null {
    const row = this.db.prepare(`
      SELECT code, device_id, expires_at_epoch_ms, claimed_by, challenge_base64, completed_at_epoch_ms
      FROM pairings WHERE code = ?
    `).get(code) as Record<string, unknown> | undefined;
    if (!row) return null;
    return {
      code: String(row.code),
      deviceId: String(row.device_id),
      expiresAtEpochMs: Number(row.expires_at_epoch_ms),
      claimedBy: row.claimed_by == null ? null : String(row.claimed_by),
      challengeBase64: row.challenge_base64 == null ? null : String(row.challenge_base64),
      completedAtEpochMs: row.completed_at_epoch_ms == null ? null : Number(row.completed_at_epoch_ms),
    };
  }

  createPairing(pairing: PairingRecord): void {
    this.db.prepare(`
      INSERT INTO pairings(code, device_id, expires_at_epoch_ms, claimed_by, challenge_base64, completed_at_epoch_ms)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(
      pairing.code,
      pairing.deviceId,
      pairing.expiresAtEpochMs,
      pairing.claimedBy ?? null,
      pairing.challengeBase64 ?? null,
      pairing.completedAtEpochMs ?? null,
    );
  }

  updatePairing(pairing: PairingRecord): void {
    const result = this.db.prepare(`
      UPDATE pairings SET device_id=?, expires_at_epoch_ms=?, claimed_by=?, challenge_base64=?, completed_at_epoch_ms=?
      WHERE code=?
    `).run(
      pairing.deviceId,
      pairing.expiresAtEpochMs,
      pairing.claimedBy ?? null,
      pairing.challengeBase64 ?? null,
      pairing.completedAtEpochMs ?? null,
      pairing.code,
    );
    if (Number(result.changes) !== 1) throw new Error("PAIRING_NOT_FOUND");
  }

  appendAudit(eventType: string, deviceId: string | null, metadataJson: string, nowEpochMs: number): void {
    this.db.prepare(`
      INSERT INTO audit(event_type, device_id, metadata_json, created_at_epoch_ms) VALUES (?, ?, ?, ?)
    `).run(eventType, deviceId, metadataJson.slice(0, 2_000), nowEpochMs);
    this.db.exec(`
      DELETE FROM audit
      WHERE id NOT IN (SELECT id FROM audit ORDER BY id DESC LIMIT 1000);
    `);
  }
}
