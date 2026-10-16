package com.conferbot.sdk.core.customization

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Launcher attention, as the web widget plays it (conferbot-widget
 * src/ts/launcher/attention.ts): an opt-in, continuous "look here" motion
 * on the closed launcher or the greeting card.
 *
 *   customizations.launcherAttention:
 *     "off"    (default) nothing;
 *     "pulse"  a soft ring grows out of the launcher every 3s;
 *     "wobble" a jelly squash and twist every 5.5s;
 *     "bounce" a hop with a contact shadow every 5s;
 *     "wave"   the greeting card bobs and a waving hand waves on it, 6s;
 *     "float"  the greeting card floats up and down, 4s;
 *     "shine"  a gloss sweeps across the button every 5s;
 *     "heartbeat" two beats (lub-dub) every 3s;
 *     "orbit"  a spark runs two laps round the button every 5.2s.
 *
 * The rules (ui/compose/LauncherFxControllers.kt keeps them): the first beat
 * 1.5s after the launcher (or the card) shows; only while the chat is
 * closed; paused while the launcher plays an idle or message moment; over
 * for the app session once the chat is opened (and not started when it was
 * opened in the last 30 minutes); nothing with reduced motion. While a
 * launcher-moving mode runs, the launcher's idle moments hold off and the
 * three ripples after load give way (a new message's ripple still plays).
 * Nothing moves while an entrance is landing.
 *
 * Ported from the React Native SDK's core/launcherAttention.ts. Pure.
 */
object LauncherAttention {
    val MODES = listOf("off", "pulse", "wobble", "bounce", "wave", "float", "shine", "heartbeat", "orbit")

    fun normalize(value: Any?): String = (value as? String)?.takeIf { it in MODES } ?: "off"

    /** Which element a mode moves: "launcher", "greeting", or null. */
    fun target(mode: String): String? = if (mode == "off") null else if (mode == "wave" || mode == "float") "greeting" else "launcher"

    /** The first beat waits this long after the launcher (or the card) shows. */
    const val START_MS = 1500

    /** While an entrance lands, attention looks again this often. */
    const val RETRY_MS = 400

    /** The waving hand: the web's sticker, unless the card's own text has one. */
    const val WAVING_HAND = "👋"

    private fun loop(part: String, kf: List<Keyframe>, duration: Number, delay: Number = START_MS) =
        Track.of(kf, duration = duration, iterations = Double.POSITIVE_INFINITY, easing = "linear", delay = delay, part = part)

    /** The pulse ring (ATTENTION_CSS cb-attn-ring / cb-attn-ring-wide): a CSS animation, so its timing function eases each step. The second ring is 350ms behind. */
    private const val RING_EASE = "cubic-bezier(0,0,.2,1)"

    private fun ring(part: String, wide: Boolean) = loop(
        part,
        if (wide) listOf(
            mapOf("offset" to 0, "scale" to "1", "opacity" to 0.42, "easing" to RING_EASE),
            mapOf("offset" to 0.55, "scale" to "1.12 1.6", "opacity" to 0, "easing" to RING_EASE),
            mapOf("offset" to 1, "scale" to "1.12 1.6", "opacity" to 0),
        ) else listOf(
            mapOf("offset" to 0, "scale" to "1", "opacity" to 0.85, "easing" to RING_EASE),
            mapOf("offset" to 0.6, "scale" to "1.85", "opacity" to 0, "easing" to RING_EASE),
            mapOf("offset" to 1, "scale" to "1.85", "opacity" to 0),
        ),
        3000,
        if (part == "ring") START_MS else START_MS + 350,
    )

    /**
     * The movement for a mode, as the web's tracks. Parts: "self" = the target
     * (launcher or card), "hand" = the card's waving hand, "shadow" = the
     * bounce's contact shadow, "shine" = the gloss band, "orbit" = the spark,
     * "ring" / "ring2" = the pulse's two rings. `clipped`: the button clips its
     * own outline, so the pulse throbs the button instead of a ring. `wide`:
     * the label pill, whose ring grows less sideways.
     */
    fun tracks(mode: String, clipped: Boolean = false, wide: Boolean = false): List<Track> {
        val e = "ease-in-out"
        return when (mode) {
            "pulse" -> if (clipped) listOf(
                loop("self", listOf(
                    mapOf("scale" to "1", "offset" to 0, "easing" to e),
                    mapOf("scale" to "1.08", "offset" to 0.1, "easing" to e),
                    mapOf("scale" to "1", "offset" to 0.2, "easing" to e),
                    mapOf("scale" to "1.05", "offset" to 0.28, "easing" to e),
                    mapOf("scale" to "1", "offset" to 0.36),
                    mapOf("scale" to "1", "offset" to 1),
                ), 3000),
            ) else listOf(ring("ring", wide), ring("ring2", wide))
            "wobble" -> listOf(
                loop("self", listOf(
                    mapOf("scale" to "1", "rotate" to "0deg", "offset" to 0, "easing" to e),
                    mapOf("scale" to "1.14 0.86", "rotate" to "-4deg", "offset" to 0.04, "easing" to e),
                    mapOf("scale" to "0.88 1.12", "rotate" to "4deg", "offset" to 0.08, "easing" to e),
                    mapOf("scale" to "1.07 0.94", "rotate" to "-2.5deg", "offset" to 0.12, "easing" to e),
                    mapOf("scale" to "0.96 1.04", "rotate" to "1.5deg", "offset" to 0.16, "easing" to e),
                    mapOf("scale" to "1.02 0.98", "rotate" to "0deg", "offset" to 0.2, "easing" to e),
                    mapOf("scale" to "1", "rotate" to "0deg", "offset" to 0.24),
                    mapOf("scale" to "1", "rotate" to "0deg", "offset" to 1),
                ), 5500),
            )
            "bounce" -> listOf(
                loop("self", listOf(
                    mapOf("translate" to "0 0", "scale" to "1", "offset" to 0),
                    mapOf("translate" to "0 0", "scale" to "1.12 0.88", "offset" to 0.04, "easing" to "cubic-bezier(.2,.7,.3,1)"),
                    mapOf("translate" to "0 -14px", "scale" to "0.92 1.1", "offset" to 0.1, "easing" to "cubic-bezier(.3,0,.7,1)"),
                    mapOf("translate" to "0 -18px", "scale" to "1 1", "offset" to 0.13, "easing" to "cubic-bezier(.6,0,.8,.4)"),
                    mapOf("translate" to "0 0", "scale" to "1.16 0.84", "offset" to 0.19, "easing" to "cubic-bezier(.2,.7,.3,1)"),
                    mapOf("translate" to "0 -5px", "scale" to "0.97 1.03", "offset" to 0.24, "easing" to "cubic-bezier(.6,0,.8,.4)"),
                    mapOf("translate" to "0 0", "scale" to "1.04 0.96", "offset" to 0.28),
                    mapOf("translate" to "0 0", "scale" to "1", "offset" to 0.31),
                    mapOf("translate" to "0 0", "scale" to "1", "offset" to 1),
                ), 5000),
                loop("shadow", listOf(
                    mapOf("scale" to "1", "opacity" to 0.35, "offset" to 0),
                    mapOf("scale" to "1.12 1", "opacity" to 0.4, "offset" to 0.04),
                    mapOf("scale" to "0.55 0.7", "opacity" to 0.14, "offset" to 0.13),
                    mapOf("scale" to "1.2 1", "opacity" to 0.42, "offset" to 0.19),
                    mapOf("scale" to "0.9 0.9", "opacity" to 0.3, "offset" to 0.24),
                    mapOf("scale" to "1", "opacity" to 0.35, "offset" to 0.31),
                    mapOf("scale" to "1", "opacity" to 0.35, "offset" to 1),
                ), 5000),
            )
            "wave" -> listOf(
                loop("self", listOf(
                    mapOf("translate" to "0 0", "offset" to 0, "easing" to e),
                    mapOf("translate" to "0 -6px", "offset" to 0.08, "easing" to e),
                    mapOf("translate" to "0 0", "offset" to 0.17, "easing" to e),
                    mapOf("translate" to "0 -2px", "offset" to 0.23, "easing" to e),
                    mapOf("translate" to "0 0", "offset" to 0.3),
                    mapOf("translate" to "0 0", "offset" to 1),
                ), 6000),
                loop("hand", listOf(
                    mapOf("rotate" to "0deg", "offset" to 0, "easing" to e),
                    mapOf("rotate" to "16deg", "offset" to 0.05, "easing" to e),
                    mapOf("rotate" to "-10deg", "offset" to 0.1, "easing" to e),
                    mapOf("rotate" to "16deg", "offset" to 0.15, "easing" to e),
                    mapOf("rotate" to "-6deg", "offset" to 0.2, "easing" to e),
                    mapOf("rotate" to "10deg", "offset" to 0.25, "easing" to e),
                    mapOf("rotate" to "0deg", "offset" to 0.3),
                    mapOf("rotate" to "0deg", "offset" to 1),
                ), 6000),
            )
            "float" -> listOf(
                loop("self", listOf(
                    mapOf("translate" to "0 0", "offset" to 0, "easing" to e),
                    mapOf("translate" to "0 -5px", "offset" to 0.5, "easing" to e),
                    mapOf("translate" to "0 0", "offset" to 1),
                ), 4000),
            )
            "shine" -> listOf(
                loop("shine", listOf(
                    mapOf("translate" to "-160% 0", "offset" to 0),
                    mapOf("translate" to "-160% 0", "offset" to 0.02, "easing" to "cubic-bezier(.4,0,.2,1)"),
                    mapOf("translate" to "260% 0", "offset" to 0.16),
                    mapOf("translate" to "260% 0", "offset" to 1),
                ), 5000),
                loop("self", listOf(
                    mapOf("scale" to "1", "offset" to 0),
                    mapOf("scale" to "1.04", "offset" to 0.08, "easing" to e),
                    mapOf("scale" to "1", "offset" to 0.18),
                    mapOf("scale" to "1", "offset" to 1),
                ), 5000),
            )
            "heartbeat" -> listOf(
                loop("self", listOf(
                    mapOf("scale" to "1", "offset" to 0),
                    mapOf("scale" to "1.14", "offset" to 0.06, "easing" to e),
                    mapOf("scale" to "1", "offset" to 0.12, "easing" to e),
                    mapOf("scale" to "1.08", "offset" to 0.18, "easing" to e),
                    mapOf("scale" to "1", "offset" to 0.28),
                    mapOf("scale" to "1", "offset" to 1),
                ), 3000),
            )
            "orbit" -> listOf(
                loop("orbit", listOf(
                    mapOf("offsetDistance" to "0%", "opacity" to 0, "offset" to 0),
                    mapOf("offsetDistance" to "8%", "opacity" to 1, "offset" to 0.05),
                    mapOf("offsetDistance" to "192%", "opacity" to 1, "offset" to 0.4, "easing" to "cubic-bezier(.4,0,.6,1)"),
                    mapOf("offsetDistance" to "200%", "opacity" to 0, "offset" to 0.45),
                    mapOf("offsetDistance" to "200%", "opacity" to 0, "offset" to 1),
                ), 5200),
            )
            else -> emptyList()
        }
    }

    /** The bits each part starts from (CSS): the shadow at .35, the ring and spark hidden, the gloss parked off the button. */
    val BASE: Map<String, Map<String, Double>> = mapOf(
        "shadow" to mapOf("o" to 0.35),
        "ring" to mapOf("o" to 0.0),
        "ring2" to mapOf("o" to 0.0),
        "orbit" to mapOf("o" to 0.0),
        "shine" to mapOf("txp" to -160.0, "r" to 18.0),
    )

    data class Point(val x: Double, val y: Double)

    /**
     * The orbit's track: a rounded rectangle 7px outside the button (a circle
     * round a round launcher, a stadium round a pill), clockwise from the top
     * edge's left end - the web's orbitPath, as a point at a share of its
     * length (wrapping, so 2 is two laps). Relative to the button's top-left.
     */
    fun orbitPoint(w: Double, h: Double, fraction: Double, margin: Double = 7.0): Point {
        val x0 = -margin
        val y0 = -margin
        val bw = w + margin * 2
        val bh = h + margin * 2
        val r = min(bw, bh) / 2
        val sw = bw - 2 * r
        val sh = bh - 2 * r
        val arc = (PI * r) / 2
        val total = 2 * sw + 2 * sh + 4 * arc
        // JavaScript's % keeps the sign (as Kotlin's); ((f % 1) + 1) % 1 is the share into the lap.
        var d = (((fraction % 1) + 1) % 1) * total
        if (fraction > 0 && fraction % 1 == 0.0) d = 0.0
        fun corner(cx: Double, cy: Double, from: Double, len: Double): Point {
            val a = from + len / r
            return Point(cx + r * cos(a), cy + r * sin(a))
        }
        // Top edge, top-right corner, right edge, bottom-right, bottom, bottom-left, left, top-left.
        if (d <= sw) return Point(x0 + r + d, y0)
        d -= sw
        if (d <= arc) return corner(x0 + bw - r, y0 + r, -PI / 2, d)
        d -= arc
        if (d <= sh) return Point(x0 + bw, y0 + r + d)
        d -= sh
        if (d <= arc) return corner(x0 + bw - r, y0 + bh - r, 0.0, d)
        d -= arc
        if (d <= sw) return Point(x0 + bw - r - d, y0 + bh)
        d -= sw
        if (d <= arc) return corner(x0 + r, y0 + bh - r, PI / 2, d)
        d -= arc
        if (d <= sh) return Point(x0, y0 + bh - r - d)
        d -= sh
        return corner(x0 + r, y0 + r, PI, d)
    }
}
