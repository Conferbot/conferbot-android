package com.conferbot.sdk.ui.compose

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import com.conferbot.sdk.core.customization.IconGradient
import com.conferbot.sdk.core.customization.IconPaint
import com.conferbot.sdk.core.customization.LauncherIcon
import com.conferbot.sdk.core.customization.LauncherIcons
import com.conferbot.sdk.core.customization.LauncherMotion

private val pathCache = HashMap<String, Path>()

private fun pathOf(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/**
 * A gradient in the shape's own bounding box (SVG objectBoundingBox): built
 * in a unit box and mapped onto the bounds with a local matrix, so a radial
 * gradient on a wide shape is an ellipse, as on the web.
 */
private fun gradientBrush(g: IconGradient, paint: IconPaint, path: Path, color: (String) -> Color): Brush {
    val b = path.getBounds()
    val colors = g.stops.map { s -> color(s.c).let { it.copy(alpha = (it.alpha * s.a * paint.a).toFloat()) }.toArgb() }.toIntArray()
    val offsets = g.stops.map { it.o.toFloat() }.toFloatArray()
    val shader: Shader = if (g.t == "r") {
        RadialGradient(g.cx.toFloat(), g.cy.toFloat(), g.r.toFloat().coerceAtLeast(0.0001f), colors, offsets, Shader.TileMode.CLAMP)
    } else {
        LinearGradient(g.x1.toFloat(), g.y1.toFloat(), g.x2.toFloat(), g.y2.toFloat(), colors, offsets, Shader.TileMode.CLAMP)
    }
    shader.setLocalMatrix(android.graphics.Matrix().apply {
        setScale(b.width.coerceAtLeast(0.0001f), b.height.coerceAtLeast(0.0001f))
        postTranslate(b.left, b.top)
    })
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
    }
}

/**
 * Draw a launcher icon (core/customization/LauncherIcons) filling a square
 * of [sizePx], in [ink] with its cut-outs in [bg], at rest: parts that only
 * show while animating stay hidden.
 */
fun DrawScope.drawLauncherIcon(id: String?, ink: Color, bg: Color, sizePx: Float, frame: List<LauncherMotion.OpFrame?>? = null) {
    drawLauncherIcon(LauncherIcons.byId(id), ink, bg, sizePx, frame)
}

private fun composeMatrix(m: List<Double>) = Matrix().apply {
    // [a b c d e f] -> column-major 4x4.
    values[Matrix.ScaleX] = m[0].toFloat(); values[Matrix.SkewY] = m[1].toFloat()
    values[Matrix.SkewX] = m[2].toFloat(); values[Matrix.ScaleY] = m[3].toFloat()
    values[Matrix.TranslateX] = m[4].toFloat(); values[Matrix.TranslateY] = m[5].toFloat()
}

private fun invert(m: List<Double>): List<Double> {
    val det = m[0] * m[3] - m[1] * m[2]
    if (det == 0.0) return listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    return listOf(m[3] / det, -m[1] / det, -m[2] / det, m[0] / det, (m[2] * m[5] - m[3] * m[4]) / det, (m[1] * m[4] - m[0] * m[5]) / det)
}

/**
 * The icon, at rest or mid-moment ([frame] from LauncherMotion.iconFrame):
 * moving parts in place, a clip with its own transform, and an animated
 * opacity fading its whole group as one layer, as CSS opacity does.
 */
fun DrawScope.drawLauncherIcon(icon: LauncherIcon, ink: Color, bg: Color, sizePx: Float, frame: List<LauncherMotion.OpFrame?>? = null) {
    val color = { c: String ->
        when (c) {
            "ink" -> ink
            "bg" -> bg
            else -> lookColor(c) ?: Color.Black
        }
    }
    val scale = sizePx / 24f
    val canvas = drawContext.canvas
    withTransform({ scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
        val open = ArrayList<Int>()
        icon.ops.forEachIndexed { i, o ->
            val f = frame?.getOrNull(i)
            val layers = f?.layers.orEmpty()
            // A layer that is the shape's own element replaces its own opacity.
            val ownGroup = if (o.self == 1) o.parts?.lastOrNull()?.g else null
            val ownLayer = layers.lastOrNull()?.takeIf { it.first == ownGroup }
            val groupLayers = if (ownLayer != null) layers.dropLast(1) else layers
            var keep = 0
            while (keep < open.size && keep < groupLayers.size && open[keep] == groupLayers[keep].first) keep++
            while (open.size > keep) { canvas.restore(); open.removeAt(open.size - 1) }
            for (k in keep until groupLayers.size) {
                canvas.saveLayer(androidx.compose.ui.geometry.Rect(-24f, -24f, 48f, 48f), androidx.compose.ui.graphics.Paint().apply { alpha = groupLayers[k].second.toFloat() })
                open.add(groupLayers[k].first)
            }
            // At rest op; moving, an animated part's opacity replaces its own.
            var opacity = o.op ?: 1.0
            if (layers.isNotEmpty()) {
                val animated = layers.map { it.first }.toSet()
                opacity = o.opRest ?: if (o.parts.orEmpty().any { it.o != null }) 1.0 else (o.op ?: 1.0)
                for (p in o.parts.orEmpty()) if (p.g !in animated && p.o != null) opacity *= p.o
                if (ownLayer != null) opacity *= ownLayer.second
            }
            if (opacity == 0.0) return@forEachIndexed
            val path = pathOf(o.d)
            if (o.rule == "evenodd") path.fillType = PathFillType.EvenOdd
            val alpha = opacity.toFloat()
            val m = f?.m ?: o.m
            fun brushOf(p: IconPaint): Brush =
                if (p.g != null) gradientBrush(icon.grads[p.g], p, path, color)
                else SolidColor(color(p.c ?: "#000000").let { it.copy(alpha = (it.alpha * p.a).toFloat()) })
            val drawFill = { o.fill?.let { drawPath(path, brushOf(it), alpha = alpha, style = Fill) } }
            val drawStroke = {
                o.stroke?.let {
                    drawPath(
                        path, brushOf(it), alpha = alpha,
                        style = Stroke(
                            width = (o.sw ?: 1.0).toFloat(),
                            cap = when (o.cap) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
                            join = when (o.join) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
                        ),
                    )
                }
            }
            val paintShape = {
                if (o.strokeFirst == true) { drawStroke(); drawFill() } else { drawFill(); drawStroke() }
            }
            val clip = o.clip
            val clipM = f?.clipM
            if (clip != null && clipM != null && m != null) {
                // The clip in its own space, then the shape's transform.
                withTransform({ transform(composeMatrix(clipM)) }) {
                    clipPath(pathOf(clip)) {
                        withTransform({ transform(composeMatrix(LauncherMotion.mul(invert(clipM), m))) }) { paintShape() }
                    }
                }
            } else {
                withTransform({ if (m != null && m.size == 6) transform(composeMatrix(m)) }) {
                    if (clip != null) clipPath(pathOf(clip)) { paintShape() } else paintShape()
                }
            }
        }
        while (open.isNotEmpty()) { canvas.restore(); open.removeAt(open.size - 1) }
    }
}
