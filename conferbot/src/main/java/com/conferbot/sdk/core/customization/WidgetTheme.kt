package com.conferbot.sdk.core.customization

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The widget theme as the web widget applies it (conferbot-widget
 * src/ts/theme/themeTokens.ts, base.css and the theme sheets):
 *
 *   widgetTheme      one of 27 themes ("classic" when unset or unknown)
 *   themeAppearance  light | dark | auto (auto follows the phone)
 *   themeColors      brand (the owner's colours) | theme (the theme's palette)
 *
 * Classic in light is the widget as it always was: no tokens, no shape.
 * Classic in dark repaints the surfaces only. Every other theme brings its
 * surfaces, text colours and shape; an owner setting (bubble radius, option
 * radius, a chat background of their own) still wins over the theme. Pure.
 */
data class ThemeTokens(
    /** Panel, conversation, composer and footer. */
    val bg: String,
    /** Canvas: home screen, help centre. */
    val bg2: String,
    /** Cards, menus, chips. */
    val card: String,
    /** Quiet fills: search field, tags. */
    val card2: String,
    /** The composer's field. */
    val field: String,
    val menu: String,
    val ink: String,
    val text: String,
    val muted: String,
    val faint: String,
    val line: String,
    val line2: String,
)

data class BubbleBorder(val width: Float, val color: String)

data class ThemeShape(
    val rBubble: Float,
    val rTail: Float,
    /** Option buttons (999 is a pill). */
    val rCtl: Float,
    /** The composer's field. */
    val rField: Float,
    val rCard: Float,
    /** An outline round every bubble (cartoon, neo-brutal, sketch...), or null. */
    val bubbleBorder: BubbleBorder?,
    val displayWeight: Int,
)

data class ResolvedWidgetTheme(
    val id: String = "classic",
    val label: String = "Classic",
    val appearance: String = "light",
    val scheme: String = "light",
    val colors: String = "brand",
    /** Null for classic in light: nothing changes. Every value a six-digit hex. */
    val tokens: ThemeTokens? = null,
    /** Null for classic: radii stay the widget's. */
    val shape: ThemeShape? = null,
    /** The theme's font family (a Google Fonts name) and what it covers. */
    val font: String? = null,
    val fontRole: String? = null,
) {
    val isDark: Boolean get() = scheme == "dark"
}

object WidgetTheme {
    val THEMES: List<String> = WidgetThemesData.themes.keys.toList()

    private fun lower(v: Any?): String = (v as? String)?.trim()?.lowercase().orEmpty()

    fun normalizeTheme(v: Any?): String = lower(v).takeIf { WidgetThemesData.themes.containsKey(it) } ?: "classic"
    fun normalizeAppearance(v: Any?): String = lower(v).takeIf { it == "dark" || it == "auto" } ?: "light"
    fun normalizeThemeColors(v: Any?): String = if (lower(v) == "theme") "theme" else "brand"

    /** Light or dark: always-dark themes, then the appearance (auto: the phone's setting). */
    fun resolveScheme(theme: String, appearance: String, prefersDark: Boolean): String = when {
        WidgetThemesData.themes[theme]?.alwaysDark == true -> "dark"
        appearance == "dark" -> "dark"
        appearance == "auto" -> if (prefersDark) "dark" else "light"
        else -> "light"
    }

