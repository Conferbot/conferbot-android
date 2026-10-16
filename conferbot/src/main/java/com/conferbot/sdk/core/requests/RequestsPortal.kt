package com.conferbot.sdk.core.requests

import com.conferbot.sdk.core.flow.FlowCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A reply on its way, or one that did not go (web `.cb-req__msg.is-pending`). */
data class PendingReply(val entryId: String, val text: String, val at: String, val status: Status) {
    enum class Status { SENDING, FAILED, LOCKED }
}

data class RequestsPortalState(
    val screen: Screen = Screen.CLOSED,
    /** The bar's title. */
    val title: String = "",
    val loading: Boolean = false,
    /** It could not be read: "We couldn't load this just now." and Try again ([RequestsPortal.reload]). */
    val failed: Boolean = false,
    /** The list screen's requests, newest replies first; null until read. */
    val list: List<RequestSummary>? = null,
    /** When each was last read here, for "New reply". */
    val seen: Map<String, Long> = emptyMap(),
    /** The open request. */
    val request: RequestDetail? = null,
    val requestState: RequestState? = null,
    /** The banner: where it stands and what happens next. */
    val banner: StateWords? = null,
    val pending: List<PendingReply> = emptyList(),
    val draft: String = "",
    val placeholder: String = Requests.REPLY_PLACEHOLDER,
    val canSend: Boolean = false
) {
    enum class Screen { CLOSED, LIST, REQUEST }
}

/**
 * "Your requests": the screens as states (web conferbot-widget
 * src/ts/home/requests/view.ts mountRequestsView; RN core/requestsPortal.ts).
 * One thing at a time:
 *
 *   list      each request's question and where it stands, in words
 *   request   a banner saying where it stands and what happens next, the
 *             conversation, and a reply box with a Send button. A reply to
 *             a solved request opens it again; the banner says so first.
 *
 * Back always goes one step back. Team replies arrive live while a request
 * is open (`my-request-update`), keeping what the visitor was typing and
 * any reply that did not go out. Views draw [state] and call the actions;
 * nothing here touches Compose. Call it on one thread (the main thread);
 * the api's answers are posted there.
 */
