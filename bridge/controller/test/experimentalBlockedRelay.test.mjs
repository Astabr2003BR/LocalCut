import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { ExperimentalBlockedRelay } from '../src/experimental/experimentalBlockedRelay.mjs';
import { ExperimentalReadOnlyGuard, experimentalGrantSigningBytes, experimentalCommandSigningBytes } from '../src/experimental/experimentalReadOnlyGuard.mjs';

const key = () => generateKeyPairSync('ed25519').publicKey;
const disabled = Object.freeze({ status: 'experimental_disabled' });
const blocked = Object.freeze({ status: 'blocked_no_trusted_binding' });

test('disabled by default and requires literal opt-in', () => {
  for (const optIn of [undefined, false, 1, 'true', null]) {
    const relay = new ExperimentalBlockedRelay({ optIn, authorityPublicKey: key() });
    assert.deepEqual(relay.evaluate('input', 'controller'), disabled);
  }
});

test('opt-in without a trusted key stays blocked', () => {
  const relay = new ExperimentalBlockedRelay({ optIn: true });
  assert.deepEqual(relay.evaluate('input', 'controller'), blocked);
});

test('opt-in with valid authority evaluates but does not dispatch', () => {
  const original = ExperimentalReadOnlyGuard.prototype.authorize;
  let calls = 0, sends = 0;
  try {
    // Force an unexpected success to prove the wrapper cannot forward it.
    ExperimentalReadOnlyGuard.prototype.authorize = function () { calls++; return { capability: 'delete_clip' }; };
    const relay = new ExperimentalBlockedRelay({ optIn: true, authorityPublicKey: key(), dispatch: () => { sends++; } });
    const result = relay.evaluate('input', 'controller');
    assert.deepEqual(result, blocked);
    assert.ok(Object.isFrozen(result));
    assert.equal(calls, 1);
    assert.equal(sends, 0);
  } finally { ExperimentalReadOnlyGuard.prototype.authorize = original; }
});

test('guard failures never escape or leak values', () => {
  const original = ExperimentalReadOnlyGuard.prototype.authorize;
  try {
    ExperimentalReadOnlyGuard.prototype.authorize = function () { throw new Error('sensitive'); };
    const relay = new ExperimentalBlockedRelay({ optIn: true, authorityPublicKey: key() });
    assert.deepEqual(relay.evaluate('input', 'controller'), blocked);
  } finally { ExperimentalReadOnlyGuard.prototype.authorize = original; }
});

test('real guard denies a correctly signed command despite an injected fake binding', () => {
  const authority = generateKeyPairSync('ed25519');
  const controller = generateKeyPairSync('ed25519');
  const encode = b => b.toString('base64url');
  const original = ExperimentalReadOnlyGuard.prototype.authorize;
  let denied = false;
  try {
    ExperimentalReadOnlyGuard.prototype.authorize = function () {
      const now = Date.now();
      const grant = { version: 1, audience: this.audience, controllerId: 'controller', deviceId: 'device', projectId: 'project', controllerKey: encode(controller.publicKey.export({ format: 'der', type: 'spki' })), scopes: ['get_device_status'], issuedAt: now - 1000, expiresAt: now + 60000 };
      const command = { protocolVersion: 1, messageId: 'message', kind: 'command', deviceId: 'device', projectId: 'project', expectedRevision: null, issuedAtEpochMs: now - 500, expiresAtEpochMs: now + 10000, nonce: encode(randomBytes(32)), capability: 'get_device_status', payload: {} };
      const grantSignature = encode(sign(null, experimentalGrantSigningBytes(grant), authority.privateKey));
      const commandSignature = encode(sign(null, experimentalCommandSigningBytes(grant, grantSignature, command), controller.privateKey));
      denied = original.call(this, JSON.stringify({ grant, grantSignature, command, commandSignature }), 'controller') === null;
      return null;
    };
    const relay = new ExperimentalBlockedRelay({ optIn: true, authorityPublicKey: authority.publicKey, verifyLiveBinding: () => true });
    assert.deepEqual(relay.evaluate('input', 'controller'), blocked);
    assert.ok(denied);
  } finally { ExperimentalReadOnlyGuard.prototype.authorize = original; }
});

test('real guard remains blocked for malformed input', () => {
  const relay = new ExperimentalBlockedRelay({ optIn: true, authorityPublicKey: key() });
  for (const raw of [null, '', '{}', ' '.repeat(17000)]) assert.deepEqual(relay.evaluate(raw, 'controller'), blocked);
});
