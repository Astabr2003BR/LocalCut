# LocalCut Mobile Media Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android users send photo/video/audio into LocalCut from the system Share sheet, and let LocalCut Mobile import a supported public HTTPS media URL after local confirmation.

**Architecture:** Keep one ingestion path from `Uri` into the existing LocalCut project/timeline semantics. Android Share intents and remote HTTPS downloads both normalize to local `Uri` values, then use the same media-ingest coordinator. Remote downloads are app-private, SSRF-guarded, size-limited, content-validated, temporary, and never execute without the existing handoff confirmation dialog.

**Tech Stack:** Kotlin, Android intents/content URIs, AndroidX Core/FileProvider, existing `AndroidAssetImporter` + `ImportedClipFactory`, OkHttp, `BitmapFactory`/`MediaMetadataRetriever`, JUnit, Android instrumentation, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-21-localcut-mobile-media-import-design.md`

## Global Constraints

- Work only on `feature/localcut-bridge-v1`; PR #13 stays draft and must not be merged without explicit authorization.
- Preserve manual image/video/audio import, preview, timeline, undo/redo, export, pairing, Always Connected, mobile handoff, and return-to-ChatGPT behavior.
- Preserve `overrides/`; do not replace or recreate it.
- Share import accepts only user-selected Android `content://` media; reject external `file://` input.
- Remote import accepts HTTPS only and must block loopback, link-local, RFC1918/private, IPv6 ULA, multicast/unspecified, and redirects to blocked destinations.
- Revalidate every redirect destination; maximum redirect count: 3.
- Stream byte limits, never trust `Content-Length` alone: image 50 MiB, audio 200 MiB, video 1 GiB; unknown/provisional responses never exceed 1 GiB before final media validation.
- `import_url` always uses the existing local confirmation dialog and returns to ChatGPT when `returnTo=chatgpt` is supplied.
- Temporary remote files live under app-private cache and are deleted on success, validation failure, import failure, cancellation, or exception.
- Do not add direct binary upload to the Relay in this phase.

## Review Focus

1. **MainActivity already running:** a new `ACTION_SEND`/`ACTION_SEND_MULTIPLE` received through `onNewIntent()` must import exactly once, not duplicate the previous share.
2. **Malicious/invalid shared URI:** missing stream, `file://`, unsupported MIME, or malformed URI must be rejected while valid siblings in a multi-share continue where possible.
3. **SSRF via DNS/redirect:** a public-looking host resolving to blocked addresses, or any redirect to a blocked host/address, must fail before bytes are downloaded.
4. **Content-Type spoofing / oversized body:** declared MIME cannot override detected media type; byte limits must be enforced during streaming and again against detected media class.
5. **Cleanup on partial failure:** network error, validation error, importer error, or user cancellation must not leave temporary remote files behind.

---

### Task 1: Shared Android media request parser and Share target declaration

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/SharedMediaIntentParser.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/SharedMediaIntentParserTest.kt`
- Modify: `overrides/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `data class SharedMediaRequest(val mimeType: String, val uriStrings: List<String>)`
- Produces: `object SharedMediaIntentParser { fun parse(action: String?, mimeType: String?, uriStrings: List<String?>): Result<SharedMediaRequest> }`
- Accepted actions: `Intent.ACTION_SEND`, `Intent.ACTION_SEND_MULTIPLE`.
- Accepted MIME prefixes: `image/`, `video/`, `audio/`.
- Accepted URI scheme at this layer: `content` only.

- [ ] **Step 1: Write failing parser tests**

```kotlin
@Test fun parsesSingleImageShare() {
    val r = SharedMediaIntentParser.parse("android.intent.action.SEND", "image/jpeg", listOf("content://media/a"))
    assertEquals(listOf("content://media/a"), r.getOrThrow().uriStrings)
}

@Test fun preservesMultipleShareOrder() { /* assert a,b,c stay a,b,c */ }
@Test fun rejectsFileScheme() { /* file:///sdcard/x.mp4 => failure */ }
@Test fun rejectsMissingStream() { /* SEND + empty streams => failure */ }
@Test fun rejectsUnsupportedMime() { /* application/pdf => failure */ }
```

- [ ] **Step 2: Assemble source and run only the new unit test; verify RED**

Run:
```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest --tests 'com.localcut.editor.mobile.SharedMediaIntentParserTest'
```
Expected: FAIL because `SharedMediaIntentParser` does not exist.

- [ ] **Step 3: Implement the parser and declare Android Share targets**

