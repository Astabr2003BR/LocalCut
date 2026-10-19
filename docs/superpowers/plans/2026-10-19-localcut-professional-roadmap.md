# LocalCut Professional Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve LocalCut from the current Build 12 editor into a professional Android-first multitrack editor with CapCut-class editing, Alight Motion-class motion design, deterministic autonomous editing and the bidirectional LocalCut Bridge.

**Architecture:** Preserve the current working V0 and `overrides/` while introducing stable editor-command, project-model and renderer contracts. Build usability first, then motion/creative tools, then autonomous editing and remote control. Every stage is test-gated and reversible; no stage may silently replace working import/preview/timeline/export flows.

**Tech Stack:** Kotlin, Jetpack Compose, Android Media3, current LocalCut project/session/domain layers, Android Keystore, HTTPS/WSS relay for Bridge V1.

**Spec:** `docs/superpowers/specs/2026-10-19-localcut-studio-bridge-design.md` and `docs/superpowers/specs/2026-10-19-localcut-bridge-v1-protocol.md`

## Global Constraints

- Android/mobile-first.
- Preserve all currently working import, preview, timeline, trim, split, move, delete, undo/redo and MP4 export behavior.
- Preserve current overrides until a separately tested source-layout migration is approved by evidence.
- Do not copy CapCut/Alight Motion code, APKs, proprietary assets, templates, branded UI or protected effects.
- Source media is immutable.
- Timeline time uses integer microseconds.
- No OpenAI/API provider key is stored in the APK.
- Every project mutation must eventually route through a typed command/transaction boundary.
- One implementation stage -> tests -> review -> commit -> CI build -> only then next stage.
- A red CI gate stops progression.

## Review Focus

1. **Legacy project compatibility:** Build 12 projects must open without losing clips/assets or changing timing.
2. **Timeline collision policy:** exclusive tracks must still reject/clamp overlap while overlay tracks intentionally allow overlap.
3. **Undo/redo integrity:** multi-command edits, keyframes and autonomous edits must restore exact prior state.
4. **Android resource pressure:** preview/export must degrade safely under low memory/storage/thermal pressure instead of corrupting projects.
5. **Remote command safety:** duplicate, stale, expired or unauthorized Bridge commands must not mutate the project.

---

## A. Capability map: CapCut + Alight Motion -> LocalCut

### A1. CapCut-class editing

| Capability class | LocalCut module | Priority |
| --- | --- | --- |
| multitrack timeline / overlays | Project Model V2 + Timeline V2 | P1 |
| trim/split/move/duplicate/delete/ripple | Timeline Operations | P1 |
| crop/position/scale/rotation/opacity | Transform Inspector + Renderer | P1 |
| transitions | Transition Engine | P1 |
| text/captions/stickers | Layer Types | P1/P2 |
| audio tracks/waveform/fades/volume | Audio Engine | P1 |
| configurable export | Export Profiles | P1 |
| filters/color adjustments | Effect Registry | P2 |
| speed/speed-ramp/reverse/freeze | Time Remap Engine | P2 |
| masks/blend/chroma key | Compositor | P2 |
| advanced/AI helpers | Optional provider modules | P3 |

### A2. Alight Motion-class motion design

| Capability class | LocalCut module | Priority |
| --- | --- | --- |
| many video/audio/graphics layers | Project Model V2 | P1 |
| keyframes across editable properties | Animatable/Keyframe Engine | P1 |
| easing/custom curves | Curve Engine | P1 |
| vector/shape layers | Graphics Layers | P2 |
| grouping/masking | Layer Graph + Compositor | P2 |
| effect stacks | Effect Registry | P2 |
| parenting/null objects | Hierarchy Engine | P2/P3 |
| camera/Z-axis/parallax | 2.5D Scene Engine | P3 |
| motion blur/focus/fog | Advanced Renderer | P3 |
| project package backup | Project Package I/O | P2 |

### A3. Findings from `209917.mp4`

The supplied 4:55 portrait screen recording was reviewed as a capability reference. It visibly demonstrates:

