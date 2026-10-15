package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.nodes.NodeHandlerRegistry
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.TimeZone

/**
 * The shared flow-node spec, run through this SDK's NodeFlowEngine and
 * handlers: conferbot-react-native scripts/parity/flows (nodes.spec.json +
 * 156 fixtures whose outcomes come from running the web widget's own code),
 * copied into src/test/resources/flows by scripts/sync-flow-fixtures.sh.
 * One test per case; the model is RN's __tests__/parity/flows.test.ts.
 *
 * Compared, per the fixtures README ("SDK tests should"): path (node, type,
 * via), ports, variables (key + value), end, errors (count + node), answers
 * (shape + value fields), rendered plain text, emits, unsupported, http (path
 * + body keys), flowVariables, and the typing pace between shown steps
 * (+-50 ms) where it is not skipped on purpose (TIMING_SKIP). Objects compare
 * by their keys and values, whatever the key order.
 *
 * Cases for node types this SDK does not run yet are listed in PENDING_TYPES
 * and skipped - every other case, the engine rules included, must pass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class FlowFixturesTest(private val id: String, private val file: String) {

    companion object {
        /** Node types not run by this SDK yet: their cases are pending. */
        val PENDING_TYPES = emptySet<String>()

        /**
         * Cases whose typing pace differs on purpose, with the reason. Everything
         * else in them is still compared. In the web harness the visitor's next
         * action is played once every timer has run, the card's own UI timers
         * included (focus 30-50 ms, a debounce, an animation). They move the web
         * clock, not the flow; this SDK's cards are native views with no such timers.
         */
        val TIMING_SKIP = mapOf(
            "navigate.links-then-done" to "web link-click timers before Done (420 ms)",
            "rating-choice.smiley-and-ten" to "web rating animation before the next pick (300 ms)",
            "otp.verified" to "web OTP card focus timers (50 + 30 ms)",
            "otp.wrong-code-then-locked" to "web OTP card focus timers (50 + 30 ms)",
            "address.search-and-pick" to "web address search debounce and focus (300 + 50 ms)",
            "payment.paid" to "web payment card focus timer (60 ms)",
            "real.qa-all-nodes-tour" to "the card timers above, added up over the tour"
        )

        private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).serializeNulls().create()

        @Suppress("UNCHECKED_CAST")
        fun load(file: String): Map<String, Any?> {
            val text = FlowFixturesTest::class.java.classLoader!!.getResource("flows/$file")!!.readText()
            return gson.fromJson(text, Map::class.java) as Map<String, Any?>
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            // FLOW_CASE=<id prefix> runs only the matching cases (debugging).
            val only = System.getenv("FLOW_CASE")?.takeIf { it.isNotBlank() }
            return Js.list(load("index.json")["cases"]).map {
                val c = Js.map(it)!!
                arrayOf<Any>(Js.str(c["id"]), Js.str(c["file"]))
            }.filter { only == null || (it[0] as String).startsWith(only) }
        }

        private val ISO_TIME = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$")
        private val ISO_IN_TEXT = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

        /**
         * A time the visitor acted at ("consent_at", "signature_at"): it depends
         * on when the driver plays the action, so it is compared as a timestamp.
         */
        private fun stamp(v: Any?): Any? = if (v is String && ISO_TIME.matches(v)) "<timestamp>" else v
        private fun stampsIn(text: Any?): Any? = if (text is String) text.replace(ISO_IN_TEXT, "<timestamp>") else text

        /** A value as canonical JSON: objects with sorted keys, numbers as JavaScript prints them. */
        fun canon(v: Any?): String = when (v) {
            is Map<*, *> -> v.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") { "${Js.stringify(it.key.toString())}:${canon(it.value)}" }
            is List<*> -> v.joinToString(",", "[", "]") { canon(it) }
            else -> Js.stringify(v)
        }

        /** The keys of [o] that the expectation names, absent ones left out (as JSON.stringify drops undefined). */
        private fun pick(o: Any?, keys: Collection<String>): Map<String, Any?> {
            val m = Js.map(o) ?: emptyMap()
            return LinkedHashMap<String, Any?>().apply { keys.forEach { k -> if (m.containsKey(k)) put(k, m[k]) } }
        }

        private fun bodyKeys(h: Any?): Map<String, Any?> {
            val m = Js.map(h) ?: emptyMap()
            return mapOf("path" to m["path"], "keys" to (Js.map(m["body"])?.keys?.sorted() ?: emptyList()))
        }

        /** The differences between what the web did and what this SDK did. */
        fun compare(id: String, exp: Map<String, Any?>, got: Map<String, Any?>): List<String> {
            val diffs = mutableListOf<String>()
            fun eq(label: String, a: Any?, b: Any?) {
                val ja = canon(a)
                val jb = canon(b)
                if (ja != jb) diffs.add("$label\n    web: $ja\n    sdk: $jb")
            }
            fun list(key: String, from: Map<String, Any?>) = Js.list(from[key])

            val step = listOf("node", "type", "via")
            eq("path", list("path", exp).map { pick(it, step) }, list("path", got).map { pick(it, step) })
            eq("ports", list("ports", exp), list("ports", got))
            fun vars(l: List<Any?>) = l.map { mapOf("key" to Js.map(it)?.get("key"), "value" to stamp(Js.map(it)?.get("value"))) }
            eq("variables", vars(list("variables", exp)), vars(list("variables", got)))
            val expEnd = Js.map(exp["end"]) ?: emptyMap()
            val endKeys = if (expEnd["crashed"] == true) listOf("ended", "crashed", "at") else if (expEnd["ended"] == true) listOf("ended") else listOf("ended", "waitingAt")
            eq("end", pick(expEnd, endKeys), pick(got["end"], endKeys))
            eq("errors (node)", list("errors", exp).map { Js.map(it)?.get("node") }, list("errors", got).map { Js.map(it)?.get("node") })
            val ea = list("answers", exp)
            val ga = list("answers", got)
            if (ea.size != ga.size) eq("answers", ea, ga)
            else ea.forEachIndexed { i, a -> eq("answers[$i]", a, pick(ga[i], Js.map(a)?.keys ?: emptySet())) }
            fun plainOf(l: List<Any?>) = l.map { mapOf("node" to Js.map(it)?.get("node"), "plain" to stampsIn(Js.map(it)?.get("plain"))) }
            eq("rendered", plainOf(list("rendered", exp)), plainOf(list("rendered", got)))
            eq("emits", list("emits", exp), list("emits", got))
            eq("unsupported", list("unsupported", exp), list("unsupported", got))
            eq("http", list("http", exp).map { bodyKeys(it) }, list("http", got).map { bodyKeys(it) })
            if (exp["flowVariables"] != null) eq("flowVariables", exp["flowVariables"], got["flowVariables"])

            if (!TIMING_SKIP.containsKey(id)) {
                val es = list("shown", exp)
                val gs = list("shown", got)
                eq("shown (order)", es.map { Js.map(it)?.get("node") }, gs.map { Js.map(it)?.get("node") })
                fun gaps(l: List<Any?>) = (1 until l.size).map { Js.number(Js.map(l[it])?.get("t")) - Js.number(Js.map(l[it - 1])?.get("t")) }
                val ge = gaps(es)
                val gg = gaps(gs)
                if (ge.size == gg.size && ge.indices.any { Math.abs(ge[it] - gg[it]) > 50 }) {
                    diffs.add("shown (pace)\n    web: ${canon(es)}\n    sdk: ${canon(gs)}")
                }
            }
            return diffs
        }
    }

    private var savedZone: TimeZone? = null

    @Before
    fun setUp() {
        // ChatState persists on the main dispatcher; nothing runs it here.
        Dispatchers.setMain(StandardTestDispatcher())
        // The visitor's time zone is UTC (fixtures README, "Fixed context").
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        savedZone?.let { TimeZone.setDefault(it) }
        Dispatchers.resetMain()
    }

    private fun pendingReason(fx: Map<String, Any?>): String? {
        val types = (Js.list(fx["covers"]).map { Js.str(it) } + Js.list(Js.map(fx["flow"])?.get("nodes")).map { Js.str(Js.map(it)?.get("type")) }).toSet()
        return types.filter { it in PENDING_TYPES }.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    @Test
    fun fixture() {
        val fx = load(file)
        val pending = pendingReason(fx)
        org.junit.Assume.assumeTrue("pending: $pending", pending == null)
        val outcome = FlowDriver.run(fx)
        if (System.getenv("FLOW_DUMP") != null) println("OUTCOME $id ${canon(outcome.data)}")
        val diffs = outcome.problems.map { "problem: $it" } + compare(id, Js.map(fx["expect"]) ?: emptyMap(), outcome.data)
        if (diffs.isNotEmpty()) throw AssertionError("$id\n  ${diffs.joinToString("\n  ")}")
    }

    @Test
    fun pendingTypesAreStillMissingFromTheRegistry() {
        PENDING_TYPES.forEach { assertThat(NodeHandlerRegistry.hasHandler(it)).isFalse() }
    }
}
