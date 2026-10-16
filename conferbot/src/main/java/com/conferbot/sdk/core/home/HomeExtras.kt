package com.conferbot.sdk.core.home

import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.requests.RequestsSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the device keeps what it remembers (the SDK's SharedPreferences; nothing: this app session only). */
interface HomeStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/** The help centre as fetched-chatbot-data sends it (`knowledgeBaseData`, lists of maps). */
data class HomeKnowledgeBase(val categories: List<Any?> = emptyList(), val recentArticles: List<Any?> = emptyList()) {
    /** Every raw article, for [Home.remoteQueryFor]. */
    val rawArticles: List<Any?> get() = recentArticles + categories.flatMap { ((it as? Map<*, *>)?.get("articles") as? List<*>).orEmpty() }
}

/**
 * The home screen in the SDK (web conferbot-widget
 * src/ts/home/firstImpression.ts enhanceHome / goToChat, index.ts's view
 * switches, ts/home/visitorMemory.ts, ts/knowledgeBase/kbContent.ts
 * remoteSearch; RN context/hooks/useHomeScreen.ts). Holds:
 *
 *   - which view the open chat shows: the home screen, the conversation or
 *     the help centre ([Home.safeView]: the home only while the owner has it
 *     on). It starts on the home and keeps the last view while the app runs,
 *     as the web keeps it for the page;
 *   - what the device remembers for "Welcome back" (per bot in
 *     SharedPreferences, in memory without a store): the first real question
 *     of the latest conversation the visitor typed in, their first name once
 *     a flow asked for it, when they last wrote;
 *   - the help centre's data from fetched-chatbot-data, for the home's
 *     cards, and the server's search inside article text.
 *
 * Owned by [com.conferbot.sdk.core.Conferbot] (`Conferbot.home`); drawn by
 * the Compose chat (ui/compose/home/HomeScreenView.kt). Call it on the main
 * thread; socket answers are [post]ed there.
 */
class HomeExtras(
    private val botId: () -> String?,
    /** The bot's settings (themed, personalised). */
    private val customizations: () -> Map<String, Any?>,
    private val chatSessionId: () -> String?,
    private val store: () -> HomeStore?,
    private val socket: () -> RequestsSocket?,
    private val connected: () -> Boolean,
    /** Send the visitor's words (Conferbot.sendMessage). */
    private val send: (String) -> Unit,
    /** The home screen came into view: read the requests again (at most every 15s). */
    private val onHomeShown: () -> Unit = {},
    private val timer: SignalTimer,
    private val post: (() -> Unit) -> Unit = { it() },
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val _chosen = MutableStateFlow<HomeView?>(null)
    /** The view chosen (null: none yet, the home when there is one). Read with [viewFor]. */
    val chosen: StateFlow<HomeView?> = _chosen.asStateFlow()

    private val _helpCategoryId = MutableStateFlow<String?>(null)
    /** The topic the help centre opened on, if any. */
    val helpCategoryId: StateFlow<String?> = _helpCategoryId.asStateFlow()

    /** Where the help centre's Back goes: the view it was opened from. */
    private var helpFrom: HomeView = HomeView.HOME

    private val _memory = MutableStateFlow<VisitorMemory?>(null)
    val memory: StateFlow<VisitorMemory?> = _memory.asStateFlow()

    private val _knowledgeBase = MutableStateFlow(HomeKnowledgeBase())
    val knowledgeBase: StateFlow<HomeKnowledgeBase> = _knowledgeBase.asStateFlow()

    private var loadedFor: String? = null
    private var lastName = ""
    private val searches = mutableMapOf<String, List<Pair<String, String>>>()
    private val pending = mutableListOf<Any>()

    /** The view the open chat shows, for these settings. */
    fun viewFor(c: Map<String, Any?>?): HomeView = Home.safeView(c, _chosen.value)

    /** The view on screen now. */
    fun view(): HomeView = viewFor(customizations())

    /** Is there a home screen? */
    fun hasHome(): Boolean = Home.on(customizations())

    private fun save(next: VisitorMemory?) {
        next ?: return
        _memory.value = next
        try {
            store()?.write(Home.memoryKey(botId()), Home.serializeMemory(next))
        } catch (e: Exception) {
            // storage full or blocked: this app session still remembers
        }
    }

    /** What the device remembers, once per bot. */
    fun load() {
        val bot = botId().orEmpty()
        if (loadedFor == bot) return
        loadedFor = bot
        searches.clear()
        lastName = ""
        val raw = try { store()?.read(Home.memoryKey(bot)) } catch (e: Exception) { null }
        _memory.value = Home.parseMemory(raw, now())
    }

    /** The visitor typed this in a live (or agent) chat (web special.ts _handleHumanHandoverInputSubmit). */
    fun rememberText(text: String?) {
        if (text.isNullOrBlank()) return
        load()
        save(Home.withMessage(_memory.value, chatSessionId(), text, now()))
    }

    /** A flow asked for their name (web index.ts _setUserMetaData "name"). */
    fun rememberName(name: String?) {
        val n = name.orEmpty().trim()
        if (n.isEmpty() || n == lastName) return
        load()
        lastName = n
        save(Home.withName(_memory.value, n, now()))
    }

    /** The help centre as fetched-chatbot-data sent it. */
    @Suppress("UNCHECKED_CAST")
    fun setKnowledgeBase(kb: Map<*, *>?) {
        _knowledgeBase.value = HomeKnowledgeBase(
            categories = kb?.get("categories") as? List<Any?> ?: emptyList(),
            recentArticles = kb?.get("recentArticles") as? List<Any?> ?: emptyList()
        )
    }

    /** Back to the home screen (nothing when the owner has none). */
    fun showHome() {
        if (Home.on(customizations())) _chosen.value = HomeView.HOME
    }

    /** To the conversation; with [text], ask it there as the visitor, once the chat is there. */
    fun showChat(text: String? = null) {
        _chosen.value = HomeView.CHAT
        val words = text?.trim().orEmpty()
        if (words.isEmpty()) return
        lateinit var handle: Any
        handle = timer.set(ASK_AFTER_MS) {
            pending.remove(handle)
            send(words)
        }
        pending += handle
    }

    /** The help centre, on one topic or its list; Back returns to [from]. */
    fun showHelp(categoryId: String? = null, from: HomeView = view()) {
        _helpCategoryId.value = categoryId
        helpFrom = if (from == HomeView.HELP) helpFrom else from
        _chosen.value = HomeView.HELP
    }

    /** Leave the help centre for the view it came from. */
    fun leaveHelp() {
        _helpCategoryId.value = null
        if (helpFrom == HomeView.HOME) showHome() else _chosen.value = helpFrom
        if (view() == HomeView.HELP) _chosen.value = HomeView.CHAT
    }

    /**
     * The system back (or the header's back): the help centre goes back where
     * it came from, the conversation to the home. False when there is nothing
     * to go back to (the chat closes).
     */
    fun back(): Boolean = when (view()) {
        HomeView.HELP -> { leaveHelp(); true }
        HomeView.CHAT -> if (hasHome()) { showHome(); true } else false
        HomeView.HOME -> false
    }

    /** The home screen is on screen: its requests card reads again. */
    fun homeShown() = onHomeShown()

    /**
     * Matches inside article text from the server (web kbContent.ts
     * remoteSearch: `search-kb-articles`, cached per query when it
     * answered). [onDone] gets (article id, snippet) pairs, empty on failure.
     */
    fun searchArticleText(query: String?, onDone: (List<Pair<String, String>>) -> Unit) {
        val key = query.orEmpty().trim().lowercase()
        if (key.length < 2) { onDone(emptyList()); return }
        searches[key]?.let { onDone(it); return }
        val s = socket()
        if (s == null || !connected()) { onDone(emptyList()); return }
        val payload = mapOf("chatbotId" to botId().orEmpty(), "query" to key, "limit" to 10)
        try {
            s.ask(EVENT_SEARCH, payload, SEARCH_TIMEOUT_MS) { reply ->
                post {
                    val ok = reply?.get("success") == true
                    val list = if (ok) (reply?.get("articles") as? List<*>).orEmpty().mapNotNull { a ->
                        val m = a as? Map<*, *> ?: return@mapNotNull null
                        val id = m["_id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        id to (m["snippet"]?.toString() ?: "")
                    } else emptyList()
                    // Failures are not kept, so the next keystroke can try again.
                    if (ok) searches[key] = list
                    onDone(list)
                }
            }
        } catch (e: Exception) {
            onDone(emptyList())
        }
    }

    /** A new SDK session: the questions waiting to be asked go. */
    fun reset() {
        pending.toList().forEach { timer.clear(it) }
        pending.clear()
    }

    companion object {
        /** The web's wait for the server's article search (kbContent.ts SEARCH_TIMEOUT_MS). */
        const val SEARCH_TIMEOUT_MS = 5000L
        /** Into the chat, then the question: the same pause the greeting card's "send" has. */
        const val ASK_AFTER_MS = 400L
        const val EVENT_SEARCH = "search-kb-articles"
    }
}
