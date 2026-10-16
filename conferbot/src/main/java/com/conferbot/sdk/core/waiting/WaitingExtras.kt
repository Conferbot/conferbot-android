package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.livechat.HandoverSocket
import com.conferbot.sdk.core.livechat.HandoverState
import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.livechat.WidgetSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A card and where it sits: after the record item [after] (null: at the end). */
data class PlacedCard<T>(val card: T, val after: String?)

/**
 * While the visitor waits for a person, as the web widget has it: the help
 * card (the articles for their question 6s in, or the server's queue-help
 * pick with "That solved it"), the games offer 2.5s after the waiting
 * signal, and a help article opened in the app. The rules are in
 * [WaitHelp], [GamesOffer] and [GamesCard]; this feeds them the handover's
 * signals and the socket. Ported from the React Native SDK's
 * src/context/hooks/useWaitingExtras.ts (web waitArticles.ts, games/wire.ts,
 * games/ui.ts, handlers/special.ts _wireQueue).
 *
 * The cards are screen-only: each is placed after the item that was last on
 * screen when it was drawn ([PlacedCard.after]) and is never saved on the
 * device or put in the record the server keeps. Owned by
 * [com.conferbot.sdk.core.Conferbot] (`Conferbot.waiting`); drawn by the
 * Compose chat (ui/compose/chat/WaitingCards.kt).
 */
