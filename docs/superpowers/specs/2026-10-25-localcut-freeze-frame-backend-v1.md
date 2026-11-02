# LocalCut Freeze / Frame Hold Backend v1

## Intent
Add real non-AI freeze/frame-hold rendering to LocalCut without changing the saved project schema, without faking reverse with negative speed, and without regressing preview/export timing, keyframes, transitions or audio.

## Scope
- Support `TimeMap.freezeSegments` for VIDEO clips in Media3 preview/export.
- Keep `TimeMap.reverse` unsupported in this block.
- Keep freeze + speed-ramp unsupported in this block.
- Keep freeze + audio fade-in/fade-out unsupported in this block.
- Do not add large UI changes.
- Preserve `overrides/`, current APK signing, Timeline v1, preview black-frame fixes and PR #13 Draft state.

## Chosen approach
Decompose one logical video clip internally into sequential Media3 pieces:

`moving source -> video-only hold -> moving source`

The saved `Clip` and `TimeMap` remain unchanged. The decomposition exists only in the Media3 compilation backend.

### Moving pieces
- Map presentation boundaries back to source offsets with `TimeMapEvaluator`.
- Use a positive constant `SpeedProvider` derived from source delta / presentation delta.
- Preserve the original output tracks.

### Hold pieces
- Select a bounded source-frame window around `FreezeSegment.sourceOffsetUs`.
- Stretch that positive-duration source window to the requested hold duration with a positive speed.
- Remove audio from the hold piece so the held video does not repeat or stretch source audio.
- Never use zero/negative speed.

### Timing semantics
- Each piece carries its logical clip-local presentation start.
- Transform/keyframe effects use the original logical clip clock, so animations do not restart at each internal piece.
- Fade-to-black transition effects use the original logical clip start and duration.
- The original blueprint remains unchanged so sequence-level opacity/compositor semantics continue to use the logical clip envelope.

## Safety / rejection rules
Freeze v1 is renderable only when:
- asset type is VIDEO;
- `TimeMap` is valid;
- at least one freeze segment exists;
- `reverse == false`;
- `speedPoints` is empty;
- audio fade-in and fade-out are both zero.

Unsupported combinations must continue failing with `UNSUPPORTED_TIME_MAP`; they must not silently degrade.

## Validation
1. Unit contract: piece boundaries, exact total presentation duration, video-only hold, source-end edge case, rejection rules and policy behavior.
2. Android instrumentation: export a multi-color baseline MP4, apply one freeze, verify output duration and representative frames before/during/after the hold.
3. Consolidated Android unit/build gate and existing instrumentation regression suite.
4. Physical-phone validation remains separate from CI/emulator validation.

## Reverse
Reverse is explicitly outside this v1. Media3 `EditedMediaItem` speed APIs require positive speed. A later reverse backend must use a dedicated safe decode/re-encode/time-remap strategy rather than a negative-speed hack.
