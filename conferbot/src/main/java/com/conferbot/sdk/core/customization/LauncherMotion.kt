package com.conferbot.sdk.core.customization

import com.google.gson.Gson
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The launcher's motion as the web widget plays it (conferbot-widget
 * src/svg/launcher/motion.ts and ts/launcher/launcherMotion.ts):
 *
 *   - which parts of an icon move, and how, at a moment of an "idle" or "msg"
 *     (new message) moment: iconFrame() evaluates the CSS keyframes, easing,
 *     delays, iterations and fill modes at time t;
 *   - when the moments play: nextIdleDelay();
 *   - the ripple: rippleRing().
 *
 * Pure: the Compose launcher drives the clock. The same algorithm as the
 * React Native SDK's core/launcherMotion.ts, which is checked frame by frame
 * against Chrome's own animation of the web icons.
 */
object LauncherMotion {
    /** How long a moment lasts (the longest keyframes are 2.4s). */
    const val MOMENT_MS = 2600L
    /** The first idle moment, after the launcher shows. */
    const val FIRST_IDLE_MS = 2000L
    /** An opening rests the idle moments this long. */
    const val OPENED_REST_MS = 30 * 60 * 1000L

    /** Milliseconds until the next idle moment after [played] of them, or null when there are no more. */
    fun nextIdleDelay(mode: String, played: Int, random: () -> Double = { Math.random() }): Long? {
        if (mode == "off") return null
        val (every, jitter, plays) = if (mode == "lively") Triple(20000L, 8000L, 10) else Triple(30000L, 0L, 4)
        if (played <= 0) return FIRST_IDLE_MS
        if (played >= plays) return null
        return every + (random() * jitter).roundToLong()
    }

    // The ripple (polish.css cb-ripple): two rings, the second 0.6s behind, each 1.8s, three times after 2.5s.
    const val RIPPLE_DELAY = 2.5
    const val RIPPLE_GAP = 0.6
    const val RIPPLE_DUR = 1.8
    const val RIPPLE_ITER = 3
    private val RIPPLE_EASE = listOf(0.0, 0.0, 0.2, 1.0)

    data class Ring(val scale: Double, val opacity: Double)

    /** A ring's scale and opacity at t seconds into the ripple (null when it is not showing). */
    fun rippleRing(t: Double, ring: Int, iterations: Int = RIPPLE_ITER, delay: Double = RIPPLE_DELAY): Ring? {
        val lt = t - delay - ring * RIPPLE_GAP
        if (lt < 0 || lt >= RIPPLE_DUR * iterations) return null
        val p = cubicBezier(RIPPLE_EASE, (lt % RIPPLE_DUR) / RIPPLE_DUR)
        return Ring(1 + 0.7 * p, 0.45 * (1 - p))
    }

    /** CSS cubic-bezier timing: the y for a given x (Newton, then bisection). */
    fun cubicBezier(e: List<Double>, x: Double): Double {
        val (x1, y1, x2, y2) = e
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        fun bx(t: Double) = 3 * x1 * t * (1 - t) * (1 - t) + 3 * x2 * t * t * (1 - t) + t * t * t
        fun by(t: Double) = 3 * y1 * t * (1 - t) * (1 - t) + 3 * y2 * t * t * (1 - t) + t * t * t
        var t = x
        for (i in 0 until 8) {
            val d = (bx(t + 1e-6) - bx(t)) / 1e-6
            if (abs(d) < 1e-6) break
            t -= (bx(t) - x) / d
        }
        if (t < 0 || t > 1 || abs(bx(t) - x) > 1e-4) {
            var lo = 0.0
            var hi = 1.0
            for (i in 0 until 40) {
                t = (lo + hi) / 2
                if (bx(t) < x) lo = t else hi = t
            }
        }
        return by(t)
    }

    // ---- data ----

    data class Frame(val at: Double, val tx: Any? = null, val ty: Any? = null, val sx: Double? = null, val sy: Double? = null, val r: Double? = null, val o: Double? = null)
    data class Rule(val part: String, val kf: String, val dur: Double, val delay: Double, val iter: Int, val ease: List<Double>, val fill: String)
    data class Family(val idle: List<Rule>, val msg: List<Rule>, val awakeOpacity: Map<String, Double>?)

