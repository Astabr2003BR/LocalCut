# LocalCut Mobile Media Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android users send photo/video/audio into LocalCut from the system Share sheet, and let LocalCut Mobile import a supported public HTTPS media URL after local confirmation.

**Architecture:** Android Share intents and remote HTTPS downloads both normalize to local `Uri` values and then use one reusable media-ingest coordinator that preserves the existing LocalCut placement/import semantics. Remote media is downloaded only to app-private cache, with SSRF protection, redirect revalidation, streaming byte limits, media validation, deterministic cleanup, and the existing LocalCut confirmation/return-to-ChatGPT flow.

**Tech Stack:** Kotlin, Android intents/content URIs, AndroidX FileProvider, existing `AndroidAssetImporter` + `ImportedClipFactory`, OkHttp 5.5.0, MockWebServer 5.5.0, `BitmapFactory`/`MediaMetadataRetriever`, JUnit, Android instrumentation, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-21-localcut-mobile-media-import-design.md`

## Global Constraints

- Work only on `feature/localcut-bridge-v1`; PR #13 stays draft and must not be merged without explicit authorization.
- Preserve manual media import, preview, timeline, undo/redo, export, pairing, Always Connected, mobile handoff, and return-to-ChatGPT behavior.
- Preserve `overrides/`; do not recreate or replace it.
- Shared media accepts user-initiated Android `content://` URIs only; reject external `file://`.
- Remote import is HTTPS-only and blocks loopback, link-local, RFC1918/private, IPv6 ULA, multicast/unspecified, CGNAT `100.64.0.0/10`, and redirects to blocked destinations.
- Follow at most 3 redirects and revalidate DNS/address policy at every hop.
- Streaming limits: image 50 MiB, audio 200 MiB, video 1 GiB; never rely only on `Content-Length`.
- `import_url` always requires the existing local confirmation dialog.
- Temporary remote files must be deleted on success, validation/import failure, cancellation, or exception.
- No direct binary upload from ChatGPT to Relay in this phase.

## Review Focus

1. `MainActivity` already open: `onNewIntent()` must import a new share exactly once.
2. Invalid shared media: missing stream, unsupported MIME, malformed URI, or `file://` must fail without corrupting the project; valid siblings continue where possible.
3. SSRF/DNS rebinding/redirect: every resolved address and redirect destination must remain public before any body bytes are consumed.
4. MIME spoofing/oversize: declared MIME cannot make an invalid file valid; stream limits and final detected-kind limits both apply.
5. Cleanup: partial download, validation failure, importer failure, or cancelled Activity must leave no temp media behind.

---

### Task 1: Android Share target + share request parser

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/SharedMediaIntentParser.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/SharedMediaIntentParserTest.kt`
- Modify: `overrides/app/src/main/AndroidManifest.xml`

**Interfaces:**
- `data class SharedMediaRequest(val mimeType: String, val uriStrings: List<String>)`
- `object SharedMediaIntentParser { fun parse(action: String?, mimeType: String?, uriStrings: List<String?>): Result<SharedMediaRequest> }`
- Accept `ACTION_SEND` and `ACTION_SEND_MULTIPLE`; MIME prefixes `image/`, `video/`, `audio/`; URI scheme `content` only.

- [ ] **Step 1: Write failing tests** for single image, multiple-order preservation, missing stream, unsupported MIME, malformed URI, and `file://` rejection.
- [ ] **Step 2: Verify RED**

```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest --tests 'com.localcut.editor.mobile.SharedMediaIntentParserTest'
```

Expected: FAIL because parser does not exist.

- [ ] **Step 3: Implement parser + manifest filters** for `ACTION_SEND` and `ACTION_SEND_MULTIPLE` with `image/*`, `video/*`, `audio/*`, preserving launcher intent filter.
- [ ] **Step 4: Re-run focused test; expect PASS.**
- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/AndroidManifest.xml overrides/app/src/main/java/com/localcut/editor/mobile/SharedMediaIntentParser.kt overrides/app/src/test/java/com/localcut/editor/mobile/SharedMediaIntentParserTest.kt
git commit -m "feat: register LocalCut Android media share target"
```

### Task 2: Reusable media ingest coordinator + MainActivity share handling

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/MediaIngestCoordinator.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/LocalCutProcessRuntime.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/MainActivity.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/SharedMediaImportSmokeTest.kt`
- Modify: `.github/workflows/android-multitrack-smoke.yml`

