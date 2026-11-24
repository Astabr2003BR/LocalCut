import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { ExperimentalReadOnlyGuard, experimentalGrantSigningBytes, experimentalCommandSigningBytes } from '../src/experimental/experimentalReadOnlyGuard.mjs';

const pair = () => generateKeyPairSync('ed25519');
const enc = b => b.toString('base64url');
function setup(opts = {}) {
  const authority = pair(), controller = pair(), other = pair();
  let bound = true;
  const guard = new ExperimentalReadOnlyGuard({ authorityPublicKey: authority.publicKey, verifyLiveBinding: () => bound, ...opts });
  const now = Date.now();
  const grant = { version: 1, audience: guard.audience, controllerId: 'controller', deviceId: 'device', projectId: 'project', controllerKey: enc(controller.publicKey.export({ format: 'der', type: 'spki' })), scopes: ['get_device_status'], issuedAt: now - 1000, expiresAt: now + 60000 };
  const command = { protocolVersion: 1, messageId: enc(randomBytes(32)), kind: 'command', deviceId: 'device', projectId: 'project', expectedRevision: null, issuedAtEpochMs: now - 500, expiresAtEpochMs: now + 10000, nonce: enc(randomBytes(32)), capability: 'get_device_status', payload: {} };
  function pack(g = grant, c = command, a = authority.privateKey, signer = controller.privateKey) {
    const grantSignature = enc(sign(null, experimentalGrantSigningBytes(g), a));
    const commandSignature = enc(sign(null, experimentalCommandSigningBytes(g, grantSignature, c), signer));
    return JSON.stringify({ grant: g, grantSignature, command: c, commandSignature });
  }
  return { authority, controller, other, guard, grant, command, pack, revoke: () => { bound = false; } };
}
const rejected = value => assert.ok(value === null);
const accepted = value => assert.ok(value !== null && value.capability === 'get_device_status' && Object.isFrozen(value) && Object.isFrozen(value.payload));

