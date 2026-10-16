package com.conferbot.sdk.core.customization

import kotlin.math.ceil
import kotlin.math.max

/**
 * The mascot's moods and the typing mascot, as the web plays them: CSS
 * @keyframes on the drawing's parts, kept here as the web wrote them and
 * turned into tracks for the evaluator ([Keyframes.cssKeyframes]).
 *
 * Moods (conferbot-widget src/ts/launcher/art3d.ts MASCOT_ANIM_CSS,
 * mascotState): a class on the drawing.
 *   idle   a natural blink now and then (cb-m--blink, always on)
 *   wave   the arm waves; a resting arm ("down") lifts first. Repeats, or
 *          `waveCount` times (the greeting card: 2) and rests
 *   think  a gentle bob with a head tilt, eyes looking around, brows up
 *   happy  a happy bounce (twice) and a big smile, kept while the mood is
 * Each part turns about its own joint ([MascotArt.origins]). Reduced motion
 * turns every movement off; the happy smile, which is not motion, stays.
 *
 * Typing ("mascot" typing style, src/css/polish.css .cb-typing--mascot):
 * the bust, 32 x 32, swaying about 50% 90% of its box, blinking and
 * glancing at three thought dots rising beside it. Still under reduced
 * motion.
 *
 * Ported from the React Native SDK's core/mascotMoods.ts. Pure.
 */
object MascotMoods {
    val MOODS = listOf("idle", "wave", "think", "happy")

    /** How long the happy bounce plays (two hops). */
    const val HAPPY_MS = 1800L

    /** The launcher's mascot keeps its smile this long after a reply (launcherStyle.ts cheerLauncherMascot). */
    const val CHEER_MS = HAPPY_MS + 600

    /** The web's @keyframes bodies (art3d.ts MASCOT_ANIM_CSS, polish.css), verbatim. */
    val KEYFRAMES: Map<String, String> = mapOf(
        "cb-m-blink" to "0%,41%,44.4%,86%,88.4%,90.4%,92.8%,100%{transform:scaleY(0)}42.6%,87.2%,91.6%{transform:scaleY(1)}",
        "cb-m-wave" to "0%,52%,100%{transform:rotate(0)}60%{transform:rotate(-18deg)}68%{transform:rotate(8deg)}76%{transform:rotate(-16deg)}84%{transform:rotate(6deg)}92%{transform:rotate(-4deg)}",
        "cb-m-wave-up" to "0%,100%{transform:rotate(0)}14%{transform:rotate(-118deg)}24%{transform:rotate(-132deg)}34%{transform:rotate(-104deg)}44%{transform:rotate(-132deg)}54%{transform:rotate(-108deg)}62%{transform:rotate(-118deg)}78%{transform:rotate(0)}",
        "cb-m-bob" to "0%,100%{transform:translateY(0) rotate(0)}25%{transform:translateY(-1.6px) rotate(-3deg)}50%{transform:translateY(0) rotate(0)}75%{transform:translateY(-1.2px) rotate(2deg)}",
        "cb-m-glance" to "0%,8%,92%,100%{transform:translate(0,0)}16%,38%{transform:translate(-2px,-1.6px)}46%,70%{transform:translate(2px,-1.8px)}78%,86%{transform:translate(.6px,.8px)}",
        "cb-m-ponder" to "0%,100%{transform:translateY(-1px)}25%{transform:translateY(-2.4px)}",
        "cb-m-hop" to "0%,100%{transform:translateY(0) scale(1,1)}18%{transform:translateY(0) scale(1.07,.92)}45%{transform:translateY(-8px) scale(.96,1.05)}70%{transform:translateY(0) scale(1.05,.95)}85%{transform:translateY(0) scale(.99,1.01)}",
        "cb-m-hop-shadow" to "0%,18%,70%,100%{transform:scale(1);opacity:1}45%{transform:scale(.78);opacity:.55}",
        "cb-m-cheer" to "0%,100%{transform:rotate(0)}45%{transform:rotate(-24deg)}",
        "cb-typing-sway" to "0%, 100% { transform: rotate(-4deg) translateY(0.5px); } 50% { transform: rotate(4deg) translateY(-1px); }",
        "cb-typing-glance" to "0%, 35%, 100% { transform: translate(0, 0); } 45%, 85% { transform: translate(2.2px, -1.6px); }",
        "cb-typing-blink" to "0%, 92%, 100% { transform: scaleY(0); } 95% { transform: scaleY(1); }",
        "cb-typing-dot" to "0%, 100% { opacity: 0.25; } 50% { opacity: 1; }",
        "cb-typing-write" to "0% { stroke-dashoffset: 60; opacity: 1; } 75% { stroke-dashoffset: 0; opacity: 1; } 90% { stroke-dashoffset: 0; opacity: 1; } 100% { stroke-dashoffset: 0; opacity: 0; }",
        "cb-typing-pencil" to "0% { transform: translate(2px, 11px); } 9% { transform: translate(6px, 6.5px); } 19% { transform: translate(11px, 10px); } 28% { transform: translate(16px, 13px); } 37% { transform: translate(19px, 9px); } 46% { transform: translate(24px, 5px); } 54% { transform: translate(27px, 10px); } 64% { transform: translate(32px, 12.5px); } 75%, 90% { transform: translate(36px, 8px); } 100% { transform: translate(2px, 11px); }",
    )