Add `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters on `MainActivity` for `image/*`, `video/*`, and `audio/*`, keeping launcher behavior intact.

- [ ] **Step 4: Re-run the focused test; verify GREEN**

Run the Step 2 command.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/AndroidManifest.xml overrides/app/src/main/java/com/localcut/editor/mobile/SharedMediaIntentParser.kt overrides/app/src/test/java/com/localcut/editor/mobile/SharedMediaIntentParserTest.kt
git commit -m "feat: register LocalCut Android media share target"
```

### Task 2: Reusable local media ingestion and MainActivity Share handling

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/MediaIngestCoordinator.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/LocalCutProcessRuntime.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/MainActivity.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/SharedMediaImportSmokeTest.kt`

**Interfaces:**
- Produces: `data class MediaIngestResult(val importedCount: Int, val failedCount: Int, val errors: List<String>)`
- Produces: `class MediaIngestCoordinator { suspend fun importUris(uris: List<Uri>): MediaIngestResult }`
- `LocalCutProcessRuntime` exposes one process-scoped `mediaIngestCoordinator` wired to the existing `AndroidAssetImporter`, `ImportedClipFactory`, current project tracks, and `EditorViewModel`.
- Manual picker import and shared-intent import both call this coordinator; no duplicate placement algorithm remains in `MainActivity`.

- [ ] **Step 1: Write a failing instrumentation smoke**

`SharedMediaImportSmokeTest` must launch `MainActivity` with an explicit `ACTION_SEND image/*` using a test-owned `content://` fixture, then assert the current project gains exactly one visual clip. Add a second test that delivers a second share to the existing activity and asserts total clips increment by one only.

- [ ] **Step 2: Add the smoke test to `.github/workflows/android-multitrack-smoke.yml` and verify it is RED before production code**

Expected: instrumentation fails because `MainActivity` does not consume shared streams.

- [ ] **Step 3: Implement `MediaIngestCoordinator` by moving the existing URI import loop out of `MainActivity` without changing placement semantics**

Preserve: audio -> audio track; image/video -> visual track; append at `max(startUs + durationUs)`; `ImportedClipFactory.create`; `EditorAction.AddImportedClip`; continue after per-item failure.

- [ ] **Step 4: Handle both cold-start and warm-activity share intents**

`MainActivity.onCreate()` consumes the initial share once; `onNewIntent()` calls `setIntent(intent)` and consumes only the new intent. Convert Android extras to URI strings, validate with `SharedMediaIntentParser`, then pass `Uri.parse(...)` to `mediaIngestCoordinator`.

- [ ] **Step 5: Run unit/build regression**

Run:
```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL; existing Bridge/manual-import compile gates remain green.

- [ ] **Step 6: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MediaIngestCoordinator.kt overrides/app/src/main/java/com/localcut/editor/LocalCutProcessRuntime.kt overrides/app/src/main/java/com/localcut/editor/MainActivity.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/SharedMediaImportSmokeTest.kt .github/workflows/android-multitrack-smoke.yml
git commit -m "feat: import Android shared media into LocalCut timeline"
```

### Task 3: `import_url` handoff contract and SSRF URL policy

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoff.kt`
- Modify: `overrides/app/src/test/java/com/localcut/editor/mobile/MobileHandoffParserTest.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaUrlPolicy.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaUrlPolicyTest.kt`

**Interfaces:**
- Extend `MobileHandoffCommand` with `remoteMediaUrl: String? = null`.
- `import_url` produces no `BridgeCapability`; it sets `remoteMediaUrl` and description `Baixar e importar mídia remota no LocalCut`.
- Produces: `object RemoteMediaUrlPolicy { fun parseAndValidate(rawUrl: String): Result<URI>; fun requirePublicAddresses(addresses: List<InetAddress>): Result<Unit> }`.
- URL max length: 4,096 characters; scheme exactly `https`; no user-info; host required; fragment allowed only if it is not sent to the server by the HTTP client.

- [ ] **Step 1: Write failing handoff and URL-policy tests**

```kotlin
@Test fun parsesHttpsImportUrlHandoff() { /* remoteMediaUrl == https://cdn.example/a.mp4 */ }
@Test fun rejectsHttpImportUrl() { /* http://... => failure */ }
@Test fun rejectsLoopbackAddress() { /* 127.0.0.1 / ::1 => failure */ }
@Test fun rejectsRfc1918AndIpv6Ula() { /* 10/8, 172.16/12, 192.168/16, fc00::/7 */ }
@Test fun acceptsPublicAddress() { /* 1.1.1.1 => success */ }
```

- [ ] **Step 2: Run focused tests; verify RED**

Run:
```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest --tests 'com.localcut.editor.mobile.MobileHandoffParserTest' --tests 'com.localcut.editor.mobile.RemoteMediaUrlPolicyTest'
```
Expected: FAIL because `import_url` and policy do not exist.

- [ ] **Step 3: Implement the parser branch and pure URL/address validation**

Blocked addresses include any-local/unspecified, loopback, link-local, site-local/RFC1918, multicast, IPv6 ULA, and IPv4 carrier-grade NAT `100.64.0.0/10`.

- [ ] **Step 4: Re-run focused tests; verify GREEN**

Run Step 2 command.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoff.kt overrides/app/src/test/java/com/localcut/editor/mobile/MobileHandoffParserTest.kt overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaUrlPolicy.kt overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaUrlPolicyTest.kt
git commit -m "feat: define secure remote media handoff contract"
```

