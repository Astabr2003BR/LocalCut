package com.localcut.editor.bridge.agent

import android.content.Context
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.required
import com.localcut.editor.ui.editor.EditorAiReviewSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal enum class ReviewHistoryStatus { RECEIVED, LOADED, APPLIED_IN_EDITOR, DISCARDED, REJECTED, SUPERSEDED, INTERRUPTED }
internal data class ReviewHistoryEntry(val proposalId: String, val source: EditorAiReviewSource,
    val status: ReviewHistoryStatus, val resultRevision: Long = -1L)
internal data class ReviewHistoryView(val entries: List<ReviewHistoryEntry> = emptyList(), val storageOk: Boolean = true)
internal interface ReviewHistoryStorage {
    fun read(): String?
    fun write(value: String): Boolean
}
internal class AndroidReviewHistoryStorage(context: Context, preferencesName: String = "localcut_review_history_v1") : ReviewHistoryStorage {
    private val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("history", null)
    override fun write(value: String): Boolean = prefs.edit().putString("history", value).commit()
}

/** Metadata-only history: never stores plans, findings, media, URIs or consent. */
internal class BridgeReviewHistory(private val storage: ReviewHistoryStorage) {
    private val mutableView = MutableStateFlow(ReviewHistoryView())
    val view: StateFlow<ReviewHistoryView> = mutableView.asStateFlow()
    private var writable = true
    init {
        val loaded = runCatching { storage.read()?.let(::decode).orEmpty() }
        if (loaded.isFailure) {
            writable = false // do not overwrite corrupt/unreadable storage
            mutableView.value = ReviewHistoryView(storageOk = false)
        } else {
            val rows = loaded.getOrThrow()
            val recovered = rows.map {
                if (it.status == ReviewHistoryStatus.RECEIVED || it.status == ReviewHistoryStatus.LOADED)
                    it.copy(status = ReviewHistoryStatus.INTERRUPTED) else it
            }
            mutableView.value = ReviewHistoryView(rows)
            if (recovered != rows) persist(recovered)
        }
    }
    @Synchronized
    fun received(proposal: BridgeReviewProposal) {
        val rows = mutableView.value.entries
        if (rows.any { it.proposalId == proposal.id }) return
        val entry = ReviewHistoryEntry(proposal.id, proposal.source, ReviewHistoryStatus.RECEIVED)
        if (valid(entry)) persist((listOf(entry) + rows).take(64))
    }
    @Synchronized
    fun transition(id: String, status: ReviewHistoryStatus, resultRevision: Long = -1L) {
        val rows = mutableView.value.entries
        val old = rows.firstOrNull { it.proposalId == id } ?: return
        val allowed = when (old.status) {
            ReviewHistoryStatus.RECEIVED -> status in setOf(ReviewHistoryStatus.LOADED, ReviewHistoryStatus.DISCARDED, ReviewHistoryStatus.SUPERSEDED)
            ReviewHistoryStatus.LOADED -> status in setOf(ReviewHistoryStatus.APPLIED_IN_EDITOR, ReviewHistoryStatus.DISCARDED,
                ReviewHistoryStatus.REJECTED, ReviewHistoryStatus.SUPERSEDED)
            else -> false
        }
        val updated = old.copy(status = status, resultRevision = resultRevision)
        if (allowed && valid(updated)) persist(listOf(updated) + rows.filterNot { it.proposalId == id })
    }
    @Synchronized
    fun supersedePending() {
        val rows = mutableView.value.entries
        val updated = rows.map {
            if (it.status == ReviewHistoryStatus.RECEIVED || it.status == ReviewHistoryStatus.LOADED)
                it.copy(status = ReviewHistoryStatus.SUPERSEDED) else it
        }
        if (updated != rows) persist(updated)
    }
    private fun persist(rows: List<ReviewHistoryEntry>) {
        val ok = writable && runCatching { storage.write(encode(rows)) }.getOrDefault(false)
        mutableView.value = ReviewHistoryView(rows, ok)
    }
    private fun encode(rows: List<ReviewHistoryEntry>): String = rows.joinToString(prefix = "[", postfix = "]", separator = ",") {
        buildString {
            append("{\"id\":"); append(jsonString(it.proposalId))
            append(",\"projectId\":"); append(jsonString(it.source.projectId))
            append(",\"revision\":"); append(it.source.revision)
            append(",\"jobId\":"); append(jsonString(it.source.jobId))
            append(",\"sha256\":"); append(jsonString(it.source.sha256))
            append(",\"status\":"); append(jsonString(it.status.name))
            append(",\"resultRevision\":"); append(it.resultRevision); append('}')
        }
    }
    private fun decode(raw: String): List<ReviewHistoryEntry> {
        require(raw.length <= 262_144)
        val values = MiniJson.parse(raw).asArray().values
        require(values.size <= 64)
        return values.map { item ->
            val row = item.asObject()
            require(row.fields.keys == setOf("id", "projectId", "revision", "jobId", "sha256", "status", "resultRevision"))
            ReviewHistoryEntry(row.required("id").asString(),
                EditorAiReviewSource(row.required("projectId").asString(), row.required("revision").asLong(),
                    row.required("jobId").asString(), row.required("sha256").asString()),
                ReviewHistoryStatus.valueOf(row.required("status").asString()), row.required("resultRevision").asLong()
            ).also { require(valid(it)) }
        }.also { require(it.map(ReviewHistoryEntry::proposalId).distinct().size == it.size) }
    }
    private fun valid(entry: ReviewHistoryEntry): Boolean {
        val s = entry.source
        return runCatching { UUID.fromString(entry.proposalId).toString() == entry.proposalId }.getOrDefault(false) &&
            s.projectId.isNotBlank() && s.projectId.length <= 256 && s.jobId.isNotBlank() && s.jobId.length <= 128 &&
            s.revision >= 0L && s.sha256.matches(Regex("[a-f0-9]{64}")) &&
            (if (entry.status == ReviewHistoryStatus.APPLIED_IN_EDITOR) entry.resultRevision > s.revision else entry.resultRevision == -1L)
    }
}
