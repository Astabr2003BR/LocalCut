// Standalone experiment. Not wired to the relay. See experimentalReadOnlyGuard.md.
import { createPublicKey, randomBytes, verify } from 'node:crypto';

const ALLOWED = new Set(['get_device_status']);
const GRANT_FIELDS = ['version', 'audience', 'controllerId', 'deviceId', 'projectId', 'controllerKey', 'scopes', 'issuedAt', 'expiresAt'];
const COMMAND_FIELDS = ['protocolVersion', 'messageId', 'kind', 'deviceId', 'projectId', 'expectedRevision', 'issuedAtEpochMs', 'expiresAtEpochMs', 'nonce', 'capability', 'payload'];
const ownObject = v => v !== null && typeof v === 'object' && !Array.isArray(v) && Object.getPrototypeOf(v) === Object.prototype;
const exact = (v, fields) => ownObject(v) && Object.keys(v).length === fields.length && fields.every(k => Object.hasOwn(v, k));
const id = v => typeof v === 'string' && /^[A-Za-z0-9._-]{1,128}$/.test(v);
const time = v => Number.isSafeInteger(v) && v >= 0;
function decoded(v, len) {
  if (typeof v !== 'string' || !/^[A-Za-z0-9_-]+$/.test(v)) return null;
  const b = Buffer.from(v, 'base64url');
  return b.length === len && b.toString('base64url') === v ? b : null;
}
function publicKey(v) {
  // SPKI DER encoding of Ed25519 is exactly 44 bytes.
  const b = decoded(v, 44);
  if (!b) return null;
  try {
    const k = createPublicKey({ key: b, type: 'spki', format: 'der' });
    return k.asymmetricKeyType === 'ed25519' && k.export({ type: 'spki', format: 'der' }).equals(b) ? k : null;
  } catch { return null; }
}
// Fixed arrays avoid object-order ambiguity; command payload is required to be {}.
export function experimentalGrantSigningBytes(g) {
  return Buffer.from(JSON.stringify(['localcut-experimental-read-grant-v1', g.version, g.audience, g.controllerId, g.deviceId, g.projectId, g.controllerKey, g.scopes, g.issuedAt, g.expiresAt]));
}
export function experimentalCommandSigningBytes(g, grantSignature, c) {
  return Buffer.from(JSON.stringify(['localcut-experimental-read-command-v1', experimentalGrantSigningBytes(g).toString('base64url'), grantSignature, c.protocolVersion, c.messageId, c.kind, c.deviceId, c.projectId, c.expectedRevision, c.issuedAtEpochMs, c.expiresAtEpochMs, c.nonce, c.capability, {}]));
}

