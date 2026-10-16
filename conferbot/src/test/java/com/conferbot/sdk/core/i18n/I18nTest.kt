package com.conferbot.sdk.core.i18n

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The SDK's own words in five languages (core/i18n/I18n.kt, a port of RN's
 * src/core/i18n.ts and the web's ts/i18n.ts): how the language is chosen,
 * the fallbacks, {name} and plurals, every key in every language, and the
 * generated tables matching RN's shared strings.json.
 */
class I18nTest {

    private val en = I18n.dictionary("en")
    private val placeholders = Regex("\\{\\w+\\}")
    private fun holes(s: String) = placeholders.findAll(s).map { it.value }.sorted().joinToString(",")

    @After
    fun reset() {
        I18n.setWidgetLanguage("en", emptyList())
    }

    // ----- which language -----

    @Test
    fun `the owner's choice wins over the phone`() {
        assertEquals("de", I18n.resolveLanguage("de", listOf("fr-FR")))
        assertEquals("nl", I18n.resolveLanguage(" NL ", listOf("fr-FR")))
    }

    @Test
    fun `auto, absent or unknown follows the first supported phone language`() {
        assertEquals("es", I18n.resolveLanguage("auto", listOf("ja-JP", "es-MX", "de-DE")))
        assertEquals("fr", I18n.resolveLanguage(null, listOf("fr_CA")))
        assertEquals("nl", I18n.resolveLanguage("klingon", listOf("nl-BE")))
        assertEquals("de", I18n.resolveLanguage(42, listOf("de")))
    }

    @Test
    fun `English when nothing fits`() {
        assertEquals("en", I18n.resolveLanguage("auto", listOf("ja-JP", "pt-BR")))
        assertEquals("en", I18n.resolveLanguage("auto", emptyList()))
        assertEquals("en", I18n.resolveLanguage("auto", null))
    }

    @Test
    fun `a stored setting is a supported code or auto`() {
        assertEquals("es", I18n.normalizeWidgetLanguage("es"))
        assertEquals("auto", I18n.normalizeWidgetLanguage("pt"))
        assertEquals("auto", I18n.normalizeWidgetLanguage(42))
        assertEquals("nl", I18n.baseLanguageOf("nl-BE"))
        assertNull(I18n.baseLanguageOf("pt-PT"))
    }

    @Test
    fun `the device's languages are BCP 47 tags`() {
        val langs = I18n.deviceLanguages()
        assertTrue(langs.isNotEmpty())
        assertTrue(langs.none { it.contains('_') })
    }

    @Test
    fun `setWidgetLanguage keeps a regional tag for dates and tells listeners once`() {
        val heard = mutableListOf<String>()
        val off = I18n.onLanguage { heard.add(it) }
        assertEquals("nl", I18n.setWidgetLanguage("auto", listOf("nl-BE", "en-GB")))
        assertEquals("nl", I18n.language)
        assertEquals("nl-BE", I18n.localeTag)
        assertEquals("nl", I18n.languageFlow.value)
        I18n.setWidgetLanguage("nl", listOf("nl-BE"))
        I18n.setWidgetLanguage("de", listOf("nl-BE"))
        assertEquals("de", I18n.localeTag)
        off()
        I18n.setWidgetLanguage("en", emptyList())
        assertEquals(listOf("nl", "de"), heard)
    }

    // ----- the words -----

    @Test
    fun `t draws the active language`() {
        assertEquals("Send", t("chat.composer.send"))
        I18n.setWidgetLanguage("de", emptyList())
        assertEquals(I18n.dictionary("de")["chat.composer.send"], t("chat.composer.send"))
        assertNotEquals("Send", t("chat.composer.send"))
    }

    @Test
    fun `name placeholders are filled, a missing value keeps its braces`() {
        assertEquals("3 out of 5", I18n.tFor("en", "rating.outOf", mapOf("n" to 3, "max" to 5)))
        assertEquals("3 out of {max}", I18n.tFor("en", "rating.outOf", mapOf("n" to 3)))
        assertEquals("3 out of 5", I18n.tFor("en", "rating.outOf", mapOf("n" to 3.0, "max" to 5)))
        assertTrue(I18n.tFor("fr", "mailchat.sent", mapOf("email" to "a@b.co")).contains("a@b.co"))
        assertEquals("3 out of 5", t("rating.outOf", "n" to 3, "max" to 5))
    }

    @Test
    fun `plurals take one for 1 and other otherwise, n filled`() {
        assertEquals("That code isn't right. 1 try left.", tPlural("otp.wrongLeft", 1))
        assertEquals("That code isn't right. 2 tries left.", tPlural("otp.wrongLeft", 2))
        assertEquals("That code isn't right. 0 tries left.", tPlural("otp.wrongLeft", 0))
        I18n.setWidgetLanguage("es", emptyList())
        assertEquals(I18n.dictionary("es").getValue("otp.wrongLeft.one").replace("{n}", "1"), tPlural("otp.wrongLeft", 1))
    }

    @Test
    fun `an unknown language is English and an unknown key is the key`() {
        assertEquals("Close", I18n.tFor("pt", "common.close"))
        assertEquals("no.such.key", I18n.tFor("de", "no.such.key"))
    }

