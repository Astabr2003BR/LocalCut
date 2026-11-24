package com.localcut.editor.mobile

/** A remote authorization may be decided at most once and is never implicit. */
internal class RemoteApprovalConsent(
    private val onAccept: () -> Unit,
    private val onReject: () -> Unit,
) {
    private var decided = false

    fun accept() {
        if (decided) return
        decided = true
        onAccept()
    }

    fun reject() {
        if (decided) return
        decided = true
        onReject()
    }
}
