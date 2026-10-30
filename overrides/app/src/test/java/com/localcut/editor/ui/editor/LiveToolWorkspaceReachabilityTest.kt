package com.localcut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class LiveToolWorkspaceReachabilityTest {
    @Test
    fun everyLivePrimaryToolIsReachableFromAtLeastOneWorkspaceForEachSupportedSelection() {
        EditorToolCatalog.all
            .filter { it.primary && it.enabled }
            .forEach { tool ->
                tool.selections.forEach { selection ->
                    val reachable = EditorWorkspace.entries.any { workspace ->
                        EditorWorkspacePolicy.toolsFor(workspace, selection)
                            .any { candidate -> candidate.id == tool.id }
                    }
                    assertTrue(
                        "${tool.id} is LIVE but unreachable for $selection",
                        reachable,
                    )
                }
            }
    }
}
