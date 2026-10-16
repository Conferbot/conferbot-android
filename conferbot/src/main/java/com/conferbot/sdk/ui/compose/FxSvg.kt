package com.conferbot.sdk.ui.compose

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import com.conferbot.sdk.core.customization.FxPaint
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A small SVG renderer for the launcher effects' drawings (the markup built
 * by core/customization/LauncherEntrance.kt, MascotArt.kt and FxPaint.kt,
 * the same markup the React Native SDK hands to react-native-svg). It covers
 * what those drawings use: svg (viewBox, preserveAspectRatio none), g
 * (transform, clip-path, opacity), path, ellipse, circle, rect (rx),
 * polygon; fill / stroke as a colour, none or a linear / radial gradient
 * (object bounding box or user space, with a gradientTransform), their
 * opacities, stroke width, caps and joins; and clipPath. No filters, as in
 * RN. Draws on the platform canvas (Compose's nativeCanvas).
 */
internal object FxSvg {
    private class Node(val tag: String, val a: Map<String, String>) {
        val kids = mutableListOf<Node>()
    }

    private class Doc(val root: Node, val ids: Map<String, Node>, val viewBox: FloatArray, val stretch: Boolean)

    private val docs = object : LinkedHashMap<String, Doc>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Doc>?) = size > 400
    }
    private val paths = object : LinkedHashMap<String, Path>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Path>?) = size > 800
    }

    private val TAG = Regex("<(/?)([a-zA-Z][\\w-]*)((?:\\s+[\\w:-]+=\"[^\"]*\")*)\\s*(/?)>")
    private val ATTR = Regex("([\\w:-]+)=\"([^\"]*)\"")
    private val SEP = Regex("[\\s,]+")
    private val FN = Regex("(\\w+)\\(([^)]*)\\)")
    private val URL = Regex("^url\\(#([^)]+)\\)$")

    private fun parse(svg: String): Doc {
        val root = Node("#root", emptyMap())
        val stack = mutableListOf(root)
        val ids = HashMap<String, Node>()
        for (m in TAG.findAll(svg)) {
            if (m.groupValues[1] == "/") {
                if (stack.size > 1) stack.removeAt(stack.size - 1)
                continue
            }
            val attrs = ATTR.findAll(m.groupValues[3]).associate { it.groupValues[1] to it.groupValues[2] }
            val n = Node(m.groupValues[2], attrs)
            stack.last().kids.add(n)
            attrs["id"]?.let { ids[it] = n }
            if (m.groupValues[4] != "/") stack.add(n)
        }
        val svgNode = root.kids.firstOrNull { it.tag == "svg" } ?: root
        val vb = (svgNode.a["viewBox"] ?: "").trim().split(SEP).mapNotNull { it.toFloatOrNull() }
        val w = svgNode.a["width"]?.toFloatOrNull()
        val h = svgNode.a["height"]?.toFloatOrNull()
        val viewBox = if (vb.size == 4) vb.toFloatArray() else floatArrayOf(0f, 0f, w ?: 100f, h ?: 100f)
        return Doc(svgNode, ids, viewBox, svgNode.a["preserveAspectRatio"] == "none")
    }

    private fun d(a: Map<String, String>, k: String, fallback: Float = 0f) = a[k]?.trim()?.toFloatOrNull() ?: fallback

    /** "rotate(a cx cy) translate(x y) scale(x y)", applied in order. */
    private fun transform(t: String?): Matrix {
        val m = Matrix()
        if (t == null) return m
        for (f in FN.findAll(t)) {
            val n = f.groupValues[2].trim().split(SEP).map { it.toFloatOrNull() ?: 0f }
            when (f.groupValues[1]) {
                "translate" -> m.preTranslate(n[0], if (n.size > 1) n[1] else 0f)
                "scale" -> m.preScale(n[0], if (n.size > 1) n[1] else n[0])
                "rotate" -> if (n.size > 2) m.preRotate(n[0], n[1], n[2]) else m.preRotate(n[0])
            }
        }
        return m
    }

    private fun pathOf(d: String): Path = synchronized(paths) {
        paths.getOrPut(d) { runCatching { PathParser().parsePathString(d).toPath().asAndroidPath() }.getOrElse { Path() } }
    }

    /** The shape of an element, or null when it is not a shape. */
    private fun shape(n: Node): Path? {
        val a = n.a
        return when (n.tag) {
            "path" -> pathOf(a["d"] ?: "")
            "ellipse" -> {
                val cx = d(a, "cx"); val cy = d(a, "cy"); val rx = d(a, "rx"); val ry = d(a, "ry")
                Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
            }
            "circle" -> Path().apply { addCircle(d(a, "cx"), d(a, "cy"), d(a, "r"), Path.Direction.CW) }
            "rect" -> {
                val x = d(a, "x"); val y = d(a, "y"); val w = d(a, "width"); val h = d(a, "height")
                val rx = min(d(a, "rx", d(a, "ry")), w / 2)
                val ry = min(d(a, "ry", rx), h / 2)
                Path().apply { addRoundRect(RectF(x, y, x + w, y + h), rx, ry, Path.Direction.CW) }
            }
            "polygon" -> {
                val p = (a["points"] ?: "").trim().split(SEP).map { it.toFloatOrNull() ?: 0f }
                Path().apply {
                    var i = 0
                    while (i + 1 < p.size) {
                        if (i == 0) moveTo(p[i], p[i + 1]) else lineTo(p[i], p[i + 1])
                        i += 2
                    }
                    close()
                }
            }
            else -> null
        }
    }

    private fun argb(c: DoubleArray, opacity: Float): Int {
        val a = (c[3] * opacity * 255).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)
    }

    /** A paint for a fill / stroke value over a shape's bounds, or null for none. */
    private fun paint(doc: Doc, value: String?, opacity: Float, bounds: RectF): Paint? {
        val v = (value ?: "").trim()
        if (v.isEmpty() || v == "none") return null
        val url = URL.find(v)
        if (url != null) {
            val g = doc.ids[url.groupValues[1]] ?: return null
            val colors = mutableListOf<Int>()
            val at = mutableListOf<Float>()
            for (s in g.kids.filter { it.tag == "stop" }) {
                val c = FxPaint.parseCssColor(s.a["stop-color"] ?: "#000") ?: doubleArrayOf(0.0, 0.0, 0.0, 1.0)
                colors.add(argb(c, d(s.a, "stop-opacity", 1f) * opacity))
                at.add(d(s.a, "offset").coerceIn(0f, 1f).let { o -> if (at.isNotEmpty() && o < at.last()) at.last() else o })
            }
            if (colors.isEmpty()) return null
            if (colors.size == 1) {
                colors.add(colors.first())
                at.add(1f)
            }
            val user = g.a["gradientUnits"] == "userSpaceOnUse"
            // Object bounding box units: the unit square stretched over the shape.
            val m = if (user) transform(g.a["gradientTransform"]) else Matrix().apply {
                preTranslate(bounds.left, bounds.top)
                preScale(bounds.width().coerceAtLeast(1e-4f), bounds.height().coerceAtLeast(1e-4f))
                preConcat(transform(g.a["gradientTransform"]))
            }
            val shader: Shader = if (g.tag == "linearGradient") {
                LinearGradient(d(g.a, "x1"), d(g.a, "y1"), d(g.a, "x2", if (user) 0f else 1f), d(g.a, "y2"), colors.toIntArray(), at.toFloatArray(), Shader.TileMode.CLAMP)
            } else {
                RadialGradient(d(g.a, "cx", 0.5f), d(g.a, "cy", 0.5f), d(g.a, "r", 0.5f).coerceAtLeast(1e-4f), colors.toIntArray(), at.toFloatArray(), Shader.TileMode.CLAMP)
            }
            shader.setLocalMatrix(m)
            return Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        }
        val c = FxPaint.parseCssColor(v) ?: return null
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(c, opacity) }
    }

    private fun draw(canvas: Canvas, doc: Doc, n: Node, inherited: Float) {
        val a = n.a
        if (n.tag == "defs" || n.tag == "clipPath" || n.tag == "linearGradient" || n.tag == "radialGradient" || n.tag == "stop") return
        val own = d(a, "opacity", 1f)
        val save = canvas.save()
        a["transform"]?.let { canvas.concat(transform(it)) }
        URL.find(a["clip-path"] ?: "")?.let { clip ->
            doc.ids[clip.groupValues[1]]?.let { cp ->
                val p = Path()
                for (k in cp.kids) {
                    val s = shape(k) ?: continue
                    val t = k.a["transform"]
                    if (t == null) p.addPath(s) else p.addPath(s, transform(t))
                }
                canvas.clipPath(p)
            }
        }
        val shape = shape(n)
        if (shape != null) {
            val o = inherited * own
            val bounds = RectF()
            @Suppress("DEPRECATION")
            shape.computeBounds(bounds, true)
            paint(doc, a["fill"] ?: "#000", o * d(a, "fill-opacity", 1f), bounds)?.let {
                it.style = Paint.Style.FILL
                canvas.drawPath(shape, it)
            }
            paint(doc, a["stroke"], o * d(a, "stroke-opacity", 1f), bounds)?.let {
                it.style = Paint.Style.STROKE
                it.strokeWidth = d(a, "stroke-width", 1f)
                it.strokeCap = when (a["stroke-linecap"]) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                it.strokeJoin = when (a["stroke-linejoin"]) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
                canvas.drawPath(shape, it)
            }
        } else if (n.kids.isNotEmpty()) {
            // A group's opacity fades its drawing as one.
            val layer = own < 1 && n.tag == "g"
            if (layer) canvas.saveLayerAlpha(null, (own * 255).roundToInt().coerceIn(0, 255))
            for (k in n.kids) draw(canvas, doc, k, if (layer) inherited else inherited * own)
            if (layer) canvas.restore()
        }
        canvas.restoreToCount(save)
    }

    /** Draws [svg] filling a w x h box at the canvas origin. */
    fun draw(canvas: Canvas, svg: String, w: Float, h: Float) {
        val doc = synchronized(docs) { docs.getOrPut(svg) { parse(svg) } }
        val vb = doc.viewBox
        if (vb[2] <= 0 || vb[3] <= 0 || w <= 0 || h <= 0) return
        var sx = w / vb[2]
        var sy = h / vb[3]
        var dx = 0f
        var dy = 0f
        if (!doc.stretch) {
            // xMidYMid meet.
            val s = min(sx, sy)
            dx = (w - vb[2] * s) / 2
            dy = (h - vb[3] * s) / 2
            sx = s
            sy = s
        }
        val save = canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(sx, sy)
        canvas.translate(-vb[0], -vb[1])
        for (k in doc.root.kids) draw(canvas, doc, k, 1f)
        canvas.restoreToCount(save)
    }
}
