# LocalCut APK gate — nullability test fix

Date: 2026-10-29
Branch: `feature/localcut-bridge-v1`
PR: #13 (must remain OPEN + DRAFT)

## Root cause

Android Debug APK run #1565 reached `assembleDebug` successfully, then failed in `:app:compileDebugUnitTestKotlin` because `CompositionRenderResolverTest.cameraCutBaselineUsesCameraActiveAtClipStart` passed a nullable `Double?` (`resolved.container?.x`) to the primitive `assertEquals(Double, Double, Double)` overload.

## Correction

Commit `97843552a2b7b62b000852ef71ac4f03a821fbc5` changes only the test: it resolves the optional camera container with `requireNotNull(resolved.container)` before asserting `container.x`.

Production renderer, camera projection, APK signing, secrets and build pipeline were not changed.

## Gate

The next Android Debug APK workflow for this PR is the authoritative verification target. Required before calling the APK validated:

- `testDebugUnitTest` passes;
- `assembleDebug` passes;
- regression gates pass;
- debug APK artifact is uploaded;
- artifact is downloaded and checked before delivery.

No merge to `main` and no Play Store publication are authorized.
