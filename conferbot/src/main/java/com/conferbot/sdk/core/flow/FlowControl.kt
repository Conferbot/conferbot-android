package com.conferbot.sdk.core.flow

/**
 * Loops and sub-flows, the pure half - ported unchanged from the web widget
 * (conferbot-widget/src/ts/chat/flowControl.ts) by way of RN
 * src/core/flow/flowControl.ts. The engine runs them (NodeFlowEngine
 * runFlowControl).
 */
object FlowControl {
    const val LOOP_TYPE = "loop-node"
    const val SUBFLOW_TYPE = "subflow-node"
    const val RUN_SUBFLOW_TYPE = "run-subflow-node"
    const val SUBFLOW_RETURN_TYPE = "subflow-return-node"
    val FLOW_CONTROL_TYPES = setOf(LOOP_TYPE, SUBFLOW_TYPE, RUN_SUBFLOW_TYPE, SUBFLOW_RETURN_TYPE)

    const val LOOP_BODY_HANDLE = "source-loop"
    const val LOOP_DONE_HANDLE = "source-done"
    const val RUN_ERROR_HANDLE = "source-error"

    const val LOOP_DEFAULT_CAP = 20
    const val LOOP_HARD_CAP = 100
    const val SUBFLOW_MAX_DEPTH = 5
    private val VAR_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    class LoopState(var i: Int, val total: Int, val items: List<Any?>?, val capped: Boolean)

    data class CallFrame(val runNodeId: String, val subflowId: String, val name: String, val outputs: List<Pair<String, String>>, val depth: Int)

    class State {
        val loops = mutableMapOf<String, LoopState>()
        val stack = mutableListOf<CallFrame>()
    }

    private fun clean(v: Any?) = Js.str(v).replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()

    /** Fill {var} placeholders from the lookup ("" for unknown ones). */
    fun fillText(text: Any?, lookup: VariableLookup): String =
        Regex("\\$?\\{(\\w+)}").replace(Js.str(text)) { m ->
            val v = lookup(m.groupValues[1])
            when (v) {
                null -> ""
                is Map<*, *>, is List<*> -> Js.stringify(v)
                else -> Js.str(v)
            }
        }

    /** The loop's variable name: data.variable, else "loop". */
    fun loopVarName(data: Map<String, Any?>?): String = clean(data?.get("variable")).let { if (VAR_RE.matches(it)) it else "loop" }

    /** The cap: data.maxIterations, 1..100, default 20. */
    fun loopCap(data: Map<String, Any?>?): Int {
        val n = Math.floor(Js.number(data?.get("maxIterations") ?: Double.NaN))
        if (!Js.isFinite(n) || n < 1) return LOOP_DEFAULT_CAP
        return Math.min(LOOP_HARD_CAP, n.toInt())
    }

