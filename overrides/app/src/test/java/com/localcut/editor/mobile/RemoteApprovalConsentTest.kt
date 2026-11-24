package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteApprovalConsentTest {
    @Test fun `no decision does not authorize`() {
        var approvals = 0
        RemoteApprovalConsent(onAccept = { approvals++ }, onReject = {})
        assertEquals(0, approvals)
    }

    @Test fun `cancel prevents later approval and calls reject once`() {
        var approvals = 0
        var rejections = 0
        val consent = RemoteApprovalConsent({ approvals++ }, { rejections++ })
        consent.reject()
        consent.reject()
        consent.accept()
        assertEquals(0, approvals)
        assertEquals(1, rejections)
    }

    @Test fun `explicit acceptance authorizes only once`() {
        var approvals = 0
        var rejections = 0
        val consent = RemoteApprovalConsent({ approvals++ }, { rejections++ })
        consent.accept()
        consent.accept()
        consent.reject()
        assertEquals(1, approvals)
        assertEquals(0, rejections)
    }
}
