package com.conferbot.sdk.core.requests

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the device keeps what it remembers (the SDK's SharedPreferences; nothing: this app session only). */
interface RequestsStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/**
 * "Your requests" in the SDK (web conferbot-widget src/ts/home/requests/
 * card.ts mountRequestsCard, store.ts, and index.ts `_sendResponse`
 * rememberSession; RN context/hooks/useRequestsPortal.ts). Holds:
 *
 *   - what the device remembers: the chats it started that the visitor
 *     wrote in, and when each request was last read here, per bot
 *     ([Requests.storageKey]; in memory without a store);
 *   - the list as the home card has it, for the chat menu's item and the
 *     app's badge ([unread]): read when the socket connects, when the chat
 *     opens or closes (at most every 15s), when the portal closes, and when
 *     a request changes while the portal is closed;
 *   - the portal's screens ([portal], [RequestsPortal]).
 *
 * Nothing is asked for a visitor with no chat on this device and no
 * signed-in user (`user.hash`): a first-time visitor has no requests.
 * Owned by [com.conferbot.sdk.core.Conferbot] (`Conferbot.requests`); drawn
 * by the Compose chat (ui/compose/requests/RequestsPortalView.kt). Call it
 * on the main thread; socket answers are [post]ed there.
 */
class RequestsExtras(
    private val botId: () -> String?,
    private val socket: () -> RequestsSocket?,
    /** The socket is connected. */
    private val connected: () -> Boolean,
    /** The visitor id the records carry (ChatState.visitorId), else the handshake's. */
    private val visitorId: () -> String?,
    private val user: () -> RequestsUser?,
    private val store: () -> RequestsStore?,
    /** The chat on screen and whether it ended (nothing live since its chat-ended). */
    private val chatSessionId: () -> String?,
    private val chatEnded: () -> Boolean,
    /** Show the chat (a request that is the chat on screen). */
    private val openChat: () -> Unit = {},
    /** Socket callbacks come on the socket's thread: run them where the portal lives. */
    private val post: (() -> Unit) -> Unit = { it() },
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private var memory = RequestMemory()
    private var loadedFor: String? = null
    private var lastLoad = 0L
    private var loading = false

    private val _requests = MutableStateFlow<List<RequestSummary>?>(null)
    /** The visitor's requests (newest replies first), null until read. */
    val requests: StateFlow<List<RequestSummary>?> = _requests.asStateFlow()

    private val _unread = MutableStateFlow(0)
    /** Requests with a team reply not read on this device: the app's badge. */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _seen = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** When each request was last read on this device, for the home card ("New reply"). */
    val seen: StateFlow<Map<String, Long>> = _seen.asStateFlow()

    private val client = Requests.api(socket, { Requests.who(visitorId(), memory, user()) }, post)

    // The portal and the list both hear the server's updates; the socket
    // changes when the SDK is set up again, so they subscribe here and
    // [attach] moves the one real subscription to the socket of the day.
    private val updateListeners = mutableListOf<(String) -> Unit>()
    private var offSocket: (() -> Unit)? = null

    private val api = object : RequestsApi by client {
        override fun onUpdate(fn: (String) -> Unit): () -> Unit {
            updateListeners += fn
            return { updateListeners -= fn }
        }
    }

    val portal = RequestsPortal(
        api = api,
        seen = { memory.seen },
        markSeen = { id, at -> save(Requests.withSeen(memory, id, at)) },
        // The chat on screen, not yet over: that chat instead (web index.ts isCurrentChat).
        isCurrentChat = { id -> id == chatSessionId() && !chatEnded() },
        openChat = { openChat() },
        onChanged = { refresh(force = true) },
        now = now
    )

    /** The portal's screens. */
    val state: StateFlow<RequestsPortalState> get() = portal.state

    init {
        // A reply arrived while the portal is closed: the list says so.
        updateListeners += { if (!portal.isOpen) refresh(force = true) }
    }

    /** Listen to the server's updates on the SDK's socket (set up, or set up again). */
    fun attach() {
        offSocket?.invoke()
        offSocket = client.onUpdate { id -> updateListeners.toList().forEach { it(id) } }
    }

    private fun save(next: RequestMemory?) {
        next ?: return
        memory = next
        _seen.value = next.seen
        _unread.value = Requests.unreadCount(_requests.value, memory.seen)
        try {
            store()?.write(Requests.storageKey(botId()), Requests.serializeMemory(next))
        } catch (e: Exception) {
            // storage full or blocked: this app session still remembers
        }
    }

    /** What the device remembers, once per bot. */
    fun load() {
        val bot = botId().orEmpty()
        if (loadedFor == bot) return
        loadedFor = bot
        val raw = try { store()?.read(Requests.storageKey(bot)) } catch (e: Exception) { null }
        memory = Requests.parseMemory(raw)
        _seen.value = memory.seen
        _requests.value = null
        _unread.value = 0
    }

    /** Read the list again (at most every 15s unless [force]). */
    fun refresh(force: Boolean = false) {
        load()
        if (loading || (!force && now() - lastLoad < Requests.REFRESH_GAP_MS)) return
        if (!Requests.canAsk(memory, user())) return
        if (!connected()) return
        loading = true
        lastLoad = now()
        client.list { list ->
            loading = false
            if (list != null) {
                _requests.value = Requests.ordered(list, memory.seen)
                _unread.value = Requests.unreadCount(list, memory.seen)
            }
        }
    }

    /**
     * The record changed: a chat the visitor wrote in may become a request
     * (web _sendResponse). [entries]: (type, shape) of each record entry.
     */
    fun onRecord(sessionId: String?, entries: List<Pair<String?, String?>>) {
        load()
        if (sessionId.isNullOrEmpty() || !Requests.hasVisitorEntry(entries)) return
        save(Requests.withSession(memory, sessionId))
    }

    /** Connected with something to ask about (and after a sign-in). */
    fun onConnected() = refresh(force = true)

    /** The chat opening or closing is the app's "home comes back into view" (web card.ts): read again, at most every 15s. */
    fun onChatShown() = refresh()

    /** The home screen came into view (web card.ts): read again, at most every 15s. */
    fun onHomeShown() = refresh()

    /** Open the list, or one request by its id. */
    fun open(requestId: String? = null) {
        load()
        if (requestId != null) portal.openRequest(requestId) else portal.openList()
    }

    fun close() = portal.close()
    fun back() = portal.back()

    /** A new conversation: the drafts go. */
    fun reset() = portal.reset()
}
