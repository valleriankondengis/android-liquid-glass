// GlassDrag: low-level drag detection with immediate press start,
// pointer transfer, and no touch slop.

package com.vallxz.glass.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Low-level drag detection for the glass motion engine.
 *
 * The press starts immediately on pointer down: there is no touch-slop or
 * movement threshold before [onDragStart] fires. An initial [onDrag]
 * callback fires once with a zero delta, then every pointer move streams
 * its position delta. Changes are never consumed by this detector: if a
 * change arrives already consumed by another gesture, the gesture ends
 * with [onDragCancel] instead. The gesture ends on pointer up
 * ([onDragEnd]). If the tracked pointer lifts while another pointer is
 * still down, tracking transfers to the remaining pointer.
 *
 * The down is awaited twice: first at
 * the Initial pass with consumption ignored, so the detector sees the
 * down before any other handler on the node (a clickable, a scrolling
 * container) can consume it, then at the Main pass. A single Main-pass
 * await that requires an unconsumed down can wait forever when another
 * handler consumes the down first, which leaves press feedback dead. Only changes that actually
 * moved reach [onDrag]; stationary events are skipped, and pointer-up is
 * detected from the previous pressed state so a consumed up still ends
 * the gesture.
 */
internal suspend fun PointerInputScope.detectGlassDrag(
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down.position)
        onDrag(Offset.Zero)
        val upEvent = drag(initialDown.id) { change ->
            onDrag(change.positionChange())
        }
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd()
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp =
        currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.previousPressed && !change.pressed) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.previousPressed && !dragEvent.pressed) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
