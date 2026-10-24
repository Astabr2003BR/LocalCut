# LocalCut Bridge V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-shaped LocalCut Bridge so a private ChatGPT controller can inspect and safely control an installed LocalCut Android editor in foreground or user-enabled background mode, using the existing ProjectSession/HistoryManager path and producing a new testable APK.

**Architecture:** Keep the Android editor authoritative. ChatGPT calls a private MCP controller hosted together with the relay; the relay routes typed commands to a paired Android agent over one outbound authenticated WebSocket. Android validates protocol version, TTL, nonce, idempotency, capability and expected project revision before dispatching through ProjectSession. V1 controls already-authorized/imported media; automatic transfer of newly generated assets is a separate Asset Inbox milestone.

**Tech Stack:** Kotlin/Android/Jetpack Compose/Media3; OkHttp WebSocket client; Android Keystore ECDSA P-256; Node.js 24 + TypeScript controller/relay; WebSocket server; official MCP SDK; SQLite-backed relay metadata; Railway deployment; existing GitHub Actions Android CI.

**Spec:** `docs/superpowers/specs/2026-10-19-localcut-bridge-v1-protocol.md`, `docs/superpowers/specs/2026-10-19-localcut-studio-bridge-design.md`, and `docs/superpowers/specs/2026-10-21-localcut-bridge-background-connectivity-addendum.md`

## Global Constraints

- Preserve all current editor behavior: import, preview, timeline, trim, split, move, delete, undo/redo, autosave, keyframes, text, transitions and MP4 export.
- Preserve `overrides/` as repository source of truth and continue building from `localcut-source-full-build9.zip` until a deliberate source-baseline migration is made.
- Android Bridge opens no inbound listening port; outbound TLS/WSS only.
- No arbitrary shell, filesystem tool, generic Android intent, other-app control or source-media deletion.
- Device private key remains non-exportable in Android Keystore under alias `localcut.bridge.device.v1`.
- Write commands require protocol version 1, unexpired TTL, nonce/replay validation, message-id idempotency and matching `expectedRevision`.
- Bridge is opt-in; foreground editing remains usable with Bridge disabled/offline.
- Mode B is default: user starts a visible `specialUse` foreground service from LocalCut and sees a persistent notification.
- Mode C is optional `Sempre conectado`: best-effort restore after process/app update/reboot, with tap-to-reconnect fallback if Android/OEM blocks background FGS startup.
- Foreground service requests no camera, microphone or location permission.
- Long-running export returns a `jobId`; one active export per project in V1.
- New network/runtime dependencies must be pinned and lockfiles committed.
- Railway relay is an implementation choice for V1, not a project-model dependency; protocol and controller interfaces remain host-portable.

## Review Focus

1. A stale command arriving after reconnect must be rejected without mutation; cover TTL + nonce + message-id tests in Task 2.
2. Local user edits racing a remote write must produce `REVISION_CONFLICT`, never last-write-wins; cover revision tests in Tasks 1 and 3.
3. Process/OEM kill during background mode must not corrupt project/autosave state; cover service restart and session-state tests in Task 5.
4. Repeating an export command after a network retry must not create duplicate MP4 jobs; cover idempotent long-running job routing in Tasks 3 and 7.
5. A remote command that needs a SAF/media permission must return `LOCAL_INTERACTION_REQUIRED` rather than opening hidden UI; cover capability/import tests in Task 3.

---

