# Character + Scene Animation Engine — Implementation Plan

**Goal:** add a complete deterministic V1 character/scene animation package on top of LocalCut's existing PropertyAnimation/keyframe pipeline.

**Constraints:** branch `feature/localcut-bridge-v1`; PR #14 stays OPEN + DRAFT; no merge/release; preserve import/preview/timeline/trim/split/delete/undo-redo/export/signing/overrides; Android-first; TDD; no cloud dependency for core animation.

## Task 1 — RED: planner contract
- Add unit tests for flat IDLE/TALK/WALK/CROUCH/STAND/ENTER/EXIT/POINT.
- Add rigged limb/head/mouth tests.
- Add lip cue validation/rest-state tests.
- Add PICK_UP/PUT_DOWN object tests.
- Add scene pan/zoom/crossfade tests.
- Verify RED in Android Debug CI because implementation does not exist.

## Task 2 — GREEN: deterministic planners
- Add `CharacterSceneAnimationPlanner.kt`.
- Compile all actions to existing transform PropertyAnimations.
- Keep every generated timestamp inside clip bounds.
- Implement deterministic merge/deduplication by property/time.
- Implement flat fallbacks and optional rig-part augmentation.
- Implement scene pan/zoom and background crossfade.

## Task 3 — RED/GREEN: atomic history command
- Test multi-clip atomic application, preservation of unrelated property animations, locked-track rejection, missing-clip rejection, undo inverse.
- Add `ApplyAnimationPlanCommand` using TimelineValidator and TrackEditGuard.

## Task 4 — RED/GREEN: Bridge control
- Add typed `animate_character` and `animate_scene` capabilities.
- Require revision as normal project writes.
- Parse strict payloads; validate action/timing/intensity/IDs/lip cues.
- Compile + dispatch one atomic command.
- Add Bridge executor/protocol tests.

## Task 5 — UI/read-model visibility
- Expose generated animation-property count/summary through existing clip/project reader where appropriate without breaking existing JSON fields.
- Do not add a second renderer or hidden state.

## Task 6 — Regression
- Run Android unit tests + debug APK.
- Run Android Multitrack Export Smoke.
- Run Bridge Controller CI if Bridge/controller contracts are affected.
- Run Termux Agent Engine CI if protocol allowlists are affected.
- Review logs for timeline, serialization, preview/export, undo/redo, track lock and protocol regressions.
- Record final HEAD and evidence in PR #14.

## Definition of done
- All acceptance criteria in `docs/superpowers/specs/2026-11-03-character-scene-animation-engine.md` pass.
- No unresolved P0/P1 regression.
- No claim of articulated movement from a flat image beyond deterministic fallback.
