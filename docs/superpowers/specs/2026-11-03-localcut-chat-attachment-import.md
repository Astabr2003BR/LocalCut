# LocalCut Chat Attachment Import Spec

## Intent
Allow a media file already attached in a normal ChatGPT conversation to be imported into the currently open LocalCut Android project without Android Share and without making the file permanently public.

## Chosen approach
Reuse the existing `import_url` APK path. The Relay accepts a temporary raw-media upload identified by a caller-generated cryptographically random token, stores it in memory with a short TTL, and exposes a one-time signed-by-possession HTTPS download URL. The LocalCut Mobile skill uploads the mounted/materialized attachment to the Relay, then hands the temporary URL to the existing `import_url` action.

## Security and limits
- No persistent/public media hosting.
- Token: at least 32 random bytes / 256 bits, URL-safe.
- Upload body: raw bytes only.
- Allowed MIME prefixes: `image/`, `audio/`, `video/`.
- Maximum attachment: 20 MiB.
- TTL: 5 minutes.
- One-time download: successful GET consumes the item.
- `Cache-Control: no-store` on upload/download responses.
- Unknown/expired/consumed token fails closed with 404.
- Invalid token/MIME/body/oversize fails closed.
- Keep existing Android confirmation/import behavior unchanged.

## Plugin behavior
When a user explicitly asks LocalCut Mobile to import an attachment from the current conversation:
1. Use the exact attached file already present in conversation context; never substitute another file.
2. Materialize only when needed to obtain local bytes.
3. Generate a fresh 256-bit token locally.
4. Upload the file to the Relay temporary upload endpoint.
5. Build one `import_url` handoff using the returned one-time HTTPS download URL.
6. Do not claim completion until the APK/user confirms import.
7. If temporary upload is unavailable, fall back to Android Share -> LocalCut; never publish the attachment to a third-party public host.

## Non-goals
- No permanent cloud library.
- No generic arbitrary file hosting.
- No APK protocol change.
- No merge to `main` or production deployment without the existing approval gates.