### Task 1: Persistent monotonic project revision in ProjectSession

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/session/ProjectRevisionClock.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/session/ProjectSession.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/MainActivity.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/session/ProjectRevisionClockTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/session/ProjectSessionRevisionTest.kt`

**Interfaces:**
- Produces: `interface ProjectRevisionClock { fun current(projectId: String): Long; fun next(projectId: String): Long }`
- Produces: `SharedPreferencesProjectRevisionClock(context: Context)` for Android persistence.
- Produces: `ProjectSessionState.revision: Long`.
- Invariant: every successful project mutation, including undo/redo and local UI edits, increments revision exactly once; selection/seek does not.

- [ ] **Step 1: Write failing revision-clock tests** asserting per-project persistence semantics, monotonic increment and no decrement.
- [ ] **Step 2: Run the focused unit tests** and verify they fail because the revision API does not exist.
- [ ] **Step 3: Implement `ProjectRevisionClock` and an in-memory test implementation; wire revision into `ProjectSessionState`.**
- [ ] **Step 4: Write failing `ProjectSessionRevisionTest`** covering successful command +1, rejected command +0, undo +1 and redo +1.
- [ ] **Step 5: Update `ProjectSession.commitEditedState()` / history-change path** so revision advances only after a successful domain mutation; initialize from `revisionClock.current(project.id)`.
- [ ] **Step 6: Add `SharedPreferencesProjectRevisionClock` in Android wiring** and pass it from `MainActivity` when creating `ProjectSession`.
- [ ] **Step 7: Run all Android unit tests** with `gradle --no-daemon -p work testDebugUnitTest` and require PASS.
- [ ] **Step 8: Commit** as `feat: add persistent project revisions`.

### Task 2: Bridge protocol, validation, replay and idempotency core

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/protocol/BridgeProtocol.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/protocol/BridgeCodec.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/security/ReplayGuard.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/protocol/BridgeProtocolTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/security/ReplayGuardTest.kt`

**Interfaces:**
- Produces: `BridgeCommandEnvelope(protocolVersion, messageId, kind, deviceId, projectId, expectedRevision, issuedAtEpochMs, expiresAtEpochMs, nonce, capability, payloadJson)`.
- Produces: `BridgeResultEnvelope(messageId, success, code, projectRevision, resultJson, retryable)`.
- Produces: `BridgeCapability` allowlist.
- Produces: `ReplayGuard.validateAndReserve(messageId: String, nonce: String, expiresAtEpochMs: Long, nowEpochMs: Long): ReplayDecision`.

- [ ] **Step 1: Write failing protocol tests** for protocolVersion=1, unknown capability rejection, malformed required fields and stable error codes.
- [ ] **Step 2: Write failing replay tests** for expired command, repeated nonce, repeated messageId and bounded cache eviction.
- [ ] **Step 3: Implement typed protocol models and JSON codec** using the JSON facilities already present in the Android source; do not introduce direct project JSON mutation.
- [ ] **Step 4: Implement `ReplayGuard`** with bounded in-memory completed/reserved entries and original-result reuse hook for message-id idempotency.
- [ ] **Step 5: Run focused tests and full Android unit suite**; require PASS.
- [ ] **Step 6: Commit** as `feat: add bridge protocol validation`.

### Task 3: Android Editor Command Bus adapter

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeCommandExecutor.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeProjectReader.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeJobRegistry.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/session/ProjectSession.kt` only if a minimal read/dispatch hook is required.
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgeCommandExecutorTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgeJobRegistryTest.kt`

**Interfaces:**
- Consumes: `ProjectSessionState.revision`, `BridgeCommandEnvelope`, existing `EditorAction` commands.
- Produces: `BridgeCommandExecutor.execute(command: BridgeCommandEnvelope): BridgeResultEnvelope`.
- Required V1 read capabilities: `get_device_status`, `get_project_state`, `get_timeline_summary`, `list_tracks`, `list_clips`, `get_selected_clip`, `get_playhead`, `get_export_status`, `get_errors`.
- Required V1 write capabilities: `seek`, `select_clip`, `split_clip`, `trim_clip`, `move_clip`, `delete_clip`, `duplicate_clip`, `set_transform`, `set_volume`, `set_keyframes`, `add_text`, `add_transition`, `undo`, `redo`, `export_project`, `cancel_export`.
- `import_media` in V1 accepts only a previously authorized LocalCut media reference; otherwise returns `LOCAL_INTERACTION_REQUIRED`.

- [ ] **Step 1: Write failing executor tests** for read commands, successful reversible write, revision conflict, missing clip, invalid argument and local-interaction-required import.
- [ ] **Step 2: Implement bounded project readers** that never return raw filesystem paths or media bytes.
- [ ] **Step 3: Implement write mapping through `EditorAction`/existing domain commands**, never by replacing `Project` JSON.
- [ ] **Step 4: Add `BridgeJobRegistry` tests** for one active export, retry of same messageId returning same job, completion/failure/cancel state.
- [ ] **Step 5: Implement export job adapter** around existing `Media3ExportController` with a LocalCut-owned MediaStore destination for unattended export.
- [ ] **Step 6: Run bridge tests plus legacy regression suite** and require PASS.
- [ ] **Step 7: Commit** as `feat: expose typed LocalCut command bus`.

