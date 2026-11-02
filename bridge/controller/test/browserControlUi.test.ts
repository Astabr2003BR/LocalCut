import assert from "node:assert/strict";
import test from "node:test";
import { panelPage, unauthorizedPage } from "../src/browserControlHttp.ts";

test("unauthorized remote page starts device approval from the UI", () => {
  const html = unauthorizedPage();

  assert.match(html, /Autorizar este navegador/);
  assert.match(html, /id="authorize"/);
  assert.match(html, /\/remote\/api\/authorize\/start/);
  assert.match(html, /id="complete-authorization"/);
  assert.match(html, /\/remote\/api\/authorize\/status/);
});

test("authorized remote page exposes a generic typed command executor", () => {
  const html = panelPage("csrf-token-value");

  assert.match(html, /id="generic-capability"/);
  assert.match(html, /id="generic-payload"/);
  assert.match(html, /id="generic-execute"/);
  assert.match(html, /list_clips/);
  assert.match(html, /context\.projectRevision/);
  assert.doesNotMatch(html, /eval\(/);
});
