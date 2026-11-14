package com.localcut.editor.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticVideoRecipeSmokeTest {
    @Test fun parsesStrictRecipeOnAndroid() {
        val plan = AutomaticVideoRecipe.parse("""{"version":1,"format":"4:5","scenes":[{"image":2,"durationMs":2500},{"image":1,"durationMs":5000}]}""", 2)
        assertEquals(1350, plan.height)
        assertEquals(7_500_000L, plan.durationUs)
        assertEquals(listOf(1, 0), plan.scenes.map { it.imageIndex })
        assertEquals(listOf(0L, 2_500_000L), plan.scenes.map { it.startUs })
    }

    @Test fun rejectsMalformedDuplicateAndExecutableFields() {
        val bad = listOf(
            "",
            """{"version":2,"format":"4:5","scenes":[{"image":1,"durationMs":1000}]}""",
            """{"version":1,"version":1,"format":"4:5","scenes":[{"image":1,"durationMs":1000}]}""",
            """{"version":1,"format":"4:5","scenes":[{"image":1,"durationMs":"1000"}]}""",
            """{"version":1,"format":"4:5","scenes":[{"image":1,"durationMs":1000,"url":"https://example.com"}]}""",
            """{"version":1,"format":"4:5","scenes":[{"image":3,"durationMs":1000}]}""",
            """{"version":1,"format":"4:5","scenes":[{"image":1,"durationMs":1000}]} {}""",
            "x".repeat(AutomaticVideoRecipe.MAX_CHARS + 1),
        )
        bad.forEach { assertTrue("Should reject recipe: " + it.take(100), runCatching { AutomaticVideoRecipe.parse(it, 2) }.isFailure) }
    }
}
