package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.customization.FontsData.CatalogFont

/**
 * The owner's font and the theme's font, as the web widget picks them.
 * Ported from the React Native SDK's src/core/ownerFont.ts (conferbot-widget
 * src/ts/fonts/ownerFont.ts findCatalogFont and the "CB Font " family;
 * src/ts/theme/themeTokens.ts isDefaultFont, parseFontFaces, the owner's
 * font over the theme's; src/ts/theme/applyTheme.ts loadThemeFont).
 *
 * Which font the chat's text uses:
 *   - the owner's font (customizations.font) when it is not a default ("",
 *     "Arial", "Gill Sans, sans-serif"): everything, body and headings;
 *   - else the theme's font: a "body" theme font for all text, a "display"
 *     one for headings only (the header's title);
 *   - else the platform's font.
 * A catalog web font (or a theme font) is fetched from Google Fonts at run
 * time (TrueType, which Google sends to a client that is not a browser); a
 * system or unknown font is looked up by its family name on the device, and
 * the platform font draws it when the device has none. Pure.
 */
object OwnerFont {
    const val PREFIX = "CB Font "

    /** Fonts that mean "the owner never picked one" (dashboard / model defaults). */
    private val DEFAULT_FONTS = setOf("", "arial", "gill sans, sans-serif", "gill sans")
    private const val SYSTEM_SANS = "-apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"

    private fun bareFont(font: Any?): String {
        var v = (font as? String)?.trim()?.replace(Regex("[;\\s]+$"), "")?.trim() ?: ""
        if (v == SYSTEM_SANS) return ""
        if (v.endsWith(", $SYSTEM_SANS")) v = v.dropLast(SYSTEM_SANS.length + 2).trim()
        return v
    }

    fun isDefaultFont(font: Any?): Boolean =
        font !is String || bareFont(font).replace("\"", "").replace("'", "").lowercase() in DEFAULT_FONTS

    private fun unquote(s: String): String = s.trim().replace(Regex("^['\"]+|['\"]+$"), "").trim()

    /** The first family named in a font-family value, unquoted. */
    fun firstFamily(value: Any?): String = (value as? String)?.let { unquote(it.split(",").firstOrNull() ?: "") } ?: ""

    private val BY_NAME: Map<String, CatalogFont> = HashMap<String, CatalogFont>().also { m ->
        FontsData.FONT_CATALOG.forEach { f -> m[f.family.lowercase()] = f; m[f.id] = f }
    }

    /** The catalog font a saved value names (by family, id, or our prefixed name), or null. */
    fun findCatalogFont(value: Any?): CatalogFont? {
        var name = firstFamily(value).lowercase()
        if (name.isEmpty()) return null
        val prefix = PREFIX.lowercase()
        if (name.startsWith(prefix)) name = name.substring(prefix.length)
        return BY_NAME[name]
    }

    fun googleFontUrl(query: String): String = "https://fonts.googleapis.com/css2?family=$query&display=swap"

    data class FontFace(val weight: Int, val style: String, val url: String)

    private val FACE_BLOCK = Regex("@font-face\\s*\\{[^\\}]*\\}")
    private val SRC = Regex("src:\\s*([^;]+);")
    private val URL = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)\\s*format\\(\\s*['\"]?(truetype|opentype)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)
    private val WEIGHT = Regex("font-weight:\\s*(\\d+)")
    private val ITALIC = Regex("font-style:\\s*italic")

    /**
     * A Google Fonts stylesheet's faces: each @font-face block's weight, style
     * and its TrueType / OpenType url (a woff2-only block is skipped).
     * Variable ranges ("100 900") read as their start.
     */
    fun parseFontFaces(css: String?): List<FontFace> = FACE_BLOCK.findAll(css.orEmpty()).mapNotNull { m ->
        val block = m.value
        val src = SRC.find(block)?.groupValues?.get(1) ?: ""
        val url = URL.find(src)?.groupValues?.get(1) ?: return@mapNotNull null
        val weight = WEIGHT.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 400
        FontFace(weight, if (ITALIC.containsMatchIn(block)) "italic" else "normal", url)
    }.toList()

    /** The name a face is registered under: the 400 face plain, others with their weight. */
    fun faceName(family: String, weight: Int): String = if (weight == 400) "$PREFIX$family" else "$PREFIX$family $weight"

    /** A font the chat uses: its family, and the Google Fonts query to fetch it (null: a system or unknown font, used by name). */
    data class FontPick(val family: String, val google: String?)

    /** The owner's or the body theme font (all of the chat's text), and the headings' font. */
    data class ChatFonts(val body: FontPick?, val display: FontPick?)

    // A system pick: "system-ui" is the platform font (nothing to set).
    private fun systemPick(family: String): FontPick? = if (family.isEmpty() || family == "system-ui") null else FontPick(family, null)

    /**
     * The fonts the chat uses for the owner's setting and the theme's font
     * (WidgetTheme `font`, `fontRole`). The owner's own font wins over the
     * theme's for both (web themeTokens --cb-o-font / --cb-o-font-display).
     */
    fun chatFontsFor(ownerFont: Any?, themeFont: String?, themeFontRole: String?): ChatFonts {
        if (!isDefaultFont(ownerFont)) {
            val cat = findCatalogFont(ownerFont)
            val pick = if (cat != null) {
                if (cat.google != null) FontPick(cat.family, cat.google) else systemPick(cat.family)
            } else systemPick(firstFamily(ownerFont))
            if (pick != null) return ChatFonts(pick, pick)
        }
        val tf = themeFont?.takeIf { it.isNotEmpty() } ?: return ChatFonts(null, null)
        val pick = FontPick(tf, FontsData.THEME_FONT_QUERIES[tf] ?: findCatalogFont(tf)?.google)
        return if (themeFontRole == "display") ChatFonts(null, pick) else ChatFonts(pick, pick)
    }

    /**
     * The loaded weight to draw `weight` with: the nearest (ties go heavier
     * for bold text, lighter for normal). null when nothing is loaded.
     */
    fun nearestWeight(loaded: Collection<Int>?, weight: Int = 400): Int? {
        if (loaded.isNullOrEmpty()) return null
        return loaded.sortedWith { a, b ->
            val d = Math.abs(a - weight) - Math.abs(b - weight)
            if (d != 0) d else if (weight >= 600) b - a else a - b
        }.first()
    }

    /** The registered face to draw `weight` with (RN fontFamilyFor): a system pick by its name, a web font once loaded. */
    fun fontFamilyFor(pick: FontPick?, loaded: Collection<Int>?, weight: Int = 400): String? {
        pick ?: return null
        if (pick.google == null) return pick.family
        return nearestWeight(loaded, weight)?.let { faceName(pick.family, it) }
    }
}
