# LocalCut ChatGPT Attachment Transport — Design

Date: 2026-11-03
Branch: `feature/localcut-bridge-v1`
PR: #14 — keep OPEN + DRAFT; do not merge to `main` without explicit authorization.

## Goal
Allow the authenticated LocalCut MCP to receive a ChatGPT attachment through the supported `_meta["openai/fileParams"]` contract and stage supported media into the existing one-time relay, so the Android `import_url` handoff can import it without exposing a public user file URL.

## Grounded platform contract
ChatGPT file parameters are top-level MCP tool inputs declared in `_meta["openai/fileParams"]`. A file value contains `download_url`, `file_id`, and may contain `mime_type` and `file_name`.

## Scope
- Add one MCP management tool, `stage_attachment`.
- The tool accepts exactly one top-level `file` parameter using the ChatGPT file object shape.
- Supported relay media in this phase: `image/*`, `audio/*`, `video/*`.
- Fetch the temporary `download_url` server-side over HTTPS only.
- Enforce the relay byte limit while reading; never rely only on `Content-Length`.
- Stage bytes directly into the same in-memory transient-media store used by `/v1/mobile-media/<token>`; do not make a loopback/public HTTP upload to the relay itself.
- Return a one-time relay URL and Android handoff URL. Android keeps the existing local confirmation before remote import.
- TXT is not sent into the Android media importer because the current importer is intentionally media-only. Text files remain model-readable and can be converted into explicit `add_text` edits only when the user requests that semantic transformation.

## Security invariants
- HTTPS-only source download URL.
- Reject empty, unsupported-MIME, and over-limit payloads.
- One-time 256-bit relay token; bounded TTL; `Cache-Control: no-store` remains intact.
- Do not expose controller credentials, device session tokens, local URIs, or raw OpenAI download URLs in tool results.
- Do not weaken OAuth, pairing, revision checks, or local Android confirmation.
- Existing transient HTTP behavior remains compatible in this change; hardening/removal of unauthenticated POST is a separate security task.

## Files expected to change
- `bridge/controller/src/transientMediaHttp.ts`
- `bridge/controller/src/browserControlHttp.ts`
- `bridge/controller/src/mcp.ts`
- `bridge/controller/src/server.ts`
- `bridge/controller/test/transientMediaHttp.test.ts`
- `bridge/controller/test/mcp.test.ts`
- `bridge/controller/test/server.test.ts` as needed
- `bridge/chatgpt-plugin/skills/localcut-control/SKILL.md`

## Acceptance criteria
1. MCP tool descriptor for `stage_attachment` declares `_meta["openai/fileParams"] = ["file"]`.
2. File schema declares `download_url`, `file_id`, `mime_type`, `file_name`; only the first two are required.
3. Server fetches the ChatGPT-provided file over HTTPS, streams with the existing 20 MiB relay cap, and stages it without self-HTTP upload.
4. Returned relay URL downloads once and then returns 404.
5. Unsupported MIME, empty body, oversized body, non-HTTPS URL, and upstream download failure produce stable errors.
6. Bridge Controller CI, Android Debug APK, Android Multitrack Export Smoke, and Termux Agent Engine CI remain green.