class RequestsPortal(
    private val api: RequestsApi,
    /** When each request was last read here, and marking one read. */
    private val seen: () -> Map<String, Long>,
    private val markSeen: (String, Long) -> Unit,
    /** The request is the chat on screen right now: open that instead. */
    private val isCurrentChat: (String) -> Boolean,
    private val openChat: () -> Unit,
    /** Something changed (the portal closed): read the list again. */
    private val onChanged: () -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() },
    private val rand: () -> Double = { Math.random() }
) {
    private val _state = MutableStateFlow(RequestsPortalState())
    val state: StateFlow<RequestsPortalState> = _state.asStateFlow()
    private val s get() = _state.value

    private var openId: String? = null
    private var fromList = false
    private val drafts = mutableMapOf<String, String>()
    private var lastLoad: (() -> Unit)? = null
    /** Each load's turn: a late answer for a screen that is gone is dropped. */
    private var turn = 0

    val isOpen: Boolean get() = s.screen != RequestsPortalState.Screen.CLOSED

    private fun set(next: RequestsPortalState) {
        _state.value = if (next.screen == RequestsPortalState.Screen.REQUEST) {
            val draft = openId?.let { drafts[it] }.orEmpty()
            next.copy(draft = draft, canSend = draft.isNotBlank())
        } else next
    }

    private fun leave() {
        openId?.let { api.close(it) }
        openId = null
        turn++
        val was = s.screen
        _state.value = RequestsPortalState()
        if (was != RequestsPortalState.Screen.CLOSED) onChanged()
    }

    // ---------- All requests ----------

    private fun showList() {
        openId?.let { api.close(it) }
        openId = null
        lastLoad = ::showList
        val mine = ++turn
        set(s.copy(screen = RequestsPortalState.Screen.LIST, title = Requests.TITLE, loading = true, failed = false, request = null, requestState = null, banner = null, pending = emptyList()))
        api.list { list ->
            if (mine != turn || s.screen != RequestsPortalState.Screen.LIST) return@list
            if (list == null) return@list set(s.copy(loading = false, failed = true))
            val marks = seen()
            set(s.copy(loading = false, failed = false, list = Requests.ordered(list, marks), seen = marks))
        }
    }

    // ---------- One request ----------

    private fun draw(r: RequestDetail, pending: List<PendingReply>) {
        val st = Requests.stateOf(r.summary)
        markSeen(r.id, Requests.seenAt(r.summary.lastAt, now()))
        set(
            s.copy(
                loading = false,
                failed = false,
                request = r,
                requestState = st,
                banner = Requests.STATE_WORDS.getValue(st),
                pending = pending,
                placeholder = if (st == RequestState.SOLVED) Requests.REOPEN_PLACEHOLDER else Requests.REPLY_PLACEHOLDER
            )
        )
    }

    private fun showRequest(id: String) {
        if (isCurrentChat(id)) {
            leave()
            openChat()
            return
        }
        openId = id
        lastLoad = { showRequest(id) }
        val mine = ++turn
        set(s.copy(screen = RequestsPortalState.Screen.REQUEST, title = Requests.ONE, loading = true, failed = false, request = null, requestState = null, banner = null, pending = emptyList()))
        api.open(id) { r ->
            if (mine != turn || openId != id || s.screen != RequestsPortalState.Screen.REQUEST) return@open
            if (r == null) return@open set(s.copy(loading = false, failed = true))
            draw(r, emptyList())
        }
    }

    private fun withPending(entryId: String, status: PendingReply.Status?): List<PendingReply> =
        if (status == null) s.pending.filter { it.entryId != entryId }
        else s.pending.map { if (it.entryId == entryId) it.copy(status = status) else it }

    private fun sendText(id: String, entryId: String, text: String) {
        set(s.copy(pending = withPending(entryId, PendingReply.Status.SENDING)))
        api.reply(id, text, entryId) { out ->
            if (openId != id || s.screen != RequestsPortalState.Screen.REQUEST) return@reply
            val back = out.request
            if (out.ok && back != null) {
                drafts.remove(id)
                draw(back, withPending(entryId, null))
                return@reply
            }
            set(s.copy(pending = withPending(entryId, if (out.reason == "locked") PendingReply.Status.LOCKED else PendingReply.Status.FAILED)))
        }
    }

    // A team reply or "solved" while it is open: read it again, keeping
    // whatever they were typing and a reply that did not go out.
    private val offUpdate = api.onUpdate { id ->
        if (id != openId || s.screen != RequestsPortalState.Screen.REQUEST) return@onUpdate
        api.open(id) { r ->
            if (r == null || openId != id || s.screen != RequestsPortalState.Screen.REQUEST ||
                s.pending.any { it.status == PendingReply.Status.SENDING }
            ) return@open
            draw(r, s.pending)
        }
    }

    /** All requests (web openList). Back leaves the portal. */
    fun openList() {
        fromList = false
        showList()
    }

    /** One request (web openRequest). Back leaves the portal. */
    fun openRequest(id: String) {
        fromList = false
        showRequest(id)
    }

    /** From the list: Back comes back to the list. */
    fun pick(id: String) {
        fromList = true
        showRequest(id)
    }

    /** One step back: a request opened from the list goes to the list, else the portal closes. */
    fun back() {
        if (s.screen == RequestsPortalState.Screen.REQUEST && fromList) {
            fromList = false
            showList()
        } else leave()
    }

    /** Leave the portal. */
    fun close() = leave()

    /** Try again after a failed load. */
    fun reload() {
        if (s.failed) lastLoad?.invoke()
    }

    fun setDraft(text: String) {
        val id = openId ?: return
        if (s.screen != RequestsPortalState.Screen.REQUEST) return
        drafts[id] = text
        set(s)
    }

    /** Send the draft as a reply. */
    fun send() {
        val id = openId ?: return
        if (s.screen != RequestsPortalState.Screen.REQUEST || s.request == null) return
        val text = drafts[id].orEmpty().trim()
        if (text.isEmpty()) return
        drafts[id] = ""
        val entryId = Requests.replyEntryId(now(), rand)
        set(s.copy(pending = s.pending + PendingReply(entryId, text, FlowCalendar.iso(now()), PendingReply.Status.SENDING)))
        sendText(id, entryId, text)
    }

    /** Try again on a reply that did not go. */
    fun retry(entryId: String) {
        val p = s.pending.firstOrNull { it.entryId == entryId } ?: return
        val id = openId ?: return
        if (p.status != PendingReply.Status.FAILED) return
        sendText(id, entryId, p.text)
    }

    /** A new conversation: the drafts go. */
    fun reset() = drafts.clear()

    fun destroy() = offUpdate()
}
