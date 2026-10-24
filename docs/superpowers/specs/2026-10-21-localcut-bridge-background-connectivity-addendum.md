# LocalCut Bridge — Background Connectivity Addendum

Date: 2026-10-21
Status: User-selected mode baseline (B + C hybrid)
Parent specs:
- `docs/superpowers/specs/2026-10-19-localcut-studio-bridge-design.md`
- `docs/superpowers/specs/2026-10-19-localcut-bridge-v1-protocol.md`

## 1. Decision

The LocalCut Bridge will support both requested operating modes:

- **Mode B — Background session:** the user explicitly enables `Conectar ao ChatGPT`; LocalCut starts a visible foreground service and maintains the outbound Bridge connection while the app is not on screen.
- **Mode C — Always connected:** optional user preference layered on top of Mode B. Once enabled and paired, LocalCut attempts to restore the Bridge session automatically after process recreation, app update and device reboot, subject to Android/OEM background restrictions.

Mode B is the default. Mode C is opt-in.

## 2. Foreground service model

Use one `BridgeConnectionService` with a persistent user-visible notification while the remote-control session is active.

Requirements:

- `android:exported="false"`
- no inbound Android socket/listening port
- outbound TLS/WSS only
- start initiated from visible LocalCut UI for Mode B
- immediate disconnect action in notification
- notification shows `Conectado`, `Reconectando`, `Editando`, `Exportando` or `Erro`
- service networking and serialization never run on the main thread
- service must not request camera, microphone or location permissions

