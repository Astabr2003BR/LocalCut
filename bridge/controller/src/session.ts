import { createHash, randomBytes } from "node:crypto";
import { DatabaseSync } from "node:sqlite";
import type { BridgeStore } from "./store.ts";

const ACCESS_TTL_MS = 60 * 60 * 1_000;
const REFRESH_TTL_MS = 30 * 24 * 60 * 60 * 1_000;

export type DeviceSessionRecord = {
  deviceId: string;
  accessTokenHash: string;
  accessExpiresAtEpochMs: number;
  refreshTokenHash: string;
  refreshExpiresAtEpochMs: number;
};

export type IssuedDeviceSession = {
  deviceId: string;
  accessToken: string;
  accessExpiresAtEpochMs: number;
  refreshToken: string;
  refreshExpiresAtEpochMs: number;
};

export interface DeviceSessionStore {
  save(record: DeviceSessionRecord): void;
  getByDeviceId(deviceId: string): DeviceSessionRecord | null;
  getByAccessTokenHash(accessTokenHash: string): DeviceSessionRecord | null;
  getByRefreshTokenHash(refreshTokenHash: string): DeviceSessionRecord | null;
  deleteByDeviceId(deviceId: string): void;
}

export class InMemorySessionStore implements DeviceSessionStore {
  private records = new Map<string, DeviceSessionRecord>();

  save(record: DeviceSessionRecord): void {
    this.records.set(record.deviceId, { ...record });
  }

  getByDeviceId(deviceId: string): DeviceSessionRecord | null {
    const value = this.records.get(deviceId);
    return value ? { ...value } : null;
  }

  getByAccessTokenHash(accessTokenHash: string): DeviceSessionRecord | null {
    for (const record of this.records.values()) {
      if (record.accessTokenHash === accessTokenHash) return { ...record };
    }
    return null;
  }

  getByRefreshTokenHash(refreshTokenHash: string): DeviceSessionRecord | null {
    for (const record of this.records.values()) {
      if (record.refreshTokenHash === refreshTokenHash) return { ...record };
    }
    return null;
  }

  deleteByDeviceId(deviceId: string): void {
    this.records.delete(deviceId);
  }
}

export class SqliteSessionStore implements DeviceSessionStore {
  private db: DatabaseSync;

  constructor(path: string) {
    this.db = new DatabaseSync(path);
    this.db.exec(`
      PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS device_sessions (
        device_id TEXT PRIMARY KEY,
        access_token_hash TEXT NOT NULL UNIQUE,
        access_expires_at_epoch_ms INTEGER NOT NULL,
        refresh_token_hash TEXT NOT NULL UNIQUE,
        refresh_expires_at_epoch_ms INTEGER NOT NULL
      ) STRICT;
      CREATE INDEX IF NOT EXISTS idx_device_sessions_access_hash ON device_sessions(access_token_hash);
      CREATE INDEX IF NOT EXISTS idx_device_sessions_refresh_hash ON device_sessions(refresh_token_hash);
    `);
  }

  save(record: DeviceSessionRecord): void {
    this.db.prepare(`
      INSERT INTO device_sessions(
        device_id,
        access_token_hash,
        access_expires_at_epoch_ms,
        refresh_token_hash,
        refresh_expires_at_epoch_ms
      ) VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(device_id) DO UPDATE SET
        access_token_hash=excluded.access_token_hash,
        access_expires_at_epoch_ms=excluded.access_expires_at_epoch_ms,
        refresh_token_hash=excluded.refresh_token_hash,
        refresh_expires_at_epoch_ms=excluded.refresh_expires_at_epoch_ms
    `).run(
      record.deviceId,
      record.accessTokenHash,
      record.accessExpiresAtEpochMs,
      record.refreshTokenHash,
      record.refreshExpiresAtEpochMs,
    );
  }

  getByDeviceId(deviceId: string): DeviceSessionRecord | null {
    return this.readOne("device_id", deviceId);
  }

  getByAccessTokenHash(accessTokenHash: string): DeviceSessionRecord | null {
    return this.readOne("access_token_hash", accessTokenHash);
  }

