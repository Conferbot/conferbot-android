package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.RecordItem

/**
 * Where a live-chat handover stands, read from the record, and the
 * "Connecting you with the team…" line that marks it. Ported from the React
 * Native SDK's src/core/handoverState.ts, itself from the web widget:
 * conferbot-widget src/ts/chat/handoverState.ts (the record reading),
 * src/core/events/agentListeners.ts (agent-left reasons, agent-accepted),
 * src/ts/chat/handlers/special.ts (_handleHandoverAccepted, the queue's
 * leave) and src/index.ts (the restore path).
 *
 *   bot / AI  ->  connecting (an AI agent passed the visitor on: the line
 *                 with its typing dots)  ->  one of
 *     JOINED       a person accepted              "Connected with the team"
 *     UNAVAILABLE  nobody could (the AI took the  "The team couldn't join just now"
 *                  chat back, or the node's
 *                  fallback ran)
 *     CANCELLED    the visitor left the queue     "You left the queue"
 *     ENDED        the chat ended first           "Chat ended before the team joined"
 *
 * The dots only run while the wait is real: every way a wait ends settles
 * the line in place, and a restore draws the settled line from the record.
 * The same reading decides whether "Talk to a person" is offered.
 *
 * Pure: works on [HandoverEntry], a view of either record (the screen's
 * [RecordItem]s or the server's [RecordEntry]s). Settling returns new lists.
 */

enum class HandoverOutcome(val value: String) {
    JOINED("joined"), UNAVAILABLE("unavailable"), CANCELLED("cancelled"), ENDED("ended");

    companion object {
        fun of(v: Any?): HandoverOutcome? = values().firstOrNull { it.value == v || it == v }
    }
}

enum class HandoverPhase { NONE, CONNECTING, JOINED }

/** agent-left read (web agentListeners.ts "agent-left"). */
enum class AgentLeftKind { IGNORE, HANDOVER, LEFT }

/** What a restored chat does next (see [HandoverState.restoreStep]). */
enum class RestoreStep { LIVE, WAIT, CONTINUE, ASK, START }

/** One record entry as the handover reading sees it. */
data class HandoverEntry(
    val type: String,
    val agentId: String? = null,
    /** An AI agent's join (recorded by a flow bot's visible AI handover). */
    val ai: Boolean = false,
    /** An agent-left that is an AI handing the visitor to the team. */
    val handover: Boolean = false,
    val settled: HandoverOutcome? = null,
    val afterAI: Boolean = false,
    val takeover: Boolean = false,
    /** The entry's other field (`shape` on the server record, a display kind on screen). */
    val shape: String? = null
)

/** agent-accepted against the record (web special.ts _handleHandoverAccepted). */
data class AcceptedPlan(
    val ai: Boolean,
    /** How a pending Connecting line settles. */
    val outcome: HandoverOutcome,
    /** Draw the join line (not for the same person again). */
    val announce: Boolean,
    /** The person follows an AI handover: the join line's note. */
    val afterAI: Boolean,
    val humanJoined: Boolean
)

data class RestoredAgent(val id: String, val name: String, val email: String? = null)

/** The server's live-chat-state applied to a restored record. */
data class RestoredLiveChat(
    /** live, waiting, fallback, ended or none. */
    val status: String,
    val agent: RestoredAgent?,
    /** How a pending Connecting line settles now; null leaves it pending. */
    val settle: HandoverOutcome?,
    /** Draw the join line the closed app missed. */
    val drawJoin: Boolean,
    val afterAI: Boolean,
    /** Seconds left of the handover's max wait (waiting only). */
    val remainingSeconds: Long?
)

object HandoverState {

    /** The web's English strings (ts/i18n/en.ts handover.*, system.*). */
    const val PENDING_TEXT = "Connecting you with the team…"
    const val HANDED_OVER_NOTE = "They can see your conversation so far"

