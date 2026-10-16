package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.services.SocketClient
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live chat beyond the messages, as the web widget has it: the visitor's
 * place in the queue, what happens when nobody can join (an email for the
 * reply, leave a message), read receipts both ways, a conversation locked or
 * its composer turned off by the agent, an agent's dropped connection, an
 * agent asking for a file, the AI's follow-up email card, the post-chat
 * rating, a visitor the workspace blocked ([LiveEvents]) and "Email me this
 * chat" ([EmailTranscript]). Owned by [com.conferbot.sdk.core.Conferbot]; the shapes are in
 * [LiveChat].
 */
class LiveChatExtras(
    private val scope: CoroutineScope,
    private val socket: () -> SocketClient?,
    private val sessionId: () -> String?,
    private val addMessage: (RecordItem) -> Unit,
    /** The flow carries on from the handover (a message was left, or skipped). */
    private val continueFlow: () -> Unit = {},
    /** "Email me this chat": the server's transcript email (ApiClient.sendTranscriptEmail). */
    private val postTranscriptEmail: suspend (chatSessionId: String, email: String) -> EmailTranscript.Result = { _, _ -> EmailTranscript.Result(false) },
    /** Every queue-status as the server sent it, the event or the poll's answer (its `help`: core/waiting/WaitingExtras). */
    private val onQueueStatus: (Map<String, Any?>?) -> Unit = {}
) {
    private val _queue = MutableStateFlow<QueueStatus?>(null)
    val queue: StateFlow<QueueStatus?> = _queue.asStateFlow()

    private val _handoverWaiting = MutableStateFlow<HandoverWaiting?>(null)
    val handoverWaiting: StateFlow<HandoverWaiting?> = _handoverWaiting.asStateFlow()

    private val _noAgentsAvailable = MutableStateFlow(false)
    val noAgentsAvailable: StateFlow<Boolean> = _noAgentsAvailable.asStateFlow()

    private val _lockedBy = MutableStateFlow<String?>(null)
    val lockedBy: StateFlow<String?> = _lockedBy.asStateFlow()

    private val _visitorInputEnabled = MutableStateFlow(true)
    val visitorInputEnabled: StateFlow<Boolean> = _visitorInputEnabled.asStateFlow()

    private val _agentConnectionLost = MutableStateFlow<String?>(null)
    val agentConnectionLost: StateFlow<String?> = _agentConnectionLost.asStateFlow()

    private val _fileRequestedBy = MutableStateFlow<String?>(null)
    val fileRequestedBy: StateFlow<String?> = _fileRequestedBy.asStateFlow()

    private val _receiptMarks = MutableStateFlow(ReceiptMarks())
    val receiptMarks: StateFlow<ReceiptMarks> = _receiptMarks.asStateFlow()

    private val _leadCardOpen = MutableStateFlow(false)
    val leadCardOpen: StateFlow<Boolean> = _leadCardOpen.asStateFlow()

    private val _csatConfig = MutableStateFlow<CsatConfig?>(null)
    val csatConfig: StateFlow<CsatConfig?> = _csatConfig.asStateFlow()

    private val _csatOpen = MutableStateFlow(false)
    val csatOpen: StateFlow<Boolean> = _csatOpen.asStateFlow()

    // The flow's end asked for the rating: it shows 1.5 s later (web index.ts).
    private val _csatDue = MutableStateFlow(false)
    val csatDue: StateFlow<Boolean> = _csatDue.asStateFlow()
    private var csatDueJob: Job? = null

    // What the visitor already sent from a card (kept here, not in the card,
    // so a re-render does not bring the form back).
    private val _leftMessageTo = MutableStateFlow<String?>(null)
    val leftMessageTo: StateFlow<String?> = _leftMessageTo.asStateFlow()

    private val _leadEmail = MutableStateFlow<String?>(null)
    val leadEmail: StateFlow<String?> = _leadEmail.asStateFlow()

    private val _csatSent = MutableStateFlow(false)
    val csatSent: StateFlow<Boolean> = _csatSent.asStateFlow()

    // The workspace blocked this visitor: the closed line (LiveEvents).
    private val _blocked = MutableStateFlow<String?>(null)
    val blocked: StateFlow<String?> = _blocked.asStateFlow()

    // "Email me this chat": the card is up, and where it went once sent.
    private val _mailOpen = MutableStateFlow(false)
    val mailOpen: StateFlow<Boolean> = _mailOpen.asStateFlow()

    private val _mailSentTo = MutableStateFlow<String?>(null)
    val mailSentTo: StateFlow<String?> = _mailSentTo.asStateFlow()

    private var queuePoll: Job? = null
    private var waitTimer: Job? = null
    private var reportedDelivered = ""
    private var reportedSeen = ""

    private fun own(data: JSONObject?): Boolean {
        val id = data?.optString("chatSessionId", "").orEmpty()
        val mine = sessionId()
        return id.isEmpty() || mine == null || id == mine
    }

    /** fetched-chatbot-data: the bot's post-chat rating settings. */
    fun setChatbotData(chatbotData: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        _csatConfig.value = LiveChat.csatConfigOf(chatbotData["csatConfig"] as? Map<String, Any?>)
    }

    fun setupListeners() {
        val s = socket() ?: return
        fun on(event: String, fn: (JSONObject?) -> Unit) = s.on(event, Emitter.Listener { args -> fn(args.firstOrNull() as? JSONObject) })

        on("queue-status") {
            if (!own(it)) return@on
            val data = toMap(it)
            _queue.value = LiveChat.queueStatusOf(data)
            onQueueStatus(data)
        }
        on("handover-waiting") {
            if (!own(it)) return@on
            _queue.value = null
            _handoverWaiting.value = LiveChat.handoverWaitingOf(toMap(it))
        }
        on("no-agents-available") {
            _queue.value = null
            stopQueuePoll()
            _noAgentsAvailable.value = true
        }
        on("agent-accepted") {
            // A person (or the AI taking it back) answered: the wait is over.
            _queue.value = null
            stopQueuePoll()
            _noAgentsAvailable.value = false
            val details = it?.optJSONObject("agentDetails")
            if (details != null && !details.optBoolean("isAIAgent", false)) _handoverWaiting.value = null
        }
        on("conversation-locked") { if (own(it)) _lockedBy.value = it?.optString("lockedByName", "").orEmpty().ifBlank { "the team" } }
        on("conversation-unlocked") { if (own(it)) _lockedBy.value = null }
        on("visitor-input-toggled") { if (own(it)) _visitorInputEnabled.value = it?.optBoolean("isInputEnabled", true) != false }
        on("agent-connection-lost") { if (own(it)) _agentConnectionLost.value = it?.optString("agentName", "").orEmpty().ifBlank { t("agent.theAgent") } }
        on("agent-connection-restored") { if (own(it)) _agentConnectionLost.value = null }
        on("file-upload-requested") {
            if (!own(it)) return@on
            val who = it?.optString("agentName", "").orEmpty().trim().ifEmpty { t("agent.theTeam") }
            _fileRequestedBy.value = who
            ChatState.pushToRecord(
                RecordEntry(
                    id = "file-request-${System.currentTimeMillis()}",
                    shape = "agent-file-request",
                    type = "agent-file-request",
                    data = mutableMapOf("agentName" to who)
                )
            )
        }
        on("agent-read-status") { if (own(it)) _receiptMarks.value = LiveChat.receiptMarksOf(toMap(it)) }
        on("chat-ended") {
            if (!own(it)) return@on
            // The same end again: one survey, not two (LiveEvents).
            if (LiveEvents.endedSinceLastLive(LiveEvents.kindsOf(ChatState.record.value))) return@on
            _queue.value = null
            stopQueuePoll()
            _lockedBy.value = null
            _fileRequestedBy.value = null
            _csatOpen.value = true
        }
        on("agent-message") {
            // Buying intent and no email yet: the one-field follow-up card.
            val ai = it?.optJSONObject("agentDetails")?.optBoolean("isAIAgent", false) == true
            if (ai && it?.optBoolean("leadCard", false) == true) _leadCardOpen.value = true
        }
    }

    /**
     * Blocked by the workspace, mid-chat (visitor-blocked) or at the
     * handshake (web _applyVisitorBlock): the chat closes with a neutral
     * line, whatever was waiting goes, and nothing more is taken.
     */
    fun block(data: Map<String, Any?>?) {
        _blocked.value = LiveEvents.blockedText(data)
        stopQueuePoll()
        stopWaitTimer()
        _queue.value = null
        _handoverWaiting.value = null
        _noAgentsAvailable.value = false
        _leadCardOpen.value = false
        _fileRequestedBy.value = null
        _csatOpen.value = false
        cancelCsatDue()
        _mailOpen.value = false
    }

    /** "Email me this chat" from the menu: the card, once at a time (web mountEmailTranscript). */
    fun openEmailTranscript() {
        if (_blocked.value != null) return
        _mailSentTo.value = null
        _mailOpen.value = true
    }

    fun closeEmailTranscript() {
        _mailOpen.value = false
    }

    /** The email the card starts with: the visitor's, when known. */
    fun knownEmail(): String = LiveChat.knownEmail(ChatState.userMetadata.value.email, ChatState.getAnswerVariablesMap().values.toList())

    /**
     * Send the transcript to this address. Sent: the address is kept as the
     * visitor's (user metadata, the email answer) and the record goes, as
     * the web's rememberEmail does. [onDone] gets the result on the main thread.
     */
    fun emailTranscript(email: String, onDone: (EmailTranscript.Result) -> Unit) {
        val id = sessionId()
        val address = email.trim()
        if (id == null) return onDone(EmailTranscript.Result(false))
        scope.launch {
            val r = runCatching { postTranscriptEmail(id, address) }.getOrElse { EmailTranscript.Result(false) }
            if (r.ok) {
                ChatState.setUserMetadata("email", address)
                ChatState.setAnswerVariableByKey("email", address)
                sendRecord()
                _mailSentTo.value = address
            }
            withContext(Dispatchers.Main) { onDone(r) }
        }
    }

    /** Where the visitor stands: asked every 20s while a handover waits for a person. */
    fun startQueuePoll() {
        if (queuePoll?.isActive == true) return
        queuePoll = scope.launch {
            while (isActive) {
                val id = sessionId()
                if (id != null) {
                    socket()?.emitWithAck("queue-status", JSONObject().put("chatSessionId", id), 10_000L) { res ->
                        (res as? JSONObject)?.let { json ->
                            val data = toMap(json)
                            LiveChat.queueStatusOf(data)?.let { _queue.value = it }
                            onQueueStatus(data)
                        }
                    }
                }
                delay(LiveChat.QUEUE_POLL_MS)
            }
        }
    }

    fun stopQueuePoll() {
        queuePoll?.cancel()
        queuePoll = null
    }

    // When the handover's max wait runs out (ms since the epoch) while a
    // flow's handover waits for a person: the wait card counts down to it.
    private val _waitEndsAt = MutableStateFlow<Long?>(null)
    val waitEndsAt: StateFlow<Long?> = _waitEndsAt.asStateFlow()

    // A restored wait's seconds left (the server's), for the next wait to start from.
    @Volatile private var restoredRemaining: Long? = null

    /** A restored wait for a person: its seconds left, before the wait starts again (HandoverLifecycle). */
    fun resumeWaitWith(remainingSeconds: Long?) {
        restoredRemaining = remainingSeconds
    }

    /**
     * The handover step's max wait: once it passes with nobody joined, the
     * server is asked to decide (handover-timeout) and answers with
     * no-agents-available. Once per wait. A restored wait goes on from the
     * server's seconds left (web special.ts _attachHandoverWait); the wait
     * card counts down to the same deadline ([waitEndsAt]).
     */
    fun startWaitTimer(nodeData: Map<String, Any?>?, workspaceId: String?, now: () -> Long = System::currentTimeMillis) {
        if (waitTimer?.isActive == true) return
        val deadline = LiveChat.waitDeadline(nodeData?.get("maxWaitTime"), restoredRemaining, now())
        restoredRemaining = null
        _waitEndsAt.value = deadline
        waitTimer = scope.launch {
            delay(maxOf(0L, deadline - now()))
            val id = sessionId() ?: return@launch
            socket()?.emit("handover-timeout", JSONObject(LiveChat.handoverTimeoutPayload(id, nodeData, workspaceId)))
        }
    }

    fun stopWaitTimer() {
        waitTimer?.cancel()
        waitTimer = null
        _waitEndsAt.value = null
    }

    /**
     * The visitor's receipts for the agent's newest message: delivered when it
     * arrives, seen while the chat is on screen.
     */
    fun reportReceipts(record: List<RecordItem>, chatVisible: Boolean) {
        val latest = LiveChat.latestAgentMessageId(entriesOf(record))
        val id = sessionId() ?: return
        if (latest.isEmpty()) return
        val s = socket() ?: return
        if (reportedDelivered != latest) {
            reportedDelivered = latest
            s.emit("visitor-read-receipt", JSONObject().put("chatSessionId", id).put("messageId", latest).put("state", "delivered"))
        }
        if (chatVisible && reportedSeen != latest) {
            reportedSeen = latest
            s.emit("visitor-read-receipt", JSONObject().put("chatSessionId", id).put("messageId", latest).put("state", "seen"))
        }
    }

    /** "Leave a message" when nobody is available: by email. [onDone] gets ok on the main thread. */
    fun leaveMessage(email: String, message: String, onDone: (Boolean) -> Unit) {
        val id = sessionId()
        val entryId = "leave-message-${System.currentTimeMillis()}-${(Math.random() * 1e9).toLong().toString(36)}"
        val payload = JSONObject().put("chatSessionId", id).put("email", email).put("message", message)
            .put("entryId", entryId).put("visitorId", ChatState.visitorId ?: id)
        val s = socket() ?: return onDone(false)
        s.emitWithAck("leave-message", payload, 15_000L) { res ->
            val ok = (res as? JSONObject)?.optBoolean("ok", false) == true
            if (ok) {
                ChatState.pushToRecord(
                    RecordEntry(
                        id = entryId,
                        shape = "user-message",
                        type = "user-message",
                        text = message,
                        data = mutableMapOf("via" to "email", "data" to mapOf("leftMessage" to true, "email" to email))
                    )
                )
                addMessage(RecordItem.UserMessage(id = entryId, time = java.util.Date(), text = message))
                ChatState.setUserMetadata("email", email)
                _leftMessageTo.value = email
                _noAgentsAvailable.value = false
            }
            scope.launch(Dispatchers.Main) {
                onDone(ok)
                if (ok) continueFlow()
            }
        }
    }

    /** The email the team replies to when nobody could join (handover-waiting). */
    fun shareEmailForReply(email: String, onDone: (Boolean) -> Unit) {
        val id = sessionId()
        val entryId = "handover-email-${System.currentTimeMillis()}-${(Math.random() * 1e9).toLong().toString(36)}"
        val payload = JSONObject().put("chatSessionId", id).put("email", email).put("entryId", entryId)
            .put("visitorId", ChatState.visitorId ?: id)
        val s = socket() ?: return onDone(false)
        s.emitWithAck("handover-email", payload, 15_000L) { res ->
            val ok = (res as? JSONObject)?.optBoolean("ok", false) == true
            if (ok) {
                ChatState.pushToRecord(
                    RecordEntry(
                        id = entryId,
                        shape = "user-message",
                        type = "user-message",
                        text = email,
                        data = mutableMapOf("via" to "email", "data" to mapOf("handoverEmail" to true, "email" to email))
                    )
                )
                _handoverWaiting.value = _handoverWaiting.value?.copy(askEmail = false, email = email)
            }
            scope.launch(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** The AI's follow-up card: the email kept as the visitor's, the record sent. */
    fun captureLeadEmail(email: String) {
        ChatState.setUserMetadata("email", email)
        ChatState.setAnswerVariableByKey("email", email)
        sendRecord()
        _leadEmail.value = email
        _leadCardOpen.value = false
    }

    /** The post-chat rating (value: Int, or Boolean for thumbs). */
    fun submitRating(value: Any, feedback: String?) {
        val config = _csatConfig.value ?: return
        val id = sessionId() ?: return
        socket()?.emit("submit-chat-rating", JSONObject(LiveChat.ratingPayload(id, config.ratingType, value, feedback)))
        _csatSent.value = true
    }

    /** "Not now" on leave a message: the flow carries on. */
    fun skipLeaveMessage() {
        _noAgentsAvailable.value = false
        continueFlow()
    }

    /** Prefill for leave a message: their email if known, and what they last asked. */
    fun leaveMessagePrefill(): Pair<String, String> {
        val email = LiveChat.knownEmail(ChatState.userMetadata.value.email, ChatState.getAnswerVariablesMap().values.toList())
        return email to LiveChat.lastQuestion(ChatState.getRecordForServer())
    }

    fun dismissCsat() {
        _csatOpen.value = false
    }

    /**
     * The flow reached its end (web index.ts, the end of a path that is not
     * a sub-flow's): the post-chat rating [delayMs] later, as an agent ending
     * the chat opens it, when the bot has one on, the visitor is not blocked
     * and none is up or sent for this conversation.
     */
    fun flowEnded(delayMs: Long = PostChatRedirect.SETTLE_MS) {
        if (!rateAfterFlowEnd()) return
        csatDueJob?.cancel()
        _csatDue.value = true
        csatDueJob = scope.launch {
            delay(delayMs)
            _csatDue.value = false
            if (rateAfterFlowEnd()) _csatOpen.value = true
        }
    }

    private fun rateAfterFlowEnd() = _csatConfig.value != null && _blocked.value == null && !_csatSent.value && !_csatOpen.value

    private fun cancelCsatDue() {
        csatDueJob?.cancel()
        csatDueJob = null
        _csatDue.value = false
    }

    /** The post-chat rating is up (or about to be) and not answered yet: the redirect waits for it. */
    val ratingPending: Boolean
        get() = _csatConfig.value != null && ((_csatOpen.value && !_csatSent.value) || _csatDue.value)

    /** The visitor's file in a live chat, uploaded already: recorded as the web widget does. */
    fun sendLiveFile(url: String, fileName: String?, mimeType: String?) {
        val id = "user-file-${System.currentTimeMillis()}-${(Math.random() * 1e9).toLong().toString(36)}"
        ChatState.pushToRecord(RecordEntry(id = id, shape = "user-live-message-file", type = "user-live-message-file", data = mutableMapOf("url" to url)))
        addMessage(RecordItem.UserLiveMessageFile(id = id, time = java.util.Date(), url = url, fileName = fileName, mimeType = mimeType))
        sendRecord()
        _fileRequestedBy.value = null
    }

    private fun sendRecord() {
        socket()?.sendResponseRecord(ChatState.buildResponseData())
    }

    fun reset() {
        stopQueuePoll()
        stopWaitTimer()
        restoredRemaining = null
        _queue.value = null
        _handoverWaiting.value = null
        _noAgentsAvailable.value = false
        _lockedBy.value = null
        _visitorInputEnabled.value = true
        _agentConnectionLost.value = null
        _fileRequestedBy.value = null
        _receiptMarks.value = ReceiptMarks()
        _leadCardOpen.value = false
        _csatOpen.value = false
        cancelCsatDue()
        _leftMessageTo.value = null
        _leadEmail.value = null
        _csatSent.value = false
        _blocked.value = null
        _mailOpen.value = false
        _mailSentTo.value = null
        reportedDelivered = ""
        reportedSeen = ""
    }

    companion object {
        /** A record as the receipt logic sees it. */
        fun entriesOf(record: List<RecordItem>): List<LiveEntry> = record.map { LiveEntry(it.id, it.type.value) }

        fun toMap(json: JSONObject?): Map<String, Any?>? {
            json ?: return null
            val out = mutableMapOf<String, Any?>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next().toString()
                out[k] = when (val v = json.opt(k)) {
                    is JSONObject -> toMap(v)
                    is JSONArray -> (0 until v.length()).map { i -> v.opt(i).let { e -> if (e is JSONObject) toMap(e) else e } }
                    JSONObject.NULL -> null
                    else -> v
                }
            }
            return out
        }
    }
}
