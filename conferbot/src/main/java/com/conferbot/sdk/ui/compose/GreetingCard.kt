package com.conferbot.sdk.ui.compose

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The greeting card by the closed launcher, dressed as the web widget dresses
 * it (conferbot-widget css/polish-home.css .cb-greet and .cb-greet--<style>,
 * ts/home/firstImpression.ts, ts/home/greetingStyle.ts): the plain card, a
 * handwritten note, a phone text (the default), a postcard, a sticky note, a
 * typewriter, glass, a push notification, neon, a voice note or an aurora
 * edge, each with its reveal. Fonts are the platform's nearest ones; glass
 * and the notification are translucent without a live blur.
 */

private val WIDTH = mapOf(
    "card" to 296, "note" to 296, "imessage" to 270, "postcard" to 296, "sticky" to 250, "typewriter" to 296,
    "glass" to 296, "notification" to 300, "neon" to 296, "voice" to 280, "aurora" to 296,
)

private val VOICE_BARS = listOf(30, 93, 41, 44, 38, 65, 74, 73, 84, 47, 44, 32, 74, 37, 98, 52, 67, 38, 42, 65, 64, 94)

private const val CLOSE_D = "m7.314 5.9l3.535-3.536A1 1 0 1 0 9.435.95L5.899 4.485L2.364.95A1 1 0 1 0 .95 2.364l3.535 3.535L.95 9.435a1 1 0 1 0 1.414 1.414l3.535-3.535l3.536 3.535a1 1 0 1 0 1.414-1.414L7.314 5.899z"
private const val TAIL_D = "M0 0 C 2 9 7 14 16 16 C 8 16 3 14 0 11 Z"
private const val PLAY_D = "M8 5.5v13l10.5-6.5z"
private const val CURL_D = "M0 0H22V4A18 18 0 0 1 4 22H0Z"

private val paths = mutableMapOf<String, Path>()
private fun svgPath(d: String): Path = paths.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private object GreetFonts {
    val note = FontFamily.Cursive
    val sticky = FontFamily.Cursive
    val serif = FontFamily.Serif
    val mono = FontFamily.Monospace
}

private fun hexColor(v: String, fallback: Color = Color.Black): Color = lookColor(v) ?: fallback

/** A value along piecewise-linear stops. */
private fun stops(t: Float, xs: List<Float>, ys: List<Float>): Float {
    if (t <= xs.first()) return ys.first()
    for (i in 1 until xs.size) {
        if (t <= xs[i]) {
            val span = xs[i] - xs[i - 1]
            val k = if (span <= 0f) 1f else (t - xs[i - 1]) / span
            return ys[i - 1] + (ys[i] - ys[i - 1]) * k
        }
    }
    return ys.last()
}

private fun easeInOut(t: Float) = CubicBezierEasing(0.42f, 0f, 0.58f, 1f).transform(t)