    fun outcomeText(o: HandoverOutcome): String = when (o) {
        HandoverOutcome.JOINED -> "Connected with the team"
        HandoverOutcome.UNAVAILABLE -> "The team couldn't join just now"
        HandoverOutcome.CANCELLED -> "You left the queue"
        HandoverOutcome.ENDED -> "Chat ended before the team joined"
    }

    /** The line's words: pending, or how the wait ended. */
    fun lineText(settled: HandoverOutcome?): String = settled?.let(::outcomeText) ?: PENDING_TEXT

    /** "<name> joined the chat" (web agentJoinedDivider.ts). */
    fun joinedText(name: String?): String = "${name?.trim().orEmpty().ifEmpty { "An agent" }} joined the chat"

    /** "<name> left the chat", or the pending line for a handover (web agentLeftDivider.ts). */
    fun agentLeftText(name: String?, handover: Boolean): String =
        if (handover) lineText(null) else "${name?.trim().orEmpty().ifEmpty { "The agent" }} left the chat"

    // ---------- reading the record ----------

    private fun isJoin(t: String) = t == "agent-joined-message" || t == "agent-joined"

    fun isAIJoin(r: HandoverEntry): Boolean = r.ai || r.agentId.orEmpty().startsWith("ai-agent-")

    /** Things that close a live-chat episode for good. */
    val CLOSERS = setOf("agent-chat-ended", "agent-declined-message", "queue-self-resolved", "ai-agent-inactive-close")

    fun isHandoverLeft(r: HandoverEntry): Boolean = r.type == "agent-left-chat" && r.handover

    /**
     * Where the latest live episode stands: JOINED (a person, not the AI,
     * joined and has not left), CONNECTING (an AI handed the visitor to the
     * team and no person has joined yet), NONE otherwise.
     */
    fun phase(record: List<HandoverEntry>): HandoverPhase {
        for (r in record.asReversed()) {
            if (r.type in CLOSERS) return HandoverPhase.NONE
            if (isJoin(r.type)) {
                if (isAIJoin(r)) continue
                return HandoverPhase.JOINED
            }
            if (r.type == "agent-left-chat") return if (r.handover) HandoverPhase.CONNECTING else HandoverPhase.NONE
        }
        return HandoverPhase.NONE
    }

    /** "Talk to a person" is offered only when nobody is coming or here (web quickActions.ts). */
    fun talkToPersonAllowed(record: List<HandoverEntry>): Boolean = phase(record) == HandoverPhase.NONE

    /** The same person joining again (a repeat agent-accepted): nothing new to announce. */
    fun isRepeatJoin(record: List<HandoverEntry>, agentId: String?): Boolean {
        if (agentId.isNullOrEmpty()) return false
        for (r in record.asReversed()) {
            if (isJoin(r.type)) return r.agentId.orEmpty() == agentId
            if (r.type == "agent-left-chat" || r.type in CLOSERS) return false
        }
        return false
    }

    /**
     * How the handover line at [index] ended, from what came after it; null
     * while it is still pending. A settled outcome already written wins.
     */
    fun outcomeAt(record: List<HandoverEntry>, index: Int): HandoverOutcome? {
        val entry = record.getOrNull(index) ?: return null
        entry.settled?.let { return it }
        for (i in index + 1 until record.size) {
            val r = record[i]
            if (isJoin(r.type)) return if (isAIJoin(r)) HandoverOutcome.UNAVAILABLE else HandoverOutcome.JOINED
            when (r.type) {
                "agent-declined-message" -> return HandoverOutcome.UNAVAILABLE
                "queue-self-resolved" -> return HandoverOutcome.CANCELLED
                "agent-chat-ended", "ai-agent-inactive-close" -> return HandoverOutcome.ENDED
            }
            // Escalated again: this earlier wait was over one way or another.
            if (isHandoverLeft(r)) return HandoverOutcome.UNAVAILABLE
        }
        return null
    }

    /** Is a handover line still waiting (so a repeat escalation draws none)? */
    fun hasPendingLine(record: List<HandoverEntry>): Boolean = record.any { isHandoverLeft(it) && it.settled == null }

