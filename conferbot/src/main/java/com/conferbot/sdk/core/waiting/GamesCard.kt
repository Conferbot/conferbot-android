package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.waiting.GamesLogic.Hand
import com.conferbot.sdk.core.waiting.WaitingGames.GameMeta

/** The card's words (web ui.ts, "games.*"), read in the active language. */
object GamesText {
    val CARD: String get() = t("games.card")
    val CLOSE: String get() = t("games.close")
    val PICK: String get() = t("games.pick")
    val AGAIN: String get() = t("games.again")
    val ANOTHER: String get() = t("games.another")
    val NOT_NOW: String get() = t("common.notNow")
    fun pausedNamed(name: String) = t("games.paused.named", "name" to name)
    val PAUSED_SOMEONE: String get() = t("games.paused.someone")
    val PAUSED_SAVED: String get() = t("games.paused.saved")
    val RESUME: String get() = t("games.paused.resume")
}

/** A footer button: [key] is notNow, again, another, action or resume. */
data class FootButton(val key: String, val label: String, val primary: Boolean)

/** One shape per game, for the view to draw. */
sealed class Board {
    data class HandPick(val hand: Hand, val emoji: String, val name: String)
    data class Rps(val you: String, val bot: String, val outcome: String?, val shaking: Boolean, val busy: Boolean, val hands: List<HandPick>) : Board()
    data class Ttt(val cells: List<String?>, val line: List<Int>, val over: Boolean, val thinking: Boolean) : Board()
    data class MemoryFace(val face: String, val up: Boolean, val matched: Boolean)
    data class Memory(val cards: List<MemoryFace>, val pop: List<Int>) : Board()
    /** [game] is "quiz" or "emoji". */
    data class Choice(val game: String, val tag: String?, val big: String?, val q: String, val options: List<String>, val answer: Int, val picked: Int?) : Board()
    /** [state] is idle, wait, go, between or done. */
    data class Reaction(val state: String, val big: String, val line: String) : Board()
    data class Scramble(
        val hint: String,
        val letters: List<String>,
        /** Slot i holds letters[placed[i]]. */
        val placed: List<Int>,
        val solved: Boolean,
        val revealed: Boolean,
        /** Bumped on a wrong word (the slots shake). */
        val nope: Int,
        val skipLabel: String
    ) : Board()
}

/** A tap inside a game. */
sealed class GameInput {
    data class Rps(val hand: Hand) : GameInput()
    data class Ttt(val i: Int) : GameInput()
    data class Memory(val i: Int) : GameInput()
    data class Choice(val i: Int) : GameInput()
    object Reaction : GameInput()
    data class Tile(val i: Int) : GameInput()
    data class Slot(val i: Int) : GameInput()
    object Skip : GameInput()
    /** A hardware key in the word scramble: a letter, or Backspace. */
    data class Key(val key: String) : GameInput()
}

data class GamesCardState(
    /** menu, game, paused or gone. */
    val view: String,
    val title: String,
    val titleEmoji: String,
    /** The score or progress at the card's top right. */
    val meta: String,
    /** The line under the game (announced politely). */
    val status: String,
    /** "win", "done" or null. */
    val result: String?,
    /** A finished game left as a result when a teammate joined: no buttons, no close. */
    val settled: Boolean,
    val closable: Boolean,
    /** The menu's games (the owner's, for the language). */
    val chips: List<GameMeta>,
    val game: String?,
    val board: Board?,
    val foot: List<FootButton>,
    /** Bumped for a small "nice!" and for a win's confetti. */
    val cheer: Int,
    val confetti: Int
)

/**
 * Waiting games: the in-chat card as a state machine, with no UI. Ported
 * from the React Native SDK's src/core/gamesCard.ts (the web widget's
 * conferbot-widget src/ts/chat/games/ui.ts, mountGamesCard and the seven
 * game factories): the offer (the owner's line and a chip per game), then
 * the game itself, then "Play again" / "Another game"; folded away when a
 * teammate joins ("Finish my game"). Every change is a new [state] snapshot
 * that a view draws (ui/compose/chat/WaitingCards.kt), and every tap is a call.
 *
 * Timers go through the injected [timer]. A game's pending steps (the bot's
 * move, cards turning back, the next round) are held while the card is
 * paused and run on resume, as the web's ctx.queued. With [reducedMotion]
 * the rock-paper-scissors shake is skipped and the bot moves sooner.
 *
 * Nothing here is sent anywhere or stored: the games are the visitor's own
 * and leave no trace in the transcript. Build it with [GamesCard.create]
 * (null when the owner left no game for the language).
 */
