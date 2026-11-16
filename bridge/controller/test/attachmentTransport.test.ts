import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";
import { createAttachmentStager } from "../src/attachmentTransport.ts";
import { createTransientMediaStore } from "../src/transientMediaHttp.ts";

const TOKEN = "C".repeat(43);

// Original 1x1 RGB PNG and one silent 16-bit PCM WAV sample.
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPgEpEDAABoAD1UCKP3AAAAAElFTkSuQmCC", "base64");
const WAV = Buffer.alloc(46);
WAV.write("RIFF"); WAV.writeUInt32LE(38, 4); WAV.write("WAVEfmt ", 8);
WAV.writeUInt32LE(16, 16); WAV.writeUInt16LE(1, 20); WAV.writeUInt16LE(1, 22);
WAV.writeUInt32LE(8000, 24); WAV.writeUInt32LE(16000, 28);
WAV.writeUInt16LE(2, 32); WAV.writeUInt16LE(16, 34); WAV.write("data", 36); WAV.writeUInt32LE(2, 40);


function file(overrides: Partial<{
  download_url: string;
  file_id: string;
  mime_type: string;
  file_name: string;
  expected_sha256: string;
}> = {}) {
  return {
    download_url: "https://files.example/private/item",
    file_id: "file_123",
    mime_type: "image/png",
    file_name: "frame.png",
    ...overrides,
  };
}

test("attachment stager fetches a ChatGPT file and stages it without exposing source URL", async () => {
  const store = createTransientMediaStore({ nowEpochMs: () => 1_000 });
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: store,
    tokenGenerator: () => TOKEN,
    fetchImpl: async () => new Response(PNG, {
      status: 200,
      headers: { "content-type": "image/png" },
    }),
  });

  const result = await stager(file());

  assert.equal(result.fileId, "file_123");
  assert.equal(result.fileName, "frame.png");
  assert.equal(result.mimeType, "image/png");
  assert.equal(result.sizeBytes, PNG.length);
  assert.equal(result.sha256, createHash("sha256").update(PNG).digest("hex"));
  assert.equal(result.relayUrl, `https://localcut-bridge.example/v1/mobile-media/${TOKEN}`);
  assert.equal(
    result.handoffUrl,
    `https://localcut-bridge.example/open?action=import_url&url=${encodeURIComponent(result.relayUrl)}&returnTo=chatgpt`,
  );
  assert.equal(result.expiresAtEpochMs, 301_000);
  assert.equal(JSON.stringify(result).includes("files.example"), false);

  const entry = store.take(TOKEN);
  assert.ok(entry);
  assert.equal(entry?.contentType, "image/png");
  assert.deepEqual(entry?.bytes, PNG);
  assert.equal(result.sha256, createHash("sha256").update(entry.bytes).digest("hex"));
  assert.equal(store.take(TOKEN), null, "relay entry must be one-time");
});

test("attachment stager rejects non-HTTPS URL before any network access", async () => {
  let fetchCalls = 0;
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: createTransientMediaStore(),
    fetchImpl: async () => {
      fetchCalls += 1;
      return new Response();
    },
  });

  await assert.rejects(
    stager(file({ download_url: "http://files.example/item" })),
    /ATTACHMENT_HTTPS_REQUIRED/,
  );
  assert.equal(fetchCalls, 0);
});