    /** Did an AI agent hand this chat to a person (so the person has read it)? */
    fun aiHandedOver(record: List<HandoverEntry>): Boolean = record.any(::isHandoverLeft)

    /** The indexes of pending handover lines (settled all at once). */
    fun pendingIndexes(record: List<HandoverEntry>): List<Int> =
        record.indices.filter { isHandoverLeft(record[it]) && record[it].settled == null }

    /**
     * Before a restored record is drawn: the outcome each pending handover
     * line already has in the record (by index), and the person's joins that
     * followed an AI handover (they draw [HANDED_OVER_NOTE]).
     */
    data class Annotations(val settle: Map<Int, HandoverOutcome>, val afterAI: Set<Int>) {
        val changed: Int get() = settle.size + afterAI.size
    }

    fun annotateRestored(record: List<HandoverEntry>): Annotations {
        val settle = mutableMapOf<Int, HandoverOutcome>()
        val afterAI = mutableSetOf<Int>()
        var handedOver = false
        record.forEachIndexed { i, r ->
            if (isHandoverLeft(r)) {
                handedOver = true
                if (r.settled == null) outcomeAt(record, i)?.let { settle[i] = it }
                return@forEachIndexed
            }
            if (isJoin(r.type) && handedOver && !isAIJoin(r) && !r.afterAI) afterAI += i
            if (r.type in CLOSERS) handedOver = false
        }
        return Annotations(settle, afterAI)
    }

    // ---------- socket events ----------

    /**
     * What an agent-left means: IGNORE an AI pausing an idle visitor (reason
     * visitor_inactive); HANDOVER an AI passing the visitor to a person
     * (reason escalated, handover: true, or the leaver is an AI agent); LEFT
     * a person left.
     */
    fun agentLeftKind(data: Map<String, Any?>?): AgentLeftKind {
        if (data?.get("reason") == "visitor_inactive") return AgentLeftKind.IGNORE
        val details = data?.get("agentDetails") as? Map<*, *>
        if (data?.get("reason") == "escalated" || data?.get("handover") == true || details?.get("isAIAgent") == true) return AgentLeftKind.HANDOVER
        return AgentLeftKind.LEFT
    }

    /** An agent-accepted from an AI agent (taking the chat, or taking it back). */
    fun isAIAccept(data: Map<String, Any?>?): Boolean {
        val details = data?.get("agentDetails") as? Map<*, *>
        return details?.get("isAIAgent") == true || details?.get("_id")?.toString().orEmpty().startsWith("ai-agent-")
    }

    fun acceptedPlan(record: List<HandoverEntry>, data: Map<String, Any?>?): AcceptedPlan {
        val ai = isAIAccept(data)
        val id = (data?.get("agentDetails") as? Map<*, *>)?.get("_id")?.toString()
        val repeat = isRepeatJoin(record, id)
        return AcceptedPlan(
            ai = ai,
            outcome = if (ai) HandoverOutcome.UNAVAILABLE else HandoverOutcome.JOINED,
            announce = !repeat,
            afterAI = !ai && aiHandedOver(record),
            humanJoined = !ai && !repeat
        )
    }

    // ---------- leaving the queue ----------

    /** queue-self-resolve: the visitor leaves the queue (embed-server handoverHandler.js). */
    fun queueSelfResolvePayload(chatSessionId: String, articleId: String? = null): Map<String, Any> =
        buildMap {
            put("chatSessionId", chatSessionId)
            if (!articleId.isNullOrEmpty()) put("articleId", articleId)
        }

    /**
     * The server's answer to queue-self-resolve: true to leave the wait (ok,
     * nothing waiting, or no answer), false when a person already picked the
     * chat up. On not_waiting, [state] is live-chat-state's answer: live
     * means a person has it (an accepted ticket is in progress, not open).
     */
    fun queueLeaves(res: Map<String, Any?>?, state: Map<String, Any?>? = null): Boolean =
        !(res?.get("reason") == "agent_joined" || (res?.get("ok") != true && state?.get("status") == "live"))