### Task 4: Device identity, pairing credentials and secure storage

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/security/DeviceIdentityStore.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/security/AndroidKeystoreDeviceIdentityStore.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/security/BridgeCredentialStore.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/security/DeviceIdentityContractTest.kt`
- Android test: `overrides/app/src/androidTest/java/com/localcut/editor/bridge/security/AndroidKeystoreDeviceIdentitySmokeTest.kt`

**Interfaces:**
- Produces: `DeviceIdentityStore.getOrCreatePublicIdentity(): DevicePublicIdentity`.
- Produces: `DeviceIdentityStore.signChallenge(challenge: ByteArray): ByteArray` using ECDSA P-256 alias `localcut.bridge.device.v1`.
- Produces: encrypted short-lived/refresh credential storage backed by Android Keystore; no plaintext long-lived token in logs/preferences.

- [ ] **Step 1: Write contract tests** proving stable public identity, challenge-sign API and secret-redaction behavior.
- [ ] **Step 2: Implement Android Keystore P-256 identity** and AES-GCM credential protection.
- [ ] **Step 3: Add Android instrumentation smoke test** that generates key, signs challenge and verifies signature with exported public key.
- [ ] **Step 4: Run unit + API 35 instrumentation smoke** and require PASS.
- [ ] **Step 5: Commit** as `feat: add secure bridge device identity`.

### Task 5: Background Bridge connection service and B+C lifecycle

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/transport/BridgeSocketClient.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/transport/OkHttpBridgeSocketClient.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeConnectionService.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeBootReceiver.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeNotificationFactory.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeSettingsStore.kt`
- Modify/Create override: `overrides/app/src/main/AndroidManifest.xml`
- Modify/Create override: `overrides/app/build.gradle.kts`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/transport/BridgeReconnectPolicyTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgeSettingsStoreTest.kt`
- Android test: `overrides/app/src/androidTest/java/com/localcut/editor/bridge/agent/BridgeConnectionServiceSmokeTest.kt`

**Interfaces:**
- Produces: `BridgeSocketClient.connect(session: BridgeSession, listener: BridgeSocketListener)` and `disconnect()`.
- Produces: reconnect policy with exponential backoff + jitter capped at 60 seconds.
- Produces: `BridgeConnectionService` foreground `specialUse` session with actions `ACTION_CONNECT`, `ACTION_DISCONNECT`, `ACTION_RECONNECT`.
- Produces: Mode C preference `alwaysConnected: Boolean` and boot/package-replaced recovery.

- [ ] **Step 1: Extract canonical Build 9 `app/build.gradle.kts` and `AndroidManifest.xml` into the worktree before creating complete overrides**, so dependency/manifest edits are reviewable and not CI-only patches.
- [ ] **Step 2: Pin OkHttp and its test dependency in Gradle**, preserving current Android/Media3/Compose configuration.
- [ ] **Step 3: Write failing reconnect/settings tests** for 1s→2s→4s bounded backoff, max 60s, manual disconnect suppressing reconnect, and `alwaysConnected=false` suppressing boot recovery.
- [ ] **Step 4: Implement WebSocket client and foreground service** with no network/serialization on the main thread.
- [ ] **Step 5: Add manifest permissions/service/receiver declarations** including `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` where applicable, `RECEIVE_BOOT_COMPLETED`, `android:exported="false"` service, and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation.
- [ ] **Step 6: Implement boot fallback**: restore service when legally allowed; otherwise post `Toque para reconectar ao ChatGPT` notification and execute no remote command first.
- [ ] **Step 7: Add instrumentation smoke** for user-started service, visible notification, disconnect action and state persistence. Run API 31/34/35 compatibility coverage in CI where available.
- [ ] **Step 8: Run full Android unit/build/regression suite** and require PASS.
- [ ] **Step 9: Commit** as `feat: keep LocalCut bridge connected in background`.

### Task 6: Bridge UI and status surface

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/BridgePanel.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeUiState.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/MainActivity.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorScreen.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgeUiStateTest.kt`