Because the Bridge control session does not fit another Android foreground-service category, the implementation should use the `specialUse` foreground-service type and declare a precise `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, e.g. user-enabled persistent remote editing/control session between LocalCut and a paired LocalCut Controller. This choice must be rechecked against Play policy before public Play release.

Do not use `dataSync` for the always-connected control channel: Android 15 applies aggregate runtime limits to that service type.

## 3. Mode B lifecycle

1. User taps `Conectar ao ChatGPT` in LocalCut.
2. Pairing/authentication completes if required.
3. LocalCut starts `BridgeConnectionService` while the activity is visible.
4. Service promotes itself to foreground immediately and displays the persistent notification.
5. Service opens the authenticated outbound WSS connection.
6. User can leave LocalCut; session remains active.
7. Controller may issue only allowlisted LocalCut commands.
8. User can disconnect from LocalCut or the notification.
9. Disconnect closes WSS, invalidates the active device session and stops the foreground service.

## 4. Mode C lifecycle

Mode C adds `Sempre conectado` to Bridge settings.

When enabled:

- persist only a boolean preference plus non-secret pairing metadata;
- device private identity remains non-exportable in Android Keystore;
- after app/process recreation, reconnect if the foreground session is still legally restartable;
- receive `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` where appropriate, and `MY_PACKAGE_REPLACED` for recovery after reboot/update;
- on supported Android versions, attempt to restore the `specialUse` foreground Bridge session;
- if the OS/OEM blocks background foreground-service startup, do not bypass the platform. Post a high-visibility `Toque para reconectar ao ChatGPT` notification instead; user interaction is then the legal foreground-service start trigger;
- use bounded exponential reconnect backoff with jitter, capped at 60 seconds while the service is active;
- never execute stale write commands after reconnection; existing command TTL/replay rules remain authoritative.

Mode C is therefore **best-effort always connected**, not a promise that Android/OEM firmware can never kill or defer the process.

## 5. Android version constraints

The implementation must test at minimum Android API 31, 34 and 35 behavior.

Known current platform constraints:

- Android 12+ generally blocks starting foreground services from background except defined exemptions.
- User-visible interaction is a valid start path.
- `BOOT_COMPLETED` is a documented exemption, but Android 14/15 apply additional restrictions to specific foreground-service types.
- Android 14+ requires declaring the foreground-service type and its corresponding permission.
- Android 15 applies time limits to `dataSync` and `mediaProcessing`; they are not suitable for the persistent Bridge control channel.
- `specialUse` requires an explicit manifest subtype explanation and may be reviewed during Play Console submission.

Any future target-SDK change must re-run these compatibility checks before release.

## 6. Security invariants

Background connectivity does not expand Bridge capabilities.

Required invariants from Bridge V1 remain:

- Android Keystore ECDSA P-256 device key
- outbound TLS/WSS only
- short-lived sessions
- replay protection and command TTL
- message-id idempotency
- `expectedRevision` for project mutations
- capability allowlist
- no shell
- no arbitrary filesystem access
- no generic Android intent/app-control surface
- no deletion/modification of source media
- sanitized diagnostics only
- local disconnect/revoke always available

The service accepts only typed LocalCut tool commands and routes project edits through the editor command/session layer.

## 7. Command execution while UI is not visible

Background remote edits are allowed only if:

- Bridge is explicitly enabled by the user;
- device is paired and session valid;
- project is loaded and not in a conflicting local transaction;
- `expectedRevision` matches;
- command is allowlisted;
- command validates through the Editor Command Bus / ProjectSession transaction path.

If a command needs a local permission grant, SAF picker, or other visible Android UI, return `LOCAL_INTERACTION_REQUIRED`; do not synthesize taps or bypass Android permission UX.

## 8. Anime/video autonomous workflow

B + C is intended to support autonomous video assembly even while the LocalCut activity is not foregrounded.

Example:

`ChatGPT Controller -> apply_edit_script -> LocalCut foreground Bridge service -> Editor Command Bus -> ProjectSession -> preview/export`

The Controller may:

- inspect project/timeline
- add previously authorized/imported media references
- arrange clips/layers
- add text
- apply transforms and keyframes
- set audio levels/fades
- add supported transitions/effects
- request preview frames
- start an export
- inspect export progress/result

Operations requiring media that has not yet been granted to LocalCut must pause with `LOCAL_INTERACTION_REQUIRED` until the user authorizes/imports that media.

## 9. Battery and reliability policy

- persistent notification is mandatory while a live Bridge foreground session exists;
- no wake-lock is held continuously just to keep the socket alive;
- network reconnect uses bounded exponential backoff;
- heartbeat cadence must be conservative and configurable by transport layer;
- when Android kills the process, project/autosave integrity takes priority over immediate reconnection;
- `Sempre conectado` may surface a one-time guidance screen for OEM battery settings, but must not silently request broad/unrelated permissions.

## 10. UI additions

Bridge settings/status screen:

- `Conectar ao ChatGPT` / `Desconectar`
- pairing code/QR when unpaired
- paired controller/device status
- toggle `Sempre conectado`
- status: Offline / Conectando / Online / Editando / Exportando / Erro
- `Revogar pareamento`
- bounded audit-log viewer

Foreground notification:

- LocalCut Bridge status
- active project name when available
- progress during export
- action `Desconectar`
- optional action `Abrir LocalCut`

## 11. Acceptance criteria

### Mode B

1. User starts connection from visible LocalCut UI.
2. Persistent foreground notification appears.
3. App can leave foreground while WSS session remains operational.
4. Read command succeeds in background.
5. Reversible project edit succeeds in background through ProjectSession.
6. Export can be started and progress queried while activity is not visible.
7. Notification disconnect immediately terminates controller access.

### Mode C

1. User enables `Sempre conectado` explicitly.
2. Preference survives app restart/update.
3. After reboot, LocalCut either restores the Bridge foreground session where Android allows it or produces a tap-to-reconnect notification without executing remote commands first.
4. Expired/stale commands are never executed after reconnection.
5. OEM/process kill does not corrupt the project or source media.
6. Turning `Sempre conectado` off prevents automatic recovery attempts.

## 12. Non-goals

- hidden surveillance/background operation without the user's visible notification
- arbitrary Android remote control
- controlling other apps
- bypassing OS battery/permission restrictions
- guaranteeing uninterrupted 24/7 connectivity on all Android OEM firmwares

