package com.conferbot.sdk.ui.compose.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.MascotArt
import com.conferbot.sdk.core.customization.MascotMoods
import com.conferbot.sdk.ui.compose.FxSvg

/**
 * The "mascot" typing style (chatChrome.ts, polish.css .cb-typing--mascot;
 * RN TypingMascot.tsx): the bot's mascot, its head framed tight (32 x 32),
 * gently swaying about 50% 90% of its box, blinking and glancing at three
 * thought dots rising beside it in the bubble's text colour. Still with the
 * phone's animations off.
 */
@Composable
internal fun TypingMascot(look: MascotArt.Look, accent: String, ink: Color, reduced: Boolean) {
    val t = MascotMoods.Typing
    val n = t.SIZE.toFloat()
    val layers = rememberMascotLayers(accent, "down", look, bust = true)
    val on = !reduced
    val parts = remember(look) {
        val joints = MascotArt.origins(look)
        mapOf(
            "lid" to MascotPart(listOf(t.blink), joints.lid, mapOf("sy" to 0.0)) { (it % 3400).toDouble() },
            "look" to MascotPart(listOf(t.glance), joints.look) { (it % 2400).toDouble() },
        )
    }
    val sway = remember { MascotPart(listOf(t.sway), t.ORIGIN) { (it % 2400).toDouble() } }
    // The dots: one lap of 1.2s after the first (their 0.2s steps), looped.
    val dotLap = loopLap(1200.0, 1200.0)
    val time = rememberMascotTime(on, look)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(n.dp)) {
            val now = if (on) time.value else 0L
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                val save = c.save()
                c.scale(density, density)
                // The whole bust sways about its pivot (px in its own 32 box).
                if (on) MascotPaint.apply(c, sway.at(now), t.ORIGIN.first.toFloat(), t.ORIGIN.second.toFloat(), 1f, "rts")
                MascotPaint.draw(c, layers, n, true, if (on) parts else emptyMap(), now, false)
                c.restoreToCount(save)
            }
        }
        Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (d in t.dots) {
                Box(
                    Modifier
                        .padding(bottom = d.up.dp)
                        .size(d.d.dp)
                        .graphicsLayer {
                            alpha = if (on) Keyframes.evaluate(listOf(d.track), dotLap(time.value), mapOf("o" to 0.25)).o.toFloat() else 0.25f
                        }
                        .background(ink, CircleShape),
                )
            }
        }
    }
}

private val PENCIL_SVG = { accent: String, ink: String ->
    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-2 -6 20 20\"><path d=\"M0 0 L4 -4 L14 6 L10 10 Z\" fill=\"$accent\"/><path d=\"M0 0 L2.6 -2.6 L1.2 1.2 Z\" fill=\"$ink\"/><path d=\"M10 10 L14 6 L15.6 7.6 L11.6 11.6 Z\" fill=\"#f59e9e\"/></svg>"
}

/**
 * The "pencil" typing style (polish.css .cb-typing--pencil; RN TypingPencil):
 * a 44 x 16 line that writes itself in 2s (stroke-dashoffset 60 to 0 over
 * 75%, faded over the last 10%), the pencil in the launcher colour riding its
 * tip. Still with the phone's animations off (the line drawn, the pencil at
 * rest).
 */
@Composable
internal fun TypingPencil(accent: String, ink: Color, reduced: Boolean) {
    val p = MascotMoods.Pencil
    val line = remember { PathParser().parsePathString(p.LINE).toPath() }
    val inkHex = remember(ink) { String.format("#%06x", ink.toArgb() and 0xFFFFFF) }
    val pencil = remember(accent, inkHex) { PENCIL_SVG(accent, inkHex) }
    val time = rememberMascotTime(!reduced, Unit)
    Box(Modifier.size(44.dp, 16.dp)) {
        Canvas(Modifier.size(44.dp, 16.dp)) {
            val t = if (reduced) 0.0 else (time.value % p.PERIOD.toLong()).toDouble()
            val w = Keyframes.evaluate(listOf(p.write), t, mapOf("dash" to p.DASH))
            val dash = if (reduced) 0.0 else w.dash
            val o = if (reduced) 1f else w.o.toFloat()
            val k = size.width / 44f
            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(
                    line,
                    ink.copy(alpha = ink.alpha * o),
                    style = Stroke(
                        width = 1.8f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(p.DASH.toFloat(), p.DASH.toFloat()), dash.toFloat()),
                    ),
                )
            }
        }
        Canvas(Modifier.size(20.dp)) {
            val t = if (reduced) 0.0 else (time.value % p.PERIOD.toLong()).toDouble()
            val v = if (reduced) null else Keyframes.evaluate(listOf(p.pencil), t)
            val x = (v?.tx ?: 2.0).toFloat()
            val y = (v?.ty ?: 11.0).toFloat()
            // Its box sits at -2, -6 in the line's box; the pen's tip at its own 0, 0.
            translate((x - 2f).dp.toPx(), (y - 6f).dp.toPx()) {
                drawIntoCanvas { cv ->
                    val c = cv.nativeCanvas
                    val save = c.save()
                    c.scale(density, density)
                    FxSvg.draw(c, pencil, 20f, 20f)
                    c.restoreToCount(save)
                }
            }
        }
    }
}