test("attachment stager rejects upstream failures, unsupported MIME, empty bodies, and oversized streams", async () => {
  const base = {
    publicBaseUrl: "https://localcut-bridge.example",
    tokenGenerator: () => TOKEN,
  } as const;

  await assert.rejects(
    createAttachmentStager({
      ...base,
      transientMediaStore: createTransientMediaStore(),
      fetchImpl: async () => new Response("no", { status: 503 }),
    })(file()),
    /ATTACHMENT_DOWNLOAD_FAILED/,
  );

  await assert.rejects(
    createAttachmentStager({
      ...base,
      transientMediaStore: createTransientMediaStore(),
      fetchImpl: async () => new Response("text", {
        status: 200,
        headers: { "content-type": "text/plain" },
      }),
    })(file({ mime_type: "text/plain", file_name: "notes.txt" })),
    /ATTACHMENT_MEDIA_TYPE_UNSUPPORTED/,
  );

  await assert.rejects(
    createAttachmentStager({
      ...base,
      transientMediaStore: createTransientMediaStore(),
      fetchImpl: async () => new Response(Buffer.alloc(0), {
        status: 200,
        headers: { "content-type": "image/png" },
      }),
    })(file()),
    /ATTACHMENT_BODY_EMPTY/,
  );

  await assert.rejects(
    createAttachmentStager({
      ...base,
      transientMediaStore: createTransientMediaStore({ maxBytes: 2 }),
      fetchImpl: async () => new Response(Buffer.from("123"), {
        status: 200,
        headers: { "content-type": "image/png" },
      }),
    })(file()),
    /ATTACHMENT_TOO_LARGE/,
  );
});

test("attachment stager falls back to declared media MIME only for generic upstream content type", async () => {
  const store = createTransientMediaStore();
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: store,
    tokenGenerator: () => TOKEN,
    fetchImpl: async () => new Response(WAV, {
      status: 200,
      headers: { "content-type": "application/octet-stream" },
    }),
  });

  const result = await stager(file({ mime_type: "audio/wav", file_name: "voice.wav" }));
  assert.equal(result.mimeType, "audio/wav");
});


test("attachment download never follows redirects and has a deadline", async () => {
  let calls = 0;
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: createTransientMediaStore(),
    fetchImpl: async (_url, init) => {
      calls += 1;
      assert.equal(init?.redirect, "error");
      assert.ok(init?.signal instanceof AbortSignal);
      assert.equal(init.signal.aborted, false);
      throw new TypeError("redirect rejected");
    },
  });
  await assert.rejects(stager(file()), /ATTACHMENT_DOWNLOAD_FAILED/);
  assert.equal(calls, 1);
});


test("attachment stager cancels rejected response bodies without masking validation errors", async () => {
  const scenarios: Array<{ status: number; headers: Record<string, string>; error: string }> = [
    { status: 503, headers: { "content-type": "image/png" }, error: "ATTACHMENT_DOWNLOAD_FAILED" },
    { status: 200, headers: { "content-type": "text/plain" }, error: "ATTACHMENT_MEDIA_TYPE_UNSUPPORTED" },
    { status: 200, headers: { "content-type": "image/png", "content-length": "3" }, error: "ATTACHMENT_TOO_LARGE" },
  ];
  for (const scenario of scenarios) {
    let cancelled = 0;
    const body = new ReadableStream<Uint8Array>({
      cancel() { cancelled += 1; },
    });
    const stager = createAttachmentStager({
      publicBaseUrl: "https://localcut-bridge.example",
      transientMediaStore: createTransientMediaStore({ maxBytes: 2 }),
      fetchImpl: async () => new Response(body, { status: scenario.status, headers: scenario.headers }),
    });
    await assert.rejects(stager(file()), new RegExp(scenario.error));
    assert.equal(cancelled, 1);
    assert.equal(body.locked, false);
  }
});

test("attachment stager cancels oversized streaming bodies and releases the reader", async () => {
  let cancelled = 0;
  const body = new ReadableStream<Uint8Array>({
    start(controller) { controller.enqueue(new Uint8Array([1, 2, 3])); },
    cancel() { cancelled += 1; },
  });
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: createTransientMediaStore({ maxBytes: 2 }),
    fetchImpl: async () => new Response(body, { headers: { "content-type": "image/png" } }),
  });
  await assert.rejects(stager(file()), /ATTACHMENT_TOO_LARGE/);
  assert.equal(cancelled, 1);
  assert.equal(body.locked, false);
});


