package com.conferbot.sdk.ui.compose.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import coil.compose.AsyncImage
import com.conferbot.sdk.core.customization.ChatBackground
import com.conferbot.sdk.core.customization.ChatBackgrounds
import com.conferbot.sdk.core.customization.PatternTile
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A CSS colour from the bot's settings as a Compose colour (hex, short hex, rgb[a]). */
internal fun cssColor(value: String, fallback: Color = Color.White): Color {
    var h = value.trim()
    Regex("rgba?\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([\\d.]+))?").find(h)?.let { m ->
        val (r, g, b) = m.destructured
        return Color(r.toInt(), g.toInt(), b.toInt(), ((m.groupValues[4].toFloatOrNull() ?: 1f) * 255).toInt())
    }
    if (h.startsWith("#") && (h.length == 4 || h.length == 5)) h = "#" + h.drop(1).map { "$it$it" }.joinToString("")
    return try { Color(android.graphics.Color.parseColor(h)) } catch (e: IllegalArgumentException) { fallback }
}

/**
 * The bot's chat background behind the messages, as the web widget draws it
 * (core/customization ChatBackground): solid, gradient (linear, radial,
 * mesh, blur), image with its mask, or a pattern tile repeated.
 */
@Composable
fun ChatBackgroundLayer(background: ChatBackground, modifier: Modifier = Modifier) {
    when (background) {
        is ChatBackground.Solid -> Box(modifier.fillMaxSize().background(cssColor(background.color)))
        is ChatBackground.Image -> Box(modifier.fillMaxSize().background(cssColor(background.color))) {
            AsyncImage(model = background.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            background.maskColor?.let { Box(Modifier.fillMaxSize().background(cssColor(it, Color.Black).copy(alpha = background.maskAlpha))) }
        }
        is ChatBackground.Gradient -> {
            val stops = background.stops.map { cssColor(it) }
            Canvas(modifier.fillMaxSize()) { drawGradient(background, stops) }
        }
        is ChatBackground.Pattern -> {
            val tile = ChatBackgrounds.PATTERNS[background.pattern] ?: ChatBackgrounds.PATTERNS.getValue("dots")
            val paths = remember(background.pattern) { tile.shapes.map { s -> s.d?.let { PathParser.createPathFromPathData(it).asComposePath() } } }
            val base = cssColor(background.color)
            val ink = cssColor(background.ink, Color(0xFF0F172A))
            Canvas(modifier.fillMaxSize().background(base)) { drawPattern(tile, paths, ink, background.alpha) }
        }
    }
}

private fun DrawScope.drawGradient(bg: ChatBackground.Gradient, stops: List<Color>) {
    val w = size.width
    val h = size.height
    when (bg.style) {
        "radial" -> {
            val (x, y) = ChatBackgrounds.rimPoint(bg.angle.toFloat(), 35f)
            val far = listOf(hypot(x, y), hypot(1 - x, y), hypot(x, 1 - y), hypot(1 - x, 1 - y)).max() * max(w, h)
            drawRect(Brush.radialGradient(stops, center = Offset(x * w, y * h), radius = far))
        }
        "mesh", "blur" -> {
            // Soft colour fields over a base: the web's mesh (and, approximately, its blur).
            val mesh = bg.style == "mesh"
            drawRect(if (mesh) stops.last() else stops.first())
            val fields = if (mesh) stops else stops.drop(1).ifEmpty { listOf(stops.first()) }
            fields.forEachIndexed { i, c ->
                val (x, y) = ChatBackgrounds.rimPoint(bg.angle + i * 360f / fields.size + if (mesh) 0f else 20f, if (mesh) 50f else 34f)
                drawRect(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(x * w, y * h), radius = (if (mesh) 0.6f else 0.75f) * max(w, h)))
            }
        }
        else -> {
            // The CSS angle (0 = to top, 180 = to bottom) across the box.
            val a = Math.toRadians(bg.angle.toDouble())
            val dx = (sin(a) / 2).toFloat()
            val dy = (-cos(a) / 2).toFloat()
            drawRect(Brush.linearGradient(stops, start = Offset((0.5f - dx) * w, (0.5f - dy) * h), end = Offset((0.5f + dx) * w, (0.5f + dy) * h)))
        }
    }
}

private fun DrawScope.drawPattern(tile: PatternTile, paths: List<Path?>, ink: Color, alpha: Float) {
    // Tiles are in CSS pixels; draw them at the screen's density like the web.
    val k = 1.dp.toPx()
    val tw = tile.w * k
    val th = tile.h * k
    clipRect {
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                translate(x, y) {
                    scale(k, k, pivot = Offset.Zero) { drawTile(tile, paths, ink, alpha) }
                }
                x += tw
            }
            y += th
        }
    }
}

private fun DrawScope.drawTile(tile: PatternTile, paths: List<Path?>, ink: Color, alpha: Float) {
    if (tile.noise) {
        ChatBackgrounds.PAPER_SPECKLES.forEach { p ->
            drawCircle(ink.copy(alpha = (alpha * 1.6f * p[3]).coerceAtMost(1f)), radius = p[2], center = Offset(p[0], p[1]))
        }
        return
    }
    tile.shapes.forEachIndexed { i, s ->
        val paint = ink.copy(alpha = alpha)
        val stroke = Stroke(width = s.sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val draw: DrawScope.() -> Unit = {
            when (s.t) {
                "circle" -> if (s.fill) drawCircle(paint, s.r, Offset(s.cx, s.cy)) else drawCircle(paint, s.r, Offset(s.cx, s.cy), style = stroke)
                "rect" -> if (s.fill) drawRoundRect(paint, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(s.rx))
                    else drawRoundRect(paint, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(s.rx), style = stroke)
                else -> paths[i]?.let { if (s.fill) drawPath(it, paint) else drawPath(it, paint, style = stroke) }
            }
        }
        val rot = s.rot
        if (rot != null) rotate(rot[0], pivot = Offset(rot[1], rot[2])) { draw() } else draw()
    }
}
