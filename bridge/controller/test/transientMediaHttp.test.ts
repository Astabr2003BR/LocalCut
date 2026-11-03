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
  const handler = createTransientMediaHttpHandler({
    nowEpochMs: () => now,
    ttlMs: 5 * 60 * 1_000,
    maxBytes: 20 * 1024 * 1024,
  });
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
    baseUrl: `http://127.0.0.1:${address.port}`,
    setNow(value: number) { now = value; },
    close: () => new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())),
  };
}

function token(): string {
  return randomBytes(32).toString("base64url");
}

test("temporary media upload is private-by-token, no-store, and consumed after one download", async t => {
  const fx = await fixture();
  t.after(() => fx.close());
  const id = token();
  const bytes = Buffer.from("png-bytes");

  const uploaded = await fetch(`${fx.baseUrl}/v1/mobile-media/${id}`, {
    method: "POST",
    headers: { "content-type": "image/png" },
    body: bytes,
  });
  assert.equal(uploaded.status, 201);
  assert.equal(uploaded.headers.get("cache-control"), "no-store");
  const uploadJson = await uploaded.json() as { downloadPath: string; expiresAtEpochMs: number };
  assert.equal(uploadJson.downloadPath, `/v1/mobile-media/${id}`);
  assert.equal(uploadJson.expiresAtEpochMs, 301_000);

  const downloaded = await fetch(`${fx.baseUrl}${uploadJson.downloadPath}`);
  assert.equal(downloaded.status, 200);
  assert.equal(downloaded.headers.get("content-type"), "image/png");
  assert.equal(downloaded.headers.get("cache-control"), "no-store");
  assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()), bytes);

  const consumed = await fetch(`${fx.baseUrl}${uploadJson.downloadPath}`);
  assert.equal(consumed.status, 404);
});

test("direct staging shares the same one-time store used by the HTTP download route", async t => {
  let now = 1_000;
  const store = createTransientMediaStore({ nowEpochMs: () => now });
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
  t.after(() => new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("missing test server address");

  const id = token();
  const staged = store.stage(id, "video/mp4", Buffer.from("clip"));
  assert.equal(staged.downloadPath, `/v1/mobile-media/${id}`);
  assert.equal(staged.expiresAtEpochMs, 301_000);

  const first = await fetch(`http://127.0.0.1:${address.port}${staged.downloadPath}`);
  assert.equal(first.status, 200);
  assert.equal(first.headers.get("content-type"), "video/mp4");
  assert.deepEqual(Buffer.from(await first.arrayBuffer()), Buffer.from("clip"));

  const second = await fetch(`http://127.0.0.1:${address.port}${staged.downloadPath}`);
  assert.equal(second.status, 404);

  now = 999_999;
});

test("temporary media upload rejects malformed tokens, unsupported MIME, empty bodies, and oversize bodies", async t => {
  const fx = await fixture();
  t.after(() => fx.close());

  const badToken = await fetch(`${fx.baseUrl}/v1/mobile-media/weak-token`, {
    method: "POST",
    headers: { "content-type": "image/png" },
    body: Buffer.from("x"),
  });
  assert.equal(badToken.status, 400);

  const badMime = await fetch(`${fx.baseUrl}/v1/mobile-media/${token()}`, {
    method: "POST",
    headers: { "content-type": "text/plain" },
    body: Buffer.from("x"),
  });
  assert.equal(badMime.status, 415);

  const empty = await fetch(`${fx.baseUrl}/v1/mobile-media/${token()}`, {
    method: "POST",
    headers: { "content-type": "image/png" },
    body: Buffer.alloc(0),
  });
  assert.equal(empty.status, 400);

  const tinyHandler = createTransientMediaHttpHandler({ maxBytes: 2 });
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", "http://localcut.invalid");
    if (await tinyHandler(req, res, url)) return;
    res.writeHead(404).end();
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  t.after(() => new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("missing test server address");
  const tooLarge = await fetch(`http://127.0.0.1:${address.port}/v1/mobile-media/${token()}`, {
    method: "POST",
    headers: { "content-type": "image/png" },
    body: Buffer.from("123"),
  });
  assert.equal(tooLarge.status, 413);
});

test("temporary media expires and cannot be downloaded after TTL", async t => {
  const fx = await fixture();
  t.after(() => fx.close());
  const id = token();
  const uploaded = await fetch(`${fx.baseUrl}/v1/mobile-media/${id}`, {
    method: "POST",
    headers: { "content-type": "audio/wav" },
    body: Buffer.from("audio"),
  });
  assert.equal(uploaded.status, 201);

  fx.setNow(301_001);
  const expired = await fetch(`${fx.baseUrl}/v1/mobile-media/${id}`);
  assert.equal(expired.status, 404);
});