  getByRefreshTokenHash(refreshTokenHash: string): DeviceSessionRecord | null {
    return this.readOne("refresh_token_hash", refreshTokenHash);
  }

  deleteByDeviceId(deviceId: string): void {
    this.db.prepare("DELETE FROM device_sessions WHERE device_id = ?").run(deviceId);
  }

  private readOne(column: "device_id" | "access_token_hash" | "refresh_token_hash", value: string): DeviceSessionRecord | null {
    const row = this.db.prepare(`
      SELECT device_id, access_token_hash, access_expires_at_epoch_ms, refresh_token_hash, refresh_expires_at_epoch_ms
      FROM device_sessions WHERE ${column} = ?
    `).get(value) as Record<string, unknown> | undefined;
    if (!row) return null;
    return {
      deviceId: String(row.device_id),
      accessTokenHash: String(row.access_token_hash),
      accessExpiresAtEpochMs: Number(row.access_expires_at_epoch_ms),
      refreshTokenHash: String(row.refresh_token_hash),
      refreshExpiresAtEpochMs: Number(row.refresh_expires_at_epoch_ms),
    };
  }
}

export type DeviceSessionServiceOptions = {
  tokenGenerator?: () => string;
};

export class DeviceSessionService {
  private devices: BridgeStore;
  private sessions: DeviceSessionStore;
  private tokenGenerator: () => string;

  constructor(devices: BridgeStore, sessions: DeviceSessionStore, options: DeviceSessionServiceOptions = {}) {
    this.devices = devices;
    this.sessions = sessions;
    this.tokenGenerator = options.tokenGenerator ?? (() => randomBytes(32).toString("base64url"));
  }

  issue(deviceId: string, nowEpochMs: number): IssuedDeviceSession {
    const device = this.devices.getDevice(deviceId);
    if (!device || device.revokedAtEpochMs != null || !device.controllerId) {
      throw new Error("DEVICE_NOT_PAIRED_OR_REVOKED");
    }

    const accessToken = this.requireToken(this.tokenGenerator());
    const refreshToken = this.requireToken(this.tokenGenerator());
    const issued: IssuedDeviceSession = {
      deviceId,
      accessToken,
      accessExpiresAtEpochMs: nowEpochMs + ACCESS_TTL_MS,
      refreshToken,
      refreshExpiresAtEpochMs: nowEpochMs + REFRESH_TTL_MS,
    };
    this.sessions.save({
      deviceId,
      accessTokenHash: hashToken(accessToken),
      accessExpiresAtEpochMs: issued.accessExpiresAtEpochMs,
      refreshTokenHash: hashToken(refreshToken),
      refreshExpiresAtEpochMs: issued.refreshExpiresAtEpochMs,
    });
    return issued;
  }

  authenticateAccess(accessToken: string, nowEpochMs: number): DeviceSessionRecord | null {
    if (!accessToken) return null;
    const session = this.sessions.getByAccessTokenHash(hashToken(accessToken));
    if (!session || session.accessExpiresAtEpochMs <= nowEpochMs) return null;
    const device = this.devices.getDevice(session.deviceId);
    if (!device || device.revokedAtEpochMs != null || !device.controllerId) return null;
    return session;
  }

  refresh(refreshToken: string, nowEpochMs: number): IssuedDeviceSession {
    if (!refreshToken) throw new Error("REFRESH_TOKEN_INVALID");
    const session = this.sessions.getByRefreshTokenHash(hashToken(refreshToken));
    if (!session || session.refreshExpiresAtEpochMs <= nowEpochMs) {
      throw new Error("REFRESH_TOKEN_INVALID");
    }
    const device = this.devices.getDevice(session.deviceId);
    if (!device || device.revokedAtEpochMs != null || !device.controllerId) {
      this.sessions.deleteByDeviceId(session.deviceId);
      throw new Error("REFRESH_TOKEN_INVALID");
    }
    return this.issue(session.deviceId, nowEpochMs);
  }

  revoke(deviceId: string): void {
    this.sessions.deleteByDeviceId(deviceId);
  }

  private requireToken(value: string): string {
    if (!value || value.length < 8) throw new Error("TOKEN_GENERATOR_INVALID");
    return value;
  }
}

function hashToken(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}
