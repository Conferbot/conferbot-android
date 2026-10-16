package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.customization.FxPaint.conicDisc
import com.conferbot.sdk.core.customization.FxPaint.linearBox
import com.conferbot.sdk.core.customization.FxPaint.radialDisc
import com.conferbot.sdk.core.customization.FxPaint.radialWash
import com.conferbot.sdk.core.customization.FxPaint.stops
import com.conferbot.sdk.core.customization.Keyframes.fixed
import com.conferbot.sdk.core.customization.Keyframes.jsNum
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The stage laid over the launcher. Sizes come from `unit`, the button's
 * short side; (cx, cy) is its centre; `above` the distance from its bottom
 * to the top of the screen; `inward` -1 when it sits on the right. `left`,
 * `top`: the launcher's top-left on the screen; `winW`, `winH`: the screen.
 */
data class EntranceStage(
    val w: Double,
    val h: Double,
    val unit: Double,
    val cx: Double,
    val cy: Double,
    val above: Double,
    val inward: Int,
    val accent: String,
    val left: Double,
    val top: Double,
    val winW: Double,
    val winH: Double,
)

/** One drawing on the stage, with its animations. Children sit in its box and move with it. */
class FxSprite(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    /** The drawing (svg markup for w x h), or a plain box of a colour. */
    val svg: String? = null,
    val fill: String? = null,
    val radius: Double? = null,
    /** The pivot, in px inside the box (default: its centre). */
    val origin: Pair<Double, Double>? = null,
    /** Its own static values (an opacity of 0 until it plays, a fixed rotate...). */
    val base: Map<String, Double>? = null,
    val tracks: List<Track> = emptyList(),
    /** offset-path: the box's centre follows this path (px from the box's own top-left), turning with it. */
    val path: String? = null,
    /** A stroke drawn on (stroke-dashoffset 100 -> 0), shown as a wipe of the drawing: "down" or "right". */
    val wipe: String? = null,
    /** Children are clipped to the box, with this corner radius. */
    val clip: Double? = null,
    /** `left` animates as a share of this width (px). */
    val leftOf: Double? = null,
    val children: List<FxSprite> = emptyList(),
) {
    internal fun with(tracks: List<Track>, children: List<FxSprite>) =
        FxSprite(x, y, w, h, svg, fill, radius, origin, base, tracks, path, wipe, clip, leftOf, children)
}

class EntrancePlan(
    /** When the launcher itself appears (ms from the start), and how. */
    val revealAt: Double,
    val arrive: Track,
    /** When the whole effect is over and the stage goes. */
    val total: Double,
    val sprites: List<FxSprite>,
    /** Drawn just behind the launcher (it rises out of it). */
    val behind: Boolean = false,
)

/**
 * A sprite's drawn values at t ms: its translate (px, the box's own share
 * and an animated `left` folded in, and a motion path's point), rotate, the
 * scale (a 3D turn about x / y, unlit and without perspective as on the
 * web, is the cosine of its angle), opacity and how much of a wipe shows.
 */
data class SpriteFrame(val tx: Double, val ty: Double, val r: Double, val sx: Double, val sy: Double, val o: Double, val reveal: Double)

/**
 * Launcher entrance, as the web widget plays it (conferbot-widget
 * src/ts/launcher/entrance.ts): how the launcher arrives the first time it
 * shows, instead of simply being there.
 *
 *   customizations.launcherEntrance:
 *     "classic"   (default) it just appears;
 *     "lightning" a bolt strikes the corner, and the launcher is in the flash;
 *     "rocket"    a rocket comes down, burns to land, and becomes the launcher;
 *     "portal"    a ring of light opens and the launcher rises out of it;
 *     "ufo"       a saucer flies in and beams the launcher down;
 *     "drop"      it falls in, squashes on landing, and bounces;
 *     "confetti"  it pops in with a burst of confetti;
 *     "coin"      it flips in like a coin and catches the light;
 *     "meteor", "magic", "parachute", "plane", "egg" (newer, same rules).
 *
 * The rules (ui/compose/LauncherFxControllers.kt keeps them): once per app
 * session per bot and entrance; never when the chat is open; nothing with
 * reduced motion. The launcher is held invisible until the effect reveals
 * it (it still takes a tap, which ends the entrance and opens the chat).
 * While it lands the greeting card stays hidden; 150ms after it has landed
 * the card fades up (0.9s). Attention and the idle call wait for it.
 *
 * Each effect is a set of sprites (drawings with their Web Animations
 * tracks) on a stage laid over the launcher, plus the launcher's own
 * arrival track. Filters (brightness, glows, blur) are left out, as in RN;
 * the movement, timing and opacity are the web's. Ported from the React
 * Native SDK's core/launcherEntrance.ts. Pure: the launcher drives the clock.
 */
object LauncherEntrance {
    val MODES = listOf("classic", "lightning", "rocket", "portal", "ufo", "drop", "confetti", "coin", "meteor", "magic", "parachute", "plane", "egg")

    fun normalize(value: Any?): String = (value as? String)?.takeIf { it in MODES } ?: "classic"

    /** Whether this mount plays an entrance. */
    fun shouldPlay(mode: String, reducedMotion: Boolean, chatOpen: Boolean, played: Boolean) =
        mode != "classic" && !reducedMotion && !chatOpen && !played

    /** After the launcher lands: a beat, then the rest fades up. */
    const val SETTLE_BEAT_MS = 150
    const val SETTLE_FADE_MS = 900

    /** The launcher must be laid out this long before the effect starts. */
    const val START_MS = 60L

    /** The greeting (and anything else beside the launcher) fading up after the landing (cb-ent-reveal). */
    val SETTLE_REVEAL: Track = Track.of(listOf(
        mapOf("opacity" to 0, "translate" to "0 10px"),
        mapOf("opacity" to 1, "translate" to "0 0"),
    ), duration = SETTLE_FADE_MS, easing = "cubic-bezier(0.22, 1, 0.36, 1)", fill = "both")

    fun stage(width: Double, height: Double, position: String, screenWidth: Double, screenHeight: Double, side: Double, bottom: Double, accent: String): EntranceStage {
        val left = if (position == "right") screenWidth - side - width else side
        val top = screenHeight - bottom - height
        return EntranceStage(
            w = width, h = height, unit = min(width, height), cx = width / 2, cy = height / 2,
            above = top + height,
            inward = if (left + width / 2 > screenWidth / 2) -1 else 1,
            accent = accent, left = left, top = top, winW = screenWidth, winH = screenHeight,
        )
    }

    data class Timeline(val landAt: Double, val landedAt: Double, val settleAt: Double, val endAt: Double)

    /** When each phase starts: the landing, the rest fading up, the end. */
    fun timeline(p: EntrancePlan): Timeline {
        val landedAt = p.revealAt + p.arrive.options.duration
        return Timeline(p.revealAt, landedAt, landedAt + SETTLE_BEAT_MS, max(p.total, landedAt + SETTLE_BEAT_MS + SETTLE_FADE_MS) + 60)
    }

    private val COLORS = listOf("#f59e0b", "#ec4899", "#22c55e", "#3b82f6", "#a855f7", "#ef4444")

    /** The web's seeded random (a small LCG), 0..1. */
    fun seeded(seed: Int): () -> Double {
        var r = if (seed == 0) 1L else seed.toLong()
        return {
            r = (r * 9301 + 49297) % 233280
            r / 233280.0
        }
    }

    /** A lightning channel by midpoint displacement: points, top to bottom. */
    fun boltPoints(x1: Double, y1: Double, x2: Double, y2: Double, spread: Double, rand: () -> Double, depth: Int = 6): MutableList<Pair<Double, Double>> {
        var pts = mutableListOf(x1 to y1, x2 to y2)
        var disp = spread
        for (d in 0 until depth) {
            val next = mutableListOf(pts[0])
            for (i in 1 until pts.size) {
                val (ax, ay) = pts[i - 1]
                val (bx, by) = pts[i]
                val mx = (ax + bx) / 2 + (rand() - 0.5) * disp
                val my = (ay + by) / 2 + (rand() - 0.5) * disp * 0.25
                next.add(mx to my)
                next.add(pts[i])
            }
            pts = next
            disp *= 0.55
        }
        return pts
    }

    private fun n(x: Number) = jsNum(x)
    private fun px(x: Number) = "${jsNum(x)}px"
    private fun deg(x: Number) = "${jsNum(x)}deg"
    private fun tr(x: Number, y: Number) = "${px(x)} ${px(y)}"

    private fun toPath(pts: List<Pair<Double, Double>>) = "M" + pts.joinToString(" L") { "${fixed(it.first, 1)},${fixed(it.second, 1)}" }
    private fun svgOf(w: Double, h: Double, vb: String, inner: String) =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${fixed(w, 2)}\" height=\"${fixed(h, 2)}\" viewBox=\"$vb\">$inner</svg>"

    private fun play(keyframes: List<Keyframe>, duration: Number, delay: Number? = null, iterations: Number? = null, easing: String? = null, fill: String? = null) =
        Track.of(keyframes, duration, delay, iterations, easing, fill)

    private fun kf(vararg p: Pair<String, Any?>): Keyframe = linkedMapOf(*p)

    // ---- Shared pieces ----

    /** A soft smoke puff at (x, y), drifting by (dx, dy) as it grows and fades. */
    private fun puff(x: Double, y: Double, d: Double, delay: Number, dx: Double, dy: Double, dur: Number = 900, tint: String = "rgba(203, 213, 225, 0.9)") = FxSprite(
        x = x - d / 2, y = y - d / 2, w = d, h = d,
        svg = radialDisc(d, d, stops(tint, "rgba(148, 163, 184, 0)")),
        base = mapOf("o" to 0.0),
        tracks = listOf(play(listOf(
            kf("opacity" to 0, "translate" to "0 0", "scale" to 0.4),
            kf("opacity" to 0.9, "offset" to 0.2),
            kf("opacity" to 0, "translate" to tr(dx, dy), "scale" to 1.9),
        ), duration = dur, delay = delay, easing = "ease-out", fill = "forwards")),
    )

