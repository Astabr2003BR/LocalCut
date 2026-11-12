import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { createServer } from "node:http";
import test from "node:test";
import {
  createTransientMediaHttpHandler,
  createTransientMediaStore,
} from "../src/transientMediaHttp.ts";

async function fixture() {
  let now = 1_000;
  const store = createTransientMediaStore({
    nowEpochMs: () => now,
    ttlMs: 5 * 60 * 1_000,
    maxBytes: 20 * 1024 * 1024,
  });
  const handler = createTransientMediaHttpHandler({ nowEpochMs: () => now }, store);
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", "http://localcut.invalid");
    if (await handler(req, res, url)) return;
    res.writeHead(404).end();
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("missing test server address");
  return {
    store,
    baseUrl: `http://127.0.0.1:${address.port}`,
    setNow(value: number) { now = value; },
    close: () => new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())),
  };
}

function token(): string {
  return randomBytes(32).toString("base64url");
}

test("public transient media HTTP upload is disabled", async t => {
  const fx = await fixture();
  t.after(() => fx.close());

  const response = await fetch(`${fx.baseUrl}/v1/mobile-media/${token()}`, {
    method: "POST",
    headers: { "content-type": "image/png" },
    body: Buffer.from("must-not-be-accepted"),
  });

  assert.equal(response.status, 405);
  assert.equal(response.headers.get("allow"), "GET");
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("direct staging shares the same one-time store used by the HTTP download route", async t => {
  const fx = await fixture();
  t.after(() => fx.close());
  const id = token();
  const bytes = Buffer.from("clip");

  const staged = fx.store.stage(id, "video/mp4", bytes);
  assert.equal(staged.downloadPath, `/v1/mobile-media/${id}`);
  assert.equal(staged.expiresAtEpochMs, 301_000);

  const first = await fetch(`${fx.baseUrl}${staged.downloadPath}`);
  assert.equal(first.status, 200);
  assert.equal(first.headers.get("content-type"), "video/mp4");
  assert.equal(first.headers.get("cache-control"), "no-store");
  assert.deepEqual(Buffer.from(await first.arrayBuffer()), bytes);

  const second = await fetch(`${fx.baseUrl}${staged.downloadPath}`);
  assert.equal(second.status, 404);
});

test("transient media store rejects malformed tokens, unsupported MIME, empty bodies, and oversize bodies", () => {
  const store = createTransientMediaStore({ maxBytes: 2 });

  assert.throws(
    () => store.stage("weak-token", "image/png", Buffer.from("x")),
    /MEDIA_TOKEN_INVALID/,
  );
  assert.throws(
    () => store.stage(token(), "text/plain", Buffer.from("x")),
    /MEDIA_TYPE_UNSUPPORTED/,
  );
  assert.throws(
    () => store.stage(token(), "image/png", Buffer.alloc(0)),
    /MEDIA_BODY_EMPTY/,
  );
  assert.throws(
    () => store.stage(token(), "image/png", Buffer.from("123")),
    /BODY_TOO_LARGE/,
  );
});

test("malformed tokens are rejected before download lookup", async t => {
  const fx = await fixture();
  t.after(() => fx.close());

  const response = await fetch(`${fx.baseUrl}/v1/mobile-media/weak-token`);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { code: "MEDIA_TOKEN_INVALID" });
});

test("temporary media expires and cannot be downloaded after TTL", async t => {
  const fx = await fixture();
  t.after(() => fx.close());
  const id = token();
  const staged = fx.store.stage(id, "audio/wav", Buffer.from("audio"));
  assert.equal(staged.expiresAtEpochMs, 301_000);

  fx.setNow(301_001);
  const expired = await fetch(`${fx.baseUrl}${staged.downloadPath}`);
  assert.equal(expired.status, 404);
});