    /** A CSS animation (name duration timing delay iterations) as a track. */
    fun cssTrack(name: String, duration: Number, timing: String, iterations: Double = 1.0, delay: Number = 0): Track =
        Track(Keyframes.cssKeyframes(KEYFRAMES.getValue(name), timing), TrackOptions(duration.toDouble(), delay.toDouble(), iterations))

    /**
     * A mood's tracks per part (lid, arm, body, look, brow, shadow: the art3d
     * classes), whether the big smile is on, when every finite part is over
     * (`settle`, ms, on a whole blink) and the loop after it (`period`).
     */
    data class MoodPlan(val parts: Map<String, List<Track>>, val grin: Boolean, val settle: Double, val period: Double)

    private const val BLINK_MS = 6400.0
    private const val INF = Double.POSITIVE_INFINITY

    /**
     * A mood's plan (mascotState: blink always, plus the mood's own). [arm] is
     * the pose as drawn ("down" lifts before it waves), [bust] has no arm,
     * [waveCount] waves (infinity: on and on).
     */
    fun moodPlan(mood: String, arm: String? = null, bust: Boolean = false, waveCount: Double? = null, reduced: Boolean = false): MoodPlan {
        val grin = mood == "happy"
        if (reduced) return MoodPlan(emptyMap(), grin, 0.0, 0.0)
        val parts = LinkedHashMap<String, List<Track>>()
        parts["lid"] = listOf(cssTrack("cb-m-blink", BLINK_MS, "linear", INF))
        val hasArm = !bust
        if (mood == "wave" && hasArm) {
            val n = waveCount ?: INF
            parts["arm"] = listOf(if (arm == "down" || arm == null) cssTrack("cb-m-wave-up", 3200, "ease-in-out", n) else cssTrack("cb-m-wave", 2800, "ease-in-out", n))
        }
        if (mood == "think") {
            parts["body"] = listOf(cssTrack("cb-m-bob", 3200, "ease-in-out", INF))
            parts["look"] = listOf(cssTrack("cb-m-glance", 4800, "ease-in-out", INF))
            parts["brow"] = listOf(cssTrack("cb-m-ponder", 3200, "ease-in-out", INF))
        }
        if (mood == "happy") {
            val hop = "cubic-bezier(.3,.7,.4,1)"
            parts["body"] = listOf(cssTrack("cb-m-hop", 900, hop, 2.0))
            parts["shadow"] = listOf(cssTrack("cb-m-hop-shadow", 900, hop, 2.0))
            if (hasArm) parts["arm"] = listOf(cssTrack("cb-m-cheer", 900, "ease-in-out", 2.0))
        }
        var settle = 0.0
        var loops = false
        for (tracks in parts.values) {
            for (t in tracks) {
                if (t.options.iterations == INF) {
                    if (t.options.duration != BLINK_MS) loops = true
                } else settle = max(settle, t.options.delay + t.options.duration * t.options.iterations)
            }
        }
        // Loops of their own (think, an endless wave): one lap of all of them together.
        val period = if (loops) lcm(parts.values.flatten().filter { it.options.iterations == INF }.map { it.options.duration.toLong() }).toDouble() else BLINK_MS
        // The finite part done, the loop starts on a whole blink.
        return MoodPlan(parts, grin, ceil(settle / BLINK_MS) * BLINK_MS, period)
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b != 0L) gcd(b, a % b) else a
    private fun lcm(xs: List<Long>): Long = xs.fold(1L) { m, x -> m * x / gcd(m, x) }

    /** One thought dot: its size, how far up it sits, its track. */
    class Dot(val d: Double, val up: Double, val track: Track)

    /** The typing mascot (polish.css): the whole bust sways, the lids blink, the pupils glance; the three dots rise. */
    object Typing {
        /** The bust's box, and its pivot (transform-origin 50% 90%). */
        const val SIZE = 32.0
        val ORIGIN = 16.0 to 28.8
        val sway = cssTrack("cb-typing-sway", 2400, "ease-in-out", INF)
        val blink = cssTrack("cb-typing-blink", 3400, "ease-in-out", INF)
        val glance = cssTrack("cb-typing-glance", 2400, "ease-in-out", INF)

        /** The thought dots: 4, 5 and 7 across, 0, 3 and 7 up, each 0.2s after the last. */
        val dots = listOf(
            Dot(4.0, 0.0, cssTrack("cb-typing-dot", 1200, "ease-in-out", INF)),
            Dot(5.0, 3.0, cssTrack("cb-typing-dot", 1200, "ease-in-out", INF, 200)),
            Dot(7.0, 7.0, cssTrack("cb-typing-dot", 1200, "ease-in-out", INF, 400)),
        )

        /** One lap of all of it (the clock loops this). */
        const val PERIOD = 40800.0
    }

    /** The pencil (polish.css .cb-typing--pencil): the line writes itself in 2s, the pencil riding its tip. */
    object Pencil {
        val write = cssTrack("cb-typing-write", 2000, "linear", INF)
        val pencil = cssTrack("cb-typing-pencil", 2000, "linear", INF)
        const val LINE = "M2 11 C 6 5, 9 5, 11 10 S 16 14, 19 9 S 25 4, 27 10 S 33 13, 36 8"
        const val DASH = 60.0
        const val PERIOD = 2000.0
    }

    /** "Pip is typing" when the owner named their mascot (chatChrome.ts typingBubbleHtml), else "Typing". */
    fun typingLabel(style: String, mascotName: String): String =
        if (style == "mascot" && mascotName.isNotEmpty()) "$mascotName is typing" else "Typing"
}