    /** r, g, b (0-255) and alpha (0-1). */
    data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0)

    /** "#abc", "#aabbcc", "#aabbccdd", "rgb(1,2,3)", "rgba(1,2,3,.5)", white, black. */
    fun parseColor(value: Any?): Rgba? {
        val v = lower(value)
        if (v == "white") return Rgba(255.0, 255.0, 255.0)
        if (v == "black") return Rgba(0.0, 0.0, 0.0)
        Regex("^#([0-9a-f]{3,8})$").find(v)?.let { m ->
            var h = m.groupValues[1]
            if (h.length == 3 || h.length == 4) h = h.map { "$it$it" }.joinToString("")
            if (h.length != 6 && h.length != 8) return null
            val a = if (h.length == 8) h.substring(6, 8).toInt(16) / 255.0 else 1.0
            return Rgba(h.substring(0, 2).toInt(16).toDouble(), h.substring(2, 4).toInt(16).toDouble(), h.substring(4, 6).toInt(16).toDouble(), a)
        }
        Regex("^rgba?\\(\\s*([\\d.]+)[\\s,]+([\\d.]+)[\\s,]+([\\d.]+)(?:[\\s,/]+([\\d.]+))?\\s*\\)$").find(v)?.let { m ->
            fun c(s: String) = s.toDouble().coerceIn(0.0, 255.0).roundToInt().toDouble()
            val a = m.groupValues[4].takeIf { it.isNotEmpty() }?.toDouble()?.coerceIn(0.0, 1.0) ?: 1.0
            return Rgba(c(m.groupValues[1]), c(m.groupValues[2]), c(m.groupValues[3]), a)
        }
        return null
    }

    private fun hex(c: Rgba): String =
        "#" + listOf(c.r, c.g, c.b).joinToString("") { it.roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }

    private fun mix(a: Rgba, b: Rgba, t: Double) =
        Rgba(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t)

    /** A colour with alpha laid over an opaque one. */
    private fun over(top: Rgba, under: Rgba) = mix(under, top.copy(a = 1.0), top.a)

    private fun channel(c: Double): Double {
        val s = c / 255
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    private fun luminance(c: Rgba) = 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
    private fun contrast(a: Rgba, b: Rgba): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** The brand lightened until it reads on a dark surface (4.5:1 on #14161c), as the web's accentOnDark. */
    fun accentOnDark(brand: Rgba): Rgba {
        val surface = Rgba(20.0, 22.0, 28.0)
        var c = brand
        var t = 0.0
        while (t <= 1.0001 && contrast(c, surface) < 4.5) {
            val m = mix(brand, Rgba(255.0, 255.0, 255.0), t)
            c = Rgba(m.r.roundToInt().toDouble(), m.g.roundToInt().toDouble(), m.b.roundToInt().toDouble())
            t += 0.05
        }
        return c
    }

    /** A token value: hex / rgb(a), "mix:N:<colour>" or "accent:<alpha>". */
    private fun tokenColor(value: String?, brand: Rgba): Rgba? {
        if (value == null) return null
        Regex("^mix:([\\d.]+):(.+)$").find(value)?.let { m ->
            val base = parseColor(m.groupValues[2]) ?: return null
            return mix(base, brand, m.groupValues[1].toDouble() / 100)
        }
        Regex("^accent:([\\d.]+)$").find(value)?.let { m ->
            return accentOnDark(brand).copy(a = m.groupValues[1].toDouble())
        }
        return parseColor(value)
    }

    /**
     * The owner's chat background counts as theirs (the theme leaves it
     * alone) unless it is the default plain white; gradients, patterns and
     * images always are.
     */
    fun ownsChatBackground(c: Map<String, Any?>?): Boolean {
        val type = lower(c?.get("chatBgType")).ifEmpty { "solid" }
        if (type == "image" || type == "gradient" || type == "pattern") return true
        val color = parseColor(c?.get("chatBgColor")) ?: return false
        return !(color.r >= 250 && color.g >= 250 && color.b >= 250)
    }

    private fun paletteFor(theme: String, colors: String, scheme: String): Map<String, String>? {
        if (theme == "classic" || colors != "theme") return null
        val d = WidgetThemesData.themes[theme] ?: return null
        return (if (scheme == "dark") d.paletteDark else d.paletteLight) ?: d.paletteLight
    }

    /**
     * The customizations with the theme's palette in place of the owner's
     * colours (themeColors "theme", any theme but classic); otherwise the
     * same map back. The chat background goes back to plain white, which the
     * theme paints with its own surface. Logo, avatar and texts stay the owner's.
     */
    fun withThemePalette(c: Map<String, Any?>, prefersDark: Boolean): Map<String, Any?> {
        val theme = normalizeTheme(c["widgetTheme"])
        val scheme = resolveScheme(theme, normalizeAppearance(c["themeAppearance"]), prefersDark)
        val p = paletteFor(theme, normalizeThemeColors(c["themeColors"]), scheme) ?: return c
        val launcher = p["launcher"]
        val launcher2 = p["launcher2"]
        return c + mapOf(
            "headerBgColor" to p["headerBg"],
            "headerTextColor" to p["headerText"],
            "botMsgColor" to p["botBg"],
            "botTextColor" to p["botText"],
            "userMsgColor" to p["userBg"],
            "userTextColor" to p["userText"],
            "optionBubbleMsgColor" to p["optionBg"],
            "optionBubbleTextColor" to p["optionText"],
            "chatBgType" to "solid",
            "chatBgColor" to "#ffffff",
            "widgetIconThemeType" to if (launcher2 != null) "Gradient" else "Solid",
            "widgetIconBgColor" to launcher,
            "widgetGradientBgOne" to launcher,
            "widgetGradientBgTwo" to (launcher2 ?: launcher),
            "themeAccent" to p["accent"],
            "themeOnAccent" to p["onAccent"],
        )
    }

    /** The theme in force for these customizations, with every colour concrete. */
    fun resolve(c: Map<String, Any?>?, prefersDark: Boolean): ResolvedWidgetTheme {
        val theme = normalizeTheme(c?.get("widgetTheme"))
        val appearance = normalizeAppearance(c?.get("themeAppearance"))
        val colors = normalizeThemeColors(c?.get("themeColors"))
        val scheme = resolveScheme(theme, appearance, prefersDark)
        val data = WidgetThemesData.themes.getValue(theme)
        val palette = paletteFor(theme, colors, scheme)
        val brand = parseColor(palette?.get("brand")) ?: parseColor(c?.get("headerBgColor"))
            ?: parseColor(c?.get("botMsgColor")) ?: Rgba(27.0, 85.0, 243.0)

        val raw = if (theme == "classic" && scheme == "light") null else if (scheme == "dark") data.dark else data.light ?: data.dark
        val tokens = raw?.let { t ->
            val fallback = if (scheme == "dark") Rgba(20.0, 22.0, 28.0) else Rgba(255.0, 255.0, 255.0)
            val surface = over(tokenColor(t["bg"], brand) ?: fallback, fallback)
            fun flat(k: String) = hex(over(tokenColor(t[k], brand) ?: surface, surface))
            ThemeTokens(
                bg = hex(surface), bg2 = flat("bg2"), card = flat("card"), card2 = flat("card2"),
                field = flat("field"), menu = flat("menu"), ink = flat("ink"), text = flat("text"),
                muted = flat("muted"), faint = flat("faint"), line = flat("line"), line2 = flat("line2"),
            )
        }

        val shape = data.shape?.let { s ->
            val border = if (s.bubbleBorderWidth > 0f) parseColor(s.bubbleBorderColor) else null
            ThemeShape(
                rBubble = s.rBubble ?: 18f,
                rTail = s.rTail ?: 6f,
                rCtl = s.rCtl ?: 12f,
                rField = s.rField ?: 22f,
                rCard = s.rCard ?: 16f,
                bubbleBorder = border?.let {
                    val under = tokens?.let { t -> parseColor(t.bg) } ?: Rgba(255.0, 255.0, 255.0)
                    BubbleBorder(s.bubbleBorderWidth, hex(over(it, under)))
                },
                displayWeight = s.displayWeight,
            )
        }

        return ResolvedWidgetTheme(
            id = theme, label = data.label, appearance = appearance, scheme = scheme, colors = colors,
            tokens = tokens, shape = shape, font = data.font, fontRole = data.fontRole,
        )
    }
}