    private data class Raw(
        val keyframes: Map<String, List<Map<String, Any?>>> = emptyMap(),
        val families: Map<String, Map<String, Any?>> = emptyMap(),
        val origins: Map<String, List<Double>> = emptyMap(),
        val delays: Map<String, Double> = emptyMap(),
    )

    private val raw: Raw by lazy { Gson().fromJson(LauncherMotionData.JSON, Raw::class.java) }

    private val keyframes: Map<String, List<Frame>> by lazy {
        raw.keyframes.mapValues { (_, list) ->
            list.map { f ->
                Frame(
                    at = (f["at"] as Number).toDouble(),
                    tx = f["tx"], ty = f["ty"],
                    sx = (f["sx"] as? Number)?.toDouble(), sy = (f["sy"] as? Number)?.toDouble(),
                    r = (f["r"] as? Number)?.toDouble(), o = (f["o"] as? Number)?.toDouble(),
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rules(v: Any?): List<Rule> = (v as? List<Map<String, Any?>>).orEmpty().map { r ->
        Rule(
            part = r["part"] as String,
            kf = r["kf"] as String,
            dur = (r["dur"] as Number).toDouble(),
            delay = (r["delay"] as Number).toDouble(),
            iter = (r["iter"] as Number).toInt(),
            ease = (r["ease"] as List<Number>).map { it.toDouble() },
            fill = r["fill"] as String,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val families: Map<String, Family> by lazy {
        raw.families.mapValues { (_, f) ->
            Family(rules(f["idle"]), rules(f["msg"]), (f["awakeOpacity"] as? Map<String, Number>)?.mapValues { it.value.toDouble() })
        }
    }

    // ---- evaluation ----

    /** 2x3 affine matrices [a, b, c, d, e, f]. */
    private val IDENTITY = listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

    fun mul(a: List<Double>, b: List<Double>) = listOf(
        a[0] * b[0] + a[2] * b[1],
        a[1] * b[0] + a[3] * b[1],
        a[0] * b[2] + a[2] * b[3],
        a[1] * b[2] + a[3] * b[3],
        a[0] * b[4] + a[2] * b[5] + a[4],
        a[1] * b[4] + a[3] * b[5] + a[5],
    )

    /** One op mid-moment: the transform to draw it with, its clip's, and its animated layers (outer first). */
    data class OpFrame(val m: List<Double>, val clipM: List<Double>?, val layers: List<Pair<Int, Double>>)

    private val BASE = mapOf("tx" to 0.0, "ty" to 0.0, "sx" to 1.0, "sy" to 1.0, "r" to 0.0, "o" to 1.0)

    private fun read(f: Frame, prop: String, dx: Double?, dy: Double?): Double? {
        val v: Any? = when (prop) { "tx" -> f.tx; "ty" -> f.ty; "sx" -> f.sx; "sy" -> f.sy; "r" -> f.r; else -> f.o }
        return when (v) {
            null -> null
            "dx" -> dx ?: 0.0
            "dy" -> dy ?: 0.0
            is Number -> v.toDouble()
            else -> null
        }
    }

    /** A property at progress p of one iteration: interpolated between the frames that set it, eased per segment. */
    private fun valueAt(frames: List<Frame>, prop: String, p: Double, ease: List<Double>, dx: Double?, dy: Double?): Double {
        val base = BASE.getValue(prop)
        val pts = frames.mapNotNull { f -> read(f, prop, dx, dy)?.let { f.at to it } }.toMutableList()
        if (pts.isEmpty()) return base
        if (pts.first().first > 0) pts.add(0, 0.0 to base)
        if (pts.last().first < 1) pts.add(1.0 to base)
        for (i in 0 until pts.size - 1) {
            val (aAt, aV) = pts[i]
            val (bAt, bV) = pts[i + 1]
            if (p >= aAt && p <= bAt) {
                val span = bAt - aAt
                val local = if (span > 0) cubicBezier(ease, (p - aAt) / span) else 1.0
                return aV + (bV - aV) * local
            }
        }
        return pts.last().second
    }

    private fun progress(rule: Rule, t: Double): Double? {
        val lt = t - rule.delay
        val back = rule.fill == "backwards" || rule.fill == "both"
        val fwd = rule.fill == "forwards" || rule.fill == "both"
        if (lt < 0) return if (back) 0.0 else null
        if (rule.iter != -1 && lt >= rule.dur * rule.iter) return if (fwd) 1.0 else null
        if (rule.dur <= 0) return null
        return (lt % rule.dur) / rule.dur
    }

    /** Each op of [icon] at t seconds into a [state] ("idle" or "msg") moment, or null for "at rest". */
    fun iconFrame(icon: LauncherIcon, state: String, t: Double): List<OpFrame?> {
        val fam = families[icon.anim] ?: return icon.ops.map { null }
        val rules = if (state == "msg") fam.msg else fam.idle
        val boxes = icon.boxes.orEmpty()
        val awakeOpacity = if (state == "msg") fam.awakeOpacity else null
        val cache = HashMap<Int, Pair<List<Double>?, Double?>?>()

        fun partFrame(classes: List<String>, gid: Int, dx: Double?, dy: Double?, unclassed: Boolean): Pair<List<Double>?, Double?>? {
            if (cache.containsKey(gid)) return cache[gid]
            var m: List<Double>? = null
            var o: Double? = null
            // The cascade gives an element one animation: the last rule that matches it.
            var rule = rules.lastOrNull { it.part in classes }
            if (rule != null) {
                // A delay set on one of the element's own classes wins.
                for (c in classes) {
                    val d = raw.delays["${icon.anim} $c"] ?: raw.delays["* $c"]
                    if (d != null) rule = rule!!.copy(delay = d)
                }
            }
            val p = rule?.let { progress(it, t) }
            if (rule != null && p != null) {
                // A frame using --dx / --dy on an element without them is "none".
                val frames = keyframes.getValue(rule.kf).map { f ->
                    if ((f.tx is String || f.ty is String) && (dx == null || dy == null)) f.copy(tx = 0.0, ty = 0.0, sx = 1.0, sy = 1.0, r = 0.0) else f
                }
                fun v(k: String) = valueAt(frames, k, p, rule.ease, dx, dy)
                if (frames.any { it.tx != null || it.ty != null || it.sx != null || it.sy != null || it.r != null }) {
                    val box = if (unclassed) listOf(0.0, 0.0, 0.0, 0.0) else boxes["$gid"] ?: listOf(0.0, 0.0, 24.0, 24.0)
                    val origin = raw.origins["${icon.anim} ${rule.part}"] ?: listOf(0.5, 0.5)
                    val ox = box[0] + box[2] * origin[0]
                    val oy = box[1] + box[3] * origin[1]
                    val rad = Math.toRadians(v("r"))
                    // translate(origin) translate(tx, ty) rotate(r) scale(sx, sy) translate(-origin)
                    var n = listOf(1.0, 0.0, 0.0, 1.0, ox + v("tx"), oy + v("ty"))
                    n = mul(n, listOf(cos(rad), sin(rad), -sin(rad), cos(rad), 0.0, 0.0))
                    n = mul(n, listOf(v("sx"), 0.0, 0.0, v("sy"), 0.0, 0.0))
                    n = mul(n, listOf(1.0, 0.0, 0.0, 1.0, -ox, -oy))
                    m = n
                }
                // CSS clamps opacity (an overshooting easing goes past 1).
                if (frames.any { it.o != null }) o = min(1.0, max(0.0, v("o")))
            }
            if (o == null && awakeOpacity != null) for (c in classes) awakeOpacity[c]?.let { o = it }
            val out = if (m != null || o != null) m to o else null
            cache[gid] = out
            return out
        }

        return icon.ops.map { op ->
            val chain = listOf(IconPart(c = listOf("cbl-r"), g = 0)) + op.parts.orEmpty()
            var m = IDENTITY
            var clipM: List<Double>? = null
            val layers = ArrayList<Pair<Int, Double>>()
            var moved = false
            chain.forEachIndexed { i, part ->
                // The clip moves with the root and the parts down to the element that declared it.
                if (op.clip != null && i == (op.clipDepth ?: 0) + 1) clipM = m
                part.pre?.let { m = mul(m, it) }
                val f = partFrame(part.c, part.g, part.dx, part.dy, part.u == 1)
                if (f != null) moved = true
                // A CSS transform replaces the element's own transform attribute while it runs.
                m = mul(m, f?.first ?: part.t ?: IDENTITY)
                f?.second?.let { layers.add(part.g to it) }
            }
            if (!moved) return@map null
            if (op.clip != null && clipM == null) clipM = m
            op.post?.let { m = mul(m, it) }
            OpFrame(m, if (op.clip != null) clipM else null, layers)
        }
    }
}
