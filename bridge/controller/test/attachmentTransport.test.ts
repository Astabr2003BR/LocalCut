import assert from "node:assert/strict";
import test from "node:test";
import { createAttachmentStager } from "../src/attachmentTransport.ts";
import { createTransientMediaStore } from "../src/transientMediaHttp.ts";

const TOKEN = "C".repeat(43);

function file(overrides: Partial<{
  download_url: string;
  file_id: string;
  mime_type: string;
  file_name: string;
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
    fetchImpl: async () => new Response(Buffer.from("image-bytes"), {
      status: 200,
      headers: { "content-type": "image/png" },
    }),
  });

  const result = await stager(file());

  assert.equal(result.fileId, "file_123");
  assert.equal(result.fileName, "frame.png");
  assert.equal(result.mimeType, "image/png");
  assert.equal(result.sizeBytes, 11);
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
  assert.deepEqual(entry?.bytes, Buffer.from("image-bytes"));
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
    fetchImpl: async () => new Response(Buffer.from("audio"), {
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
