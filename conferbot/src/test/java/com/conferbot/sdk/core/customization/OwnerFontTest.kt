package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.customization.OwnerFont.ChatFonts
import com.conferbot.sdk.core.customization.OwnerFont.FontFace
import com.conferbot.sdk.core.customization.OwnerFont.FontPick
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import org.junit.Test
import java.io.File

/**
 * The owner's font and the theme's (core/customization/OwnerFont.kt): RN's
 * __tests__/core/ownerFont.test.ts (web ts/fonts/ownerFont.ts,
 * ts/theme/themeTokens.ts, ts/theme/applyTheme.ts loadThemeFont), and the
 * catalog checked against RN's fonts.data.ts.
 */
class OwnerFontTest {

    private val googleTtf = """
        @font-face {
          font-family: 'Inter';
          font-style: normal;
          font-weight: 400;
          font-display: swap;
          src: url(https://fonts.gstatic.com/s/inter/v20/a.ttf) format('truetype');
        }
        @font-face {
          font-family: 'Inter';
          font-style: normal;
          font-weight: 700;
          font-display: swap;
          src: url(https://fonts.gstatic.com/s/inter/v20/b.ttf) format('truetype');
        }
        @font-face {
          font-family: 'Inter';
          font-style: italic;
          font-weight: 400;
          src: url(https://fonts.gstatic.com/s/inter/v20/c.ttf) format('truetype');
        }
        @font-face {
          font-family: 'Inter';
          font-weight: 400;
          src: url(https://fonts.gstatic.com/s/inter/v20/d.woff2) format('woff2');
        }
    """.trimIndent()

    @Test
    fun `the defaults that mean no font was picked`() {
        assertThat(OwnerFont.isDefaultFont("")).isTrue()
        assertThat(OwnerFont.isDefaultFont("Arial")).isTrue()
        assertThat(OwnerFont.isDefaultFont("Gill Sans, sans-serif;")).isTrue()
        assertThat(OwnerFont.isDefaultFont(null)).isTrue()
        assertThat(OwnerFont.isDefaultFont("Inter")).isFalse()
    }

    @Test
    fun `a catalog font by family, stack, id or our prefixed name`() {
        assertThat(OwnerFont.findCatalogFont("Inter")?.id).isEqualTo("inter")
        assertThat(OwnerFont.findCatalogFont("'Plus Jakarta Sans', sans-serif")?.id).isEqualTo("plus-jakarta-sans")
        assertThat(OwnerFont.findCatalogFont("dm-sans")?.family).isEqualTo("DM Sans")
        assertThat(OwnerFont.findCatalogFont("CB Font Poppins")?.id).isEqualTo("poppins")
        assertThat(OwnerFont.findCatalogFont("Charlesworth")).isNull()
        assertThat(OwnerFont.firstFamily("\"Gill Sans\", serif")).isEqualTo("Gill Sans")
    }

    @Test
    fun `Google Fonts' TrueType faces, italics kept by style, woff2 skipped`() {
        assertThat(OwnerFont.parseFontFaces(googleTtf)).containsExactly(
            FontFace(400, "normal", "https://fonts.gstatic.com/s/inter/v20/a.ttf"),
            FontFace(700, "normal", "https://fonts.gstatic.com/s/inter/v20/b.ttf"),
            FontFace(400, "italic", "https://fonts.gstatic.com/s/inter/v20/c.ttf")
        ).inOrder()
        assertThat(OwnerFont.parseFontFaces("")).isEmpty()
        assertThat(OwnerFont.googleFontUrl("Inter:wght@400;700")).isEqualTo("https://fonts.googleapis.com/css2?family=Inter:wght@400;700&display=swap")
    }

    @Test
    fun `faces named so the app's own text never picks them up`() {
        assertThat(OwnerFont.faceName("Inter", 400)).isEqualTo("CB Font Inter")
        assertThat(OwnerFont.faceName("Inter", 700)).isEqualTo("CB Font Inter 700")
    }

