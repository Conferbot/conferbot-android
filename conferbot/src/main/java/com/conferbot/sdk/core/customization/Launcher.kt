package com.conferbot.sdk.core.customization

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The closed chat button as the web widget draws it on a phone
 * (conferbot-widget templates/shared.ts generateWidgetIcon,
 * ts/launcher/launcherStyle.ts, launcherShape.ts, layout/ownerLayout.ts and
 * the phone rules in css/liveChat/responsive*.css). Pure: the floating
 * widget reads the result.
 */
data class ResolvedLauncher(
    val position: String = "right",
    /** The button's height (and width, except for tab, peek and the label pill). */
    val size: Float = 50f,
    /** Width of the button: the size, or the tab's narrower width. */
    val width: Float = 50f,
    /** Distance from the bottom and from the screen side (0 for tab and peek). */
    val bottom: Float = 16f,
    val side: Float = 16f,
    /** Peek: how far the tab is tucked off screen. */
    val tucked: Float = 0f,
    val shape: String = "bubble",
    val corners: Corners = Corners(25f, 25f, 6f, 25f),
    /** The two stops of a left-to-right gradient, or null for a solid launcher. */
    val gradient: Pair<String, String>? = null,
    /** The launcher's own colour as one solid (a gradient's first stop). */
    val solid: String = "#1b55f3",
    /** Icon ink: dark on a light launcher, else white. */
    val ink: String = "#ffffff",
    /** The soft glow round the button: the header colour. */
    val glow: String = "#1b55f3",
    /** The style drawn: an uploaded image or a missing avatar fall back to the icon. */
    val style: String = "icon",
    val label: String = Launcher.DEFAULT_LABEL,
    val avatar: String? = null,
    val imageUrl: String? = null,
    val iconId: String? = null,
    /** Icon size inside the button. */
    val glyph: Float = 30f,
    val animation: String = "subtle",
    /** The greeting next to the button, when the owner turned it on. */
    val ctaText: String? = null,
)

object Launcher {
    val SHAPES = listOf("circle", "squircle", "bubble", "tab", "peek")
    val STYLES = listOf("icon", "label", "avatar", "mascot")
    const val LABEL_MAX = 24
    const val DEFAULT_LABEL = "Chat with us"
    /** The web folds the label back to the round button at this width and under. */
    const val LABEL_MIN_WIDTH = 480

    private fun str(v: Any?): String = (v as? String)?.trim().orEmpty()

    private fun num(v: Any?): Float? = when (v) {
        null, is Boolean -> null
        is Number -> v.toFloat().takeIf { it.isFinite() }
        is String -> v.trim().takeIf { it.isNotEmpty() }?.toFloatOrNull()
        else -> null
    }

    /** A number in [lo, hi] (clamped, rounded), or null when unset / junk (ownerLayout ownerRange). */
    private fun range(v: Any?, lo: Int, hi: Int): Float? = num(v)?.let { min(hi, max(lo, it.roundToInt())).toFloat() }

    /** The shape: unset is the speech bubble, unless the owner rounded the button (a radius under 50) themselves. */
    fun normalizeShape(value: Any?, radius: Any?): String {
        if (value is String && value in SHAPES) return value
        val r = num(radius)
        return if (r != null && r < 50) "circle" else "bubble"
    }

    fun normalizeLabel(value: Any?): String =
        (value as? String)?.replace(Regex("\\s+"), " ")?.trim()?.take(LABEL_MAX).orEmpty()