    @Test
    fun `owner fields - the stock English shows translated, the owner's own words as written`() {
        I18n.setWidgetLanguage("de", emptyList())
        assertEquals(t("common.close"), I18n.ownerTextOr("", "common.close"))
        assertEquals(t("common.close"), I18n.ownerTextOr(null, "common.close"))
        assertEquals(t("common.close"), I18n.ownerTextOr("<b>Close.</b>", "common.close"))
        assertEquals("Schließen bitte", I18n.ownerTextOr("Schließen bitte", "common.close"))
        assertEquals("", I18n.stockText("", "common.close"))
        assertEquals(t("common.close"), I18n.stockText("close", "common.close"))
        assertEquals("Bye", I18n.stockText("Bye", "common.close"))
        assertEquals(t("common.close"), I18n.stockText("Shut", "common.close", listOf("shut")))
    }

    @Test
    fun `day and month names in the language, English keeps its own`() {
        assertEquals("Mon", I18n.weekdayNames("short", listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))[1])
        assertEquals("Jan", I18n.monthNames("short", listOf("Jan"))[0])
        I18n.setWidgetLanguage("de", emptyList())
        assertEquals(7, I18n.weekdayNames("long").size)
        assertEquals("Montag", I18n.weekdayNames("long")[1])
        assertEquals(12, I18n.monthNames("long").size)
        assertEquals("Januar", I18n.monthNames("long")[0])
        assertEquals("M", I18n.weekdayNames("narrow")[1])
    }

    // ----- the tables -----

    @Test
    fun `every key is in all five languages, none empty`() {
        assertTrue(I18n.keys.size > 1000)
        assertEquals(I18n.keys.size, I18n.keys.toSet().size)
        for (lang in I18n.SUPPORTED) {
            val dict = I18n.dictionary(lang)
            val missing = I18n.keys.filter { dict[it].isNullOrBlank() }
            assertEquals("$lang missing", emptyList<String>(), missing)
        }
    }

    @Test
    fun `each translation has the same placeholders as the English`() {
        for (lang in I18n.SUPPORTED) {
            val dict = I18n.dictionary(lang)
            // queue.position: the web passes both {n} and {ord}; English says "{ord}", the others "{n}".
            val off = I18n.keys.filter { it != "queue.position" && holes(dict.getValue(it)) != holes(en.getValue(it)) }
            assertEquals("$lang placeholders", emptyList<String>(), off)
        }
    }

    @Test
    fun `every one has its other`() {
        val lonely = I18n.keys.filter { it.endsWith(".one") }
            .filter { it.removeSuffix(".one") + ".other" !in en && it !in setOf("sources.one", "requests.one") }
        assertEquals(emptyList<String>(), lonely)
    }

    @Test
    fun `the games have their lists in every language`() {
        for (lang in I18n.SUPPORTED) {
            assertTrue(lang, I18nGames.triviaOf(lang).size >= 10)
            assertTrue(lang, I18nGames.wordsOf(lang).size >= 10)
        }
        assertNotEquals(I18nGames.triviaOf("en")[0].q, I18nGames.triviaOf("de")[0].q)
    }

    // Every literal key the SDK passes to t(...) / tPlural(...) is in the table.
    @Test
    fun `every key the SDK draws is in the table`() {
        val src = File("src/main/java/com/conferbot/sdk")
        assumeTrue(src.isDirectory)
        val call = Regex("""\b(?:t|tFor|tPlural|ownerTextOr|stockText)\(\s*(?:"[a-z]{2}",\s*)?"([a-zA-Z][\w-]*(?:\.[\w-]+)+)"""")
        val unknown = mutableListOf<String>()
        src.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.name.startsWith("I18nStrings") }.forEach { f ->
            for (m in call.findAll(f.readText())) {
                val key = m.groupValues[1]
                val known = key in en || "$key.one" in en || "$key.other" in en
                if (!known) unknown.add("${f.name}: $key")
            }
        }
        assertEquals(emptyList<String>(), unknown.distinct())
    }

    // The Kotlin tables are RN's shared strings.json (scripts/sync-i18n.sh).
    @Test
    fun `the generated tables match the shared strings json`() {
        val dev = System.getenv("CONFERBOT_DEV")?.let { File(it, "sdk") } ?: File("../..").canonicalFile
        val json = File(dev, "conferbot-react-native/scripts/parity/i18n/strings.json")
        assumeTrue("RN SDK is not checked out beside this one", json.isFile)
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val d: Map<String, Any?> = Gson().fromJson(json.readText(), type)
        @Suppress("UNCHECKED_CAST")
        val strings = d["strings"] as Map<String, Map<String, String>>
        assertEquals(I18n.SUPPORTED, d["languages"])
        assertEquals(strings.getValue("en").keys.toList(), I18n.keys)
        for (lang in I18n.SUPPORTED) assertEquals("$lang words", strings.getValue(lang), I18n.dictionary(lang))
        @Suppress("UNCHECKED_CAST")
        val games = d["games"] as Map<String, Map<String, List<Map<String, Any?>>>>
        for (lang in I18n.SUPPORTED) {
            assertEquals(games.getValue("trivia").getValue(lang).map { it["q"] }, I18nGames.triviaOf(lang).map { it.q })
            assertEquals(games.getValue("trivia").getValue(lang).map { it["wrong"] }, I18nGames.triviaOf(lang).map { it.wrong })
            assertEquals(games.getValue("words").getValue(lang).map { it["w"] }, I18nGames.wordsOf(lang).map { it.w })
        }
    }
}