test("attachment source rejects literal IP aliases, local names, ports and fragments before fetch", async () => {
  let calls = 0;
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: createTransientMediaStore(),
    fetchImpl: async () => { calls++; return new Response("ok"); },
  });
  for (const download_url of [
    "https://127.0.0.1/a", "https://2130706433/a", "https://0x7f000001/a",
    "https://[::1]/a", "https://[::ffff:127.0.0.1]/a",
    "https://169.254.169.254/a", "https://localhost./a",
    "https://service.internal/a", "https://printer.local/a",
    "https://router.home.arpa/a", "https://files.example:8443/a",
    "https://files.example/a#fragment",
  ]) {
    await assert.rejects(stager(file({ download_url })), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  }
  assert.equal(calls, 0);
});


test("configured attachment origins match exactly and empty configuration denies all", async () => {
  let calls = 0;
  const base = {
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: createTransientMediaStore(),
    fetchImpl: async () => {
      calls++;
      return new Response(PNG, { headers: { "content-type": "image/png" } });
    },
  };
  const stager = createAttachmentStager({ ...base, allowedSourceOrigins: ["https://files.example"] });
  for (const download_url of ["https://files.example.evil.example/a", "https://sub.files.example/a", "https://files.example./a"]) {
    await assert.rejects(stager(file({ download_url })), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  }
  await assert.rejects(createAttachmentStager({ ...base, allowedSourceOrigins: [] })(file()), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  assert.equal(calls, 0);
  await stager(file());
  assert.equal(calls, 1);
  assert.throws(() => createAttachmentStager({ ...base, allowedSourceOrigins: ["https://files.example/path"] }), /ATTACHMENT_SOURCE_ORIGIN_INVALID/);
  assert.throws(() => createAttachmentStager({ ...base, allowedSourceOrigins: ["https://files.example/?x=1"] }), /ATTACHMENT_SOURCE_ORIGIN_INVALID/);
});


test("expected digest validates before network and rejects mismatches before staging", async () => {
  const store = createTransientMediaStore();
  let calls = 0;
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example",
    transientMediaStore: store,
    tokenGenerator: () => TOKEN,
    fetchImpl: async () => {
      calls++;
      return new Response(PNG, { headers: { "content-type": "image/png" } });
    },
  });
  for (const expected_sha256 of ["", "a".repeat(63), "G".repeat(64), "A".repeat(64)]) {
    await assert.rejects(stager(file({ expected_sha256 })), /ATTACHMENT_HASH_INVALID/);
  }
  assert.equal(calls, 0);
  await assert.rejects(stager(file({ expected_sha256: "0".repeat(64) })), /ATTACHMENT_HASH_MISMATCH/);
  assert.equal(store.take(TOKEN), null);
  const expected_sha256 = createHash("sha256").update(PNG).digest("hex");
  assert.equal((await stager(file({ expected_sha256 }))).sha256, expected_sha256);
  assert.ok(store.take(TOKEN));
  assert.equal(calls, 2);
});


test("attachment rejects partial and encoded upstream responses before relay staging", async () => {
  const scenarios: Array<{ status: number; headers: Record<string, string>; error: string }> = [
    { status: 206, headers: { "content-range": "bytes 0-2/10" }, error: "ATTACHMENT_DOWNLOAD_FAILED" },
    { status: 200, headers: { "content-range": "bytes 0-2/10" }, error: "ATTACHMENT_DOWNLOAD_FAILED" },
    { status: 200, headers: { "content-encoding": "gzip" }, error: "ATTACHMENT_CONTENT_ENCODING_UNSUPPORTED" },
  ];
  for (const {status, headers, error} of scenarios) {
    const store = createTransientMediaStore();
    let cancelled = 0;
    const body = new ReadableStream<Uint8Array>({ cancel() { cancelled++; } });
    const stager = createAttachmentStager({
      publicBaseUrl: "https://localcut-bridge.example", transientMediaStore: store,
      tokenGenerator: () => TOKEN,
      fetchImpl: async () => new Response(body, { status, headers: { "content-type": "image/png", ...headers } }),
    });
    await assert.rejects(stager(file()), new RegExp(error));
    assert.equal(cancelled, 1);
    assert.equal(store.take(TOKEN), null);
  }
});


