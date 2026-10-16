package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.livechat.SignalTimer

/**
 * Waiting games: the owner's settings, the catalogue, and when the offer is
 * made and when it steps aside. Ported from the React Native SDK's
 * src/core/waitingGames.ts (the web widget's conferbot-widget
 * src/ts/chat/games/settings.ts and wire.ts, with nodeKit.ts pickAccent).
 * Pure: the card itself is [GamesCard], and [WaitingExtras] feeds the
 * signals in.
 *
 *   waitingGames: { enabled: boolean, games: string[], offerText: string }
 *
 * Unset means on with every game: the offer only ever appears while a
 * visitor is waiting for a person (cb:waiting-for-human), never starts a
 * game by itself, and goes away the moment a teammate joins.
 *
 *   cb:waiting-for-human  after OFFER_DELAY_MS, one offer card per wait.
 *   cb:human-joined       a game in progress is saved and folded away
 *                         (wrapUp), an unused offer leaves.
 *   cb:wait-over          no offer any more; an unused card leaves, a game
 *                         being played stays to finish.
 *
 * While waiting, a visitor who types "play" or "games" gets the menu instead
 * of sending it. "Not now" (or the x) means no more offers for this app
 * session (the web: this page view). Nothing is stored or sent.
 */
object WaitingGames {

    /** A game's entry; its name and line are read in the active language (web ui.ts gameName, games.<id>.name / .blurb). */
    data class GameMeta(val id: String, val emoji: String) {
        val name: String get() = t("games.$id.name")

        /** One line for the menu. */
        val blurb: String get() = t("games.$id.blurb")
    }

    val GAMES: List<GameMeta> = listOf(
        GameMeta("rps", "✊"),
        GameMeta("ttt", "⭕"),
        GameMeta("quiz", "🧠"),
        GameMeta("memory", "🃏"),
        GameMeta("reaction", "⚡"),
        GameMeta("emoji", "🤔"),
        GameMeta("scramble", "🔤")
    )

    val GAME_IDS: List<String> = GAMES.map { it.id }

    fun gameMeta(id: String): GameMeta? = GAMES.firstOrNull { it.id == id }

    /** The stock offer as stored (English); drawn as [offerLine] (web ui.ts: the stock line is t("games.offer")). */
    const val DEFAULT_OFFER_TEXT = "While you wait - fancy a quick game? 🎮"

    /** The offer as drawn: the stock line in the active language, the owner's own as written. */
    fun offerLine(offerText: String): String = if (offerText == DEFAULT_OFFER_TEXT) t("games.offer") else offerText
    const val OFFER_TEXT_MAX = 140

    /**
     * The games that work in a language: the emoji riddles are English
     * wordplay ("Bookworm", "Hot dog"), so they are offered in English only.
     */
    fun gamesForLanguage(games: List<GameMeta>, lang: String): List<GameMeta> = games.filter { it.id != "emoji" || lang == "en" }

    data class Settings(val enabled: Boolean, val games: List<String>, val offerText: String)

    /** The settings as the widget uses them, from whatever was stored. */
    fun settingsOf(c: Map<String, Any?>?): Settings {
        val w = c?.get("waitingGames") as? Map<*, *> ?: emptyMap<String, Any?>()
        val raw = w["games"]
        val games = if (raw is List<*>) raw.filterIsInstance<String>().filter { it in GAME_IDS }.distinct() else GAME_IDS
        val text = (w["offerText"] as? String)?.replace(Regex("\\s+"), " ")?.trim()?.take(OFFER_TEXT_MAX).orEmpty()
        return Settings(enabled = w["enabled"] != false && games.isNotEmpty(), games = games, offerText = text.ifEmpty { DEFAULT_OFFER_TEXT })
    }

    /** Typed while waiting, these open the games menu: "play", "games", "let's play", "play a game". */
    val PLAY_RE = Regex("^\\s*(?:let'?s\\s+)?(?:play(?:\\s+a)?(?:\\s+game)?|games?|i\\s+want\\s+to\\s+play)\\s*[!.?]*\\s*$", RegexOption.IGNORE_CASE)

    fun isPlay(text: String?): Boolean = PLAY_RE.containsMatchIn(text.orEmpty())

    // ------------------------------------------------------------ the accent

    private val HEX = Regex("^#?([0-9a-f]{3}|[0-9a-f]{6})$", RegexOption.IGNORE_CASE)

