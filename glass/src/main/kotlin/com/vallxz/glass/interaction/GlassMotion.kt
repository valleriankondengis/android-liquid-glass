// GlassMotion: the shared press/drag/release animation engine for
// interactive glass components.

package com.vallxz.glass.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The shared spring-motion engine behind the draggable glass components
 * (slider, toggle, segmented control, bottom tabs).
 *
 * One controller owns four animations: the dragged value, the press
 * progress (0 at rest, 1 pressed), and the pressed scales on both axes.
 * Drag deltas retarget the value spring, so the visible value chases the
 * finger with a damped lag instead of tracking it 1:1. There is no
 * fling: on release the value springs to its snap target, and velocity is
 * only used for visual squash.
 *
 * Spring specs (damping ratio / stiffness): press progress 1.0 / 1000,
 * horizontal scale 0.6 / 250, vertical scale 0.7 / 250, value 1.0 / 1000,
 * velocity smoothing 0.5 / 300. The value spring's visibility threshold is
 * the caller's [visibilityThreshold]; the velocity holder uses a threshold
 * of 5 and the smoothing spring ten times the visibility threshold.
 *
 * Velocity is fed strictly from value-animation frames: every frame the
 * value animation produces retargets the velocity holder toward the
 * animation's normalized velocity. There is no sampling loop, so nothing
 * writes while the screen is idle or the finger is held still.
 *
 * Retargets are fire-and-forget: each drag callback launches a fresh
 * coroutine that animates the value holder toward the new target, and
 * superseding is delegated to the animation primitive. No job is tracked
 * or cancelled explicitly.
 *
 * Release sequencing: the component's stop handler runs first - snapping
 * decisions and state callbacks fire synchronously, before the release
 * visuals run, so taps and tab switches feel immediate. The release then
 * waits one frame, waits until the animated value is within 2.5% of the
 * value-range span of its target, and only then fires the press-progress
 * and scale animations back to rest - without waiting for them, so the
 * release never stalls on a slow settle. Velocity is left untouched: it
 * decays on its own as the value animation settles. A programmatic
 * [moveTo] (tap-to-jump) runs the same press and release sequence, so
 * taps get the same grow/shrink feedback as drags - but the value
 * animation is only launched, never awaited: awaiting its full settle
 * before releasing left the component frozen in its grown state for as
 * long as the spring took to converge (seconds on a wide value range),
 * which is the tap freeze: waiting only for the 2.5% window keeps taps
 * responsive.
 *
 * There is no separate cancel path: a cancelled gesture runs the same
 * stop handler as a normal end, so a gesture stolen by a parent still
 * flips or snaps instead of leaving the component mid-press.
 *
 * Tap-vs-drag classification uses a small movement dead-zone (see
 * [noteMovement]): a tap that jitters a pixel or two under the finger
 * must still count as a tap. The dead-zone gates only the classification,
 * never the drag response - the value follows every delta immediately.
 *
 * @param scope A composition scope ([rememberCoroutineScope]), which
 *   provides the frame clock the release sequencing waits on.
 */
