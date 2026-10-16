package com.conferbot.sdk.ui.compose

import com.conferbot.sdk.core.i18n.I18n
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.customization.EntrancePlan
import com.conferbot.sdk.core.customization.FxPaint
import com.conferbot.sdk.core.customization.FxSprite
import com.conferbot.sdk.core.customization.FxValues
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.LauncherAttention
import com.conferbot.sdk.core.customization.LauncherEntrance
import com.conferbot.sdk.core.customization.LauncherIdleCall
import com.conferbot.sdk.core.customization.MascotArt
import kotlin.math.roundToInt

/*
 * The launcher effects drawn (the React Native SDK's FxSprites.tsx and
 * IdleCallView.tsx): an entrance's sprites on their stage, the idle call's
 * mascot with its moving parts and rings, the attention loop's extra pieces,
 * and the idle call's speech bubble. Each is drawn at a time t (ms) the
 * launcher's clock gives it, in the launcher's own box (dp, its top-left at
 * 0, 0); sprites reach past it.
 */
internal object LauncherFxDraw {
    /** The orbit's spark and the gloss band (attention.ts ATTENTION_CSS). */
    const val ORBIT_DOT = 8f
    const val GLOSS_WIDTH = 0.45f
    const val GLOSS_TOP = -0.3f
    const val GLOSS_HEIGHT = 1.6f

    /** The waving hand's box (a 20px emoji), turning about 70% 80%. */
    const val HAND_W = 22f
    const val HAND_H = 20f
    const val HAND_OX = 15.4f
    const val HAND_OY = 16f

    private fun alpha(o: Double) = (o * 255).roundToInt().coerceIn(0, 255)

    fun color(css: String): Int {
        val c = FxPaint.parseCssColor(css) ?: return 0
        return (alpha(c[3]) shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)
    }