    /** Ask live-chat-state before leaving on this answer to queue-self-resolve? */
    fun queueLeaveNeedsState(res: Map<String, Any?>?): Boolean = res?.get("ok") != true && res?.get("reason") == "not_waiting"

    // ---------- restore (live-chat-state) ----------

    private val RESTORE_LIVE_TYPES = setOf(
        "agent-joined-message", "agent-message", "agent-message-file", "agent-message-audio",
        "agent-message-rich", "user-live-message", "user-live-message-file", "agent-left-chat"
    )
    private val EPISODE_CLOSERS = setOf("agent-declined-message", "agent-chat-ended", "queue-self-resolved")

    private fun isLive(r: HandoverEntry) = r.type in RESTORE_LIVE_TYPES || r.shape in RESTORE_LIVE_TYPES

    /**
     * A restored chat: should the server be asked where it stands
     * (live-chat-state)? Only the latest live episode counts. [atHandover]:
     * the flow was waiting on its handover step.
     */
    fun restoreNeedsLiveChatState(record: List<HandoverEntry>, atHandover: Boolean = false): Boolean {
        var start = 0
        record.forEachIndexed { i, r ->
            if ((r.type == "agent-left-chat" && r.takeover) || r.type in EPISODE_CLOSERS) start = i + 1
        }
        val ongoing = record.drop(start).any(::isLive)
        val ended = record.any { it.type in EPISODE_CLOSERS } && !ongoing
        return ongoing || (atHandover && !ended)
    }

    /**
     * The server's live-chat-state (embed-server liveChatSession
     * getLiveChatState: status live | waiting | fallback | ended | none,
     * agentDetails, waitEndsAt) applied to a restored record (web index.ts
     * _applyRestoredLiveChatState / _restoreAgentBotHandover).
     */
    fun restoredLiveChat(state: Map<String, Any?>?, record: List<HandoverEntry>, now: Long = System.currentTimeMillis()): RestoredLiveChat {
        val raw = state?.get("status")?.toString() ?: "none"
        val details = state?.get("agentDetails") as? Map<*, *>
        val person = details?.takeIf { it["isAIAgent"] != true }
        if (raw == "live" && person != null) {
            return RestoredLiveChat(
                status = "live",
                agent = RestoredAgent(
                    id = person["_id"]?.toString().orEmpty(),
                    name = person["name"]?.toString().orEmpty(),
                    email = person["email"]?.toString()?.takeIf { it.isNotEmpty() }
                ),
                settle = HandoverOutcome.JOINED,
                drawJoin = phase(record) != HandoverPhase.JOINED,
                afterAI = aiHandedOver(record),
                remainingSeconds = null
            )
        }
        if (raw == "waiting") {
            val ends = (state?.get("waitEndsAt") as? Number)?.toLong()
                ?: state?.get("waitEndsAt")?.toString()?.toLongOrNull()
            return RestoredLiveChat(
                "waiting", null, null, false, false,
                if (ends != null && ends > 0) maxOf(5L, Math.round((ends - now) / 1000.0)) else null
            )
        }
        if (raw == "ended") return RestoredLiveChat("ended", null, HandoverOutcome.ENDED, false, false, null)
        if (raw == "fallback") return RestoredLiveChat("fallback", null, HandoverOutcome.UNAVAILABLE, false, false, null)
        // Live with nobody in it (every agent left), or no handover at all.
        return RestoredLiveChat("none", null, if (hasPendingLine(record)) HandoverOutcome.UNAVAILABLE else null, false, false, null)
    }

    /**
     * What a restored chat does next: LIVE (a person, or the AI while still
     * waiting after its escalation, has the chat), WAIT (still in the queue
     * at the flow's handover step: back on its wait without asking again),
     * CONTINUE (the wait or live chat is over: the flow carries on from the
     * step), ASK (the request never went out: run the step again), START
     * (nothing live to rejoin).
     */
    fun restoreStep(r: RestoredLiveChat, atHandover: Boolean, record: List<HandoverEntry> = emptyList()): RestoreStep {
        if (r.status == "live") return RestoreStep.LIVE
        if (r.status == "waiting") return if (atHandover) RestoreStep.WAIT else RestoreStep.LIVE
        if (!atHandover) return RestoreStep.START
        val asked = record.any(::isLive)
        return if (r.status == "none" && !asked && !hasPendingLine(record)) RestoreStep.ASK else RestoreStep.CONTINUE
    }

