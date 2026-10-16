package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.customization.Keyframes.jsNum
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Idle call, as the web widget plays it (conferbot-widget
 * src/ts/launcher/idleCall.ts and idlePacing.ts): when the chat has sat
 * unopened a while, the mascot slips out from behind the launcher and calls
 * the visitor over.
 *
 *   customizations.launcherIdleCall:
 *     "off"   (default) nothing;
 *     "psst"  it sidles out, cups a hand to its mouth: "Psst!";
 *     "peek"  its head rises over the button, looks round, blinks;
 *     "wave"  it hops out and waves;
 *     "knock" it knocks on the button three times;
 *     "tap"   it taps the button twice, which dips.
 *   launcherIdleCallDelay: seconds before the call (5-120, default 12);
 *   launcherIdleCallText: the speech bubble's line (24 characters; default
 *     per style). Tapping the bubble opens the chat.
 *
 * The rules (ui/compose/LauncherFxControllers.kt keeps them): one call per
 * screen at most; two per app session, 3 minutes apart; a visitor who
 * opened the chat, or let both calls go by, is left alone for 3 days;
 * closing the greeting means no more calls this session. A call waits (4s,
 * then looks again) while the chat is open, the app is in the background,
 * the keyboard is up, an entrance is landing, or the greeting has been up
 * less than 15s; with the greeting up the mascot only calls (no bubble).
 * Nothing with reduced motion.
 *
 * Ported from the React Native SDK's core/launcherIdleCall.ts. Pure.
 */
object LauncherIdleCall {
    val MODES = listOf("off", "psst", "peek", "wave", "knock", "tap")
    const val DEFAULT_DELAY = 12
    const val TEXT_MAX = 24

    /** A blocked call looks again after this long. */
    const val RETRY_MS = 4000L

    /** A greeting on screen gets this long alone before the mascot may call. */
    const val GREETING_FIRST_MS = 15000L

    /** The bubble's line per style (i18n en idle.*). */
    val LINES = mapOf(
        "psst" to "Psst!",
        "peek" to "Hey, over here!",
        "wave" to "Hi there!",
        "knock" to "Knock knock!",
        "tap" to "Got a question?",
    )

    fun normalize(value: Any?): String = (value as? String)?.takeIf { it in MODES } ?: "off"

