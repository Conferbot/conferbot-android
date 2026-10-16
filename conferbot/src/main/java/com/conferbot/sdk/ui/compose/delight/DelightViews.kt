package com.conferbot.sdk.ui.compose.delight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.delight.Delight
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.ui.compose.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/*
 * The conversation's human touches, drawn (core/delight; RN
 * components/Delight/Delight.tsx; web ts/chat/delight/ and
 * css/nodes/17-delight.css, 06-rating-fx.css):
 *
 *   ReactionBadge    the bot's tapback on the corner of the visitor's
 *                    answer facing the bot, popping in (cb-react-pop)
 *   ConfettiBurst    a second of confetti in the bot's colours from the
 *                    answer (cb-confetti-fly), transform and opacity only
 *   RatingSparks     the rating's sparks on a pick (cbn-spark)
 *   StallHint        "No rush - ..." under a question that has waited
 *   ChangeAnswerLink "Edit" under the visitor's latest answer
 *
 * Nothing moves with the animator duration scale at 0; the confetti and the
 * sparks are not drawn at all then. No vibration.
 */

// The web's easings (css/nodes/01-base.css --cbn-spring, --cbn-ease).
private val SPRING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val EASE = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Piecewise-linear interpolation of [v] over the stops (RN Animated interpolate). */
private fun lerpStops(v: Float, inputs: FloatArray, outputs: FloatArray): Float {
    if (v <= inputs.first()) return outputs.first()
    for (i in 1 until inputs.size) {
        if (v <= inputs[i]) {
            val f = (v - inputs[i - 1]) / (inputs[i] - inputs[i - 1])
            return outputs[i - 1] + (outputs[i] - outputs[i - 1]) * f
        }
    }
    return outputs.last()
}

/** The tapback: a 26dp round badge, start corner, 14dp below the bubble. */
@Composable
fun BoxScope.ReactionBadge(emoji: String, card: Color, ring: Color) {
    val reduced = rememberReducedMotion()
    val pop = remember(emoji) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(emoji, reduced) {
        if (reduced) pop.snapTo(1f) else pop.animateTo(1f, tween(460, easing = SPRING))
    }
    val label = Delight.reactionLabel(emoji)
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .offset(x = (-8).dp, y = 14.dp)
            .size(26.dp)
            .graphicsLayer {
                val v = pop.value
                val sc = lerpStops(v, floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0.2f, 1.18f, 1f))
                scaleX = sc
                scaleY = sc
                rotationZ = lerpStops(v, floatArrayOf(0f, 0.6f, 1f), floatArrayOf(-24f, 6f, 0f))
                alpha = lerpStops(v, floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0f, 1f, 1f)).coerceIn(0f, 1f)
            }
            .shadow(2.dp, CircleShape, clip = false)
            .background(card, CircleShape)
            .border(2.dp, ring, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp))
    }
}

/**
 * The burst, from the bubble's middle: each piece flies out to (dx, dy) by
 * 55% of 1.05 s, then on and down by its fall while it fades. Colours: the
 * bot's accent, the star gold, the accent lightened, a mint.
 */
@Composable
fun BoxScope.ConfettiBurst(key: Any, accent: Color, onDone: () -> Unit) {
    val reduced = rememberReducedMotion()
    if (reduced) {
        LaunchedEffect(key) { onDone() }
        return
    }
    val pieces = remember(key) { Delight.confettiPieces() }
    val progress = remember(key) { pieces.map { Animatable(0f) } }
    LaunchedEffect(key) {
        pieces.forEachIndexed { i, p ->
            launch {
                delay(p.delay.toLong())
                progress[i].animateTo(1f, tween(1050, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)))
            }
        }
        delay(Delight.CONFETTI_MS)
        onDone()
    }
    val light = Color(0xFF000000.toInt() or Delight.mixWithWhite(accent.toArgb() and 0xFFFFFF, 0.45))
    val tones = listOf(accent, Color(0xFFF5A524), light, Color(0xFF34D399))
    Canvas(Modifier.align(Alignment.Center).size(1.dp)) {
        val px = density
        pieces.forEachIndexed { i, p ->
            val v = progress[i].value
            if (v <= 0f) return@forEachIndexed
            val stops = floatArrayOf(0f, 0.55f, 1f)
            val tx = lerpStops(v, stops, floatArrayOf(0f, p.dx.toFloat(), p.dx * 1.15f)) * px
            val ty = lerpStops(v, stops, floatArrayOf(0f, p.dy.toFloat(), (p.dy + p.fall).toFloat())) * px
            val rot = lerpStops(v, stops, floatArrayOf(0f, p.rot * 0.6f, p.rot.toFloat()))
            val sc = lerpStops(v, stops, floatArrayOf(0.6f, 1f, 0.9f))
            val op = lerpStops(v, floatArrayOf(0f, 0.01f, 0.55f, 1f), floatArrayOf(0f, 1f, 1f, 0f)).coerceIn(0f, 1f)
            val w = 6f * px * sc
            val h = (if (p.round) 6f else 9f) * px * sc
            val c = Offset(center.x + tx, center.y + ty)
            withTransform({ rotate(rot, c) }) {
                drawRoundRect(
                    color = tones[p.tone].copy(alpha = op),
                    topLeft = Offset(c.x - w / 2, c.y - h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(if (p.round) w / 2 else 1.5f * px * sc)
                )
            }
        }
    }
}

