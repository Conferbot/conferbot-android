package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.FlowTrace
import com.conferbot.sdk.core.NodeFlowEngine
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Headless driver for the shared flow-node fixtures (conferbot-react-native
 * scripts/parity/flows, README "Using the fixtures in an SDK"), modelled on
 * RN's __tests__/parity/flowDriver.ts.
 *
 * One case = a fresh ChatState and NodeFlowEngine on the real handlers. The
 * clock is virtual (the engine's FlowClock), the bot API answers from the
 * case's `server.http`, and a fake socket answers `execute-integration` and
 * `initiate-handover` from `server.integration` and `server.handover`. The
 * engine runs on Dispatchers.Unconfined, so everything happens on the test
 * thread as the clock moves. Each visitor action is played through
 * NodeFlowEngine.submitResponse with the answer the SDK's UI would send.
 */
object FlowDriver {
    /** 2026-10-15 10:00 UTC, a Thursday (fixtures README, "Fixed context"). */
    const val BASE = 1_792_058_400_000L

    // ----- the virtual clock (web/env.js) -----
    private class Timer(val id: Int, var at: Long, val fn: () -> Unit, val every: Long)

    class VirtualClock : FlowClock {
        var now = 0L
        private var seq = 0
        private val timers = LinkedHashMap<Int, Timer>()
        val errors = mutableListOf<String>()

        fun reset() {
            now = 0
            seq = 0
            timers.clear()
        }

        private fun add(ms: Long, every: Boolean, fn: () -> Unit): () -> Unit {
            val id = ++seq
            val wait = Math.max(0, ms)
            timers[id] = Timer(id, now + wait, fn, if (every) Math.max(1, wait) else 0)
            return { timers.remove(id) }
        }

        override fun now(): Long = BASE + now
        override suspend fun delay(ms: Long) = suspendCancellableCoroutine<Unit> { cont ->
            val cancel = add(ms, false) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { cancel() }
        }

        override fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit = add(ms, false, fn)
        override fun setInterval(ms: Long, fn: () -> Unit): () -> Unit = add(ms, true, fn)

        /**
         * Run until the engine waits for the visitor: every timeout in time
         * order; when only repeating timers are left (a status poll) it stops.
         * With [waitMs], time runs on - intervals included - that long.
         */
        fun settle(waitMs: Long? = null) {
            val explicit = waitMs != null
            val limit = now + (waitMs ?: 30 * 60 * 1000L)
            repeat(200_000) {
                val next = timers.values.filter { explicit || it.every == 0L }.minWithOrNull(compareBy<Timer>({ it.at }, { it.id }))
                if (next == null || next.at > limit) {
                    if (explicit) now = Math.max(now, limit)
                    return
                }
                now = Math.max(now, next.at)
                if (next.every > 0) next.at = now + next.every else timers.remove(next.id)
                try {
                    next.fn()
                } catch (e: Throwable) {
                    errors.add(e.stackTraceToString())
                }
            }
        }
    }

    // ----- the fake socket (web/fakeSocket.js) -----
    class FakeSocket(private val server: Map<String, Any?>, private val clock: VirtualClock) : FlowSocket {
        val emits = mutableListOf<String>()
        private val listeners = mutableMapOf<String, MutableList<(Map<String, Any?>?) -> Unit>>()

        override fun on(event: String, listener: (Map<String, Any?>?) -> Unit): () -> Unit {
            listeners.getOrPut(event) { mutableListOf() }.add(listener)
            return { listeners[event]?.remove(listener) }
        }

        fun fire(event: String, payload: Map<String, Any?>?) {
            listeners[event]?.toList()?.forEach { it(payload) }
        }

        override fun emit(event: String, payload: Map<String, Any?>): Boolean {
            // The record resend and the room join are transport, not flow events.
            if (event != "response-record" && event != "join-chat-room-visitor") emits.add(event)
            if (event == "execute-integration") {
                val scripted = Js.map(server["integration"])
                val r = scripted?.get(Js.str(payload["nodeId"])) ?: scripted?.get(Js.str(payload["nodeType"])) ?: mapOf("success" to true)
                if (r != "none") {
                    val result = Js.map(r) ?: emptyMap()
                    val delay = (result["delayMs"] as? Number)?.toLong() ?: 50L
                    clock.setTimeout(delay) { fire("integration-result", mapOf("nodeId" to payload["nodeId"]) + result) }
                }
            }
            if (event == "initiate-handover" && server["handover"] == "no-agents") {
                clock.setTimeout(1000) { fire("no-agents-available", mapOf("chatSessionId" to payload["chatSessionId"])) }
            }
            return true
        }

        override fun sendResponseRecord(data: Map<String, Any?>) {}
    }

    // ----- the bot API, scripted by server.http (web/driver.js) -----
    class ScriptedApi(private val server: Map<String, Any?>) : FlowApi {
        val http = mutableListOf<Map<String, Any?>>()
        private val calls = mutableMapOf<String, Int>()

        private fun reply(key: String, body: Any?): ApiReply {
            calls[key] = (calls[key] ?: 0) + 1
            http.add(if (key == "media" || body == null) mapOf("path" to key) else mapOf("path" to key, "body" to body))
            var r: Any? = Js.map(server["http"])?.get(key)
            if (r is List<*>) r = r[Math.min(calls.getValue(key) - 1, r.size - 1)]
            if (r == null && key == "media") r = mapOf("status" to 200, "body" to mapOf("url" to "https://api.test/media/qa-file"))
            if (r == null || r == "network") return ApiReply(false, 0, null, "network")
            val m = Js.map(r) ?: emptyMap()
            val status = (m["status"] as? Number)?.toInt() ?: 200
            return ApiReply(status in 200..299, status, m["body"])
        }

        override suspend fun post(botId: String, path: String, body: Map<String, Any?>, timeoutMs: Long?) = reply(path, Js.parse(Js.stringify(body)))

        override suspend fun upload(botId: String, sessionId: String, visitorId: String, file: UploadFile): UploadResult {
            val r = reply("media", null)
            val url = Js.map(r.body)?.get("url") as? String
            return if (r.ok && !url.isNullOrBlank()) UploadResult(true, url) else UploadResult(false, error = r.error ?: "HTTP ${r.status}")
        }

        override suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?): ApiReply {
            val parsed = body?.let { Js.parse(it) }
            return reply(url, parsed)
        }
    }

    class Outcome(val data: Map<String, Any?>, val problems: List<String>)

    // ----- visitor actions -> the answer the SDK UI sends -----
    private data class Option(val id: String, val label: String)

    private fun optionsOf(ui: NodeUIState?): List<Option> = when (ui) {
        is NodeUIState.SingleChoice -> ui.choices.map { Option(it.id, it.text) }
        is NodeUIState.Dropdown -> ui.options.map { Option(it.id, it.text) }
        is NodeUIState.MultipleChoice -> ui.options.map { Option(it.id, it.text) }
        is NodeUIState.ImageChoice -> ui.images.map { Option(it.id, it.label) }
        else -> emptyList()
    }

    /** The answer for an action; null when the action only changes the card (local). */
    private fun answerFor(a: Map<String, Any?>, ui: NodeUIState?, memo: MutableMap<String, Any?>, nodeId: String, engine: NodeFlowEngine): Any? {
        fun s(k: String) = a[k]?.let { Js.str(it) }
        return when (a["do"]) {
            "text" -> Js.str(a["value"])
            "choose" -> when {
                a.containsKey("buttonId") -> mapOf("buttonId" to a["buttonId"])
                a.containsKey("value") -> mapOf("value" to a["value"])
                a.containsKey("id") -> {
                    val b = optionsOf(ui).firstOrNull { it.id == s("id") } ?: throw IllegalStateException("no shown option with id ${a["id"]}")
                    mapOf("buttonId" to b.id, "label" to b.label, "value" to b.label)
                }
                a.containsKey("index") -> {
                    val i = (a["index"] as Number).toInt()
                    val b = optionsOf(ui).getOrNull(i) ?: throw IllegalStateException("no shown option at index $i")
                    mapOf("buttonId" to b.id, "label" to b.label, "value" to b.label, "index" to i)
                }
                else -> mapOf("action" to "continue")
            }
            "check" -> mapOf("buttonIds" to a["ids"])
            "file" -> mapOf("file" to mapOf("name" to a["name"], "size" to a["size"], "type" to a["type"], "uri" to "file:///tmp/${s("name")}"))
            "skip" -> mapOf("skip" to true)
            "navigateLink" -> mapOf("action" to "link", "index" to a["index"])
            "navigateDone" -> mapOf("action" to "done")
            "date" -> Js.str(a["value"])
            "calendar" -> mapOf("date" to a["date"], "slot" to a["slot"])
            "consent" -> mapOf("agreed" to (a["agreed"] == true))
            "slider", "datetime" -> mapOf("value" to a["value"])
            "rankingMove" -> {
                @Suppress("UNCHECKED_CAST")
                val order = (memo[nodeId] as? MutableList<String>) ?: optionsOf(ui).map { it.id }.toMutableList()
                val i = order.indexOf(s("id"))
                val j = i + Js.number(a["dir"]).toInt()
                if (i >= 0 && j >= 0 && j < order.size) {
                    val t = order[i]
                    order[i] = order[j]
                    order[j] = t
                }
                memo[nodeId] = order
                null
            }
            "rankingSubmit" -> mapOf("order" to (memo[nodeId] ?: optionsOf(ui).map { it.id }))
            "matrix" -> {
                @Suppress("UNCHECKED_CAST")
                val cells = LinkedHashMap((memo[nodeId] as? Map<String, Any?>) ?: emptyMap())
                val multiple = engine.getNode(nodeId)?.data?.get("mode") == "multiple"
                Js.map(a["cells"])?.forEach { (row, cols) ->
                    val list = if (cols is List<*>) cols else listOf(cols)
                    if (multiple) cells[row] = ((cells[row] as? List<*> ?: emptyList<Any?>()) + list).distinct()
                    else cells[row] = list.last()
                }
                memo[nodeId] = cells
                if (a["submit"] == false) null else mapOf("cells" to cells)
            }
            "otpEmail" -> mapOf("action" to "send", "email" to a["email"])
            "otpCode" -> mapOf("action" to "verify", "code" to Js.str(a["code"]))
            "otpSkip" -> mapOf("action" to "skip")
            "addressManual" -> mapOf("action" to "manual", "line1" to a["line1"], "city" to a["city"], "region" to a["region"], "postal" to a["postal"], "country" to a["country"])
            "addressSearch" -> mapOf("action" to "search", "q" to a["q"])
            "addressPick" -> mapOf("action" to "pick", "index" to a["index"])
            "signTyped" -> mapOf("action" to "sign", "method" to "typed", "name" to a["name"], "fullName" to a["fullName"])
            "signEmpty" -> mapOf("action" to "sign", "method" to "drawn", "strokes" to 0)
            "payOpen" -> mapOf("action" to "open")
            "payCancel" -> mapOf("action" to "cancel")
            "wait" -> null
            else -> throw IllegalStateException("unknown action ${a["do"]}")
        }
    }

    // ----- outcome (web/driver.js `outcome`) -----
    private val STRIP = setOf("time", "at", "agreedAt", "options", "choices", "images", "carousel", "_id")

    private fun clean(o: Any?): Any? = when (o) {
        is List<*> -> o.map { clean(it) }
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply { o.forEach { (k, v) -> if (k.toString() !in STRIP) put(k.toString(), clean(v)) } }
        else -> o
    }

    /** Runs one fixture through the engine and returns what it did. */
    fun run(fx: Map<String, Any?>): Outcome {
        val clock = VirtualClock()
        val server = Js.map(fx["server"]) ?: emptyMap()
        val socket = FakeSocket(server, clock)
        val api = ScriptedApi(server)
        val traces = mutableListOf<Pair<FlowTrace, Long>>()
        val problems = mutableListOf<String>()
        val randoms = Js.list(fx["random"]).map { (it as Number).toDouble() }.toMutableList()
        val customizations = Js.map(fx["customizations"]) ?: emptyMap()

        ChatState.reset()
        ChatState.setAutoPersist(false)
        ChatState.initialize("qa-session", "qa-visitor", "qa-bot", null)
        ChatState.redactSensitive = true

        val engine = NodeFlowEngine(
            socket,
            NodeFlowEngine.Config(
                dispatcher = Dispatchers.Unconfined,
                clock = { clock },
                api = api,
                customizations = { customizations },
                pageUrl = "https://shop.example/pricing?utm_source=newsletter&utm_campaign=fall",
                pageReferrer = "https://www.google.com/",
                visitorTimeZone = (fx["visitorTimeZone"] as? String) ?: "UTC",
                random = { if (randoms.size > 1) randoms.removeAt(0) else randoms.firstOrNull() ?: 0.5 },
                onTrace = { traces.add(it to clock.now) }
            )
        )
        val flow = Js.map(fx["flow"]) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        engine.loadFlow(Js.list(flow["nodes"]) as List<Map<String, Any?>>, Js.list(flow["edges"]) as List<Map<String, Any?>>)
        engine.start()
        clock.settle()

        val memo = mutableMapOf<String, Any?>()
        Js.list(fx["inputs"]).forEachIndexed { i, raw ->
            val a = Js.map(raw) ?: emptyMap()
            val at = engine.currentNode
            if (a["do"] != "serverEvent") {
                if (a["at"] != null && a["at"] != at) problems.add("input $i: waiting at $at, fixture expected ${a["at"]}")
                if (!engine.isWaitingForInput && a["do"] != "wait") problems.add("input $i (${a["do"]}): engine is not waiting (at $at)")
            }
            try {
                if (a["do"] == "serverEvent") socket.fire(Js.str(a["event"]), Js.map(a["payload"]))
                else answerFor(a, engine.currentUIState.value, memo, at ?: "", engine)?.let { engine.submitResponse(it) }
            } catch (e: Exception) {
                problems.add("input $i (${a["do"]}): ${e.message}")
            }
            val waitMs = (a["waitMs"] as? Number)?.toLong()
            clock.settle(waitMs)
            if (waitMs != null) clock.settle()
        }

        val shownAt = linkedMapOf<String, Long>()
        traces.forEach { (e, t) -> if (e is FlowTrace.Shown && !shownAt.containsKey(e.node)) shownAt[e.node] = t }
        val record = ChatState.record.value
        val crash = traces.firstOrNull { it.first is FlowTrace.Crash }?.first as? FlowTrace.Crash
        val endAt = traces.firstOrNull { it.first is FlowTrace.End }?.second
        val flowVariables = ChatState.variables.value.filterKeys { !it.startsWith("_") }

        val answers = record.filter { r ->
            if (r.shape.isNotEmpty()) r.shape.startsWith("user") else (r.type ?: "").startsWith("user") && r.data.isEmpty() && !r.fields.containsKey("position")
        }.map { r ->
            val m = linkedMapOf<String, Any?>("id" to r.id)
            if (r.shape.isNotEmpty()) m["shape"] = r.shape
            if (r.type != null) m["type"] = r.type
            if (r.text != null) m["text"] = r.text
            m.putAll(r.fields)
            if (r.data.isNotEmpty() && !m.containsKey("data")) m["data"] = r.data
            clean(m)
        }

        val end: Map<String, Any?> = when {
            crash != null -> mapOf("ended" to false, "crashed" to true, "at" to crash.node, "reason" to crash.reason)
            engine.isEnded -> mapOf("ended" to true, "t" to endAt)
            else -> mapOf("ended" to false, "waitingAt" to engine.currentNode, "type" to engine.currentNode?.let { engine.getNode(it)?.type })
        }

        val outcome = linkedMapOf<String, Any?>(
            "path" to traces.mapNotNull { (e, t) -> (e as? FlowTrace.Enter)?.let { mapOf("node" to it.node, "type" to it.type, "via" to it.via, "t" to t) } },
            "shown" to shownAt.map { (node, t) -> mapOf("node" to node, "t" to t) },
            "rendered" to record.filter { it.type != null && it.shape.isEmpty() && !it.fields.containsKey("source") && it.fields.containsKey("renderedText") }
                .map { mapOf("node" to it.id, "html" to it.fields["renderedText"], "plain" to FlowText.plainText(it.fields["renderedText"])) },
            "ports" to traces.mapNotNull { (e, _) -> (e as? FlowTrace.Port)?.let { mapOf("node" to it.node, "handle" to it.handle, "wired" to it.wired) } },
            "answers" to answers,
            "variables" to ChatState.answerVariables.value.map { mapOf("key" to it.key, "value" to it.value, "nodeId" to it.nodeId) },
            "errors" to traces.mapNotNull { (e, _) -> (e as? FlowTrace.Error)?.let { mapOf("node" to it.node, "message" to it.message) } },
            "http" to api.http,
            "emits" to socket.emits,
            "unsupported" to traces.mapNotNull { (e, _) -> (e as? FlowTrace.Unsupported)?.let { mapOf("node" to it.node, "type" to it.type) } },
            "end" to end,
            "transcript" to ChatState.transcript.value.map { mapOf("by" to it.by, "message" to it.message) }
        )
        if (flowVariables.isNotEmpty()) outcome["flowVariables"] = flowVariables
        problems.addAll(clock.errors.map { "uncaught: $it" })
        engine.stop()
        return Outcome(outcome, problems)
    }
}