- Alight Motion: project/element creation, resolution/FPS setup, shape/media/audio/object/vector/text categories, color editing, effect selection, masks, move/transform controls and presets.
- CapCut: multi-photo import, timeline clip sequencing, transition insertion, split/animation/effects/crop/delete/duplicate, video/body/AI effect categories, retouch/background tools, filters/adjustments and transform/mask controls.

LocalCut should reproduce the capability categories, not the exact layouts.

---

## B. Delivery order

### Gate 0 — CI/test truth before feature expansion

**Why first:** current CI proves compilation/build, but the custom `TimelineDragPlannerTestMain.kt` is a `main()` harness. The observed GitHub Actions log shows `testDebugUnitTest` succeeded but does not show the harness PASS output, so those overlap cases cannot yet be claimed as executed tests.

**Deliverables:**

- make overlap/timeline tests real Gradle-discovered unit tests or explicitly execute the harness in CI
- make APK artifact naming reflect the actual app build/run instead of fixed `build12`
- stop documentation-only commits from generating misleading APK build numbers, or decouple app versioning from workflow run number
- preserve stable development signing lineage
- add a sanitized source-audit mechanism if needed to inspect the Build 9 archive without publishing additional secrets

**Gate:** CI must show named timeline tests executed and pass before feature work.

### Milestone 1 — Editor usability foundation

This milestone makes LocalCut comfortable for real manual editing before advanced motion design.

Features:

- duplicate selected clip
- explicit ripple insert/delete policy
- snapping to playhead/clip edges
- timeline pinch zoom and horizontal navigation improvements
- clearer clip handles/selection state
- track mute/visibility controls groundwork
- project duration/selection diagnostics

Do not add a new renderer model yet unless required by the multitrack step.

### Milestone 2 — Project Model V2 + multitrack/layers

Features:

- multiple visual tracks/layers
- multiple audio tracks
- intentional overlay overlap
- exclusive/sequential track mode for legacy behavior
- add/remove/reorder tracks
- layer z-order
- V1 -> V2 migration
- backwards-compatible load/save

This is the critical architectural milestone. Later text, graphics, masks, keyframes and Bridge commands build on it.

### Milestone 3 — Audio + export + preview reliability

Features:

- waveform cache/generation
- audio fade in/out
- per-clip volume
- volume automation groundwork
- mute/solo
- detach/extract video audio
- configurable export profiles: resolution/FPS/bitrate and H.264; HEVC only when device support is verified
- adaptive preview quality
- low-storage/low-memory/export-failure handling

### Milestone 4 — Text, overlays and transitions

Features:

- text layer
- image/sticker overlay layer
- font/size/alignment/fill/stroke/shadow basics
- manual captions/subtitle track
- SRT import/export
- dissolve/fade/wipe-class original transitions
- transition duration editing

This milestone plus multitrack/audio/export is the first target that should feel like a broadly usable mobile editor.

### Milestone 5 — Keyframe and easing engine

Features:

- `Animatable<T>`
- keyframes for position, scale, rotation, opacity and volume
- hold/linear/ease-in/ease-out/ease-in-out
- cubic-Bezier custom curves
- keyframe timeline markers
- mobile curve editor
- deterministic interpolation tests

### Milestone 6 — Creative compositor

Features:

- effect registry
- brightness/contrast/exposure
- saturation/vibrance/temperature/tint
- blur/sharpen/vignette/glow/grain
- masks + feather
- blend modes
- chroma key
- crop/transform stack
- effect parameter keyframes

### Milestone 7 — Time remapping

Features:

- constant speed
- speed ramp/curve
- reverse
- freeze frame
- audio behavior policy under speed changes
- duration recalculation and snapping correctness

### Milestone 8 — Autonomous Editor core

Features:

- typed `EditorCommand`
- deterministic `EditScript`
- schema validator
- project revision precondition
- transaction executor
- checkpoint/rollback
- one-tap undo of a complete automated edit
- deterministic templates such as “N images x 4s + Ken Burns + music + transitions”
- Auto Edit screen

The AI/planner is not allowed to write project JSON directly.

### Milestone 9 — LocalCut Bridge V1

Implementation is split so editor usability remains the first priority.

**Bridge A — read/observe after the command bus is stable:**

