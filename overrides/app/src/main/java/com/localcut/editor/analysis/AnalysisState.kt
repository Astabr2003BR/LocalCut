package com.localcut.editor.analysis

data class ClipAnalysisState(
    val tracking: TrackingData? = null,
    val stabilization: StabilizationData? = null,
) {
    fun validationIssues(): List<String> = buildList {
        tracking?.let { addAll(it.validationIssues()) }
        stabilization?.let { addAll(it.validationIssues()) }
    }
}

data class ProjectAnalysisState(
    val autoReframe: AutoReframeData? = null,
) {
    fun validationIssues(): List<String> = buildList {
        autoReframe?.let { addAll(it.validationIssues()) }
    }
}
