package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.i18n.I18nGames

/** A source of "random" numbers in [0, 1): Math.random, or a fixed sequence in tests. */
typealias Rng = () -> Double

/**
 * Waiting games: the rules and the words, with no UI, so they can be tested
 * on their own. Ported from the React Native SDK's src/core/gamesLogic.ts
 * (the web widget's conferbot-widget src/ts/chat/games/logic.ts, every rule
 * as it is, and src/ts/chat/games/content.ts: the quiz and scramble per
 * language from core/i18n/I18nGames.kt, the emoji riddles English wordplay).
 * Words are the web's "games.*" keys, read with t(). Every random choice
 * takes an [Rng] so tests can pin it.
 */
object GamesLogic {

    val random: Rng = { Math.random() }

    /** A shuffled copy (Fisher-Yates). */
    fun <T> shuffle(items: List<T>, rng: Rng = random): MutableList<T> {
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = Math.floor(rng() * (i + 1)).toInt()
            val t = out[i]
            out[i] = out[j]
            out[j] = t
        }
        return out
    }

    fun <T> pick(items: List<T>, rng: Rng = random): T = items[Math.floor(rng() * items.size).toInt() % items.size]

    // ---------------------------------------------------------------- content (web content.ts)

    data class Trivia(val q: String, val a: String, val wrong: List<String>)
    data class Riddle(val e: String, val a: String, val wrong: List<String>)
    data class Word(val w: String, val hint: String)

    /** The English quiz (web content.ts TRIVIA; every language in core/i18n/I18nGames.kt). */
    val TRIVIA: List<Trivia> get() = I18nGames.triviaOf("en")

    /** The quiz in a language, English for any other (web content.ts triviaFor). */
    fun triviaFor(lang: String): List<Trivia> = I18nGames.triviaOf(lang)

    /** The scramble words in a language, English for any other (web content.ts wordsFor). */
    fun wordsFor(lang: String): List<Word> = I18nGames.wordsOf(lang)

    /** The emoji riddles: English wordplay, offered in English only (web gamesForLanguage). */
    val RIDDLES: List<Riddle> = listOf(
        Riddle("☀️🌻", "Sunflower", listOf("Daisy", "Sunrise", "Sunscreen")),
        Riddle("⭐🐟", "Starfish", listOf("Goldfish", "Swordfish", "Shooting star")),
        Riddle("🥵🐶", "Hot dog", listOf("Puppy love", "Sunbathing", "Dog days")),
        Riddle("🍿🎬", "Movie night", listOf("Popcorn party", "Film star", "Snack time")),
        Riddle("🐛📚", "Bookworm", listOf("Library", "Caterpillar", "Homework")),
        Riddle("🧈🪰", "Butterfly", listOf("Dragonfly", "Firefly", "Bumblebee")),
        Riddle("🌙🚶", "Moonwalk", listOf("Night shift", "Sleepwalk", "Stargazing")),
        Riddle("☕⏸️", "Coffee break", listOf("Morning rush", "Tea time", "Wake up call")),
        Riddle("🐟🍟", "Fish and chips", listOf("Sushi", "Fish fingers", "Seafood")),
        Riddle("🍯🌙", "Honeymoon", listOf("Sweet dreams", "Moonlight", "Bedtime")),
        Riddle("🌧️🏹", "Rainbow", listOf("Raincoat", "Archery", "Storm")),
        Riddle("☀️👓", "Sunglasses", listOf("Sunburn", "Eyesight", "Daylight")),
        Riddle("🧀🎂", "Cheesecake", listOf("Pizza", "Fondue", "Cupcake")),
        Riddle("⌚🐶", "Watchdog", listOf("Hot dog", "Dog walk", "Clockwork")),
        Riddle("🦶⚽", "Football", listOf("Footprint", "Basketball", "Kick off")),
        Riddle("🌊🏄", "Surfing", listOf("Swimming", "Sailing", "Beach day"))
    )

    /** The English scramble words (web content.ts WORDS). */
    val WORDS: List<Word> get() = I18nGames.wordsOf("en")

    val MEMORY_FACES: List<String> = listOf("🍕", "🚀", "🌵", "🐙", "🎈", "🍩", "🦊", "🌈", "⚽", "🎧", "🍉", "🐢", "🦄", "🍦")

    // ---------------------------------------------------------------- RPS

    enum class Hand(val id: String, val emoji: String) {
        ROCK("rock", "✊"),
        PAPER("paper", "✋"),
        SCISSORS("scissors", "✌️");

        /** The hand's name in the active language (web ui.ts handName, games.rps.*). */
        val label: String get() = t("games.rps.$id")

        val beats: Hand get() = when (this) { ROCK -> SCISSORS; PAPER -> ROCK; SCISSORS -> PAPER }
    }

    val HANDS: List<Hand> = listOf(Hand.ROCK, Hand.PAPER, Hand.SCISSORS)

    /** From the visitor's side: "win", "lose" or "draw". */
    fun rpsOutcome(you: Hand, bot: Hand): String = if (you == bot) "draw" else if (you.beats == bot) "win" else "lose"

    /** Best of three: the first to two wins. */
    const val RPS_TO_WIN = 2

    /** "you", "bot", or null while nobody has two. */
    fun rpsWinner(you: Int, bot: Int): String? = if (you >= RPS_TO_WIN) "you" else if (bot >= RPS_TO_WIN) "bot" else null

    // ---------------------------------------------------------------- Tic-tac-toe

    /** A square: "X", "O" or null. */
    val LINES: List<List<Int>> = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )

    /** The winner ("X", "O" or "draw") and the line. */
    data class TttResult(val winner: String, val line: List<Int>)

    /** The winner and the line, "draw" on a full board, else null. */
    fun tttWinner(board: List<String?>): TttResult? {
        for ((a, b, c) in LINES) {
            val v = board[a]
            if (v != null && v == board[b] && v == board[c]) return TttResult(v, listOf(a, b, c))
        }
        return if (board.all { it != null }) TttResult("draw", emptyList()) else null
    }

    private fun empties(board: List<String?>) = board.indices.filter { board[it] == null }

    /** The square that completes a line for [who], or -1. */
    fun tttFinisher(board: List<String?>, who: String): Int {
        for (line in LINES) {
            val mine = line.count { board[it] == who }
            val open = line.filter { board[it] == null }
            if (mine == 2 && open.size == 1) return open[0]
        }
        return -1
    }

    private fun minimax(board: MutableList<String?>, turn: String, depth: Int): Int {
        val w = tttWinner(board)
        if (w != null) return if (w.winner == "O") 10 - depth else if (w.winner == "X") depth - 10 else 0
        val scores = empties(board).map { i ->
            board[i] = turn
            val s = minimax(board, if (turn == "O") "X" else "O", depth + 1)
            board[i] = null
            s
        }
        return if (turn == "O") scores.max() else scores.min()
    }

    /**
     * The bot (O): always takes a win, always blocks one, and otherwise plays
     * its best move - except now and then ([slip], 0-1) it plays a casual one,
     * so a sharp visitor can set up a fork and beat it. Smart, but beatable.
     */
    fun tttBotMove(board: List<String?>, rng: Rng = random, slip: Double = 0.3): Int {
        val open = empties(board)
        if (open.isEmpty()) return -1
        val win = tttFinisher(board, "O")
        if (win >= 0) return win
        val block = tttFinisher(board, "X")
        if (block >= 0) return block
        if (rng() < slip) return pick(open, rng)
        val b = board.toMutableList()
        var best = Int.MIN_VALUE
        var moves = mutableListOf<Int>()
        for (i in open) {
            b[i] = "O"
            val s = minimax(b, "X", 1)
            b[i] = null
            if (s > best) {
                best = s
                moves = mutableListOf(i)
            } else if (s == best) moves.add(i)
        }
        return pick(moves, rng)
    }

    // ---------------------------------------------------------------- Memory

    data class MemoryCard(val id: Int, val face: String, val matched: Boolean)

    data class MemoryState(
        val cards: List<MemoryCard>,
        /** Face-up cards not yet matched (0, 1 or 2). */
        val open: List<Int>,
        val moves: Int
    )

    /** "ignored", "open", "match", "miss" or "done", and the state after. */
    data class Flip(val state: MemoryState, val result: String)

    /** [pairs] faces from the pool, each twice, shuffled (4x3 = 6 pairs). */
    fun memoryDeck(pool: List<String>, pairs: Int = 6, rng: Rng = random): List<MemoryCard> =
        shuffle(shuffle(pool, rng).take(pairs).flatMap { listOf(it, it) }, rng).mapIndexed { id, face -> MemoryCard(id, face, false) }

    /**
     * Turn a card over. Two open cards make a move: a pair stays up, a miss
     * reports "miss" (the caller turns them back after a beat with [memoryHide]).
     */
    fun memoryFlip(s: MemoryState, id: Int): Flip {
        val card = s.cards.getOrNull(id)
        if (card == null || card.matched || id in s.open || s.open.size >= 2) return Flip(s, "ignored")
        val open = s.open + id
        if (open.size < 2) return Flip(s.copy(open = open), "open")
        val a = s.cards[open[0]]
        val b = s.cards[open[1]]
        val moves = s.moves + 1
        if (a.face == b.face) {
            val cards = s.cards.map { if (it.id == a.id || it.id == b.id) it.copy(matched = true) else it }
            val done = cards.all { it.matched }
            return Flip(MemoryState(cards, emptyList(), moves), if (done) "done" else "match")
        }
        return Flip(s.copy(open = open, moves = moves), "miss")
    }

    fun memoryHide(s: MemoryState): MemoryState = s.copy(open = emptyList())

    /** Three stars for a near-perfect run, down to one. */
    fun memoryStars(moves: Int, pairs: Int = 6): Int = if (moves <= pairs + 3) 3 else if (moves <= pairs * 2 + 2) 2 else 1

    // ---------------------------------------------------------------- Quiz

    data class QuizQuestion(
        val q: String,
        val options: List<String>,
        val answer: Int,
        /** Where it came from: "kb" (the bot's help centre) or "trivia". */
        val source: String
    )

    private fun cleanText(v: Any?, max: Int): String =
        (v as? String)?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(max) ?: ""

    /** Options in a random order, the right one tracked. */
    private fun asQuestion(q: String, right: String, wrong: List<String>, source: String, rng: Rng): QuizQuestion {
        val options = shuffle(listOf(right) + wrong, rng)
        return QuizQuestion(q, options, options.indexOf(right), source)
    }

    // Compared by identity, as the web's `o !== it`.
    private class Item(val title: String, val desc: String, val cat: String)

    /**
     * Questions from the bot's own help centre: "Which of our articles helps
     * with: <its summary>" and "Where in our help centre would you find
     * <title>?". Needs three titled articles (and three categories for the
     * second kind); none otherwise.
     */
    fun quizFromArticles(categories: List<Any?>?, recent: List<Any?>?, rng: Rng = random, count: Int = 3): List<QuizQuestion> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<Item>()
        fun add(a: Any?, cat: String) {
            val m = a as? Map<*, *>
            val title = cleanText(m?.get("title"), 70)
            if (title.isEmpty() || !seen.add(title.lowercase())) return
            items.add(Item(title, cleanText(m?.get("description"), 120), cat))
        }
        (categories ?: emptyList()).forEach { c ->
            val m = c as? Map<*, *>
            ((m?.get("articles") as? List<*>) ?: emptyList<Any?>()).forEach { add(it, cleanText(m?.get("name"), 40)) }
        }
        (recent ?: emptyList()).forEach { add(it, "") }
        if (items.size < 3) return emptyList()
        val cats = items.map { it.cat }.filter { it.isNotEmpty() }.distinct()
        val out = mutableListOf<QuizQuestion>()
        for (it in shuffle(items, rng)) {
            if (out.size >= count) break
            val others = shuffle(items.filter { o -> o !== it }, rng).map { o -> o.title }.take(2)
            if (it.desc.isNotEmpty() && it.desc.lowercase() != it.title.lowercase() && others.size == 2) {
                out.add(asQuestion(t("games.quiz.kbWhich", "desc" to it.desc), it.title, others, "kb", rng))
            } else if (it.cat.isNotEmpty() && cats.size >= 3) {
                val wrong = shuffle(cats.filter { c -> c != it.cat }, rng).take(2)
                out.add(asQuestion(t("games.quiz.kbWhere", "title" to it.title), it.cat, wrong, "kb", rng))
            }
        }
        return out
    }

    /** The quiz: help-centre questions first, topped up with trivia. */
    fun buildQuiz(trivia: List<Trivia>, kb: List<QuizQuestion>, rng: Rng = random, count: Int = 3): List<QuizQuestion> {
        val out = kb.take(count).toMutableList()
        for (one in shuffle(trivia, rng)) {
            if (out.size >= count) break
            out.add(asQuestion(one.q, one.a, one.wrong.take(2), "trivia", rng))
        }
        return out
    }

    /** How many of [picks] are right. */
    fun quizScore(questions: List<QuizQuestion>, picks: List<Int>): Int =
        questions.indices.count { picks.getOrNull(it) == questions[it].answer }

    /** The verdicts in the active language (web logic.ts, games.verdict.*). */
    object Verdict {
        val PERFECT: String get() = t("games.verdict.perfect")
        val TOUGH: String get() = t("games.verdict.tough")
        val CLOSE: String get() = t("games.verdict.close")
        val NOT_BAD: String get() = t("games.verdict.notBad")
        val NICE: String get() = t("games.verdict.nice")
        val all: List<String> get() = listOf(PERFECT, TOUGH, CLOSE, NOT_BAD, NICE)
    }

    fun quizVerdict(score: Int, total: Int): String =
        if (score == total) Verdict.PERFECT else if (score == 0) Verdict.TOUGH else if (score >= total - 1) Verdict.CLOSE else Verdict.NOT_BAD

    // ---------------------------------------------------------------- Reflex

    /** A random wait before green: 1.2 to 3.4 seconds (JS Math.round: half up). */
    fun reactionDelay(rng: Rng = random): Long = 1200L + Math.round(rng() * 2200)

    /** Faster than any person can see it: a lucky guess, counted as too soon. */
    const val REACTION_MIN_MS = 100L

    fun reactionCounts(ms: Long): Boolean = ms >= REACTION_MIN_MS

    fun reactionVerdict(ms: Long): String =
        t(if (ms < 250) "games.reaction.fast" else if (ms < 350) "games.reaction.sharp" else if (ms < 500) "games.verdict.nice" else "games.reaction.steady")

    fun average(xs: List<Long>): Long = if (xs.isEmpty()) 0L else Math.round(xs.sum().toDouble() / xs.size)

    // ---------------------------------------------------------------- Word scramble

    /** The word's letters in an order that is not the word itself. */
    fun scrambleWord(word: String, rng: Rng = random): List<String> {
        val letters = word.uppercase().map { it.toString() }
        if (letters.toSet().size < 2) return letters
        repeat(20) {
            val s = shuffle(letters, rng)
            if (s.joinToString("") != letters.joinToString("")) return s
        }
        return letters.reversed()
    }

    fun scrambleSolved(guess: String, word: String): Boolean = guess.trim().uppercase() == word.trim().uppercase()

    // ---------------------------------------------------------------- Emoji riddles

    /** One riddle's options, shuffled, and the answer's index. */
    fun riddleOptions(r: Riddle, rng: Rng = random): Pair<List<String>, Int> {
        val options = shuffle(listOf(r.a) + r.wrong.take(3), rng)
        return options to options.indexOf(r.a)
    }
}