### Task 4: Guarded remote downloader, media validation, and temporary URI exposure

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaDownloader.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaValidator.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaDownloaderTest.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaValidatorSmokeTest.kt`
- Modify: `overrides/app/src/main/AndroidManifest.xml`
- Create: `overrides/app/src/main/res/xml/localcut_remote_media_paths.xml`

**Interfaces:**
- Produces: `enum class RemoteMediaKind { IMAGE, AUDIO, VIDEO }`.
- Produces: `data class DownloadedRemoteMedia(val file: File, val declaredContentType: String?, val byteCount: Long)`.
- Produces: `class RemoteMediaDownloader { suspend fun download(rawUrl: String): Result<DownloadedRemoteMedia> }`.
- Produces: `object RemoteMediaValidator { fun validate(file: File, declaredContentType: String?): Result<RemoteMediaKind>; fun limitBytes(kind: RemoteMediaKind): Long }`.
- OkHttp automatic redirects must be disabled. Follow at most 3 redirects manually; call `RemoteMediaUrlPolicy` and resolve/revalidate every hop before requesting it.
- Use a DNS implementation that returns only addresses validated by `requirePublicAddresses`, preventing a second unvalidated resolution inside OkHttp.
- Stream to `cacheDir/remote-import/`; enforce provisional stream cap from supported declared MIME when available, otherwise 1 GiB; enforce final class-specific cap after validation.
- FileProvider authority: `${applicationId}.remote-media`; expose only `cache/remote-import/`.

- [ ] **Step 1: Write failing downloader tests with MockWebServer/fake DNS**

Test: direct 200 download, redirect to public URL, fourth redirect rejected, redirect to `127.0.0.1` rejected, stream exceeding limit aborted, and partial file deleted after failure.

- [ ] **Step 2: Run focused tests; verify RED**

Run the assembled unit-test command for `RemoteMediaDownloaderTest`.
Expected: FAIL because downloader does not exist.

- [ ] **Step 3: Implement downloader with manual redirect loop, public-only DNS, streaming limits, random app-private filename, and `finally` cleanup on failed result**

Do not derive filesystem names from remote path segments.

- [ ] **Step 4: Implement Android media validation**

Image detection: decode bounds with `BitmapFactory` without allocating the full bitmap. Audio/video detection: `MediaMetadataRetriever`; classify as video when video dimensions are present, otherwise audio when duration/audio metadata is readable. Declared MIME may reject obvious mismatches but can never upgrade an unrecognized file into valid media.

- [ ] **Step 5: Add validator instrumentation fixture tests and FileProvider declaration**

Verify a valid image/video/audio fixture is classified; a text file named `.mp4` is rejected. Verify FileProvider URI resolves only inside `remote-import` cache.

- [ ] **Step 6: Run unit/build regression and commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaDownloader.kt overrides/app/src/main/java/com/localcut/editor/mobile/RemoteMediaValidator.kt overrides/app/src/test/java/com/localcut/editor/mobile/RemoteMediaDownloaderTest.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaValidatorSmokeTest.kt overrides/app/src/main/AndroidManifest.xml overrides/app/src/main/res/xml/localcut_remote_media_paths.xml
git commit -m "feat: add guarded HTTPS media downloader"
```

