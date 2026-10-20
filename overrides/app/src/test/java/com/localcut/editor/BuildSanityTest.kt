package com.localcut.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSanityTest {
    @Test
    fun projectSchemaVersionIsPinned() {
        assertEquals(3, BuildContract.PROJECT_SCHEMA_VERSION)
    }
}
