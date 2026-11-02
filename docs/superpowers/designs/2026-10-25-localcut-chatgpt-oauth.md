# LocalCut ChatGPT OAuth Design

## Intent
Enable the private LocalCut ChatGPT plugin to connect to the existing authenticated LocalCut Bridge without embedding a fixed API key in the plugin. The user should authorize the connection through the already-paired LocalCut Android app, then ChatGPT can read and edit the active project directly.

## Goals
- Replace plugin fixed-bearer/API-key auth with OAuth 2.1 compatible with ChatGPT MCP.
- Keep `/mcp` protected at all times.
- Use the paired Android device/session as the owner authentication factor.
- Preserve existing device pairing, recovery, foreground service, timeline and export behavior.
- After first authorization, ChatGPT can reconnect using OAuth refresh tokens without a new pairing code.

## Non-goals
- Multi-user accounts or public plugin distribution.
- Replacing the existing device session protocol.
- Moving project data to the cloud.

## Architecture
1. Controller exposes RFC 9728 protected-resource metadata and RFC 8414 authorization-server metadata.
2. ChatGPT uses authorization-code + PKCE (S256), public client `localcut-chatgpt`, stable ChatGPT redirect URI, and scopes `localcut:read` / `localcut:write`.
3. `/oauth/authorize` creates a short-lived pending request and shows a page with a deep link into LocalCut.
4. LocalCut opens the deep link, approves the pending request by POSTing it to the controller using its existing device access token, then opens `/oauth/complete` in the browser.
5. Controller redirects to ChatGPT with the authorization code; `/oauth/token` validates PKCE and issues signed access/refresh tokens bound to the approving device and MCP resource.
6. `/mcp` accepts only valid OAuth access tokens. Device revocation invalidates MCP authorization because token validation also checks the device is still paired/not revoked.
7. Plugin `mcp.json` changes to OAuth with a provided public client and explicit endpoints/scopes.

## Security constraints
- No controller token, device session token, OAuth token or signing material in plugin files or user-visible logs.
- Authorization requests expire and are single-use.
- Authorization codes expire quickly and are single-use.
- Exact allowlist for `client_id`, redirect URI, resource and scopes.
- PKCE S256 required.
- OAuth tokens are signed using controller secret material but are logically separate from device credentials.
- Existing `/v1/*` device and pairing authorization remains unchanged.

## Approaches considered
- Keep fixed API key: rejected because the current ChatGPT connection flow fails before reaching the Relay and fixed API-key setup is not suitable for this private install path.
- Make `/mcp` unauthenticated: rejected because it would expose write tools publicly.
- OAuth via third-party identity provider: valid but adds an external account/provider and unnecessary setup.
- **Chosen: OAuth 2.1 approved by the already-paired LocalCut device.** It reuses the strongest identity already present and minimizes user setup.
