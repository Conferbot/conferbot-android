package com.conferbot.sdk.core.customization

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The chat background as the web widget draws it (conferbot-widget
 * ts/chatBackground/chatBackground.ts): solid, gradient (linear, radial,
 * mesh, blur), image with an optional mask, or one of 13 pattern tiles.
 * Pure: the Compose layer (ui/compose/chat/ChatBackgroundLayer) draws it.
 */
sealed class ChatBackground {
    abstract val color: String
    data class Solid(override val color: String) : ChatBackground()
    data class Gradient(override val color: String, val stops: List<String>, val angle: Int, val style: String) : ChatBackground()
    data class Image(override val color: String, val url: String, val maskColor: String?, val maskAlpha: Float) : ChatBackground()
    data class Pattern(override val color: String, val pattern: String, val ink: String, val alpha: Float) : ChatBackground()
}

/** One shape of a tile: a path, circle or rect, filled or stroked, maybe rotated about a point. */
data class PatternShape(
    val t: String,
    val fill: Boolean,
    val sw: Float = 1.2f,
    val d: String? = null,
    val cx: Float = 0f, val cy: Float = 0f, val r: Float = 0f,
    val x: Float = 0f, val y: Float = 0f, val w: Float = 0f, val h: Float = 0f, val rx: Float = 0f,
    val rot: FloatArray? = null,
)

data class PatternTile(val w: Float, val h: Float, val noise: Boolean = false, val shapes: List<PatternShape>)

object ChatBackgrounds {
    val GRADIENT_STYLES = listOf("linear", "radial", "mesh", "blur")