    /** A ground shockwave: a flattened ring racing out from under the button. */
    private fun shockwave(s: EntranceStage, delay: Number, color: String = "rgba(255, 255, 255, 0.9)", spread: Number = 2.6): FxSprite {
        val w = s.w + s.unit * 0.4
        val h = s.unit * 0.5
        val e = "<ellipse cx=\"${n(w / 2)}\" cy=\"${n(h / 2)}\" rx=\"${n(w / 2 - 1)}\" ry=\"${n(h / 2 - 1)}\" fill=\"none\" stroke=\"$color\""
        return FxSprite(
            x = s.cx - w / 2, y = s.h - s.unit * 0.25, w = w, h = h,
            // A 2px border with a soft glow (box-shadow 0 0 10px).
            svg = svgOf(w, h, "0 0 ${n(w)} ${n(h)}", "$e stroke-width=\"6\" stroke-opacity=\"0.25\"/>$e stroke-width=\"2\"/>"),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0.95, "scale" to 0.4),
                kf("opacity" to 0, "scale" to spread),
            ), duration = 650, delay = delay, easing = "cubic-bezier(0.1, 0.8, 0.3, 1)", fill = "forwards")),
        )
    }

    private const val STAR4 = "M6 0 L7.2 4.8 L12 6 L7.2 7.2 L6 12 L4.8 7.2 L0 6 L4.8 4.8 Z"

    /** Twinkling four-point stars round the button. */
    private fun sparkles(s: EntranceStage, count: Int, delay: Number, color: String = "#fde047", reach: Double = 1.0) = List(count) { i ->
        val a = (i.toDouble() / count) * PI * 2 + 0.4
        val x = s.cx + cos(a) * (s.w / 2 + s.unit * 0.3 * reach)
        val y = s.cy + sin(a) * (s.h / 2 + s.unit * 0.3 * reach)
        val size = (9 + (i % 3) * 4).toDouble()
        FxSprite(
            x = x - size / 2, y = y - size / 2, w = size, h = size,
            svg = svgOf(size, size, "0 0 12 12", "<path d=\"$STAR4\" fill=\"$color\"/>"),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0, "rotate" to "0deg"),
                kf("opacity" to 1, "scale" to 1.25, "rotate" to "60deg", "offset" to 0.45),
                kf("opacity" to 0, "scale" to 0.3, "rotate" to "120deg"),
            ), duration = 620, delay = delay.toDouble() + i * 55, easing = "ease-out", fill = "forwards")),
        )
    }

    /** Bits thrown up from the button's base that fall back with gravity. */
    private fun debris(s: EntranceStage, count: Int, delay: Number, colors: List<String>, size: Number = 5, height: Double = 1.0) = List(count) { i ->
        val dir = if (i % 2 == 1) 1 else -1
        val dx = dir * (s.w / 2 + s.unit * (0.2 + (i % 4) * 0.22))
        val up = s.unit * (0.5 + ((i * 3) % 5) * 0.16) * height
        val d = size.toDouble() + (i % 3) * 2
        FxSprite(
            x = s.cx - d / 2, y = s.h - d, w = d, h = d * 0.8,
            fill = colors[i % colors.size], radius = d * 0.35,
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 1, "translate" to "0 0", "rotate" to "0deg", "easing" to "cubic-bezier(0.2, 0.7, 0.4, 1)"),
                kf("opacity" to 1, "translate" to "${px(dx * 0.6)} -${px(up)}", "rotate" to deg(dir * 200), "offset" to 0.4, "easing" to "cubic-bezier(0.5, 0, 0.9, 0.5)"),
                kf("opacity" to 0, "translate" to tr(dx, s.unit * 0.2), "rotate" to deg(dir * 420)),
            ), duration = 820, delay = delay, fill = "forwards")),
        )
    }

    /** A launcher arrival that lands with a jolt (for impacts). */
    private fun joltArrival(duration: Number, from: Keyframe) = play(listOf(
        LinkedHashMap<String, Any?>().apply { putAll(kf("opacity" to 0, "scale" to 0.4, "translate" to "0 0")); putAll(from) },
        kf("opacity" to 1, "scale" to 1.15, "translate" to "-3px 0", "offset" to 0.18),
        kf("scale" to 0.95, "translate" to "3px 1px", "offset" to 0.3),
        kf("scale" to 1.03, "translate" to "-2px 0", "offset" to 0.42),
        kf("scale" to 1, "translate" to "1px 0", "offset" to 0.54),
        kf("opacity" to 1, "scale" to 1, "translate" to "0 0", "filter" to "none"),
    ), duration = duration, easing = "linear")

    /** A sprite's tracks delayed (lightning: everything after the storm gathers). */
    private fun later(sp: FxSprite, ms: Double): FxSprite =
        sp.with(sp.tracks.map { it.withDelay(it.options.delay + ms) }, sp.children.map { later(it, ms) })

    private val EFFECTS: Map<String, (EntranceStage, () -> Double) -> EntrancePlan> = mapOf(
        "lightning" to ::lightning,
        "rocket" to { s, _ -> rocket(s) },
        "portal" to { s, _ -> portal(s) },
        "ufo" to { s, _ -> ufo(s) },
        "drop" to { s, _ -> drop(s) },
        "confetti" to { s, _ -> confetti(s) },
        "coin" to { s, _ -> coin(s) },
        "meteor" to { s, _ -> meteor(s) },
        "magic" to { s, _ -> magic(s) },
        "parachute" to { s, _ -> parachute(s) },
        "plane" to { s, _ -> plane(s) },
        "egg" to { s, _ -> egg(s) },
    )

    // A storm gathers (the corner of the screen dims), then a blue-white bolt
    // with forks races down, flashes, restrikes and fades; sky light, a burst,
    // crackles and a wisp of smoke at the strike point.
    private fun lightning(s: EntranceStage, rand: () -> Double): EntrancePlan {
        val dark = 240.0
        val sprites = listOf(FxSprite(
            x = -s.left, y = -s.top, w = s.winW, h = s.winH,
            svg = radialWash(s.winW, s.winH, s.left + s.cx, s.top, s.winW * 0.6, s.winH * 0.85,
                stops("rgba(8, 12, 32, 0.55)", "rgba(8, 12, 32, 0.18)" to 55, "rgba(8, 12, 32, 0)" to 80)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0),
                kf("opacity" to 1, "offset" to 0.2),
                kf("opacity" to 1, "offset" to 0.45),
                kf("opacity" to 0),
            ), duration = 1150, easing = "ease-in-out", fill = "forwards")),
        ))
        val after = mutableListOf<FxSprite>()
        val h = min(s.above + s.cy, 560.0)
        val bw = 220.0
        val x = bw / 2
        val main = boltPoints(x, 0.0, x, h, max(24.0, h * 0.22), rand)
        main[main.size - 1] = x to h
        val branches = mutableListOf<String>()
        for (i in 0 until 3) {
            val at = main[floor(main.size * (0.25 + i * 0.18 + rand() * 0.08)).toInt()]
            val dir = if (rand() > 0.5) 1 else -1
            val len = h * (0.18 + rand() * 0.16)
            val b = boltPoints(at.first, at.second, at.first + dir * len * (0.4 + rand() * 0.4), at.second + len, len * 0.35, rand, 4)
            branches.add(toPath(b))
        }
        val all = listOf(toPath(main)) + branches
        // Glow, halo and core; the blurred layers are a little fainter without the blur.
        fun layer(d: String, width: Number, color: String, opacity: Double) =
            "<path d=\"$d\" fill=\"none\" stroke=\"$color\" stroke-width=\"${n(width)}\" stroke-opacity=\"${n(opacity)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
        val bolt = all.indices.joinToString("") { i -> layer(all[i], if (i > 0) 8 else 16, "#6d9eff", (if (i > 0) 0.35 else 0.45) * 0.55) } +
            all.indices.joinToString("") { i -> layer(all[i], if (i > 0) 2.6 else 5, "#c7dcff", (if (i > 0) 0.7 else 0.95) * 0.85) } +
            all.indices.joinToString("") { i -> layer(all[i], if (i > 0) 1 else 2, "#ffffff", 1.0) }
        after.add(FxSprite(
            x = s.cx - x, y = s.cy - h, w = bw, h = h,
            svg = svgOf(bw, h, "0 0 ${n(bw)} ${n(h)}", bolt),
            // Stepped leader: every channel races down in ~110ms.
            wipe = "down",
            base = mapOf("dash" to 100.0),
            tracks = listOf(
                play(listOf(kf("strokeDashoffset" to 100), kf("strokeDashoffset" to 0)), duration = 110, easing = "linear", fill = "forwards"),
                // Return stroke, a dim beat, one restrike, then the channel fades.
                play(listOf(
                    kf("opacity" to 1),
                    kf("opacity" to 1, "offset" to 0.2),
                    kf("opacity" to 0.25, "offset" to 0.3),
                    kf("opacity" to 1, "offset" to 0.38),
                    kf("opacity" to 0.55, "offset" to 0.55),
                    kf("opacity" to 0),
                ), duration = 560, fill = "forwards"),
            ),
        ))
        // Sky light: the screen lights up blue-white round the strike, twice.
        val skyH = s.top + s.h
        after.add(FxSprite(
            x = -s.left, y = -s.top, w = s.winW, h = skyH,
            svg = radialWash(s.winW, skyH, s.left + s.cx, skyH * 0.55, s.winW * 0.45, skyH * 0.7, stops("rgba(210, 226, 255, 0.55)", "rgba(210, 226, 255, 0)" to 70)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0),
                kf("opacity" to 0.85, "offset" to 0.2),
                kf("opacity" to 0.12, "offset" to 0.34),
                kf("opacity" to 0.6, "offset" to 0.42),
                kf("opacity" to 0),
            ), duration = 620, fill = "forwards")),
        ))
        val d = s.unit * 3.2
        after.add(FxSprite(
            x = s.cx - d / 2, y = s.cy - d / 2, w = d, h = d,
            svg = radialDisc(d, d, stops("#ffffff", "rgba(186, 212, 255, 0.9)" to 30, "rgba(109, 158, 255, 0.35)" to 55, "transparent" to 75)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0.2),
                kf("opacity" to 1, "scale" to 1, "offset" to 0.25),
                kf("opacity" to 0.3, "scale" to 1.1, "offset" to 0.5),
                kf("opacity" to 0, "scale" to 1.45),
            ), duration = 640, delay = 100, easing = "ease-out", fill = "forwards")),
        ))
        // Crackles: tiny arcs that flicker round the button.
        for (i in 0 until 7) {
            val a = (i / 7.0) * PI * 2 + rand()
            val r1 = s.unit * 0.55
            val r2 = s.unit * (0.95 + rand() * 0.35)
            val pts = boltPoints(cos(a) * r1, sin(a) * r1, cos(a) * r2, sin(a) * r2, s.unit * 0.25, rand, 3)
            val x0 = pts.minOf { it.first } - 2
            val y0 = pts.minOf { it.second } - 2
            val cw = pts.maxOf { it.first } - x0 + 2
            val ch = pts.maxOf { it.second } - y0 + 2
            after.add(FxSprite(
                x = s.cx + x0, y = s.cy + y0, w = cw, h = ch,
                svg = svgOf(cw, ch, "${n(x0)} ${n(y0)} ${n(cw)} ${n(ch)}", "<path d=\"${toPath(pts)}\" fill=\"none\" stroke=\"#e0ecff\" stroke-width=\"1.6\" stroke-linecap=\"round\"/>"),
                base = mapOf("o" to 0.0),
                tracks = listOf(play(listOf(
                    kf("opacity" to 0),
                    kf("opacity" to 1, "offset" to 0.1),
                    kf("opacity" to 0.1, "offset" to 0.3),
                    kf("opacity" to 1, "offset" to 0.45),
                    kf("opacity" to 0),
                ), duration = 380, delay = 110 + i * 35, fill = "forwards")),
            ))
        }
        // A wisp of smoke once it has struck.
        for (i in 0 until 3) {
            val pd = s.unit * (0.5 + i * 0.15)
            after.add(FxSprite(
                x = s.cx - pd / 2 + (i - 1) * s.unit * 0.25, y = s.cy - pd / 2, w = pd, h = pd,
                svg = radialDisc(pd, pd, stops("rgba(203, 213, 225, 0.85)", "rgba(148, 163, 184, 0)")),
                base = mapOf("o" to 0.0),
                tracks = listOf(play(listOf(
                    kf("opacity" to 0, "translate" to "0 0", "scale" to 0.5),
                    kf("opacity" to 0.55, "offset" to 0.3),
                    kf("opacity" to 0, "translate" to "0 -${px(s.unit * 0.9)}", "scale" to 1.6),
                ), duration = 1100, delay = 320 + i * 90, easing = "ease-out", fill = "forwards")),
            ))
        }
        return EntrancePlan(
            revealAt = dark + 110,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to 0.4, "translate" to "0 0", "filter" to "brightness(3) drop-shadow(0 0 18px #9ec5ff)"),
                kf("opacity" to 1, "scale" to 1.16, "translate" to "-3px 0", "offset" to 0.14, "filter" to "brightness(2.2) drop-shadow(0 0 16px #9ec5ff)"),
                kf("scale" to 0.96, "translate" to "3px 1px", "offset" to 0.24),
                kf("scale" to 1.03, "translate" to "-2px 0", "offset" to 0.34, "filter" to "brightness(1.4) drop-shadow(0 0 12px #9ec5ff)"),
                kf("scale" to 1, "translate" to "1px 0", "offset" to 0.44),
                kf("opacity" to 1, "scale" to 1, "translate" to "0 0", "filter" to "brightness(1) drop-shadow(0 0 0 rgba(158, 197, 255, 0))"),
            ), duration = 1100, easing = "ease-out"),
            total = dark + 1500,
            sprites = sprites + after.map { later(it, dark) },
        )
    }

    // A rocket comes down on a flickering flame that burns harder as it slows,
    // leaving a smoke trail; legs unfold for touchdown, a dust ring races out,
    // and the rocket becomes the button.
    private fun rocket(s: EntranceStage): EntrancePlan {
        val w = s.unit * 0.78
        val h = s.unit * 1.6
        val k = w / 40
        val accent = s.accent
        // The drawing reaches past its 40 x 82 box (the flame, the fins): drawn padded.
        fun pad(inner: String, ox: Double, oy: Double, tracks: List<Track> = emptyList()) =
            FxSprite(x = -5 * k, y = -2 * k, w = 50 * k, h = 92 * k, origin = (ox + 5) * k to (oy + 2) * k, svg = svgOf(50 * k, 92 * k, "-5 -2 50 92", inner), tracks = tracks)
        val body = "<defs><linearGradient id=\"b\" x1=\"0\" x2=\"1\"><stop offset=\"0\" stop-color=\"#94a3b8\"/><stop offset=\"0.4\" stop-color=\"#ffffff\"/><stop offset=\"0.7\" stop-color=\"#e2e8f0\"/><stop offset=\"1\" stop-color=\"#64748b\"/></linearGradient>" +
            "<radialGradient id=\"w\" cx=\"0.35\" cy=\"0.35\" r=\"0.7\"><stop offset=\"0\" stop-color=\"#e0f2fe\"/><stop offset=\"1\" stop-color=\"$accent\"/></radialGradient></defs>" +
            "<path d=\"M20 1 C32 11 34 28 33 46 L33 58 L7 58 L7 46 C6 28 8 11 20 1 Z\" fill=\"url(#b)\" stroke=\"#64748b\" stroke-width=\"1\"/>" +
            "<path d=\"M7 38 L-1 54 L7 57 Z M33 38 L41 54 L33 57 Z\" fill=\"$accent\" stroke=\"rgba(15,23,42,0.35)\" stroke-width=\"1\"/>" +
            "<path d=\"M14 10 Q20 4 26 10\" fill=\"none\" stroke=\"$accent\" stroke-width=\"3\" stroke-linecap=\"round\"/>" +
            "<circle cx=\"20\" cy=\"27\" r=\"7\" fill=\"url(#w)\" stroke=\"#64748b\" stroke-width=\"1.4\"/>" +
            "<ellipse cx=\"17.5\" cy=\"24.5\" rx=\"2.4\" ry=\"1.6\" fill=\"#fff\" opacity=\"0.9\"/>" +
            "<path d=\"M12 22 V50\" stroke=\"#fff\" stroke-width=\"2\" opacity=\"0.7\" stroke-linecap=\"round\"/>"
        val flame = "<defs><radialGradient id=\"f\" cx=\"0.5\" cy=\"0.1\" r=\"0.9\"><stop offset=\"0\" stop-color=\"#fffbeb\"/><stop offset=\"0.35\" stop-color=\"#fde047\"/><stop offset=\"0.7\" stop-color=\"#fb923c\"/><stop offset=\"1\" stop-color=\"#ef4444\" stop-opacity=\"0\"/></radialGradient></defs>" +
            "<ellipse cx=\"20\" cy=\"70\" rx=\"9\" ry=\"16\" fill=\"url(#f)\"/><ellipse cx=\"20\" cy=\"66\" rx=\"5\" ry=\"10\" fill=\"#fffbeb\" opacity=\"0.9\"/>"
        val legs = "<path d=\"M9 50 L3 64 M31 50 L37 64\" stroke=\"#475569\" stroke-width=\"2.4\" stroke-linecap=\"round\"/>"
        val fall = s.above + h + 40
        val dur = 1250
        val decel = "cubic-bezier(0.22, 0.7, 0.3, 1)"
        val legsL = pad(legs, 20.0, 56.0, listOf(play(listOf(
            kf("scale" to "1 0"),
            kf("scale" to "1 0", "offset" to 0.7),
            kf("scale" to "1 1"),
        ), duration = dur, fill = "forwards")))
        val flameL = pad(flame, 20.0, 58.0, listOf(
            play(listOf(kf("scale" to "1 1"), kf("scale" to "0.85 1.15"), kf("scale" to "1.05 0.9"), kf("scale" to "1 1")), duration = 120, iterations = ceil(dur / 120.0).toInt()),
            play(listOf(kf("opacity" to 0.8), kf("opacity" to 1, "offset" to 0.7), kf("opacity" to 0)), duration = dur + 100, fill = "forwards"),
        ))
        val rocket = FxSprite(
            x = s.cx - w / 2, y = s.h - h, w = w, h = h,
            tracks = listOf(
                play(listOf(
                    kf("translate" to "0 -${px(fall)}", "rotate" to "-3deg"),
                    kf("translate" to "0 -10px", "rotate" to "1.5deg", "offset" to 0.88),
                    kf("translate" to "0 0", "rotate" to "0deg"),
                ), duration = dur, easing = decel, fill = "forwards"),
                play(listOf(kf("opacity" to 1, "scale" to 1), kf("opacity" to 0, "scale" to 0.5)), duration = 260, delay = dur + 60, easing = "ease-in", fill = "forwards"),
            ),
            children = listOf(legsL, flameL, pad(body, 20.0, 41.0)),
        )
        val sprites = mutableListOf<FxSprite>()
        // Smoke left along the path: where the rocket is at each moment.
        fun ease(t: Double) = 1 - (1 - t).pow(3)
        for (i in 0 until 9) {
            val t = 0.1 + i * 0.09
            val y = s.h - (1 - ease(t)) * fall + s.unit * 0.1
            if (y < -s.top) continue
            sprites.add(puff(s.cx + if (i % 2 == 1) 3 else -3, y, s.unit * (0.35 + i * 0.03), t * dur, (if (i % 2 == 1) 1 else -1) * s.unit * 0.25, -s.unit * 0.2, 1100))
        }
        sprites.add(rocket)
        // Touchdown: a dust ring, dust both ways.
        sprites.add(shockwave(s, dur - 40, "rgba(226, 232, 240, 0.95)"))
        for (i in 0 until 8) {
            val dir = if (i % 2 == 1) 1 else -1
            sprites.add(puff(s.cx, s.h - s.unit * 0.1, s.unit * (0.35 + (i % 3) * 0.12), dur - 60 + i * 25, dir * (s.w / 2 + s.unit * (0.3 + (i shr 1) * 0.25)), -s.unit * 0.15, 900))
        }
        return EntrancePlan(revealAt = dur + 80.0, arrive = joltArrival(520, emptyMap()), total = dur + 1100.0, sprites = sprites)
    }

    // A flat, glowing vortex opens on the ground under the button (rings
    // spinning inside a squashed frame), sparks spiral in, a column of light
    // rises; drawn BEHIND the button, which rises out of it; a flash closes it.
    private fun portal(s: EntranceStage): EntrancePlan {
        val bw = max(s.w * 1.35, s.unit * 2)
        val bh = bw * 0.32
        val cy = s.h - bh * 0.15
        fun size(inset: Double) = bw - inset * 2
        fun disc(inset: Double, svg: String, spin: Int, dur: Int) = FxSprite(
            x = inset, y = inset, w = bw - inset * 2, h = bw - inset * 2, svg = svg,
            tracks = if (spin != 0) listOf(play(listOf(kf("rotate" to "0deg"), kf("rotate" to deg(spin * 360))), duration = dur, iterations = Double.POSITIVE_INFINITY)) else emptyList(),
        )
        // The ring mask (transparent 52%, solid 60-86%, gone at 100%), as a band.
        val ring = 0.56 to 0.93
        val well = FxSprite(
            x = 0.0, y = 0.0, w = bw, h = bw,
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0),
                kf("opacity" to 1, "scale" to 1.08, "offset" to 0.2, "easing" to "ease-out"),
                kf("opacity" to 1, "scale" to 1, "offset" to 0.3),
                kf("opacity" to 1, "scale" to 1, "offset" to 0.76, "easing" to "ease-in"),
                kf("opacity" to 1, "scale" to 0.08, "offset" to 0.92),
                kf("opacity" to 0, "scale" to 0),
            ), duration = 1650, fill = "forwards")),
            children = listOf(
                disc(-bw * 0.12, radialDisc(size(-bw * 0.12), size(-bw * 0.12), stops(s.accent, "rgba(168, 85, 247, 0.55)" to 45, "transparent" to 72)), 0, 0),
                disc(0.0, conicDisc(size(0.0), 0.0, stops(s.accent, "#a855f7", "#22d3ee", "#f0abfc", s.accent), ring), 1, 1100),
                disc(bw * 0.08, conicDisc(size(bw * 0.08), 90.0, stops("#22d3ee", "transparent" to 25, "#a855f7" to 50, "transparent" to 75, "#22d3ee"), ring), -1, 700),
                disc(bw * 0.16, conicDisc(size(bw * 0.16), 45.0, stops("#ffffff", "transparent" to 20, "#f0abfc" to 50, "transparent" to 70, "#ffffff"), ring), 1, 520),
                disc(bw * 0.22, radialDisc(size(bw * 0.22), size(bw * 0.22), stops("#020617", "#1e1b4b" to 55, "#4c1d95" to 80, "rgba(76, 29, 149, 0)")), 0, 0),
                disc(bw * 0.3, conicDisc(size(bw * 0.3), 0.0, stops("rgba(165, 243, 252, 0.7)", "transparent" to 30, "rgba(240, 171, 252, 0.6)" to 60, "transparent" to 85, "rgba(165, 243, 252, 0.7)")), 1, 900),
            ),
        )
        // Sparks drawn in along a spiral: each on an arm spinning inside the frame.
        val arms = List(12) { i ->
            val d = (4 + (i % 3) * 1.5) * (bw / bh) * 0.35
            val c = if (i % 2 == 1) "#f5d0fe" else "#cffafe"
            FxSprite(
                x = bw / 2, y = bw / 2, w = 0.0, h = 0.0, origin = 0.0 to 0.0,
                tracks = listOf(play(listOf(kf("rotate" to deg(i * 30)), kf("rotate" to deg(i * 30 + 420))), duration = 950, delay = 180 + i * 35, easing = "ease-in", fill = "forwards")),
                children = listOf(FxSprite(
                    x = bw * 0.62, y = -d / 2, w = d, h = d,
                    svg = radialDisc(d, d, stops(c, c to 70, "transparent")),
                    base = mapOf("o" to 0.0),
                    tracks = listOf(play(listOf(
                        kf("opacity" to 0, "translate" to "0 0"),
                        kf("opacity" to 1, "offset" to 0.2),
                        kf("opacity" to 0, "translate" to "-${px(bw * 0.62)} 0"),
                    ), duration = 950, delay = 180 + i * 35, easing = "ease-in", fill = "forwards")),
                )),
            )
        }
        val frame = FxSprite(x = s.cx - bw / 2, y = cy - bw / 2, w = bw, h = bw, base = mapOf("sy" to bh / bw), children = listOf(well) + arms)
        val colW = min(bw * 0.62, s.w + s.unit * 0.3)
        val colH = s.unit * 2.2
        val col = FxSprite(
            x = s.cx - colW / 2, y = cy - colH, w = colW, h = colH, origin = colW / 2 to colH,
            svg = linearBox(colW, colH, 0.0, stops("rgba(240, 171, 252, 0.75)", "rgba(165, 243, 252, 0.35)" to 50, "rgba(165, 243, 252, 0)"), listOf(colW / 2, colW / 2, 0.0, 0.0)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to "0.4 0"),
                kf("opacity" to 1, "scale" to "1 1", "offset" to 0.35),
                kf("opacity" to 0.6, "offset" to 0.7),
                kf("opacity" to 0, "scale" to "0.6 1.1"),
            ), duration = 1000, delay = 380, easing = "ease-out", fill = "forwards")),
        )
        val flash = FxSprite(
            x = s.cx - bw / 2, y = cy - bh * 0.3, w = bw, h = bh * 0.6,
            svg = radialDisc(bw, bh * 0.6, stops("#ffffff", "rgba(165, 243, 252, 0.7)" to 40, "transparent" to 75)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to "0.3 1"),
                kf("opacity" to 1, "scale" to "1.3 1", "offset" to 0.35),
                kf("opacity" to 0, "scale" to "0.05 0.4"),
            ), duration = 420, delay = 1250, easing = "ease-out", fill = "forwards")),
        )
        return EntrancePlan(
            revealAt = 450.0,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to 0.55, "translate" to "0 ${px(s.unit * 0.7)}", "filter" to "brightness(2.2) drop-shadow(0 0 16px #a5f3fc)", "easing" to "cubic-bezier(0.2, 0.8, 0.3, 1.2)"),
                kf("opacity" to 1, "scale" to 1.08, "translate" to "0 -${px(s.unit * 0.18)}", "offset" to 0.6, "filter" to "brightness(1.4) drop-shadow(0 0 10px #c084fc)"),
                kf("opacity" to 1, "scale" to 0.98, "translate" to "0 2px", "offset" to 0.8),
                kf("opacity" to 1, "scale" to 1, "translate" to "0 0", "filter" to "brightness(1) drop-shadow(0 0 0 rgba(192, 132, 252, 0))"),
            ), duration = 820),
            total = 1750.0,
            sprites = listOf(frame, col, flash),
            behind = true,
        )
    }

    // A saucer with chasing rim lights glides in and hovers; its tractor beam
    // lifts sparkles and draws the button down, top to bottom; then it zips off.
    private fun ufo(s: EntranceStage): EntrancePlan {
        val w = s.unit * 1.6
        val h = s.unit * 0.8
        val k = w / 60
        val lift = s.unit * 1.9
        val saucer = "<defs><radialGradient id=\"d\" cx=\"0.38\" cy=\"0.3\" r=\"0.8\"><stop offset=\"0\" stop-color=\"#ffffff\"/><stop offset=\"0.5\" stop-color=\"#bae6fd\"/><stop offset=\"1\" stop-color=\"#0ea5e9\"/></radialGradient>" +
            "<linearGradient id=\"h\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"#f1f5f9\"/><stop offset=\"0.5\" stop-color=\"#94a3b8\"/><stop offset=\"1\" stop-color=\"#334155\"/></linearGradient></defs>" +
            "<ellipse cx=\"30\" cy=\"12\" rx=\"12\" ry=\"10.5\" fill=\"url(#d)\" stroke=\"#334155\" stroke-width=\"1\"/>" +
            "<ellipse cx=\"26\" cy=\"7.5\" rx=\"4\" ry=\"2.4\" fill=\"#fff\" opacity=\"0.85\"/>" +
            "<ellipse cx=\"30\" cy=\"18.5\" rx=\"29\" ry=\"8.5\" fill=\"url(#h)\" stroke=\"#1e293b\" stroke-width=\"1\"/>" +
            "<ellipse cx=\"30\" cy=\"16\" rx=\"24\" ry=\"2.2\" fill=\"${s.accent}\" opacity=\"0.85\"/>"
        val lxs = listOf(8, 19, 30, 41, 52)
        val lights = lxs.indices.map { i ->
            val ly = if (i == 2) 22.5 else if (i == 1 || i == 3) 21.5 else 19.5
            val r = 2.1 * k
            FxSprite(
                x = lxs[i] * k - r, y = ly * k - r, w = r * 2, h = r * 2, fill = "#fde047", radius = r,
                tracks = listOf(play(listOf(
                    kf("opacity" to 0.25),
                    kf("opacity" to 1, "offset" to 0.2),
                    kf("opacity" to 0.25, "offset" to 0.4),
                    kf("opacity" to 0.25),
                ), duration = 500, delay = i * 100, iterations = Double.POSITIVE_INFINITY)),
            )
        }
        val from = if (s.inward == -1) s.winW - s.left + w else -(s.left + w * 2)
        val ufo = FxSprite(
            x = s.cx - w / 2, y = -lift, w = w, h = h, origin = w * 0.5 to h * 0.62,
            svg = svgOf(w, h, "0 0 60 30", saucer),
            tracks = listOf(play(listOf(
                kf("translate" to "${px(from)} -70px", "rotate" to deg(s.inward * -16), "easing" to "cubic-bezier(0.2, 0.8, 0.3, 1)"),
                kf("translate" to "0 0", "rotate" to deg(s.inward * 5), "offset" to 0.3),
                kf("translate" to "0 -4px", "rotate" to "0deg", "offset" to 0.42),
                kf("translate" to "0 3px", "rotate" to "0deg", "offset" to 0.56),
                kf("translate" to "0 -2px", "rotate" to "0deg", "offset" to 0.7),
                kf("translate" to "0 0", "rotate" to "0deg", "offset" to 0.78, "easing" to "cubic-bezier(0.6, 0, 0.9, 0.4)"),
                kf("translate" to "${px(s.inward * -40)} -${px(s.above + lift + 120)}", "rotate" to deg(s.inward * 20), "scale" to "0.6 0.6"),
            ), duration = 2300, fill = "forwards")),
            children = lights,
        )
        val beamTop = -lift + h * 0.8
        val beamH = s.h - beamTop
        val bw = w * 1.05
        val stripes = StringBuilder()
        var y = 0
        while (y < beamH) {
            for (o in listOf(6, 15, 24)) if (y + o < beamH) stripes.append("<rect x=\"0\" y=\"${y + o}\" width=\"${n(bw)}\" height=\"3\" fill=\"rgb(254,249,195)\" fill-opacity=\"0.35\"/>")
            y += 30
        }
        val beam = FxSprite(
            x = s.cx - bw / 2, y = beamTop, w = bw, h = beamH,
            svg = svgOf(bw, beamH, "0 0 ${n(bw)} ${n(beamH)}",
                "<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"rgb(254,240,138)\" stop-opacity=\"0.9\"/><stop offset=\"1\" stop-color=\"rgb(254,240,138)\" stop-opacity=\"0.15\"/></linearGradient>" +
                    "<clipPath id=\"c\"><polygon points=\"${n(bw * 0.36)},0 ${n(bw * 0.64)},0 ${n(bw)},${n(beamH)} 0,${n(beamH)}\"/></clipPath></defs>" +
                    "<g clip-path=\"url(#c)\"><rect width=\"${n(bw)}\" height=\"${n(beamH)}\" fill=\"url(#g)\"/>$stripes</g>"),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0),
                kf("opacity" to 0.95, "offset" to 0.15),
                kf("opacity" to 0.95, "offset" to 0.8),
                kf("opacity" to 0),
            ), duration = 1000, delay = 760, fill = "forwards")),
        )
        val dots = List(8) { i ->
            FxSprite(
                x = s.cx + (i - 3.5) * (s.unit * 0.13), y = s.h - 6, w = 4.0, h = 4.0, fill = "#fef9c3", radius = 2.0,
                base = mapOf("o" to 0.0),
                tracks = listOf(play(listOf(
                    kf("opacity" to 0, "translate" to "0 0"),
                    kf("opacity" to 1, "offset" to 0.2),
                    kf("opacity" to 0, "translate" to "0 -${px(beamH * 0.8)}"),
                ), duration = 800, delay = 820 + i * 60, easing = "ease-in", fill = "forwards")),
            )
        }
        return EntrancePlan(
            revealAt = 900.0,
            arrive = play(listOf(
                kf("opacity" to 0.2, "scale" to 0.9, "translate" to "0 -${px(s.unit * 0.35)}", "clipPath" to "inset(0 0 100% 0)", "filter" to "brightness(1.8)"),
                kf("opacity" to 0.6, "offset" to 0.25),
                kf("opacity" to 0.35, "offset" to 0.35),
                kf("opacity" to 1, "scale" to 1, "translate" to "0 0", "clipPath" to "inset(0 0 0% 0)", "filter" to "brightness(1)", "offset" to 0.8),
                kf("opacity" to 1, "scale" to 1, "translate" to "0 0", "clipPath" to "inset(0 0 0% 0)", "filter" to "brightness(1)"),
            ), duration = 700, easing = "ease-out"),
            total = 2350.0,
            sprites = listOf(beam) + dots + ufo,
        )
    }

    // It falls from the sky with speed lines, stretched by the speed; hits the
    // ground in a squash with a shockwave, pebbles and a ring of cartoon stars;
    // bounces twice and settles.
    private fun drop(s: EntranceStage): EntrancePlan {
        val fall = s.above + s.h + 20
        val sprites = mutableListOf<FxSprite>()
        for (i in 0 until 3) {
            val lh = s.unit * 0.9
            sprites.add(FxSprite(
                x = s.cx + (i - 1) * s.unit * 0.3, y = -s.unit * 1.1, w = 2.0, h = lh,
                svg = linearBox(2.0, lh, 180.0, stops("rgba(148, 163, 184, 0)", "rgba(100, 116, 139, 0.8)"), 1.0),
                base = mapOf("o" to 0.0),
                tracks = listOf(play(listOf(
                    kf("opacity" to 0, "translate" to "0 -${px(fall * 0.6)}"),
                    kf("opacity" to 0.9, "translate" to "0 0", "offset" to 0.85),
                    kf("opacity" to 0, "translate" to "0 ${px(s.unit * 0.4)}"),
                ), duration = 480, delay = 20 + i * 30, easing = "cubic-bezier(0.55, 0, 1, 0.45)", fill = "forwards")),
            ))
        }
        val hit = 470
        sprites.add(shockwave(s, hit, "rgba(148, 163, 184, 0.9)", 2.4))
        sprites.addAll(debris(s, 6, hit, listOf("#94a3b8", "#64748b", "#cbd5e1"), 5, 0.9))
        sprites.add(puff(s.cx - s.w * 0.3, s.h - 4, s.unit * 0.5, hit, -s.unit * 0.6, -4.0, 700))
        sprites.add(puff(s.cx + s.w * 0.3, s.h - 4, s.unit * 0.5, hit, s.unit * 0.6, -4.0, 700))
        // Cartoon stars circling where it hit, for a moment.
        val star5 = "M6 0 L7.5 4.2 L12 4.6 L8.5 7.4 L9.7 12 L6 9.4 L2.3 12 L3.5 7.4 L0 4.6 L4.5 4.2 Z"
        for (i in 0 until 3) {
            sprites.add(FxSprite(
                x = s.cx, y = -s.unit * 0.15, w = 0.0, h = 0.0, origin = 0.0 to 0.0,
                base = mapOf("sy" to 0.35),
                tracks = listOf(play(listOf(kf("rotate" to deg(i * 120)), kf("rotate" to deg(i * 120 + 520))), duration = 1100, delay = hit + 60, fill = "forwards")),
                children = listOf(FxSprite(
                    x = s.unit * 0.55, y = -6.0, w = 12.0, h = 12.0,
                    svg = svgOf(12.0, 12.0, "0 0 12 12", "<path d=\"$star5\" fill=\"#fde047\" stroke=\"#b45309\" stroke-width=\"0.8\"/>"),
                    base = mapOf("o" to 0.0),
                    tracks = listOf(play(listOf(
                        kf("opacity" to 0),
                        kf("opacity" to 1, "offset" to 0.15),
                        kf("opacity" to 1, "offset" to 0.75),
                        kf("opacity" to 0),
                    ), duration = 1100, delay = hit + 60, fill = "forwards")),
                )),
            ))
        }
        return EntrancePlan(
            revealAt = 0.0,
            arrive = play(listOf(
                kf("opacity" to 1, "translate" to "0 -${px(fall)}", "scale" to "0.86 1.18", "easing" to "cubic-bezier(0.55, 0, 1, 0.45)"),
                kf("translate" to "0 0", "scale" to "0.9 1.12", "offset" to 0.43, "easing" to "ease-out"),
                kf("translate" to "0 ${px(s.unit * 0.1)}", "scale" to "1.28 0.72", "offset" to 0.5, "easing" to "ease-out"),
                kf("translate" to "0 -${px(s.unit * 0.45)}", "scale" to "0.93 1.08", "offset" to 0.65, "easing" to "ease-in"),
                kf("translate" to "0 0", "scale" to "1.12 0.88", "offset" to 0.78, "easing" to "ease-out"),
                kf("translate" to "0 -${px(s.unit * 0.12)}", "scale" to "0.98 1.02", "offset" to 0.87, "easing" to "ease-in"),
                kf("translate" to "0 0", "scale" to "1.03 0.97", "offset" to 0.94),
                kf("opacity" to 1, "translate" to "0 0", "scale" to "1 1"),
            ), duration = 1100),
            total = 1700.0,
            sprites = sprites,
        )
    }

    // A confetti cannon: 40 pieces blasted up and out, each tumbling as it
    // flutters down, drifting.
    private fun confetti(s: EntranceStage): EntrancePlan {
        val count = 40
        val fd = s.unit * 2.4
        val sprites = mutableListOf(FxSprite(
            x = s.cx - s.unit * 1.2, y = s.cy - s.unit * 1.2, w = fd, h = fd,
            svg = radialDisc(fd, fd, stops("rgba(255, 255, 255, 0.95)", "rgba(253, 224, 71, 0.4)" to 45, "transparent" to 70)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0.3),
                kf("opacity" to 1, "scale" to 1, "offset" to 0.3),
                kf("opacity" to 0, "scale" to 1.4),
            ), duration = 420, delay = 60, fill = "forwards")),
        ))
        for (i in 0 until count) {
            val kind = i % 4
            val wpx = if (kind == 1) 7 else if (kind == 2) 4 else 7
            val hpx = if (kind == 1) 7 else if (kind == 2) 16 else 10
            // A fan leaning into the screen (so the burst is never cut off).
            val a = (-170 + (160.0 * i) / (count - 1)) * (PI / 180)
            val v = s.unit * (1.2 + ((i * 7) % 9) * 0.18) + s.w * 0.2
            val dx = cos(a) * v * (if (s.inward == -1) -1 else 1) * -1
            val dy = sin(a) * v
            val drift = ((i % 3) - 1) * s.unit * 0.4
            val dur = 1500 + (i % 5) * 140
            sprites.add(FxSprite(
                x = s.cx - wpx / 2.0, y = s.cy - hpx / 2.0, w = wpx.toDouble(), h = hpx.toDouble(),
                fill = if (i % 5 == 0) s.accent else COLORS[i % COLORS.size],
                radius = if (kind == 1) wpx / 2.0 else 2.0,
                base = mapOf("o" to 0.0),
                tracks = listOf(
                    play(listOf(
                        kf("opacity" to 1, "translate" to "0 0", "rotate" to "0deg", "easing" to "cubic-bezier(0.1, 0.8, 0.3, 1)"),
                        kf("opacity" to 1, "translate" to tr(dx, dy), "rotate" to deg(160 + i * 23), "offset" to 0.3, "easing" to "cubic-bezier(0.4, 0, 0.8, 0.6)"),
                        kf("opacity" to 1, "translate" to tr(dx + drift, dy + s.unit * 1.1), "rotate" to deg(320 + i * 31), "offset" to 0.75),
                        kf("opacity" to 0, "translate" to tr(dx - drift, dy + s.unit * 1.8), "rotate" to deg(420 + i * 37)),
                    ), duration = dur, delay = 60 + (i % 6) * 12, fill = "forwards"),
                    // The 3D tumble: the piece flips edge-on and back as it spins.
                    play(listOf(kf("scale" to "1 1"), kf("scale" to "1 0.1"), kf("scale" to "1 1")), duration = 260 + (i % 4) * 70, iterations = ceil(dur / 300.0).toInt()),
                ),
            ))
        }
        return EntrancePlan(
            revealAt = 0.0,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to 0, "rotate" to "-14deg"),
                kf("opacity" to 1, "scale" to 1.22, "rotate" to "6deg", "offset" to 0.5),
                kf("scale" to 0.94, "rotate" to "-3deg", "offset" to 0.72),
                kf("scale" to 1.03, "rotate" to "1deg", "offset" to 0.86),
                kf("opacity" to 1, "scale" to 1, "rotate" to "0deg"),
            ), duration = 700, easing = "ease-out"),
            total = 2200.0,
            sprites = sprites,
        )
    }

    // Jackpot: it rises spinning like a gold coin, a shower of coins bursts
    // out and falls, it lands with a bounce and a glint sweeps across it.
    private fun coin(s: EntranceStage): EntrancePlan {
        val sprites = mutableListOf<FxSprite>()
        for (i in 0 until 12) {
            val d = s.unit * (0.28 + (i % 3) * 0.06)
            val dir = if (i % 2 == 1) 1 else -1
            val dx = dir * (s.w / 2 + s.unit * (0.2 + (i % 4) * 0.28))
            val up = s.unit * (0.9 + ((i * 5) % 4) * 0.3)
            sprites.add(FxSprite(
                x = s.cx - d / 2, y = s.cy - d / 2, w = d, h = d,
                svg = svgOf(d, d, "0 0 20 20",
                    "<defs><radialGradient id=\"c\" cx=\"0.35\" cy=\"0.3\" r=\"0.8\"><stop offset=\"0\" stop-color=\"#fef9c3\"/><stop offset=\"0.45\" stop-color=\"#facc15\"/><stop offset=\"1\" stop-color=\"#a16207\"/></radialGradient></defs><circle cx=\"10\" cy=\"10\" r=\"9\" fill=\"url(#c)\" stroke=\"#854d0e\" stroke-width=\"1\"/><circle cx=\"10\" cy=\"10\" r=\"5.5\" fill=\"none\" stroke=\"#fef08a\" stroke-width=\"1\" opacity=\"0.8\"/>"),
                base = mapOf("o" to 0.0),
                tracks = listOf(
                    play(listOf(
                        kf("opacity" to 1, "translate" to "0 0", "easing" to "cubic-bezier(0.2, 0.7, 0.4, 1)"),
                        kf("opacity" to 1, "translate" to "${px(dx * 0.6)} -${px(up)}", "offset" to 0.4, "easing" to "cubic-bezier(0.5, 0, 0.9, 0.5)"),
                        kf("opacity" to 0, "translate" to tr(dx, s.unit * 0.6)),
                    ), duration = 1100, delay = 560 + i * 25, fill = "forwards"),
                    play(listOf(kf("scale" to "1 1"), kf("scale" to "0.1 1"), kf("scale" to "1 1")), duration = 240, iterations = 5, delay = 560 + i * 25),
                ),
            ))
        }
        sprites.addAll(sparkles(s, 6, 900, "#fde047", 1.2))
        // The glint: a band that sweeps across the button (left -40% -> 115%).
        val bandW = s.w * 0.35
        sprites.add(FxSprite(
            x = 0.0, y = 0.0, w = s.w, h = s.h, clip = s.h / 2,
            children = listOf(FxSprite(
                x = 0.0, y = -s.h * 0.2, w = bandW, h = s.h * 1.4, leftOf = s.w,
                svg = linearBox(bandW, s.h * 1.4, 100.0, stops("transparent", "rgba(255,255,255,0.75)", "transparent")),
                tracks = listOf(play(listOf(kf("left" to "-40%"), kf("left" to "115%")), duration = 520, delay = 1050, easing = "ease-in-out", fill = "forwards")),
            )),
        ))
        return EntrancePlan(
            revealAt = 0.0,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to 0.3, "rotate" to "y 0deg", "translate" to "0 ${px(s.unit * 0.8)}", "filter" to "brightness(1)"),
                kf("opacity" to 1, "rotate" to "y 180deg", "filter" to "brightness(1.6) sepia(0.4)", "offset" to 0.15),
                kf("rotate" to "y 360deg", "filter" to "brightness(0.8)", "offset" to 0.3),
                kf("rotate" to "y 540deg", "translate" to "0 -${px(s.unit * 0.5)}", "filter" to "brightness(1.6) sepia(0.4)", "offset" to 0.45, "scale" to 1.1),
                kf("rotate" to "y 720deg", "filter" to "brightness(0.85)", "offset" to 0.6),
                kf("rotate" to "y 1080deg", "translate" to "0 0", "scale" to 1.06, "filter" to "brightness(1.2)", "offset" to 0.8, "easing" to "ease-out"),
                kf("translate" to "0 -${px(s.unit * 0.1)}", "scale" to 0.98, "offset" to 0.9),
                kf("opacity" to 1, "rotate" to "y 1080deg", "translate" to "0 0", "scale" to 1, "filter" to "brightness(1)"),
            ), duration = 1200),
            total = 1900.0,
            sprites = sprites,
        )
    }

    // A fireball streaks in from the top corner of the screen with a burning
    // tail, strikes with a flash and a shockwave, throws rocks and smoke.
    private fun meteor(s: EntranceStage): EntrancePlan {
        val dur = 560
        val sx = s.inward * min(s.winW * 0.35, 380.0)
        val sy = -(s.above + s.unit)
        val ang = (atan2(-sy, -sx) * 180) / PI
        val len = sqrt(sx * sx + sy * sy)
        val bd = s.unit * 0.56
        val tw = min(len * 0.45, 260.0)
        val th = s.unit * 0.4
        val sprites = mutableListOf(FxSprite(
            x = s.cx, y = s.cy, w = 0.0, h = 0.0, origin = 0.0 to 0.0,
            base = mapOf("r" to ang),
            tracks = listOf(play(listOf(
                kf("translate" to tr(sx, sy), "opacity" to 1),
                kf("translate" to "0 0", "opacity" to 1, "offset" to 0.97),
                kf("translate" to "0 0", "opacity" to 0),
            ), duration = dur, easing = "cubic-bezier(0.5, 0, 0.9, 0.6)", fill = "forwards")),
            children = listOf(
                FxSprite(x = -s.unit * 0.1 - tw, y = -s.unit * 0.2, w = tw, h = th,
                    svg = linearBox(tw, th, 270.0, stops("rgba(253, 224, 71, 0.95)", "rgba(249, 115, 22, 0.6)" to 30, "rgba(127, 29, 29, 0)"), th / 2)),
                FxSprite(x = -bd / 2, y = -bd / 2, w = bd, h = bd, svg = radialDisc(bd, bd, stops("#fffbeb", "#fde047" to 30, "#f97316" to 60, "#7c2d12"))),
            ),
        ))
        val bu = s.unit * 3.4
        sprites.add(FxSprite(
            x = s.cx - bu / 2, y = s.cy - bu / 2, w = bu, h = bu,
            svg = radialDisc(bu, bu, stops("#ffffff", "rgba(253, 224, 71, 0.95)" to 25, "rgba(249, 115, 22, 0.6)" to 50, "transparent" to 72)),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0.2),
                kf("opacity" to 1, "scale" to 1, "offset" to 0.25),
                kf("opacity" to 0, "scale" to 1.5),
            ), duration = 620, delay = dur - 20, easing = "ease-out", fill = "forwards")),
        ))
        sprites.add(shockwave(s, dur, "rgba(253, 186, 116, 0.95)", 3))
        sprites.addAll(debris(s, 9, dur, listOf("#44403c", "#78716c", "#292524", "#f97316"), 6, 1.3))
        for (i in 0 until 5) {
            sprites.add(puff(s.cx + (i - 2) * s.unit * 0.25, s.cy, s.unit * (0.55 + (i % 2) * 0.2), dur + 120 + i * 60, (i - 2) * s.unit * 0.15, -s.unit * 1.1, 1300, "rgba(120, 113, 108, 0.75)"))
        }
        return EntrancePlan(
            revealAt = dur.toDouble(),
            arrive = joltArrival(1500, kf("filter" to "brightness(1.7) sepia(1) saturate(4) hue-rotate(-20deg) drop-shadow(0 0 14px #f97316)")),
            total = dur + 1600.0,
            sprites = sprites,
        )
    }

    // A top hat appears where the button will be, a wand taps it twice in a
    // flurry of sparkles, and the button springs out; the hat drops away.
    private fun magic(s: EntranceStage): EntrancePlan {
        val hw = s.unit * 1.2
        val hh = s.unit * 1.0
        val hatTop = s.h - hh * 0.72
        val sprites = mutableListOf(FxSprite(
            x = s.cx - hw / 2, y = hatTop, w = hw, h = hh,
            svg = svgOf(hw, hh, "0 0 60 50",
                "<defs><linearGradient id=\"h\" x1=\"0\" x2=\"1\"><stop offset=\"0\" stop-color=\"#0f172a\"/><stop offset=\"0.35\" stop-color=\"#475569\"/><stop offset=\"0.6\" stop-color=\"#1e293b\"/><stop offset=\"1\" stop-color=\"#020617\"/></linearGradient></defs>" +
                    "<ellipse cx=\"30\" cy=\"44\" rx=\"29\" ry=\"6\" fill=\"#0f172a\"/><path d=\"M12 44 L14 6 Q30 1 46 6 L48 44 Z\" fill=\"url(#h)\"/><rect x=\"13\" y=\"30\" width=\"34\" height=\"7\" fill=\"${s.accent}\"/>" +
                    "<ellipse cx=\"30\" cy=\"6\" rx=\"16\" ry=\"4\" fill=\"#334155\"/><path d=\"M19 10 L18 28\" stroke=\"#fff\" stroke-width=\"2\" opacity=\"0.25\" stroke-linecap=\"round\"/>"),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to 0.3, "rotate" to "-10deg"),
                kf("opacity" to 1, "scale" to 1.1, "rotate" to "4deg", "offset" to 0.12, "easing" to "ease-out"),
                kf("opacity" to 1, "scale" to 1, "rotate" to "0deg", "offset" to 0.2),
                kf("opacity" to 1, "scale" to 1, "rotate" to "0deg", "offset" to 0.62),
                kf("opacity" to 1, "translate" to "0 ${px(s.unit * 0.3)}", "scale" to "1.15 0.85", "offset" to 0.68),
                kf("opacity" to 0, "translate" to "0 ${px(s.unit * 0.9)}", "scale" to 0.6),
            ), duration = 2000, fill = "forwards")),
        ))
        val ww = s.unit * 0.9
        val tilt = if (s.inward == -1) 1 else -1
        sprites.add(FxSprite(
            x = if (s.inward == -1) s.cx - hw / 2 - ww * 0.8 else s.cx + hw / 2 - ww * 0.2,
            y = hatTop - ww * 0.2, w = ww, h = ww * 0.2,
            svg = svgOf(ww, ww * 0.2, "0 0 50 10",
                "<rect x=\"0\" y=\"3\" width=\"42\" height=\"4\" rx=\"2\" fill=\"#111827\"/><rect x=\"38\" y=\"2.4\" width=\"12\" height=\"5.2\" rx=\"2.4\" fill=\"#f8fafc\"/><rect x=\"0\" y=\"3\" width=\"8\" height=\"4\" rx=\"2\" fill=\"#f8fafc\"/>"),
            base = if (s.inward == 1) mapOf("o" to 0.0, "sx" to -1.0) else mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "rotate" to deg(tilt * -30), "translate" to "${px(-s.inward * 0)} 0"),
                kf("opacity" to 1, "rotate" to deg(tilt * -20), "offset" to 0.12),
                kf("rotate" to deg(tilt * 10), "offset" to 0.24),
                kf("rotate" to deg(tilt * -20), "offset" to 0.34),
                kf("rotate" to deg(tilt * 10), "offset" to 0.44),
                kf("opacity" to 1, "rotate" to deg(tilt * -15), "offset" to 0.56),
                kf("opacity" to 0, "rotate" to deg(tilt * -30)),
            ), duration = 1400, delay = 150, fill = "forwards")),
        ))
        sprites.addAll(sparkles(s, 5, 480, "#fde047", 0.8))
        sprites.addAll(sparkles(s, 5, 800, "#f0abfc", 1.1))
        sprites.add(puff(s.cx, s.h - s.unit * 0.1, s.unit * 1.1, 1350, 0.0, -s.unit * 0.3, 900, "rgba(226, 232, 240, 0.95)"))
        return EntrancePlan(
            revealAt = 1150.0,
            arrive = play(listOf(
                kf("opacity" to 0, "translate" to "0 ${px(s.unit * 0.55)}", "scale" to 0.5, "easing" to "cubic-bezier(0.2, 0.8, 0.3, 1)"),
                kf("opacity" to 1, "translate" to "0 -${px(s.unit * 0.9)}", "scale" to 1.08, "rotate" to deg(s.inward * 8), "offset" to 0.45, "easing" to "cubic-bezier(0.55, 0, 1, 0.45)"),
                kf("translate" to "0 0", "scale" to "1.12 0.88", "rotate" to "0deg", "offset" to 0.75, "easing" to "ease-out"),
                kf("translate" to "0 -4px", "scale" to "0.98 1.02", "offset" to 0.88),
                kf("opacity" to 1, "translate" to "0 0", "scale" to 1, "rotate" to "0deg"),
            ), duration = 900),
            total = 2300.0,
            sprites = sprites,
        )
    }

    // It drifts down under a striped parachute, swaying; touches down softly,
    // and the canopy collapses and floats away.
    private fun parachute(s: EntranceStage): EntrancePlan {
        val a = s.accent
        val cw = max(s.unit * 1.7, s.w * 0.9)
        val ch = s.unit * 1.25
        val fall = s.above + ch + s.h
        val dur = 1900
        val mid = s.left + s.cx
        val shift = min(0.0, s.winW - 6 - (mid + cw / 2)) + max(0.0, 6 - (mid - cw / 2))
        val sway = listOf(
            kf("translate" to "0 -${px(fall)}", "rotate" to "0deg"),
            kf("translate" to "${px(s.unit * 0.35)} -${px(fall * 0.72)}", "rotate" to "-9deg", "offset" to 0.22),
            kf("translate" to "-${px(s.unit * 0.35)} -${px(fall * 0.45)}", "rotate" to "8deg", "offset" to 0.45),
            kf("translate" to "${px(s.unit * 0.2)} -${px(fall * 0.2)}", "rotate" to "-5deg", "offset" to 0.68),
            kf("translate" to "-${px(s.unit * 0.05)} -${px(s.unit * 0.12)}", "rotate" to "2deg", "offset" to 0.88),
            kf("translate" to "0 0", "rotate" to "0deg"),
        )
        val canopy = "<defs><linearGradient id=\"s\" x1=\"0\" x2=\"1\">" +
            "<stop offset=\"0\" stop-color=\"$a\"/><stop offset=\"0.2\" stop-color=\"$a\"/><stop offset=\"0.2\" stop-color=\"#ffffff\"/><stop offset=\"0.4\" stop-color=\"#ffffff\"/>" +
            "<stop offset=\"0.4\" stop-color=\"$a\"/><stop offset=\"0.6\" stop-color=\"$a\"/><stop offset=\"0.6\" stop-color=\"#ffffff\"/><stop offset=\"0.8\" stop-color=\"#ffffff\"/>" +
            "<stop offset=\"0.8\" stop-color=\"$a\"/><stop offset=\"1\" stop-color=\"$a\"/></linearGradient>" +
            "<radialGradient id=\"l\" cx=\"0.4\" cy=\"0.2\" r=\"0.9\"><stop offset=\"0\" stop-color=\"#fff\" stop-opacity=\"0.45\"/><stop offset=\"1\" stop-color=\"#000\" stop-opacity=\"0.18\"/></radialGradient></defs>" +
            "<path d=\"M2 26 Q40 -12 78 26 Q70 22 60 26 Q50 21 40 26 Q30 21 20 26 Q10 22 2 26 Z\" fill=\"url(#s)\" stroke=\"rgba(15,23,42,0.35)\" stroke-width=\"1\"/>" +
            "<path d=\"M2 26 Q40 -12 78 26 Q70 22 60 26 Q50 21 40 26 Q30 21 20 26 Q10 22 2 26 Z\" fill=\"url(#l)\"/>" +
            "<path d=\"M4 26 L30 60 M20 26 L35 60 M40 26 L40 60 M60 26 L45 60 M76 26 L50 60\" stroke=\"rgba(71,85,105,0.8)\" stroke-width=\"0.8\"/>"
        val chute = FxSprite(
            x = s.cx - cw / 2 + shift, y = s.cy - ch - s.h * 0.35, w = cw, h = ch,
            origin = cw / 2 - shift to ch + s.h * 0.35,
            tracks = listOf(play(sway, duration = dur, easing = "cubic-bezier(0.3, 0.2, 0.4, 1)", fill = "forwards")),
            children = listOf(FxSprite(
                x = 0.0, y = 0.0, w = cw, h = ch, origin = cw / 2 to ch,
                svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${n(cw)}\" height=\"${n(ch)}\" viewBox=\"0 0 80 60\" preserveAspectRatio=\"none\">$canopy</svg>",
                tracks = listOf(play(listOf(
                    kf("scale" to "1 1", "opacity" to 1, "translate" to "0 0"),
                    kf("scale" to "0.8 0.35", "opacity" to 1, "translate" to tr(s.inward * s.unit * 0.4, ch * 0.45), "offset" to 0.45),
                    kf("scale" to "0.4 0.2", "opacity" to 0, "translate" to tr(s.inward * s.unit * 1.2, ch * 0.6)),
                ), duration = 800, delay = dur + 80, fill = "forwards")),
            )),
        )
        val sprites = listOf(
            chute,
            puff(s.cx - s.w * 0.3, s.h - 3, s.unit * 0.4, dur - 40, -s.unit * 0.4, -3.0, 600),
            puff(s.cx + s.w * 0.3, s.h - 3, s.unit * 0.4, dur - 40, s.unit * 0.4, -3.0, 600),
        )
        return EntrancePlan(
            revealAt = 0.0,
            arrive = play(sway.mapIndexed { i, k0 ->
                val k = LinkedHashMap<String, Any?>(k0)
                k["opacity"] = 1
                val o = if (i == 0) 0 else k0["offset"] as Number?
                k["offset"] = o?.let { it.toDouble() * (dur.toDouble() / (dur + 260)) }
                k
            }, duration = dur + 260, easing = "cubic-bezier(0.3, 0.2, 0.4, 1)"),
            total = dur + 950.0,
            sprites = sprites,
        )
    }

    // A paper plane loops the loop across the screen and glides in; at the
    // corner it unfolds into the button.
    private fun plane(s: EntranceStage): EntrancePlan {
        val pw = s.unit * 0.95
        val ph = pw * 0.55
        val l = min(s.winW * 0.55, 520.0) * s.inward
        val top = -min(s.top * 0.6, 260.0)
        val r = s.unit * 1.1
        val d = "M ${n(l * 1.1)},${n(top)} C ${n(l * 0.6)},${n(top + 40)} ${n(l * 0.35)},${n(top - 30)} ${n(l * 0.25)},${n(top + 20)} " +
            "a ${n(r)},${n(r)} 0 1,${if (s.inward == -1) 1 else 0} ${n(s.inward * 0.01)},0 " +
            "C ${n(l * 0.12)},${n(top + 60)} ${n(s.inward * s.unit * 0.8)},${n(s.cy - 30)} ${n(s.cx - s.w / 2 + s.w / 2)},${n(s.cy)}"
        val dur = 1700
        val ease = "cubic-bezier(0.35, 0.1, 0.4, 1)"
        val sprites = mutableListOf(FxSprite(
            x = -pw / 2, y = -ph / 2, w = pw, h = ph, path = d,
            svg = svgOf(pw, ph, "0 0 40 22",
                "<defs><linearGradient id=\"p\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"#ffffff\"/><stop offset=\"1\" stop-color=\"#cbd5e1\"/></linearGradient></defs>" +
                    "<path d=\"M40 11 L0 0 L10 11 Z\" fill=\"url(#p)\" stroke=\"#94a3b8\" stroke-width=\"0.8\" stroke-linejoin=\"round\"/><path d=\"M40 11 L10 11 L0 22 Z\" fill=\"#e2e8f0\" stroke=\"#94a3b8\" stroke-width=\"0.8\" stroke-linejoin=\"round\"/>" +
                    "<path d=\"M40 11 L10 11 L14 15 Z\" fill=\"#94a3b8\"/><path d=\"M40 11 L10 11\" stroke=\"${s.accent}\" stroke-width=\"1.2\"/>"),
            tracks = listOf(play(listOf(
                kf("offsetDistance" to "0%", "opacity" to 1),
                kf("offsetDistance" to "100%", "opacity" to 1, "offset" to 0.96),
                kf("offsetDistance" to "100%", "opacity" to 0),
            ), duration = dur, easing = ease, fill = "forwards")),
        ))
        for (i in 0 until 6) {
            sprites.add(FxSprite(
                x = -2.0, y = -2.0, w = 4.0, h = 4.0, fill = "rgba(148, 163, 184, 0.7)", radius = 2.0, path = d,
                base = mapOf("o" to 0.0),
                tracks = listOf(play(listOf(
                    kf("offsetDistance" to "0%", "opacity" to 0),
                    kf("offsetDistance" to "80%", "opacity" to 0.6, "offset" to 0.7),
                    kf("offsetDistance" to "96%", "opacity" to 0),
                ), duration = dur, delay = 80 + i * 70, easing = ease, fill = "forwards")),
            ))
        }
        sprites.add(FxSprite(
            x = 0.0, y = 0.0, w = s.w, h = s.h,
            svg = linearBox(s.w, s.h, 135.0, stops("#ffffff", "#e2e8f0"), min(s.w, s.h) / 2),
            base = mapOf("o" to 0.0),
            tracks = listOf(play(listOf(
                kf("opacity" to 0, "scale" to "0.2 0.2", "rotate" to "-40deg"),
                kf("opacity" to 1, "scale" to "1 1", "rotate" to "0deg", "offset" to 0.55),
                kf("opacity" to 0, "scale" to "1 1", "rotate" to "0deg"),
            ), duration = 520, delay = dur - 80, easing = "ease-out", fill = "forwards")),
        ))
        return EntrancePlan(
            revealAt = dur + 150.0,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to "1 0.1", "rotate" to "x 80deg"),
                kf("opacity" to 1, "scale" to "1 1.08", "rotate" to "x 0deg", "offset" to 0.6),
                kf("opacity" to 1, "scale" to "1 1", "rotate" to "x 0deg"),
            ), duration = 480, easing = "cubic-bezier(0.22, 1, 0.36, 1)"),
            total = dur + 700.0,
            sprites = sprites,
        )
    }

    // An egg lands in the corner, wobbles, cracks, the top pops off; the button
    // hatches with a happy bounce.
    private fun egg(s: EntranceStage): EntrancePlan {
        val ew = s.unit * 0.95
        val eh = s.unit * 1.2
        val left = s.cx - ew / 2
        val top = s.h - eh
        fun shell(part: String) = svgOf(ew, eh, "0 0 40 50",
            "<defs><radialGradient id=\"g\" cx=\"0.38\" cy=\"0.3\" r=\"0.85\"><stop offset=\"0\" stop-color=\"#fffdf5\"/><stop offset=\"0.6\" stop-color=\"#fdf2d8\"/><stop offset=\"1\" stop-color=\"#d6b98c\"/></radialGradient>" +
                "<clipPath id=\"c\"><path d=\"${if (part == "top") "M-5 -5 H45 V24 L36 20 L29 27 L21 20 L13 27 L6 21 L-5 25 Z" else "M-5 25 L6 21 L13 27 L21 20 L29 27 L36 20 L45 24 V60 H-5 Z"}\"/></clipPath></defs>" +
                "<g clip-path=\"url(#c)\"><path d=\"M20 1 C33 1 39 20 39 31 C39 43 30 49 20 49 C10 49 1 43 1 31 C1 20 7 1 20 1 Z\" fill=\"url(#g)\" stroke=\"#c9a57a\" stroke-width=\"1\"/>" +
                "<circle cx=\"13\" cy=\"16\" r=\"1.6\" fill=\"#d6b98c\" opacity=\"0.7\"/><circle cx=\"27\" cy=\"12\" r=\"1.2\" fill=\"#d6b98c\" opacity=\"0.7\"/>" +
                "<circle cx=\"29\" cy=\"36\" r=\"1.6\" fill=\"#d6b98c\" opacity=\"0.6\"/><circle cx=\"11\" cy=\"38\" r=\"1.2\" fill=\"#d6b98c\" opacity=\"0.6\"/>" +
                "<ellipse cx=\"14\" cy=\"12\" rx=\"4\" ry=\"6\" fill=\"#fff\" opacity=\"0.7\"/></g>")
        val land = play(listOf(
            kf("translate" to "0 -${px(s.above + eh)}", "rotate" to "0deg", "scale" to "1 1", "easing" to "cubic-bezier(0.55, 0, 1, 0.45)"),
            kf("translate" to "0 0", "scale" to "1.12 0.9", "offset" to 0.22, "easing" to "ease-out"),
            kf("translate" to "0 0", "scale" to "1 1", "rotate" to "0deg", "offset" to 0.3),
            kf("rotate" to "-12deg", "offset" to 0.38),
            kf("rotate" to "10deg", "offset" to 0.46),
            kf("rotate" to "-8deg", "offset" to 0.54),
            kf("rotate" to "6deg", "offset" to 0.6),
            kf("rotate" to "0deg", "offset" to 0.66),
            kf("translate" to "0 0", "rotate" to "0deg", "scale" to "1 1"),
        ), duration = 1600, fill = "forwards")
        val hatch = 1600
        val k = ew / 40
        val bottom = FxSprite(
            x = left, y = top, w = ew, h = eh, origin = ew / 2 to eh, tracks = listOf(land),
            children = listOf(FxSprite(x = 0.0, y = 0.0, w = ew, h = eh, svg = shell("bottom"), tracks = listOf(play(listOf(
                kf("opacity" to 1, "scale" to 1),
                kf("opacity" to 0, "scale" to 0.7, "translate" to "0 ${px(s.unit * 0.3)}"),
            ), duration = 500, delay = hatch + 250, fill = "forwards")))),
        )
        val topHalf = FxSprite(
            x = left, y = top, w = ew, h = eh, origin = ew / 2 to eh, tracks = listOf(land),
            children = listOf(FxSprite(
                x = 0.0, y = 0.0, w = ew, h = eh, svg = shell("top"),
                tracks = listOf(play(listOf(
                    kf("translate" to "0 0", "rotate" to "0deg", "opacity" to 1),
                    kf("translate" to "${px(-s.inward * s.unit * 0.8)} -${px(s.unit * 1.2)}", "rotate" to deg(-s.inward * 160), "opacity" to 1, "offset" to 0.55),
                    kf("translate" to tr(-s.inward * s.unit * 1.2, s.unit * 0.4), "rotate" to deg(-s.inward * 260), "opacity" to 0),
                ), duration = 800, delay = hatch, easing = "cubic-bezier(0.2, 0.8, 0.4, 1)", fill = "forwards")),
                children = listOf(
                    // The crack racing across the shell, as a wipe from the left.
                    FxSprite(
                        x = 2 * k, y = 19 * k, w = 37 * k, h = 9 * k,
                        svg = svgOf(37 * k, 9 * k, "2 19 37 9", "<path d=\"M3 24 L6 21 L13 27 L21 20 L29 27 L36 20 L38 22\" fill=\"none\" stroke=\"#7c5a33\" stroke-width=\"1.4\" stroke-linejoin=\"round\"/>"),
                        wipe = "right",
                        base = mapOf("dash" to 100.0),
                        tracks = listOf(play(listOf(
                            kf("strokeDashoffset" to 100),
                            kf("strokeDashoffset" to 100, "offset" to 0.4),
                            kf("strokeDashoffset" to 0),
                        ), duration = 1400, fill = "forwards")),
                    ),
                ),
            )),
        )
        return EntrancePlan(
            revealAt = hatch + 60.0,
            arrive = play(listOf(
                kf("opacity" to 0, "scale" to 0.2, "translate" to "0 ${px(s.unit * 0.25)}"),
                kf("opacity" to 1, "scale" to 1.25, "translate" to "0 -${px(s.unit * 0.35)}", "offset" to 0.4, "easing" to "ease-in"),
                kf("scale" to "1.12 0.88", "translate" to "0 0", "offset" to 0.62, "easing" to "ease-out"),
                kf("scale" to "0.97 1.03", "translate" to "0 -3px", "offset" to 0.8),
                kf("opacity" to 1, "scale" to 1, "translate" to "0 0"),
            ), duration = 820),
            total = hatch + 1000.0,
            sprites = listOf(bottom, topHalf) +
                debris(s, 6, hatch + 30, listOf("#fdf2d8", "#f5e6c8", "#e7d3b0"), 5, 0.8) +
                sparkles(s, 5, hatch + 120, "#fde047", 0.9),
        )
    }

    /** The entrance for a mode on this stage (null for "classic"). `seed` picks the lightning's shape. */
    fun plan(mode: String, s: EntranceStage, seed: Int? = null): EntrancePlan? {
        val fx = EFFECTS[mode] ?: return null
        return fx(s, seeded(seed ?: (kotlin.random.Random.nextInt(100000) + 7)))
    }

    private val pathCache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()

    /** A sprite's drawn values at t ms (see [SpriteFrame]); `values` when already evaluated. */
    fun spriteFrame(sp: FxSprite, t: Double, values: FxValues? = null): SpriteFrame {
        val f = values ?: Keyframes.evaluate(sp.tracks, t, sp.base)
        var tx = f.tx + (f.txp / 100) * sp.w + (if (sp.leftOf != null) (f.left / 100) * sp.leftOf else 0.0)
        var ty = f.ty + (f.typ / 100) * sp.h
        var r = f.r
        val path = sp.path
        if (path != null) {
            val pts = pathCache.getOrPut(path) { FxPaint.flattenPath(path) }
            val at = FxPaint.pointAlong(pts, f.od / 100)
            // Chrome measures the path from the box's own top-left and puts its centre on it.
            tx += at.x - sp.w / 2
            ty += at.y - sp.h / 2
            r += at.angle
        }
        return SpriteFrame(
            tx, ty, r,
            f.sx * cos((f.ry * PI) / 180),
            f.sy * cos((f.rx * PI) / 180),
            f.o,
            if (sp.wipe != null) 1 - f.dash / 100 else 1.0,
        )
    }
}