test('requires a pinned Ed25519 authority and synchronous binding checker', () => {
  assert.throws(() => new ExperimentalReadOnlyGuard());
  assert.throws(() => new ExperimentalReadOnlyGuard({ authorityPublicKey: pair().publicKey }));
  assert.throws(() => new ExperimentalReadOnlyGuard({ authorityPublicKey: pair().publicKey, verifyLiveBinding: () => true, replayCapacity: 0 }));
});
test('valid scoped command is reconstructed and replay is denied', () => {
  const x = setup(); const raw = x.pack(); accepted(x.guard.authorize(raw, 'controller')); rejected(x.guard.authorize(raw, 'controller'));
});
test('message and nonce each independently reject replay even if other fields differ', () => {
  for (const field of ['messageId', 'nonce']) {
    const x = setup(); accepted(x.guard.authorize(x.pack(), 'controller'));
    const c = { ...x.command, [field === 'nonce' ? 'messageId' : 'nonce']: enc(randomBytes(32)) };
    rejected(x.guard.authorize(x.pack(x.grant, c), 'controller'));
  }
});
test('blocks every non-allowlisted command even under an authority-signed grant', () => {
  for (const capability of ['get_project_state', 'get_timeline_summary', 'list_tracks', 'list_clips', 'get_selected_clip', 'get_playhead', 'get_export_status', 'get_errors', 'seek', 'select_clip', 'delete_clip', 'submit_export_review_proposal', 'get_preview_frame', 'unknown']) {
    const x = setup(); const g = { ...x.grant, scopes: [capability] }; const c = { ...x.command, capability };
    rejected(x.guard.authorize(x.pack(g, c), 'controller'));
  }
});
test('scope, command envelope, and payload are exact and fail closed', () => {
  const changes = [ { capability: 'list_clips', payload: { x: 1 } }, { payload: [] }, { payload: null }, { kind: 'result' }, { protocolVersion: 2 }, { expectedRevision: 1 }, { projectId: 'other' }, { deviceId: 'other' }, { capability: 'get_errors' }, { extra: true }, { nonce: 'bad' } ];
  for (const change of changes) { const x = setup(); rejected(x.guard.authorize(x.pack(x.grant, { ...x.command, ...change }), 'controller')); }
});
test('rejects expired, future, too long, and out-of-grant timestamps', () => {
  for (const change of [ { expiresAtEpochMs: Date.now() - 1 }, { issuedAtEpochMs: Date.now() + 10000 }, { expiresAtEpochMs: Date.now() + 45000 }, { issuedAtEpochMs: 0 }, { expiresAtEpochMs: Number.MAX_SAFE_INTEGER } ]) {
    const x = setup(); rejected(x.guard.authorize(x.pack(x.grant, { ...x.command, ...change }), 'controller'));
  }
  for (const change of [ { expiresAt: Date.now() - 1 }, { issuedAt: Date.now() + 10000 }, { expiresAt: Date.now() + 600000 } ]) {
    const x = setup(); rejected(x.guard.authorize(x.pack({ ...x.grant, ...change }), 'controller'));
  }
});
test('verifies authority, proof of possession, and transport identity', () => {
  { const x = setup(); rejected(x.guard.authorize(x.pack(x.grant, x.command, x.other.privateKey), 'controller')); }
  { const x = setup(); rejected(x.guard.authorize(x.pack(x.grant, x.command, x.authority.privateKey, x.other.privateKey), 'controller')); }
  { const x = setup(); rejected(x.guard.authorize(x.pack(), 'other')); }
  { const x = setup(); const obj = JSON.parse(x.pack()); obj.command.capability = 'list_clips'; rejected(x.guard.authorize(JSON.stringify(obj), 'controller')); }
});
test('revocation and binding failure reject, including promises and exceptions', () => {
  { const x = setup(); x.revoke(); rejected(x.guard.authorize(x.pack(), 'controller')); }
  for (const verifyLiveBinding of [() => Promise.resolve(true), () => { throw new Error(); }, () => 1]) {
    const x = setup({ verifyLiveBinding }); rejected(x.guard.authorize(x.pack(), 'controller'));
  }
});
test('a fresh guard epoch rejects grants made for another instance', () => {
  const x = setup(); const guard2 = new ExperimentalReadOnlyGuard({ authorityPublicKey: x.authority.publicKey, verifyLiveBinding: () => true });
  rejected(guard2.authorize(x.pack(), 'controller'));
});
test('capacity exhaustion fails closed without evicting valid replay entries', () => {
  const x = setup({ replayCapacity: 2 }); const raw = x.pack(); accepted(x.guard.authorize(raw, 'controller'));
  const c = { ...x.command, messageId: enc(randomBytes(32)), nonce: enc(randomBytes(32)) };
  rejected(x.guard.authorize(x.pack(x.grant, c), 'controller')); rejected(x.guard.authorize(raw, 'controller'));
});
test('malformed, extra fields, bad signatures and oversized input fail closed', () => {
  const x = setup();
  for (const raw of ['', 'null', '[]', '{', ' '.repeat(17000), JSON.stringify({ ...JSON.parse(x.pack()), extra: true }), JSON.stringify({ ...JSON.parse(x.pack()), grantSignature: 'bad' })]) rejected(x.guard.authorize(raw, 'controller'));
});
test('grant reissue does not reset replay', () => {
  const x = setup(); accepted(x.guard.authorize(x.pack(), 'controller'));
  rejected(x.guard.authorize(x.pack({ ...x.grant, expiresAt: x.grant.expiresAt + 1 }), 'controller'));
});
test('clock rollback fails closed permanently for this guard epoch', () => {
  const x = setup(); const realNow = Date.now;
  try {
    Date.now = () => realNow() - 60000;
    rejected(x.guard.authorize(x.pack(), 'controller'));
  } finally { Date.now = realNow; }
  rejected(x.guard.authorize(x.pack(), 'controller'));
});
