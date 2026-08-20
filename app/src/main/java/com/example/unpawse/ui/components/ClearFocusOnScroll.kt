package com.example.unpawse.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Drops focus — and so the soft keyboard — the moment a scroll starts inside this element.
 *
 * For any list with a search field above it: the keyboard covers roughly the bottom half of the
 * screen, so scrolling to look at the results is the clearest possible statement that the user is
 * done typing. Consumes nothing, so the list scrolls exactly as before.
 */
fun Modifier.clearFocusOnScroll(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            // Pre-scroll, so the keyboard leaves on the same frame as the first drag rather than
            // one frame behind it. Later frames find nothing focused and cost a no-op.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}
