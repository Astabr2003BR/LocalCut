# Termux Agent Engine Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the incremental LocalCut ↔ Termux Agent Engine foundation through durable typed jobs, safe media staging, ffprobe analysis, restart recovery, approval policy scaffolding, Android integration, and final regression evidence without replacing the LocalCut editor.

**Architecture:** LocalCut remains the sole editor/UI. The Android app connects only to `127.0.0.1:43127`, authenticates with the existing Keystore-backed token, creates typed jobs, and streams media bytes over the authenticated socket into a Termux-private spool. The engine persists job state in SQLite, invokes ffprobe only through a fixed argv schema with `shell=False`, and never exposes arbitrary shell execution.

**Tech Stack:** Kotlin/Android/Compose, Python 3 stdlib, SQLite, socket TCP loopback, ffprobe from Termux `ffmpeg`, GitHub Actions.

**Spec:** `termux-engine/README.md` plus the LocalCut Termux Agent Engine requirements supplied for PR #13.

## Global Constraints

- Repository is `Astabr2003BR/LocalCut`; branch is `feature/localcut-bridge-v1`; GitHub is source of truth.
- PR #13 stays OPEN + DRAFT; never merge `main` without explicit authorization.
- Preserve import, preview, timeline, trim, split, delete, undo/redo, export, signing and `overrides/`.
- Bind Termux engine to literal `127.0.0.1`; never expose `0.0.0.0` by default.
- Never execute model/user text as shell; all operations are typed and validated.
- LocalCut must continue working when Termux is absent or stopped.
- Secrets must not be committed or written to logs.
- Critical operations remain blocked behind `WAITING_APPROVAL`; absence of approval never means approval.

## Review Focus

- Interrupted media upload must delete partial bytes and leave a retryable job.
- Duplicate idempotency key with different input must fail rather than reuse the wrong task.
- Process restart while RUNNING must produce deterministic recovery state, not silently replay a non-idempotent action.
- Unicode names and spaces must remain metadata only and never influence filesystem paths or shell parsing.
- Termux unavailable/ffprobe unavailable must fail the task without breaking LocalCut's normal editing functions.

---

### Task 1: Durable Typed Job Store

**Files:** create `termux-engine/localcut_jobs.py`; modify `termux-engine/localcut_engine.py`; tests under `termux-engine/tests/`.

**Interfaces:** produces `JobStore.create_job/get_job/update_job/cancel_job/recover_interrupted_jobs` and protocol operations `CREATE_JOB`, `GET_JOB`, `CANCEL_JOB`.

- [x] Write failing JobStore/idempotency/recovery tests.
- [x] Verify RED because `localcut_jobs` does not exist.
- [x] Implement SQLite JobStore with private permissions and bounded JSON.
- [x] Verify unit GREEN with ResourceWarnings treated as errors.
- [x] Integrate operations into the authenticated engine protocol.
- [ ] Run repository CI and close the commit gate.

### Task 2: Safe Media Staging + ffprobe ANALYZE_MEDIA

**Files:** `termux-engine/localcut_jobs.py`, `termux-engine/localcut_engine.py`, tests.

**Interfaces:** produces `MediaSpool.stage_bytes/path_for`, `FfprobeMediaAnalyzer.analyze`, `STAGE_MEDIA`, and `ANALYZE_MEDIA` job output.

- [x] Write failing spool/ffprobe tests.
- [x] Implement generated spool paths, byte limits, SHA-256 and atomic replacement.
- [x] Implement ffprobe argv execution with `shell=False`, timeout and normalized output.
- [x] Integrate raw body staging and job execution into the socket handler.
- [x] Test path traversal, wrong length, ffprobe missing, Unicode metadata and idempotency.
- [ ] Run repository CI and close the commit gate.

### Task 3: Android Job/Upload Client

**Files:** modify `TermuxEngineContract.kt`, `SocketTermuxEngineClient.kt`; create focused coordinator/test files in `overrides/app/src/main/java/com/localcut/editor/termux/`.

**Interfaces:** consumes current `Asset.uri/sizeBytes` and `ContentResolver`; produces typed create/get/cancel/stage/analyze calls.

- [ ] Write RED Kotlin tests for encoders/parsers and streaming with a loopback fake server.
- [ ] Extend contract without weakening V1 required capabilities.
- [ ] Stream ContentResolver bytes over socket while computing SHA-256; compare returned digest.
- [ ] Add coordinator that never exposes app-private paths to Termux.
- [ ] Verify unit tests and Android Debug gate.

### Task 4: Minimal LocalCut UI Integration

**Files:** `TermuxEnginePanel.kt` plus focused UI/session policy tests.

**Interfaces:** consumes Android job coordinator; produces “Analisar mídia selecionada” status/progress/result without blocking editing.

- [ ] Add RED UI/session tests for engine absent, queued/running/succeeded/failed/cancelled.
- [ ] Add the minimal action/status inside existing Agente panel.
- [ ] Preserve fallback and no-Termux requirement for normal editing.
- [ ] Verify build and regressions.

### Task 5: Recovery and Approval Policy Foundation

**Files:** focused Python policy module/tests and Android notification policy only where an actual WAITING_APPROVAL task exists.

**Interfaces:** produces safe/approval-required operation catalog and deterministic restart recovery.

- [x] Test RUNNING recovery and no automatic replay of interrupted work.
- [ ] Test critical operation classification and `WAITING_APPROVAL` persistence.
- [ ] Ensure no critical action implementation can bypass the policy layer.
- [ ] Add notification surface only for persisted approval-required tasks; no response means no execution.

### Task 6: Documentation, Artifact and Regression Gate

**Files:** update `termux-engine/README.md` and this plan/checkpoint.

- [ ] Run Python suite with ResourceWarnings as errors.
- [ ] Run Android Debug, Bridge, Termux and emulator smoke gates once per final SHA.
- [ ] Download and hash-match the final APK artifact.
- [ ] Verify PR #13 remains open/draft/not merged and diff is scoped.
- [ ] Record physical-device-only checks separately: install-over-existing, Termux pairing, real ffprobe job, process-death recovery and notification behavior.
