# LocalCut Character Animation Risk Remediation

## Goal

Resolve the known animation risks before adding new character or scene capabilities: make TALK useful on a single flat character image, provide deterministic audio-energy-to-mouth-cue planning without blocking the Android main thread, and add an automated Bridge-to-preview/export integration gate. Preserve all validated animation behavior and keep PR #14 open/draft.

## Scope

### 1. Flat-image TALK

A character may be a single visual clip without a separate mouth clip. `animate_character` may therefore accept an optional normalized `mouthRegion`:

- `centerX`, `centerY`: `0.0..1.0`
- `radiusX`: `0.01..0.50`
- `radiusY`: `0.005..0.30`

For `TALK`:

- when `mouthClipId` exists, preserve the existing separate-mouth animation path;
- otherwise, when `mouthRegion` exists, attach/update a localized `localcut.character.mouth_warp` effect on the body clip;
- the mouth-warp `openness` parameter is animated from the same validated lip-sync cues used by the mouth-clip path;
- if no cues are supplied, the existing deterministic local open/close fallback generates the `openness` animation;
- unrelated effects and transform/property animations must be preserved;
- the edit remains atomic and one Undo/Redo operation;
- locked tracks must fail closed before mutation.

The localized warp must affect only the supplied mouth ellipse. It must not claim to infer a skeleton or independently extract limbs from an arbitrary flat image.

### 2. Mouth-warp renderer

Add a renderable effect type `localcut.character.mouth_warp` with:

- `center`: POINT, default `(0.5, 0.65)`;
- `radiusX`: NUMBER, default `0.10`, range `0.01..0.50`;
- `radiusY`: NUMBER, default `0.05`, range `0.005..0.30`;
- `openness`: NUMBER, default `0.0`, range `0.0..1.0`.

Only this effect may carry an animated effect parameter through the Media3 effect stack in this remediation. Existing effect animation policy remains unchanged.

The GL effect evaluates `openness` against presentation time and performs a bounded, continuous vertical mouth-region warp. Sampling must clamp to valid texture coordinates. At `openness=0`, output coordinates are unchanged.

### 3. Audio-energy cue planner

Add a pure planner that converts `WaveformData.peaks` plus action duration into sorted, non-overlapping `LipSyncCue` values.

Requirements:

- deterministic for identical input;
- silence/no meaningful energy => no cues;
- cue openness normalized to `0.0..1.0` using a noise floor and local peak range;
- adjacent active buckets may be merged, but every cue must remain within the action duration;
- malformed/empty input fails safely or returns empty as specified by tests;
- no network and no paid service.

The existing `WaveformRepository` remains the asynchronous decoding boundary. Do not decode media synchronously inside `BridgeCommandExecutor` or on the Android main thread.

This remediation exposes the pure planner and a coordinator interface suitable for background waveform decoding. Full automatic Bridge audio decoding must not block the current synchronous command path; if an audio waveform is not already available to a caller, the caller continues to provide cues or uses the deterministic fallback.

## 4. Bridge payload

`animate_character` accepts optional `mouthRegion` inside its existing generic `payload`. Android validates the region strictly. No new authentication bypass, no invented clip IDs, and `expectedRevision` remains mandatory.

The result must identify whether a flat-image mouth effect was applied without exposing local media URIs.

## 5. Automated E2E gate

Add an Android instrumentation smoke that builds an image-based project and exercises:

`Bridge animate_character(TALK + mouthRegion) -> ProjectSession mutation -> preview/render path -> export path`

Acceptance:

- command succeeds at the expected revision;
- the body clip contains one mouth-warp effect with a valid animated `openness` track;
- preview compilation/rendering accepts the effect;
- export completes on the emulator using the same project state;
- existing multitrack/freeze-frame/Bridge/mobile/creative/export smokes remain green.

This automated emulator gate does not masquerade as a physical-device test. A physical paired-phone E2E can only be claimed when a reachable paired device is actually exercised.

## Non-goals

- automatic skeleton extraction from one arbitrary image;
- AI/ML face or limb segmentation;
- phoneme/viseme speech recognition;
- cloud speech analysis;
- changing existing scene animation semantics;
- merge to `main`, production deploy, Play Store publish, signing changes, or force-push.

## Security and integrity

- Preserve revision checks, replay protection, track locks, history/undo, autosave and project validation.
- Never block the Android main thread on audio decoding.
- Never expose private media paths through Bridge results.
- Do not weaken the current relay, OAuth, pairing or local confirmation controls.

## Acceptance summary

1. Flat image + `mouthRegion` can visibly animate TALK without a separate mouth clip.
2. Existing rigged mouth clips continue to use the old path unchanged.
3. Waveform peaks can be deterministically converted into valid lip cues off the render/Bridge main path.
4. The flat-image mouth effect is preview/export renderable and time-varying.
5. Undo/redo, locks, unrelated effects and existing animation keyframes are preserved.
6. Bridge/controller, Android unit/build, Termux and emulator smoke regressions are green before this remediation is called validated.