- pairing/device identity
- WSS connection state
- `get_device_status`
- `get_project_state`
- `list_clips`
- `get_preview_frame`
- `get_errors`
- `get_export_status`

**Bridge B — controlled writes after transaction/revision infrastructure exists:**

- `seek`
- `split_clip`
- `trim_clip`
- `move_clip`
- `delete_clip`
- `undo`
- `redo`
- `import_media`
- `export_project`
- `apply_edit_script`

### Milestone 10 — Alight-style hierarchy / 2.5D

Features:

- groups
- parenting
- null/controller objects
- X/Y/Z transforms
- camera objects
- parallax workflows
- camera keyframes
- motion blur
- later focus blur/fog where mobile performance allows

### Milestone 11 — 3D later

Real 3D remains intentionally separate from the 2.5D editor so it cannot destabilize the core video editor.

Potential later scope:

- 3D model import
- scene lights/materials
- perspective camera
- GPU renderer path
- compositing 3D render output into normal LocalCut layers

---

## C. Implementation tasks and gates

### Task 1: Establish trustworthy CI timeline tests

**Files:**
- Modify: `.github/workflows/android-debug.yml`
- Modify/Create under: `overrides/app/src/test/java/com/localcut/editor/ui/editor/`

**Interfaces:**
- Consumes: current `TimelineEditBounds`, `TimelineDragPlanner`, `TimelineOps`
- Produces: CI-visible tests for overlap-safe trim/move behavior

- [ ] **Step 1: Write/convert a test discovered by `testDebugUnitTest` for left-trim collision.**
- [ ] **Step 2: Run CI and verify the test is reported/executed, not merely compiled.**
- [ ] **Step 3: Add right-trim and move collision cases, zero-boundary case, missing-clip invalid-input case.**
- [ ] **Step 4: Run `testDebugUnitTest assembleDebug`; expected: PASS and APK exists.**
- [ ] **Step 5: Commit only after the evidence is green.**

### Task 2: Normalize override application without rewriting the base

**Files:**
- Modify: `.github/workflows/android-debug.yml`

**Interfaces:**
- Consumes: `overrides/app/**`
- Produces: deterministic recursive overlay into unpacked Build 9 source

- [ ] **Step 1: Add CI assertions proving the known Build 12 override files exist.**
- [ ] **Step 2: Replace the fixed file-by-file copy list with a recursive overlay that preserves relative paths.**
- [ ] **Step 3: Keep post-overlay sentinel checks for media import, split and collision clamps.**
- [ ] **Step 4: Build/test and compare the resulting baseline behavior before adding new source files.**
- [ ] **Step 5: Commit.**

### Task 3: Add clip duplication and ripple policy