class WaitingExtras(
    private val socket: () -> HandoverSocket?,
    private val sessionId: () -> String?,
    /** The bot's customizations (the help centre switch, waitingGames, the colours). */
    private val customizations: () -> Map<String, Any?>?,
    /** The record as the server gets it, for the visitor's question. */
    private val record: () -> List<Map<String, Any?>>,
    /** A person is in the chat already (HandoverLifecycle.phase). */
    private val joined: () -> Boolean,
    /** The id of the last item on screen: a new card goes after it. */
    private val lastItemId: () -> String?,
    /** Out of the queue because an article helped: the line, the record, the flow on. */
    private val leftQueueHelped: () -> Unit,
    /** The phone asks for less motion (animator duration scale 0). */
    private val reducedMotion: () -> Boolean = { false },
    /** A chat that can draw the cards is on screen (the Compose chat). */
    private val canDraw: () -> Boolean = { true },
    private val timer: SignalTimer,
    /** Socket callbacks come on the socket's thread: run them where the cards live. */
    private val post: (() -> Unit) -> Unit = { it() },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    // The bot's help centre from fetched-chatbot-data (knowledgeBaseData).
    @Volatile private var categories: List<Any?> = emptyList()
    @Volatile private var recentArticles: List<Any?> = emptyList()

    private val _help = MutableStateFlow<PlacedCard<HelpCardState>?>(null)
    /** The help card on screen, or null. */
    val help: StateFlow<PlacedCard<HelpCardState>?> = _help.asStateFlow()

    private val _games = MutableStateFlow<PlacedCard<GamesCard>?>(null)
    /** The games card on screen, or null. */
    val games: StateFlow<PlacedCard<GamesCard>?> = _games.asStateFlow()

    private val waitHelp = WaitHelp(
        customizations = customizations,
        question = { WaitArticles.waitQuestion(record()) },
        articles = { categories to recentArticles },
        joined = joined,
        onChange = { card ->
            val before = _help.value
            // A new card (its key) is drawn after the conversation so far; a change keeps its place.
            _help.value = card?.let { PlacedCard(it, if (before?.card?.key == it.key) before.after else lastItemId()) }
        },
        timer = timer
    )

    private var gamesSeq = 0

    private val offer: GamesOffer = GamesOffer(
        enabled = { WaitingGames.settingsOf(customizations()).enabled && canDraw() },
        mount = { mode -> mountGames(mode) },
        timer = timer
    )

    private fun mountGames(mode: String): OfferCard? {
        val card = GamesCard.create(
            customizations = customizations(),
            timer = timer,
            categories = categories,
            recentArticles = recentArticles,
            mode = mode,
            reducedMotion = reducedMotion(),
            onDismiss = { offer.dismissed() },
            now = now
        ) ?: return null
        val n = ++gamesSeq
        // Gone (Not now, the x, the wait over, a person joined before it was played): off the screen.
        card.subscribe { s ->
            if (s.view == "gone" && gamesSeq == n) _games.value = null
        }
        _games.value = PlacedCard(card, lastItemId())
        return card
    }

    private fun own(data: Map<String, Any?>?): Boolean {
        val id = data?.get("chatSessionId")?.toString().orEmpty()
        val mine = sessionId().orEmpty()
        return id.isEmpty() || mine.isEmpty() || id == mine
    }

    /** The handover's signals (HandoverLifecycle.onWidgetSignal). */
    fun signal(s: WidgetSignal) = post {
        when (s) {
            is WidgetSignal.WaitingForHuman -> offer.waiting()
            is WidgetSignal.HumanJoined -> {
                waitHelp.endWait()
                offer.joined(s.agentName ?: "")
            }
            is WidgetSignal.WaitOver -> {
                waitHelp.endWait()
                offer.waitOver()
            }
        }
    }

    /** The socket: the server's pick of help, and the queue it keeps. */
    fun setupListeners() {
        val s = socket() ?: return
        s.on("queue-help") { data -> if (own(data)) post { waitHelp.queueHelp(data) } }
        s.on("queue-left") { data -> if (own(data) && data?.get("reason") == "self_service") post { waitHelp.left() } }
    }

    /** queue-status, from the event or the poll's answer (LiveChatExtras). */
    fun onQueueStatus(data: Map<String, Any?>?) {
        if (data != null && own(data)) post { waitHelp.queueStatus(data) }
    }

    /** "That solved it": queue-self-resolve with the article; ok leaves the queue. [onDone] gets ok. */
    fun solveWait(onDone: (Boolean) -> Unit = {}) {
        val item = waitHelp.solving() ?: return onDone(false)
        val s = socket()
        val sid = sessionId()
        if (s == null || sid == null) {
            waitHelp.solved(false)
            return onDone(false)
        }
        s.ask("queue-self-resolve", HandoverState.queueSelfResolvePayload(sid, item.id), WaitArticles.SOLVE_TIMEOUT_MS) { res ->
            post {
                val ok = res?.get("ok") == true
                waitHelp.solved(ok)
                if (ok) leftQueueHelped()
                onDone(ok)
            }
        }
    }

    /** A row of the help card was opened: the article "That solved it" names. */
    fun opened(item: WaitArticles.HelpItem) = waitHelp.open(item.id)

    /** An article of the bot's help centre data by id (its category's copy first), or null. */
    fun findArticle(id: String): Map<*, *>? = WaitArticles.findArticle(id, categories, recentArticles)

    /** The visitor's words while waiting: "play" opens the games instead (true: do not send). */
    fun interceptPlay(text: String): Boolean = offer.play(text)

    /** fetched-chatbot-data's knowledgeBaseData. */
    fun setKnowledgeBase(kb: Map<String, Any?>?) {
        categories = kb?.get("categories") as? List<Any?> ?: emptyList()
        recentArticles = kb?.get("recentArticles") as? List<Any?> ?: emptyList()
    }

    /** Where the handover begins a wait for a person (HandoverLifecycle). */
    fun startWait() = post { waitHelp.startWait() }

    /** Where the handover ends a wait for a person, however it ends. */
    fun endWait() = post { waitHelp.endWait() }

    /** A new conversation: the cards go. */
    fun reset() = post {
        waitHelp.reset()
        offer.reset()
        val g = _games.value?.card
        gamesSeq += 1
        g?.remove()
        _games.value = null
        _help.value = null
    }
}
