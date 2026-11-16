package com.localcut.editor.data.project

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.domain.model.Project
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectStartupRecoveryAndroidTest {
    @Test
    fun appPrivateFilesDirectoryRecoverySurvivesReselectionAfterSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectsRoot = context.filesDir.toPath()
            .resolve("startup-recovery-smoke-${System.nanoTime()}")
        try {
            val primaryDir = projectsRoot.resolve("default")
            val primaryStore = AtomicProjectStore(primaryDir)
            primaryStore.save(Project.create("primary-before-corruption", 1080, 1920, 30)).getOrThrow()
            val corruptPrimary = "{truncated-primary-snapshot"
            Files.writeString(primaryDir.resolve("project.json"), corruptPrimary)

            val firstSelection = ProjectStartupRecovery.select(projectsRoot)
            assertNotNull("corrupt primary must produce a visible recovery warning", firstSelection.warning)
            assertTrue(
                "recovery directory must remain inside app-private filesDir",
                firstSelection.directory.startsWith(context.filesDir.toPath()),
            )
            assertEquals(null, firstSelection.project)

            val savedRecovery = Project.create("android-private-recovery", 1080, 1920, 30)
            firstSelection.store.save(savedRecovery).getOrThrow()

            // A second selection models a fresh process choosing its persisted store again.
            val reopened = ProjectStartupRecovery.select(projectsRoot)
            assertEquals(firstSelection.directory, reopened.directory)
            assertEquals(savedRecovery, reopened.project)
            assertEquals(corruptPrimary, Files.readString(primaryDir.resolve("project.json")))
        } finally {
            projectsRoot.toFile().deleteRecursively()
        }
    }
}