    /** A box's transform as the web moves it: about `origin` (px in the box), translate, rotate, scale. */
    private fun transform(c: Canvas, ox: Double, oy: Double, tx: Double, ty: Double, r: Double, sx: Double, sy: Double) {
        c.translate((ox + tx).toFloat(), (oy + ty).toFloat())
        if (r != 0.0) c.rotate(r.toFloat())
        if (sx != 1.0 || sy != 1.0) c.scale(sx.toFloat(), sy.toFloat())
        c.translate(-ox.toFloat(), -oy.toFloat())
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** One sprite and its children at t ms, in the stage's coordinates. */
    fun sprite(c: Canvas, sp: FxSprite, t: Double) {
        val f = LauncherEntrance.spriteFrame(sp, t)
        if (f.o <= 0.002) return
        val save = c.save()
        c.translate(sp.x.toFloat(), sp.y.toFloat())
        transform(c, sp.origin?.first ?: (sp.w / 2), sp.origin?.second ?: (sp.h / 2), f.tx, f.ty, f.r, f.sx, f.sy)
        val layer = f.o < 0.998
        val leafFill = sp.fill != null && sp.children.isEmpty() && sp.svg == null
        if (layer && !leafFill) c.saveLayerAlpha(null, alpha(f.o))
        if (sp.svg != null && sp.w > 0 && sp.h > 0) {
            val s = c.save()
            if (sp.wipe != null) {
                // A stroke drawn on: only what has been drawn so far shows.
                val p = f.reveal.coerceIn(0.0, 1.0)
                if (sp.wipe == "down") c.clipRect(0f, 0f, sp.w.toFloat(), (sp.h * p).toFloat()) else c.clipRect(0f, 0f, (sp.w * p).toFloat(), sp.h.toFloat())
            }
            FxSvg.draw(c, sp.svg, sp.w.toFloat(), sp.h.toFloat())
            c.restoreToCount(s)
        } else if (sp.fill != null) {
            val col = color(sp.fill)
            fillPaint.shader = null
            fillPaint.color = if (leafFill && layer) (col and 0xFFFFFF) or (((col ushr 24) * f.o).roundToInt().coerceIn(0, 255) shl 24) else col
            val r = (sp.radius ?: 0.0).toFloat()
            c.drawRoundRect(RectF(0f, 0f, sp.w.toFloat(), sp.h.toFloat()), r, r, fillPaint)
        }
        if (sp.children.isNotEmpty()) {
            val s = c.save()
            sp.clip?.let { r ->
                val p = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, sp.w.toFloat(), sp.h.toFloat()), r.toFloat(), r.toFloat(), android.graphics.Path.Direction.CW) }
                c.clipPath(p)
            }
            for (k in sp.children) sprite(c, k, t)
            c.restoreToCount(s)
        }
        c.restoreToCount(save)
    }

    /** An entrance's sprites over the launcher. */
    fun entrance(c: Canvas, plan: EntrancePlan, t: Double) {
        for (sp in plan.sprites) sprite(c, sp, t)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * The idle call's mascot on its stage over the launcher (idleCall.ts
     * perform): the owner's mascot, each part of the drawing its own layer
     * turned about that character's joints ([origins]: its lids at its eyes),
     * and the white rings where a knock or tap lands.
     */
    fun idleCall(c: Canvas, perf: LauncherIdleCall.Performance, layers: MascotArt.Layers, origins: MascotArt.Origins, t: Double) {
        val n = perf.mascot.size
        val k = n / 100
        val box = Keyframes.evaluate(perf.self, t, perf.mascot.base)
        if (box.o > 0.002) {
            val save = c.save()
            c.translate(perf.mascot.x.toFloat(), perf.mascot.y.toFloat())
            transform(c, n / 2, n / 2, box.tx, box.ty, box.r, box.sx, box.sy)
            if (box.o < 0.998) c.saveLayerAlpha(null, alpha(box.o))
            fun layer(svg: String?, part: String? = null, origin: Pair<Double, Double>? = null) {
                if (svg == null) return
                val tracks = part?.let { perf.parts[it] }
                val base = part?.let { LauncherIdleCall.PART_BASE[it] }
                val v = if (tracks != null) Keyframes.evaluate(tracks, t, base) else FxValues.rest(base)
                if (v.o <= 0.002 || v.sx == 0.0 || v.sy == 0.0) return
                val s = c.save()
                val o = origin ?: (50.0 to 50.0)
                transform(c, o.first * k, o.second * k, v.tx * k, v.ty * k, v.r, v.sx, v.sy)
                if (v.o < 0.998) c.saveLayerAlpha(null, alpha(v.o))
                FxSvg.draw(c, svg, n.toFloat(), n.toFloat())
                c.restoreToCount(s)
            }
            layer(layers.shadow)
            // The body and everything on it move with the body.
            val b = perf.parts["body"]?.let { Keyframes.evaluate(it, t) } ?: FxValues.rest()
            val s = c.save()
            val bo = origins.body
            transform(c, bo.first * k, bo.second * k, b.tx * k, b.ty * k, b.r, b.sx, b.sy)
            layer(layers.tail)
            layer(layers.armBack, "arm", origins.arm)
            layer(layers.body)
            layer(layers.look, "look", origins.look)
            layer(layers.face)
            layer(layers.lids, "lid", origins.lid)
            layer(layers.cover)
            layer(layers.brows, "brow", origins.brow)
            layer(layers.armFront, "arm", origins.arm)
            layer(layers.smile, "smile")
            layer(layers.o, "o")
            layer(layers.grin, "grin")
            layer(layers.top)
            c.restoreToCount(s)
            c.restoreToCount(save)
        }
        for (r in perf.ripples) {
            val v = Keyframes.evaluate(listOf(r.track), t, mapOf("o" to 0.0))
            if (v.o <= 0.002) continue
            val s = c.save()
            c.translate(r.x.toFloat(), r.y.toFloat())
            c.scale(v.sx.toFloat(), v.sy.toFloat())
            val rad = (r.d / 2 - 1.25).toFloat()
            ringPaint.strokeWidth = 3.5f
            ringPaint.color = (alpha(0.12 * v.o) shl 24) or 0x0F172A
            c.drawCircle(0f, 0f, rad, ringPaint)
            ringPaint.strokeWidth = 2.5f
            ringPaint.color = (alpha(0.9 * v.o) shl 24) or 0xFFFFFF
            c.drawCircle(0f, 0f, rad, ringPaint)
            c.restoreToCount(s)
        }
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The bounce's contact shadow on the screen under the launcher (w x h its box). */
    fun bounceShadow(c: Canvas, w: Float, h: Float, v: FxValues) {
        if (v.o <= 0.002) return
        val sw = w * 0.76f
        val sh = maxOf(6f, h * 0.2f)
        val save = c.save()
        c.translate(w * 0.12f, h * 0.9f)
        transform(c, sw / 2.0, sh / 2.0, v.tx, v.ty, v.r, v.sx, v.sy)
        c.translate(sw / 2, sh / 2)
        c.scale(1f, sh / sw)
        shadowPaint.shader = RadialGradient(0f, 0f, sw / 2, intArrayOf((alpha(0.55 * v.o) shl 24) or 0x0F172A, 0x000F172A), null, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, sw / 2, shadowPaint)
        c.restoreToCount(save)
    }

    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The shine's gloss band sweeping across the button (drawn inside its clip; w x h the button). */
    fun gloss(c: Canvas, w: Float, h: Float, v: FxValues) {
        val bw = w * GLOSS_WIDTH
        val bh = h * GLOSS_HEIGHT
        val save = c.save()
        c.translate(0f, h * GLOSS_TOP)
        transform(c, bw / 2.0, bh / 2.0, v.tx + v.txp / 100 * bw, v.ty, v.r, v.sx, v.sy)
        glossPaint.shader = LinearGradient(0f, 0f, bw, 0f, intArrayOf(0x00FFFFFF, 0xBFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw, bh, glossPaint)
        c.restoreToCount(save)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The orbit's spark: a white dot with a soft glow of the launcher's colour. */
    fun orbit(c: Canvas, w: Float, h: Float, v: FxValues, glow: Int) {
        if (v.o <= 0.002) return
        val p = LauncherAttention.orbitPoint(w.toDouble(), h.toDouble(), v.od / 100)
        val a = alpha(v.o)
        val x = p.x.toFloat()
        val y = p.y.toFloat()
        val r = ORBIT_DOT / 2
        dotPaint.shader = RadialGradient(x, y, r + 6, intArrayOf(((glow ushr 24) * a / 255 shl 24) or (glow and 0xFFFFFF), glow and 0xFFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r + 6, dotPaint)
        dotPaint.shader = null
        dotPaint.color = (a shl 24) or 0xFFFFFF
        c.drawCircle(x, y, r, dotPaint)
    }
}

/** The speech bubble over the mascot (.cb-ic-bubble), its tail on the mascot `tail` dp from its left edge. */
@Composable
internal fun IdleCallBubble(line: String, tail: Float, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.semantics { contentDescription = "$line ${I18n.t("launcher.openThe")}" }) {
        // The tail: a white square turned 45deg, half under the box.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (tail - 6).dp, y = 6.dp)
                .size(12.dp)
                .rotate(45f)
                .background(Color.White),
        )
        Box(
            Modifier
                .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x380F172A), spotColor = Color(0x380F172A))
                .background(Color.White, RoundedCornerShape(16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Text(line, maxLines = 1, softWrap = false, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 16.sp)
        }
    }
}