export class ExperimentalReadOnlyGuard {
  #authority;
  #binding;
  #audience = randomBytes(32).toString('base64url');
  #seen = new Map();
  #lastNow = 0;
  #baseWall = Date.now();
  #baseMono = process.hrtime.bigint();
  #clockInvalid = false;
  #capacity;
  constructor({ authorityPublicKey, verifyLiveBinding, replayCapacity = 10000 } = {}) {
    if (!authorityPublicKey || authorityPublicKey.type !== 'public' || authorityPublicKey.asymmetricKeyType !== 'ed25519') throw new Error('INVALID_AUTHORITY');
    if (typeof verifyLiveBinding !== 'function') throw new Error('BINDING_CHECK_REQUIRED');
    if (!Number.isSafeInteger(replayCapacity) || replayCapacity < 1 || replayCapacity > 100000) throw new Error('INVALID_CAPACITY');
    this.#authority = authorityPublicKey;
    this.#binding = verifyLiveBinding;
    this.#capacity = replayCapacity;
  }
  // An authority must issue grants specifically for this unpredictable process epoch.
  get audience() { return this.#audience; }
  // The input is a JSON string from the transport. Never forward or reparse it;
  // forward only the newly constructed command returned by this method.
  authorize(raw, authenticatedControllerId) {
    try {
      if (typeof raw !== 'string' || Buffer.byteLength(raw, 'utf8') > 16384 || !id(authenticatedControllerId)) return null;
      const envelope = JSON.parse(raw);
      if (!exact(envelope, ['grant', 'grantSignature', 'command', 'commandSignature'])) return null;
      const { grant: g, command: c, grantSignature: gs, commandSignature: cs } = envelope;
      if (!exact(g, GRANT_FIELDS) || !exact(c, COMMAND_FIELDS)) return null;
      if (g.version !== 1 || g.audience !== this.#audience || !id(g.controllerId) || g.controllerId !== authenticatedControllerId || !id(g.deviceId) || !id(g.projectId)) return null;
      if (!Array.isArray(g.scopes) || g.scopes.length < 1 || g.scopes.length > ALLOWED.size || !g.scopes.every(s => typeof s === 'string' && ALLOWED.has(s)) || new Set(g.scopes).size !== g.scopes.length) return null;
      const key = publicKey(g.controllerKey), grantSig = decoded(gs, 64), commandSig = decoded(cs, 64);
      if (!key || !grantSig || !commandSig || !verify(null, experimentalGrantSigningBytes(g), this.#authority, grantSig)) return null;
      if (c.protocolVersion !== 1 || c.kind !== 'command' || !id(c.messageId) || !id(c.deviceId) || !id(c.projectId) || c.deviceId !== g.deviceId || c.projectId !== g.projectId || c.expectedRevision !== null || !decoded(c.nonce, 32)) return null;
      if (typeof c.capability !== 'string' || !ALLOWED.has(c.capability) || !g.scopes.includes(c.capability) || !exact(c.payload, [])) return null;
      if (![g.issuedAt, g.expiresAt, c.issuedAtEpochMs, c.expiresAtEpochMs].every(time)) return null;
      const current = Date.now();
      if (!time(current)) return null;
      // A wall-clock change must not extend a grant: poison this epoch on drift.
      const elapsed = Number(process.hrtime.bigint() - this.#baseMono) / 1e6;
      if (this.#clockInvalid || elapsed < 0 || Math.abs((current - this.#baseWall) - elapsed) > 2000) {
        this.#clockInvalid = true;
        return null;
      }
      const now = this.#lastNow = Math.max(this.#lastNow, current);
      if (g.issuedAt > now || now >= g.expiresAt || g.expiresAt - g.issuedAt > 300000 || c.issuedAtEpochMs < g.issuedAt || c.issuedAtEpochMs > now || now >= c.expiresAtEpochMs || c.expiresAtEpochMs > g.expiresAt || c.expiresAtEpochMs - c.issuedAtEpochMs > 30000) return null;
      if (!verify(null, experimentalCommandSigningBytes(g, gs, c), key, commandSig)) return null;
      // This must synchronously check the authenticated connection and current
      // authoritative device/controller/project/key association, including revocation.
      if (this.#binding(Object.freeze({ controllerId: g.controllerId, deviceId: g.deviceId, projectId: g.projectId, controllerKey: g.controllerKey })) !== true) return null;
      for (const [k, expiry] of this.#seen) if (expiry <= now) this.#seen.delete(k);
      // Scoped to all grants in this process, so reissuing a grant cannot reset replay.
      const nonceKey = JSON.stringify(['nonce', g.controllerId, g.deviceId, c.nonce]);
      const messageKey = JSON.stringify(['message', g.controllerId, g.deviceId, c.messageId]);
      if (this.#seen.has(nonceKey) || this.#seen.has(messageKey) || this.#seen.size + 2 > this.#capacity) return null;
      this.#seen.set(nonceKey, g.expiresAt);
      this.#seen.set(messageKey, g.expiresAt);
      return Object.freeze({ protocolVersion: 1, messageId: c.messageId, kind: 'command', deviceId: c.deviceId, projectId: c.projectId, expectedRevision: null, issuedAtEpochMs: c.issuedAtEpochMs, expiresAtEpochMs: c.expiresAtEpochMs, nonce: c.nonce, capability: c.capability, payload: Object.freeze({}) });
    } catch { return null; }
  }
}
