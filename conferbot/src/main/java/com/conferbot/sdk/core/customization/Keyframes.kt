package com.conferbot.sdk.core.customization

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Web Animations, evaluated: the launcher's entrance, attention and idle
 * call (conferbot-widget src/ts/launcher/entrance.ts, attention.ts,
 * idleCall.ts) are element.animate() calls on the individual translate /
 * scale / rotate properties and opacity. Their keyframes are kept here as the
 * web wrote them, and this works out each property at time t the way the
 * browser does:
 *
 *   - offsets: a keyframe without one is spaced evenly between its
 *     neighbours (the first defaults to 0, the last to 1);
 *   - per property, only the keyframes that set it count; a missing 0% or
 *     100% keyframe is the element's own value (the step from a missing 0%
 *     eases with the 0% keyframe's easing, as Chrome plays it);
 *   - each keyframe's `easing` eases the step that starts at it; the
 *     options' easing eases the whole iteration first;
 *   - delay, iterations (infinity loops) and fill (forwards / backwards /
 *     both; none: the element's own value outside the animation);
 *   - several animations on one element: the later one wins for the
 *     properties it sets;
 *   - the `transform` property: "scaleY(n)" (the eyelids) is the scale's y;
 *     a list ("translateY(-3px) scale(0.92, 1.14)", "rotate(-7deg)
 *     translateX(-1px)", "none") is interpolated function by function, as
 *     Chrome does for lists of the same functions (none is their identity),
 *     into tx / ty / sx / sy / r; [Keyframes.transformOrder] says in which
 *     order to draw them. Its values share the channels of the individual
 *     translate / scale / rotate, so an element animated both ways is drawn
 *     as two nested boxes (the individual properties outside);
 *   - CSS @keyframes ([Keyframes.cssKeyframes]): each selector's offsets,
 *     the animation's timing function on every keyframe, as CSS plays it.
 *
 * The same algorithm as the React Native SDK's core/keyframes.ts, which is
 * checked against Chrome's own animations frame by frame
 * (scripts/parity/launcherfx); src/test/resources/launcherfx-golden.json is
 * that golden. Pure.
 */

/** One keyframe, as the web passes it to element.animate(): `offset`, `easing` and the properties (strings or numbers). */
typealias Keyframe = Map<String, Any?>

data class TrackOptions(
    val duration: Double,
    val delay: Double = 0.0,
    /** Double.POSITIVE_INFINITY loops forever. */
    val iterations: Double = 1.0,
    val easing: String? = null,
    /** none, forwards, backwards, both, auto. */
    val fill: String = "none",
)

/** One animation: its keyframes and options. `part` names what it moves (attention). */
class Track(val keyframes: List<Keyframe>, val options: TrackOptions, val part: String? = null) {
    /** The same track, its delay changed. */
    fun withDelay(delay: Double) = Track(keyframes, options.copy(delay = delay), part)

    internal val compiled: Keyframes.Compiled by lazy { Keyframes.compile(this) }

    companion object {
        /** A track built as the web writes `{ duration, delay?, iterations?, easing?, fill? }`. */
        fun of(
            keyframes: List<Keyframe>,
            duration: Number,
            delay: Number? = null,
            iterations: Number? = null,
            easing: String? = null,
            fill: String? = null,
            part: String? = null,
        ) = Track(
            keyframes,
            TrackOptions(duration.toDouble(), (delay ?: 0).toDouble(), (iterations ?: 1).toDouble(), easing, fill ?: "none"),
            part,
        )

        /** From the golden's JSON (iterations -1 = infinity). */
        @Suppress("UNCHECKED_CAST")
        fun fromJson(j: Map<String, Any?>): Track {
            val o = j["options"] as Map<String, Any?>
            val it = o["iterations"] as Number?
            return Track(
                (j["keyframes"] as List<Map<String, Any?>>),
                TrackOptions(
                    duration = (o["duration"] as Number).toDouble(),
                    delay = ((o["delay"] as Number?) ?: 0).toDouble(),
                    iterations = if (it == null) 1.0 else if (it.toDouble() == -1.0) Double.POSITIVE_INFINITY else it.toDouble(),
                    easing = o["easing"] as String?,
                    fill = (o["fill"] as String?) ?: "none",
                ),
                j["part"] as String?,
            )
        }
    }
}

/** An element's values at a moment (the channels of [Keyframes.CHANNELS]). */
class FxValues internal constructor(val v: DoubleArray) {
    operator fun get(ch: String): Double = v[Keyframes.INDEX.getValue(ch)]
    operator fun set(ch: String, x: Double) { v[Keyframes.INDEX.getValue(ch)] = x }
    fun copy() = FxValues(v.copyOf())

    val tx get() = v[0]
    val ty get() = v[1]
    val txp get() = v[2]
    val typ get() = v[3]
    val sx get() = v[4]
    val sy get() = v[5]
    val r get() = v[6]
    val rx get() = v[7]
    val ry get() = v[8]
    val o get() = v[9]
    val od get() = v[10]
    val dash get() = v[11]
    val clip get() = v[12]
    val left get() = v[13]

    /** Only the channels away from rest (as the golden stores a sample). */
    val moved: Map<String, Double>
        get() = Keyframes.CHANNELS.indices.filter { abs(v[it] - Keyframes.REST[it]) > 1e-9 }.associate { Keyframes.CHANNELS[it] to v[it] }

    companion object {
        /** At rest, with `base` (the element's own values) over it. */
        fun rest(base: Map<String, Double>? = null): FxValues {
            val v = Keyframes.REST.copyOf()
            base?.forEach { (k, x) -> v[Keyframes.INDEX.getValue(k)] = x }
            return FxValues(v)
        }
    }
}

object Keyframes {
    /**
     * The animated channels: translate in px (tx, ty) plus a share of the
     * element's own box (txp, typ, in %), scale, rotate about z (r) and the 3D
     * turns (rx, ry) in degrees, opacity, offset-distance (od, %),
     * stroke-dashoffset (dash), clip-path inset from the bottom (clip, %) and
     * `left` (%).
     */
    val CHANNELS = listOf("tx", "ty", "txp", "typ", "sx", "sy", "r", "rx", "ry", "o", "od", "dash", "clip", "left")
    internal val REST = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0)
    internal val INDEX: Map<String, Int> = CHANNELS.withIndex().associate { it.value to it.index }

    private val EASE = mapOf(
        "linear" to listOf(0.0, 0.0, 1.0, 1.0),
        "ease" to listOf(0.25, 0.1, 0.25, 1.0),
        "ease-in" to listOf(0.42, 0.0, 1.0, 1.0),
        "ease-out" to listOf(0.0, 0.0, 0.58, 1.0),
        "ease-in-out" to listOf(0.42, 0.0, 0.58, 1.0),
    )
    private val LINEAR = EASE.getValue("linear")

    private val CUBIC = Regex("^cubic-bezier\\(([^)]*)\\)$")
    private val FLOAT = Regex("^\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)")

    /** A CSS easing as cubic-bezier control points (unknown: linear). */
    fun easingOf(e: String?): List<Double> {
        if (e == null) return LINEAR
        val s = e.trim()
        EASE[s]?.let { return it }
        val m = CUBIC.find(s)
        if (m != null) {
            val n = m.groupValues[1].split(",").map { jsParseFloat(it) }
            if (n.size == 4 && n.all { it.isFinite() }) return n
        }
        return LINEAR
    }

    private fun isLinear(e: List<Double>) = e[0] == 0.0 && e[1] == 0.0 && e[2] == 1.0 && e[3] == 1.0
    private fun apply(e: List<Double>, x: Double) = if (isLinear(e)) x else LauncherMotion.cubicBezier(e, x)

    /** JavaScript's parseFloat: the leading number of a string (NaN when none). */
    fun jsParseFloat(v: Any?): Double {
        if (v is Number) return v.toDouble()
        val m = FLOAT.find(v?.toString() ?: "") ?: return Double.NaN
        return m.groupValues[1].toDouble()
    }

    /** A number as JavaScript prints it (`${n}`): 1 not 1.0, -0 as 0, no exponent for everyday sizes. */
    fun jsNum(n: Number): String {
        if (n is Int || n is Long) return n.toString()
        val d = n.toDouble()
        if (d == 0.0) return "0"
        if (!d.isFinite()) return if (d.isNaN()) "NaN" else if (d > 0) "Infinity" else "-Infinity"
        if (d == floor(d) && abs(d) < 1e21) return BigDecimal(d).toPlainString()
        val a = abs(d)
        return if (a >= 1e-7 && a < 1e21) BigDecimal(d.toString()).stripTrailingZeros().toPlainString() else d.toString().replace("E", "e")
    }

    /** JavaScript's x.toFixed(d), back to a number as JS prints it. */
    fun fixed(x: Double, d: Int): String = jsNum(BigDecimal(x).setScale(d, RoundingMode.HALF_UP).toDouble())

    private val GROUPS = listOf("translate", "scale", "rotate", "opacity", "offsetDistance", "strokeDashoffset", "clipPath", "left", "transform")
    private val CHANNELS_OF = mapOf(
        "translate" to listOf("tx", "ty", "txp", "typ"),
        "scale" to listOf("sx", "sy"),
        "rotate" to listOf("r", "rx", "ry"),
        "opacity" to listOf("o"),
        "offsetDistance" to listOf("od"),
        "strokeDashoffset" to listOf("dash"),
        "clipPath" to listOf("clip"),
        "left" to listOf("left"),
        // "scaleY(n)" (the eyelids) is the scale's y; a function list sets them all (TRANSFORM_LIST).
        "transform" to listOf("sy"),
    )
    private val TRANSFORM_LIST = listOf("tx", "ty", "sx", "sy", "r")
    private val SCALE_Y_ONLY = Regex("^scaleY\\([^)]*\\)$")
    private val TRANSFORM_FN = Regex("([a-zA-Z]+)\\(([^)]*)\\)")
    private val FN_NAME = Regex("([a-zA-Z]+)\\(")
    private val ARGS = Regex("[\\s,]+")
    private val CSS_RULE = Regex("([^\\{\\}]+)\\{([^\\}]*)\\}")
    private val DASHED = Regex("-([a-z])")

    private fun transformText(k: Keyframe): String? = k["transform"]?.let { str(it).trim() }

    /** A track whose `transform` is a function list (not the eyelids' plain scaleY). */
    private fun listTransform(kfs: List<Keyframe>): Boolean = kfs.any { k -> transformText(k)?.let { !SCALE_Y_ONLY.matches(it) } ?: false }

    /** A CSS transform list as tx / ty (px), sx / sy, r (deg); "none" is the identity. */
    private fun transformList(s: String): Map<String, Double> {
        val out = linkedMapOf("tx" to 0.0, "ty" to 0.0, "sx" to 1.0, "sy" to 1.0, "r" to 0.0)
        if (s == "none") return out
        for (m in TRANSFORM_FN.findAll(s)) {
            val args = m.groupValues[2].split(ARGS).filter { it.isNotEmpty() }.map { jsParseFloat(it) }
            val a = args.firstOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val b = if (args.size > 1 && args[1].isFinite()) args[1] else null
            when (m.groupValues[1]) {
                "translate" -> { out["tx"] = a; out["ty"] = b ?: 0.0 }
                "translateX" -> out["tx"] = a
                "translateY" -> out["ty"] = a
                "scale" -> { out["sx"] = a; out["sy"] = b ?: a }
                "scaleX" -> out["sx"] = a
                "scaleY" -> out["sy"] = a
                "rotate" -> out["r"] = a
            }
        }
        return out
    }

    /**
     * The order a track's transform list draws its functions in ("t" translate,
     * "r" rotate, "s" scale), from its fullest list: "ts" for
     * "translateY(..) scale(..)", "rts" for "rotate(..) translateX(..)". The
     * individual properties (and a plain scaleY) draw translate, rotate, scale.
     */
    fun transformOrder(track: Track): String {
        var order = ""
        for (k in track.keyframes) {
            val s = transformText(k) ?: ""
            if (s.isEmpty() || s == "none" || SCALE_Y_ONLY.matches(s)) continue
            var o = ""
            for (m in FN_NAME.findAll(s)) {
                val fn = m.groupValues[1]
                val c = when {
                    fn.startsWith("translate") -> "t"
                    fn.startsWith("scale") -> "s"
                    fn.startsWith("rotate") -> "r"
                    else -> ""
                }
                if (c.isNotEmpty() && !o.contains(c)) o += c
            }
            if (o.length > order.length) order = o
        }
        for (c in "trs") if (!order.contains(c)) order += c
        return order
    }

    /**
     * A transform's values as the CSS matrix [a, b, c, d, e, f] (x' = a x + c y
     * + e, y' = b x + d y + f), its functions in [order] (transformOrder).
     */
    fun transformMatrix(v: FxValues, order: String = "trs"): DoubleArray {
        var m = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        fun mul(n: DoubleArray) {
            val (a1, b1, c1, d1) = m
            val e1 = m[4]
            val f1 = m[5]
            val (a2, b2, c2, d2) = n
            val e2 = n[4]
            val f2 = n[5]
            m = doubleArrayOf(a1 * a2 + c1 * b2, b1 * a2 + d1 * b2, a1 * c2 + c1 * d2, b1 * c2 + d1 * d2, a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)
        }
        for (op in order) {
            when (op) {
                't' -> mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, v.tx, v.ty))
                's' -> mul(doubleArrayOf(v.sx, 0.0, 0.0, v.sy, 0.0, 0.0))
                'r' -> {
                    val a = v.r * Math.PI / 180
                    mul(doubleArrayOf(Math.cos(a), Math.sin(a), -Math.sin(a), Math.cos(a), 0.0, 0.0))
                }
            }
        }
        return m
    }

    /**
     * A CSS @keyframes body ("0%,100%{transform:scaleY(0)}42.6%{transform:scaleY(1)}")
     * as keyframes, in offset order, each eased by the animation's timing
     * function (CSS eases every keyframe interval with it).
     */
    fun cssKeyframes(body: String, timing: String = "ease"): List<Keyframe> {
        val out = mutableListOf<Keyframe>()
        for (m in CSS_RULE.findAll(body)) {
            val props = LinkedHashMap<String, Any?>()
            for (decl in m.groupValues[2].split(";")) {
                val i = decl.indexOf(':')
                if (i < 0) continue
                val name = decl.substring(0, i).trim().replace(DASHED) { it.groupValues[1].uppercase() }
                val value = decl.substring(i + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) props[name] = value
            }
            for (sel in m.groupValues[1].split(",")) {
                val t = sel.trim()
                val offset = when (t) {
                    "from" -> 0.0
                    "to" -> 1.0
                    else -> jsParseFloat(t) / 100
                }
                if (offset.isFinite()) out.add(LinkedHashMap<String, Any?>().apply { put("offset", offset); put("easing", timing); putAll(props) })
            }
        }
        // A stable sort, as Array.prototype.sort.
        return out.sortedBy { (it["offset"] as Double) }
    }

    private val CALC_TERM = Regex("([+-]?)\\s*([\\d.]+)(px|%)")
    private val SPACE_OUTSIDE_CALC = Regex("\\s+(?![^(]*\\))")
    private val SPACES = Regex("\\s+")
    private val ROTATE = Regex("^(?:([xyz])\\s+)?(-?[\\d.]+)deg$")
    private val INSET = Regex("inset\\(([^)]*)\\)")
    private val SCALE_Y = Regex("scaleY\\(([^)]*)\\)")

    /** "12px", "-160%", "0", "calc(10% + 4px)": [px, %]. */
    private fun length(v: String): DoubleArray {
        val s = v.trim()
        if (s.startsWith("calc(")) {
            var px = 0.0
            var pc = 0.0
            for (m in CALC_TERM.findAll(s.substring(5, s.length - 1))) {
                val x = (if (m.groupValues[1] == "-") -1 else 1) * m.groupValues[2].toDouble()
                if (m.groupValues[3] == "%") pc += x else px += x
            }
            return doubleArrayOf(px, pc)
        }
        val n = jsParseFloat(s)
        if (!n.isFinite()) return doubleArrayOf(0.0, 0.0)
        return if (s.endsWith("%")) doubleArrayOf(0.0, n) else doubleArrayOf(n, 0.0)
    }

    private fun str(v: Any): String = if (v is Number) jsNum(v) else v.toString()

    /** One keyframe's value for a group, or null when it does not set it. */
    private fun parse(k: Keyframe, g: String, list: Boolean = false): Map<String, Double>? {
        val v = k[g] ?: return null
        val s = str(v).trim()
        if (g == "transform" && list) return transformList(s)
        return when (g) {
            "translate" -> {
                // Split on spaces outside calc().
                val parts = s.split(SPACE_OUTSIDE_CALC)
                val x = length(if (parts.isNotEmpty() && parts[0].isNotEmpty()) parts[0] else "0")
                val y = length(if (parts.size > 1) parts[1] else "0")
                mapOf("tx" to x[0], "txp" to x[1], "ty" to y[0], "typ" to y[1])
            }
            "scale" -> {
                val parts = s.split(SPACES).map { jsParseFloat(it) }
                mapOf("sx" to parts[0], "sy" to if (parts.size > 1) parts[1] else parts[0])
            }
            "rotate" -> {
                // "45deg", "y 180deg", "x 80deg".
                val m = ROTATE.find(s)
                val a = if (m != null) m.groupValues[2].toDouble() else jsParseFloat(s).let { if (it.isNaN()) 0.0 else it }
                val axis = m?.groupValues?.get(1)?.ifEmpty { null } ?: "z"
                mapOf("r" to if (axis == "z") a else 0.0, "rx" to if (axis == "x") a else 0.0, "ry" to if (axis == "y") a else 0.0)
            }
            "opacity" -> mapOf("o" to if (v is Number) v.toDouble() else jsParseFloat(s))
            "offsetDistance" -> mapOf("od" to jsParseFloat(s))
            "strokeDashoffset" -> mapOf("dash" to if (v is Number) v.toDouble() else jsParseFloat(s))
            "clipPath" -> {
                // inset(top right bottom left): the bottom inset, in %.
                val m = INSET.find(s)
                val p = m?.groupValues?.get(1)?.trim()?.split(SPACES) ?: emptyList()
                val b = if (p.size >= 3) jsParseFloat(p[2]) else 0.0
                mapOf("clip" to if (b.isNaN()) 0.0 else b)
            }
            "left" -> mapOf("left" to jsParseFloat(s))
            "transform" -> SCALE_Y.find(s)?.let { mapOf("sy" to jsParseFloat(it.groupValues[1])) }
            else -> null
        }
    }

    /** Offsets as the browser computes them: missing ones spaced evenly between their neighbours. */
    fun computedOffsets(kfs: List<Keyframe>): List<Double> {
        val out: MutableList<Double?> = kfs.map { (it["offset"] as? Number)?.toDouble() }.toMutableList()
        if (out.size == 1 && out[0] == null) out[0] = 1.0
        if (out.size > 1) {
            if (out[0] == null) out[0] = 0.0
            if (out[out.size - 1] == null) out[out.size - 1] = 1.0
        }
        var i = 0
        while (i < out.size) {
            if (out[i] != null) { i += 1; continue }
            val a = i - 1
            var b = i
            while (out[b] == null) b += 1
            val from = out[a]!!
            val to = out[b]!!
            for (j in a + 1 until b) out[j] = from + ((to - from) * (j - a)) / (b - a)
            i = b
        }
        return out.map { it!! }
    }

    /** The iteration progress (0..1, eased by the options' easing) at t ms, or null when the animation does not apply. */
    fun iterationProgress(o: TrackOptions, t: Double): Double? {
        val dur = o.duration
        val iterations = o.iterations
        val active = if (iterations == Double.POSITIVE_INFINITY) Double.POSITIVE_INFINITY else dur * iterations
        val local = t - o.delay
        val p: Double
        if (local < 0) {
            if (o.fill != "backwards" && o.fill != "both") return null
            p = 0.0
        } else if (local >= active) {
            if (o.fill != "forwards" && o.fill != "both") return null
            // Ended on a whole iteration: its end (1), else where it stopped.
            val whole = iterations - floor(iterations)
            p = if (iterations > 0 && whole == 0.0) 1.0 else whole
        } else {
            if (dur <= 0) return null
            p = (local % dur) / dur
        }
        return apply(easingOf(o.easing), p)
    }

    internal class Pt(val at: Double, val v: Map<String, Double>?, val e: List<Double>)
    internal class Group(val name: String, val pts: List<Pt>, val firstEase: List<Double>, val channels: List<String>)
    internal class Compiled(val groups: List<Group>)

    internal fun compile(track: Track): Compiled {
        val kfs = track.keyframes
        val offs = computedOffsets(kfs)
        val first = offs.indexOfFirst { it == 0.0 }
        val firstEase = if (first >= 0) easingOf(kfs[first]["easing"] as String?) else LINEAR
        val groups = mutableListOf<Group>()
        val list = listTransform(kfs)
        for (g in GROUPS) {
            val pts = mutableListOf<Pt>()
            for (i in kfs.indices) {
                val v = parse(kfs[i], g, list)
                if (v != null) pts.add(Pt(offs[i], v, easingOf(kfs[i]["easing"] as String?)))
            }
            if (pts.isNotEmpty()) groups.add(Group(g, pts, firstEase, if (g == "transform" && list) TRANSFORM_LIST else CHANNELS_OF.getValue(g)))
        }
        return Compiled(groups)
    }

    /** One track's values at t ms: only the channels it animates, or null when it does not apply. */
    fun trackValues(track: Track, t: Double, base: FxValues? = null): Map<String, Double>? {
        val b = base ?: FxValues.rest()
        val p = iterationProgress(track.options, t) ?: return null
        val c = track.compiled
        if (c.groups.isEmpty()) return null
        val out = HashMap<String, Double>()
        for (grp in c.groups) {
            val chs = grp.channels
            val parsed = grp.pts
            // A missing 0% or 100% keyframe is the element's own value (null here); a missing 0% eases with the 0% keyframe's easing.
            val pts = ArrayList<Pt>(parsed.size + 2)
            if (parsed.first().at != 0.0) pts.add(Pt(0.0, null, grp.firstEase))
            pts.addAll(parsed)
            if (parsed.last().at != 1.0) pts.add(Pt(1.0, null, LINEAR))
            // The interval: the last keyframe at or before p (and before 1), and the next.
            var s = -1
            for (i in pts.indices) if (pts[i].at <= p && pts[i].at < 1) s = i
            if (s < 0) for (i in pts.indices) if (pts[i].at == 0.0) s = i
            fun at(x: Pt, ch: String) = x.v?.get(ch) ?: b[ch]
            if (p >= 1 && pts.count { it.at == 1.0 } > 1) {
                for (ch in chs) out[ch] = at(pts.last(), ch)
            } else if (s < 0 || s >= pts.size - 1) {
                val x = pts[max(0, min(pts.size - 1, s))]
                for (ch in chs) out[ch] = at(x, ch)
            } else {
                val a = pts[s]
                val z = pts[s + 1]
                val span = z.at - a.at
                val local = if (span > 0) apply(a.e, (p - a.at) / span) else 1.0
                for (ch in chs) {
                    val va = at(a, ch)
                    val vb = at(z, ch)
                    out[ch] = va + (vb - va) * local
                }
            }
        }
        // The computed opacity is clamped (an overshooting easing goes past 1).
        out["o"]?.let { out["o"] = it.coerceIn(0.0, 1.0) }
        return out
    }

    /** An element's values at t ms under its animations, in order (the later wins), over its own `base`. */
    fun evaluate(tracks: List<Track>, t: Double, base: Map<String, Double>? = null): FxValues {
        val b = FxValues.rest(base)
        val out = b.copy()
        for (tr in tracks) trackValues(tr, t, b)?.forEach { (k, x) -> out[k] = x }
        return out
    }

    /** When a set of tracks is over (ms; infinity when one loops forever). */
    fun tracksEnd(tracks: List<Track>): Double =
        tracks.fold(0.0) { m, tr -> max(m, tr.options.delay + tr.options.duration * tr.options.iterations) }

    /** Every option, defaults filled in, infinity as -1 (the golden's form, normalised). */
    fun toJson(t: Track): Map<String, Any?> {
        val o = t.options
        val m = LinkedHashMap<String, Any?>()
        if (t.part != null) m["part"] = t.part
        m["keyframes"] = t.keyframes.map { k -> k.filterValues { it != null } }
        m["options"] = mapOf(
            "duration" to o.duration,
            "delay" to o.delay,
            "iterations" to if (o.iterations == Double.POSITIVE_INFINITY) -1.0 else o.iterations,
            "easing" to (o.easing ?: "linear"),
            "fill" to o.fill,
        )
        return m
    }
}
