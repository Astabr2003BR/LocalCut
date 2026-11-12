# LocalCut Character + Scene Animation Engine — Design

Date: 2026-11-03
Branch: `feature/localcut-bridge-v1`
PR: #14 — keep OPEN + DRAFT; no merge/release without explicit authorization.

## Goal
Add a deterministic, Android-first animation orchestration layer that lets LocalCut animate characters and scenes using the existing timeline/property-animation renderer. The feature must cover speech/lip motion, idle/blink, walking, crouching/standing, object pickup/put-down, entering/exiting, pointing, background/camera motion, and scene changes.

## Non-goals / truth boundary
- A single flattened image cannot become a physically correct articulated human automatically. Flat characters receive deterministic transform-based fallbacks.
- Articulated limb motion requires separately imported layer clips (head/eyes/mouth/arms/legs/prop) or pose assets.
- This change does not add a cloud generative-video dependency.
- This change does not bypass local import confirmation, track locks, revision checks, undo/redo, or export validation.

## Architecture
High-level character/scene actions compile into existing `PropertyAnimation` tracks (`transform.x`, `transform.y`, `transform.scale`, `transform.rotationDeg`, `transform.opacity`). This keeps preview/export on the already validated path and keeps project persistence backward compatible because no new mandatory persisted model fields are required.

### Character rig binding
A runtime/action request can bind optional clip IDs:
- body (required)
- head
- eyes
- mouth
- left/right arm
- left/right leg
- held object/prop

Every referenced clip must exist. Missing optional parts are ignored only when the action has a flat fallback. Actions that explicitly require a target object reject a missing target.

### Actions
- `IDLE`: breathing/head bob using small Y/scale/rotation motion.
- `TALK`: body micro-motion plus optional mouth clip motion driven by lip cues.
- `WALK`: body X travel + vertical bob; optional leg/arm counter-motion.
- `CROUCH`: body moves down and slightly scales; optional limbs/head receive complementary motion.
- `STAND`: reverse crouch.
- `PICK_UP`: approach/crouch/reach plus target-object motion to the hand/character; requires target object clip.
- `PUT_DOWN`: reverse object handoff to a requested target point.
- `POINT`: arm motion when rigged, otherwise subtle body/head aim fallback.
- `ENTER` / `EXIT`: position + opacity orchestration.

### Lip sync
`LipSyncCue(offsetUs, durationUs, openness)` is deterministic and local. Cues must be sorted, non-overlapping, inside action duration, and openness must be `0..1`. The planner creates a closed/rest mouth state around cues and scales/moves the optional mouth clip. Silence stays closed. Automatic phoneme extraction from raw audio is a separate analysis source; the animation engine consumes cues and does not pretend to infer speech without analysis.

### Scene/camera
- Background pan/zoom uses the same transform property animation on a background clip.
- Scene changes cross-fade an outgoing and incoming background clip.
- Existing composition camera nodes/cuts remain supported; this feature does not replace them.
- Parallax is produced by different X/Y/scale amplitudes on separate background layers.

## Flat vs rigged behavior
### Flat mode
Works with one body clip:
- idle, talk micro-motion, walk translation/bob, crouch/stand, enter/exit, point fallback.
- pickup/put-down can move a separate object clip, but arm articulation is unavailable.

### Rigged mode
If optional body-part clips are provided, the planner adds complementary transforms for mouth/eyes/head/arms/legs and held props.

## Mutation model
A single atomic editor command applies generated property animations to all affected clips. It:
- enforces track locks;
- validates every clip exists exactly once;
- preserves unrelated property animations;
- replaces only properties owned by the requested action plan;
- runs `TimelineValidator` before commit;
- provides a full inverse for undo/redo.

## Security/integrity invariants
- No arbitrary code/shell execution.
- No network requirement for core animation.
- No NaN/Infinity or negative durations.
- All offsets must stay inside the owning clip.
- Duplicate property/keyframe timestamps are rejected or deterministically merged by the planner.
- Bridge writes require project revision and existing authorization.
- Track locks are authoritative.
- Existing project files with no animation action data remain valid.

## Bridge contract
Add a typed `animate_character` capability. Payload includes action, body clip, optional rig part IDs, action timing/intensity/direction/target, and optional lip cues. The executor compiles the action, applies it atomically, and returns affected clip/property counts. A second typed `animate_scene` capability handles pan/zoom/parallax/crossfade requests.

## Acceptance criteria
1. Flat character presets produce deterministic valid property animations.
2. Rigged presets add optional part motion only when bindings exist.
3. Lip cues generate closed/rest states and mouth motion without overlap or out-of-range timestamps.
4. Walk/crouch/stand/point/enter/exit produce finite in-clip keyframes.
5. Pickup/put-down reject a missing object and animate the object when valid.
6. Scene pan/zoom and crossfade are deterministic and finite.
7. Applying a plan is atomic, lock-aware, undoable, and preserves unrelated animations.
8. Bridge commands require expected revision and reject invalid/missing clip IDs.
9. Existing animation/timeline/preview/export regression remains green.
10. Android Debug APK and Android Multitrack Export Smoke pass before declaring complete.