### Task 5: Execute remote import through the existing confirmation flow

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt`
- Modify: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/MobileHandoffIntentFilterSmokeTest.kt`
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaHandoffSmokeTest.kt`
- Modify: `.github/workflows/android-multitrack-smoke.yml`

**Interfaces:**
- `MobileHandoffActivity.execute(command)` routes in this order: mobile pairing -> remote media import -> existing Bridge edit capability.
- Remote branch launches work on `Dispatchers.IO`, downloads and validates, converts the cache file to the configured FileProvider `content://` URI, calls `runtime.mediaIngestCoordinator.importUris(listOf(uri))`, deletes the temp file in `finally`, shows success/failure Toast, then calls existing `returnAfterHandoff(command.returnTarget)`.
- No success message until `MediaIngestResult.importedCount == 1`.

- [ ] **Step 1: Write failing remote handoff instrumentation test**

Use an emulator-local HTTPS-compatible test fixture or injected downloader seam so the test can confirm: confirmation path -> one media item imported -> temp file absent -> return target logic unchanged.

- [ ] **Step 2: Extend mobile handoff intent-filter smoke with `import_url` parsing/resolution coverage; verify RED**

Expected: new smoke fails before activity routing exists.

- [ ] **Step 3: Implement the remote branch with lifecycle-safe coroutine scope and deterministic cleanup**

If Activity is destroyed/cancelled, cancel the job and cleanup any downloaded temp file. Never fall through to `BridgeCommandExecutor` for `import_url`.

- [ ] **Step 4: Run full Android unit/build gate**

Run:
```bash
rm -rf work && mkdir work && unzip -q localcut-source-full-build9.zip -d work && cp -R overrides/app/. work/app/ && gradle --no-daemon -p work testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/MobileHandoffIntentFilterSmokeTest.kt overrides/app/src/androidTest/java/com/localcut/editor/mobile/RemoteMediaHandoffSmokeTest.kt .github/workflows/android-multitrack-smoke.yml
git commit -m "feat: import remote media through mobile handoff"
```

### Task 6: LocalCut Mobile plugin contract, full regression, APK, and checkpoint

**Files:**
- Update the owned `LocalCut Mobile` plugin skill and manifests after Android gates are green.
- Modify if needed: `.github/workflows/android-debug.yml` only to include new required test report assertions; do not weaken existing gates.
- Modify: `docs/superpowers/specs/2026-10-21-localcut-mobile-media-import-design.md` only if implementation evidence requires a documented constraint change.

**Interfaces:**
- LocalCut Mobile adds supported action:
  `import_url`: `action=import_url&url=<URL_ENCODED_HTTPS_URL>&returnTo=chatgpt`.
- Plugin must state clearly that attachments from ChatGPT are not directly uploaded by the plugin in this phase; Android Share or an accessible HTTPS URL is required.

- [ ] **Step 1: Update LocalCut Mobile plugin to the next patch version only after Android unit/build tests pass**

Add `import_url` syntax, confirmation requirement, HTTPS-only rule, and the no-direct-binary-upload limitation. Preserve pairing and all existing edit actions.

- [ ] **Step 2: Run/observe all three PR gates on the final commit**

Required results:
- `Android Debug APK`: success.
- `Android Multitrack Export Smoke`: success, including `SharedMediaImportSmokeTest`, `RemoteMediaValidatorSmokeTest`, and `RemoteMediaHandoffSmokeTest`.
- `Bridge Controller CI`: success (regression; controller should remain behaviorally unchanged unless the Relay handoff contract actually requires a patch).

- [ ] **Step 3: Audit the final diff against the pre-feature commit**

Expected: changes limited to media intake, mobile handoff, Android manifest/FileProvider, tests/workflows, docs, and plugin contract; no unrelated timeline/render/export rewrites.

- [ ] **Step 4: Download the exact Android Debug artifact, verify ZIP/APK integrity, record `versionName`, `versionCode`, byte size and SHA-256, and provide the APK**

Do not call the feature physically validated until the user installs and tests it on the real Android device.

- [ ] **Step 5: Physical acceptance test**

On the user's phone:
1. Share one photo/video from Android -> LocalCut; verify it appears in timeline without picker.
2. Share multiple same-category media files; verify order and count.
3. Use a safe public HTTPS test media URL through LocalCut Mobile; confirm local dialog, import, and automatic return to ChatGPT.
4. Verify Bridge still reports Online and a normal edit handoff still works.

- [ ] **Step 6: Final checkpoint**

Record final commit SHA, plugin version/release, APK version/hash, CI run IDs, physical-test result, and remaining limitation: no direct ChatGPT-attachment binary transport yet. Keep PR #13 draft/unmerged.
