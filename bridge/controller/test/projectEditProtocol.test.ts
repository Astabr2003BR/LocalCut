import test from 'node:test';
import assert from 'node:assert/strict';
import { bridgeCapabilities, projectWriteCapabilities, revisionRequiredCapabilities } from '../src/protocol.ts';

test('project_edit is an allowed revision-protected project write capability', () => {
  assert.ok((bridgeCapabilities as readonly string[]).includes('project_edit'));
  assert.ok((projectWriteCapabilities as Set<string>).has('project_edit'));
  assert.ok((revisionRequiredCapabilities as Set<string>).has('project_edit'));
});