    @Test
    fun `the owner's font over the theme's, everywhere`() {
        val inter = FontPick("Inter", "Inter:wght@400;500;600;700")
        assertThat(OwnerFont.chatFontsFor("Inter", "Fraunces", "display")).isEqualTo(ChatFonts(inter, inter))
        // A system font is used by name; system-ui is the platform's own.
        assertThat(OwnerFont.chatFontsFor("Georgia", null, null)).isEqualTo(ChatFonts(FontPick("Georgia", null), FontPick("Georgia", null)))
        assertThat(OwnerFont.chatFontsFor("system-ui", null, null)).isEqualTo(ChatFonts(null, null))
        // An older bot's unknown font stays a plain family name.
        assertThat(OwnerFont.chatFontsFor("Charlesworth", null, null).body).isEqualTo(FontPick("Charlesworth", null))
    }

    @Test
    fun `a body theme font for all text, a display one for headings only`() {
        val manrope = FontPick("Manrope", "Manrope:wght@400;500;600;700")
        assertThat(OwnerFont.chatFontsFor("Arial", "Manrope", "body")).isEqualTo(ChatFonts(manrope, manrope))
        assertThat(OwnerFont.chatFontsFor("", "Fraunces", "display")).isEqualTo(ChatFonts(null, FontPick("Fraunces", FontsData.THEME_FONT_QUERIES["Fraunces"])))
        assertThat(OwnerFont.chatFontsFor("", null, null)).isEqualTo(ChatFonts(null, null))
    }

    @Test
    fun `each weight drawn with the nearest face it has, nothing until one is loaded`() {
        val pick = FontPick("Inter", "Inter")
        assertThat(OwnerFont.fontFamilyFor(pick, emptyList(), 400)).isNull()
        assertThat(OwnerFont.fontFamilyFor(pick, listOf(400, 700), 400)).isEqualTo("CB Font Inter")
        assertThat(OwnerFont.fontFamilyFor(pick, listOf(400, 700), 600)).isEqualTo("CB Font Inter 700")
        assertThat(OwnerFont.fontFamilyFor(pick, listOf(400, 600), 500)).isEqualTo("CB Font Inter")
        assertThat(OwnerFont.fontFamilyFor(pick, listOf(500), 400)).isEqualTo("CB Font Inter 500")
        assertThat(OwnerFont.fontFamilyFor(FontPick("Georgia", null), null, 700)).isEqualTo("Georgia")
        assertThat(OwnerFont.fontFamilyFor(null, listOf(400), 400)).isNull()
    }

    @Test
    fun `the catalog and theme fonts are RN's, unchanged`() {
        val dev = System.getenv("CONFERBOT_DEV") ?: File("../../..").canonicalPath
        val rn = listOf(File(dev, "sdk/conferbot-react-native/src/core/fonts.data.ts"), File("../../conferbot-react-native/src/core/fonts.data.ts"))
            .firstOrNull { it.exists() } ?: return // RN is not checked out beside this SDK
        val src = rn.readText()
        val catalog = JsonParser.parseString(src.substringAfter("export const FONT_CATALOG: CatalogFont[] = ").substringBefore("];") + "]").asJsonArray
        assertThat(catalog.size()).isEqualTo(FontsData.FONT_CATALOG.size)
        catalog.forEachIndexed { i, e ->
            val o = e.asJsonObject
            val f = FontsData.FONT_CATALOG[i]
            assertThat(f.id).isEqualTo(o["id"].asString)
            assertThat(f.family).isEqualTo(o["family"].asString)
            assertThat(f.stack).isEqualTo(o["stack"].asString)
            assertThat(f.google).isEqualTo(if (o["google"].isJsonNull) null else o["google"].asString)
        }
        val themes = JsonParser.parseString(src.substringAfter("export const THEME_FONT_QUERIES: Record<string, string> = ").substringBefore("};") + "}").asJsonObject
        assertThat(themes.entrySet().associate { it.key to it.value.asString }).isEqualTo(FontsData.THEME_FONT_QUERIES)
    }
}
