package com.conferbot.sdk.ui.compose.mascot

import com.conferbot.sdk.core.i18n.I18n
import android.graphics.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.customization.FxValues
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.MascotArt
import com.conferbot.sdk.core.customization.MascotMoods
import com.conferbot.sdk.core.customization.Track
import com.conferbot.sdk.ui.compose.FxSvg
import com.conferbot.sdk.ui.compose.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One moving part of the mascot: its tracks, its joint (in the drawing's
 * units: [MascotArt.origins]), its own value at rest (the lids open:
 * scaleY 0, as drawn on the web) and the clock it runs on ([lap]: a time in
 * ms to the time its tracks are read at).
 */
internal class MascotPart(
    val tracks: List<Track>,
    val pivot: Pair<Double, Double>,
    val base: Map<String, Double>? = null,
    val lap: (Long) -> Double = { it.toDouble() },
) {
    /** The order its transform list draws in (Keyframes.transformOrder). */
    val order: String = if (tracks.isEmpty()) "trs" else Keyframes.transformOrder(tracks[0])

    fun at(t: Long): FxValues = Keyframes.evaluate(tracks, lap(t), base)
}

/** A clock that plays to `settle + period`, then loops its last `period` (the RN FxClock loop). */
internal fun loopLap(settle: Double, period: Double): (Long) -> Double = { t ->
    val x = t.toDouble()
    if (period <= 0 || x < settle + period) x else settle + (x - settle) % period
}

/**
 * The mascot drawn (the React Native SDK's MascotFigure.tsx): its layers
 * stacked back to front in a size x size box on the platform canvas, each
 * moving part drawn in its own layer, turned about its joint in the order
 * its CSS transform list names; no SVG filters, as in RN. Without a part's
 * motion it stands as drawn, eyes open and the usual smile; [grin] puts the
 * big smile on (the happy mood).
 */
internal object MascotPaint {
    private fun alpha(o: Double) = (o * 255).roundToInt().coerceIn(0, 255)

    /** Moves the canvas as `v` moves a part about `pivot` (px in the box); translations in drawing units (`k` px each). */
    fun apply(c: Canvas, v: FxValues, px: Float, py: Float, k: Float, order: String) {
        c.translate(px, py)
        for (op in order) {
            when (op) {
                't' -> if (v.tx != 0.0 || v.ty != 0.0) c.translate((v.tx * k).toFloat(), (v.ty * k).toFloat())
                'r' -> if (v.r != 0.0) c.rotate(v.r.toFloat())
                's' -> if (v.sx != 1.0 || v.sy != 1.0) c.scale(v.sx.toFloat(), v.sy.toFloat())
            }
        }
        c.translate(-px, -py)
    }

    /**
     * Draws [layers] in a [size] x [size] box at the canvas origin at time
     * [t] (ms), [parts] by name: body, arm, look, lid, brow, shadow, smile,
     * o, grin.
     */
    fun draw(c: Canvas, layers: MascotArt.Layers, size: Float, bust: Boolean, parts: Map<String, MascotPart>, t: Long, grin: Boolean) {
        val k = MascotArt.unit(size.toDouble(), bust).toFloat()
        fun layer(svg: String?, name: String? = null, restAlpha: Double = 1.0, drawAtRest: Boolean = true) {
            if (svg == null) return
            val part = name?.let { parts[it] }
            if (part == null && !drawAtRest) return
            val s = c.save()
            var o = restAlpha
            if (part != null) {
                val v = part.at(t)
                if (v.sx == 0.0 || v.sy == 0.0 || v.o <= 0.002) {
                    c.restoreToCount(s)
                    return
                }
                val (px, py) = MascotArt.point(part.pivot, size.toDouble(), bust)
                apply(c, v, px.toFloat(), py.toFloat(), k, part.order)
                o = v.o
            }
            if (o <= 0.002) {
                c.restoreToCount(s)
                return
            }
            if (o < 0.998) c.saveLayerAlpha(null, alpha(o))
            FxSvg.draw(c, svg, size, size)
            c.restoreToCount(s)
        }
        layer(layers.shadow, "shadow")
        val s = c.save()
        parts["body"]?.let { b ->
            val v = b.at(t)
            val (px, py) = MascotArt.point(b.pivot, size.toDouble(), bust)
            apply(c, v, px.toFloat(), py.toFloat(), k, b.order)
            if (v.o < 0.998) c.saveLayerAlpha(null, alpha(v.o))
        }
        layer(layers.tail)
        layer(layers.armBack, "arm")
        layer(layers.body)
        layer(layers.look, "look")
        layer(layers.face)
        // The blinking lids rest open (scaleY 0 on the web): not drawn until a blink moves them.
        layer(layers.lids, "lid", drawAtRest = false)
        layer(layers.cover)
        layer(layers.brows, "brow")
        layer(layers.armFront, "arm")
        layer(layers.smile, "smile", restAlpha = if (grin) 0.0 else 1.0)
        layer(layers.o, "o", drawAtRest = false)
        layer(layers.grin, "grin", drawAtRest = grin)
        layer(layers.top)
        c.restoreToCount(s)
    }