**Interfaces:**
- `data class MediaIngestResult(val importedCount: Int, val failedCount: Int, val errors: List<String>)`
- `class MediaIngestCoordinator { suspend fun importUris(uris: List<Uri>): MediaIngestResult }`
- `LocalCutProcessRuntime.mediaIngestCoordinator` is process-scoped and reuses the existing `AndroidAssetImporter`, `ImportedClipFactory`, current tracks, and `EditorViewModel`.

- [ ] **Step 1: Write failing instrumentation smoke**: cold-start `ACTION_SEND image/*` adds one visual clip; warm `onNewIntent()` adds exactly one more clip, not a duplicate.
- [ ] **Step 2: Add smoke class to `.github/workflows/android-multitrack-smoke.yml`; verify RED before production implementation.**
- [ ] **Step 3: Move existing manual URI-import loop into `MediaIngestCoordinator` without changing placement semantics**: audio->audio track; image/video->visual track; append at `max(startUs + durationUs)`; continue after per-item failure.
- [ ] **Step 4: Make manual picker and share-intent paths call the same coordinator.** `onCreate()` consumes initial share once; `onNewIntent()` consumes only the new intent.
- [ ] **Step 5: Run full unit/build regression**

```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MediaIngestCoordinator.kt overrides/app/src/main/java/com/localcut/editor/LocalCutProcessRuntime.kt overrides/app/src/main/java/com/localcut/editor/MainActivity.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/SharedMediaImportSmokeTest.kt .github/workflows/android-multitrack-smoke.yml
git commit -m "feat: import Android shared media into LocalCut timeline"
```

### Task 3: `import_url` handoff contract + URL/address policy

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoff.kt`
- Modify: `overrides/app/src/test/java/com/localcut/editor/mobile/MobileHandoffParserTest.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaUrlPolicy.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaUrlPolicyTest.kt`

**Interfaces:**
- Add `remoteMediaUrl: String? = null` to `MobileHandoffCommand`.
- `import_url` sets `remoteMediaUrl`, has no `BridgeCapability`, and description `Baixar e importar mídia remota no LocalCut`.
- `object RemoteMediaUrlPolicy { fun parseAndValidate(rawUrl: String): Result<URI>; fun requirePublicAddresses(addresses: List<InetAddress>): Result<Unit> }`.
- URL max length 4096; scheme exactly `https`; host required; user-info forbidden.

- [ ] **Step 1: Write failing tests** for valid `import_url`, HTTP rejection, loopback, RFC1918, IPv6 ULA, CGNAT, and public address acceptance.
- [ ] **Step 2: Verify RED**

```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest --tests 'com.localcut.editor.mobile.MobileHandoffParserTest' --tests 'com.localcut.editor.mobile.RemoteMediaUrlPolicyTest'
```

- [ ] **Step 3: Implement parser branch and pure URL/address policy.**
- [ ] **Step 4: Re-run focused tests; expect PASS.**
- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoff.kt overrides/app/src/test/java/com/localcut/editor/mobile/MobileHandoffParserTest.kt overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaUrlPolicy.kt overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaUrlPolicyTest.kt
git commit -m "feat: define secure remote media handoff contract"
```

### Task 4: Guarded remote downloader + media validation + FileProvider

**Files:**
- Modify: `overrides/app/build.gradle.kts`
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaDownloader.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaValidator.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaDownloaderTest.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaValidatorSmokeTest.kt`
- Modify: `overrides/app/src/main/AndroidManifest.xml`
- Create: `overrides/app/src/main/res/xml/localcut_remote_media_paths.xml`

**Interfaces:**
- Add `testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")`; do not alter production OkHttp `5.5.0`.
- `enum class RemoteMediaKind { IMAGE, AUDIO, VIDEO }`
- `data class DownloadedRemoteMedia(val file: File, val declaredContentType: String?, val byteCount: Long)`
- `class RemoteMediaDownloader { suspend fun download(rawUrl: String): Result<DownloadedRemoteMedia> }`
- `object RemoteMediaValidator { fun validate(file: File, declaredContentType: String?): Result<RemoteMediaKind>; fun limitBytes(kind: RemoteMediaKind): Long }`
- FileProvider authority `${applicationId}.remote-media`, exposing only `cache/remote-import/`.

