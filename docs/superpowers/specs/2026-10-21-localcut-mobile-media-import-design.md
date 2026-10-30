# LocalCut Mobile Media Import — Design

Date: 2026-10-21
Branch: `feature/localcut-bridge-v1`
PR: #13 (remain draft; do not merge without explicit authorization)

## Goal
Allow media originating from ChatGPT or any Android app to enter LocalCut with minimal friction, then continue editing through the existing LocalCut Mobile handoff/Bridge flow.

## Scope — Phase 1

### A. Android native Share Target
LocalCut will register as an Android share target for:
- `image/*`
- `video/*`
- `audio/*`

Supported intents:
- `ACTION_SEND`
- `ACTION_SEND_MULTIPLE`

The app will accept one or more shared `content://` URIs, validate MIME/type, import each media item using the existing LocalCut asset-import path, and append clips to the appropriate track using the same semantics as manual import.

This lets the user use Android's normal **Compartilhar → LocalCut** flow from ChatGPT, Gallery, Files, or another app without reopening LocalCut's picker.

### B. HTTPS `import_url` handoff
LocalCut Mobile will support an explicit handoff action:

`https://localcut-bridge-v1.onrender.com/open?action=import_url&url=<https-url>&returnTo=chatgpt`

The APK will require the same local confirmation dialog used by other handoff actions.

Constraints:
- HTTPS only.
- Reject non-HTTP(S), localhost/private-network destinations, oversized URLs, unsupported MIME types, redirect abuse, and responses larger than configured limits.
- Download into app-private temporary storage.
- Validate declared and sniffed media type before import.
- Import through the existing asset-import pipeline.
- Delete temporary network payload after LocalCut has copied/persisted what it needs.
- Do not silently overwrite existing project media.

Initial URL-import size limits:
- image: 50 MiB
- audio: 200 MiB
- video: 1 GiB

The implementation may use stricter limits if Android memory/storage behavior requires it.

## Non-goals
- No direct binary upload from the ChatGPT plugin to the Relay in this phase, because the current mobile conversation/plugin interface does not expose a supported binary file transport to LocalCut.
- No public Drive links or weakening sharing permissions.
- No automatic import without local user confirmation for remote URLs.
- No changes to timeline rendering/export architecture.
- No merge of PR #13.

## Architecture

### Share import path
Android app -> `MainActivity` share intent -> `SharedMediaIntentParser` -> existing `AndroidAssetImporter` -> existing `ImportedClipFactory` / overlay policy where applicable -> `EditorAction.AddImportedClip` -> timeline.

### URL import path
ChatGPT -> LocalCut Mobile HTTPS handoff -> `MobileHandoffActivity` -> confirm -> `RemoteMediaImporter` -> guarded HTTPS download to app-private cache -> existing `AndroidAssetImporter`/equivalent file-content import -> timeline -> cleanup -> return to ChatGPT.

The URL importer must not execute through the edit command envelope because it performs I/O and media ingestion rather than a timeline mutation against an existing clip.

## Files expected to change
- `overrides/app/src/main/AndroidManifest.xml`
- `overrides/app/src/main/java/com/localcut/editor/MainActivity.kt`
- `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoff.kt`
- `overrides/app/src/main/java/com/localcut/editor/mobile/MobileHandoffActivity.kt`
- new shared-media parser/import helper(s)
- new remote-media downloader/import helper(s)
- associated unit/instrumentation tests
- LocalCut Mobile plugin skill/manifest for `import_url`

## Security invariants
- Never trust MIME alone; validate content where practical.
- Never accept `file://` from external apps.
- Remote import is HTTPS-only.
- Block loopback, link-local, RFC1918/private addresses, and obvious SSRF pivots.
- Enforce redirect count and revalidate redirect destinations.
- Enforce byte limits while streaming; never rely only on `Content-Length`.
- Generate app-private temporary names; never honor remote path traversal names.
- Local user confirmation remains mandatory for `import_url` handoff.
- Shared Android intents are user-initiated by the OS chooser and may import immediately when LocalCut is chosen.

## UX
- Shared media opens LocalCut and displays import result via Toast/status.
- Single image/video/audio appends to its normal track.
- Multiple items import in incoming order.
- Unsupported items are skipped with a visible error; valid siblings continue.
- `import_url` confirmation text identifies that LocalCut will download/import remote media.
- After remote import success/failure, return to ChatGPT when `returnTo=chatgpt` is present.

## Testing

### Unit
- parse `ACTION_SEND` for image/video/audio.
- parse `ACTION_SEND_MULTIPLE` preserving order.
- reject missing/unsupported stream values.
- parse `import_url` only for valid HTTPS URLs.
- reject non-HTTPS/private/local targets.
- redirect validation.
- byte-limit enforcement.
- temporary-file cleanup on success and failure.

### Android emulator smoke
- resolve LocalCut as share target for image/video/audio.
- import one visual media item through an explicit `ACTION_SEND` intent.
- import multiple media URIs through `ACTION_SEND_MULTIPLE` where emulator fixtures permit.
- existing Bridge + mobile handoff + export smoke continues passing.

### Regression gates
- Android Debug APK workflow.
- Android Multitrack Export Smoke workflow.
- Bridge Controller CI when plugin/handoff contract changes.
- Existing manual import, preview, timeline editing, undo/redo, export, pairing, and return-to-ChatGPT flows must remain intact.

## Success criteria
1. On Android, LocalCut appears in Share targets for photo/video/audio.
2. Choosing LocalCut imports media into the current project without opening LocalCut's picker.
3. An `import_url` handoff can import a supported public HTTPS media URL after confirmation.
4. Existing LocalCut features and Bridge pairing remain green in CI/emulator regression.
5. PR #13 remains draft and unmerged.
