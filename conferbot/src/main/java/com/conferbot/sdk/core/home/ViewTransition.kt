package com.conferbot.sdk.core.home

/**
 * How the open chat moves between its views - the home screen, the
 * conversation and the help centre - from the owner's setting
 * (customizations.viewTransition, viewTransitionDirection). Ported from the
 * React Native SDK's src/core/viewTransition.ts, itself from the web widget's
 * src/ts/layout/viewTransition.ts (VIEW_TRANSITIONS, normalizeViewTransition,
 * VIEW_DIRECTIONS, normalizeViewDirection, sweepVector, directionBetween and
 * the "reduced", "fade" and "slide" tracks of playViewTransition).
 *
 * The web lays a still copy of the old view (a "ghost") over the panel and
 * animates it away while the new view comes in. A native view has no ghost
 * to copy, so the app plays the new view's half only:
 *
 *   classic, none   the view switches at once (classic's header drop is the
 *                   old widget's own and is not played);
 *   fade            the new view fades up from 103.5% over 340ms, 40ms in;
 *   slide           it slides in from the sweep's side over 420ms;
 *   anything else   (honeycomb - the default -, iris, ripple, cards, blinds,
 *                   pixels, flip, mosaic, diamonds, bubbles, stripes, wave,
 *                   curtain, shutter): the slide, the calm move closest to
 *                   the web's sweeps;
 *   reduced motion  a 200ms fade, whatever the owner picked (as the web).
 *
 * Pure.
 */
object ViewTransition {

    val KINDS = listOf(
        "classic", "none", "slide", "fade", "iris", "ripple", "honeycomb", "cards", "blinds",
        "pixels", "flip", "mosaic", "diamonds", "bubbles", "stripes", "wave", "curtain", "shutter"
    )

    /** Unset: the sliding honeycomb ("classic" stays the widget as it always was). */
    const val DEFAULT = "honeycomb"

    fun normalize(value: Any?): String = (value as? String)?.takeIf { it in KINDS } ?: DEFAULT

    /**
     * Which way a transition sweeps. "auto" goes sideways by where the views
     * sit (deeper is to the right); the others are fixed, and coming back
     * plays them in reverse. "tap" (out from the tap) has no tap point here
     * and goes sideways as "auto".
     */
    val DIRECTIONS = listOf("auto", "ltr", "rtl", "ttb", "btt", "tap")

    fun normalizeDirection(value: Any?): String = (value as? String)?.takeIf { it in DIRECTIONS } ?: "auto"

    /** The sweep as a unit vector (x, y) for a move forward (+1) or back (-1). */
    fun sweepVector(sweep: Any?, dir: Int): Pair<Int, Int> {
        val (x, y) = when (normalizeDirection(sweep)) {
            "rtl" -> -1 to 0
            "ttb" -> 0 to 1
            "btt" -> 0 to -1
            else -> 1 to 0
        }
        return x * dir to y * dir
    }

    /** Where the views sit, left to right (the web's VIEW_ORDER; the app's help centre is "help"). */
    private val ORDER = mapOf("home" to 0, "initial_page" to 0, "chat" to 1, "kb" to 2, "help" to 2, "articles" to 2, "list" to 3, "article" to 4)

    /** True when going deeper (forward), false coming back. */
    fun isForward(from: String, to: String): Boolean = (ORDER[to] ?: 1) >= (ORDER[from] ?: 1)

    /** The web's easings (viewTransition.ts EASE, EASE_IO) as cubic-bezier points. */
    val EASE = floatArrayOf(0.22f, 0.8f, 0.24f, 1f)
    val EASE_IO = floatArrayOf(0.6f, 0f, 0.25f, 1f)

    /** How the new view comes in. */
    sealed class Motion {
        object None : Motion()
        /** The new view's opacity 0 -> 1, linear. */
        data class Reduced(val durationMs: Int) : Motion()
        /** Opacity 0 -> 1 and scale [scaleFrom] -> 1, after [delayMs]. */
        data class Fade(val durationMs: Int, val delayMs: Int, val scaleFrom: Float) : Motion()
        /** In from (dx, dy) times the view's size to its place. */
        data class Slide(val durationMs: Int, val dx: Int, val dy: Int) : Motion()
    }

    /** How the new view comes in when the chat moves from one view to another. */
    fun motion(kind: Any?, sweep: Any?, from: String, to: String, reduced: Boolean = false): Motion {
        if (from == to) return Motion.None
        val k = normalize(kind)
        if (k == "classic" || k == "none") return Motion.None
        if (reduced) return Motion.Reduced(200)
        if (k == "fade") return Motion.Fade(340, 40, 1.035f)
        val (dx, dy) = sweepVector(sweep, if (isForward(from, to)) 1 else -1)
        return Motion.Slide(420, dx, dy)
    }
}
