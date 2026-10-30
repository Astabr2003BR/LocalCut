# LocalCut — Termux Agent Engine full automation spec

## Goal
After the initial secure pairing/bootstrap, LocalCut must recover the Termux Agent Engine without requiring routine copy/paste commands. The managed flow is: LocalCut wake request -> Termux background RUN_COMMAND -> local watchdog -> local-first engine start -> validated update/toolchain maintenance -> safe restart -> bounded client retry -> job execution.

## One-time security boundary
Termux intentionally requires both `com.termux.permission.RUN_COMMAND` and `allow-external-apps=true` before another Android app may invoke its RunCommandService. LocalCut may request the Android permission, but it cannot edit Termux private preferences before Termux authorizes external commands. Therefore an existing legacy installation needs one bootstrap inside Termux. This is a migration/bootstrap step, not normal operation.

## Managed steady state
- LocalCut records `managed_ready` only after the connected engine reports the complete creative capability set.
- LocalCut wakes Termux on process cold start when paired and previously confirmed managed.
- Health-sensitive read/idempotent job operations may wake and retry exactly once.
- Media staging, generated-media download and cancellation are never blindly retried.
- ChatGPT developer-agent operations use a recoverable health preflight, then execute exactly once.
- The Termux watchdog checks engine health every 60 seconds and maintenance every 6 hours.
- Maintenance repairs FFmpeg/OpenCV when missing.
- Source updates are accepted only from the latest successful Termux Agent Engine CI revision for `feature/localcut-bridge-v1`.
- Update origin must remain `Astabr2003BR/LocalCut`, the managed tree must be clean, and a non-fast-forward update is rejected.
- Python/shell entrypoints are syntax-validated before the running engine is replaced.
- Restart reads the supervisor lock PID, validates its cmdline, sends SIGTERM only to that process and fails closed on mismatch/timeout.

## Explicit non-goals / safety locks
- No merge to `main`.
- No Play Store publication.
- No signing changes.
- No arbitrary shell command supplied by ChatGPT/UI.
- No token in generated commands.
- No `pkill`, `kill -9`, `git reset --hard` or destructive source replacement.
- No unbounded reconnect/update loop.
- No automatic replay of potentially partial or side-effecting operations.

## Boot/recovery
`setup_termux.sh` writes a Termux:Boot-compatible script when that companion is present. Independently, LocalCut cold start and recoverable client preflight can dispatch the managed background RunCommand. OEM/Android force-stop remains an OS boundary: a user force-stop can require reopening the affected app.
