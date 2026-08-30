package com.cairn.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pull down on the thing itself to see what is inside it.
 *
 * This replaces long press everywhere in Cairn. A long press is a timer with no face on it:
 * nothing tells you how long to hold, and nothing distinguishes "still waiting" from "it did not
 * take". A pull shows you it is working from the first pixel, and you can put it back.
 */
fun Modifier.pullable(
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onRelease: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var travelled = 0f
    detectVerticalDragGestures(
        onDragStart = {
            travelled = 0f
            onStart()
        },
        onDragEnd = { onRelease() },
        onDragCancel = { onRelease() }
    ) { change, dragAmount ->
        if (dragAmount > 0f || travelled > 0f) {
            change.consume()
            travelled = (travelled + dragAmount).coerceAtLeast(0f)
            onDelta(travelled)
        }
    }
}

/** Icons should not ripple. The app opening is the feedback. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick
    )
}
