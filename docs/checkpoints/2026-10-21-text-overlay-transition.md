# LocalCut checkpoint — Task 8 text / overlay / transition

DATE: 2026-10-21
PROJECT: LocalCut
BRANCH: feature/text-overlay-transition-foundation
PR: #10
GOAL: complete Task 8 — text/overlay/transition minimum professional set.

## Implemented
- Project schema V3 with serialized TextOverlay and ClipTransition; migration from V1/V2 keeps empty creative-layer defaults.
- Text overlay model, validation, persistence, undo/redo and mobile UI for create/edit/delete.
- Text render in shared Media3 composition compiler, with deterministic timing, z-order, transform/scale and adaptive-preview output scaling.
- Android emulator export smoke verifies text pixels in actual MP4.
- Visual overlay/sticker import from Android media picker at playhead.
- Atomic visual overlay import creates/reuses VISUAL/OVERLAY track and supports undo/redo.
- Overlay placement is clamped to the primary visual timeline rather than extending the project accidentally.
- Transition model validation requires adjacent clips on the same EXCLUSIVE visual track with an exact boundary and valid duration.
- Basic supported transition: FADE_TO_BLACK.
- Fade transition render specs map outgoing clip to fade-out and incoming clip to fade-in.
- Fade envelope rejects overlapping fade-in/fade-out windows inside a clip.
- Media3 RgbMatrix fade effect is timestamp-dependent and shared by preview/export through ProjectCompositionCompiler.
- Transition UI on selected clip offers 0.25 s / 0.5 s / 1 s presets and remove action.
- Transition edits use HistoryManager with add/update/delete undo/redo.
- UI clamps neighboring fades so a middle clip cannot get overlapping transition windows.
- Delete-selected now atomically removes attached transitions; undo restores the exact clip + transition state.
- DISSOLVE and WIPE_LEFT remain fail-explicit until a custom compositor exists; they are not exposed in the editor UI.

## TDD / verification evidence
- Schema/model RED/GREEN cycles: earlier Task 8 CI runs #135/#136 and subsequent validation runs.
- Transition-boundary validation: RED/GREEN runs around #137/#138.
- Text integrity/render cycles and Android 35 export smoke validated in earlier Task 8 runs, including build #160 + smoke #42.
- Visual overlay import integration validated by build #168.
- FadeToBlackEnvelope RED: run #171; GREEN: build #173.
- FadeToBlackEffectFactory RED: build #174; implementation commit 84db73d... and later builds include it.
- Real Android smoke includes legacy multitrack z-order/text tests plus FadeToBlackExportSmokeTest.
- TDD RED for renderer integration: Android smoke #58 failed before compiler integration with outgoing frame rgb=(253,0,0), proving the transition was not yet applied.
- Fade smoke checks both sides of the transition using actual exported MP4 frames: dimmed outgoing red at 0.75 s and dimmed incoming blue at 1.25 s.
- Pre-fix code HEAD c50fa7bbf17f98db42f1d019ecb87dc3c733c15c: Android Debug APK #193 PASS (`testDebugUnitTest assembleDebug`, overlap-safe checks, 23/23 legacy harnesses, APK v0.2.193 code 193).
- Android 35 smoke #74 exposed a real second-item timestamp bug: outgoing red dimmed, but incoming blue at 1.25 s was black (`rgb=(0,0,0)`).
- Root cause: `RgbMatrix.getMatrix()` received sequence/composition presentation time; the fade envelope evaluated it as if every item started at 0.
- Fix: `FadeToBlackEffectFactory` now receives `presentationStartUs`; `ProjectCompositionCompiler` computes cumulative sequence presentation offsets (including gaps) and evaluates the envelope in item-local time.
- TDD contract `compositionTimestampIsConvertedToItemLocalTime` was committed before the fix.
- Final corrected code HEAD: a6517c8b0fb038ef69a1a225a41744af27fc5902.
- Final Android Debug APK run #196: PASS (`testDebugUnitTest assembleDebug`, overlap-safe checks, 23/23 legacy harnesses, APK generated).
- Final Android 35 emulator smoke run #77: PASS (legacy multitrack z-order + text export pixels + both sides of FADE_TO_BLACK in actual MP4).

## Known limitations / risks
- No manual UX/performance pass on a physical Android device in this task.
- DISSOLVE and WIPE_LEFT are modeled/serialized but deliberately not renderable or exposed; Media3 Composition does not provide the required native crossfade path for this implementation.
- Stroke/shadow fields exist in the text model but this task does not claim full stroke/shadow renderer parity.
- Moving/trimming/splitting a clip can invalidate an existing transition boundary and is rejected by validation; removal/re-application of the transition may be required before those edits. Delete is handled atomically.
- Fade-to-black is currently a visual transition; this task does not add an automatic audio crossfade.

## Next action
After Task 8 integration decision, continue roadmap Task 9 (keyframes) from current main/stacked base without redoing Task 8.
