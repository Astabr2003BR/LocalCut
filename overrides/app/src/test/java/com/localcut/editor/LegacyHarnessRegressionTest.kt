package com.localcut.editor

import java.lang.reflect.InvocationTargetException
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LegacyHarnessRegressionTest(
    private val harnessClassName: String,
) {
    @Test
    fun legacyHarnessPasses() {
        val harnessClass = Class.forName(harnessClassName)
        val main = harnessClass.methods.firstOrNull { method ->
            method.name == "main" && method.parameterCount == 0
        } ?: harnessClass.methods.firstOrNull { method ->
            method.name == "main" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isArray
        } ?: fail("No main() entry point found in $harnessClassName").let { return }

        try {
            if (main.parameterCount == 0) {
                main.invoke(null)
            } else {
                main.invoke(null, emptyArray<String>() as Any)
            }
        } catch (error: InvocationTargetException) {
            val cause = error.targetException ?: error
            throw AssertionError("Legacy harness failed: $harnessClassName", cause)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun harnesses(): Collection<Array<String>> = listOf(
            "com.localcut.editor.acceptance.V0HostAcceptanceTestMainKt",
            "com.localcut.editor.data.assets.AssetImportPolicyTestMainKt",
            "com.localcut.editor.data.assets.ImportedClipFactoryTestMainKt",
            "com.localcut.editor.data.project.AtomicProjectStoreTestMainKt",
            "com.localcut.editor.domain.history.HistoryManagerTestMainKt",
            "com.localcut.editor.domain.timeline.CoreTimelineTestMainKt",
            "com.localcut.editor.domain.timeline.TimelineMathTestMainKt",
            "com.localcut.editor.domain.timeline.TimelineSlicerTestMainKt",
            "com.localcut.editor.media.ExportLifecycleCoordinatorTestMainKt",
            "com.localcut.editor.media.ExportTransactionTestMainKt",
            "com.localcut.editor.media.FinalPreviewPlannerTestMainKt",
            "com.localcut.editor.media.FinalPreviewRendererTestMainKt",
            "com.localcut.editor.media.Media3CompositionBlueprintTestMainKt",
            "com.localcut.editor.media.PreviewControllerTestMainKt",
            "com.localcut.editor.media.PreviewTransportTestMainKt",
            "com.localcut.editor.media.RenderPlanCompilerTestMainKt",
            "com.localcut.editor.media.TransformInterpolatorTestMainKt",
            "com.localcut.editor.session.DebouncedProjectSaverTestMainKt",
            "com.localcut.editor.session.EditorSessionActionsTestMainKt",
            "com.localcut.editor.session.ProjectSessionTestMainKt",
            "com.localcut.editor.ui.editor.EditorViewModelTestMainKt",
            "com.localcut.editor.ui.editor.TimelineGeometryTestMainKt",
            "com.localcut.editor.ui.editor.TimelineHitTesterTestMainKt",
        ).map { arrayOf(it) }
    }
}
