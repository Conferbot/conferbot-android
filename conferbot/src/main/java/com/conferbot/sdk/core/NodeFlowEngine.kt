package com.conferbot.sdk.core

import android.os.Build
import com.conferbot.sdk.core.analytics.ChatAnalytics
import com.conferbot.sdk.core.flow.ApiReply
import com.conferbot.sdk.core.flow.CoroutineFlowClock
import com.conferbot.sdk.core.flow.FlowApi
import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.flow.FlowClock
import com.conferbot.sdk.core.flow.FlowContext
import com.conferbot.sdk.core.flow.FlowContextElement
import com.conferbot.sdk.core.flow.FlowControl
import com.conferbot.sdk.core.flow.FlowEdge
import com.conferbot.sdk.core.flow.FlowNode
import com.conferbot.sdk.core.flow.FlowSocket
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.HttpFlowApi
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.UploadFile
import com.conferbot.sdk.core.flow.UploadResult
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.services.SocketClient
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** What the engine did, step by step (the flow-node spec's `expect`). */
sealed class FlowTrace {
    data class Enter(val node: String, val type: String, val via: String) : FlowTrace()
    data class Shown(val node: String) : FlowTrace()
    data class Port(val node: String, val handle: String, val wired: Boolean) : FlowTrace()
    data class Unsupported(val node: String, val type: String) : FlowTrace()
    data class Error(val node: String, val message: String) : FlowTrace()
    object End : FlowTrace()
    data class Crash(val node: String, val reason: String) : FlowTrace()
}

/**
 * Runs a bot flow the way the web widget does. The reference is the shared
 * flow-node spec (conferbot-react-native scripts/parity/flows,
 * nodes.spec.json "engine"), made by running conferbot-widget's own code;
 * FlowFixturesTest runs every fixture through this engine. Ported from the
 * React Native SDK's src/core/NodeFlowEngine.ts.
 *
 * - start: the FIRST welcome-node in nodes[].
 * - next: a step that does not branch leaves by the FIRST edge from it,
 *   whatever its handle. No edge, or a target that does not exist: the chat ends.
 * - port: a branching step leaves by an exact handle. No such edge: the chat
 *   ends - never another edge. A wired edge to a missing node stops the chat
 *   where it is (the web throws). Rich steps may name a fallback handle,
 *   taken when only it is wired (web goPort).
 * - unknown step types (and a welcome-node reached mid-flow) show nothing and
 *   continue along their first edge on the next tick.
 * - jump: to the target node; a missing target ends the chat.
 * - end: a sub-flow call returns to its caller; otherwise the chat is over
 *   ("chat-flow-completed").
 * - pacing: typing dots before each displayed step for paceFor().
 */
class NodeFlowEngine internal constructor(
    private val socket: FlowSocket?,
    private val config: Config
) : com.conferbot.sdk.core.livechat.HandoverFlow {

    /** How the engine runs: the SDK's defaults, or a test's clock, API and socket. */
    class Config(
        val dispatcher: CoroutineDispatcher = Dispatchers.Main,
        val clock: ((CoroutineScope) -> FlowClock)? = null,
        val api: FlowApi? = null,
        /** The bot's customizations (redactSensitive, chatLeaveMessage...). */
        val customizations: () -> Map<String, Any?> = { emptyMap() },
        /** The page the chat runs on, when the host app has one (hidden-fields). */
        val pageUrl: String? = null,
        val pageReferrer: String? = null,
        /** The visitor's IANA time zone (default: the device's). */
        val visitorTimeZone: String? = null,
        val random: (() -> Double)? = null,
        /** A fixed typing delay (ms) before each displayed step; null is the web's pacing. */
        val typingDelay: Long? = null,
        /** How long an image or video step waits for its media at most (web: 4000). */
        val mediaAdvanceMs: Long = 4000,
        /** Steps run without the visitor before the engine stops (a runaway loop guard). */
        val maxNodesPerCycle: Int = 500,
        val nodeProcessingTimeoutMs: Long = 90_000,
        /** Every step entered, port taken, step passed and the end. */
        val onTrace: ((FlowTrace) -> Unit)? = null
    )

    /**
     * The SDK's engine on [socketClient]; [apiOrigin] is the server the bot
     * API calls go to (the socket server), [customizations] the bot's.
     */
    constructor(
        socketClient: SocketClient,
        apiOrigin: () -> String = { com.conferbot.sdk.utils.ConferBotEndpoints.socketUrl },
        customizations: () -> Map<String, Any?> = { emptyMap() },
        readFile: (String) -> ByteArray? = { null }
    ) : this(SocketClientFlowSocket(socketClient), Config(api = HttpFlowApi(apiOrigin, readFile), customizations = customizations))

    private val scope = CoroutineScope(config.dispatcher + SupervisorJob())
    private val clock: FlowClock = config.clock?.invoke(scope) ?: CoroutineFlowClock(scope)
    private val api: FlowApi = config.api ?: HttpFlowApi({ com.conferbot.sdk.utils.ConferBotEndpoints.socketUrl })

    // Current UI state to render
    private val _currentUIState = MutableStateFlow<NodeUIState?>(null)
    val currentUIState: StateFlow<NodeUIState?> = _currentUIState.asStateFlow()

    // Loading state for typing indicator
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Flow is complete
    private val _isFlowComplete = MutableStateFlow(false)
    val isFlowComplete: StateFlow<Boolean> = _isFlowComplete.asStateFlow()

    /**
     * The chat's flow reached its end (not a sub-flow's return): the
     * conversation is over (the post-chat rating, then the redirect).
     */
    var onFlowEnd: ((reason: String) -> Unit)? = null

    /**
     * Bot lines besides a step's own card (a greeting, an answer, a refused
     * answer's reason, the team's fallback): the chat draws each as a bubble.
     */
    private val _botMessages = MutableSharedFlow<NodeUIState>(extraBufferCapacity = 64)
    val botMessages: SharedFlow<NodeUIState> = _botMessages.asSharedFlow()

    /**
     * The visitor's lines a step card's answer made (the step's own reply:
     * "Agreed to Terms", "Verified: a@b.io", "Paid $5.00"), for the chat to
     * draw as the visitor's bubbles. Only answers sent with [submitStepResponse].
     */
    private val _visitorLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val visitorLines: SharedFlow<String> = _visitorLines.asSharedFlow()

    /** A visitor's line and the step it answered (what the bot's tapback and "Edit" read). */
    data class VisitorLine(val text: String, val nodeId: String, val type: String)

    /** The same lines as [visitorLines], each with the step it answered. */
    private val _visitorAnswers = MutableSharedFlow<VisitorLine>(extraBufferCapacity = 64)
    val visitorAnswers: SharedFlow<VisitorLine> = _visitorAnswers.asSharedFlow()

    /**
     * A question the visitor may go back to (web index.ts _noteQuestionPoint):
     * kept each time a step that waits for an answer is entered, before it is
     * drawn, with a copy of the conversation's state as it was.
     */
    class QuestionPoint internal constructor(
        /** Unique per point (a step asked twice has two). */
        val id: Int,
        val nodeId: String,
        val type: String,
        /** Steps entered before this question. */
        val stepsLen: Int,
        /** The record's length before the question. */
        val recordLen: Int,
        internal val snap: ChatState.Snapshot,
        internal val flowControl: FlowControl.State
    )

    /** Questions asked, newest last, for "Edit" (only the recent past). */
    private val points = mutableListOf<QuestionPoint>()
    private var pointSeq = 0

    /** The bot's bookings (the bot data's calendarBookings): a calendar's taken slots. */
    @Volatile
    var calendarBookings: List<Map<String, Any?>> = emptyList()
        private set

    /** The bot's bookings from the bot data (a calendar greys out taken slots). */
    @Suppress("UNCHECKED_CAST")
    fun setCalendarBookings(bookings: Any?) {
        calendarBookings = (bookings as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
    }

    // Flow definition
    private val nodes = LinkedHashMap<String, FlowNode>()
    private var nodeList: List<FlowNode> = emptyList()
    private var edges: List<FlowEdge> = emptyList()
    private var startNodeId: String? = null

    // Engine state
    private var currentNodeId: String? = null
    private var waiting = false
    private var ended = false
    private val steps = mutableListOf<FlowNode>()
    /** Bumped whenever the flow moves: a stale timer or reply does nothing. */
    private var moveToken = 0
    /** The step whose "shown" was already reported. */
    private var shownToken = -1
    private val mediaWaiters = mutableMapOf<String, () -> Unit>()
    private var flowControl = FlowControl.State()
    private var nodesProcessedInCycle = 0

    /** True while an interactive node is awaiting typed text. */
    val isAwaitingInput: Boolean
        get() = currentNodeId != null && waiting && _currentUIState.value is NodeUIState.TextInput

    /** Waiting for the visitor on the current step. */
    val isWaitingForInput: Boolean get() = waiting

    /** The step the flow is on. */
    val currentNode: String? get() = currentNodeId

    /** Whether the chat has ended (the flow reached its end). */
    val isEnded: Boolean get() = ended

    fun getNode(nodeId: String): FlowNode? = nodes[nodeId]

    companion object {
        private const val TAG = "NodeFlowEngine"

        /** Steps that draw something, after the typing dots (web DISPLAY_NODE_TYPES). */
        val DISPLAY_NODE_TYPES = setOf(
            "two-choices-node", "three-choices-node", "select-option-node", "user-rating-node", "quiz-node", "user-input-node",
            "user-range-node", "welcome-node", "message-node", "image-node", "video-node", "audio-node", "file-node",
            "ask-name-node", "ask-email-node", "ask-phone-number-node", "ask-number-node", "ask-url-node", "ask-file-node",
            "ask-location-node", "ask-custom-question-node", "ask-multiple-questions-node", "calendar-node",
            "n-select-option-node", "n-check-options-node", "n-choices-node", "image-choice-node", "carousel-node",
            "rating-choice-node", "yes-or-no-choice-node", "opinion-scale-choice-node", "user-redirect-node", "html-node",
            "navigate-node", "dynamic-choices-node", "consent-node", "product-card-node", "slider-node", "datetime-node",
            "ranking-node", "otp-node", "address-node", "matrix-node", "ai-extract-node", "ai-route-node", "kb-answer-node",
            "signature-node", "payment-node"
        )

        /** Question steps that register data.answerVariable when shown (web _nodeCategorizeHelper). */
        private val REGISTERS_ANSWER = setOf(
            "ask-name-node", "ask-email-node", "ask-phone-number-node", "ask-number-node", "ask-url-node", "ask-location-node",
            "ask-custom-question-node", "ask-file-node", "calendar-node", "n-select-option-node", "n-check-options-node",
            "n-choices-node", "image-choice-node", "rating-choice-node", "yes-or-no-choice-node", "opinion-scale-choice-node",
            "carousel-node", "dynamic-choices-node", "consent-node", "product-card-node", "slider-node", "datetime-node",
            "ranking-node", "otp-node", "address-node", "matrix-node", "ai-extract-node", "ai-route-node", "kb-answer-node",
            "signature-node", "payment-node"
        )

        /** Steps whose media decides when the flow moves on (load, or the cap). */
        private val MEDIA_TYPES = setOf("image-node", "video-node")

        /** A node as the builder saved it: its type from node.type first (data.type is a legacy input's kind). */
        @Suppress("UNCHECKED_CAST")
        fun nodeOf(raw: Map<String, Any?>): FlowNode? {
            val id = raw["id"]?.let { Js.str(it) } ?: raw["_id"]?.let { Js.str(it) } ?: return null
            val data = (raw["data"] as? Map<String, Any?>) ?: emptyMap()
            val type = (raw["type"] as? String)?.takeIf { it.isNotEmpty() } ?: (data["type"] as? String) ?: ""
            return FlowNode(id, type, data, raw)
        }

        fun edgeOf(raw: Map<String, Any?>, index: Int): FlowEdge? {
            val source = raw["source"]?.let { Js.str(it) } ?: return null
            val target = raw["target"]?.let { Js.str(it) } ?: ""
            return FlowEdge(raw["id"]?.let { Js.str(it) } ?: "edge-$index", source, target, raw["sourceHandle"] as? String, raw)
        }
    }

    // ========================================
    // FLOW SETUP
    // ========================================

    /**
     * Initialize the flow engine with bot data from server
     */
    fun initialize(
        chatSessionId: String,
        visitorId: String,
        botId: String,
        workspaceId: String?,
        stepsData: List<Map<String, Any>>,
        edgesData: List<Map<String, Any>>
    ) {
        ChatState.initialize(chatSessionId, visitorId, botId, workspaceId)
        ChatState.setSteps(stepsData)
        loadFlow(stepsData, edgesData)
    }

    /** Loads a flow (the saved nodes and edges). */
    fun loadFlow(nodesData: List<Map<String, Any?>>, edgesData: List<Map<String, Any?>>) {
        nodes.clear()
        nodeList = nodesData.mapNotNull { nodeOf(it) }
        nodeList.forEach { nodes[it.id] = it }
        edges = edgesData.mapIndexedNotNull { i, e -> edgeOf(e, i) }
        startNodeId = findStartNode()
    }

    /**
     * The first welcome-node in nodes[] starts the chat (web index.ts
     * _findAndDisplayNode). A flow without one starts at a node no edge leads
     * to, else the first node.
     */
    private fun findStartNode(): String? {
        nodeList.firstOrNull { it.type == "welcome-node" }?.let { return it.id }
        val targets = edges.map { it.target }.toSet()
        return nodeList.firstOrNull { it.id !in targets }?.id ?: nodeList.firstOrNull()?.id
    }

    // ========================================
    // FLOW EXECUTION
    // ========================================

    /**
     * Start processing from the start node
     */
    fun start() {
        val start = startNodeId?.let { nodes[it] }
        if (start == null) {
            _isFlowComplete.value = true
            return
        }
        resetFlow()
        scope.launch { enter(start, "start") }
    }

    private fun resetFlow() {
        steps.clear()
        points.clear()
        ended = false
        _isFlowComplete.value = false
        nodesProcessedInCycle = 0
        flowControl = FlowControl.State()
        mediaWaiters.clear()
    }

    private fun applyCustomizations() {
        ChatState.redactSensitive = config.customizations()["redactSensitive"] != false
    }

    private fun lookup(key: String) = ChatState.lookupVariable(key)

    private fun now() = clock.now()

    /** Enter a step: record it, pace it, run its handler. */
    private suspend fun enter(node: FlowNode, via: String) {
        if (ended) return
        nodesProcessedInCycle++
        if (nodesProcessedInCycle > config.maxNodesPerCycle) {
            handleError("Max nodes per cycle exceeded (${config.maxNodesPerCycle})", node.id)
            return
        }
        applyCustomizations()

        // A question: what "Edit" goes back to, before it is drawn.
        if (com.conferbot.sdk.core.delight.Delight.waitsForAnswer(node.type)) notePoint(node)

        val token = ++moveToken
        currentNodeId = node.id
        waiting = false
        _isProcessing.value = true
        _errorMessage.value = null
        val index = nodeList.indexOf(node)
        if (index >= 0) ChatState.setCurrentIndex(index)
        steps.add(node)
        trace(FlowTrace.Enter(node.id, node.type, via))
        ChatAnalytics.trackNodeEntry(node.id, node.type, node.data["label"]?.toString() ?: node.type)

        // The step on the record, with its text as the visitor saw it (web _nodeRecord)
        val rendered = FlowText.renderedTextFor(node.type, node.data, ::lookup)
        val top = node.raw.filterKeys { it != "id" && it != "_id" && it != "type" && it != "data" } +
            (if (rendered != null) mapOf("renderedText" to rendered) else emptyMap())
        ChatState.appendRecord(RecordEntry(id = node.id, shape = "", type = node.type, time = FlowCalendar.iso(now()), data = node.data.toMutableMap(), fields = top))

        // A welcome reached again is not a display step there: passed.
        if (node.type == "welcome-node" && via != "start") {
            passUnknown(node, token)
            return
        }
        if (node.type in FlowControl.FLOW_CONTROL_TYPES) {
            runFlowControl(node, token)
            return
        }
        val handler = NodeHandlerRegistry.getHandler(node.type)
        if (handler == null) {
            passUnknown(node, token)
            return
        }

        if (node.type in DISPLAY_NODE_TYPES) {
            // A message with no text or an image with no picture: straight on.
            if (FlowText.isEmptyContentStep(node.type, node.data)) {
                next(node, token)
                return
            }
            registerAnswerVariables(node)
            val previous = steps.getOrNull(steps.size - 2)
            val pace = config.typingDelay ?: FlowText.paceFor(previous?.data, node.data, FlowText.followsAutoAdvanceStep(previous?.type))
            if (pace > 0) {
                _isProcessing.value = true
                clock.delay(pace)
                if (token != moveToken || ended) return
            }
            shownToken = token
            trace(FlowTrace.Shown(node.id))
        }

        val result = try {
            runHandler(handler, node, via, token) { h -> h.process(node.data, node.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleError("Error processing node: ${e.message}", node.id)
            return
        }
        if (token != moveToken || ended) return
        handleNodeResult(result, node, token)
    }

    private fun copyControl(f: FlowControl.State): FlowControl.State = FlowControl.State().also { c ->
        f.loops.forEach { (k, l) -> c.loops[k] = FlowControl.LoopState(l.i, l.total, l.items, l.capped) }
        c.stack.addAll(f.stack)
    }

    private fun notePoint(node: FlowNode) {
        points.add(QuestionPoint(++pointSeq, node.id, node.type, steps.size, ChatState.record.value.size, ChatState.snapshot(), copyControl(flowControl)))
        while (points.size > com.conferbot.sdk.core.delight.Delight.MAX_QUESTION_POINTS) points.removeAt(0)
    }

    /** The questions asked so far that can still be gone back to, oldest first. */
    fun questionPoints(): List<QuestionPoint> = points.toList()

    /**
     * The step types run since a question (it excluded); "agent" when a
     * person or the AI agent has been in the conversation since (what they
     * saw stays). Web index.ts typesSince.
     */
    fun typesSince(point: QuestionPoint): List<String> {
        val types = steps.drop(point.stepsLen + 1).map { it.type }
        val agent = ChatState.record.value.drop(point.recordLen).any { (it.type ?: "").startsWith("agent") || it.shape.startsWith("agent") }
        return if (agent) types + "agent" else types
    }

    /**
     * "Edit": the conversation as it was when the question was asked, and the
     * question asked again exactly as the forward path asks it (web index.ts
     * _rewindToQuestion). False when the point is gone or the chat ended.
     */
    fun rewindTo(pointId: Int): Boolean {
        val i = points.indexOfFirst { it.id == pointId }
        val point = points.getOrNull(i) ?: return false
        val node = nodes[point.nodeId] ?: return false
        if (ended || _isFlowComplete.value) return false
        moveToken++
        ChatState.restore(point.snap)
        while (steps.size > point.stepsLen) steps.removeAt(steps.size - 1)
        flowControl = copyControl(point.flowControl)
        while (points.size > i) points.removeAt(points.size - 1)
        mediaWaiters.clear()
        waiting = false
        nodesProcessedInCycle = 0
        _errorMessage.value = null
        sendResponseToServer()
        scope.launch { enter(node, "rewind") }
        return true
    }

    /** A question step registers {nodeId, key, null} when shown. */
    private fun registerAnswerVariables(node: FlowNode) {
        val d = node.data
        if (node.type == "ask-multiple-questions-node") {
            Js.list(d["questions"]).forEachIndexed { i, q ->
                val key = Js.map(q)?.get("answerVariable")?.let { Js.str(it) }
                if (!key.isNullOrEmpty()) ChatState.registerAnswerVariable("${node.id}$i", key)
            }
            return
        }
        val key = d["answerVariable"]?.let { Js.str(it) }
        if (node.type in REGISTERS_ANSWER && !key.isNullOrEmpty()) ChatState.registerAnswerVariable(node.id, key)
    }

    /** Runs a handler call with the step's context, under the step timeout. */
    private suspend fun runHandler(handler: NodeHandler, node: FlowNode, via: String, token: Int, call: suspend (NodeHandler) -> NodeResult): NodeResult {
        val ctx = StepContext(node, via, token)
        val done = CompletableDeferred<NodeResult>()
        val cancelTimer = clock.setTimeout(config.nodeProcessingTimeoutMs) {
            done.complete(NodeResult.Error("Node processing timed out after ${config.nodeProcessingTimeoutMs}ms", shouldProceed = false))
        }
        val job = scope.launch(FlowContextElement(ctx)) {
            try {
                done.complete(call(handler))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                done.completeExceptionally(e)
            }
        }
        try {
            return done.await()
        } finally {
            cancelTimer()
            if (!job.isCompleted && done.isCompleted && !done.isCancelled) job.cancel()
        }
    }

    private suspend fun handleNodeResult(result: NodeResult, node: FlowNode, token: Int) {
        when (result) {
            is NodeResult.DisplayUI -> handleDisplayUI(result.uiState, node, token)
            is NodeResult.Proceed -> handleProceed(result.targetPort, result.fallbackPort, node, token)
            is NodeResult.DelayedProceed -> {
                clock.delay(result.delayMs)
                if (token != moveToken || ended) return
                handleProceed(result.targetPort, result.fallbackPort, node, token)
            }
            is NodeResult.JumpTo -> {
                _isProcessing.value = false
                ChatAnalytics.trackNodeExit(node.id, "proceeded")
                sendResponseToServer()
                jump(result.targetNodeId, token)
            }
            is NodeResult.Invalid -> handleInvalid(result.message, node, token, result.uiState, result.inCard)
            is NodeResult.End -> end("completed")
            is NodeResult.Error -> {
                _isProcessing.value = false
                _errorMessage.value = result.message
                ChatAnalytics.trackNodeExit(node.id, "error")
                if (result.shouldProceed) {
                    sendResponseToServer()
                    next(node, token)
                }
            }
        }
    }

    /** Whether the step waits for the visitor once drawn. */
    private fun awaitsInput(node: FlowNode): Boolean = NodeTypes.requiresUserInteraction(node.type)

    /** [ui] stamped with its step (see NodeUIState.step); [error] the card's refusal. */
    private fun stamped(ui: NodeUIState, node: FlowNode, token: Int, error: String? = null): NodeUIState =
        ui.withStep(NodeUIState.StepInfo(type = node.type, node = node.data, extra = ui.step?.extra ?: emptyMap(), error = error, visit = token, nodeId = node.id))

    private suspend fun handleDisplayUI(ui: NodeUIState, node: FlowNode, token: Int) {
        _currentUIState.value = stamped(ui, node, token)
        _isProcessing.value = false
        if (shownToken != token && node.type in DISPLAY_NODE_TYPES) {
            shownToken = token
            trace(FlowTrace.Shown(node.id))
        }
        if (awaitsInput(node)) {
            waiting = true
            return
        }
        // Shown and moving on by itself
        ChatAnalytics.trackNodeExit(node.id, "proceeded")
        sendResponseToServer()
        if (node.type in MEDIA_TYPES) {
            waitForMedia(node.id, config.mediaAdvanceMs)
            if (token != moveToken || ended) return
        }
        next(node, token)
    }

    /** Resolves when the media loads ([mediaSettled]) or after [capMs]. */
    private suspend fun waitForMedia(nodeId: String, capMs: Long) {
        val done = CompletableDeferred<Unit>()
        var cancel: () -> Unit = {}
        val finish = {
            cancel()
            mediaWaiters.remove(nodeId)
            done.complete(Unit)
            Unit
        }
        mediaWaiters[nodeId] = finish
        cancel = clock.setTimeout(Math.max(0, capMs)) { finish() }
        done.await()
    }

    /**
     * An image or video step's media loaded (or failed): the flow moves on
     * now instead of at the cap (web: load / loadedmetadata / error).
     */
    fun mediaSettled(nodeId: String) {
        mediaWaiters[nodeId]?.invoke()
    }

    private suspend fun handleProceed(port: String?, fallback: String?, node: FlowNode, token: Int) {
        _isProcessing.value = false
        ChatAnalytics.trackNodeExit(node.id, "proceeded")
        sendResponseToServer()
        if (port == null) next(node, token) else port(node, port, fallback, token)
    }

    // ========================================
    // ROUTING
    // ========================================

    /** The FIRST edge from the step, whatever its handle; none ends the chat. */
    private suspend fun next(node: FlowNode, token: Int) {
        if (ended || token != moveToken) return
        val edge = edges.firstOrNull { it.source == node.id }
        if (edge != null) recordEdge(edge)
        val target = edge?.let { nodes[it.target] }
        if (edge != null && target != null) enter(target, if (edge.sourceHandle != null) "edge:${edge.sourceHandle}" else "edge")
        else end("no_next_node")
    }

    private fun recordEdge(edge: FlowEdge) {
        ChatState.appendRecord(
            RecordEntry(
                id = edge.id, shape = "", type = edge.raw["type"] as? String, time = FlowCalendar.iso(now()),
                fields = edge.raw.filterKeys { it != "id" && it != "type" }
            )
        )
    }

    /** The edge from exactly this handle; none ends the chat. */
    private suspend fun port(node: FlowNode, handle: String, fallback: String?, token: Int) {
        if (ended || token != moveToken) return
        fun wired(h: String) = edges.any { it.source == node.id && it.sourceHandle == h }
        val h = if (fallback != null && !wired(handle) && wired(fallback)) fallback else handle
        val edge = edges.firstOrNull { it.source == node.id && it.sourceHandle == h }
        trace(FlowTrace.Port(node.id, h, edge != null))
        if (edge == null) {
            end("no_next_node")
            return
        }
        recordEdge(edge)
        val target = nodes[edge.target]
        if (target == null) {
            // The web throws on nextNode.type here: the chat stops where it is.
            trace(FlowTrace.Crash(node.id, "edge target missing"))
            waiting = false
            handleError("Edge ${edge.id} leads to a missing node (${edge.target})", node.id)
            return
        }
        enter(target, "port:$h")
    }

    /** To a node by id; a missing target ends the chat. */
    private suspend fun jump(targetNodeId: String, token: Int) {
        if (ended || token != moveToken) return
        val target = if (targetNodeId.isNotEmpty()) nodes[targetNodeId] else null
        if (target == null) {
            end("jump_target_missing")
            return
        }
        enter(target, "jump")
    }

    /** A step type this engine does not run: nothing shown, then its first edge. */
    private suspend fun passUnknown(node: FlowNode, token: Int) {
        android.util.Log.w(TAG, "step \"${node.type}\" is not supported here; continuing the flow")
        trace(FlowTrace.Unsupported(node.id, node.type))
        clock.delay(0)
        if (token != moveToken || ended) return
        next(node, token)
    }

    // ========================================
    // LOOPS AND SUB-FLOWS (web flowControlRun.ts)
    // ========================================

    private fun setVars(vars: Map<String, Any?>) = vars.forEach { (k, v) -> ChatState.saveVariableByName(k, v) }

    /** A trail entry on the step's latest record ("Loop 3 of 5"). */
    private fun trail(node: FlowNode, extra: Map<String, Any?>) {
        val record = ChatState.record.value
        for (i in record.indices.reversed()) {
            val r = record[i]
            if (r.id == node.id && r.type == node.type && r.shape.isEmpty()) {
                ChatState.addRecord(r.copy(fields = r.fields + extra))
                return
            }
        }
    }

    private suspend fun runFlowControl(node: FlowNode, token: Int) {
        val d = node.data
        when (node.type) {
            FlowControl.LOOP_TYPE -> {
                val step = FlowControl.stepLoop(node.id, d, flowControl, ::lookup)
                setVars(step.vars)
                trail(node, mapOf("loop" to step.trail.toMap() + ("text" to FlowControl.loopTrailText(step.trail))))
                sendResponseToServer()
                clock.delay(0)
                if (token != moveToken || ended) return
                port(node, step.handle, null, token)
            }
            FlowControl.RUN_SUBFLOW_TYPE -> {
                val call = FlowControl.callSubflow(node.id, d, nodeList.map { it.id to (it.type to it.data) }, flowControl, ::lookup)
                if (!call.ok) {
                    trail(node, mapOf("subflow" to mapOf("name" to (call.name ?: ""), "error" to call.error)))
                    sendResponseToServer()
                    clock.delay(0)
                    if (token != moveToken || ended) return
                    val wiredError = edges.any { it.source == node.id && it.sourceHandle == FlowControl.RUN_ERROR_HANDLE }
                    port(node, if (wiredError) FlowControl.RUN_ERROR_HANDLE else "source", null, token)
                    return
                }
                setVars(call.vars)
                trail(node, mapOf("subflow" to mapOf("name" to call.name, "inputs" to call.vars, "depth" to call.frame?.depth, "text" to "Ran sub-flow ${call.name}")))
                sendResponseToServer()
                clock.delay(0)
                if (token != moveToken || ended) return
                jump(call.startId ?: "", token)
            }
            FlowControl.SUBFLOW_TYPE -> {
                trail(node, mapOf("subflow" to mapOf("name" to FlowControl.subflowName(d), "entered" to true)))
                clock.delay(0)
                if (token != moveToken || ended) return
                port(node, "source", null, token)
            }
            FlowControl.SUBFLOW_RETURN_TYPE -> {
                if (returnFromSubflowPath(token)) return
                clock.delay(0)
                if (token != moveToken || ended) return
                next(node, token)
            }
        }
    }

    /**
     * The end of a path inside a sub-flow: copy the outputs back and carry on
     * from Run sub-flow's "source" edge (or one with no handle). True when it
     * carried on.
     */
    private suspend fun returnFromSubflowPath(token: Int): Boolean {
        var ret = FlowControl.returnFromSubflow(flowControl, ::lookup)
        while (ret != null) {
            setVars(ret.vars)
            val runNodeId = ret.frame.runNodeId
            val edge = edges.firstOrNull { it.source == runNodeId && (it.sourceHandle == "source" || it.sourceHandle == null) }
            if (edge != null && edge.target.isNotEmpty()) {
                sendResponseToServer()
                clock.delay(0)
                if (token != moveToken || ended) return true
                jump(edge.target, token)
                return true
            }
            ret = FlowControl.returnFromSubflow(flowControl, ::lookup)
        }
        return false
    }

    // ========================================
    // USER INPUT HANDLING
    // ========================================

    /**
     * Handle user response for current interactive node
     */
    override fun submitResponse(response: Any) {
        scope.launch { respond(response) }
    }

    /**
     * A step card's answer (core/flow/FlowStepAnswer): as [submitResponse],
     * and the visitor's lines the step says in reply come out of
     * [visitorLines] for the chat to draw.
     */
    fun submitStepResponse(response: Any) {
        scope.launch { respond(response, echo = true) }
    }

    /** The visitor's answer to the waiting step. */
    suspend fun respond(response: Any, echo: Boolean = false) {
        val nodeId = currentNodeId ?: return
        if (!waiting) {
            android.util.Log.d(TAG, "Not waiting for input, ignoring response")
            return
        }
        // Typed text is trimmed; an empty send is ignored (web composer).
        if (response is String && response.trim().isEmpty()) return
        val node = nodes[nodeId] ?: return
        val handler = NodeHandlerRegistry.getHandler(node.type)
        if (handler == null) {
            handleError("Handler not found for: ${node.type}", node.id)
            return
        }
        if (response is String) ChatAnalytics.trackUserMessage(messageLength = response.length, messageText = response)

        waiting = false
        _isProcessing.value = true
        _errorMessage.value = null
        nodesProcessedInCycle = 0
        applyCustomizations()
        val token = moveToken

        // The handover survey's rating, as the web sends it (submit-chat-rating).
        if (node.type == NodeTypes.HUMAN_HANDOVER && response is List<*>) sendSurveyRating(response)

        try {
            val before = ChatState.transcript.value.lastOrNull()
            val result = runHandler(handler, node, "edge", token) { h -> h.handleResponse(response, node.data, node.id) }
            if (echo) {
                val now = ChatState.transcript.value
                now.drop(now.indexOfLast { it === before } + 1).filter { it.by == "user" }.forEach {
                    _visitorLines.tryEmit(it.message)
                    _visitorAnswers.tryEmit(VisitorLine(it.message, node.id, node.type))
                }
            }
            if (token != moveToken || ended) return
            sendResponseToServer()
            handleNodeResult(result, node, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _isProcessing.value = false
            handleError(e.message ?: "Error", node.id)
            ChatAnalytics.trackNodeExit(node.id, "error")
        }
    }

    /**
     * A refused answer: say why, keep waiting at the step. A typed answer's
     * reason comes as a bot bubble after 700 ms of typing (web helpers
     * _displayErrorMessage); a card shows its own at once.
     */
    private suspend fun handleInvalid(message: String, node: FlowNode, token: Int, ui: NodeUIState?, inCard: Boolean) {
        trace(FlowTrace.Error(node.id, message))
        _isProcessing.value = false
        if (message.isNotEmpty() && !inCard) {
            _isProcessing.value = true
            clock.delay(700)
            if (token != moveToken || ended) return
            val text = FlowText.bubbleText(message)
            ChatState.addToTranscript("bot", text)
            _botMessages.tryEmit(NodeUIState.Message(text, node.id))
            _isProcessing.value = false
        } else if (message.isNotEmpty()) {
            _errorMessage.value = FlowText.plainText(message)
        }
        val shown = ui ?: _currentUIState.value
        val reason = FlowText.plainText(message).takeIf { it.isNotEmpty() }
        _currentUIState.value = when (shown) {
            null -> null
            // The card says why on itself; a typed answer's reason is the bubble above.
            is NodeUIState.TextInput -> stamped(shown.copy(errorMessage = FlowText.plainText(message)), node, token, if (inCard) reason else null)
            else -> stamped(shown, node, token, if (inCard) reason else null)
        }
        waiting = true
    }

    // ========================================
    // SERVER COMMUNICATION
    // ========================================

    /**
     * Send current state to server via socket (the web's response-record)
     */
    private fun sendResponseToServer() {
        val responseData = ChatState.buildResponseData()
        // Never emit without a session id - the server would file the record
        // under a shared no-session document.
        if (responseData["chatSessionId"] == null) return
        socket?.sendResponseRecord(responseData)
    }

    // ========================================
    // ERROR HANDLING AND THE END
    // ========================================

    private fun handleError(message: String, nodeId: String?) {
        android.util.Log.e(TAG, "Error: $message (node $nodeId)")
        _errorMessage.value = message
        _isProcessing.value = false
    }

    /**
     * The end of a path: a sub-flow returns to its caller; otherwise the chat
     * is over (web index.ts: hasChatEnded, record sent, chat-flow-completed).
     */
    private suspend fun end(reason: String) {
        if (ended) return
        if (flowControl.stack.isNotEmpty() && returnFromSubflowPath(moveToken)) return
        if (ended) return
        ended = true
        waiting = false
        _isProcessing.value = false
        ChatState.setVariable("_flowComplete", true)
        ChatState.setVariable("_flowCompletionReason", reason)
        sendResponseToServer()
        ChatState.chatSessionId?.let { socket?.emit("chat-flow-completed", mapOf("chatSessionId" to it)) }
        trace(FlowTrace.End)
        _isFlowComplete.value = true
        try {
            onFlowEnd?.invoke(reason)
        } catch (e: Exception) {
            // a listener's error never stops the flow
        }
    }

    private fun trace(event: FlowTrace) {
        try {
            config.onTrace?.invoke(event)
        } catch (e: Exception) {
            // a listener's error never stops the flow
        }
    }

    /**
     * Clear current error
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ========================================
    // THE STEP'S CONTEXT
    // ========================================

    private inner class StepContext(private val node: FlowNode, override val via: String, private val token: Int) : FlowContext {
        override val nodeId: String = node.id
        override val stepIndex: Int = Math.max(0, steps.lastIndexOf(node))
        override val customizations: Map<String, Any?> get() = config.customizations()
        override val visitorTimeZone: String = config.visitorTimeZone ?: FlowCalendar.visitorTimeZone()
        override val pageUrl: String? = config.pageUrl
        override val pageReferrer: String? = config.pageReferrer
        override val calendarBookings: List<Map<String, Any?>> get() = this@NodeFlowEngine.calendarBookings

        override suspend fun api(path: String, body: Map<String, Any?>, timeoutMs: Long?): ApiReply =
            api.post(ChatState.botId ?: "", path, body, timeoutMs)

        override suspend fun upload(file: UploadFile): UploadResult {
            val session = ChatState.chatSessionId ?: ""
            return api.upload(ChatState.botId ?: "", session, ChatState.visitorId ?: session, file)
        }

        override suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?): ApiReply =
            api.fetch(url, method, headers, body, timeoutMs)

        override fun emit(event: String, payload: Map<String, Any?>): Boolean = socket?.emit(event, payload) ?: false

        override fun on(event: String, listener: suspend (Map<String, Any?>?) -> Unit): () -> Unit {
            val s = socket ?: return {}
            return s.on(event) { payload -> scope.launch(FlowContextElement(this)) { listener(payload) } }
        }

        override suspend fun typing(ms: Long) {
            if (ms > 0) {
                _isProcessing.value = true
                clock.delay(ms)
            }
        }

        override suspend fun delay(ms: Long) = clock.delay(ms)
        override fun setTimeout(ms: Long, fn: () -> Unit) = clock.setTimeout(ms, fn)
        override fun setInterval(ms: Long, fn: () -> Unit) = clock.setInterval(ms, fn)
        override fun edges(): List<FlowEdge> = edges.filter { it.source == node.id }
        override fun edgesOf(nodeId: String): List<FlowEdge> = edges.filter { it.source == nodeId }
        override fun wired(handle: String) = edges.any { it.source == node.id && it.sourceHandle == handle }
        override fun random(): Double = config.random?.invoke() ?: Math.random()
        override fun now(): Long = clock.now()

        override fun respond(response: Any) {
            scope.launch { if (currentNodeId == node.id && waiting) this@NodeFlowEngine.respond(response) }
        }

        override fun shown() {
            if (shownToken != token && token == moveToken) {
                shownToken = token
                trace(FlowTrace.Shown(node.id))
            }
        }

        override fun isStale(): Boolean = token != moveToken && currentNodeId != node.id
        override fun node(id: String): FlowNode? = nodes[id]

        override fun say(text: String) {
            if (text.isEmpty()) return
            _isProcessing.value = false
            _botMessages.tryEmit(NodeUIState.Message(text, node.id))
        }

        override fun sendRecord() = sendResponseToServer()

        override fun joinChatRoom() {
            ChatState.chatSessionId?.let { socket?.joinChatRoom(it) }
        }

        override fun background(block: suspend () -> Unit) {
            scope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Background step work failed: ${e.message}")
                }
            }
        }
    }

    // ========================================
    // SERVER MESSAGES AND THE HANDOVER STEP
    // ========================================

    /**
     * A plain message from the server outside the flow.
     */
    fun handleServerMessage(message: Map<String, Any?>) {
        val text = message["text"]?.toString() ?: return
        _currentUIState.value = NodeUIState.Message(text = text, nodeId = message["_id"]?.toString() ?: "")
    }

    /**
     * The handover survey's answers as submit-chat-rating (the server has
     * no post-chat-survey-response): its first rating, and its first written
     * answer as the feedback.
     */
    private fun sendSurveyRating(responses: List<*>) {
        val sessionId = ChatState.chatSessionId ?: return
        val survey = _currentUIState.value as? NodeUIState.PostChatSurvey ?: return
        val answers = responses.filterIsInstance<NodeUIState.SurveyResponse>()
        val ratingQ = survey.questions.firstOrNull { q ->
            q.type == NodeUIState.PostChatSurvey.SurveyQuestionType.RATING && answers.any { it.questionId == q.id && it.value is Number }
        }
        val rating = ratingQ?.let { q -> (answers.first { it.questionId == q.id }.value as Number).toInt() }
        val feedback = answers.firstOrNull { a ->
            survey.questions.any { it.id == a.questionId && it.type == NodeUIState.PostChatSurvey.SurveyQuestionType.TEXT } && a.value is String
        }?.value as? String
        val payload = com.conferbot.sdk.core.livechat.LiveChat.surveyRatingPayload(sessionId, ratingQ?.maxRating, rating, feedback) ?: return
        socket?.emit("submit-chat-rating", payload)
    }

    /**
     * A restored chat that was waiting on a step (a handover still in the
     * queue): back on that step showing [ui], without running the step
     * again - a second run would ask the server for a person a second time.
     * The visitor's answer (Cancel, the flow carrying on) then goes through
     * submitResponse as usual. False when the step is not in the flow.
     */
    override fun waitAt(nodeId: String, ui: NodeUIState): Boolean {
        if (!nodes.containsKey(nodeId)) return false
        currentNodeId = nodeId
        nodeList.indexOfFirst { it.id == nodeId }.takeIf { it >= 0 }?.let { ChatState.setCurrentIndex(it) }
        _isProcessing.value = false
        waiting = true
        _currentUIState.value = ui
        return true
    }

    /** Run a step again (a restored handover whose request never went out). */
    override fun resumeFrom(nodeId: String) {
        val node = nodes[nodeId] ?: return
        nodesProcessedInCycle = 0
        ended = false
        scope.launch { enter(node, "edge") }
    }

    /**
     * Handle agent-related events
     */
    override fun handleAgentAccepted(agentName: String) {
        val nodeId = currentNodeId ?: return
        if (!isAtHandover) return
        val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER) as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler
        handler?.onAgentAccepted(nodeId, agentName)
        _currentUIState.value = NodeUIState.HumanHandover(
            state = NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED,
            agentName = agentName,
            nodeId = nodeId
        )
    }

    /**
     * Handle no agents available
     */
    fun handleNoAgentsAvailable() {
        val nodeId = currentNodeId ?: return
        val node = nodes[nodeId] ?: return
        if (!isAtHandover) return
        val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER) as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler
        scope.launch {
            val result = handler?.onNoAgentsAvailable(nodeId, node.data)
            if (result != null) handleNodeResult(result, node, moveToken)
        }
    }

    /** The handover step's data while the flow is on it (its fallbackMessage...). */
    override val handoverNodeData: Map<String, Any?>?
        get() = if (isAtHandover) currentNodeId?.let { nodes[it]?.data } else null

    /** The handover step's card goes (a person joined, or nobody could). */
    fun clearHandoverUI() {
        if (_currentUIState.value is NodeUIState.HumanHandover) _currentUIState.value = null
    }

    /** The handover step the flow is on, or null. */
    override val handoverNodeId: String?
        get() = if (isAtHandover) currentNodeId else null

    /** Waiting on its handover step (for a person, or after nobody came). */
    override val isAtHandover: Boolean
        get() = currentNodeId?.let { nodes[it]?.type } == NodeTypes.HUMAN_HANDOVER

    /**
     * Nobody came and the visitor left a message or skipped it: the flow
     * carries on from the handover step, as the web widget does.
     */
    fun continueAfterHandover() {
        if (!isAtHandover) return
        _currentUIState.value = null
        val node = currentNodeId?.let { nodes[it] } ?: return
        val token = ++moveToken
        waiting = false
        scope.launch { next(node, token) }
    }

    /**
     * Handle chat ended (for handover)
     * Triggers post-chat survey if configured
     */
    fun handleChatEnded() {
        val nodeId = currentNodeId ?: return
        val node = nodes[nodeId] ?: return
        if (!isAtHandover) return
        val handler = NodeHandlerRegistry.getHandler(NodeTypes.HUMAN_HANDOVER) as? com.conferbot.sdk.core.nodes.handlers.HumanHandoverNodeHandler
        scope.launch {
            val result = handler?.onChatEnded(nodeId, node.data) ?: return@launch
            if (result is NodeResult.DisplayUI && result.uiState is NodeUIState.PostChatSurvey) {
                ChatAnalytics.trackNodeEntry(nodeId = "${nodeId}_survey", nodeType = "post-chat-survey", nodeName = "Post Chat Survey")
            }
            handleNodeResult(result, node, moveToken)
        }
    }

    /**
     * Handle post-chat survey response submission
     */
    fun handleSurveySubmission(nodeId: String, responses: List<NodeUIState.SurveyResponse>) {
        ChatAnalytics.trackNodeExit(
            nodeId = "${nodeId}_survey",
            exitType = "proceeded",
            userInput = "Survey completed with ${responses.size} responses"
        )
    }

    /** Stops the flow where it is: its timers and pending replies do nothing. */
    override fun stop() {
        moveToken++
        waiting = false
        scope.coroutineContext[Job]?.cancelChildren()
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        ChatAnalytics.finalizeChatAnalytics()
        moveToken++
        scope.cancel()
        ChatState.reset()
    }
}

/** The SDK's socket as the flow uses it. */
internal class SocketClientFlowSocket(private val client: SocketClient) : FlowSocket {
    override fun emit(event: String, payload: Map<String, Any?>): Boolean {
        if (!client.isConnected) return false
        return try {
            client.emit(event, JSONObject(payload))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun on(event: String, listener: (Map<String, Any?>?) -> Unit): () -> Unit {
        val l = Emitter.Listener { args ->
            listener(com.conferbot.sdk.core.livechat.LiveChatExtras.toMap(args.firstOrNull() as? JSONObject))
        }
        client.on(event, l)
        return { client.off(event, l) }
    }

    override fun sendResponseRecord(data: Map<String, Any?>) = client.sendResponseRecord(data)

    override fun joinChatRoom(chatSessionId: String) = client.joinChatRoom(
        chatSessionId = chatSessionId,
        deviceInfo = mapOf(
            "os" to "Android",
            "osVersion" to (Build.VERSION.RELEASE ?: "unknown"),
            "sdkVersion" to Build.VERSION.SDK_INT.toString(),
            "deviceModel" to (Build.MODEL ?: "unknown")
        )
    )
}