internal class GlassMotion(
    initialValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val visibilityThreshold: Float,
    private val scope: CoroutineScope,
) {
    val span: Float
        get() = valueRange.endInclusive - valueRange.start

    /** Animated value, in [valueRange] units. */
    val value = Animatable(initialValue.coerceIn(valueRange), Float.VectorConverter, visibilityThreshold)

    /** Press progress: 0 at rest, 1 while pressed. */
    val pressProgress = Animatable(0f, Float.VectorConverter, 0.001f)

    /** Pressed scale on the X axis. */
    val scaleX = Animatable(1f, Float.VectorConverter, 0.001f)

    /** Pressed scale on the Y axis. */
    val scaleY = Animatable(1f, Float.VectorConverter, 0.001f)

    /** Smoothed normalized velocity (value velocity divided by the span). */
    val velocity = Animatable(0f, Float.VectorConverter, 5f)

    /** Scale the component grows to while pressed. */
    var pressedScale: Float = 1.5f

    /** True from press until the release sequence finishes. */
    var isPressed by mutableStateOf(false)
        private set

    /** True once the gesture has moved past the tap dead-zone. */
    var didDrag by mutableStateOf(false)
        private set

    private val pressSpring = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleXSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val valueSpring = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = visibilityThreshold)
    private val velocitySpring = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = 300f,
        visibilityThreshold = visibilityThreshold * 10f,
    )

    private var sequenceJob: Job? = null
    private var dragDistancePx = 0f

    /** Starts the press: resets velocity and drag tracking and grows the component. */
    fun onPress() {
        sequenceJob?.cancel()
        didDrag = false
        dragDistancePx = 0f
        isPressed = true
        sequenceJob = scope.launch {
            launch { velocity.snapTo(0f) }
            launch { pressProgress.animateTo(1f, pressSpring) }
            launch { scaleX.animateTo(pressedScale, scaleXSpring) }
            launch { scaleY.animateTo(pressedScale, scaleYSpring) }
        }
    }

    /**
     * Retargets the value spring to [target]. Call on every drag delta;
     * the spring chase gives the damped feel. Fire-and-forget: a new
     * animation on the holder supersedes the running one.
     */
    fun onDragTo(target: Float) {
        scope.launch {
            value.animateTo(target.coerceIn(valueRange), valueSpring) {
                feedVelocity(velocity)
            }
        }
    }

    /**
     * Records horizontal movement toward the tap-vs-drag decision. The
     * accumulated distance must reach [thresholdPx] before the gesture
     * counts as a drag, so a tap that jitters under the finger still
     * flips or jumps instead of snapping back to where it started.
     */
    fun noteMovement(dxPx: Float, thresholdPx: Float) {
        if (didDrag) return
        dragDistancePx += abs(dxPx)
        if (dragDistancePx >= thresholdPx) {
            didDrag = true
        }
    }

    /**
     * Releases the press, springing the value to [snapTarget] with the
     * release sequencing described above. State callbacks belong in the
     * caller's stop handler, fired synchronously before this call - not
     * after the release animation finishes.
     */
    fun onRelease(snapTarget: Float) {
        sequenceJob?.cancel()
        sequenceJob = scope.launch {
            releaseSequence(snapTarget)
            isPressed = false
        }
    }

    /**
     * Programmatic move (tap-to-jump): runs the press sequence, moves the
     * value, zeroes velocity, then runs the release sequence. Nothing here
     * awaits the value animation: the release waits only for the 2.5%
     * window, so a tap never leaves the component frozen in its grown
     * state while the spring fully settles. The value animation carries
     * no velocity feed, so no squash appears during a tap jump.
     *
     * With [snapValue], the value jumps to [target] instantly instead of
     * sliding: for a thumb that was hidden and must appear at the tapped
     * position, sliding in from its stale position reads as a startled
     * jump across the control. The press pulse still runs, in place.
     */
    fun moveTo(target: Float, snapValue: Boolean = false) {
        sequenceJob?.cancel()
        didDrag = false
        dragDistancePx = 0f
        isPressed = true
        sequenceJob = scope.launch {
            launch { velocity.snapTo(0f) }
            launch { pressProgress.animateTo(1f, pressSpring) }
            launch { scaleX.animateTo(pressedScale, scaleXSpring) }
            launch { scaleY.animateTo(pressedScale, scaleYSpring) }
            if (snapValue) {
                value.snapTo(target.coerceIn(valueRange))
            } else {
                launch { value.animateTo(target.coerceIn(valueRange), valueSpring) }
            }
            if (velocity.value != 0f) {
                launch { velocity.animateTo(0f, velocitySpring) }
            }
            releaseSequence(target)
            isPressed = false
        }
    }

    /** Syncs the animated value to an external change while idle. */
    fun syncTo(target: Float) {
        if (isPressed) return
        scope.launch {
            value.animateTo(target.coerceIn(valueRange), valueSpring) {
                feedVelocity(velocity)
            }
        }
    }

    private suspend fun releaseSequence(snapTarget: Float) {
        withFrameNanos { }
        val target = snapTarget.coerceIn(valueRange)
        // Retarget only when the running animation is not already headed
        // for the snap target: a programmatic move launched its own
        // no-feed animation, and superseding it here would feed velocity
        // and squash a tap jump. A drag's last retarget may aim
        // elsewhere, so the snap animation runs with its velocity feed.
        if (value.targetValue != target) {
            scope.launch {
                value.animateTo(target, valueSpring) {
                    feedVelocity(velocity)
                }
            }
        }
        while (abs(value.value - target) > 0.025f * span) {
            withFrameNanos { }
        }
        // Fire the shrink animations without awaiting them: the release
        // must not stall on their settle, and a fresh press supersedes
        // them at the animation level.
        scope.launch { pressProgress.animateTo(0f, pressSpring) }
        scope.launch { scaleX.animateTo(1f, scaleXSpring) }
        scope.launch { scaleY.animateTo(1f, scaleYSpring) }
    }

    /**
     * Feeds one value-animation frame into the velocity holder: the
     * animation's velocity, normalized by the value-range span, chased
     * on the smoothing spring. Called only from value-animation frames,
     * so velocity work happens exactly when the value moves - never
     * while idle, never while the finger is held still on a settled
     * value. Each frame launches a fresh retarget; the animation
     * primitive supersedes the previous one.
     */
    private fun feedVelocity(animationVelocity: Float) {
        val normalized = if (span > 0f) animationVelocity / span else 0f
        scope.launch { velocity.animateTo(normalized, velocitySpring) }
    }
}
