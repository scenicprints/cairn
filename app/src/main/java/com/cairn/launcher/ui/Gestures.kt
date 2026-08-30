package com.cairn.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private enum class TileMode { Tap, Pull, Lift }

/**
 * The whole gesture vocabulary of a tile, decided by direction rather than by duration.
 *
 * Straight down means show me what is inside this. Any other direction means pick this up.
 * Neither one is a long press, because a long press is a timer with no face on it: nothing
 * tells you how long to hold, and nothing distinguishes still waiting from it did not take.
 * Both of these announce themselves at the first pixel and both can be put back.
 */
fun Modifier.tileGestures(
    onTap: () -> Unit,
    onPullStart: () -> Unit,
    onPullDelta: (Float) -> Unit,
    onPullRelease: () -> Unit,
    onLiftStart: (Offset) -> Unit,
    onLiftMove: (Offset) -> Unit,
    onLiftDrop: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var mode = TileMode.Tap

        val crossed = awaitTouchSlopOrCancellation(down.id) { change, over ->
            mode = if (over.y > 0f && abs(over.y) > abs(over.x)) TileMode.Pull else TileMode.Lift
            change.consume()
        }

        if (crossed == null) {
            // The pointer went up without travelling. That is a tap.
            if (mode == TileMode.Tap) onTap()
            return@awaitEachGesture
        }

        when (mode) {
            TileMode.Pull -> {
                onPullStart()
                var travelled = 0f
                drag(down.id) { change ->
                    travelled += change.positionChange().y
                    onPullDelta(travelled.coerceAtLeast(0f))
                    change.consume()
                }
                onPullRelease()
            }

            TileMode.Lift -> {
                onLiftStart(down.position)
                var moved = Offset.Zero
                drag(down.id) { change ->
                    moved += change.positionChange()
                    onLiftMove(moved)
                    change.consume()
                }
                onLiftDrop()
            }

            TileMode.Tap -> Unit
        }
    }
}

/**
 * Pinch in, anywhere on the pages, to see all of them at once.
 *
 * It watches on the initial pass so it sees the gesture before the pager does, but it consumes
 * nothing until a second finger is down. A one-finger swipe therefore reaches the pager
 * completely untouched, which is the only way these two can share the same surface.
 */
fun Modifier.pinchIn(threshold: Float = 0.72f, onPinchIn: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                if (event.changes.count { it.pressed } >= 2 && !fired) {
                    zoom *= event.calculateZoom()
                    if (zoom < threshold) {
                        fired = true
                        event.changes.forEach { it.consume() }
                        onPinchIn()
                    }
                }
            }
        }
    }
