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