    /** Relative luminance (0 black .. 1 white), or null for non-hex input (web nodeKit.ts). */
    fun luminance(hex: Any?): Double? {
        val m = HEX.find((hex as? String)?.trim() ?: return null) ?: return null
        var h = m.groupValues[1]
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        val n = h.toInt(16)
        val (r, g, b) = listOf((n shr 16) and 255, (n shr 8) and 255, n and 255).map { v ->
            val c = v / 255.0
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * The card's accent (web nodeKit.ts pickAccent): the header colour unless
     * it is too pale to carry white text, then the bot bubble's, the visitor
     * bubble's, the choice text's, then Conferbot blue.
     */
    fun pickAccent(c: Map<String, Any?>?): String {
        for (v in listOf(c?.get("headerBgColor"), c?.get("botMsgColor"), c?.get("userMsgColor"), c?.get("optionBubbleTextColor"))) {
            val l = luminance(v)
            if (l != null && l < 0.5) return v as String
        }
        return "#0808EE"
    }

    // ------------------------------------------------------------ the offer

    /** The offer card comes this long after the waiting signal. */
    const val OFFER_DELAY_MS = 2500L
    /** A card that could not be drawn is tried again this often, OFFER_RETRIES times. */
    const val OFFER_RETRY_MS = 1500L
    const val OFFER_RETRIES = 3
}

/** What the offer needs of the card on screen ([GamesCard]). */
interface OfferCard {
    /** Still on screen. */
    fun up(): Boolean
    /** A game is under way. */
    fun playing(): Boolean
    /** Back to the menu. */
    fun menu()
    fun remove()
    /** A teammate joined: save a game in progress, or step aside. */
    fun wrapUp(name: String)
}

/**
 * The offer's rules (web wire.ts wireWaitingGames). Feed it the signals:
 * [waiting] on cb:waiting-for-human, [joined] on cb:human-joined,
 * [waitOver] on cb:wait-over; [play] before sending the visitor's words;
 * [dismissed] when the card's "Not now" or x closes it.
 *
 * [mount] draws a new card ("offer": the owner's line; "menu": the visitor
 * asked); null when it cannot.
 */
class GamesOffer(
    private val enabled: () -> Boolean,
    private val mount: (mode: String) -> OfferCard?,
    private val timer: SignalTimer
) {
    private var waiting = false
    private var offered = false
    private var dismissed = false
    private var card: OfferCard? = null
    private var handle: Any? = null

    private fun cancel() {
        handle?.let(timer::clear)
        handle = null
    }

    private fun show(mode: String): Boolean {
        if (!enabled()) return false
        val c = card
        if (c != null && c.up() && mode == "menu") {
            // Mid-game, "play" brings the game back into view instead.
            if (!c.playing()) c.menu()
            return true
        }
        card = mount(mode)
        return card != null
    }

    /** cb:waiting-for-human: after OFFER_DELAY_MS, one offer per wait. */
    fun waiting() {
        if (waiting) return
        waiting = true
        offered = false
        cancel()
        attempt(WaitingGames.OFFER_RETRIES)
    }

    // The chat may still be drawing: a few more tries before giving up.
    private fun attempt(left: Int) {
        handle = timer.set(if (left == WaitingGames.OFFER_RETRIES) WaitingGames.OFFER_DELAY_MS else WaitingGames.OFFER_RETRY_MS) {
            handle = null
            if (!waiting || offered || dismissed) return@set
            if (card?.up() == true) return@set
            offered = show("offer")
            if (!offered && left > 0) attempt(left - 1)
        }
    }

    /** cb:wait-over: no more offers; an unused card leaves, a game being played stays. */
    fun waitOver() {
        waiting = false
        cancel()
        val c = card
        if (c != null && c.up() && !c.playing()) {
            c.remove()
            card = null
        }
    }

    /** cb:human-joined: a game in progress is saved and folded away, an unused card leaves. */
    fun joined(name: Any?) {
        waiting = false
        cancel()
        val c = card
        if (c != null && c.up()) c.wrapUp(name as? String ?: "")
    }

    /**
     * The visitor's words while waiting: "play" opens the menu instead of
     * being sent. True when it did (do not send them).
     */
    fun play(text: String?): Boolean {
        if (!waiting || !WaitingGames.isPlay(text)) return false
        if (!show("menu")) return false
        dismissed = false
        return true
    }

    /** The card's "Not now" or x: no more offers for this session. */
    fun dismissed() {
        dismissed = true
        card = null
    }

    val isWaiting: Boolean get() = waiting

    /** A new conversation: the wait is gone ("Not now" still holds, as on the web's page view). */
    fun reset() {
        cancel()
        waiting = false
        offered = false
        card = null
    }
}
