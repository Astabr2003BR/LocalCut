import assert from "node:assert/strict";
import test from "node:test";
import { isPublicAttachmentIPv4, resolveAttachmentIPv4, pinnedAttachmentRequestOptions } from "../src/attachmentPinnedFetch.ts";

test("attachment DNS policy rejects private and special-use addresses", () => {
  for (const ip of ["0.0.0.0", "10.0.0.1", "127.0.0.1", "100.64.0.1", "169.254.169.254",
    "172.16.0.1", "192.168.1.1", "192.0.0.1", "192.0.2.1", "198.18.0.1",
    "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1", "::ffff:127.0.0.1", "bad"]) {
    assert.equal(isPublicAttachmentIPv4(ip), false, ip);
  }
  assert.equal(isPublicAttachmentIPv4("8.8.8.8"), true);
  assert.equal(isPublicAttachmentIPv4("1.1.1.1"), true);
});

test("attachment DNS resolution rejects mixed and empty answers", async () => {
  await assert.rejects(resolveAttachmentIPv4("files.example", async () => []), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  await assert.rejects(resolveAttachmentIPv4("files.example", async () => ["8.8.8.8", "127.0.0.1"]), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  assert.equal(await resolveAttachmentIPv4("files.example", async () => ["8.8.8.8", "1.1.1.1"]), "8.8.8.8");
});

test("pinned HTTPS request preserves host and path without another DNS lookup", () => {
  const signal = new AbortController().signal;
  const options = pinnedAttachmentRequestOptions(new URL("https://files.example/a%20b?sig=abc"), "8.8.8.8", signal);
  assert.equal(options.hostname, "8.8.8.8");
  assert.equal(options.servername, "files.example");
  assert.equal(options.path, "/a%20b?sig=abc");
  assert.equal(options.port, 443);
  assert.equal(options.agent, false);
  assert.equal(options.rejectUnauthorized, true);
  assert.equal(options.signal, signal);
  assert.deepEqual(options.headers, { host: "files.example", accept: "image/*,audio/*,video/*,application/octet-stream", "accept-encoding": "identity" });
  assert.throws(() => pinnedAttachmentRequestOptions(new URL("https://files.example"), "127.0.0.1", signal), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
  assert.throws(() => pinnedAttachmentRequestOptions(new URL("http://files.example"), "8.8.8.8", signal), /ATTACHMENT_SOURCE_NOT_ALLOWED/);
});
