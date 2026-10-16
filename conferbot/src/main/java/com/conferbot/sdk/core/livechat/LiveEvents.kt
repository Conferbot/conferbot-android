package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.t

/**
 * Live chat events beyond the handover line ([HandoverState]): the chat
 * going back to the queue, a transfer, the same end again, a blocked visitor
 * and the closed chat. Ported from the React Native SDK's
 * src/core/liveEvents.ts (web widget):
 *
 *   ts/chat/liveTakeover.ts   live-chat-requeued ("Connecting you to another
 *                             agent..." / "Connecting you to Sales..."),
 *                             live-chat-transferred (kept in the record)
 *   utils/recordIds.ts        endedSinceLastLive: a repeated chat-ended is
 *                             the same end, and draws nothing
 *   ts/chat/closedState.ts    the closed chat ("This conversation has been
 *                             closed."), index.ts _applyVisitorBlock
 *   agentListeners.ts         ai-agent-inactive-close: "Chat paused" or,
 *                             for continue_flow, "Chat ended"
 *
 * Pure: works on the record's types and the socket maps.
 */
object LiveEvents {

    private var seq = 0

    /** Its own id per event entry: two in the same millisecond must not merge (web liveEventId). */
    fun eventId(kind: String, now: Long = System.currentTimeMillis()): String = "$kind-$now-${synchronized(this) { ++seq }}"

    // ---- Back in the queue (live-chat-requeued) ----

    /** The record entry's English text (the web's en takeover.requeued); drawn with [requeuedText]. */
    const val REQUEUED_TEXT = "Connecting you to another agent..."

    /** What the visitor reads when the chat goes back to the queue (web requeuedText). */
    fun requeuedText(teamName: Any?): String {
        val team = (teamName as? String)?.replace(Regex("[<>]"), "")?.trim()?.take(60).orEmpty()
        return if (team.isNotEmpty()) t("takeover.requeuedTeam", "team" to team) else t("takeover.requeued")
    }

    // ---- Transferred (live-chat-transferred) ----

    data class Transfer(val id: String, val text: String, val time: String?, val kind: String?)

    /**
     * The transfer line the server wrote into the record, as the widget
     * keeps it in its own copy (so the next write keeps it). Null when
     * malformed. Not drawn, as on the web: the requeued line or the next
     * agent's join says what happened.
     */
    fun transferOf(data: Map<String, Any?>?): Transfer? {
        val e = data?.get("entry") as? Map<*, *> ?: return null
        val id = e["id"] as? String ?: return null
        val text = e["text"] as? String ?: return null
        return Transfer(id, text.take(200), e["time"] as? String, e["kind"]?.toString())
    }

    // ---- The same end again (chat-ended) ----

    // A live chat going on again: someone joined, the AI came back, or a
    // message went either way (web recordIds.ts LIVE_AGAIN).
    val LIVE_AGAIN = setOf(
        "agent-joined-message", "agent-joined", "ai-agent-resumed",
        "agent-message", "agent-message-file", "agent-message-audio", "agent-message-rich",
        "user-live-message", "user-live-message-file"
    )

    /**
     * Has the live chat already ended, with nothing live since? A repeated
     * chat-ended is then the same end again: no second "Chat ended" line, no
     * second survey (web endedSinceLastLive). [entries]: each record
     * entry's (type, shape), oldest first.
     */
    fun endedSinceLastLive(entries: List<Pair<String?, String?>>): Boolean {
        for (i in entries.indices.reversed()) {
            val (type, shape) = entries[i]
            val kind = type ?: shape
            if (kind == "agent-chat-ended") return true
            if (kind in LIVE_AGAIN || shape in LIVE_AGAIN) return false
        }
        return false
    }

    /** The conversation's record as [endedSinceLastLive] reads it. */
    fun kindsOf(record: List<com.conferbot.sdk.core.state.RecordEntry>): List<Pair<String?, String?>> =
        record.map { (it.type?.takeIf { t -> t.isNotEmpty() }) to it.shape }

    // ---- The AI let an idle visitor go (ai-agent-inactive-close) ----

    /**
     * What an inactivity close does (web agentListeners.ts
     * ai-agent-inactive-close; server aiInactivityHandler.js
     * closeInactiveWebChat, which sends `action`: the agent's onCloseAction).
     *
     *   paused       only paused: "Chat paused" with "Send a message to pick
     *                up where you left off". The composer stays on and the
     *                visitor's next message reopens it (the server's
     *                ai-agent-resumed). Always for an agent bot (no flow), and
     *                for any action but continue_flow.
     *   continueFlow the AI moved on for good: "Chat ended", the live chat is
     *                over and the flow carries on from its handover step along
     *                the next edge (web _handoverProceedToNextStep: no survey).
     */
    data class InactiveClosePlan(val paused: Boolean, val continueFlow: Boolean)

