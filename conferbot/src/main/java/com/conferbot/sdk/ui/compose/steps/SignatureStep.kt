package com.conferbot.sdk.ui.compose.steps

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.nodes.NodeUIState
import java.io.ByteArrayOutputStream

private val INK = Color(0xFF111827)
private const val INK_ARGB = 0xFF111827.toInt()

/** The drawn box around the strokes, padded and kept inside [w] x [h]; null when nothing is drawn. */
internal data class InkBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun inkBounds(strokes: List<List<Offset>>, pad: Float, w: Int, h: Int): InkBounds? {
    val points = strokes.flatten()
    if (points.isEmpty()) return null
    val l = Math.max(0f, points.minOf { it.x } - pad).toInt()
    val t = Math.max(0f, points.minOf { it.y } - pad).toInt()
    val r = Math.min(w.toFloat(), points.maxOf { it.x } + pad).toInt()
    val b = Math.min(h.toFloat(), points.maxOf { it.y } + pad).toInt()
    return InkBounds(l, t, Math.max(1, r - l), Math.max(1, b - t))
}

/**
 * The strokes as a PNG trimmed to the ink (web signature pad toDataURL after
 * trimming), on a transparent ground; null when nothing was drawn.
 */
internal fun signaturePng(strokes: List<List<Offset>>, w: Int, h: Int, strokePx: Float): Triple<ByteArray, Int, Int>? {
    val box = inkBounds(strokes, strokePx * 2, w, h) ?: return null
    val bmp = Bitmap.createBitmap(box.width, box.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = INK_ARGB
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { s ->
        if (s.size == 1) {
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(s[0].x - box.left, s[0].y - box.top, strokePx / 2, paint)
            paint.style = android.graphics.Paint.Style.STROKE
        } else {
            val p = android.graphics.Path()
            s.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x - box.left, o.y - box.top) else p.lineTo(o.x - box.left, o.y - box.top) }
            canvas.drawPath(p, paint)
        }
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return Triple(out.toByteArray(), box.width, box.height)
}

/**
 * signature-node's card (web richNodesView signatureCardHtml): a pad to sign
 * with a finger (Undo, Clear), or the name typed when the step allows it,
 * and the full name when it asks for one. Sends {action: sign, method:
 * drawn, dataUrl (PNG trimmed to the ink), width, height} or {action: sign,
 * method: typed, name}; the step uploads it and the card says why one was
 * refused. Sign stays off until something is drawn or typed.
 */
@Composable
fun SignatureStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val cfg = FlowRich.signatureConfig(step.node)
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }
    var typed by remember(step.visit) { mutableStateOf(false) }
    var name by remember(step.visit) { mutableStateOf("") }
    var fullName by remember(step.visit) { mutableStateOf("") }
    val strokes = remember(step.visit) { mutableStateListOf<List<Offset>>() }
    var current by remember(step.visit) { mutableStateOf<List<Offset>>(emptyList()) }
    var pad by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    StepCard(style) {
        if (cfg.requireName) {
            StepLabel(t("signature.fullName"), style)
            StepField(
                fullName, { fullName = it }, style, placeholder = t("signature.namePlaceholder"), enabled = !busy,
                invalid = !step.error.isNullOrEmpty() && !FlowRich.isSignatureName(fullName)
            )
        }
        StepLabel(t("signature.label"), style)
        if (typed) {
            StepField(name, { name = it }, style, placeholder = t("signature.namePlaceholder"), enabled = !busy, invalid = !step.error.isNullOrEmpty())
            if (name.isNotBlank()) {
                Text(
                    FlowRich.cleanSignatureName(name),
                    style = TextStyle(fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = INK),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, if (!step.error.isNullOrEmpty() && strokes.isEmpty()) StepLook.ERROR_LINE else style.text.copy(alpha = 0.22f), shape)
                    .onSizeChanged { pad = it }
                    .semantics { contentDescription = t("sdk.signature.pad") }
                    // Every stroke from the finger's first touch (a dot when it does not move).
                    .pointerInput(step.visit, busy) {
                        if (busy) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var line = listOf(down.position)
                            current = line
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                line = line + c.position
                                current = line
                            }
                            strokes.add(line)
                            current = emptyList()
                        }
                    }
            ) {
                if (strokes.isEmpty() && current.isEmpty()) {
                    Text(t("signature.here"), style = style.hint, modifier = Modifier.align(Alignment.Center))
                }
                // The line to sign on.
                Box(Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 30.dp).fillMaxWidth().height(1.dp).background(style.text.copy(alpha = 0.15f)))
                Canvas(Modifier.matchParentSize()) {
                    (strokes + listOf(current)).forEach { s ->
                        if (s.size == 1) drawCircle(INK, strokePx / 2, s[0])
                        else if (s.size > 1) {
                            val p = Path().apply { s.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                            drawPath(p, INK, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepLink(t("signature.undo"), style, enabled = strokes.isNotEmpty() && !busy) { strokes.removeAt(strokes.lastIndex) }
                Spacer(Modifier.width(14.dp))
                StepLink(t("signature.clear"), style, enabled = strokes.isNotEmpty() && !busy) { strokes.clear() }
            }
        }
        StepStatus(style, step.error)
        StepActions {
            if (cfg.allowTyped) StepButton(if (typed) t("signature.drawInstead") else t("signature.typeInstead"), style, enabled = !busy) { typed = !typed }
            // A typed name the step will not take is sent all the same: it says why (web).
            val ready = if (typed) name.isNotBlank() else strokes.isNotEmpty()
            StepButton(cfg.submitLabel, style, primary = true, enabled = !busy && ready) {
                val full = fullName.takeIf { cfg.requireName }
                if (typed) send(FlowStepAnswer.signTyped(name, full))
                else signaturePng(strokes.toList(), pad.width, pad.height, strokePx)?.let { (png, w, h) -> send(FlowStepAnswer.signDrawn(png, w, h, full)) }
            }
        }
    }
}
