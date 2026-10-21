# LocalCut checkpoint — Task 9 keyframes

DATE: 2026-10-21
PROJECT: LocalCut
BRANCH: feature/keyframe-editor
PR: #12
GOAL: mobile keyframe editor + preview/export parity.

## Implemented
- Reused existing TransformKeyframe/TransformValue engine, SetKeyframesCommand, persistence and trim/split remapping.
- Added KeyframeEditorPolicy for project-time ↔ clip-local time, deterministic upsert/replace, delete, previous/next navigation and normalized timeline markers.
- Inspector controls for X/Y, scale, rotation and opacity at the playhead.
- Add/Update/Delete keyframe actions route through EditorAction.SetSelectedKeyframes, preserving HistoryManager undo/redo/autosave.
- Previous/Next keyframe seek controls.
- Keyframe markers drawn inside timeline clips.
- Media3 transform evaluation now converts sequence/composition presentation timestamp to item-local time using cumulative presentationStartUs.
- Added KeyframeRenderResolver and effective opacity policy.
- Added Media3SequenceOpacityResolver and time-dependent VideoCompositorSettings alpha, preserving default compositor for projects without alpha changes.
- Added real Android 35 MP4 smoke for a second visual layer whose opacity animates after a 1-second offset.

## TDD / verification evidence
- KeyframeEditorPolicy RED: Android Debug APK #201 failed because KeyframeEditorPolicy was absent.
- KeyframeEditorPolicy GREEN: Android Debug APK #203 PASS.
- Renderer local-time RED: KeyframeRenderResolver test committed before implementation.
- Media3 local-time implementation: commit d78c95c223230ecedb1ca536646aaec18063ea15.
- Animated opacity RED: Media3SequenceOpacityResolver test committed before implementation.
- Media3 opacity implementation compiled and preserved baseline export: Android Debug APK #207 PASS + Android smoke #84 PASS.
- Timeline marker RED preceded markerFractions implementation.
- Final UI + keyframe smoke HEAD: 02754772c86e6d90ecb52d5f71a25bd23b95f9b8.
- FINAL_BUILD: Android Debug APK #214 PASS; artifact localcut-debug-apk-v0.2.214-code214, SHA256 78b836ccf6fc022ac5b6f7ec1d98924c9a7c4f3b67038e259d125068aa741cf5.
- FINAL_SMOKE: Android 35 Multitrack Export Smoke #91 PASS; legacy z-order/text/fade checks plus KeyframeExportSmokeTest all passed on actual exported MP4.
- FINAL_APK CANDIDATE: v0.2.214 code214 from build #214; actual APK SHA256 `10e5f94e649779f2d63e88cdc38bb557aab64c2bd25a067ca4627b9c3c39916f`.

## Known limitations / risks
- No manual physical-device UX/performance pass in this task.
- Keyframe interpolation remains linear; easing/bezier curves are not added in Task 9.
- Keyframes currently animate transform X/Y, scale, rotation and opacity; no per-effect parameter curves yet.
- Timeline markers are display/navigation aids; direct marker dragging is not added in this task.

## Next action
Task 9 code is validated. Merge PR #12 into main, run the post-merge Android build/smoke, and deliver the APK generated from main.