/**
 * The greeting card. [onPress] for a tap on the card, [onClose] for its X,
 * [onStarter] for a suggested question.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GreetingCard(
    data: GreetingCardData,
    onPress: () -> Unit,
    onClose: () -> Unit,
    onStarter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduced = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val style = data.style
    val accent = hexColor(data.accent, Color(0xFF4F46E5))
    val width = (WIDTH[style] ?: 296).dp

    // ---- Entrance (cb-greet-spring 620ms; notification slides down; neon flickers) ----
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        if (style == "neon") {
            // Hard steps: 0, 1, .3, 1, .55, 1 at 0/90/144/216/270/342ms.
            for ((d, v) in listOf(90L to 1f, 54L to 0.3f, 72L to 1f, 54L to 0.55f, 72L to 1f)) {
                delay(d)
                enter.snapTo(v)
            }
        } else {
            enter.animateTo(
                1f,
                tween(
                    if (style == "notification") 500 else 620,
                    easing = if (style == "notification") CubicBezierEasing(0.2f, 1.2f, 0.3f, 1f) else CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f),
                ),
            )
        }
    }
    val e = enter.value
    val enterAlpha = if (style == "neon") e else stops(e, listOf(0f, 0.55f, 1f), listOf(0f, 1f, 1f))
    val enterY = if (style == "neon") 0f else stops(e, listOf(0f, 0.55f, 0.78f, 1f), if (style == "notification") listOf(-14f, -1f, 0f, 0f) else listOf(14f, -3f, 1f, 0f))
    val enterScale = if (style == "neon") 1f else stops(e, listOf(0f, 0.55f, 0.78f, 1f), if (style == "notification") listOf(0.96f, 1.005f, 1f, 1f) else listOf(0.94f, 1.01f, 0.998f, 1f))
    val density = LocalDensity.current

    // ---- imessage: typing dots 1.1s, the bubble pops at 1.05s, "Delivered" and chips at 1.45s ----
    val reveal = style == "imessage" && data.firstShow && !reduced
    val dotsIn = remember { Animatable(0f) }
    val pop = remember { Animatable(if (reveal) 0f else 1f) }
    val late = remember { Animatable(if (reveal) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!reveal) return@LaunchedEffect
        launch { dotsIn.animateTo(1f, tween(1100, easing = LinearEasing)) }
        launch { pop.animateTo(1f, tween(420, delayMillis = 1050, easing = CubicBezierEasing(0.2f, 1.3f, 0.4f, 1f))) }
        launch { late.animateTo(1f, tween(300, delayMillis = 1450)) }
    }

    // ---- typewriter: typed from 260ms, 34ms + up to 30ms a character, a pause after punctuation ----
    var typed by remember { mutableStateOf(if (style == "typewriter" && !reduced) "" else data.text) }
    LaunchedEffect(data.text) {
        if (style != "typewriter" || reduced) return@LaunchedEffect
        delay(260)
        for (i in 1..data.text.length) {
            typed = data.text.substring(0, i)
            if (i >= data.text.length) break
            delay(if (data.text[i - 1] in ".,!?") 204L else (34 + Math.random() * 30).toLong())
        }
    }

    // ---- voice: bars squash to 35% and back, twice, 60ms apart ----
    val voiceClock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (style != "voice" || reduced) return@LaunchedEffect
        val total = (VOICE_BARS.size - 1) * 60 + 2400
        voiceClock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }

    // Loops: the aurora's edge turns every 6s, the caret blinks, the dots bob.
    val loops = rememberInfiniteTransition(label = "greeting")
    val spin by loops.animateFloat(0f, if (style == "aurora" && !reduced) 360f else 0f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    val blink by loops.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val bob by loops.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "bob")

    val cardFont: FontFamily? = null

    // ======== the shared pieces ========

    @Composable
    fun BoxScope.Close(top: Float, right: Float, color: Color, circle: Color? = null, size: Float = 26f, glyph: Float = 20f) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-right).dp, y = top.dp)
                .size(size.dp)
                .clip(if (circle != null) CircleShape else RoundedCornerShape(8.dp))
                .then(if (circle != null) Modifier.background(circle) else Modifier)
                .semantics { contentDescription = "Dismiss" }
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(glyph.dp)) {
                val k = this.size.width / 24f
                translate(6 * k, 6 * k) {
                    drawContext.canvas.save()
                    drawContext.canvas.scale(k, k)
                    drawPath(svgPath(CLOSE_D), color)
                    drawContext.canvas.restore()
                }
            }
        }
    }

    @Composable
    fun Head(
        name: TextStyle = TextStyle(),
        status: TextStyle = TextStyle(),
        avatar: Float = 32f,
        avatarRadius: Float? = null,
        showAvatar: Boolean = true,
        statusText: String? = null,
        dot: Boolean = true,
        row: Boolean = false,
    ) {
        val baseName = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontFamily = cardFont).merge(name)
        val baseStatus = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B), fontFamily = cardFont).merge(status)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(end = if (style == "postcard") 62.dp else 22.dp),
        ) {
            if (showAvatar && data.head.avatar != null) {
                coil.compose.AsyncImage(
                    model = data.head.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatar.dp)
                        .clip(RoundedCornerShape((avatarRadius ?: (avatar / 2)).dp))
                        .background(Color(0xFFEEF2F7)),
                )
            }
            val showStatus = statusText != null || data.head.online
            if (row) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(data.head.name, style = baseName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (showStatus) {
                        Box(Modifier.weight(1f))
                        Text(statusText ?: "Online", style = baseStatus, modifier = Modifier.padding(end = 18.dp))
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(data.head.name, style = baseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showStatus) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (dot && statusText == null) Box(Modifier.size(7.dp).background(Color(0xFF22C55E), CircleShape))
                            Text(statusText ?: "Online", style = baseStatus)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Message(s: TextStyle = TextStyle(), lines: Int = 3, modifier: Modifier = Modifier) {
        val base = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.3.sp, color = Color(0xFF1E293B), fontFamily = cardFont).merge(s)
        val caret = style == "typewriter" && !reduced
        val shown = if (style == "typewriter") typed else data.text
        Text(
            buildAnnotatedString {
                append(shown)
                if (caret) withStyle(SpanStyle(color = if (blink < 0.5f) accent else Color.Transparent)) { append("▌") }
            },
            style = base,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }

    @Composable
    fun Chips(chip: Modifier, shape: Shape, text: TextStyle, end: Boolean = false, alpha: Float = 1f) {
        if (data.starters.isEmpty()) return
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, if (end) Alignment.End else Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).graphicsLayer { this.alpha = alpha },
        ) {
            data.starters.forEach { s ->
                Box(
                    Modifier
                        .then(chip)
                        .clip(shape)
                        .clickable { onStarter(s) }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                ) {
                    Text(
                        s,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, fontFamily = cardFont).merge(text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val pill = RoundedCornerShape(999.dp)
    @Composable
    fun BaseChips(radius: Dp? = null, family: FontFamily? = cardFont) {
        val shape = if (radius != null) RoundedCornerShape(radius) else pill
        Chips(
            Modifier.background(accent.copy(alpha = 0.07f), shape).border(1.dp, accent.copy(alpha = 0.22f), shape),
            shape,
            TextStyle(color = accent, fontFamily = family),
        )
    }

    // A column of the card's parts in its padding.
    @Composable
    fun Parts(top: Dp = 14.dp, h: Dp = 16.dp, bottom: Dp = 14.dp, gap: Dp = 8.dp, content: @Composable () -> Unit) {
        Column(Modifier.padding(start = h, end = h, top = top, bottom = bottom), verticalArrangement = Arrangement.spacedBy(gap)) { content() }
    }

    val tap = Modifier
        .semantics { contentDescription = data.text }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onPress)

    Box(
        modifier
            .width(width)
            .graphicsLayer {
                alpha = enterAlpha
                translationY = with(density) { enterY.dp.toPx() }
                scaleX = enterScale
                scaleY = enterScale
            },
    ) {
        when (style) {
            "imessage" -> {
                val bubbleShape = RoundedCornerShape(19.dp)
                val fg = hexColor(data.bubble.fg, Color.White)
                val bg: Modifier = if (data.bubble.bg.size >= 2) {
                    Modifier.background(Brush.linearGradient(listOf(hexColor(data.bubble.bg[0]), hexColor(data.bubble.bg[1])), Offset.Zero, Offset.Infinite), bubbleShape)
                } else Modifier.background(hexColor(data.bubble.bg.firstOrNull() ?: "#1b55f3"), bubbleShape)
                val tail = hexColor(data.bubble.tail)
                Box(tap) {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Box(
                                Modifier
                                    .widthIn(max = width * 0.88f)
                                    .graphicsLayer {
                                        alpha = pop.value.coerceIn(0f, 1f)
                                        val s = 0.4f + 0.6f * pop.value
                                        scaleX = s
                                        scaleY = s
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                                    },
                            ) {
                                Canvas(Modifier.size(16.dp).align(Alignment.BottomEnd).offset(x = 5.dp)) { drawPath(svgPath(TAIL_D), tail) }
                                Box(
                                    Modifier
                                        .shadow(6.dp, bubbleShape, clip = false, ambientColor = Color(0x4D0F172A), spotColor = Color(0x4D0F172A))
                                        .then(bg)
                                        .padding(vertical = 9.dp, horizontal = 14.dp),
                                ) {
                                    Text(data.text, color = fg, fontSize = 15.sp, lineHeight = 20.sp, fontFamily = cardFont, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (reveal && dotsIn.value < 1f) {
                                val t = dotsIn.value
                                Box(
                                    Modifier.graphicsLayer {
                                        alpha = stops(t, listOf(0f, 0.15f, 0.8f, 1f), listOf(0f, 1f, 1f, 0f))
                                        val s = stops(t, listOf(0f, 0.15f, 0.8f, 1f), listOf(0.6f, 1f, 1f, 1.08f))
                                        scaleX = s
                                        scaleY = s
                                    },
                                ) {
                                    Canvas(Modifier.size(16.dp).align(Alignment.BottomEnd).offset(x = 5.dp)) { drawPath(svgPath(TAIL_D), Color(0xFFE9E9EB)) }
                                    Row(
                                        Modifier.background(Color(0xFFE9E9EB), bubbleShape).padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        for (k in 0..2) {
                                            val xs = listOf(0f, k * 0.15f, 0.3f + k * 0.15f, 0.6f + k * 0.15f, 1f).map { minOf(1f, it) }
                                            val o = stops(bob, xs, listOf(0.35f, 0.35f, 1f, 0.35f, 0.35f))
                                            val y = stops(bob, xs, listOf(0f, 0f, -3f, 0f, 0f))
                                            Box(Modifier.offset(y = y.dp).size(7.dp).graphicsLayer { alpha = o }.background(Color(0xFF8E8E93), CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "Delivered",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.padding(end = 6.dp).offset(y = (-2).dp).graphicsLayer { alpha = late.value },
                        )
                        val chipShape = RoundedCornerShape(17.dp)
                        Chips(
                            Modifier
                                .shadow(2.dp, chipShape, clip = false, ambientColor = Color(0x400F172A), spotColor = Color(0x400F172A))
                                .background(Color.White.copy(alpha = 0.94f), chipShape)
                                .border(1.dp, accent.copy(alpha = 0.35f), chipShape),
                            chipShape,
                            TextStyle(color = accent),
                            end = true,
                            alpha = late.value,
                        )
                    }
                    Close(top = -6f, right = -4f, color = Color.White, circle = Color(0xE68E8E93), size = 22f, glyph = 16f)
                }
            }

            "note" -> {
                val shape = RoundedCornerShape(3.dp)
                Box(Modifier.rotate(if (reduced) 0f else -1.6f)) {
                    Box(
                        Modifier
                            .shadow(8.dp, shape, ambientColor = Color(0x591E293B), spotColor = Color(0x591E293B))
                            .background(Color(0xFFFFFDF5), shape)
                            .drawBehind {
                                // Ruled paper: a line every 26dp from 69dp down.
                                var y = 69.dp.toPx()
                                while (y < size.height) {
                                    drawRect(Color(0x293B82F6), Offset(0f, y), Size(size.width, 1.dp.toPx()))
                                    y += 26.dp.toPx()
                                }
                            }
                            .then(tap),
                    ) {
                        Parts(top = 20.dp) {
                            Head(
                                name = TextStyle(fontFamily = GreetFonts.note, fontSize = 17.sp, color = Color(0xFF1F2A44)),
                                status = TextStyle(fontFamily = GreetFonts.note, color = Color(0xFF475569)),
                            )
                            Message(TextStyle(fontFamily = GreetFonts.note, fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp, color = Color(0xFF1F2A44)))
                            Chips(
                                Modifier,
                                RoundedCornerShape(4.dp),
                                TextStyle(color = accent, fontFamily = GreetFonts.note, fontSize = 16.sp, fontWeight = FontWeight.Normal, textDecoration = TextDecoration.Underline),
                            )
                        }
                        Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                    }
                    // The tape
                    Box(Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).rotate(2f).size(84.dp, 20.dp).background(Color(0xB8FDE68A)))
                }
            }

            "postcard" -> {
                val shape = RoundedCornerShape(6.dp)
                // The close sits on the corner, outside the clipped card.
                Box {
                Box(
                    Modifier
                        .shadow(10.dp, shape, ambientColor = Color(0x662B2118), spotColor = Color(0x662B2118))
                        .clip(shape)
                        .drawBehind {
                            // The airmail edge: red / paper / blue / paper stripes at 45deg.
                            val diag = size.width + size.height
                            rotate(45f, Offset(size.width / 2, size.height / 2)) {
                                var x = -diag
                                val u = 1.dp.toPx()
                                while (x < diag) {
                                    drawRect(Color(0xFFD9383A), Offset(size.width / 2 + x, size.height / 2 - diag), Size(12 * u, 2 * diag))
                                    drawRect(Color(0xFFFBF6EA), Offset(size.width / 2 + x + 12 * u, size.height / 2 - diag), Size(6 * u, 2 * diag))
                                    drawRect(Color(0xFF2754C5), Offset(size.width / 2 + x + 18 * u, size.height / 2 - diag), Size(12 * u, 2 * diag))
                                    drawRect(Color(0xFFFBF6EA), Offset(size.width / 2 + x + 30 * u, size.height / 2 - diag), Size(6 * u, 2 * diag))
                                    x += 36 * u
                                }
                            }
                        }
                        .then(tap),
                ) {
                    Box(Modifier.padding(6.dp).background(Color(0xFFFBF6EA))) {
                        Parts {
                            Head(
                                showAvatar = false,
                                name = TextStyle(fontFamily = GreetFonts.serif, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.44.sp, color = Color(0xFF8A6D4B)),
                                status = TextStyle(fontFamily = GreetFonts.serif, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, color = Color(0xFF2B2118), lineHeight = 21.sp),
                                statusText = "Greetings from ${data.head.name}",
                                dot = false,
                            )
                            Message(
                                TextStyle(fontFamily = GreetFonts.serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Color(0xFF3B2F24), lineHeight = 21.75.sp),
                                modifier = Modifier.padding(end = 40.dp),
                            )
                            BaseChips(radius = 3.dp, family = GreetFonts.serif)
                        }
                        // Stamp and postmark
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 12.dp).size(40.dp, 48.dp)
                                .background(accent).border(2.dp, Color.White),
                            contentAlignment = Alignment.Center,
                        ) { Text("♥", color = Color.White.copy(alpha = 0.92f), fontSize = 20.sp, fontFamily = GreetFonts.serif) }
                        Canvas(Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = 34.dp).size(44.dp).rotate(-14f)) {
                            val u = size.width / 44f
                            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(22 * u, 22 * u), 21 * u)) }
                            clipPath(circle) {
                                for (k in 0 until 7) drawRect(Color(0x472B2118), Offset(0f, (5 + 6 * k) * u), Size(44 * u, u))
                            }
                            drawCircle(Color(0x592B2118), 21 * u, Offset(22 * u, 22 * u), style = Stroke(2 * u))
                        }
                    }
                }
                Close(top = -6f, right = -6f, color = Color(0xFF94A3B8), circle = Color(0xFFFBF6EA))
                }
            }

            "sticky" -> {
                val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomEnd = 18.dp, bottomStart = 2.dp)
                Box(
                    Modifier
                        .rotate(if (reduced) 0f else 1.8f)
                        .shadow(8.dp, shape, ambientColor = Color(0x735A4600), spotColor = Color(0x735A4600))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFF8B0), Color(0xFFFFEC6E))), shape)
                        .then(tap),
                ) {
                    Parts {
                        Head(
                            avatar = 26f,
                            name = TextStyle(fontFamily = GreetFonts.sticky, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Color(0xFF3A2F00)),
                            status = TextStyle(color = Color(0xFF6B5A00)),
                        )
                        Message(TextStyle(fontFamily = GreetFonts.sticky, fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 23.4.sp, color = Color(0xFF2B2300)))
                        Chips(
                            Modifier.background(Color.White.copy(alpha = 0.55f), pill).border(1.dp, Color(0x403A2F00), pill),
                            pill,
                            TextStyle(color = Color(0xFF3A2F00)),
                        )
                    }
                    // The curled corner
                    Canvas(Modifier.align(Alignment.BottomEnd).size(22.dp)) {
                        val k = size.width / 22f
                        drawContext.canvas.save()
                        drawContext.canvas.scale(k, k)
                        drawPath(
                            svgPath(CURL_D),
                            Brush.linearGradient(
                                0.5f to Color(0xFFFFE45C), 0.5f to Color(0x1F000000), 0.6f to Color(0xFFF4D63A),
                                start = Offset.Zero, end = Offset(22f, 22f),
                            ),
                        )
                        drawContext.canvas.restore()
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF6B5A00))
                }
            }

            "typewriter" -> {
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier
                        .shadow(8.dp, shape, ambientColor = Color(0x59000000), spotColor = Color(0x59000000))
                        .clip(shape)
                        .background(Color(0xFFFCFBF6))
                        .drawBehind { drawRect(accent, size = Size(size.width, 3.dp.toPx())) }
                        .then(tap),
                ) {
                    Parts(top = 17.dp) {
                        Head(
                            name = TextStyle(fontFamily = GreetFonts.mono, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.27.sp),
                            status = TextStyle(fontFamily = GreetFonts.mono),
                        )
                        Message(TextStyle(fontFamily = GreetFonts.mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.5.sp, color = Color(0xFF1C1C1C)), lines = 4)
                        BaseChips(radius = 3.dp, family = GreetFonts.mono)
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }

            "glass" -> {
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .shadow(10.dp, shape, ambientColor = Color(0x590F172A), spotColor = Color(0x590F172A))
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.86f))
                        .drawBehind {
                            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f)), Offset(size.width, 0f), 0.72f * maxOf(size.width, size.height)))
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
                        .then(tap),
                ) {
                    Parts {
                        Head(status = TextStyle(color = Color(0xFF475569)))
                        Message(TextStyle(color = Color(0xFF0F172A)))
                        Chips(
                            Modifier.background(Color.White.copy(alpha = 0.7f), pill).border(1.dp, Color(0x140F172A), pill),
                            pill,
                            TextStyle(color = Color(0xFF0F172A)),
                        )
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }

            "notification" -> {
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier
                        .shadow(10.dp, shape, ambientColor = Color(0x59000000), spotColor = Color(0x59000000))
                        .background(Color(0xF7F6F6F8), shape)
                        .border(0.5.dp, Color(0x14000000), shape)
                        .then(tap),
                ) {
                    Parts(top = 12.dp, h = 14.dp, bottom = 12.dp) {
                        Head(
                            avatar = 22f,
                            avatarRadius = 6f,
                            row = true,
                            name = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.sp, color = Color(0xFF555555)),
                            status = TextStyle(color = Color(0xFF8E8E93)),
                            statusText = "now",
                            dot = false,
                        )
                        Message(TextStyle(fontSize = 14.5.sp, lineHeight = 19.6.sp, color = Color(0xFF111111)))
                        val chipShape = RoundedCornerShape(12.dp)
                        Chips(Modifier.background(Color(0x0F000000), chipShape), chipShape, TextStyle(color = Color(0xFF111111)))
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }

            "neon" -> {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .shadow(10.dp, shape, ambientColor = accent, spotColor = accent)
                        .clip(shape)
                        .background(Color(0xFF07070C))
                        .drawBehind {
                            drawRect(Brush.radialGradient(0f to Color(0xFF1B1530), 0.7f to Color(0xFF07070C), center = Offset.Zero, radius = 1.3f * maxOf(size.width, size.height)))
                        }
                        .border(1.5.dp, accent.copy(alpha = 0.95f), shape)
                        .then(tap),
                ) {
                    Parts {
                        Head(
                            name = TextStyle(color = Color.White, shadow = Shadow(accent.copy(alpha = 0.9f), Offset.Zero, 16f)),
                            status = TextStyle(color = Color(0xFFA5A3C7)),
                        )
                        Message(TextStyle(color = Color(0xFFEDE9FE)))
                        Chips(Modifier.border(1.dp, accent.copy(alpha = 0.8f), pill), pill, TextStyle(color = Color(0xFFF5F3FF)))
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFFC4B5FD))
                }
            }

            "voice" -> {
                val shape = RoundedCornerShape(20.dp)
                val words = data.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                val secs = (words / 2.4).roundToInt().coerceIn(2, 59)
                val pillBg = data.bubble.bg.firstOrNull()?.let { hexColor(it, accent) } ?: accent
                val fg = hexColor(data.bubble.fg, Color.White)
                Box(
                    Modifier
                        .shadow(8.dp, shape, ambientColor = Color(0x4D0F172A), spotColor = Color(0x4D0F172A))
                        .background(Color.White, shape)
                        .border(1.dp, Color(0x0F0F172A), shape)
                        .then(tap),
                ) {
                    Parts {
                        Head()
                        Row(
                            Modifier.fillMaxWidth().background(pillBg, pill).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(30.dp).background(Color.White.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(14.dp)) {
                                    val k = size.width / 24f
                                    drawContext.canvas.save()
                                    drawContext.canvas.scale(k, k)
                                    drawPath(svgPath(PLAY_D), fg)
                                    drawContext.canvas.restore()
                                }
                            }
                            Row(Modifier.weight(1f).height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                VOICE_BARS.forEachIndexed { i, h ->
                                    val local = voiceClock.value - i * 60
                                    val squash = if (local <= 0f || local >= 2400f) 1f else {
                                        val half = (local % 1200f) / 600f
                                        val t = if (half <= 1f) half else 2f - half
                                        1f - 0.65f * easeInOut(t)
                                    }
                                    Box(
                                        Modifier.weight(1f).height((26f * h / 100f).dp)
                                            .graphicsLayer { scaleY = squash }
                                            .background(fg.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
                                    )
                                }
                            }
                            Text("0:" + secs.toString().padStart(2, '0'), color = fg.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Message(
                            TextStyle(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Color(0xFF475569), lineHeight = 18.85.sp),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        BaseChips()
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }

            "aurora" -> {
                val shape = RoundedCornerShape(20.dp)
                val ring = listOf(accent, Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF472B6), accent)
                Box(
                    Modifier
                        .shadow(8.dp, shape, ambientColor = accent, spotColor = accent)
                        .clip(shape)
                        .drawBehind {
                            // A conic ring (accent, cyan, violet, pink) turning behind a white card.
                            val c = Offset(size.width / 2, size.height / 2)
                            rotate(spin - 90f, c) {
                                drawCircle(Brush.sweepGradient(ring, c), radius = size.width * 0.9f, center = c)
                            }
                            val inset = 2.dp.toPx()
                            drawRoundRect(
                                Color.White,
                                Offset(inset, inset),
                                Size(size.width - 2 * inset, size.height - 2 * inset),
                                androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            )
                        }
                        .then(tap),
                ) {
                    Parts {
                        Head()
                        Message()
                        BaseChips()
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }

            else -> {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .shadow(10.dp, shape, ambientColor = Color(0x4D0F172A), spotColor = Color(0x4D0F172A))
                        .background(Color.White, shape)
                        .border(1.dp, Color(0x0F0F172A), shape)
                        .then(tap),
                ) {
                    Parts {
                        Head()
                        Message()
                        BaseChips()
                    }
                    Close(top = 10f, right = 10f, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
