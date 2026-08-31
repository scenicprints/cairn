package com.cairn.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private enum class TileMode { Tap, Pull, Lift, PassThrough }

/**
 * A tile's gestures, and the one rule that matters: it claims as little as possible.
 *
 * The first version of this decided by direction alone, treating anything that was not a
 * downward drag as "pick this up". That consumed the touch slop before HorizontalPager ever
 * saw it, so a sideways swipe that began on an icon could not turn the page, and since the
 * home screen is mostly icons, paging was effectively dead.
 *
 * The gesture space is simply full. Horizontal belongs to the pager and vertical-up belongs to
 * the drawer, which leaves only vertical-down and a hold. So:
 *
 *  - Straight down: pull the tile open. Consumed, because nothing else wants it.
 *  - Hold still: pick the tile up. Nothing has been consumed yet and the finger has not moved,
 *    so nothing else has started either.
 *  - Anything else: consume nothing at all and let it through to the pager or the drawer.
 *
 * That means long press is back for dragging. It is the honest answer: a launcher has more
 * meanings than it has directions, and the hold is the only one left that collides with nothing.
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
        val slop = viewConfiguration.touchSlop
        val holdMillis = viewConfiguration.longPressTimeoutMillis

        var travel = Offset.Zero
        var mode: TileMode? = null

        // Nothing is consumed inside this window. If the gesture turns out to belong to the
        // pager or the drawer, they have been receiving it in parallel the whole time.
        try {
            withTimeout(holdMillis) {
                while (mode == null) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        mode = TileMode.PassThrough
                        break
                    }
                    if (!change.pressed) {
                        mode = TileMode.Tap
                        break
                    }
                    if (change.isConsumed) {
                        mode = TileMode.PassThrough
                        break
                    }
                    travel += change.positionChange()
                    if (travel.getDistance() > slop) {
                        mode = if (travel.y > 0f && abs(travel.y) > abs(travel.x)) {
                            TileMode.Pull
                        } else {
                            TileMode.PassThrough
                        }
                    }
                }
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            // Held still for the long press timeout without moving. That is a pick-up.
            mode = TileMode.Lift
        }

        when (mode) {
            TileMode.Tap -> onTap()

            TileMode.Pull -> {
                onPullStart()
                var travelled = travel.y.coerceAtLeast(0f)
                onPullDelta(travelled)
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

            // Consume nothing and get out of the way.
            TileMode.PassThrough, null -> Unit
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
