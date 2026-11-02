# LocalCut Always-Connected Remote Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the LocalCut Android agent persistently reachable and add a private browser control path that can execute existing Bridge commands without per-command taps on the APK.

**Architecture:** Preserve the existing Bridge protocol and CommandRouter. Add a browser-session authorization service to the controller, an APK deep-link approval flow, and a small private web control UI/API. The browser session is authorized once by the paired device and then uses signed HttpOnly cookies plus CSRF protection for writes.

**Tech Stack:** Kotlin/Android foreground service, TypeScript/Node controller, existing WebSocket Bridge, existing CommandRouter, SQLite-backed controller state where needed, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-25-always-connected-remote-control.md`

## Global Constraints
- Preserve PR #13 as OPEN + DRAFT + NOT MERGED.
- Preserve existing pairing/session/recovery, Android signing, overrides, timeline/edit/export behavior.
- No secrets in URLs, HTML, logs, plugin files, or client-visible JavaScript.
- No second editing protocol; all commands route through CommandRouter.
- Background keepalive must not eagerly initialize Media3/editor runtime.

## Review Focus
- Expired/replayed browser authorization requests must fail closed.
- Browser cookie tampering must fail closed.
- Write requests without valid CSRF must fail closed.
- Offline device must return DEVICE_OFFLINE without hanging.
- Revision conflict must be surfaced, never silently overwritten.

---

### Task 1: Browser control session policy

**Files:**
- Create: `bridge/controller/src/browserControl.ts`
- Create: `bridge/controller/test/browserControl.test.ts`

**Interfaces:**
- Produces `BrowserControlSessionService` for one-time authorization requests, signed browser sessions, CSRF values, device scope, expiry and replay protection.

- [ ] Write failing unit tests for request expiry, one-time approval/exchange, cookie tamper, device scope, CSRF and session expiry.
- [ ] Run controller tests and verify RED because `browserControl.ts` does not exist.
- [ ] Implement the minimal service.
- [ ] Run the focused tests and full controller suite GREEN.
- [ ] Commit.

### Task 2: Controller web routes and CommandRouter adapter

**Files:**
- Modify: `bridge/controller/src/server.ts`
- Create: `bridge/controller/test/browserControlServer.test.ts`

**Interfaces:**
- Consumes `BrowserControlSessionService`.
- Produces `/remote`, `/remote/api/contexts`, `/remote/api/command`, `/remote/authorize`, `/remote/complete`, and device approval endpoint using existing authenticated device sessions.

- [ ] Write failing HTTP tests for unauthorized page/API, approval, cookie issuance, context discovery, reversible command routing, CSRF failure, offline device and revision conflict.
- [ ] Run tests RED.
- [ ] Implement routes using `discoverActiveContexts`, BridgeCommandEnvelope and `router.execute`.
- [ ] Run focused and full controller tests GREEN.
- [ ] Commit.

### Task 3: Android browser-control approval deep link

**Files:**
- Modify: `overrides/app/src/main/AndroidManifest.xml`
- Modify: `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt`
- Modify: existing Bridge HTTP client/control files as needed.
- Create focused unit tests under `overrides/app/src/test/java/.../mobile/` and/or `bridge/agent/`.

**Interfaces:**
- Consumes `localcut://remote/approve?requestId=...`.
- Uses the existing paired device session to approve the browser-control request and opens the validated completion URL.

- [ ] Write failing parser/policy tests.
- [ ] Verify RED.
- [ ] Implement approval using the existing credential renewal/recovery path.
- [ ] Run Android unit tests GREEN.
- [ ] Commit.

### Task 4: Always-connected lifecycle hardening

**Files:**
- Modify: `BridgeConnectionService.kt`, `LocalCutApplication.kt`, and narrowly related policy/tests only.

**Interfaces:**
- Keeps foreground connectivity when `alwaysConnected` is enabled while avoiding eager editor runtime initialization.

- [ ] Add tests for boot/app cold-start reconnect and lightweight foreground behavior.
- [ ] Verify RED where behavior is missing.
- [ ] Implement minimal reconnect/lifecycle changes.
- [ ] Run Android unit tests and smoke suite GREEN.
- [ ] Commit.

### Task 5: Deploy and end-to-end verification

- [ ] Verify Bridge Controller CI, Android Debug APK and emulator smoke on the same HEAD.
- [ ] Deploy controller to Render.
- [ ] Install the new APK over the current build, preserving data.
- [ ] Authorize one browser session through the APK.
- [ ] From the browser panel, discover the active context, call `get_project_state`, issue a reversible `seek`, and verify with `get_playhead`.
- [ ] Undo/restore any QA mutation if necessary.
- [ ] Record checkpoint; do not merge PR #13.