    /** A list from a variable's value: an array, a JSON array, or a comma / new-line separated text. */
    fun listOf(value: Any?): List<Any?> {
        if (value is List<*>) return value
        if (value == null || value is Map<*, *>) return emptyList()
        val t = Js.str(value).trim()
        if (t.isEmpty()) return emptyList()
        if (t.startsWith("[")) (Js.parse(t) as? List<*>)?.let { return it }
        return t.split(Regex("\\s*(?:,|\n)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** How many rounds: a number, or a {var} holding one. */
    fun loopCount(data: Map<String, Any?>?, lookup: VariableLookup): Int {
        val raw = data?.get("count")
        val n = if (raw is Number) raw.toDouble() else Js.number(fillText(raw, lookup).trim())
        return if (Js.isFinite(n) && n > 0) Math.floor(n).toInt() else 0
    }

    /** First arrival: how many rounds and over which items. */
    fun startLoop(data: Map<String, Any?>?, lookup: VariableLookup): LoopState {
        val cap = loopCap(data)
        if (data?.get("mode") == "each") {
            val key = clean(data["listVariable"]).replace(Regex("^\\{|}$"), "")
            val items = listOf(if (key.isNotEmpty()) lookup(key) else null)
            return LoopState(0, Math.min(items.size, cap), items.take(cap), items.size > cap)
        }
        val n = loopCount(data, lookup)
        return LoopState(0, Math.min(n, cap), null, n > cap)
    }

    /** An item as variable text ({x_item}): plain values as they are, objects as JSON. */
    fun itemValue(item: Any?): Any? = if (item is Map<*, *> || item is List<*>) Js.stringify(item) else item

    class Trail(val iteration: Int, val total: Int, val item: Any? = null, val done: Boolean = false, val capped: Boolean = false, val variable: String) {
        fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>("iteration" to iteration, "total" to total).apply {
            if (item != null) put("item", item)
            if (done) put("done", true)
            if (capped) put("capped", true)
            put("variable", variable)
        }
    }

    class LoopStep(val handle: String, val vars: Map<String, Any?>, val trail: Trail)

    /** The loop step: start the next round or leave by Done. Mutates `state`. */
    fun stepLoop(nodeId: String, data: Map<String, Any?>?, state: State, lookup: VariableLookup): LoopStep {
        val name = loopVarName(data)
        val s = state.loops.getOrPut(nodeId) { startLoop(data, lookup) }
        if (s.i < s.total) {
            s.i += 1
            val item: Any? = if (s.items != null) s.items[s.i - 1] else s.i
            return LoopStep(
                LOOP_BODY_HANDLE,
                linkedMapOf("${name}_index" to s.i, "${name}_item" to itemValue(item), "${name}_count" to s.total),
                Trail(s.i, s.total, if (s.items != null) itemValue(item) else null, variable = name)
            )
        }
        state.loops.remove(nodeId)
        return LoopStep(LOOP_DONE_HANDLE, emptyMap(), Trail(s.i, s.total, done = true, capped = s.capped, variable = name))
    }

    /** "Loop 3 of 5 · Apples", "Loop done · 5 rounds", for trails. */
    fun loopTrailText(t: Trail): String {
        if (t.done) {
            val rounds = "${t.total} round${if (t.total == 1) "" else "s"}"
            return if (t.capped) "Loop stopped at the safety cap · $rounds" else "Loop done · $rounds"
        }
        val item = if (t.item == null || t.item == "") "" else " · ${Js.str(t.item).take(60)}"
        return "Loop ${t.iteration} of ${t.total}$item"
    }

    fun subflowName(data: Map<String, Any?>?): String = clean(data?.get("name")).ifEmpty { "Sub-flow" }

    class SubflowCall(val ok: Boolean, val error: String? = null, val startId: String? = null, val name: String? = null, val vars: Map<String, Any?> = emptyMap(), val frame: CallFrame? = null)

    /**
     * Run sub-flow: find the start, check depth / recursion, fill the inputs.
     * Pushes the call frame onto `state.stack` when it can run.
     */
    fun callSubflow(runNodeId: String, runData: Map<String, Any?>?, nodes: List<Pair<String, Pair<String, Map<String, Any?>>>>, state: State, lookup: VariableLookup): SubflowCall {
        val d = runData ?: emptyMap()
        val target = nodes.firstOrNull { it.first == Js.str(d["subflowId"]) && it.second.first == SUBFLOW_TYPE } ?: return SubflowCall(false, "missing")
        val name = subflowName(target.second.second)
        if (state.stack.size >= SUBFLOW_MAX_DEPTH) return SubflowCall(false, "depth", name = name)
        if (state.stack.any { it.subflowId == target.first }) return SubflowCall(false, "recursion", name = name)
        val vars = linkedMapOf<String, Any?>()
        Js.list(d["inputs"]).forEach { m ->
            val mm = Js.map(m)
            val key = clean(mm?.get("name"))
            if (VAR_RE.matches(key)) vars[key] = fillText(mm?.get("value"), lookup)
        }
        val outputs = Js.list(d["outputs"]).mapNotNull { m ->
            val mm = Js.map(m)
            val from = clean(mm?.get("name"))
            val to = clean(mm?.get("variable")).ifEmpty { from }
            if (VAR_RE.matches(from) && VAR_RE.matches(to)) from to to else null
        }
        val frame = CallFrame(runNodeId, target.first, name, outputs, state.stack.size + 1)
        state.stack.add(frame)
        return SubflowCall(true, startId = target.first, name = name, vars = vars, frame = frame)
    }

    class SubflowReturn(val frame: CallFrame, val vars: Map<String, Any?>)

    /** The sub-flow ended: pop the frame and read its outputs. null when not in one. */
    fun returnFromSubflow(state: State, lookup: VariableLookup): SubflowReturn? {
        if (state.stack.isEmpty()) return null
        val frame = state.stack.removeAt(state.stack.size - 1)
        val vars = linkedMapOf<String, Any?>()
        frame.outputs.forEach { (from, to) -> vars[to] = lookup(from) ?: "" }
        return SubflowReturn(frame, vars)
    }
}
