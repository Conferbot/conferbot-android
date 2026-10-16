package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.Agent
import com.conferbot.sdk.models.AgentDetails
import com.conferbot.sdk.models.RecordItem
import java.util.Date

/** The socket as the handover lifecycle uses it (SocketClient in the SDK, a fake in tests). */
interface HandoverSocket {
    fun on(event: String, fn: (Map<String, Any?>?) -> Unit)
    fun emit(event: String, data: Map<String, Any?>)
    /** Emit and wait for the answer; null when offline or after [timeoutMs]. */
    fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit)
    fun joinRoom(chatSessionId: String)
}

/** The flow as the handover lifecycle drives it (NodeFlowEngine). */
interface HandoverFlow {
    /** On its human-handover step. */
    val isAtHandover: Boolean
    /** Back on a step showing [ui], without running it again; false when it is not in the flow. */
    fun waitAt(nodeId: String, ui: NodeUIState): Boolean
    /** Run a step again (the request never went out). */
    fun resumeFrom(nodeId: String)
    fun submitResponse(response: Any)
    /** A person has the chat (the step's connected state, the survey on chat end). */
    fun handleAgentAccepted(agentName: String)
    /** The handover step the flow is on, or null. */
    val handoverNodeId: String? get() = null
    /** That handover step's data (its maxWaitTime, assignment), or null. */
    val handoverNodeData: Map<String, Any?>? get() = null
    /** Stop the flow where it is (a blocked visitor). */
    fun stop() {}
}

/** What the lifecycle reads and changes in the chat (Conferbot). */
interface HandoverHost {
    /** The record on screen. */
    fun items(): List<RecordItem>
    /** Change the record on screen (and save the entries that changed). */
    fun updateItems(transform: (List<RecordItem>) -> List<RecordItem>)
    /** Add to the record on screen, at once, and save it. */
    fun addItem(item: RecordItem)
    /** The conversation's record for the server (ChatState). */
    fun record(): List<RecordEntry>
    /** Add or merge an entry in that record (ChatState.pushToRecord). */
    fun saveEntry(entry: RecordEntry)
    fun sessionId(): String?
    /** The chat is on screen (the waiting signal waits for it). */
    fun chatOpen(): Boolean
    /** A flow's handover step is waiting for a person (its card is up, no live chat yet). */
    fun flowWaiting(): Boolean
    fun setLiveMode(on: Boolean)
    fun setAgent(agent: Agent?)
}

/**
 * Where a wait for a person begins and ends, for the help card that comes 6s
 * in (core/waiting/WaitingExtras; web special.ts waitForPerson).
 */
interface WaitHooks {
    fun start()
    fun end()
}

/**
 * The handover's lifecycle in the chat: the "Connecting you with the
 * team…" line and how it settles, the 20s waiting signal, leaving the
 * queue, and picking a live chat back up after the app restarts. The logic
 * is in [HandoverState] and [HandoverSignals]; this applies it to the
 * record and the socket. Ported from the React Native SDK's
 * src/context/hooks/useHandoverLifecycle.ts (web special.ts, index.ts,
 * agentListeners.ts).
 */