- [ ] **Step 1: Add MockWebServer test dependency.**
- [ ] **Step 2: Write failing downloader tests** for direct 200, 1-3 public redirects, 4th redirect rejection, private-address redirect rejection, streamed oversize abort, DNS private-address rejection, and partial-file cleanup.
- [ ] **Step 3: Verify RED** with the focused `RemoteMediaDownloaderTest`.
- [ ] **Step 4: Implement downloader** with OkHttp automatic redirects disabled, manually revalidated redirect hops, public-only DNS, random app-private filenames, provisional stream cap and deterministic failure cleanup.
- [ ] **Step 5: Implement validator**: image via `BitmapFactory` bounds; audio/video via `MediaMetadataRetriever`; declared MIME cannot upgrade an unrecognized file.
- [ ] **Step 6: Add instrumentation fixtures**: valid image/video/audio accepted; text renamed `.mp4` rejected; FileProvider resolves only `remote-import` cache.
- [ ] **Step 7: Run unit/build regression and commit**

```bash
git add overrides/app/build.gradle.kts overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaDownloader.kt overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaValidator.kt overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaDownloaderTest.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaValidatorSmokeTest.kt overrides/app/src/main/AndroidManifest.xml overrides/app/src/main/res/xml/localcut_remote_media_paths.xml
git commit -m "feat: add guarded HTTPS media downloader"
```

### Task 5: Execute remote import through the existing handoff confirmation flow

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt`
- Modify: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/MobileHandoffIntentFilterSmokeTest.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaHandoffSmokeTest.kt`
- Modify: `.github/workflows/android-multitrack-smoke.yml`

**Interfaces:**
- `MobileHandoffActivity.execute(command)` routing order: mobile pairing -> remote media import -> existing Bridge capability.
- Remote branch performs download/validation off main thread, converts temp file to configured FileProvider URI, calls `runtime.mediaIngestCoordinator.importUris(listOf(uri))`, deletes temp file in `finally`, shows success only when `importedCount == 1`, then calls existing `returnAfterHandoff()`.

- [ ] **Step 1: Write failing remote handoff instrumentation test** covering confirmed import, one new clip, temp cleanup, and unchanged return-to-ChatGPT behavior.
- [ ] **Step 2: Add `import_url` coverage to mobile handoff smoke and add the new smoke class to `.github/workflows/android-multitrack-smoke.yml`; verify RED.**
- [ ] **Step 3: Implement remote-import branch with lifecycle-safe coroutine cancellation and `finally` cleanup.** Never dispatch `import_url` into `BridgeCommandExecutor`.
- [ ] **Step 4: Run full Android unit/build gate**

```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/MobileHandoffIntentFilterSmokeTest.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaHandoffSmokeTest.kt .github/workflows/android-multitrack-smoke.yml
git commit -m "feat: import remote media through mobile handoff"
```

### Task 6: LocalCut Mobile plugin contract + final regression + APK checkpoint

**Files:**
- Update owned `LocalCut Mobile` plugin skill/manifests after Android gates are green.
- Modify `.github/workflows/android-debug.yml` only if a new mandatory report assertion is needed; do not weaken existing gates.

**Interfaces:**
- Add plugin action: `import_url`: `action=import_url&url=<URL_ENCODED_HTTPS_URL>&returnTo=chatgpt`.
- Plugin must explicitly say direct ChatGPT-attachment binary upload is not available in this phase; use Android Share or an accessible HTTPS URL.

- [ ] **Step 1: Update LocalCut Mobile to next patch version** only after Android unit/build tests pass; preserve pairing and all current edit actions.
- [ ] **Step 2: Final CI gates must all PASS**: `Android Debug APK`, `Android Multitrack Export Smoke` (including new share/validator/handoff tests), and `Bridge Controller CI` regression.
- [ ] **Step 3: Audit final diff** against pre-feature commit; no unrelated timeline/render/export rewrites.
- [ ] **Step 4: Download exact APK artifact and record** `versionName`, `versionCode`, bytes, SHA-256, ZIP/APK integrity.
- [ ] **Step 5: Physical acceptance on user's Android**: Share one photo/video -> LocalCut; share multiple media in order; execute a safe public HTTPS `import_url`; verify Bridge remains Online and normal edit handoff still works.
- [ ] **Step 6: Record checkpoint** with final commit, plugin release/version, APK version/hash, CI run IDs, physical result, and the remaining limitation (no direct ChatGPT attachment binary transport). Keep PR #13 draft/unmerged.
