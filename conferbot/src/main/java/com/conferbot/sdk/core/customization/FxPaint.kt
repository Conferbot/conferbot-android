package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.customization.Keyframes.fixed
import com.conferbot.sdk.core.customization.Keyframes.jsNum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The paints the launcher effects use on the web (CSS radial / linear /
 * conic gradients, as in conferbot-widget src/ts/launcher/entrance.ts and
 * attention.ts), written as SVG markup (drawn by ui/compose/FxSvg.kt); and a
 * motion path (CSS offset-path) as points along it. Ported from the React
 * Native SDK's core/fxPaint.ts. Pure.
 */
object FxPaint {
    private val HEX = Regex("^#([0-9a-f]{3}|[0-9a-f]{6})$")
    private val RGBA = Regex("^rgba?\\(\\s*([\\d.]+)[\\s,]+([\\d.]+)[\\s,]+([\\d.]+)(?:[\\s,/]+([\\d.]+))?\\s*\\)$")

    /** A CSS colour: #rgb, #rrggbb, rgb(), rgba(), "transparent" (null: unknown). [r, g, b, a]. */
    fun parseCssColor(c: String): DoubleArray? {
        val s = c.trim().lowercase()
        if (s == "transparent") return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        HEX.find(s)?.let { m ->
            val g = m.groupValues[1]
            val h = if (g.length == 3) g.map { "$it$it" }.joinToString("") else g
            return doubleArrayOf(h.substring(0, 2).toInt(16).toDouble(), h.substring(2, 4).toInt(16).toDouble(), h.substring(4, 6).toInt(16).toDouble(), 1.0)
        }
        RGBA.find(s)?.let { m ->
            val a = m.groupValues[4]
            return doubleArrayOf(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), m.groupValues[3].toDouble(), if (a.isEmpty()) 1.0 else a.toDouble())
        }
        return null
    }

    /** One colour stop; `at` 0..1, missing positions spread evenly as CSS does. */
    data class Stop(val color: String, val at: Double? = null)

    /** "rgba(1,2,3,.5) 30%" pieces, as a CSS gradient lists them: a colour, or (colour to percent). */
    fun stops(vararg list: Any): List<Stop> = list.map { x ->
        if (x is Pair<*, *>) Stop(x.first as String, (x.second as Number).toDouble() / 100) else Stop(x as String)
    }

    private class S(val c: DoubleArray, var at: Double?)

    private fun spread(list: List<Stop>): List<S> {
        val out = list.map { S(parseCssColor(it.color) ?: doubleArrayOf(0.0, 0.0, 0.0, 0.0), it.at) }
        if (out[0].at == null) out[0].at = 0.0
        if (out[out.size - 1].at == null) out[out.size - 1].at = 1.0
        for (i in 1 until out.size - 1) {
            if (out[i].at != null) continue
            var j = i
            while (out[j].at == null) j++
            val a = out[i - 1].at!!
            val b = out[j].at!!
            for (k in i until j) out[k].at = a + ((b - a) * (k - i + 1)) / (j - i + 1)
        }
        // A fully transparent stop fades its neighbour's colour (CSS interpolates
        // premultiplied; SVG does not, so the colour is borrowed).
        return out.mapIndexed { i, s ->
            if (s.c[3] > 0) return@mapIndexed s
            val prev = if (i > 0) out[i - 1] else null
            val next = if (i < out.size - 1) out[i + 1] else null
            val near = if (prev != null && prev.c[3] > 0) prev else if (next != null && next.c[3] > 0) next else (prev ?: next)
            val n = near?.c ?: s.c
            S(doubleArrayOf(n[0], n[1], n[2], s.c[3]), s.at)
        }
    }

    private fun jsRound(x: Double) = kotlin.math.floor(x + 0.5).toInt()

    private fun stopTags(list: List<Stop>) = spread(list).joinToString("") { s ->
        "<stop offset=\"${fixed(s.at!!, 4)}\" stop-color=\"rgb(${jsRound(s.c[0])},${jsRound(s.c[1])},${jsRound(s.c[2])})\" stop-opacity=\"${fixed(s.c[3], 3)}\"/>"
    }

    private fun svg(w: Number, h: Number, inner: String, vb: String? = null): String {
        val fw = fixed(w.toDouble(), 2)
        val fh = fixed(h.toDouble(), 2)
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$fw\" height=\"$fh\" viewBox=\"${vb ?: "0 0 $fw $fh"}\">$inner</svg>"
    }

    /** radial-gradient(closest-side, ...) filling an ellipse in a w x h box. */
    fun radialDisc(w: Double, h: Double, list: List<Stop>): String = svg(w, h,
        "<defs><radialGradient id=\"g\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">${stopTags(list)}</radialGradient></defs><ellipse cx=\"${jsNum(w / 2)}\" cy=\"${jsNum(h / 2)}\" rx=\"${jsNum(w / 2)}\" ry=\"${jsNum(h / 2)}\" fill=\"url(#g)\"/>")

    /** radial-gradient(ellipse rx ry at cx cy, ...) over a w x h box (a page wash): the ellipse's radii in px. */
    fun radialWash(w: Double, h: Double, cx: Double, cy: Double, rx: Double, ry: Double, list: List<Stop>): String = svg(w, h,
        "<defs><radialGradient id=\"g\" gradientUnits=\"userSpaceOnUse\" cx=\"${jsNum(cx)}\" cy=\"${jsNum(cy)}\" r=\"${jsNum(rx)}\" gradientTransform=\"translate(${jsNum(cx)} ${jsNum(cy)}) scale(1 ${jsNum(ry / rx)}) translate(${jsNum(-cx)} ${jsNum(-cy)})\">${stopTags(list)}</radialGradient></defs><rect width=\"${jsNum(w)}\" height=\"${jsNum(h)}\" fill=\"url(#g)\"/>")

    /**
     * linear-gradient(<angle>, ...) in a w x h box with rounded corners (CSS angles: 180deg = to bottom).
     * `radius`: one number, or [tl, tr, br, bl].
     */
    fun linearBox(w: Double, h: Double, angle: Double, list: List<Stop>, radius: List<Double> = listOf(0.0)): String {
        val a = angle * PI / 180
        // The CSS gradient line: through the centre, long enough that the corners get the end colours.
        val len = abs(w * sin(a)) + abs(h * cos(a))
        val dx = (sin(a) * len) / 2
        val dy = (-cos(a) * len) / 2
        val r = if (radius.size >= 4) radius else List(4) { radius[0] }
        val (tl, tr, br, bl) = r
        val shape = if (tl != 0.0 || tr != 0.0 || br != 0.0 || bl != 0.0)
            "<path d=\"M${jsNum(tl)} 0 H${jsNum(w - tr)} A${jsNum(tr)} ${jsNum(tr)} 0 0 1 ${jsNum(w)} ${jsNum(tr)} V${jsNum(h - br)} A${jsNum(br)} ${jsNum(br)} 0 0 1 ${jsNum(w - br)} ${jsNum(h)} H${jsNum(bl)} A${jsNum(bl)} ${jsNum(bl)} 0 0 1 0 ${jsNum(h - bl)} V${jsNum(tl)} A${jsNum(tl)} ${jsNum(tl)} 0 0 1 ${jsNum(tl)} 0 Z\" fill=\"url(#g)\"/>"
        else "<rect width=\"${jsNum(w)}\" height=\"${jsNum(h)}\" fill=\"url(#g)\"/>"
        return svg(w, h,
            "<defs><linearGradient id=\"g\" gradientUnits=\"userSpaceOnUse\" x1=\"${jsNum(w / 2 - dx)}\" y1=\"${jsNum(h / 2 - dy)}\" x2=\"${jsNum(w / 2 + dx)}\" y2=\"${jsNum(h / 2 + dy)}\">${stopTags(list)}</linearGradient></defs>$shape")
    }

    fun linearBox(w: Double, h: Double, angle: Double, list: List<Stop>, radius: Double) = linearBox(w, h, angle, list, listOf(radius))

    private fun mixAt(list: List<S>, p: Double): DoubleArray {
        if (p <= list[0].at!!) return list[0].c
        for (i in 0 until list.size - 1) {
            val a = list[i]
            val b = list[i + 1]
            if (p <= b.at!!) {
                val f = if (b.at!! > a.at!!) (p - a.at!!) / (b.at!! - a.at!!) else 1.0
                // Premultiplied, as CSS gradients interpolate.
                val al = a.c[3] + (b.c[3] - a.c[3]) * f
                fun ch(k: Int) = if (al > 0) (a.c[k] * a.c[3] + (b.c[k] * b.c[3] - a.c[k] * a.c[3]) * f) / al else 0.0
                return doubleArrayOf(ch(0), ch(1), ch(2), al)
            }
        }
        return list.last().c
    }

    /**
     * conic-gradient(from <from>deg, ...) in a circle of diameter d, as thin
     * wedges; `ring` keeps only the band between two shares of the radius
     * (the portal's ring mask).
     */
    fun conicDisc(d: Double, from: Double, list: List<Stop>, ring: Pair<Double, Double>? = null, slices: Int = 48): String {
        val s = spread(list)
        val r = d / 2
        val ri = if (ring != null) ring.first * r else 0.0
        val ro = if (ring != null) ring.second * r else r
        val out = StringBuilder()
        for (i in 0 until slices) {
            val p = (i + 0.5) / slices
            val c = mixAt(s, p)
            if (c[3] <= 0.003) continue
            // CSS: 0deg points up, clockwise.
            val a0 = ((from + (i.toDouble() / slices) * 360 - 90) * PI) / 180
            val a1 = ((from + ((i + 1.04) / slices) * 360 - 90) * PI) / 180
            fun pt(rad: Double, a: Double) = "${fixed(r + rad * cos(a), 2)} ${fixed(r + rad * sin(a), 2)}"
            val path = if (ri > 0)
                "M${pt(ro, a0)} A${jsNum(ro)} ${jsNum(ro)} 0 0 1 ${pt(ro, a1)} L${pt(ri, a1)} A${jsNum(ri)} ${jsNum(ri)} 0 0 0 ${pt(ri, a0)} Z"
            else "M${jsNum(r)} ${jsNum(r)} L${pt(ro, a0)} A${jsNum(ro)} ${jsNum(ro)} 0 0 1 ${pt(ro, a1)} Z"
            out.append("<path d=\"$path\" fill=\"rgb(${jsRound(c[0])},${jsRound(c[1])},${jsRound(c[2])})\" fill-opacity=\"${fixed(c[3], 3)}\"/>")
        }
        return svg(d, d, out.toString())
    }

    // ---- A motion path (offset-path: path("M ... C ... a ...")) ----

    private val TOKEN = Regex("[a-zA-Z]|-?\\d*\\.?\\d+(?:e[-+]?\\d+)?")
    private val IS_CMD = Regex("[a-zA-Z]")

    /** The path flattened to points (M, L, C and a / A arcs, absolute or relative). */
    fun flattenPath(d: String, steps: Int = 24): List<Pair<Double, Double>> {
        val tok = TOKEN.findAll(d).map { it.value }.toList()
        val pts = mutableListOf<Pair<Double, Double>>()
        var i = 0
        var cur = 0.0 to 0.0
        var cmd = ""
        fun n(): Double = tok[i++].toDouble()
        while (i < tok.size) {
            if (IS_CMD.matches(tok[i])) cmd = tok[i++]
            val rel = cmd == cmd.lowercase()
            val c = cmd.uppercase()
            if (c == "M" || c == "L") {
                val x = n() + if (rel) cur.first else 0.0
                val y = n() + if (rel) cur.second else 0.0
                cur = x to y
                pts.add(cur)
                if (c == "M") cmd = if (rel) "l" else "L"
            } else if (c == "C") {
                val o = if (rel) cur else 0.0 to 0.0
                val p1 = n() + o.first to n() + o.second
                val p2 = n() + o.first to n() + o.second
                val p3 = n() + o.first to n() + o.second
                val p0 = cur
                for (k in 1..steps) {
                    val t = k.toDouble() / steps
                    val u = 1 - t
                    pts.add(
                        u * u * u * p0.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first to
                            u * u * u * p0.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second,
                    )
                }
                cur = p3
            } else if (c == "A") {
                var rx = abs(n())
                var ry = abs(n())
                val phi = (n() * PI) / 180
                val large = n() != 0.0
                val sweep = n() != 0.0
                val x2 = n() + if (rel) cur.first else 0.0
                val y2 = n() + if (rel) cur.second else 0.0
                val (x1, y1) = cur
                // Endpoint to centre parameterisation (SVG implementation notes F.6.5).
                val cp = cos(phi)
                val sp = sin(phi)
                val dx = (x1 - x2) / 2
                val dy = (y1 - y2) / 2
                val x1p = cp * dx + sp * dy
                val y1p = -sp * dx + cp * dy
                val lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
                if (lam > 1) {
                    rx *= sqrt(lam)
                    ry *= sqrt(lam)
                }
                val num0 = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
                val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
                var co = sqrt(max(0.0, num0 / den))
                if (large == sweep) co = -co
                val cxp = (co * rx * y1p) / ry
                val cyp = (-co * ry * x1p) / rx
                val cx = cp * cxp - sp * cyp + (x1 + x2) / 2
                val cy = sp * cxp + cp * cyp + (y1 + y2) / 2
                fun ang(ux: Double, uy: Double, vx: Double, vy: Double) = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
                val t1 = ang(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
                var dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
                if (!sweep && dt > 0) dt -= 2 * PI
                if (sweep && dt < 0) dt += 2 * PI
                val k = max(steps, ceil((abs(dt) / (2 * PI)) * steps * 3).toInt())
                for (j in 1..k) {
                    val t = t1 + (dt * j) / k
                    pts.add(cx + rx * cos(t) * cp - ry * sin(t) * sp to cy + rx * cos(t) * sp + ry * sin(t) * cp)
                }
                cur = x2 to y2
            } else {
                i++
            }
        }
        return pts
    }

    data class Along(val x: Double, val y: Double, val angle: Double)

    /** A point at a share (0..1) of a flattened path's length, and the path's direction there (deg). */
    fun pointAlong(pts: List<Pair<Double, Double>>, share: Double): Along {
        val seg = mutableListOf<Double>()
        var total = 0.0
        for (i in 1 until pts.size) {
            val dx = pts[i].first - pts[i - 1].first
            val dy = pts[i].second - pts[i - 1].second
            val l = sqrt(dx * dx + dy * dy)
            seg.add(l)
            total += l
        }
        var d = share.coerceIn(0.0, 1.0) * total
        for (i in seg.indices) {
            if (d <= seg[i] || i == seg.size - 1) {
                val f = if (seg[i] > 0) min(1.0, d / seg[i]) else 0.0
                val a = pts[i]
                val b = pts[i + 1]
                return Along(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f, (atan2(b.second - a.second, b.first - a.first) * 180) / PI)
            }
            d -= seg[i]
        }
        val last = pts.lastOrNull() ?: (0.0 to 0.0)
        return Along(last.first, last.second, 0.0)
    }
}