/** Sparks round the picked star or face: out from its edge, 12 to 36dp, fading. */
@Composable
fun BoxScope.RatingSparks(key: Any, top: Boolean, color: Color) {
    val reduced = rememberReducedMotion()
    if (reduced) return
    val angles = remember(top) { Delight.ratingSparks(top) }
    val progress = remember(key) { angles.map { Animatable(0f) } }
    LaunchedEffect(key) {
        angles.indices.forEach { i ->
            launch {
                delay(80)
                progress[i].animateTo(1f, tween(if (i % 2 == 1) 600 else 750, easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)))
            }
        }
    }
    Canvas(Modifier.align(Alignment.Center).size(1.dp)) {
        val px = density
        angles.forEachIndexed { i, a ->
            val v = progress[i].value
            if (v <= 0f) return@forEachIndexed
            val size = (if (i % 2 == 1) 4f else 6f) * px
            val dist = lerpStops(v, floatArrayOf(0f, 1f), floatArrayOf(12f, 36f)) * px
            val sc = lerpStops(v, floatArrayOf(0f, 1f), floatArrayOf(0.4f, 0.3f))
            val op = lerpStops(v, floatArrayOf(0f, 0.15f, 0.65f, 1f), floatArrayOf(0f, 1f, 1f, 0f)).coerceIn(0f, 1f)
            // rotate(a) then translateY(-dist): the point at angle a from straight up.
            val rad = Math.toRadians(a.toDouble())
            val c = Offset(center.x + (sin(rad) * dist).toFloat(), center.y - (cos(rad) * dist).toFloat())
            drawCircle(color.copy(alpha = op), radius = size / 2 * sc, center = c)
        }
    }
}

/** [RatingSparks] in a box of their own, centred on what holds them. */
@Composable
fun RatingSparksAt(key: Any, top: Boolean, color: Color) {
    Box(Modifier.size(1.dp)) { RatingSparks(key, top, color) }
}

/** "No rush - ...": a soft line under the question, a breathing dot, rising in. */
@Composable
fun StallHint(text: String, accent: Color, muted: Color, indent: androidx.compose.ui.unit.Dp = 0.dp, fontFamily: FontFamily? = null) {
    val reduced = rememberReducedMotion()
    val enter = remember(text) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(text, reduced) { if (!reduced) enter.animateTo(1f, tween(400, easing = EASE)) }
    val breathe = if (reduced) null else rememberInfiniteTransition(label = "stall").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "breathe"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 8.dp, bottom = 4.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1 - enter.value) * 4.dp.toPx()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer {
                    val b = breathe?.value ?: 1f
                    alpha = if (breathe == null) 1f else 0.35f + 0.65f * b
                    val sc = if (breathe == null) 1f else 0.85f + 0.25f * b
                    scaleX = sc
                    scaleY = sc
                }
                .background(accent, CircleShape)
        )
        Text(text, color = muted, style = TextStyle(fontSize = 12.5.sp, fontFamily = fontFamily))
    }
}

/** "Edit": a small muted link right under the latest answer, at its edge. */
@Composable
fun ChangeAnswerLink(onClick: () -> Unit, muted: Color, fontFamily: FontFamily? = null) {
    val reduced = rememberReducedMotion()
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { if (!reduced) enter.animateTo(1f, tween(300, easing = EASE)) }
    val label = I18n.t("chat.changeAnswer")
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, end = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier
                .alpha(0.85f * enter.value)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✎", color = muted, style = TextStyle(fontSize = 11.sp))
            Spacer(Modifier.width(3.dp))
            Text(I18n.t("chat.edit"), color = muted, style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamily))
        }
    }
}
