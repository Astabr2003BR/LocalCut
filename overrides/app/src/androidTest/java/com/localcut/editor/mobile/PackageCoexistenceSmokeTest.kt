package com.localcut.editor.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageCoexistenceSmokeTest {
    @Test
    fun localCutPackageAndProviderRemainDistinctFromReferenceEditors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localPackage = context.packageName

        assertEquals("com.localcut.editor", localPackage)
        assertNotEquals(CAPCUT_PACKAGE, localPackage)
        assertNotEquals(ALIGHT_MOTION_PACKAGE, localPackage)

        val providerAuthority = "$localPackage.remote-media"
        val provider = context.packageManager.resolveContentProvider(providerAuthority, 0)
        assertNotNull("LocalCut private media provider must resolve", provider)
        requireNotNull(provider)
        assertEquals(localPackage, provider.packageName)
        assertFalse("LocalCut private media provider must not be exported", provider.exported)
        assertNotEquals("$CAPCUT_PACKAGE.remote-media", providerAuthority)
        assertNotEquals("$ALIGHT_MOTION_PACKAGE.remote-media", providerAuthority)
    }

    private companion object {
        const val CAPCUT_PACKAGE = "com.lemon.lvoverseas"
        const val ALIGHT_MOTION_PACKAGE = "com.alightcreative.motion"
    }
}