    // ---------- the two records ----------

    /** The screen's record as handover entries. */
    fun entriesOfItems(items: List<RecordItem>): List<HandoverEntry> = items.map(::entryOf)

    fun entryOf(item: RecordItem): HandoverEntry = when (item) {
        is RecordItem.AgentJoinedMessage -> HandoverEntry(
            "agent-joined-message", agentId = item.agentDetails.id, ai = item.agentDetails.isAIAgent, afterAI = item.afterAI
        )
        is RecordItem.AgentLeftMessage -> HandoverEntry(
            "agent-left-chat", agentId = item.agentDetails?.id, handover = item.handover, settled = HandoverOutcome.of(item.settled)
        )
        is RecordItem.SystemMessage -> HandoverEntry(item.kind ?: "system-message")
        is RecordItem.BotMessage -> HandoverEntry(if (item.id.startsWith("agent-declined-")) "agent-declined-message" else "bot-message")
        else -> HandoverEntry(item.type.value)
    }

    /** The server record (ChatState) as handover entries. */
    fun entriesOfRecord(record: List<RecordEntry>): List<HandoverEntry> = record.map { e ->
        HandoverEntry(
            type = e.type ?: e.shape,
            agentId = e.data["agentId"]?.toString(),
            ai = e.data["ai"] == true,
            handover = e.data["handover"] == true,
            settled = HandoverOutcome.of(e.data["settled"]),
            afterAI = e.data["afterAI"] == true,
            takeover = e.data["takeover"] == true,
            shape = e.shape
        )
    }

    /** Settle the screen's pending handover lines; the same list when none was pending. */
    fun settleItems(items: List<RecordItem>, outcome: HandoverOutcome): List<RecordItem> {
        var n = 0
        val next = items.map {
            if (it is RecordItem.AgentLeftMessage && it.handover && it.settled == null) {
                n++
                it.copy(settled = outcome.value, text = outcomeText(outcome))
            } else it
        }
        return if (n > 0) next else items
    }

    /** The server record's pending handover lines, settled (to save back). */
    fun settledRecordEntries(record: List<RecordEntry>, outcome: HandoverOutcome): List<RecordEntry> =
        record.filter { (it.type ?: it.shape) == "agent-left-chat" && it.data["handover"] == true && it.data["settled"] == null }
            .map { it.copy(text = outcomeText(outcome), data = (it.data + ("settled" to outcome.value)).toMutableMap()) }

    /** The screen's record with the restore annotations applied. */
    fun annotateItems(items: List<RecordItem>): List<RecordItem> {
        val a = annotateRestored(entriesOfItems(items))
        if (a.changed == 0) return items
        return items.mapIndexed { i, it ->
            when {
                it is RecordItem.AgentLeftMessage && a.settle.containsKey(i) ->
                    a.settle.getValue(i).let { o -> it.copy(settled = o.value, text = outcomeText(o)) }
                it is RecordItem.AgentJoinedMessage && i in a.afterAI -> it.copy(afterAI = true)
                else -> it
            }
        }
    }

    /** The server record's entries the restore annotations change (to save back). */
    fun annotatedRecordEntries(record: List<RecordEntry>): List<RecordEntry> {
        val a = annotateRestored(entriesOfRecord(record))
        return record.mapIndexedNotNull { i, e ->
            when {
                a.settle.containsKey(i) -> a.settle.getValue(i).let { o ->
                    e.copy(text = outcomeText(o), data = (e.data + ("settled" to o.value)).toMutableMap())
                }
                i in a.afterAI -> e.copy(data = (e.data + ("afterAI" to true)).toMutableMap())
                else -> null
            }
        }
    }
}
