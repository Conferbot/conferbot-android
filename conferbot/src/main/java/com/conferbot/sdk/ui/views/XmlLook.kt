package com.conferbot.sdk.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.customization.Corners

/**
 * The bot's settings (core/customization) for the XML / View screen: colours
 * as ints and bubbles as drawables, drawn as the web widget draws them. Until
 * the bot's settings arrive, every helper keeps the screen's own look.
 */
internal object XmlLook {
    val loaded: Boolean get() = Conferbot.serverCustomizationMap.value.isNotEmpty()
    private val c get() = Conferbot.customizations.value

    fun color(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        var h = hex.trim()
        Regex("rgba?\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").find(h)?.let { m ->
            val (r, g, b) = m.destructured
            return Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }
        if (h.startsWith("#") && (h.length == 4 || h.length == 5)) h = "#" + h.drop(1).map { "$it$it" }.joinToString("")
        return try { Color.parseColor(h) } catch (e: IllegalArgumentException) { fallback }
    }

    /** Black or white, whichever reads on the colour. */
    fun inkOn(color: Int): Int {
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return if (lum > 0.6) Color.BLACK else Color.WHITE
    }

    fun headerBg(fallback: Int) = if (loaded) color(c.headerBg, fallback) else fallback
    fun headerText(fallback: Int) = if (loaded) color(c.headerText, fallback) else fallback
    fun botBg(fallback: Int) = if (loaded) color(c.botBubbleColor, fallback) else fallback
    fun botText(fallback: Int) = if (loaded) color(c.botTextColor, fallback) else fallback
    fun userBg(fallback: Int) = if (loaded) color(c.userBubbleColor, fallback) else fallback
    fun userText(fallback: Int) = if (loaded) color(c.userTextColor, fallback) else fallback
    fun optionBg(fallback: Int) = if (loaded) color(c.optionBubbleColor, fallback) else fallback
    fun optionText(fallback: Int) = if (loaded) color(c.optionTextColor, fallback) else fallback

    private fun px(context: Context, dp: Float) = dp * context.resources.displayMetrics.density

    private fun drawable(context: Context, fill: Int, corners: Corners) = GradientDrawable().apply {
        setColor(fill)
        cornerRadii = floatArrayOf(
            px(context, corners.topLeft), px(context, corners.topLeft),
            px(context, corners.topRight), px(context, corners.topRight),
            px(context, corners.bottomRight), px(context, corners.bottomRight),
            px(context, corners.bottomLeft), px(context, corners.bottomLeft),
        )
    }

    /** A bot (or agent) bubble: the bot's colour and the web's corners. Keeps the layout's own when not loaded. */
    fun botBubble(view: View) {
        if (!loaded) return
        view.background = drawable(view.context, color(c.botBubbleColor, Color.parseColor("#0b4eb8")), c.botBubble)
        view.backgroundTintList = null
    }

    fun userBubble(view: View) {
        if (!loaded) return
        view.background = drawable(view.context, color(c.userBubbleColor, Color.parseColor("#f0f0f0")), c.userBubble)
        view.backgroundTintList = null
    }

    /** A choice button: the option colours and radius. */
    fun option(view: TextView, enabled: Boolean) {
        if (!loaded) return
        val bg = color(c.optionBubbleColor, Color.parseColor("#e7e4e4"))
        view.background = drawable(view.context, bg, Corners.all(c.optionRadius))
        view.backgroundTintList = null
        view.setTextColor(color(c.optionTextColor, Color.BLACK))
        view.alpha = if (enabled) 1f else 0.5f
        view.isAllCaps = false
    }

    /** Typing dots in the bot's text colour, on the bot's bubble. */
    fun typing(bubble: View, dots: List<View>) {
        if (!loaded) return
        botBubble(bubble)
        val ink = ColorStateList.valueOf(color(c.botTextColor, Color.WHITE))
        dots.forEach { it.backgroundTintList = ink }
    }
}
