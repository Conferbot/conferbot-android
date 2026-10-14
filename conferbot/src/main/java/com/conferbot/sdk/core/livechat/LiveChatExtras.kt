package com.conferbot.sdk.core.livechat

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
 * agent asking for a file, the AI's follow-up email card, and the post-chat
 * rating. Owned by [com.conferbot.sdk.core.Conferbot]; the shapes are in
 * [LiveChat].
 */
class LiveChatExtras(
    private val scope: CoroutineScope,
    private val socket: () -> SocketClient?,
    private val sessionId: () -> String?,
    private val addMessage: (RecordItem) -> Unit
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

    // What the visitor already sent from a card (kept here, not in the card,
    // so a re-render does not bring the form back).
    private val _leftMessageTo = MutableStateFlow<String?>(null)
    val leftMessageTo: StateFlow<String?> = _leftMessageTo.asStateFlow()

    private val _leadEmail = MutableStateFlow<String?>(null)
    val leadEmail: StateFlow<String?> = _leadEmail.asStateFlow()

    private val _csatSent = MutableStateFlow(false)
    val csatSent: StateFlow<Boolean> = _csatSent.asStateFlow()

    private var queuePoll: Job? = null
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

        on("queue-status") { if (own(it)) _queue.value = LiveChat.queueStatusOf(toMap(it)) }
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
        on("agent-connection-lost") { if (own(it)) _agentConnectionLost.value = it?.optString("agentName", "").orEmpty().ifBlank { "The agent" } }
        on("agent-connection-restored") { if (own(it)) _agentConnectionLost.value = null }
        on("file-upload-requested") {
            if (!own(it)) return@on
            val who = it?.optString("agentName", "").orEmpty().trim().ifEmpty { "The team" }
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

    /** Where the visitor stands: asked every 20s while a handover waits for a person. */
    fun startQueuePoll() {
        if (queuePoll?.isActive == true) return
        queuePoll = scope.launch {
            while (isActive) {
                val id = sessionId()
                if (id != null) {
                    socket()?.emitWithAck("queue-status", JSONObject().put("chatSessionId", id), 10_000L) { res ->
                        (res as? JSONObject)?.let { json -> LiveChat.queueStatusOf(toMap(json))?.let { _queue.value = it } }
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
                _leftMessageTo.value = email
                _noAgentsAvailable.value = false
            }
            scope.launch(Dispatchers.Main) { onDone(ok) }
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

    fun dismissCsat() {
        _csatOpen.value = false
    }

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
        _leftMessageTo.value = null
        _leadEmail.value = null
        _csatSent.value = false
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