    /**
     * How long from [t] nothing a part draws changes (ms, up to [horizon]),
     * looking ahead a frame at a time: the clock can sleep through a still
     * stretch (between blinks) instead of drawing the same frame.
     */
    fun stillFor(parts: Collection<MascotPart>, t: Long, horizon: Long = 1500): Long {
        if (parts.isEmpty()) return horizon
        val now = parts.map { it.at(t) }
        var dt = 16L
        while (dt <= horizon) {
            val next = parts.map { it.at(t + dt) }
            for (i in now.indices) {
                val a = now[i]
                val b = next[i]
                if (abs(a.tx - b.tx) > 1e-3 || abs(a.ty - b.ty) > 1e-3 || abs(a.r - b.r) > 1e-3 || abs(a.sx - b.sx) > 1e-4 || abs(a.sy - b.sy) > 1e-4 || abs(a.o - b.o) > 1e-3) return dt - 16
            }
            dt += 16
        }
        return horizon
    }
}

/**
 * Milliseconds since [key] last changed, on the frame clock, while
 * [running]; read it only in a draw lambda (redraws, no recomposition).
 * [still] says how long nothing will change from a time: the clock sleeps
 * through it.
 */
@Composable
internal fun rememberMascotTime(running: Boolean, key: Any?, still: ((Long) -> Long)? = null): State<Long> {
    val time = remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(running, key) {
        if (!running) return@LaunchedEffect
        val t0 = withFrameMillis { it } - time.longValue
        while (true) {
            val now = withFrameMillis { it } - t0
            time.longValue = now
            val quiet = still?.invoke(now) ?: 0L
            if (quiet > 48) delay(quiet - 24)
        }
    }
    return time
}

/** The layers for a look, worked out once per look, pose, colour and framing. */
@Composable
internal fun rememberMascotLayers(accent: String, arm: String, look: MascotArt.Look?, bust: Boolean = false): MascotArt.Layers =
    remember(accent, arm, look, bust) { MascotArt.layers(accent, arm, look, bust) }

/** A mood's parts about the look's joints (RN useMascotMood): lid, arm, body, look, brow, shadow. */
internal fun moodParts(plan: MascotMoods.MoodPlan, look: MascotArt.Look?): Map<String, MascotPart> {
    val o = MascotArt.origins(look)
    val lap = loopLap(plan.settle, plan.period)
    val pivots = mapOf(
        "body" to o.body, "arm" to o.arm, "lid" to o.lid, "look" to o.look, "brow" to o.brow,
        "shadow" to (50.0 to if (look?.shape == "ghost") 99.0 else 95.0),
    )
    return plan.parts.filterValues { it.isNotEmpty() }.mapValues { (part, tracks) ->
        MascotPart(tracks, pivots.getValue(part), if (part == "lid") mapOf("sy" to 0.0) else null, lap)
    }
}

/**
 * The mascot in a mood ([MascotMoods]: blink, wave, think, happy), drawn in
 * a [size] dp box: one clock plays the mood's finite part, then loops a lap
 * of what goes on (the blink). [reduced]: still, the happy smile kept.
 */
@Composable
internal fun MascotMoodFigure(
    layers: MascotArt.Layers,
    look: MascotArt.Look?,
    size: Float,
    mood: String,
    arm: String? = null,
    bust: Boolean = false,
    waveCount: Double? = null,
    reduced: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val plan = remember(mood, arm, bust, waveCount, reduced) { MascotMoods.moodPlan(mood, arm, bust, waveCount, reduced) }
    val parts = remember(plan, look) { moodParts(plan, look) }
    val moving = enabled && parts.isNotEmpty()
    val time = rememberMascotTime(moving, plan) { t -> MascotPaint.stillFor(parts.values, t) }
    Canvas(modifier.size(size.dp)) {
        val t = if (moving) time.value else 0L
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            val save = c.save()
            c.scale(density, density)
            MascotPaint.draw(c, layers, size, bust, if (moving) parts else emptyMap(), t, plan.grin)
            c.restoreToCount(save)
        }
    }
}

/**
 * The owner's mascot (web art3d.ts mascot()), for an app's own screens
 * (the home screen's mascot slot, say) in one of its moods: idle (blinks),
 * wave, think or happy. [look] is `Conferbot.customizations.value.mascot`
 * or `HomeModel.mascot.look`; [accent] the colour it is drawn in when the
 * owner chose none (`mascotAccent`, the launcher's). [bust]: head and body
 * only, framed tight. Still with the phone's animations off.
 */
@Composable
fun Mascot(
    look: MascotArt.Look,
    accent: String,
    modifier: Modifier = Modifier,
    size: Float = 64f,
    mood: String = "idle",
    arm: String = if (mood == "wave") "wave" else "down",
    bust: Boolean = false,
    waveCount: Double? = null,
) {
    val reduced = rememberReducedMotion()
    val layers = rememberMascotLayers(accent, arm, look, bust)
    val name = look.name.ifEmpty { MascotArt.DEFAULT_NAMES[look.shape] ?: I18n.t("sdk.mascot.name") }
    MascotMoodFigure(
        layers = layers,
        look = look,
        size = size,
        mood = mood,
        arm = arm,
        bust = bust,
        waveCount = waveCount,
        reduced = reduced,
        modifier = modifier.semantics { contentDescription = name },
    )
}