**Files:**
- Create/Modify under: `overrides/app/src/main/java/com/localcut/editor/domain/timeline/`
- Modify session actions under the corresponding override path after source audit
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorScreen.kt`
- Test under: `overrides/app/src/test/java/com/localcut/editor/`

**Interfaces:**
- Produces: `DuplicateSelected` and explicit ripple operations without mutating source media

- [ ] **Step 1: Test duplicate creates a new clip id with identical source trim/properties.**
- [ ] **Step 2: Test duplicate placement cannot create invalid overlap on an exclusive track.**
- [ ] **Step 3: Implement minimal domain/session operation.**
- [ ] **Step 4: Add mobile UI action.**
- [ ] **Step 5: Run regression tests/build and commit.**

### Task 4: Introduce Project Model V2 migration

**Files:**
- Create V2 model/migration files under `overrides/app/src/main/java/com/localcut/editor/domain/model/`
- Modify project persistence through targeted overrides after source audit
- Add migration tests

**Interfaces:**
- Produces: explicit track collision policy, multiple tracks/layers and V1 -> V2 migration

- [ ] **Step 1: Fixture-test Build 12/V1 project loading into equivalent V2 timing/assets.**
- [ ] **Step 2: Test exclusive track rejects overlap while overlay track accepts overlap.**
- [ ] **Step 3: Implement smallest model/migration changes.**
- [ ] **Step 4: Test save/reload round-trip equality.**
- [ ] **Step 5: Build/regression/commit.**

### Task 5: Multitrack timeline UI

**Files:**
- Modify/Create timeline components under `overrides/app/src/main/java/com/localcut/editor/ui/editor/components/`
- Add geometry/hit-test tests

**Interfaces:**
- Consumes: Project Model V2
- Produces: vertically scrollable tracks, add/reorder/select track, layer z-order

- [ ] **Step 1: Test track row geometry/hit testing independently of Compose.**
- [ ] **Step 2: Add vertical track rendering without changing legacy clip timing math.**
- [ ] **Step 3: Add track controls and overlay placement.**
- [ ] **Step 4: Regression test selection/trim/split/move on legacy track.**
- [ ] **Step 5: Build/device test/commit.**

### Task 6: Waveform/audio controls

**Files:**
- Create waveform cache/generator and UI components
- Modify audio inspector/session actions
- Add cache and envelope tests

**Interfaces:**
- Produces: waveform, fade, volume, mute/solo

- [ ] **Step 1: Test waveform cache key and bounded memory behavior.**
- [ ] **Step 2: Test fade/envelope math at start/middle/end.**
- [ ] **Step 3: Implement background waveform generation.**
- [ ] **Step 4: Integrate controls and preview/export audio path.**
- [ ] **Step 5: Build/regression/commit.**

### Task 7: Export profiles and adaptive preview

**Files:**
- Extend media/export overrides after source audit
- Add export profile tests

**Interfaces:**
- Produces: validated resolution/FPS/bitrate/codec profile and preview quality tier

- [ ] **Step 1: Test invalid/unsupported profile rejection/fallback.**
- [ ] **Step 2: Test H.264 baseline profile and no-source/zero-duration failure.**
- [ ] **Step 3: Add settings UI.**
- [ ] **Step 4: Test cancellation/temp cleanup/storage failure.**
- [ ] **Step 5: Build/device export/commit.**

### Task 8: Text/overlay/transition minimum professional set

**Files:**
- New layer/transition domain files
- New inspector/timeline UI files
- Renderer/compiler overrides

**Interfaces:**
- Produces: text/image overlays and basic original transitions

- [ ] **Step 1: Model/serialization tests.**
- [ ] **Step 2: Transition boundary/duration tests.**
- [ ] **Step 3: Preview renderer integration.**
- [ ] **Step 4: Export renderer parity test.**
- [ ] **Step 5: Build/regression/commit.**

### Task 9: Keyframe/easing engine

**Files:**
- Create `Animatable`, `Keyframe`, interpolation/easing domain files
- UI curve editor/timeline marker files
- Tests for numeric/vector interpolation

**Interfaces:**
- Produces: deterministic value-at-time API used by renderer/effects/audio

- [ ] **Step 1: Failing tests for hold/linear/ease and exact boundary values.**
- [ ] **Step 2: Implement typed keyframe interpolation.**
- [ ] **Step 3: Add cubic-Bezier solver with monotonic-time validation.**
- [ ] **Step 4: Integrate transform/opacity/volume.**
- [ ] **Step 5: Build/performance/regression/commit.**

### Task 10: Effect/compositor registry

**Files:**
- Create effect descriptors/parameters/stack
- Preview/export effect adapters
- Mask/blend/chroma modules

**Interfaces:**
- Produces: stable effect ids and keyframeable parameters

- [ ] **Step 1: Registry/schema tests.**
- [ ] **Step 2: Color/basic effect parity tests between preview/export.**
- [ ] **Step 3: Mask/blend tests with known pixel fixtures.**
- [ ] **Step 4: Chroma-key threshold/feather tests.**
- [ ] **Step 5: Build/device/performance/commit.**

### Task 11: Time remap engine

**Files:**
- Create time mapping domain files
- Extend renderer/compiler
- Add reverse/freeze cache logic

**Interfaces:**
- Produces: source-time-at-project-time mapping

- [ ] **Step 1: Constant-speed duration tests.**
- [ ] **Step 2: Piecewise speed-ramp mapping tests.**
- [ ] **Step 3: Reverse and freeze boundary tests.**
- [ ] **Step 4: Integrate preview/export.**
- [ ] **Step 5: Build/regression/commit.**

### Task 12: Editor Command Bus + EditScript

**Files:**
- Create `com.localcut.editor.commands` package
- Add validator/transaction executor/checkpoint code
- Adapt local UI actions incrementally to the same command path

**Interfaces:**
- Produces: typed commands, `EditScript`, `CommandResult`, monotonic project revision

- [ ] **Step 1: Test stale revision rejects with zero mutation.**
- [ ] **Step 2: Test multi-command transaction rollback.**
- [ ] **Step 3: Test complete automated edit is one undo checkpoint.**
- [ ] **Step 4: Add deterministic “images + duration + zoom + audio + transitions” template.**
- [ ] **Step 5: Build/regression/commit.**

### Task 13: Bridge A — secure read-only agent

**Files:**
- Create packages defined in Bridge V1 spec
- Add Android bridge settings/status UI
- Add relay service in a separate deployable project/module when execution begins

**Interfaces:**
- Consumes: Editor Command Bus/state snapshots
- Produces: pairing, WSS connection and read tools

- [ ] **Step 1: Test device-key abstraction with fake Keystore implementation.**
- [ ] **Step 2: Test pairing challenge/signature flow.**
- [ ] **Step 3: Test offline/reconnect/expired session behavior.**
- [ ] **Step 4: Implement bounded state/preview/error tools.**
- [ ] **Step 5: Security review + build + commit.**

### Task 14: Bridge B — write commands and export jobs

**Files:**
- Extend bridge protocol/agent/relay/controller
- Add idempotency and revision caches/tests

**Interfaces:**
- Consumes: Editor Command Bus/EditScript
- Produces: required V1 write tools

- [ ] **Step 1: Test duplicate command id executes once.**
- [ ] **Step 2: Test stale revision, replay, expiry and unauthorized capability all leave project unchanged.**
- [ ] **Step 3: Map local editor commands to bridge tools.**
- [ ] **Step 4: Test export job id/progress/cancel/failure without arbitrary overwrite.**
- [ ] **Step 5: End-to-end paired-device test + commit.**

### Task 15: 2.5D hierarchy

**Files:**
- Create hierarchy/camera scene-domain files
- Extend transform renderer
- Add parent/camera tests

**Interfaces:**
- Produces: groups, parent/null/camera/Z-axis/parallax

- [ ] **Step 1: Parent transform composition tests.**
- [ ] **Step 2: Reparent-without-jump test.**
- [ ] **Step 3: Camera/Z-axis projection tests.**
- [ ] **Step 4: Motion blur quality/performance tests.**
- [ ] **Step 5: Build/regression/commit.**

---

## D. Per-stage verification template

Every implementation commit must report:

- **Input/scenario:** exact project/media/actions tested.
- **Expected:** exact invariant or visible result.
- **Obtained:** actual CI/device output.
- **Status:** PASS/FAIL.
- **Regression:** import, preview, select, trim, split, move, delete, undo/redo, export.
- **Edge cases:** zero duration, first/last clip, missing asset, unsupported media, low storage, cancellation, repeated command, stale revision as relevant.

Do not call a stage complete from compilation alone.

## E. Current known risks to resolve early

1. **CI test evidence gap:** `TimelineDragPlannerTestMain.kt` is currently a `main()` harness; CI success alone does not prove those named tests executed.
2. **Build-number drift:** a docs-only commit triggered workflow run 13 and produced an APK whose manifest differs from run 12 at version metadata, while the uploaded artifact name still says `build12`.
3. **Fixed override copy list:** adding many new modules will become fragile unless CI overlays the override tree recursively or the source-layout migration is performed later.
4. **Public debug signing material:** the public Build 9 archive contains `localcut-debug.jks`; keep it development-only and create a separate private release signing lineage before public distribution.
5. **Hidden base source in ZIP:** before modifying persistence/renderer internals, obtain a sanitized source audit from the Build 9 archive so exact interfaces are verified rather than guessed.

## F. Next executable stage

Proceed with **Gate 0 / Task 1** first. It is deliberately small: establish trustworthy test execution and correct build/artifact identity without touching editor behavior. Once CI proves that gate, continue directly to Task 2, then the usability/multitrack milestones without waiting for a new architectural decision.