class GamesCard private constructor(
    private val customizations: Map<String, Any?>?,
    private val categories: List<Any?>,
    private val recentArticles: List<Any?>,
    private val mode: String,
    private val rng: Rng,
    private val reducedMotion: Boolean,
    private val onDismiss: () -> Unit,
    private val timer: SignalTimer,
    private val now: () -> Long,
    private val settings: WaitingGames.Settings,
    available: List<GameMeta>,
    /** The language the quiz and the scramble are in. */
    private val lang: String
) : OfferCard {

    companion object {
        const val REACTION_ROUNDS = 3
        const val SCRAMBLE_ROUNDS = 3

        /**
         * One card (web mountGamesCard). Null when the owner left no game for
         * this language. Starts on the menu: with the owner's offer line for an
         * offer ([mode] "offer"), "Pick a game" when the visitor asked ("menu").
         */
        fun create(
            customizations: Map<String, Any?>?,
            timer: SignalTimer,
            categories: List<Any?> = emptyList(),
            recentArticles: List<Any?> = emptyList(),
            mode: String = "offer",
            rng: Rng = GamesLogic.random,
            reducedMotion: Boolean = false,
            lang: String = I18n.language,
            onDismiss: () -> Unit = {},
            now: () -> Long = { System.nanoTime() / 1_000_000 }
        ): GamesCard? {
            val settings = WaitingGames.settingsOf(customizations)
            val available = WaitingGames.gamesForLanguage(WaitingGames.GAMES.filter { it.id in settings.games }, lang)
            if (available.isEmpty()) return null
            return GamesCard(customizations, categories, recentArticles, mode, rng, reducedMotion, onDismiss, timer, now, settings, available, lang)
        }

        private val RIGHT = listOf("games.right.1", "games.right.2", "games.right.3", "games.right.4")

        // German nouns keep their capital (web ui.ts handLower).
        private fun handLower(h: Hand) = if (I18n.language == "de") h.label else h.label.lowercase(I18n.locale)
    }

    private interface Game {
        fun input(a: GameInput)
        fun board(): Board
        fun pause() {}
    }

    private inner class Ctx(
        val rng: Rng,
        val reduced: Boolean,
        val plays: Int,
        val entry: Entry
    ) {
        fun meta(text: String) = patch { copy(meta = text) }
        fun status(text: String) = patch { copy(status = text) }

        /** The main button in the footer ("Next"), gone once pressed. */
        fun action(label: String, fn: () -> Unit) {
            action = fn
            patch { copy(foot = foot.filter { it.key != "action" } + FootButton("action", label, true)) }
        }

        fun cheer() {
            if (!reducedMotion) patch { copy(cheer = cheer + 1) }
        }

        fun finish(text: String, win: Boolean) {
            entry.over = true
            action = null
            patch {
                copy(
                    result = if (win) "win" else "done",
                    status = text,
                    confetti = if (win && !reducedMotion) confetti + 1 else confetti,
                    foot = listOf(FootButton("again", GamesText.AGAIN, true), FootButton("another", GamesText.ANOTHER, false))
                )
            }
        }

        /** A pending step, held while paused. */
        fun later(ms: Long, fn: () -> Unit) {
            lateinit var t: Any
            t = timer.set(ms) {
                entry.timers.remove(t)
                entry.queued.remove(t)
                if (current !== entry) return@set
                fn()
                emit()
            }
            entry.timers.add(t)
            entry.queued[t] = fn
        }

        /** A timer that a pause drops (the reflex test's wait for green). */
        fun timer(ms: Long, fn: () -> Unit): Any {
            lateinit var t: Any
            t = timer.set(ms) {
                entry.timers.remove(t)
                if (current !== entry) return@set
                fn()
                emit()
            }
            entry.timers.add(t)
            return t
        }

        fun clear(t: Any) {
            timer.clear(t)
            entry.timers.remove(t)
        }

        fun now(): Long = this@GamesCard.now()
        val c get() = customizations
        val categories get() = this@GamesCard.categories
        val recent get() = recentArticles
    }

    private class Entry(val id: String) {
        lateinit var game: Game
        val timers = linkedSetOf<Any>()
        val queued = linkedMapOf<Any, () -> Unit>()
        var over = false
    }

    // ------------------------------------------------------------------ games

    private fun rps(ctx: Ctx): Game = object : Game {
        var youScore = 0
        var botScore = 0
        var you = Hand.ROCK.emoji
        var bot = Hand.ROCK.emoji
        var outcome: String? = null
        var shaking = false
        var busy = false
        fun show() = ctx.meta(t("games.rps.score", "you" to youScore, "bot" to botScore))

        init {
            show()
            ctx.status(t("games.rps.intro"))
        }

        override fun board() = Board.Rps(you, bot, outcome, shaking, busy, GamesLogic.HANDS.map { Board.HandPick(it, it.emoji, it.label) })

        override fun input(a: GameInput) {
            if (a !is GameInput.Rps || busy) return
            val mine = a.hand
            val theirs = GamesLogic.HANDS[Math.floor(ctx.rng() * 3).toInt()]
            busy = true
            you = Hand.ROCK.emoji
            bot = Hand.ROCK.emoji
            outcome = null
            val reveal = {
                shaking = false
                you = mine.emoji
                bot = theirs.emoji
                val r = GamesLogic.rpsOutcome(mine, theirs)
                outcome = r
                if (r == "win") youScore += 1
                if (r == "lose") botScore += 1
                show()
                val line = when (r) {
                    "draw" -> t("games.rps.draw", "a" to mine.label, "b" to handLower(theirs))
                    "win" -> t("games.rps.youPoint", "a" to mine.label, "b" to handLower(theirs))
                    else -> t("games.rps.botPoint", "a" to theirs.label, "b" to handLower(mine))
                }
                val w = GamesLogic.rpsWinner(youScore, botScore)
                if (w != null) {
                    ctx.finish(
                        if (w == "you") "$line ${t("games.rps.youWin", "you" to youScore, "bot" to botScore)}" else "$line ${t("games.rps.botWins", "you" to youScore, "bot" to botScore)}",
                        w == "you"
                    )
                } else {
                    ctx.status(line)
                    busy = false
                }
            }
            if (ctx.reduced) reveal()
            else {
                shaking = true
                ctx.status(t("games.rps.shake"))
                ctx.later(900, reveal)
            }
        }
    }

    private fun ttt(ctx: Ctx): Game = object : Game {
        val board = MutableList<String?>(9) { null }
        var over = false
        var busy = false
        var line: List<Int> = emptyList()

        fun check(): Boolean {
            val w = GamesLogic.tttWinner(board) ?: return false
            over = true
            line = w.line
            when (w.winner) {
                "X" -> ctx.finish(t("games.ttt.win"), true)
                "O" -> ctx.finish(t("games.ttt.lose"), false)
                else -> ctx.finish(t("games.ttt.draw"), false)
            }
            return true
        }

        fun botTurn() {
            busy = true
            ctx.status(t("games.ttt.thinking"))
            ctx.later(if (ctx.reduced) 250 else 550) {
                val m = GamesLogic.tttBotMove(board, ctx.rng)
                if (m >= 0) board[m] = "O"
                busy = false
                if (!check()) ctx.status(t("games.ttt.yourMove"))
            }
        }

        init {
            ctx.meta(t("games.ttt.meta"))
            // Turns alternate between games: the bot opens every second one.
            if (ctx.plays % 2 == 1) botTurn() else ctx.status(t("games.ttt.youFirst"))
        }

        override fun board() = Board.Ttt(board.toList(), line.toList(), over, busy)

        override fun input(a: GameInput) {
            if (a !is GameInput.Ttt || over || busy) return
            if (a.i < 0 || a.i > 8 || board[a.i] != null) return
            board[a.i] = "X"
            if (!check()) botTurn()
        }
    }

    private fun memory(ctx: Ctx): Game = object : Game {
        var s = GamesLogic.MemoryState(GamesLogic.memoryDeck(GamesLogic.MEMORY_FACES, 6, ctx.rng), emptyList(), 0)
        var waiting = false
        var pop: List<Int> = emptyList()

        fun paint() {
            val pairs = s.cards.count { it.matched } / 2
            ctx.meta(t("games.memory.meta", "moves" to s.moves, "pairs" to pairs))
        }

        init {
            paint()
            ctx.status(t("games.memory.intro"))
        }

        override fun board() = Board.Memory(
            s.cards.mapIndexed { i, c -> Board.MemoryFace(c.face, c.matched || i in s.open, c.matched) },
            pop.toList()
        )

        override fun input(a: GameInput) {
            if (a !is GameInput.Memory || waiting) return
            val r = GamesLogic.memoryFlip(s, a.i)
            if (r.result == "ignored") return
            s = r.state
            pop = emptyList()
            paint()
            when (r.result) {
                "match" -> {
                    ctx.status(t("games.memory.pair"))
                    // The pair just made (the card tapped and its twin).
                    val face = s.cards.getOrNull(a.i)?.face
                    pop = s.cards.indices.filter { s.cards[it].face == face }
                }
                "miss" -> {
                    ctx.status(t("games.memory.noPair"))
                    waiting = true
                    ctx.later(850) {
                        s = GamesLogic.memoryHide(s)
                        waiting = false
                        paint()
                    }
                }
                "done" -> ctx.finish("${t("games.memory.done", "moves" to s.moves)} ${"⭐".repeat(GamesLogic.memoryStars(s.moves))}", true)
            }
        }
    }

    private class Round(val q: String, val big: String?, val tag: String?, val options: List<String>, val answer: Int)

    /** Shared by the quiz and the emoji riddles: a round at a time, three in all. */
    private fun choiceRounds(ctx: Ctx, game: String, rounds: List<Round>, summary: (Int, Int) -> Pair<String, Boolean>): Game = object : Game {
        var at = 0
        var score = 0
        var picked: Int? = null
        var shown = 0
        fun meta() = ctx.meta(t("games.choice.meta", "n" to shown + 1, "total" to rounds.size, "score" to score))
        fun showRound() {
            shown = at
            picked = null
            meta()
        }

        init {
            showRound()
        }

        override fun board(): Board {
            val r = rounds[shown]
            return Board.Choice(game, r.tag, r.big, r.q, r.options.toList(), r.answer, picked)
        }

        override fun input(a: GameInput) {
            if (a !is GameInput.Choice || picked != null) return
            val r = rounds[shown]
            if (a.i < 0 || a.i >= r.options.size) return
            picked = a.i
            val right = a.i == r.answer
            if (right) score += 1
            meta()
            if (right) ctx.cheer()
            val fb = if (right) t(RIGHT[Math.floor(ctx.rng() * RIGHT.size).toInt() % RIGHT.size]) else t("games.choice.wrong", "answer" to r.options[r.answer])
            at += 1
            if (at >= rounds.size) {
                val (text, win) = summary(score, rounds.size)
                ctx.finish("$fb. $text", win)
                return
            }
            ctx.status(fb)
            ctx.action(if (at == rounds.size - 1) t("games.choice.last") else t("games.choice.next")) { showRound() }
        }
    }

    private fun quiz(ctx: Ctx): Game {
        val kb = if (ctx.c?.get("enableKnowledgeBase") == true) GamesLogic.quizFromArticles(ctx.categories, ctx.recent, ctx.rng) else emptyList()
        val qs = GamesLogic.buildQuiz(GamesLogic.triviaFor(lang), kb, ctx.rng)
        ctx.status(if (kb.isNotEmpty()) t("games.quiz.introKb") else t("games.quiz.intro"))
        return choiceRounds(
            ctx, "quiz",
            qs.map { Round(it.q, null, if (it.source == "kb") t("games.quiz.fromKb") else null, it.options, it.answer) }
        ) { score, total -> "${t("games.quiz.score", "score" to score, "total" to total)} ${GamesLogic.quizVerdict(score, total)}" to (score == total) }
    }

    private fun emoji(ctx: Ctx): Game {
        val picked = GamesLogic.shuffle(GamesLogic.RIDDLES, ctx.rng).take(3)
        ctx.status(t("games.emoji.intro"))
        return choiceRounds(
            ctx, "emoji",
            picked.map { r -> GamesLogic.riddleOptions(r, ctx.rng).let { (options, answer) -> Round(t("games.emoji.q"), r.e, null, options, answer) } }
        ) { score, total -> "${t("games.emoji.score", "score" to score, "total" to total)} ${GamesLogic.quizVerdict(score, total)}" to (score == total) }
    }

    private fun reaction(ctx: Ctx): Game = object : Game {
        val times = mutableListOf<Long>()
        var state = "idle"
        var big = "⚡"
        var line = t("games.reaction.start")
        var goAt = 0L
        var handle: Any? = null
        fun set(s: String, e: String, text: String) {
            state = s
            big = e
            line = text
        }

        init {
            ctx.meta(t("games.reaction.round", "n" to 1, "total" to REACTION_ROUNDS))
            ctx.status(t("games.reaction.intro"))
        }

        fun start() {
            set("wait", "👀", t("games.reaction.wait"))
            ctx.meta(t("games.reaction.round", "n" to times.size + 1, "total" to REACTION_ROUNDS))
            handle = ctx.timer(GamesLogic.reactionDelay(ctx.rng)) {
                handle = null
                goAt = ctx.now()
                set("go", "🟢", t("games.reaction.go"))
            }
        }

        override fun board() = Board.Reaction(state, big, line)

        // On press, not on release, so the time is the visitor's.
        override fun input(a: GameInput) {
            if (a !is GameInput.Reaction) return
            when (state) {
                "idle", "between" -> start()
                "wait" -> {
                    handle?.let(ctx::clear)
                    handle = null
                    set("between", "🙈", t("games.reaction.tooSoon"))
                    ctx.status(t("games.reaction.tooSoonHint"))
                }
                "go" -> {
                    val ms = ctx.now() - goAt
                    if (!GamesLogic.reactionCounts(ms)) {
                        set("between", "🙈", t("games.reaction.lucky"))
                        ctx.status(t("games.reaction.luckyHint"))
                        return
                    }
                    times.add(ms)
                    if (times.size >= REACTION_ROUNDS) {
                        val avg = GamesLogic.average(times)
                        set("done", "🏁", t("sdk.games.reactionAvg", "ms" to avg))
                        ctx.meta(times.joinToString(" · ") + " ms")
                        ctx.finish("${GamesLogic.reactionVerdict(avg)} - ${t("games.reaction.avg", "ms" to avg)}", avg < 350)
                        return
                    }
                    set("between", "⏱️", t("games.reaction.next", "ms" to ms))
                    ctx.status(t("games.reaction.ms", "ms" to ms))
                }
            }
        }

        override fun pause() {
            handle?.let(ctx::clear)
            handle = null
            if (state == "wait" || state == "go") set("between", "⏸️", t("games.reaction.resume"))
        }
    }

    private fun scramble(ctx: Ctx): Game = object : Game {
        val words = GamesLogic.shuffle(GamesLogic.wordsFor(lang), ctx.rng).take(SCRAMBLE_ROUNDS)
        var at = 0
        var solved = 0
        var letters: List<String> = emptyList()
        var placed = mutableListOf<Int>()
        var isSolved = false
        var revealed = false
        var nope = 0
        var word = ""
        var hint = ""

        fun round() {
            word = words[at].w
            hint = words[at].hint
            letters = GamesLogic.scrambleWord(word, ctx.rng)
            placed = mutableListOf()
            isSolved = false
            revealed = false
            ctx.meta(t("games.scramble.meta", "n" to at + 1, "total" to SCRAMBLE_ROUNDS, "solved" to solved))
        }

        fun next(msg: String, win: Boolean) {
            at += 1
            if (win) solved += 1
            ctx.meta(t("games.scramble.meta", "n" to minOf(at + 1, SCRAMBLE_ROUNDS), "total" to SCRAMBLE_ROUNDS, "solved" to solved))
            if (at >= SCRAMBLE_ROUNDS) {
                val verdict = if (solved == SCRAMBLE_ROUNDS) t("games.scramble.verdict.all") else if (solved > 0) GamesLogic.Verdict.NICE else t("games.scramble.verdict.none")
                ctx.finish("$msg ${t("games.scramble.score", "solved" to solved, "total" to SCRAMBLE_ROUNDS)} $verdict", solved == SCRAMBLE_ROUNDS)
                return
            }
            ctx.status(msg)
            ctx.later(if (win) 1100 else 1500) { round() }
        }

        fun check() {
            if (placed.size < letters.size) return
            val guess = placed.joinToString("") { letters[it] }
            if (GamesLogic.scrambleSolved(guess, word)) {
                isSolved = true
                ctx.cheer()
                next(t("games.scramble.gotIt", "word" to word), true)
            } else {
                nope += 1
                ctx.status(t("games.scramble.wrong"))
            }
        }

        fun put(i: Int) {
            if (i < 0 || i >= letters.size || i in placed || placed.size >= letters.size || isSolved) return
            placed.add(i)
            check()
        }

        init {
            // The web's intro says "or just type": a phone has no keys to type on.
            ctx.status(t("sdk.games.scrambleIntro"))
            round()
        }

        override fun board() = Board.Scramble(hint, letters.toList(), placed.toList(), isSolved, revealed, nope, if (at == SCRAMBLE_ROUNDS - 1) t("games.scramble.reveal") else t("common.skip"))

        override fun input(a: GameInput) {
            when (a) {
                is GameInput.Tile -> put(a.i)
                is GameInput.Slot -> {
                    if (isSolved || a.i < 0 || a.i >= placed.size) return
                    while (placed.size > a.i) placed.removeAt(placed.size - 1)
                }
                is GameInput.Key -> {
                    if (isSolved) return
                    if (a.key == "Backspace") {
                        if (placed.isNotEmpty()) placed.removeAt(placed.size - 1)
                        return
                    }
                    if (a.key.length != 1 || a.key[0].lowercaseChar() !in 'a'..'z') return
                    val i = letters.indices.firstOrNull { j -> letters[j] == a.key.uppercase() && j !in placed } ?: -1
                    if (i >= 0) put(i)
                }
                GameInput.Skip -> {
                    if (isSolved) return
                    isSolved = true
                    revealed = true
                    placed = mutableListOf()
                    word.forEach { ch -> placed.add(letters.indices.firstOrNull { j -> letters[j] == ch.toString() && j !in placed } ?: -1) }
                    next(t("games.scramble.itWas", "word" to word), false)
                }
                else -> {}
            }
        }
    }

    private fun factory(id: String, ctx: Ctx): Game = when (id) {
        "rps" -> rps(ctx)
        "ttt" -> ttt(ctx)
        "quiz" -> quiz(ctx)
        "memory" -> memory(ctx)
        "reaction" -> reaction(ctx)
        "emoji" -> emoji(ctx)
        else -> scramble(ctx)
    }

    // ------------------------------------------------------------------ the card

    var state: GamesCardState = GamesCardState(
        view = "menu", title = "", titleEmoji = "", meta = "", status = "", result = null, settled = false, closable = true,
        chips = available, game = null, board = null, foot = emptyList(), cheer = 0, confetti = 0
    )
        private set

    private val listeners = linkedSetOf<(GamesCardState) -> Unit>()
    private var current: Entry? = null
    private var plays = 0
    private var action: (() -> Unit)? = null
    private var held: MutableList<() -> Unit> = mutableListOf()

    private fun emit() {
        current?.let { state = state.copy(board = it.game.board()) }
        val snap = state
        listeners.toList().forEach { runCatching { it(snap) } }
    }

    private fun patch(p: GamesCardState.() -> GamesCardState) {
        state = state.p()
    }

    private fun stopTimers() {
        current?.timers?.forEach(timer::clear)
    }

    private val quietAnother = FootButton("another", GamesText.ANOTHER, false)

    private fun showMenu(first: Boolean) {
        stopTimers()
        current = null
        action = null
        patch {
            copy(
                view = "menu", settled = false, closable = true, game = null, board = null, titleEmoji = "",
                title = if (first && mode != "menu") WaitingGames.offerLine(settings.offerText) else GamesText.PICK,
                meta = "", status = "", result = null,
                foot = listOf(FootButton("notNow", GamesText.NOT_NOW, false))
            )
        }
    }

    private fun openGame(id: String) {
        val g = WaitingGames.gameMeta(id)
        if (g == null || id !in settings.games || state.view == "gone") return
        stopTimers()
        action = null
        patch { copy(view = "game", game = id, titleEmoji = g.emoji, title = g.name, meta = "", status = "", result = null, settled = false, closable = true, foot = listOf(quietAnother)) }
        val entry = Entry(id)
        val ctx = Ctx(rng, reducedMotion, plays, entry)
        plays += 1
        current = entry
        entry.game = factory(id, ctx)
    }

    private fun removeCard() {
        stopTimers()
        current = null
        action = null
        patch { copy(view = "gone", board = null, foot = emptyList()) }
    }

    private fun resume() {
        val cur = current ?: return
        if (state.view != "paused") return
        val g = WaitingGames.gameMeta(cur.id)
        patch { copy(view = "game", titleEmoji = g?.emoji.orEmpty(), title = g?.name.orEmpty(), status = "", foot = emptyList()) }
        val run = held.toList()
        held.clear()
        run.forEach { it() }
    }

    init {
        showMenu(true)
    }

    fun subscribe(fn: (GamesCardState) -> Unit): () -> Unit {
        listeners.add(fn)
        return { listeners.remove(fn) }
    }

    fun open(id: String) {
        openGame(id)
        emit()
    }

    override fun menu() {
        if (state.view == "gone") return
        showMenu(false)
        emit()
    }

    /** A footer button. */
    fun press(key: String) {
        if (state.foot.none { it.key == key }) return
        if (key == "notNow") return dismiss()
        val cur = current
        when {
            key == "again" && cur != null -> openGame(cur.id)
            key == "another" -> showMenu(false)
            key == "resume" -> resume()
            key == "action" && action != null -> {
                val fn = action!!
                action = null
                patch { copy(foot = foot.filter { it.key != "action" }) }
                fn()
            }
        }
        emit()
    }

    /** A tap inside the game. */
    fun input(a: GameInput) {
        val cur = current ?: return
        if (state.view != "game" || cur.over) return
        cur.game.input(a)
        emit()
    }

    /** The x. */
    fun dismiss() {
        if (state.view == "gone") return
        removeCard()
        emit()
        onDismiss()
    }

    override fun wrapUp(name: String) {
        if (state.view == "paused" || state.view == "gone") return
        val cur = current
        if (cur == null) {
            // Only the offer or the menu: nothing to keep.
            removeCard()
            emit()
            return
        }
        if (cur.over) {
            // A finished game stays as a result, without its buttons.
            patch { copy(foot = emptyList(), settled = true, closable = false) }
            emit()
            return
        }
        cur.game.pause()
        // Hold the game's pending steps for "Finish my game": dropped, the board would stay locked.
        held = cur.queued.values.toMutableList()
        stopTimers()
        cur.timers.clear()
        cur.queued.clear()
        action = null
        val who = name.trim().take(40)
        patch {
            copy(
                view = "paused", titleEmoji = "👋",
                title = if (who.isNotEmpty()) GamesText.pausedNamed(who) else GamesText.PAUSED_SOMEONE,
                status = GamesText.PAUSED_SAVED,
                foot = listOf(FootButton("resume", GamesText.RESUME, false))
            )
        }
        emit()
    }

    override fun remove() {
        if (state.view == "gone") return
        removeCard()
        emit()
    }

    /** A game is under way (not the menu, not a finished one). */
    override fun playing(): Boolean = state.view == "game" && current?.over == false

    override fun up(): Boolean = state.view != "gone"
}
