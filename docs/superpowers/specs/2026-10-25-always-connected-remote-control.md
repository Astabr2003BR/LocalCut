# LocalCut Always-Connected Remote Control — Design

## Intent
Keep the LocalCut Android agent continuously reachable in normal background use and let ChatGPT operate the paired editor without requiring the user to tap a handoff for every edit.

## Goals
- Preserve the existing Android pairing/session, revision checks, replay protection, CommandRouter, and Bridge protocol.
- Keep the background agent lightweight: no eager Media3/editor runtime solely to maintain connectivity.
- Reconnect automatically after transient network/process loss when Android permits it.
- Add a private web control surface on the existing Relay that routes through the same CommandRouter as MCP.
- Use device-approved browser authorization once; afterward the browser session may read project state and execute requested edits without local per-command confirmation.
- Keep PR #13 open, draft, and unmerged.

## Non-goals
- No unauthenticated command endpoint.
- No controller token or device token in URLs, HTML, logs, or plugin files.
- No new editing protocol.
- No copying project media to the Relay.
- No guarantee against Android Force Stop, OEM package kill, loss of internet, or server outage.

## Architecture
### Android
BridgeConnectionService remains the persistent foreground boundary. Pairing/credential work can start a lightweight foreground state before the full coordinator is needed. The service must not initialize LocalCutProcessRuntime merely to remain connected.

### Relay web control
Add a browser-control session service backed by signed, HttpOnly, Secure, SameSite=Strict cookies. A browser begins an authorization request; the page offers a `localcut://remote/approve` deep link. The APK approves the request using its existing authenticated device session. The browser then exchanges the approved request for a browser session cookie.

The browser control API exposes only the existing Bridge capabilities. It discovers active contexts via the same CommandRouter and creates BridgeCommandEnvelope values with TTL, nonce, revision, and existing authorization checks.

### Security
- Browser authorization requests are short-lived and one-time.
- Browser sessions are signed, scoped to the authorized device, revocable, and never returned to page JavaScript.
- Mutating commands require the current project revision when the Bridge capability requires it.
- CSRF protection is required for browser writes.
- Preview responses are bounded.

## Success criteria
1. Paired APK remains connected during ordinary background use on the user's POCO when Android does not forcibly stop the package.
2. Browser can authorize once through the APK.
3. Browser can discover the active project and execute a reversible `seek` through CommandRouter without any local confirmation.
4. Existing Bridge/MCP/mobile handoff tests continue to pass.
