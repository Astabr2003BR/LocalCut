package com.localcut.editor.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCoordinatorTest {
    @Test
    fun missingProviderFailsGracefully() {
        val coordinator = AnalysisCoordinator(provider = null)

        val result = coordinator.track(
            AnalysisRequest("clip-1", 1_000_000L),
            TrackingTarget(TrackingTargetKind.OBJECT, 0.5, 0.5),
            AnalysisCancellationSignal { false },
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisUnavailableException)
    }

    @Test
    fun cancelledRequestDoesNotInvokeProvider() {
        var calls = 0
        val provider = object : MediaAnalysisProvider {
            override val id: String = "fake"
            override fun track(request: AnalysisRequest, target: TrackingTarget, cancellation: AnalysisCancellationSignal): Result<TrackingData> {
                calls++
                return Result.success(TrackingData(target, listOf(TrackingSample(0L, 0.5, 0.5))))
            }
            override fun stabilize(request: AnalysisRequest, cancellation: AnalysisCancellationSignal): Result<StabilizationData> = error("unused")
            override fun reframe(request: AnalysisRequest, aspectWidth: Int, aspectHeight: Int, cancellation: AnalysisCancellationSignal): Result<AutoReframeData> = error("unused")
        }
        val coordinator = AnalysisCoordinator(provider)

        val result = coordinator.track(
            AnalysisRequest("clip-1", 1_000_000L),
            TrackingTarget(TrackingTargetKind.POINT, 0.5, 0.5),
            AnalysisCancellationSignal { true },
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisCancelledException)
        assertEquals(0, calls)
    }

    @Test
    fun invalidProviderResultIsRejected() {
        val provider = object : MediaAnalysisProvider {
            override val id: String = "broken"
            override fun track(request: AnalysisRequest, target: TrackingTarget, cancellation: AnalysisCancellationSignal): Result<TrackingData> =
                Result.success(TrackingData(target, listOf(TrackingSample(0L, 2.0, 0.5))))
            override fun stabilize(request: AnalysisRequest, cancellation: AnalysisCancellationSignal): Result<StabilizationData> = error("unused")
            override fun reframe(request: AnalysisRequest, aspectWidth: Int, aspectHeight: Int, cancellation: AnalysisCancellationSignal): Result<AutoReframeData> = error("unused")
        }

        val result = AnalysisCoordinator(provider).track(
            AnalysisRequest("clip-1", 1_000_000L),
            TrackingTarget(TrackingTargetKind.OBJECT, 0.5, 0.5),
            AnalysisCancellationSignal { false },
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidAnalysisResultException)
    }
}
