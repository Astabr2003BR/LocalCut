# Checkpoint — Termux full automation gate

Base: `62f7e55392bc5854cc0ec7a08922ed4dbb0c085e`
Branch: `feature/localcut-bridge-v1`
PR: #13, must remain OPEN + DRAFT.

## Implemented in this batch
- Managed Termux watchdog with local-first recovery.
- Explicit `MANAGED_AUTOMATION_V1` capability; Android persists managed readiness only after observing it with the complete creative capability set.
- Latest-successful-Termux-CI revision selection before auto-update.
- Origin/clean-tree/fast-forward/source syntax gates.
- FFmpeg + OpenCV self-repair.
- PID-validated graceful supervisor restart.
- Stable `auto-recover` launcher and Termux:Boot hook.
- One-time external automation bootstrap.
- Android RUN_COMMAND permission and static background command dispatch.
- 60-second wake throttle and persisted `managed_ready` evidence.
- One-retry policy for safe/idempotent engine calls.
- No automatic retry for stage/download/cancel.
- Developer-agent preflight followed by exactly one operation send.
- Cold-start automatic wake for paired managed installations.
- UI reduced to one-time activation/migration plus emergency recovery.

## Local evidence before branch update
- Python automation/setup/bootstrap/managed-capability tests: 11 PASS.
- `python3 -m py_compile`: PASS.
- `bash -n` setup/bootstrap: PASS.
- Pure Kotlin automation/recovery policy harness: PASS.
- Pure Kotlin recovering client harness: RED observed without implementation, GREEN after implementation.
- Pure Kotlin developer-agent preflight harness: RED observed without implementation, GREEN after implementation.

## Remaining gate
Full Android/Gradle and repository-wide Termux tests require GitHub CI after the single final branch commit. Do not generate/deliver another APK until those gates are green.
