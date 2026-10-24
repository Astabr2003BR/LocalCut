package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier

/**
 * Compatibility shim for the Compose version used by LocalCut.
 *
 * Timeline children live inside fixed-size clip boxes, so fillMaxSize() preserves
 * the intended match-parent rendering without changing the timeline model.
 */
fun Modifier.matchParentSize(): Modifier = fillMaxSize()