    /** The web's tiles (generated from chatBackground.ts PATTERNS). */
    val PATTERNS: Map<String, PatternTile> = mapOf(
        "dots" to PatternTile(18.0f, 18.0f, noise = false, shapes = listOf(
            PatternShape(t = "circle", fill = true, cx = 9.0f, cy = 9.0f, r = 1.6f)
        )),
        "grid" to PatternTile(24.0f, 24.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M24 .5H.5V24")
        )),
        "diagonal" to PatternTile(14.0f, 14.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M-3.5 3.5l7-7M0 14L14 0M10.5 17.5l7-7")
        )),
        "waves" to PatternTile(40.0f, 16.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.2f, d = "M0 8q5-6 10 0t10 0 10 0 10 0")
        )),
        "topography" to PatternTile(220.0f, 220.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M60 40c30-18 72-10 84 18s-8 58-40 64-70-6-74-36 6-32 30-46z"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M70 58c20-10 48-4 56 14s-6 38-28 40-46-4-48-24 4-20 20-30z"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M84 74c10-4 24 0 26 8s-4 16-14 16-20-2-20-10 2-10 8-14z"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M150 150c26-6 58 6 62 30s-22 40-52 38-50-14-48-36 12-26 38-32z"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M158 166c16-2 34 4 36 18s-14 24-30 22-30-8-28-20 8-18 22-20z"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M-10 170c20-14 50-10 58 8s-8 40-32 44"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M210 30c-6 20-2 44 16 52"),
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M0 110c30 6 44 30 36 60M220 110c-30-4-50 10-56 34")
        )),
        "confetti" to PatternTile(90.0f, 90.0f, noise = false, shapes = listOf(
            PatternShape(t = "rect", fill = true, x = 10.0f, y = 12.0f, w = 7.0f, h = 3.0f, rx = 1.5f, rot = floatArrayOf(30.0f, 13.0f, 13.0f)),
            PatternShape(t = "rect", fill = true, x = 58.0f, y = 8.0f, w = 7.0f, h = 3.0f, rx = 1.5f, rot = floatArrayOf(-40.0f, 61.0f, 9.0f)),
            PatternShape(t = "rect", fill = true, x = 40.0f, y = 52.0f, w = 7.0f, h = 3.0f, rx = 1.5f, rot = floatArrayOf(70.0f, 43.0f, 53.0f)),
            PatternShape(t = "rect", fill = true, x = 72.0f, y = 66.0f, w = 7.0f, h = 3.0f, rx = 1.5f, rot = floatArrayOf(10.0f, 75.0f, 67.0f)),
            PatternShape(t = "circle", fill = true, cx = 34.0f, cy = 26.0f, r = 2.2f),
            PatternShape(t = "circle", fill = true, cx = 80.0f, cy = 34.0f, r = 1.6f),
            PatternShape(t = "circle", fill = true, cx = 16.0f, cy = 70.0f, r = 2.0f),
            PatternShape(t = "circle", fill = true, cx = 56.0f, cy = 82.0f, r = 1.4f),
            PatternShape(t = "path", fill = true, d = "M24 44l3 5h-6z"),
            PatternShape(t = "path", fill = true, d = "M68 44l3 5h-6z", rot = floatArrayOf(40.0f, 68.0f, 46.0f))
        )),
        "doodles" to PatternTile(128.0f, 128.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M14 14h26a5 5 0 0 1 5 5v12a5 5 0 0 1-5 5H26l-7 6v-6h-5a5 5 0 0 1-5-5V19a5 5 0 0 1 5-5z"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M19 23h16M19 28h10"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M92 26c-3-5-11-4-11 3 0 6 11 12 11 12s11-6 11-12c0-7-8-8-11-3z"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M30 78l3 7 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z"),
            PatternShape(t = "circle", fill = false, sw = 1.4f, cx = 96.0f, cy = 92.0f, r = 11.0f),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M91 95q5 5 10 0"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M92 88v1M100 88v1"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M58 60l20-8-6 20-5-7zM67 65l11-13"),
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M60 110h.01M112 60h.01M8 58h.01M70 12h.01")
        )),
        "paper" to PatternTile(180.0f, 180.0f, noise = true, shapes = listOf(
            
        )),
        "hexagons" to PatternTile(24.0f, 42.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.0f, d = "M12 0l12 7v14l-12 7-12-7V7zM12 28v14")
        )),
        "rings" to PatternTile(36.0f, 36.0f, noise = false, shapes = listOf(
            PatternShape(t = "circle", fill = false, sw = 1.1f, cx = 18.0f, cy = 18.0f, r = 9.0f)
        )),
        "plus" to PatternTile(26.0f, 26.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.4f, d = "M13 9v8M9 13h8")
        )),
        "zigzag" to PatternTile(24.0f, 14.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = false, sw = 1.2f, d = "M0 10l6-6 6 6 6-6 6 6")
        )),
        "sparkles" to PatternTile(72.0f, 72.0f, noise = false, shapes = listOf(
            PatternShape(t = "path", fill = true, d = "M18 8c1 6 3 8 9 9-6 1-8 3-9 9-1-6-3-8-9-9 6-1 8-3 9-9z"),
            PatternShape(t = "path", fill = true, d = "M54 42c.7 4 2 5.3 6 6-4 .7-5.3 2-6 6-.7-4-2-5.3-6-6 4-.7 5.3-2 6-6z"),
            PatternShape(t = "circle", fill = true, cx = 52.0f, cy = 14.0f, r = 1.4f),
            PatternShape(t = "circle", fill = true, cx = 16.0f, cy = 56.0f, r = 1.6f)
        ))
    )

    private val COLOR_RE = Regex("""^(#[0-9a-f]{3,8}|rgba?\([\d\s.,%/]+\)|hsla?\([\d\s.,%/a-z]+\)|[a-z]{3,20})$""", RegexOption.IGNORE_CASE)

    /** A colour we can use, else the fallback (as the web's safeColor). */
    fun safeColor(value: Any?, fallback: String): String {
        val v = (value as? String)?.trim().orEmpty()
        return if (v.isNotEmpty() && COLOR_RE.matches(v) && !Regex("url|expression", RegexOption.IGNORE_CASE).containsMatchIn(v)) v else fallback
    }

    private fun clamp(value: Any?, min: Float, max: Float, fallback: Float): Float {
        val n = when (value) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        } ?: return fallback
        return if (n.isFinite()) n.coerceIn(min, max) else fallback
    }

    /** The gradient's colours: the saved stops (2-4), else the legacy two. */
    fun gradientStops(c: Map<String, Any?>): List<String> {
        val stops = (c["chatBgGradientStops"] as? List<*>).orEmpty().map { safeColor(it, "") }.filter { it.isNotEmpty() }.take(4)
        return if (stops.size >= 2) stops else listOf(safeColor(c["gradientBgOne"], "#fffcf1"), safeColor(c["gradientBgTwo"], "#1b55f3"))
    }

    fun resolve(raw: Map<String, Any?>?): ChatBackground {
        val c = raw ?: emptyMap()
        val type = (c["chatBgType"] as? String ?: "solid").lowercase()
        val color = safeColor(c["chatBgColor"], "#ffffff")
        return when (type) {
            "gradient" -> ChatBackground.Gradient(
                color,
                gradientStops(c),
                clamp(c["chatBgGradientAngle"], 0f, 360f, 180f).roundToInt(),
                (c["chatBgGradientStyle"] as? String)?.takeIf { it in GRADIENT_STYLES } ?: "linear",
            )
            "pattern" -> ChatBackground.Pattern(
                color,
                (c["chatBgPattern"] as? String)?.takeIf { it in PATTERNS } ?: "dots",
                safeColor(c["chatBgPatternColor"], "#0f172a"),
                clamp(c["chatBgPatternOpacity"], 0f, 100f, 12f) / 100f,
            )
            "image" -> {
                val url = ((c["chatBgImage"] ?: c["chatBgImg"]) as? String)?.trim().orEmpty()
                if (url.isEmpty() || url.startsWith("javascript:", ignoreCase = true)) ChatBackground.Solid(color)
                else {
                    val alpha = clamp(c["chatBgMaskOpacity"], 0f, 90f, 0f) / 100f
                    ChatBackground.Image(color, url, if (alpha > 0f) safeColor(c["chatBgMaskColor"], "#000000") else null, alpha)
                }
            }
            else -> ChatBackground.Solid(color)
        }
    }

    /** A point on the rim at deg (0 = top, clockwise), as a fraction of the box. */
    fun rimPoint(deg: Float, reach: Float = 50f): Pair<Float, Float> {
        val r = Math.toRadians((deg - 90).toDouble())
        return Pair((50 + reach * cos(r)).roundToInt() / 100f, (50 + reach * sin(r)).roundToInt() / 100f)
    }

    /** Seeded speckles for the paper tile (the web uses an SVG noise filter): x, y, r, alpha factor. */
    val PAPER_SPECKLES: List<FloatArray> by lazy {
        var seed = 7L
        fun rand(): Float { seed = (seed * 16807) % 2147483647; return seed / 2147483647f }
        List(260) { floatArrayOf(rand() * 180f, rand() * 180f, 0.4f + rand() * 0.7f, 0.3f + rand() * 0.7f) }
    }
}