    fun inactiveClosePlan(data: Map<String, Any?>?, agentBotChat: Boolean): InactiveClosePlan {
        val continueFlow = data?.get("action") == "continue_flow" && !agentBotChat
        return InactiveClosePlan(paused = !continueFlow, continueFlow = continueFlow)
    }

    const val INACTIVE_CLOSE = "ai-agent-inactive-close"

    /** The entry's English text (the record's copy); drawn with [inactiveCloseLine]. */
    const val CHAT_PAUSED_TEXT = "Chat paused"

    /**
     * Its record entry: its own id (a second pause must not merge into the
     * first), the server's `action`, and `paused` so a restart draws the same
     * line. Returns the id, the English text and the entry's data.
     */
    data class InactiveCloseEntry(val id: String, val text: String, val paused: Boolean, val data: Map<String, Any?>)

    fun inactiveCloseEntry(data: Map<String, Any?>?, agentBotChat: Boolean, now: Long = System.currentTimeMillis()): InactiveCloseEntry {
        val paused = inactiveClosePlan(data, agentBotChat).paused
        val action = data?.get("action") as? String
        return InactiveCloseEntry(
            id = eventId(INACTIVE_CLOSE, now),
            text = if (paused) CHAT_PAUSED_TEXT else CHAT_ENDED_TEXT,
            paused = paused,
            data = buildMap {
                action?.let { put("action", it) }
                put("paused", paused)
            }
        )
    }

    /** The line as drawn, in the active language: its words and the note under them (none for an end). */
    data class InactiveCloseLine(val text: String, val note: String?, val paused: Boolean)

    /** web system.chatPaused + system.chatPausedNote, or system.chatEnded. An entry without `paused` (older records) is a pause. */
    fun inactiveCloseLine(paused: Boolean?): InactiveCloseLine =
        if (paused == false) InactiveCloseLine(t("system.chatEnded"), null, false)
        else InactiveCloseLine(t("system.chatPaused"), t("system.chatPausedNote"), true)

    // ---- Blocked, and the closed chat ----

    /** The neutral line a blocked visitor sees (embed-server visitorBlock CLOSED_TEXT, web system.closed). */
    const val CLOSED_TEXT = "This conversation has been closed."
    const val CHAT_ENDED_TEXT = "Chat ended"

    /** The closed chat's button, in the active language (the SDK's own words). */
    val NEW_CHAT_TEXT: String get() = t("sdk.live.newChat")

    /**
     * The same words in the active language (web system.closed,
     * system.chatEnded). The English constants above stay as what the record
     * and the server say.
     */
    object Text {
        val closed: String get() = t("system.closed")
        val chatEnded: String get() = t("system.chatEnded")
        val newChat: String get() = t("sdk.live.newChat")
    }

    /** The handshake refused a blocked visitor (connect_error "visitor_blocked"). */
    fun isBlockedError(message: String?): Boolean = message == "visitor_blocked"

    /** The visitor-blocked event's line: the server's words, else ours. */
    fun blockedText(data: Map<String, Any?>?): String =
        (data?.get("message") as? String)?.trim()?.take(200)?.takeIf { it.isNotEmpty() } ?: Text.closed

    enum class ClosedKind { BLOCKED, ENDED }

    data class ClosedState(
        val kind: ClosedKind,
        val text: String,
        /** Offer "Start a new chat" (never to a blocked visitor: the server refuses them). */
        val newChat: Boolean,
        /** The composer takes no more input. */
        val inputOff: Boolean
    )

    /**
     * The chat's closed state, or null while it is open:
     *   blocked  the workspace blocked the visitor: the neutral line, no
     *            input, no new chat.
     *   ended    the live chat ended and nothing is going on (no step
     *            waiting, no person, nothing live since the end): "Chat
     *            ended" and a button to start a new chat. The composer is
     *            off, as the web turns it off on chat-ended.
     */
    fun closedStateOf(entries: List<Pair<String?, String?>>, blocked: String? = null, stepWaiting: Boolean = false, live: Boolean = false): ClosedState? {
        if (!blocked.isNullOrEmpty()) return ClosedState(ClosedKind.BLOCKED, blocked, newChat = false, inputOff = true)
        if (stepWaiting || live) return null
        if (!endedSinceLastLive(entries)) return null
        return ClosedState(ClosedKind.ENDED, Text.chatEnded, newChat = true, inputOff = true)
    }
}