    fun normalizeDelay(value: Any?): Int {
        val n: Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            else -> null
        }
        if (n == null || !n.isFinite()) return DEFAULT_DELAY
        // Math.round: halves go up.
        return min(max(floor(n + 0.5).toInt(), 5), 120)
    }

    private val SPACES = Regex("\\s+")

    /** The bubble's line: the owner's, trimmed and capped, else the style's. */
    fun line(mode: String, text: Any?): String {
        if (mode == "off") return ""
        var own = (text as? String)?.replace(SPACES, " ")?.trim().orEmpty()
        if (own.length > TEXT_MAX) own = own.substring(0, TEXT_MAX)
        return own.ifEmpty { LINES[mode] ?: "" }
    }

    // ---- Pacing (idlePacing.ts): calling without nagging ----

    const val MIN_GAP_MS = 3 * 60 * 1000L
    const val SESSION_MAX = 2
    const val REST_MS = 3 * 24 * 60 * 60 * 1000L

    /** The calls this session: how many played, when the last did, and "not now" (the greeting was closed). */
    data class VisitState(val n: Int = 0, val at: Long = 0, val quiet: Boolean = false)

    /** May a call play now? */
    fun callAllowed(visit: VisitState, restUntil: Long, now: Long): Boolean =
        !visit.quiet && now >= restUntil && visit.n < SESSION_MAX && (visit.at == 0L || now - visit.at >= MIN_GAP_MS)

    /** A call just played: the new session state, and whether the visitor now rests (the last call). */
    fun afterCall(visit: VisitState, now: Long): Pair<VisitState, Boolean> {
        val v = visit.copy(n = visit.n + 1, at = now)
        return v to (v.n >= SESSION_MAX)
    }

    // ---- The performances ----

    /**
     * The geometry, from the launcher: its short side (unit), which way the
     * screen is (-1: the launcher is on the right), the x of its edge facing
     * the screen and its height, in the stage (the launcher's own box); and
     * whether the greeting card is up.
     */
    data class Geometry(val unit: Double, val inward: Int, val edgeX: Double, val width: Double, val height: Double, val card: Boolean)

    /** The idle call's geometry for a launcher of this size on this side. */
    fun geometry(width: Double, height: Double, position: String, card: Boolean): Geometry {
        val inward = if (position == "right") -1 else 1
        return Geometry(min(width, height), inward, if (inward == -1) 0.0 else width, width, height, card)
    }

    /** Where the mascot sits (box top-left and size), its arm pose (MascotArt), and its base (mirrored when the launcher is on the left). */
    data class Mascot(val x: Double, val y: Double, val size: Double, val arm: String, val base: Map<String, Double>)

    data class Ripple(val x: Double, val y: Double, val d: Double, val track: Track)

    class Performance(
        val total: Double,
        /** When the speech bubble shows, and where it points (stage px). */
        val bubbleAt: Double,
        val sayX: Double,
        val sayY: Double,
        val mascot: Mascot,
        /** The box's own movement, and each part's (in the drawing's 100 x 100 units). */
        val self: List<Track>,
        val parts: Map<String, List<Track>>,
        /** The launcher's jolt (knock, tap). */
        val launcher: List<Track> = emptyList(),
        /** White rings where a knock / tap lands (stage px, centre and diameter). */
        val ripples: List<Ripple> = emptyList(),
        /** The bubble's pop and fade (from bubbleAt). */
        val bubble: Track,
        /** It speaks (a bubble) only when the greeting is not up. */
        val talks: Boolean,
        /** Drawn in front of the launcher (its paw lands on the button); else just behind it. */
        val front: Boolean,
    )

    private const val POP = "cubic-bezier(0.34, 1.56, 0.64, 1)"

    private fun px(x: Number, y: Number) = "${jsNum(x)}px ${jsNum(y)}px"

    /** Slide out past the facing edge, hold, slide back - fading in from behind. */
    private fun slide(dx: Double, dy: Double, total: Int, rotate: String = "0deg"): Track {
        val inAt = 380.0 / total
        return Track.of(listOf(
            mapOf("translate" to "0 0", "rotate" to "0deg", "opacity" to 0, "scale" to 0.7, "easing" to POP),
            mapOf("translate" to px(dx, dy), "rotate" to rotate, "opacity" to 1, "scale" to 1, "offset" to inAt),
            mapOf("translate" to px(dx, dy), "rotate" to rotate, "opacity" to 1, "scale" to 1, "offset" to 1 - inAt, "easing" to "ease-in"),
            mapOf("translate" to "0 0", "rotate" to "0deg", "opacity" to 0, "scale" to 0.7),
        ), duration = total, easing = "linear", fill = "forwards")
    }

    private fun ripple(unit: Double, x: Double, y: Double, delay: Double) = Ripple(
        x, y, unit * 0.55,
        Track.of(listOf(
            mapOf("opacity" to 0.95, "scale" to 0.35),
            mapOf("opacity" to 0, "scale" to 1.7),
        ), duration = 520, delay = delay, easing = "ease-out", fill = "forwards"),
    )

    /** Blink (the lids are closed for ~70ms each time). */
    private fun blink(total: Int, at: List<Double>) = Track.of(
        buildList {
            add(mapOf("transform" to "scaleY(0)"))
            for (o in at) {
                add(mapOf("transform" to "scaleY(0)", "offset" to o - 0.02))
                add(mapOf("transform" to "scaleY(1)", "offset" to o))
                add(mapOf("transform" to "scaleY(0)", "offset" to o + 0.02))
            }
            add(mapOf("transform" to "scaleY(0)"))
        },
        duration = total, fill = "forwards",
    )

    /** Where the pupils look: (offset, x, y) in drawing units. */
    private fun look(total: Int, path: List<Triple<Double, Double, Double>>) = Track.of(
        buildList {
            add(mapOf("translate" to "0 0"))
            for ((o, x, y) in path) add(mapOf("translate" to px(x, y), "offset" to o, "easing" to "ease-in-out"))
            add(mapOf("translate" to "0 0"))
        },
        duration = total, fill = "forwards",
    )

    private fun l(o: Double, x: Number, y: Number) = Triple(o, x.toDouble(), y.toDouble())

    /** One mouth ("smile" rests) between two offsets. */
    private fun mouth(total: Int, name: String, from: Double, to: Double): Map<String, List<Track>> = mapOf(
        name to listOf(Track.of(listOf(
            mapOf("opacity" to 0),
            mapOf("opacity" to 0, "offset" to from),
            mapOf("opacity" to 1, "offset" to from + 0.02),
            mapOf("opacity" to 1, "offset" to to),
            mapOf("opacity" to 0, "offset" to to + 0.02),
            mapOf("opacity" to 0),
        ), duration = total, fill = "forwards")),
        "smile" to listOf(Track.of(listOf(
            mapOf("opacity" to 1),
            mapOf("opacity" to 1, "offset" to from),
            mapOf("opacity" to 0, "offset" to from + 0.02),
            mapOf("opacity" to 0, "offset" to to),
            mapOf("opacity" to 1, "offset" to to + 0.02),
            mapOf("opacity" to 1),
        ), duration = total, fill = "forwards")),
    )

    private fun brows(total: Int, from: Double, to: Double) = Track.of(listOf(
        mapOf("translate" to "0 0"),
        mapOf("translate" to "0 0", "offset" to from),
        mapOf("translate" to "0 -3.5px", "offset" to from + 0.05),
        mapOf("translate" to "0 -3.5px", "offset" to to),
        mapOf("translate" to "0 0", "offset" to to + 0.05),
        mapOf("translate" to "0 0"),
    ), duration = total, fill = "forwards")

    private fun bubbleTrack(total: Int, at: Int) = Track.of(listOf(
        mapOf("opacity" to 0, "scale" to 0.6, "translate" to "0 8px", "easing" to POP),
        mapOf("opacity" to 1, "scale" to 1, "translate" to "0 0", "offset" to 0.12),
        mapOf("opacity" to 1, "scale" to 1, "translate" to "0 0", "offset" to 0.88, "easing" to "ease-in"),
        mapOf("opacity" to 0, "scale" to 0.9, "translate" to "0 4px"),
    ), duration = total - at, delay = at, fill = "forwards")

    /** The parts each mascot part starts from: closed-lid rest, the o and grin mouths hidden. */
    val PART_BASE: Map<String, Map<String, Double>> = mapOf(
        "lid" to mapOf("sy" to 0.0),
        "o" to mapOf("o" to 0.0),
        "grin" to mapOf("o" to 0.0),
    )

    /**
     * The performance for a mode in this geometry (PERFORMANCES): the mascot,
     * a touch bigger than the button, is centred just inside the facing edge
     * (behind the button) and moves out from there.
     */
    fun performance(mode: String, g: Geometry): Performance {
        val w = g.unit * 1.2
        fun place(dy: Double, arm: String) = Mascot(
            g.edgeX - g.inward * (min(w, g.width) / 2) - w / 2,
            g.height / 2 + dy - w / 2,
            w,
            arm,
            mapOf("sx" to if (g.inward == 1) -1.0 else 1.0),
        )
        val front = mode == "tap" || mode == "knock"
        val talks = !g.card
        when (mode) {
            "psst" -> {
                val total = 3600
                val dx = g.inward * (w * 0.5)
                return Performance(
                    total = total.toDouble(),
                    bubbleAt = 360.0,
                    sayX = g.edgeX + dx,
                    sayY = g.height / 2 - g.unit * 0.1 - w * 0.42,
                    mascot = place(-g.unit * 0.1, "whisper"),
                    self = listOf(slide(dx, -g.unit * 0.06, total, "${jsNum(g.inward * -6)}deg")),
                    parts = mouth(total, "o", 0.14, 0.82) + mapOf(
                        "brow" to listOf(brows(total, 0.14, 0.8)),
                        "look" to listOf(look(total, listOf(l(0.16, 0, 0), l(0.32, -2.5, -0.5), l(0.44, -2.5, -0.5), l(0.52, 1.5, 0), l(0.72, 0, 0.5)))),
                        "arm" to listOf(Track.of(listOf(
                            mapOf("rotate" to "0deg"),
                            mapOf("rotate" to "0deg", "offset" to 0.3),
                            mapOf("rotate" to "-8deg", "offset" to 0.36),
                            mapOf("rotate" to "0deg", "offset" to 0.42),
                            mapOf("rotate" to "-8deg", "offset" to 0.48),
                            mapOf("rotate" to "0deg", "offset" to 0.54),
                            mapOf("rotate" to "0deg"),
                        ), duration = total, fill = "forwards")),
                        "lid" to listOf(blink(total, listOf(0.62))),
                    ),
                    bubble = bubbleTrack(total, 360),
                    talks = talks,
                    front = front,
                )
            }
            "peek" -> {
                val total = 3800
                val dx = if (g.card) g.inward * (w * 0.55) else -g.inward * w * 0.15
                val dy = if (g.card) -g.unit * 0.05 else -(g.height * 0.5 + w * 0.08)
                return Performance(
                    total = total.toDouble(),
                    bubbleAt = 560.0,
                    sayX = g.edgeX + dx,
                    sayY = g.height / 2 + dy - w * 0.35,
                    mascot = place(if (g.card) 0.0 else g.height * 0.05, "down"),
                    self = listOf(Track.of(listOf(
                        mapOf("translate" to "0 0", "opacity" to 0, "easing" to "cubic-bezier(0.34, 1.56, 0.64, 1)"),
                        mapOf("translate" to px(dx, dy), "opacity" to 1, "offset" to 0.12),
                        mapOf("translate" to px(dx, dy + 3), "offset" to 0.5),
                        mapOf("translate" to px(dx, dy), "opacity" to 1, "offset" to 0.86, "easing" to "ease-in"),
                        mapOf("translate" to "0 0", "opacity" to 0),
                    ), duration = total, fill = "forwards")),
                    parts = mapOf(
                        "look" to listOf(look(total, listOf(l(0.18, 0, 0), l(0.28, -3, 0), l(0.4, -3, 0), l(0.5, 3, 0), l(0.62, 3, 0), l(0.7, 0, 1)))),
                        "brow" to listOf(brows(total, 0.2, 0.36)),
                        "lid" to listOf(blink(total, listOf(0.46, 0.76))),
                    ),
                    bubble = bubbleTrack(total, 560),
                    talks = talks,
                    front = front,
                )
            }
            "wave" -> {
                val total = 3500
                val dx = g.inward * (w * 0.62 + g.unit * 0.05)
                return Performance(
                    total = total.toDouble(),
                    bubbleAt = 320.0,
                    sayX = g.edgeX + dx,
                    sayY = g.height / 2 - g.unit * 0.1 - w * 0.46,
                    mascot = place(-g.unit * 0.08, "wave"),
                    self = listOf(slide(dx, -g.unit * 0.1, total)),
                    parts = mapOf(
                        "body" to listOf(Track.of(listOf(
                            mapOf("scale" to "1 1"),
                            mapOf("scale" to "1.12 0.88", "offset" to 0.06),
                            mapOf("scale" to "0.92 1.1", "offset" to 0.12),
                            mapOf("scale" to "1.04 0.96", "offset" to 0.18),
                            mapOf("scale" to "1 1", "offset" to 0.24),
                            mapOf("scale" to "1 1"),
                        ), duration = total, fill = "forwards")),
                    ) + mouth(total, "grin", 0.12, 0.84) + mapOf(
                        "arm" to listOf(Track.of(listOf(
                            mapOf("rotate" to "0deg"),
                            mapOf("rotate" to "0deg", "offset" to 0.14),
                            mapOf("rotate" to "-24deg", "offset" to 0.24),
                            mapOf("rotate" to "18deg", "offset" to 0.36),
                            mapOf("rotate" to "-24deg", "offset" to 0.48),
                            mapOf("rotate" to "18deg", "offset" to 0.6),
                            mapOf("rotate" to "-10deg", "offset" to 0.7),
                            mapOf("rotate" to "0deg", "offset" to 0.78),
                            mapOf("rotate" to "0deg"),
                        ), duration = total, fill = "forwards")),
                        "look" to listOf(look(total, listOf(l(0.2, 0, 1)))),
                        "lid" to listOf(blink(total, listOf(0.66))),
                    ),
                    bubble = bubbleTrack(total, 320),
                    talks = talks,
                    front = front,
                )
            }
            "knock" -> {
                val total = 3500
                val dx = g.inward * (w * 0.9)
                val beats = listOf(0.3, 0.41, 0.52)
                return Performance(
                    total = total.toDouble(),
                    bubbleAt = 320.0,
                    sayX = g.edgeX + dx,
                    sayY = g.height / 2 - g.unit * 0.08 - w * 0.42,
                    mascot = place(-g.unit * 0.04, "knock"),
                    self = listOf(slide(dx, -g.unit * 0.08, total)),
                    parts = mapOf(
                        "arm" to listOf(Track.of(buildList {
                            add(mapOf("translate" to "0 0", "rotate" to "0deg"))
                            for (o in beats) {
                                add(mapOf("translate" to "0 0", "rotate" to "0deg", "offset" to o - 0.045))
                                add(mapOf("translate" to "4px 0.5px", "rotate" to "5deg", "offset" to o, "easing" to "cubic-bezier(.5,0,.9,.4)"))
                                add(mapOf("translate" to "0 0", "rotate" to "0deg", "offset" to o + 0.05, "easing" to "cubic-bezier(.2,.8,.4,1)"))
                            }
                            add(mapOf("translate" to "0 0", "rotate" to "0deg"))
                        }, duration = total, fill = "forwards")),
                        "body" to listOf(Track.of(buildList {
                            add(mapOf("rotate" to "0deg"))
                            for (o in beats) {
                                add(mapOf("rotate" to "0deg", "offset" to o - 0.045))
                                add(mapOf("rotate" to "2.5deg", "offset" to o))
                                add(mapOf("rotate" to "0deg", "offset" to o + 0.05))
                            }
                            add(mapOf("rotate" to "0deg"))
                        }, duration = total, fill = "forwards")),
                        "lid" to listOf(Track.of(buildList {
                            add(mapOf("transform" to "scaleY(0)"))
                            for (o in beats) {
                                add(mapOf("transform" to "scaleY(0)", "offset" to o - 0.03))
                                add(mapOf("transform" to "scaleY(0.5)", "offset" to o))
                                add(mapOf("transform" to "scaleY(0)", "offset" to o + 0.04))
                            }
                            add(mapOf("transform" to "scaleY(0)"))
                        }, duration = total, fill = "forwards")),
                    ) + mouth(total, "grin", 0.26, 0.8) + mapOf(
                        "look" to listOf(look(total, listOf(l(0.2, 0, 1)))),
                    ),
                    launcher = beats.map { o ->
                        Track.of(listOf(
                            mapOf("translate" to "0 0"),
                            mapOf("translate" to "${jsNum(-g.inward * 2)}px 0", "offset" to 0.35),
                            mapOf("translate" to "0 0"),
                        ), duration = 200, delay = o * total, easing = "ease-out")
                    },
                    ripples = beats.map { o -> ripple(g.unit, g.edgeX - g.inward * g.unit * 0.08, g.height / 2 - g.unit * 0.12, o * total) },
                    bubble = bubbleTrack(total, 320),
                    talks = talks,
                    front = front,
                )
            }
        }
        // tap
        val total = 3300
        val dx = g.inward * (w * 0.98)
        val taps = listOf(0.32, 0.46)
        return Performance(
            total = total.toDouble(),
            bubbleAt = 320.0,
            sayX = g.edgeX + dx,
            sayY = g.height / 2 - w * 0.42,
            mascot = place(-g.unit * 0.02, "reach"),
            self = listOf(slide(dx, 0.0, total)),
            parts = mapOf(
                "arm" to listOf(Track.of(buildList {
                    add(mapOf("translate" to "0 0"))
                    for (o in taps) {
                        add(mapOf("translate" to "0 0", "offset" to o - 0.05))
                        add(mapOf("translate" to "5px 1px", "offset" to o, "easing" to "cubic-bezier(.5,0,.9,.4)"))
                        add(mapOf("translate" to "0 0", "offset" to o + 0.06, "easing" to "cubic-bezier(.2,.8,.4,1)"))
                    }
                    add(mapOf("translate" to "0 0"))
                }, duration = total, fill = "forwards")),
                "look" to listOf(look(total, listOf(l(0.16, 3, 1), l(0.52, 3, 1), l(0.62, 0, 0)))),
            ) + mouth(total, "grin", 0.58, 0.84) + mapOf(
                "lid" to listOf(blink(total, listOf(0.7))),
            ),
            launcher = taps.map { o ->
                Track.of(listOf(
                    mapOf("scale" to 1),
                    mapOf("scale" to 0.92, "offset" to 0.4),
                    mapOf("scale" to 1),
                ), duration = 240, delay = o * total, easing = "ease-out")
            },
            ripples = taps.map { o -> ripple(g.unit, g.edgeX - g.inward * g.unit * 0.18, g.height / 2, o * total) },
            bubble = bubbleTrack(total, 320),
            talks = talks,
            front = front,
        )
    }
}
