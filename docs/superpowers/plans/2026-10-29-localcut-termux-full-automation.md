# LocalCut Termux full automation implementation plan

1. Add a Termux watchdog/autoupdater with source, CI, toolchain, PID and lock safety gates.
2. Upgrade Termux setup with stable launchers, external automation opt-in and boot hook.
3. Add one-time bootstrap script that safely migrates the currently paired legacy engine and forces one validated restart.
4. Add Android RUN_COMMAND manifest permission, static command spec and throttled dispatcher.
5. Persist `managed_ready` only after full creative capabilities are observed.
6. Add bounded recovery wrapper for safe/idempotent engine operations only.
7. Add developer-agent health preflight and preserve exactly-once operation dispatch.
8. Wake the managed engine on LocalCut cold start when paired.
9. Replace recurring manual repair UI with one-time activation/migration plus emergency fallback.
10. Run local Python/shell/Kotlin gates, create one atomic branch commit, then use one CI checkpoint for the new SHA.
