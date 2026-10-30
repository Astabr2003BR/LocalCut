# LocalCut Termux Agent Engine

The Termux Agent Engine is an auxiliary worker for LocalCut. LocalCut remains the editor, project source of truth, preview, timeline and export surface. The worker never exposes an arbitrary shell endpoint and binds its services only to loopback.

## Install and configure in Termux

Install the runtime dependencies, then run the repository setup script:

```bash
pkg install python ffmpeg
cd /path/to/LocalCut
bash termux-engine/setup_termux.sh
```

For the first pairing:

```bash
python termux-engine/localcut_supervisor.py --pair
```

The supervisor prints only a short-lived one-time code such as `LOCALCUT_PAIRING_CODE=ABCD-EFGH-JKLM`. Enter it in LocalCut. The APK exchanges it locally, stores the long-lived token in Android Keystore and checks the worker with `PING`.

Pairing properties:

- 12-character human-safe one-time code;
- default lifetime: 5 minutes;
- maximum invalid attempts: 5;
- successful pairing invalidates the code immediately;
- the long-lived token is not printed by the normal CLI flow;
- Termux credentials live in `~/.localcut-engine/credentials.json` with private permissions.

## Supervisor and background operation

Normal foreground start:

```bash
python termux-engine/localcut_supervisor.py
```

The supervisor holds a private instance lock and starts both local services:

- media/job engine on `127.0.0.1:43127`;
- development-agent RPC on `127.0.0.1:43128`.

`setup_termux.sh` also creates the Termux:Boot launcher. Open the Termux:Boot app once after installing it. Android can still kill background processes under memory pressure; persisted jobs make interrupted work explicit after restart instead of pretending it continued.

## Media jobs and `ANALYZE_MEDIA`

Authenticated typed media operations include `PING`, `GET_STATUS`, `GET_CAPABILITIES`, `CREATE_JOB`, `GET_JOB`, `CANCEL_JOB`, and `ANALYZE_MEDIA` through `STAGE_MEDIA`. There is no generic shell/`EXEC` endpoint.

LocalCut does not hand Termux a private Android filesystem path. The APK opens the selected asset through `ContentResolver` and streams exactly the declared byte length over the authenticated loopback socket. The APK validates request id, protocol version, returned byte count and SHA-256.

The engine stages media under its private spool and runs `ffprobe` using a fixed argv list with `shell=False`. `ffprobe` is supplied by the Termux `ffmpeg` package.

Jobs are persisted in `~/.localcut-engine/jobs.sqlite3`. Idempotency-key conflicts fail. If the worker restarts while a job is `RUNNING`, startup recovery marks it failed with `ENGINE_RESTARTED_DURING_JOB` instead of silently replaying it.

## Dedicated development agent

`localcut_agent.py` contains the typed development-agent policy, safe executor, persistent approval store, notification sink and controller. `localcut_agent_rpc.py` exposes those operations only over authenticated loopback RPC.

The safe executor is restricted to a resolved LocalCut workspace/repository and a small fixed argv allowlist (status/diff/tests/debug build). It does not execute arbitrary user/model command strings and does not inherit secret-like environment variables.

Protected branches are rejected by policy. Critical classes never enter the safe executor: merge to main, force push, deleting project/repository/database/production data, dropping tables/databases, changing APK signing/keystore, altering or revoking secrets, Play Store/production publishing, critical-backup overwrite and destructive migrations.

A critical request is persisted as `WAITING_APPROVAL`. Secret-like keys are recursively filtered from the persisted payload/checkpoint. If `termux-notification` is available, the RPC layer can emit an informational Android notification. Approval only changes persistent state; it does not automatically execute the critical operation. No response means no execution.

`GET_PENDING_APPROVALS` and `RESOLVE_APPROVAL` let the authenticated LocalCut side review/resolve those checkpoints without weakening the execution gate.

For Android notifications, install the Termux:API companion/tooling appropriate to your Termux installation so `termux-notification` is available. Its absence is non-fatal and never bypasses approval.

## Security invariants

- media and agent services bind only to `127.0.0.1`;
- shared authenticated token, protocol and request-id checks;
- no arbitrary shell endpoint;
- safe development commands and ffprobe use fixed argv with `shell=False`;
- generated media staging and bounded transfer verification;
- private credential/job/approval storage permissions;
- secret-like environment variables are not inherited by the safe executor;
- secrets are filtered from persisted approval payloads/checkpoints;
- LocalCut editing continues to work when Termux is unavailable;
- PR #13 remains draft and must not be merged without explicit authorization.
