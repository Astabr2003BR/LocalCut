package com.localcut.editor.analysis

data class AnalysisRequest(
    val clipId: String,
    val durationUs: Long,
) {
    fun validationIssues(): List<String> = buildList {
        if (clipId.isBlank()) add("analysis clip id must not be blank")
        if (durationUs <= 0L) add("analysis duration must be positive")
    }
}

fun interface AnalysisCancellationSignal {
    fun isCancelled(): Boolean
}

interface MediaAnalysisProvider {
    val id: String

    fun track(
        request: AnalysisRequest,
        target: TrackingTarget,
        cancellation: AnalysisCancellationSignal,
    ): Result<TrackingData>

    fun stabilize(
        request: AnalysisRequest,
        cancellation: AnalysisCancellationSignal,
    ): Result<StabilizationData>

    fun reframe(
        request: AnalysisRequest,
        aspectWidth: Int,
        aspectHeight: Int,
        cancellation: AnalysisCancellationSignal,
    ): Result<AutoReframeData>
}

class AnalysisUnavailableException : IllegalStateException("analysis provider is not configured")
class AnalysisCancelledException : IllegalStateException("analysis cancelled")
class InvalidAnalysisResultException(message: String) : IllegalStateException(message)

class AnalysisCoordinator(
    private val provider: MediaAnalysisProvider?,
) {
    fun track(
        request: AnalysisRequest,
        target: TrackingTarget,
        cancellation: AnalysisCancellationSignal,
    ): Result<TrackingData> = runAnalysis(
        request = request,
        cancellation = cancellation,
        execute = { activeProvider -> activeProvider.track(request, target, cancellation) },
        validate = TrackingData::validationIssues,
    ).map { data -> data.copy(sourceAnalyzerId = provider?.id) }

    fun stabilize(
        request: AnalysisRequest,
        cancellation: AnalysisCancellationSignal,
    ): Result<StabilizationData> = runAnalysis(
        request = request,
        cancellation = cancellation,
        execute = { activeProvider -> activeProvider.stabilize(request, cancellation) },
        validate = StabilizationData::validationIssues,
    ).map { data -> data.copy(sourceAnalyzerId = provider?.id) }

    fun reframe(
        request: AnalysisRequest,
        aspectWidth: Int,
        aspectHeight: Int,
        cancellation: AnalysisCancellationSignal,
    ): Result<AutoReframeData> {
        if (aspectWidth <= 0 || aspectHeight <= 0) {
            return Result.failure(IllegalArgumentException("reframe aspect must be positive"))
        }
        return runAnalysis(
            request = request,
            cancellation = cancellation,
            execute = { activeProvider -> activeProvider.reframe(request, aspectWidth, aspectHeight, cancellation) },
            validate = AutoReframeData::validationIssues,
        ).map { data -> data.copy(sourceAnalyzerId = provider?.id) }
    }

    private fun <T> runAnalysis(
        request: AnalysisRequest,
        cancellation: AnalysisCancellationSignal,
        execute: (MediaAnalysisProvider) -> Result<T>,
        validate: (T) -> List<String>,
    ): Result<T> {
        val requestIssue = request.validationIssues().firstOrNull()
        if (requestIssue != null) return Result.failure(IllegalArgumentException(requestIssue))
        if (cancellation.isCancelled()) return Result.failure(AnalysisCancelledException())
        val activeProvider = provider ?: return Result.failure(AnalysisUnavailableException())
        if (activeProvider.id.isBlank()) return Result.failure(AnalysisUnavailableException())

        val raw = runCatching { execute(activeProvider).getOrThrow() }
        if (raw.isFailure) return raw
        if (cancellation.isCancelled()) return Result.failure(AnalysisCancelledException())
        val value = raw.getOrThrow()
        val resultIssue = validate(value).firstOrNull()
        return if (resultIssue == null) {
            Result.success(value)
        } else {
            Result.failure(InvalidAnalysisResultException(resultIssue))
        }
    }
}