**Interfaces:**
- Produces UI actions: `Conectar ao ChatGPT`, `Desconectar`, `Sempre conectado`, `Revogar pareamento`.
- Shows: Offline / Pareando / Conectando / Online / Editando / Exportando / Erro.
- Pairing surface shows short code and QR payload only; never private key or raw refresh credential.

- [ ] **Step 1: Write failing state-reducer tests** for connection state transitions and safe redaction.
- [ ] **Step 2: Implement `BridgeUiState` reducer and service binding/status flow.**
- [ ] **Step 3: Add mobile-first Bridge panel** without restructuring the working editor screen.
- [ ] **Step 4: Verify normal editor startup with Bridge disabled** has no network side effect and all existing UI regression tests/builds pass.
- [ ] **Step 5: Commit** as `feat: add LocalCut bridge controls`.

### Task 7: Hosted relay + MCP controller service

**Files:**
- Create: `bridge/controller/package.json`
- Create: `bridge/controller/package-lock.json`
- Create: `bridge/controller/tsconfig.json`
- Create: `bridge/controller/src/server.ts`
- Create: `bridge/controller/src/protocol.ts`
- Create: `bridge/controller/src/deviceRegistry.ts`
- Create: `bridge/controller/src/pairing.ts`
- Create: `bridge/controller/src/commandRouter.ts`
- Create: `bridge/controller/src/mcp.ts`
- Create: `bridge/controller/src/store.ts`
- Create: `bridge/controller/test/pairing.test.ts`
- Create: `bridge/controller/test/router.test.ts`
- Create: `bridge/controller/test/mcp.test.ts`
- Create: `bridge/controller/railway.json`

**Interfaces:**
- Device WSS: `/v1/device/connect`.
- Pairing HTTPS: `/v1/pair/start`, `/v1/pair/claim`, `/v1/pair/status`.
- Health: `GET /health`.
- MCP endpoint: authenticated streamable HTTP endpoint exposing only LocalCut tools.
- Persistent metadata: device public identity, pairing/session state, replay/idempotency metadata and bounded audit metadata; never project/media contents.

- [ ] **Step 1: Initialize Node 24 TypeScript service and pin dependencies** (`ws`, official MCP SDK, schema validator, SQLite driver) with committed lockfile.
- [ ] **Step 2: Write failing pairing tests** for 10-minute code TTL, one-time claim, challenge signature verification and revoked device rejection.
- [ ] **Step 3: Implement SQLite-backed device/pairing/session metadata** with schema initialization and bounded audit rows.
- [ ] **Step 4: Write failing routing tests** for offline device, command timeout, response correlation, same-message idempotency and one active device session.
- [ ] **Step 5: Implement WebSocket device registry/router** and stable error mapping.
- [ ] **Step 6: Write failing MCP tests** proving only allowlisted LocalCut tools are exposed and write schemas require `expectedRevision` where applicable.
- [ ] **Step 7: Implement MCP tool handlers** by routing each call to the paired Android device; no tool directly edits project storage.
- [ ] **Step 8: Run `npm test` and a local WebSocket integration test**; require PASS.
- [ ] **Step 9: Deploy one-replica service to Railway**, attach persistent volume for SQLite, configure TLS public domain/health check and store service secrets only in Railway variables.
- [ ] **Step 10: Run deployed `/health`, WSS connect and pairing smoke tests**; require PASS before plugin creation.
- [ ] **Step 11: Commit** as `feat: add LocalCut bridge relay and controller`.

### Task 8: End-to-end Android ↔ relay pairing and command smoke

