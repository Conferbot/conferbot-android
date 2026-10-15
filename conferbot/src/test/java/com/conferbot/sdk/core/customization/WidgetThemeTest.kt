package com.conferbot.sdk.core.customization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {

    private val hex = Regex("^#[0-9a-f]{6}$")

    @Test
    fun knowsTheTwentySevenWebThemesClassicFirst() {
        assertEquals(27, WidgetTheme.THEMES.size)
        assertEquals("classic", WidgetTheme.THEMES.first())
        assertTrue(WidgetTheme.THEMES.containsAll(listOf("minimal", "glass", "neobrutal", "midnight", "space")))
    }

    @Test
    fun fallsBackToClassicLightBrand() {
        assertEquals("classic", WidgetTheme.normalizeTheme("NOPE"))
        assertEquals("minimal", WidgetTheme.normalizeTheme(" Minimal "))
        assertEquals("light", WidgetTheme.normalizeAppearance("purple"))
        assertEquals("auto", WidgetTheme.normalizeAppearance("AUTO"))
        assertEquals("theme", WidgetTheme.normalizeThemeColors("theme"))
        assertEquals("brand", WidgetTheme.normalizeThemeColors(null))
    }

    @Test
    fun resolvesTheScheme() {
        assertEquals("dark", WidgetTheme.resolveScheme("midnight", "light", false))
        assertEquals("dark", WidgetTheme.resolveScheme("classic", "dark", false))
        assertEquals("dark", WidgetTheme.resolveScheme("classic", "auto", true))
        assertEquals("light", WidgetTheme.resolveScheme("classic", "auto", false))
        assertEquals("light", WidgetTheme.resolveScheme("minimal", "light", true))
    }

    @Test
    fun changesNothingForClassicInLight() {
        val t = WidgetTheme.resolve(emptyMap(), false)
        assertEquals("classic", t.id)
        assertNull(t.tokens)
        assertNull(t.shape)
    }

    @Test
    fun repaintsOnlySurfacesForClassicInDark() {
        val t = WidgetTheme.resolve(mapOf("themeAppearance" to "dark"), false)
        assertEquals("#14161c", t.tokens?.bg)
        assertEquals("#f1f3f7", t.tokens?.ink)
        assertNull(t.shape)
        assertTrue(t.isDark)
    }

    @Test
    fun givesEveryThemeConcreteColours() {
        for (id in WidgetTheme.THEMES) for (appearance in listOf("light", "dark")) {
            val t = WidgetTheme.resolve(mapOf("widgetTheme" to id, "themeAppearance" to appearance, "headerBgColor" to "#0F766E"), false)
            val tokens = t.tokens ?: continue
            listOf(tokens.bg, tokens.bg2, tokens.card, tokens.card2, tokens.field, tokens.menu, tokens.ink,
                tokens.text, tokens.muted, tokens.faint, tokens.line, tokens.line2).forEach {
                assertTrue("$id $appearance $it", hex.matches(it))
            }
        }
    }

    @Test
    fun bringsTheThemeShape() {
        val minimal = WidgetTheme.resolve(mapOf("widgetTheme" to "minimal"), false).shape!!
        assertEquals(14f, minimal.rBubble)
        assertEquals(10f, minimal.rCtl)
        assertEquals(12f, minimal.rField)
        assertEquals(BubbleBorder(2f, "#000000"), WidgetTheme.resolve(mapOf("widgetTheme" to "neobrutal"), false).shape!!.bubbleBorder)
    }

    @Test
    fun tintsBrandMixedSurfaces() {
        val red = WidgetTheme.resolve(mapOf("widgetTheme" to "clay", "headerBgColor" to "#ff0000"), false).tokens!!.bg
        val blue = WidgetTheme.resolve(mapOf("widgetTheme" to "clay", "headerBgColor" to "#0000ff"), false).tokens!!.bg
        assertTrue(hex.matches(red))
        assertNotEquals(red, blue)
    }

    @Test
    fun makesGlassOpaque() {
        assertEquals("#fafbfd", WidgetTheme.resolve(mapOf("widgetTheme" to "glass"), false).tokens!!.bg)
    }

    @Test
    fun swapsInThePaletteOnlyForThemeColours() {
        val owner = mapOf<String, Any?>("widgetTheme" to "minimal", "headerBgColor" to "#0F766E", "chatBgType" to "pattern", "logo" to "x")
        assertSame(owner, WidgetTheme.withThemePalette(owner, false))
        val p = WidgetTheme.withThemePalette(owner + ("themeColors" to "theme"), false)
        assertEquals("#09090b", p["headerBgColor"])
        assertEquals("#2563eb", p["userMsgColor"])
        assertEquals("solid", p["chatBgType"])
        assertEquals("#ffffff", p["chatBgColor"])
        assertEquals("x", p["logo"])
        val dark = WidgetTheme.withThemePalette(owner + mapOf("themeColors" to "theme", "themeAppearance" to "dark"), false)
        assertEquals("#18181b", dark["headerBgColor"])
    }

    @Test
    fun onlyPlainWhiteIsTheDefaultBackground() {
        assertFalse(WidgetTheme.ownsChatBackground(emptyMap()))
        assertFalse(WidgetTheme.ownsChatBackground(mapOf("chatBgColor" to "#ffffff")))
        assertTrue(WidgetTheme.ownsChatBackground(mapOf("chatBgColor" to "#f8fafc")))
        assertTrue(WidgetTheme.ownsChatBackground(mapOf("chatBgType" to "gradient")))
    }

    @Test
    fun takesThemeRadiiUnlessTheOwnerSetTheirOwn() {
        val theme = WidgetTheme.resolve(mapOf("widgetTheme" to "minimal"), false)
        val c = Customizations.resolve(mapOf("bubbleBorderRadius" to 10, "optionBubbleBorderRadius" to 10), theme = theme)
        assertEquals(Corners(14f, 14f, 14f, 14f), c.botBubble)
        assertEquals(10f, c.optionRadius)
        assertEquals(12f, c.fieldRadius)
        val own = Customizations.resolve(mapOf("bubbleBorderRadius" to 4, "optionBubbleBorderRadius" to 20), theme = theme)
        assertEquals(4f, own.botBubble.bottomLeft)
        assertEquals(20f, own.optionRadius)
    }

    @Test
    fun keepsClassicWithoutATheme() {
        val c = Customizations.resolve(mapOf("optionBubbleBorderRadius" to 10))
        assertEquals(6f, c.botBubble.bottomLeft)
        assertEquals(25f, c.fieldRadius)
        assertNull(c.surface)
        assertNull(c.bubbleBorder)
    }

    @Test
    fun paintsTheSurfaceUnlessTheOwnerHasABackground() {
        val theme = WidgetTheme.resolve(mapOf("widgetTheme" to "midnight"), false)
        assertEquals(theme.tokens!!.bg, Customizations.resolve(emptyMap(), theme = theme).surface)
        assertNull(Customizations.resolve(mapOf("chatBgType" to "gradient"), theme = theme).surface)
    }

    @Test
    fun usesThePaletteSendColours() {
        val raw = WidgetTheme.withThemePalette(mapOf("widgetTheme" to "aurora", "themeColors" to "theme", "themeAppearance" to "dark"), false)
        val c = Customizations.resolve(raw)
        assertEquals("#a78bfa", c.accent)
        assertEquals("#0c0d18", c.accentInk)
        assertNull(Customizations.resolve(mapOf("headerBgColor" to "#0F766E")).accentInk)
    }
}