test("attachment validates declared length against actual bytes before staging", async () => {
  for (const [length, error] of [
    ["2", "ATTACHMENT_LENGTH_MISMATCH"], ["4", "ATTACHMENT_LENGTH_MISMATCH"],
    ["-1", "ATTACHMENT_LENGTH_INVALID"], ["3, 3", "ATTACHMENT_LENGTH_INVALID"],
    ["1e2", "ATTACHMENT_LENGTH_INVALID"], ["9007199254740992", "ATTACHMENT_TOO_LARGE"],
  ]) {
    const store = createTransientMediaStore();
    const stager = createAttachmentStager({
      publicBaseUrl: "https://localcut-bridge.example", transientMediaStore: store,
      tokenGenerator: () => TOKEN,
      fetchImpl: async () => new Response("abc", { headers: { "content-type": "image/png", "content-length": length } }),
    });
    await assert.rejects(stager(file()), new RegExp(error));
    assert.equal(store.take(TOKEN), null);
  }
  const store = createTransientMediaStore();
  const stager = createAttachmentStager({
    publicBaseUrl: "https://localcut-bridge.example", transientMediaStore: store,
    tokenGenerator: () => TOKEN,
    fetchImpl: async () => new Response(PNG, { headers: { "content-type": "image/png", "content-length": String(PNG.length) } }),
  });
  assert.equal((await stager(file())).sizeBytes, PNG.length);
  assert.deepEqual(store.take(TOKEN)?.bytes, PNG);
});

test("attachment rejects spoofed and truncated media before relay staging", async () => {
  for (const [mime, bytes, error] of [
    ["image/png", Buffer.from("<html>fake</html>"), "MISMATCH"],
    ["image/png", PNG.subarray(0, 20), "MISMATCH"],
    ["audio/wav", PNG, "MISMATCH"],
    ["audio/wav", WAV.subarray(0, 44), "MISMATCH"],
    ["video/mp4", Buffer.from("not a video"), "MISMATCH"],
    ["image/svg+xml", Buffer.from("<svg/>"), "UNSUPPORTED"],
  ] as const) {
    const store = createTransientMediaStore();
    const stager = createAttachmentStager({
      publicBaseUrl: "https://localcut-bridge.example",
      transientMediaStore: store, tokenGenerator: () => TOKEN,
      fetchImpl: async () => new Response(Buffer.from(bytes), {headers: {"content-type": mime}}),
    });
    await assert.rejects(stager(file({ mime_type: mime })), new RegExp("ATTACHMENT_MEDIA_SIGNATURE_" + error));
    assert.equal(store.take(TOKEN), null);
  }
});


// A small real STORED ZIP with CRC32; no external archive library needed.
function packageZip(entries: Array<[string, Buffer]>): Buffer {
  const local: Buffer[] = [], central: Buffer[] = [];
  let offset = 0;
  for (const [name, data] of entries) {
    const n = Buffer.from(name);
    let crc = 0xffffffff;
    for (const b of data) {
      crc ^= b;
      for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
    crc = (crc ^ 0xffffffff) >>> 0;
    const l = Buffer.alloc(30);
    l.writeUInt32LE(0x04034b50); l.writeUInt16LE(20, 4); l.writeUInt32LE(crc, 14);
    l.writeUInt32LE(data.length, 18); l.writeUInt32LE(data.length, 22); l.writeUInt16LE(n.length, 26);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6);
    c.writeUInt32LE(crc, 16); c.writeUInt32LE(data.length, 20); c.writeUInt32LE(data.length, 24);
    c.writeUInt16LE(n.length, 28); c.writeUInt32LE(offset, 42);
    local.push(l, n, data); central.push(c, n); offset += l.length + n.length + data.length;
  }
  const directory = Buffer.concat(central), e = Buffer.alloc(22);
  e.writeUInt32LE(0x06054b50); e.writeUInt16LE(entries.length, 8); e.writeUInt16LE(entries.length, 10);
  e.writeUInt32LE(directory.length, 12); e.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, e]);
}

