import assert from "node:assert/strict";
import test from "node:test";
import { isPublicAttachmentIPv4, resolveAttachmentIPv4 } from "../src/attachmentPinnedFetch.ts";

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
