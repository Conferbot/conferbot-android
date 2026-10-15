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
fun DrawScope.drawLauncherIcon(id: String?, ink: Color, bg: Color, sizePx: Float) {
    drawLauncherIcon(LauncherIcons.byId(id), ink, bg, sizePx)
}

fun DrawScope.drawLauncherIcon(icon: LauncherIcon, ink: Color, bg: Color, sizePx: Float) {
    val color = { c: String ->
        when (c) {
            "ink" -> ink
            "bg" -> bg
            else -> lookColor(c) ?: Color.Black
        }
    }
    val scale = sizePx / 24f
    withTransform({ scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
        for (o in icon.ops) {
            if (o.op == 0.0) continue
            val path = pathOf(o.d)
            if (o.rule == "evenodd") path.fillType = PathFillType.EvenOdd
            val alpha = (o.op ?: 1.0).toFloat()
            val m = o.m
            val matrix = if (m != null && m.size == 6) Matrix().apply {
                // [a b c d e f] -> column-major 4x4.
                values[Matrix.ScaleX] = m[0].toFloat(); values[Matrix.SkewY] = m[1].toFloat()
                values[Matrix.SkewX] = m[2].toFloat(); values[Matrix.ScaleY] = m[3].toFloat()
                values[Matrix.TranslateX] = m[4].toFloat(); values[Matrix.TranslateY] = m[5].toFloat()
            } else null
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
            withTransform({ if (matrix != null) transform(matrix) }) {
                val clip = o.clip
                if (clip != null) clipPath(pathOf(clip)) { paintShape() } else paintShape()
            }
        }
    }
}