    fun resolve(raw: Map<String, Any?>?, screenWidthDp: Float = 0f): ResolvedLauncher {
        val c = raw ?: emptyMap()
        val position = if (c["widgetPosition"] == "left") "left" else "right"
        // Phones: the owner's phone size and offsets, else the launcher's size at 16px from the edges.
        val size = range(c["mobileWidgetSize"], 36, 80) ?: num(c["widgetSize"])?.takeIf { it > 0 } ?: 50f
        val bottom = range(c["mobileOffsetBottom"], 0, 400) ?: 16f
        val sideOffset = range(c["mobileOffsetSide"], 0, 400) ?: 16f
        val shape = normalizeShape(c["launcherShape"], c["widgetBorderRadius"])

        val isGradient = c["widgetIconThemeType"] == "Gradient" && str(c["widgetGradientBgOne"]).isNotEmpty() && str(c["widgetGradientBgTwo"]).isNotEmpty()
        val solidRaw = if (isGradient) c["widgetGradientBgOne"] else c["widgetIconBgColor"]
        val solid = if (WidgetTheme.parseColor(solidRaw) != null) str(solidRaw) else str(c["headerBgColor"]).ifEmpty { "#1b55f3" }
        val ink = WidgetTheme.parseColor(solid)?.let { if (luminance(it) > 0.6) "#111827" else "#ffffff" } ?: "#ffffff"

        val imageUrl = str(c["widgetIconImage"]).takeIf { c["widgetIconType"] == "Image" && it.isNotEmpty() }
        val avatar = str(c["avatar"]).ifEmpty { str(c["logo"]) }.ifEmpty { null }
        var style = (c["launcherStyle"] as? String)?.takeIf { it in STYLES } ?: "icon"
        if (imageUrl != null || (style == "avatar" && avatar == null)) style = "icon"
        // The label folds back to the round button on phones, as on the web.
        if (style == "label" && screenWidthDp <= LABEL_MIN_WIDTH) style = "icon"

        val half = (size / 2).roundToInt().toFloat()
        var width = size
        var side = sideOffset
        var tucked = 0f
        val corners = when (shape) {
            "squircle" -> Corners.all((size * 0.3f).roundToInt().toFloat())
            "bubble" -> {
                // The corner nearest the screen corner pinched into a tail.
                val tail = max(4, (size * 0.12f).roundToInt()).toFloat()
                if (position == "left") Corners(half, half, half, tail) else Corners(half, half, tail, half)
            }
            "tab", "peek" -> {
                // Flush to the screen edge, rounded on its inner side.
                width = (size * 0.82f).roundToInt().toFloat()
                side = 0f
                val round = (size * 0.3f).roundToInt().toFloat()
                if (shape == "peek") tucked = (width * 0.42f).roundToInt().toFloat()
                if (position == "left") Corners(0f, round, round, 0f) else Corners(round, 0f, 0f, round)
            }
            else -> {
                // The round button: the owner's corner radius, capped at half the size.
                val r = num(c["widgetBorderRadius"])
                Corners.all(if (r == null) half else min(half, max(0f, r)))
            }
        }

        return ResolvedLauncher(
            position = position,
            size = size,
            width = width,
            bottom = bottom,
            side = side,
            tucked = tucked,
            shape = shape,
            corners = corners,
            gradient = if (isGradient) str(c["widgetGradientBgOne"]) to str(c["widgetGradientBgTwo"]) else null,
            solid = solid,
            ink = ink,
            glow = str(c["headerBgColor"]).ifEmpty { solid },
            style = style,
            label = normalizeLabel(c["launcherLabel"]).ifEmpty { DEFAULT_LABEL },
            avatar = avatar,
            imageUrl = imageUrl,
            iconId = str(c["widgetIconSVG"]).ifEmpty { null },
            glyph = (size * if (shape == "tab" || shape == "peek") 0.5f else 0.6f).roundToInt().toFloat(),
            animation = (c["launcherAnimation"] as? String)?.takeIf { it == "lively" || it == "off" } ?: "subtle",
            // Only when the owner turned the greeting on (templates/shared.ts: no chatIconCta, no bubble).
            ctaText = if (c["chatIconCta"] == true) str(c["chatIconCtaText"]).ifEmpty { null } else null,
        )
    }

    private fun channel(c: Double): Double {
        val s = c / 255
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    private fun luminance(c: WidgetTheme.Rgba) = 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
}