**Files:**
- Create: `bridge/controller/test/e2e-device-fixture.ts`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/bridge/BridgeEndToEndSmokeTest.kt`
- Modify: `.github/workflows/android-multitrack-smoke.yml` or add a dedicated bridge smoke workflow if secrets/network isolation require it.

**Interfaces:**
- Validates: pair → online → get_project_state → seek → reversible edit → stale-revision rejection → undo → export job idempotency → disconnect.

- [ ] **Step 1: Add a deterministic fake-device fixture** for relay CI without secrets.
- [ ] **Step 2: Add Android instrumentation test against an injectable local/fake relay** for socket/protocol behavior.
- [ ] **Step 3: Run hosted manual smoke against the deployed relay** using a development device identity; do not store production credentials in GitHub.
- [ ] **Step 4: Verify a repeated write messageId is executed once and stale `expectedRevision` is rejected.**
- [ ] **Step 5: Commit** as `test: cover LocalCut bridge end to end`.

### Task 9: Private ChatGPT LocalCut Controller plugin

**Files:**
- Create plugin package/connection metadata through Plugin Creator after the live MCP endpoint is verified.
- Add plugin skill describing the safe LocalCut workflow and tool-selection rules.

**Interfaces:**
- Tools are discovered from the live MCP server, not invented in the plugin package.
- Plugin never stores Android Keystore material.
- Plugin can inspect state, issue allowlisted edits, request preview/status and start/cancel export.

- [ ] **Step 1: Inspect the live MCP endpoint and verify tool schemas/authentication.**
- [ ] **Step 2: Create the private `LocalCut Controller` plugin bound to that MCP server.**
- [ ] **Step 3: Add controller instructions** requiring state read before write, revision preconditions, bounded retries and preview verification after multi-step edits.
- [ ] **Step 4: Connect plugin and verify tools are available in a normal ChatGPT conversation.**
- [ ] **Step 5: Run a real conversational smoke**: `Veja meu projeto atual` → state read; then one reversible edit → preview/state verification; then undo.
- [ ] **Step 6: Record plugin identity/connection instructions in a non-secret project checkpoint.**

### Task 10: Release candidate APK and regression checkpoint

**Files:**
- Create: `docs/checkpoints/2026-10-21-localcut-bridge-v1.md`
- Modify CI assertions only as required to prove Bridge source and tests are actually compiled/run.

**Interfaces:**
- Produces a signed debug/update-compatible APK artifact from current CI lineage.
- Does not merge to `main` without explicit merge authorization.

- [ ] **Step 1: Run Android unit tests, `assembleDebug`, overlap-safe checks and all existing legacy regression harnesses.**
- [ ] **Step 2: Run Android 35 media export smoke including existing multitrack/text/fade/keyframe tests.**
- [ ] **Step 3: Run Bridge protocol/service/e2e smoke gates and relay `npm test`.**
- [ ] **Step 4: Audit APK manifest** for only intended network/FGS/boot/notification permissions and verify no embedded relay/admin secret or private key material.
- [ ] **Step 5: Generate checkpoint** with exact commit SHA, workflow runs, artifact name, SHA-256 and known limitations.
- [ ] **Step 6: Download and integrity-check the candidate APK** and provide it for installation/testing.
- [ ] **Step 7: Open/update PR for review; leave unmerged until explicit authorization.**

## Deferred follow-on plans

These are intentionally separate architectural subprojects and are not hidden inside Bridge V1:

1. **Asset Inbox:** secure ChatGPT/controller → LocalCut transfer of generated images/audio/video with hash verification, quotas, expiry and import into app-private storage. This removes the V1 requirement that media already be authorized/imported locally.
2. **Anime Mode:** scene/layer recipes, parallax, camera 2.5D, parenting/rig helpers, mouth/eye animation presets, motion/easing presets and automated EditScript planning.
3. **CapCut + Alight Motion capability parity roadmap:** speed curves/reverse/freeze, masks/chroma/blend, effect registry/color tools, easing graph, shapes/vector, groups/parenting/nulls/camera/Z, motion blur, captions/audio tools, tracking/stabilization and optional AI modules. Each category gets its own TDD plan and regression gate.
4. **Production release security:** private Play signing lineage, Play foreground-service declaration review, rate limits/abuse controls and production observability.

## Final acceptance gate for Bridge V1

Bridge V1 is complete only when all of these are proven:

- LocalCut remains fully usable offline with Bridge disabled.
- User can pair from the APK and see a persistent B-mode foreground notification.
- `Sempre conectado` persists and follows C-mode best-effort recovery behavior.
- Private ChatGPT plugin can read the live project and execute at least one reversible edit through ProjectSession.
- Revision conflict, TTL, replay and message-id idempotency tests pass.
- Remote import without prior authorization returns `LOCAL_INTERACTION_REQUIRED`.
- Export can be started remotely, returns one job id, reports progress/final state and is not duplicated on retry.
- Notification/local disconnect revokes active controller access.
- Existing LocalCut media/timeline/keyframe/text/transition/export regression suite remains green.
- Candidate APK is generated and integrity-checked; no merge happens without explicit user authorization.
