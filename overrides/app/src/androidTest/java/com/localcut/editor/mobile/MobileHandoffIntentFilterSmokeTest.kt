package com.localcut.editor.mobile

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileHandoffIntentFilterSmokeTest {
    @Test
    fun customSchemeResolvesToMobileHandoffActivity() {
        assertResolves(
            "localcut://command?action=undo",
        )
    }

    @Test
    fun httpsHandoffResolvesToMobileHandoffActivity() {
        assertResolves(
            "https://localcut-bridge-v1.onrender.com/open?action=undo",
        )
    }

    @Test
    fun androidSharedMediaActuallyImportsIntoTimeline() {
        val smoke = SharedMediaImportSmokeTest()
        smoke.importsOneSharedImageOnColdStart()
        smoke.warmActivityImportsOnlyTheNewShareOnce()
    }

    private fun assertResolves(uri: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(context.packageName)
        }
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertNotNull("handoff intent must resolve inside LocalCut", resolved)
        assertEquals(
            "com.localcut.editor.mobile.MobileHandoffActivity",
            resolved?.activityInfo?.name,
        )
    }
}