test("explicit package stage creates matching Android handoff and rechecks authorization", async () => {
  const mime = "application/vnd.localcut.package";
  const manifest = Buffer.from(JSON.stringify({ version: 1, recipe: JSON.stringify({version:1,format:"9:16",scenes:[{image:1,durationMs:1000}]}),
    images: [{id:"frame", mime:"image/png", bytes:PNG.length, sha256:createHash("sha256").update(PNG).digest("hex")}] }));
  const zip = packageZip([["manifest.json", manifest], ["media/frame", PNG]]);
  const store = createTransientMediaStore();
  const stage = createAttachmentStager({publicBaseUrl:"https://localcut-bridge-v1.onrender.com", transientMediaStore:store,
    tokenGenerator:()=>TOKEN, fetchImpl:async()=>new Response(new Uint8Array(zip),{headers:{"content-type":"application/zip"}})});
  const input = file({mime_type:mime,file_name:"intro.localcut",expected_sha256:createHash("sha256").update(zip).digest("hex")});
  let checks = 0;
  const result = await stage(input,{authorize(){checks++;}});
  assert.equal(checks,2);
  assert.equal(result.mimeType,mime);
  const link = new URL(result.handoffUrl);
  assert.equal(link.searchParams.get("action"),"open_package");
  assert.equal(link.searchParams.get("url"),result.relayUrl);
  assert.equal(link.searchParams.get("sha256"),result.sha256);
  assert.equal(link.searchParams.get("bytes"),String(zip.length));
  assert.deepEqual(store.take(TOKEN)?.bytes,zip);
  assert.equal(checks,3, "authorization must also be checked at redemption");
  assert.equal(store.take(TOKEN),null);
  await assert.rejects(stage(input), /ATTACHMENT_MEDIA_TYPE_UNSUPPORTED/);
  let expiredChecks = 0;
  await assert.rejects(stage(input,{authorize(){if (++expiredChecks > 1) throw Error("OAUTH_TOKEN_INVALID");}}), /OAUTH_TOKEN_INVALID/);
  assert.equal(store.take(TOKEN),null);
  await assert.rejects(stage({...input,expected_sha256:"0".repeat(64)},{authorize(){}}),/ATTACHMENT_HASH_MISMATCH/);
  assert.equal(store.take(TOKEN),null);
  let authorized = true;
  await stage(input, {authorize(){if (!authorized) throw Error("OAUTH_TOKEN_INVALID");}});
  authorized = false;
  assert.equal(store.take(TOKEN),null, "revocation after staging must deny redemption");
  authorized = true;
  assert.equal(store.take(TOKEN),null, "a denied token must never resurrect");
});

test("package preflight rejects traversal duplicate manifest malformed archive and wrong MIME", async () => {
  const mime = "application/vnd.localcut.package";
  const input = file({mime_type:mime,file_name:"intro.localcut"});
  for (const zip of [
    Buffer.from("not a ZIP"),
    packageZip([["manifest.json",Buffer.from("{}")],["../frame",PNG]]),
    packageZip([["manifest.json",Buffer.from("{}")],["manifest.json",PNG]]),
    packageZip([["media/a",PNG],["media/b",PNG]]),
  ]) {
    const store = createTransientMediaStore();
    const stage = createAttachmentStager({publicBaseUrl:"https://localcut-bridge-v1.onrender.com", transientMediaStore:store,
      tokenGenerator:()=>TOKEN,fetchImpl:async()=>new Response(new Uint8Array(zip),{headers:{"content-type":mime}})});
    await assert.rejects(stage(input,{authorize(){}}),/PACKAGE_ZIP_INVALID/);
    assert.equal(store.take(TOKEN),null);
  }
  const stage = createAttachmentStager({publicBaseUrl:"https://localcut-bridge-v1.onrender.com", transientMediaStore:createTransientMediaStore(),
    fetchImpl:async()=>new Response(PNG,{headers:{"content-type":"image/png"}})});
  await assert.rejects(stage(input,{authorize(){}}),/PACKAGE_ATTACHMENT_TYPE_INVALID/);
  await assert.rejects(stage({...input,file_name:"intro.zip"},{authorize(){}}),/PACKAGE_ATTACHMENT_TYPE_INVALID/);
});
