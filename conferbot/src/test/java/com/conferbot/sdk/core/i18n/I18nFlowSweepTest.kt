package com.conferbot.sdk.core.i18n

import com.conferbot.sdk.core.flow.FlowDriver
import com.conferbot.sdk.core.flow.FlowFixturesTest
import com.conferbot.sdk.core.flow.Js
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.TimeZone

/**
 * Every flow fixture run under "de" (the model is RN's
 * __tests__/components/i18nSweep.test.tsx, its flow part): every step state
 * the engine hands the cards (prompts, placeholders, buttons, the reason an
 * answer was refused) and every bubble it renders must hold no English table
 * value the German table words differently. The fixture's own strings (the
 * owner's flow, the server's answers, the visitor's input) are taken out
 * first: what the owner wrote is shown as written. The record's own values
 * ("Continue", the survey and queue texts) stay English, as the web stores
 * them, and are not drawn from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class I18nFlowSweepTest(private val id: String, private val file: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = FlowFixturesTest.cases()

        private fun esc(s: String) = Regex.escape(s)

        /** English table values the German table words differently, as patterns. */
        val ENGLISH_VALUES: List<Pair<String, Regex>> by lazy {
            val en = I18n.dictionary("en")
            val de = I18n.dictionary("de")
            en.keys.filter { de[it] != null && de[it] != en[it] }.mapNotNull { key ->
                val v = en.getValue(key)
                if (Regex("\\{\\w+\\}").containsMatchIn(v)) {
                    // The longest literal part of a template ("{name} is typing" -> " is typing").
                    val part = v.split(Regex("\\{\\w+\\}")).map { it.trim() }.maxByOrNull { it.length } ?: ""
                    if (part.length >= 8) key to Regex(esc(part)) else null
                } else if (v.trim().length < 4) null
                else key to Regex("(^|[^A-Za-zÀ-ÿ])${esc(v.trim())}($|[^A-Za-zÀ-ÿ])")
            }
        }

        /** Every string in the fixture, and the text of its HTML and markdown. */
        fun ownerOf(fx: Map<String, Any?>): List<String> {
            val out = mutableListOf<String>()
            fun walk(v: Any?) {
                when (v) {
                    is String -> {
                        out.add(v)
                        out.addAll(
                            v.replace(Regex("<[^>]*>"), "\n").replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
                                .split(Regex("\\n|\\*\\*")).map { it.trim() }
                        )
                    }
                    is Map<*, *> -> v.values.forEach { walk(it) }
                    is List<*> -> v.forEach { walk(it) }
                }
            }
            walk(listOf(fx["flow"], fx["server"], fx["inputs"], fx["customizations"]))
            return out.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }
        }

        /** What in [strings] is English, the owner's own strings taken out first. */
        fun englishIn(strings: Collection<String>, owner: List<String>): List<String> {
            val templates = owner.filter { Regex("\\{\\w+\\}").containsMatchIn(it) }
                .map { o -> Regex(o.split(Regex("\\{\\w+\\}")).joinToString(".+?") { esc(it) }) }
            val bad = mutableListOf<String>()
            for (raw in strings) {
                val trimmed = raw.trim()
                if (trimmed.length >= 2 && owner.any { it.contains(trimmed) }) continue
                var s = raw
                for (o in owner) s = s.split(o).joinToString(" ")
                for (re in templates) s = re.replace(s, " ")
                s = s.trim()
                if (s.isEmpty()) continue
                ENGLISH_VALUES.firstOrNull { it.second.containsMatchIn(s) }?.let { bad.add("\"$raw\" (en ${it.first})") }
            }
            return bad.distinct()
        }

        /** Every string a step state holds, its fields walked (the step's settings included). */
        fun stringsOf(root: Any?): List<String> {
            val out = mutableListOf<String>()
            val seen = IdentityHashMap<Any, Boolean>()
            fun walk(v: Any?, depth: Int) {
                if (v == null || depth > 12) return
                when (v) {
                    is String -> out.add(v)
                    is Number, is Boolean, is Char, is Enum<*> -> {}
                    is Map<*, *> -> v.values.forEach { walk(it, depth + 1) }
                    is Iterable<*> -> v.forEach { walk(it, depth + 1) }
                    is Array<*> -> v.forEach { walk(it, depth + 1) }
                    else -> {
                        if (seen.put(v, true) != null) return
                        if (!v.javaClass.name.startsWith("com.conferbot.")) return
                        var c: Class<*>? = v.javaClass
                        while (c != null && c != Any::class.java) {
                            for (f in c.declaredFields) {
                                if (Modifier.isStatic(f.modifiers)) continue
                                runCatching { f.isAccessible = true; walk(f.get(v), depth + 1) }
                            }
                            c = c.superclass
                        }
                    }
                }
            }
            walk(root, 0)
            return out.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        }
    }

    private var savedZone: TimeZone? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.setWidgetLanguage("de", emptyList())
    }

    @After
    fun tearDown() {
        I18n.setWidgetLanguage("en", emptyList())
        savedZone?.let { TimeZone.setDefault(it) }
        Dispatchers.resetMain()
    }

    @Test
    fun noEnglishUnderGerman() {
        val fx = FlowFixturesTest.load(file)
        val owner = ownerOf(fx)
        val drawn = linkedSetOf<String>()
        val outcome = FlowDriver.run(fx, view = { _, ui, _, _ ->
            if (ui != null) drawn.addAll(stringsOf(ui))
            false to null
        })
        Js.list(outcome.data["rendered"]).forEach { r -> Js.str(Js.map(r)?.get("plain")).takeIf { it.isNotBlank() }?.let { drawn.add(it) } }
        assertEquals(id, emptyList<String>(), englishIn(drawn, owner))
    }

    // The check itself sees English (run once, on the first case).
    @Test
    fun theCheckCatchesEnglish() {
        if (id != cases().first()[0]) return
        assertTrue(englishIn(listOf(I18n.tFor("en", "validation.email")), emptyList()).isNotEmpty())
        assertTrue(englishIn(listOf(I18n.tFor("en", "otp.sentTo", mapOf("n" to 6, "email" to "a@b.co"))), emptyList()).isNotEmpty())
        assertEquals(emptyList<String>(), englishIn(listOf(I18n.tFor("de", "validation.email")), emptyList()))
        assertEquals(emptyList<String>(), englishIn(listOf("Please enter a valid email address"), listOf("Please enter a valid email address")))
    }
}