class HandoverLifecycle(
    private val socket: () -> HandoverSocket?,
    private val flow: () -> HandoverFlow?,
    private val host: HandoverHost,
    timer: SignalTimer,
    /** Where a wait for a person begins and ends (the while-you-wait help card). */
    private val waitHooks: WaitHooks? = null,
    /** The chat is an AI agent bot's (no flow): an inactivity close only pauses it. */
    private val isAgentBot: () -> Boolean = { false },
    /** The bot's workspace, for the escalation's handover-timeout. */
    private val workspaceId: () -> String? = { null },
    /** A restored wait for a person: the server's seconds left of its max wait (the countdown and the max-wait timer). */
    private val onRestoredWait: (Long?) -> Unit = {},
    private val now: () -> Date = { Date() }
) {
    val bus = SignalBus()

    private val connecting = ConnectingSignal(timer, canFire = { host.chatOpen() }) {
        bus.emit(WidgetSignal.WaitingForHuman(offline = false, reason = "busy", backAt = null, emailCaptured = false))
    }

    // The handover step an AI escalation waits on: the flow's, else the agent
    // bot's silent one in the record.
    private fun escalationStep(): Map<String, Any?>? = EscalationWait.handoverData(flow()?.handoverNodeData, host.record())

    /**
     * After an AI escalates: its step's max wait plus 30s, then the server is
     * nudged to decide (handover-timeout). Nothing is drawn (web special.ts
     * _handleAIEscalationWait, handoverWaiting.ts armAIEscalationNudge).
     */
    val nudge = EscalationNudge(timer) {
        val sid = host.sessionId()
        if (!sid.isNullOrEmpty()) socket()?.emit("handover-timeout", LiveChat.handoverTimeoutPayload(sid, escalationStep(), workspaceId().orEmpty()))
    }

    // The queue was left for this wait (our own answer and the room's
    // queue-left both say so): once.
    @Volatile private var queueLeft = false

    private fun entries() = HandoverState.entriesOfItems(host.items())
    private fun own(data: Map<String, Any?>?): Boolean {
        val id = data?.get("chatSessionId")?.toString().orEmpty()
        val mine = host.sessionId()
        return id.isEmpty() || mine == null || id == mine
    }

    private var seq = 0
    // Its own id per entry: two lines in the same millisecond must not merge.
    private fun entryId(kind: String) = "$kind-${now().time}-${++seq}"

    /** Waiting for a person: the flow's step, the waiting signal, or a pending line. */
    fun inWait(): Boolean = host.flowWaiting() || connecting.pending || HandoverState.hasPendingLine(entries())

    /** The handover phase of the record on screen. */
    fun phase(): HandoverPhase = HandoverState.phase(entries())

    fun onWidgetSignal(name: String, fn: (WidgetSignal) -> Unit): () -> Unit = bus.on(name, fn)

    /** The Connecting line has its answer: settled on screen and in the record. */
    fun settle(outcome: HandoverOutcome) {
        host.updateItems { HandoverState.settleItems(it, outcome) }
        HandoverState.settledRecordEntries(host.record(), outcome).forEach(host::saveEntry)
    }

    private fun waitStart() = waitHooks?.start()
    private fun waitEnd() = waitHooks?.end()

    /** The wait ended without a person: no waiting signal, and cb:wait-over. */
    private fun waitOver(reason: String) {
        connecting.end()
        nudge.clear()
        waitEnd()
        bus.emit(WidgetSignal.WaitOver(reason))
    }

    /**
     * A flow's handover started waiting for a person: the 20s signal (web
     * special.ts startHandover; not when an AI agent takes it first).
     */
    fun flowWaitStarted(nodeData: Map<String, Any?>?) {
        queueLeft = false
        val ai = nodeData?.get("assignedAIAgents") as? List<*>
        if (ai.isNullOrEmpty()) {
            connecting.start()
            waitStart()
        }
    }

    /**
     * agent-left (web agentListeners.ts): an AI escalating draws one
     * Connecting line and starts the wait; an idle pause draws nothing; a
     * person leaving says so. [fallbackName]: the agent on the chat.
     */
    fun onAgentLeft(data: Map<String, Any?>?, fallbackName: String? = null): AgentLeftKind {
        val kind = HandoverState.agentLeftKind(data)
        if (kind == AgentLeftKind.IGNORE) return kind
        val details = data?.get("agentDetails") as? Map<*, *>
        val name = details?.get("name")?.toString()?.takeIf { it.isNotBlank() } ?: fallbackName
        val handover = kind == AgentLeftKind.HANDOVER
        if (handover) {
            queueLeft = false
            // The AI passed the visitor on: the wait for a person starts again.
            if (EscalationWait.starts(data)) nudge.arm(escalationStep()?.get("maxWaitTime"))
            connecting.start()
            // Passed on to a person: what may help while they wait (web special.ts agent-left).
            waitStart()
            // The same escalation again: one line is enough.
            if (HandoverState.hasPendingLine(entries())) return kind
        }
        val id = entryId("agent-left")
        val text = HandoverState.agentLeftText(name, handover)
        val agentId = details?.get("_id")?.toString()
        host.addItem(
            RecordItem.AgentLeftMessage(
                id = id, time = now(), text = text, handover = handover,
                agentDetails = agentId?.let { AgentDetails(id = it, name = name.orEmpty(), isAIAgent = details["isAIAgent"] == true) }
            )
        )
        host.saveEntry(
            RecordEntry(
                id = id, shape = "agent-left-chat", type = "agent-left-chat", text = text,
                data = buildMap<String, Any?> {
                    put("name", name.orEmpty())
                    agentId?.let { put("agentId", it) }
                    if (handover) put("handover", true)
                }.toMutableMap()
            )
        )
        return kind
    }

    /**
     * agent-accepted (web special.ts _handleHandoverAccepted): the wait is
     * over - a person joined, or the AI took the chat back. Settles the line
     * before anything else (also before an agent bot's own check).
     */
    fun onAccepted(data: Map<String, Any?>?): AcceptedPlan {
        val plan = HandoverState.acceptedPlan(entries(), data)
        connecting.end()
        nudge.clear()
        waitEnd()
        settle(plan.outcome)
        if (plan.humanJoined) {
            val name = (data?.get("agentDetails") as? Map<*, *>)?.get("name")?.toString()
            bus.emit(WidgetSignal.HumanJoined(name))
        }
        return plan
    }

    /** The join line: "<name> joined the chat", with its note after an AI handover. */
    fun addJoin(details: AgentDetails, afterAI: Boolean) {
        val id = entryId("agent-joined")
        host.addItem(RecordItem.AgentJoinedMessage(id = id, time = now(), agentDetails = details, afterAI = afterAI))
        host.saveEntry(
            RecordEntry(
                id = id, shape = "agent-joined-message", type = "agent-joined-message", text = HandoverState.joinedText(details.name),
                data = buildMap<String, Any?> {
                    put("name", details.name)
                    put("agentId", details.id)
                    if (afterAI) put("afterAI", true)
                    if (details.isAIAgent) put("ai", true)
                }.toMutableMap()
            )
        )
    }

    /**
     * Out of the queue: the line, the record, the signal, the flow moves on.
     * Once per wait. [helped]: a help article solved it ("That solved it",
     * web special.ts _wireQueue leave()): "You left the queue. Glad that
     * helped.", recorded as "Resolved while waiting".
     */
    fun leftQueue(helped: Boolean = false) {
        if (queueLeft) return
        queueLeft = true
        waitOver("cancelled")
        settle(HandoverOutcome.CANCELLED)
        val id = entryId("queue-self-resolved")
        val cancelled = HandoverState.outcomeText(HandoverOutcome.CANCELLED)
        val text = if (helped) com.conferbot.sdk.core.waiting.WaitArticles.LEFT_HELPED else cancelled
        val recorded = if (helped) com.conferbot.sdk.core.waiting.WaitArticles.RECORD_TEXT else cancelled
        host.addItem(RecordItem.SystemMessage(id = id, time = now(), text = text, kind = "queue-self-resolved"))
        host.saveEntry(RecordEntry(id = id, shape = "queue-self-resolved", type = "queue-self-resolved", text = recorded))
        flow()?.takeIf { it.isAtHandover }?.submitResponse(mapOf("action" to "cancel", "queueLeft" to true))
    }

    /** The server let them go because an article helped (queue-self-resolve answered ok). */
    fun leftQueueHelped() = leftQueue(helped = true)

    /**
     * The visitor leaves the queue (the wait card's Cancel): queue-self-resolve,
     * as the web widget does, so the team stops seeing them waiting. [onDone]
     * gets false when a person already picked the chat up (they stay).
     */
    fun leaveQueue(onDone: (Boolean) -> Unit = {}) {
        val s = socket()
        val sid = host.sessionId()
        if (s == null || sid == null) {
            leftQueue()
            return onDone(true)
        }
        s.ask("queue-self-resolve", HandoverState.queueSelfResolvePayload(sid), 8_000L) { res ->
            // not_waiting: no queued ticket - or a person just took it (in progress).
            if (HandoverState.queueLeaveNeedsState(res)) {
                s.ask("live-chat-state", mapOf("chatSessionId" to sid), 8_000L) { state -> finishLeave(res, state, onDone) }
            } else finishLeave(res, null, onDone)
        }
    }

    private fun finishLeave(res: Map<String, Any?>?, state: Map<String, Any?>?, onDone: (Boolean) -> Unit) {
        val leaves = HandoverState.queueLeaves(res, state)
        if (leaves) leftQueue()
        onDone(leaves)
    }

    fun setupListeners() {
        val s = socket() ?: return
        // The server's own word on the wait (an AI escalation found nobody):
        // its cb:waiting-for-human, never ours as well.
        s.on("handover-waiting") {
            if (!own(it)) return@on
            connecting.end()
            nudge.clear()
            waitEnd()
            settle(HandoverOutcome.UNAVAILABLE)
            bus.emit(HandoverSignals.waitingDetail(it))
        }
        s.on("no-agents-available") {
            settle(HandoverOutcome.UNAVAILABLE)
            waitOver("unavailable")
        }
        s.on("chat-ended") { ended(it) }
        s.on("live-chat-requeued") { if (own(it)) requeued(it) }
        // A transfer the server wrote into the record: kept in this copy
        // too, so the next write keeps it. Not drawn (as on the web): the
        // requeued line or the next agent's join tells the visitor.
        s.on("live-chat-transferred") {
            if (!own(it)) return@on
            LiveEvents.transferOf(it)?.let { t ->
                host.saveEntry(
                    RecordEntry(
                        id = t.id, shape = "live-chat-transfer", type = "live-chat-transfer", text = t.text,
                        data = buildMap<String, Any?> { t.kind?.let { k -> put("kind", k) } }.toMutableMap()
                    )
                )
            }
        }
        // The AI let an idle visitor go (web agentListeners.ts, server
        // aiInactivityHandler.js): "Chat paused" (a message picks it back up)
        // or, for continue_flow on a flow bot, "Chat ended" and the flow
        // carries on from its handover step (LiveEvents.inactiveClosePlan).
        s.on(LiveEvents.INACTIVE_CLOSE) {
            if (!own(it)) return@on
            ended(it)
            inactiveClosed(it)
        }
        // Left the queue (this device's own leave, or another device of this chat).
        s.on("queue-left") {
            if (!own(it) || it?.get("reason") != "self_service") return@on
            if (flow()?.isAtHandover == true && host.flowWaiting()) leftQueue()
        }
    }

    /**
     * live-chat-requeued (web liveTakeover.ts): the agent left and nobody
     * else owns the chat, so it is back in the queue. "Connecting you to
     * another agent..." (or the team it went to), and a flow at its handover
     * step waits again.
     */
    fun requeued(data: Map<String, Any?>?) {
        val id = LiveEvents.eventId("requeued", now().time)
        val text = LiveEvents.requeuedText(data?.get("teamName"))
        host.addItem(RecordItem.SystemMessage(id = id, time = now(), text = text, kind = "live-chat-requeued"))
        host.saveEntry(RecordEntry(id = id, shape = "live-chat-requeued", type = "live-chat-requeued", text = text))
        host.setAgent(null)
        val engine = flow()
        val nodeId = engine?.handoverNodeId
        if (engine != null && nodeId != null && engine.isAtHandover) {
            host.setLiveMode(false)
            engine.waitAt(nodeId, NodeUIState.HumanHandover(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = nodeId))
        }
        queueLeft = false
        connecting.start()
    }

    /**
     * Blocked by the workspace, mid-chat or at the handshake (web
     * closedState.ts, _applyVisitorBlock): a Connecting line stops its dots,
     * the wait and the flow are over.
     */
    fun blocked() {
        connecting.end()
        nudge.clear()
        waitEnd()
        settle(HandoverOutcome.ENDED)
        flow()?.stop()
        host.setLiveMode(false)
    }

    /**
     * ai-agent-inactive-close: the paused or ended line, on screen and in the
     * record (once: the chat's own handler records nothing), and for
     * continue_flow nobody in the chat any more, live mode off and the flow on
     * from its handover step (the step's next edge, no post-chat survey). A
     * pause leaves live mode on: the visitor's next message goes the live way
     * and the server answers ai-agent-resumed.
     */
    fun inactiveClosed(data: Map<String, Any?>?) {
        val agentBot = isAgentBot()
        val entry = LiveEvents.inactiveCloseEntry(data, agentBot, now().time)
        host.addItem(RecordItem.SystemMessage(id = entry.id, time = now(), text = entry.text, kind = LiveEvents.INACTIVE_CLOSE, paused = entry.paused))
        host.saveEntry(RecordEntry(id = entry.id, shape = LiveEvents.INACTIVE_CLOSE, type = LiveEvents.INACTIVE_CLOSE, text = entry.text, data = entry.data.toMutableMap()))
        if (!LiveEvents.inactiveClosePlan(data, agentBot).continueFlow) return
        host.setAgent(null)
        host.setLiveMode(false)
        flow()?.takeIf { it.isAtHandover }?.submitResponse(mapOf("action" to "continue"))
    }

    private fun ended(data: Map<String, Any?>?) {
        if (!own(data)) return
        nudge.clear()
        waitEnd()
        val waiting = inWait()
        settle(HandoverOutcome.ENDED)
        if (waiting) waitOver("ended")
    }

    /**
     * A chat restored from the device: ask the server where its live chat
     * stands (live-chat-state) and pick it back up - the person and the join
     * line it missed, still in the queue, or over - instead of running the
     * flow again. [handoverNodeId]: the flow's current step when it is its
     * human-handover step (never for an agent bot). [onDone] gets what was
     * done; START leaves the chat as any restored chat.
     */
    fun resumeRestored(handoverNodeId: String?, onDone: (RestoreStep) -> Unit = {}) {
        // Lines whose outcome the record knows are drawn settled.
        host.updateItems(HandoverState::annotateItems)
        HandoverState.annotatedRecordEntries(host.record()).forEach(host::saveEntry)

        val atHandover = handoverNodeId != null
        val s = socket()
        val sid = host.sessionId()
        if (s == null || sid == null || !HandoverState.restoreNeedsLiveChatState(entries(), atHandover)) return onDone(RestoreStep.START)

        // Back in the room first: the server answers only its own session.
        s.joinRoom(sid)
        s.ask("live-chat-state", mapOf("chatSessionId" to sid), 10_000L) { res ->
            val r = HandoverState.restoredLiveChat(res ?: mapOf("status" to "none"), entries(), now().time)
            r.settle?.let(::settle)
            val step = HandoverState.restoreStep(r, atHandover, entries())
            val engine = flow()
            when (step) {
                RestoreStep.LIVE -> {
                    host.setLiveMode(true)
                    r.agent?.let { a ->
                        host.setAgent(Agent(id = a.id, name = a.name, email = a.email))
                        if (r.drawJoin) addJoin(AgentDetails(id = a.id, name = a.name, email = a.email), r.afterAI)
                        if (engine != null && handoverNodeId != null &&
                            engine.waitAt(handoverNodeId, NodeUIState.HumanHandover(NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED, agentName = a.name, nodeId = handoverNodeId))
                        ) engine.handleAgentAccepted(a.name)
                    }
                    if (r.status == "waiting") connecting.start()
                }
                RestoreStep.WAIT -> {
                    // The countdown and the max wait go on from where the server is
                    // (before the step waits again, which starts them).
                    onRestoredWait(r.remainingSeconds)
                    if (engine != null && handoverNodeId != null) {
                        engine.waitAt(handoverNodeId, NodeUIState.HumanHandover(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = handoverNodeId))
                    }
                    queueLeft = false
                    connecting.start()
                }
                RestoreStep.CONTINUE -> {
                    // Ended while the app was closed: say so, as the live event would.
                    if (r.status == "ended") {
                        val id = entryId("chat-ended")
                        // Drawn in the visitor's language; the record keeps the English line.
                        host.addItem(RecordItem.SystemMessage(id = id, time = now(), text = chatEndedLine(), kind = "agent-chat-ended"))
                        host.saveEntry(RecordEntry(id = id, shape = "agent-chat-ended", type = "agent-chat-ended", text = CHAT_ENDED_TEXT))
                    }
                    host.setLiveMode(false)
                    if (engine != null && handoverNodeId != null &&
                        engine.waitAt(handoverNodeId, NodeUIState.HumanHandover(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT, nodeId = handoverNodeId))
                    ) engine.submitResponse(mapOf("action" to "continue"))
                }
                RestoreStep.ASK -> if (engine != null && handoverNodeId != null) engine.resumeFrom(handoverNodeId)
                RestoreStep.START -> {}
            }
            onDone(step)
        }
    }

    fun reset() {
        connecting.end()
        nudge.clear()
        queueLeft = false
    }

    companion object {
        const val CHAT_ENDED_TEXT = "Chat has ended"

        /** The ended line as drawn: the English line, else the language's "Chat ended" (web system.chatEnded). */
        fun chatEndedLine(): String = if (I18n.language == I18n.EN) CHAT_ENDED_TEXT else t("system.chatEnded")
    }
}
