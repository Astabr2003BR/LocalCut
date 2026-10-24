import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";
import { WebSocketDeviceChannel, type DeviceSocket } from "../src/deviceChannel.ts";
import type { BridgeCommandEnvelope } from "../src/protocol.ts";

function command(messageId: string): BridgeCommandEnvelope {
  return {
    protocolVersion: 1,
    messageId,
    kind: "command",
    deviceId: "lc_device",
    projectId: "project-1",
    expectedRevision: null,
    issuedAtEpochMs: 1_000,
    expiresAtEpochMs: 31_000,
    nonce: `nonce-${messageId}`,
    capability: "get_project_state",
    payload: {},
  };
}

class FakeSocket extends EventEmitter implements DeviceSocket {
  readyState = 1;
  sent: string[] = [];
  closed = 0;

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closed += 1;
    this.readyState = 3;
    this.emit("close", 1000, Buffer.from("closed"));
  }
}

test("channel sends command and correlates result by messageId", async () => {
  const socket = new FakeSocket();
  const channel = new WebSocketDeviceChannel(socket);
  const pending = channel.send(command("msg-1"));

  assert.equal(JSON.parse(socket.sent[0]!).messageId, "msg-1");
  socket.emit("message", Buffer.from(JSON.stringify({
    messageId: "msg-1",
    success: true,
    code: null,
    projectRevision: 8,
    result: { ok: true },
    retryable: false,
  })));

  const result = await pending;
  assert.equal(result.success, true);
  assert.equal(result.projectRevision, 8);
});

test("unknown or malformed device messages do not resolve another command", async () => {
  const socket = new FakeSocket();
  const channel = new WebSocketDeviceChannel(socket);
  const pending = channel.send(command("msg-1"));
  let settled = false;
  void pending.then(
    () => { settled = true; },
    () => { settled = true; },
  );

  socket.emit("message", Buffer.from("not-json"));
  socket.emit("message", Buffer.from(JSON.stringify({ messageId: "other", success: true })));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(settled, false);

  channel.close();
  await assert.rejects(pending, /DEVICE_CHANNEL_CLOSED/);
  assert.equal(settled, true);
});

test("socket close rejects all pending commands", async () => {
  const socket = new FakeSocket();
  const channel = new WebSocketDeviceChannel(socket);
  const first = channel.send(command("a"));
  const second = channel.send(command("b"));

  socket.emit("close", 1006, Buffer.from("network"));

  await assert.rejects(first, /DEVICE_CHANNEL_CLOSED/);
  await assert.rejects(second, /DEVICE_CHANNEL_CLOSED/);
});
