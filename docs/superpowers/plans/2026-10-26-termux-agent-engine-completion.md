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

- Interrupted media upload must fail closed and never turn arbitrary input into a path or command.
- Duplicate idempotency key with different input must fail rather than reuse the wrong task.
- Process restart while RUNNING must produce deterministic recovery state, not silently replay a non-idempotent action.
- Unicode names and spaces must remain metadata only and never influence filesystem paths or shell parsing.
- Termux unavailable/ffprobe unavailable must fail the task without breaking LocalCut's normal editing functions.

---

### Task 1: Durable Typed Job Store

- [x] Write failing JobStore/idempotency/recovery tests.
- [x] Implement SQLite JobStore with private permissions and bounded JSON.
- [x] Integrate `CREATE_JOB`, `GET_JOB`, `CANCEL_JOB` into the authenticated engine protocol.
- [x] Preserve the original V1 read-only contract when no JobStore is supplied.

### Task 2: Safe Media Staging + ffprobe `ANALYZE_MEDIA`

- [x] Implement generated spool paths, byte limits, SHA-256 and atomic staging.
- [x] Implement ffprobe argv execution with `shell=False`, timeout and normalized output.
- [x] Integrate typed `STAGE_MEDIA` and `ANALYZE_MEDIA` jobs.
- [x] Test path traversal, wrong length, ffprobe unavailable, Unicode metadata and idempotency conflicts.

### Task 3: Android Job/Upload Client

- [x] Keep V1 `TermuxEngineClient` intact and add a separate `TermuxEngineJobClient`.
- [x] Stream `ContentResolver` bytes rather than exposing an app-private filesystem path.
- [x] Verify response request id, protocol version, transfer size and SHA-256.
- [x] Add media analysis coordinator with deterministic idempotency keys.

### Task 4: Minimal LocalCut UI Integration

- [x] Add analysis availability policy for connection, selection, job capabilities and `FFPROBE`.
- [x] Add “Analisar mídia selecionada” inside the existing Agente panel.
- [x] Preserve normal editing when Termux is absent or stopped.
- [x] Display structured success/failure state without blocking the editor.

### Task 5: Recovery, Background and Approval Policy

- [x] Mark interrupted RUNNING jobs failed on engine restart rather than silently replaying them.
- [x] Add boot/start scripts with wake lock opt-in rather than mandatory.
- [x] Isolate the development agent to project `localcut`, repository `Astabr2003BR/LocalCut` and one workspace.
- [x] Classify critical operations as approval-required and unknown operations as denied.
- [x] Persist `WAITING_APPROVAL` checkpoints with private permissions and filtered context.
- [x] Add optional `termux-notification` surface; no notification/response never bypasses the gate.
- [x] Approval changes checkpoint state only; it does not automatically execute the critical operation.

### Task 6: Documentation, Artifact and Regression Gate

- [x] Run the local Python suite with ResourceWarnings as errors.
- [x] Add Kotlin hardening tests for protocol mismatch and transfer-size mismatch.
- [ ] Run Android Debug, Bridge, Termux and emulator smoke gates once on the final SHA.
- [ ] Download and hash-match the final APK artifact.
- [ ] Verify PR #13 remains open/draft/not merged and final diff is scoped.
- [ ] Physical-device-only checks: install over existing APK, pair with real Termux, run real ffprobe media job, process-death recovery and approval notification behavior.
