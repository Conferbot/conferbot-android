package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.waiting.GamesLogic.Hand
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The waiting games' rules (core/waiting/GamesLogic.kt), the cases of the
 * React Native SDK's __tests__/core/gamesLogic.test.ts (the web widget's
 * tests/waitingGames.test.js).
 */
class GamesLogicTest {

    // A repeatable "random": a fixed sequence.
    private fun seq(vararg xs: Double): Rng {
        var i = 0
        return { xs[i++ % xs.size] }
    }

    private fun b(s: String): List<String?> = s.map { if (it == 'X' || it == 'O') it.toString() else null }

    @Test
    fun `rock paper scissors - every pairing`() {
        assertThat(GamesLogic.rpsOutcome(Hand.ROCK, Hand.SCISSORS)).isEqualTo("win")
        assertThat(GamesLogic.rpsOutcome(Hand.PAPER, Hand.ROCK)).isEqualTo("win")
        assertThat(GamesLogic.rpsOutcome(Hand.SCISSORS, Hand.PAPER)).isEqualTo("win")
        assertThat(GamesLogic.rpsOutcome(Hand.ROCK, Hand.PAPER)).isEqualTo("lose")
        assertThat(GamesLogic.rpsOutcome(Hand.PAPER, Hand.SCISSORS)).isEqualTo("lose")
        assertThat(GamesLogic.rpsOutcome(Hand.SCISSORS, Hand.ROCK)).isEqualTo("lose")
        GamesLogic.HANDS.forEach { assertThat(GamesLogic.rpsOutcome(it, it)).isEqualTo("draw") }
    }

    @Test
    fun `rock paper scissors - best of three, the first to two`() {
        assertThat(GamesLogic.rpsWinner(1, 1)).isNull()
        assertThat(GamesLogic.rpsWinner(2, 1)).isEqualTo("you")
        assertThat(GamesLogic.rpsWinner(0, 2)).isEqualTo("bot")
    }

    @Test
    fun `tic-tac-toe - rows, columns, diagonals, draws`() {
        assertThat(GamesLogic.tttWinner(b("XXX......"))).isEqualTo(GamesLogic.TttResult("X", listOf(0, 1, 2)))
        assertThat(GamesLogic.tttWinner(b("O..O..O.."))).isEqualTo(GamesLogic.TttResult("O", listOf(0, 3, 6)))
        assertThat(GamesLogic.tttWinner(b("..X.X.X.."))).isEqualTo(GamesLogic.TttResult("X", listOf(2, 4, 6)))
        assertThat(GamesLogic.tttWinner(b("XOXXOOOXX"))).isEqualTo(GamesLogic.TttResult("draw", emptyList()))
        assertThat(GamesLogic.tttWinner(b("XO......."))).isNull()
    }

    @Test
    fun `tic-tac-toe - the bot takes a win before it blocks, and always blocks`() {
        assertThat(GamesLogic.tttBotMove(b("OO.XX...."), GamesLogic.random, 1.0)).isEqualTo(2)
        assertThat(GamesLogic.tttBotMove(b("XX..O...."), { 0.0 }, 1.0)).isEqualTo(2)
        assertThat(GamesLogic.tttBotMove(b("X...X...."), { 0.99 }, 1.0)).isEqualTo(8)
    }

    @Test
    fun `tic-tac-toe - with no slip the bot never loses`() {
        fun play(board: List<String?>, turn: String): Boolean {
            val w = GamesLogic.tttWinner(board)
            if (w != null) return w.winner != "X"
            if (turn == "O") {
                val m = GamesLogic.tttBotMove(board, { 0.0 }, 0.0)
                return play(board.toMutableList().also { it[m] = "O" }, "X")
            }
            return board.indices.all { i -> board[i] != null || play(board.toMutableList().also { it[i] = "X" }, "O") }
        }
        assertThat(play(List(9) { null }, "X")).isTrue()
    }

    @Test
    fun `tic-tac-toe - the bot can slip into a casual move, so it can be beaten`() {
        val moves = listOf(0.0, 0.2, 0.5, 0.8, 0.95).map { r -> GamesLogic.tttBotMove(b("X........"), seq(0.0, r), 1.0) }.toSet()
        assertThat(moves.size).isGreaterThan(1)
        assertThat(GamesLogic.tttBotMove(b("X........"), { 0.99 }, 0.0)).isEqualTo(4)
    }

    @Test
    fun `memory - six pairs, shuffled`() {
        val deck = GamesLogic.memoryDeck(GamesLogic.MEMORY_FACES, 6, seq(0.1, 0.7, 0.3, 0.9, 0.5))
        assertThat(deck).hasSize(12)
        val counts = deck.groupingBy { it.face }.eachCount()
        assertThat(counts).hasSize(6)
        assertThat(counts.values.all { it == 2 }).isTrue()
        assertThat(deck.map { it.id }).isEqualTo((0 until 12).toList())
    }

    @Test
    fun `memory - a pair stays up, a miss is counted and turned back, the last pair ends it`() {
        val cards = listOf("A", "B", "A", "B").mapIndexed { id, face -> GamesLogic.MemoryCard(id, face, false) }
        var s = GamesLogic.MemoryState(cards, emptyList(), 0)
        var r = GamesLogic.memoryFlip(s, 0)
        assertThat(r.result).isEqualTo("open")
        r = GamesLogic.memoryFlip(r.state, 0)
        assertThat(r.result).isEqualTo("ignored")
        r = GamesLogic.memoryFlip(r.state, 1)
        assertThat(r.result).isEqualTo("miss")
        assertThat(r.state.moves).isEqualTo(1)
        assertThat(GamesLogic.memoryFlip(r.state, 2).result).isEqualTo("ignored")
        s = GamesLogic.memoryHide(r.state)
        r = GamesLogic.memoryFlip(GamesLogic.memoryFlip(s, 0).state, 2)
        assertThat(r.result).isEqualTo("match")
        assertThat(r.state.cards[0].matched && r.state.cards[2].matched).isTrue()
        assertThat(GamesLogic.memoryFlip(r.state, 0).result).isEqualTo("ignored")
        r = GamesLogic.memoryFlip(GamesLogic.memoryFlip(r.state, 1).state, 3)
        assertThat(r.result).isEqualTo("done")
        assertThat(r.state.moves).isEqualTo(3)
    }

    @Test
    fun `memory - stars for fewer moves`() {
        assertThat(GamesLogic.memoryStars(6)).isEqualTo(3)
        assertThat(GamesLogic.memoryStars(9)).isEqualTo(3)
        assertThat(GamesLogic.memoryStars(12)).isEqualTo(2)
        assertThat(GamesLogic.memoryStars(20)).isEqualTo(1)
    }

    private val cats = listOf(
        mapOf(
            "name" to "Billing",
            "articles" to listOf(
                mapOf("_id" to "1", "title" to "Downloading invoices", "description" to "Find every invoice in one place."),
                mapOf("_id" to "2", "title" to "Changing your plan", "description" to "Upgrade, downgrade or cancel at any time.")
            )
        ),
        mapOf("name" to "Account", "articles" to listOf(mapOf("_id" to "3", "title" to "Resetting your password", "description" to ""))),
        mapOf("name" to "Shipping", "articles" to listOf(mapOf("_id" to "4", "title" to "Tracking an order", "description" to "See where your parcel is.")))
    )

    @Test
    fun `quiz - questions from the help centre, the right answer among the options`() {
        val qs = GamesLogic.quizFromArticles(cats, emptyList(), seq(0.3, 0.6, 0.1, 0.8))
        assertThat(qs).hasSize(3)
        qs.forEach { q ->
            assertThat(q.source).isEqualTo("kb")
            assertThat(q.options).hasSize(3)
            assertThat(q.answer).isAtLeast(0)
            assertThat(q.options.toSet()).hasSize(3)
        }
        val pw = qs.first { it.q.contains("Resetting your password") }
        assertThat(pw.options[pw.answer]).isEqualTo("Account")
        assertThat(pw.q).isEqualTo("Where in our help centre would you find \"Resetting your password\"?")
    }

    @Test
    fun `quiz - too few articles means trivia`() {
        assertThat(GamesLogic.quizFromArticles(listOf(mapOf("name" to "x", "articles" to listOf(mapOf("title" to "Only one")))), emptyList())).isEmpty()
        val qs = GamesLogic.buildQuiz(GamesLogic.TRIVIA, emptyList(), seq(0.2, 0.4, 0.6))
        assertThat(qs).hasSize(3)
        assertThat(qs.all { it.source == "trivia" && it.options.size == 3 }).isTrue()
    }

    @Test
    fun `quiz - help-centre questions come first, topped up with trivia`() {
        val kb = GamesLogic.quizFromArticles(cats, emptyList(), seq(0.5)).take(1)
        assertThat(GamesLogic.buildQuiz(GamesLogic.TRIVIA, kb, seq(0.5)).map { it.source }).containsExactly("kb", "trivia", "trivia").inOrder()
    }

    @Test
    fun `quiz - scoring and the verdict`() {
        val qs = listOf(0, 2, 1).map { GamesLogic.QuizQuestion("q", emptyList(), it, "trivia") }
        assertThat(GamesLogic.quizScore(qs, listOf(0, 2, 1))).isEqualTo(3)
        assertThat(GamesLogic.quizScore(qs, listOf(0, 1, 1))).isEqualTo(2)
        assertThat(GamesLogic.quizScore(qs, listOf(1, 1, 0))).isEqualTo(0)
        assertThat(GamesLogic.quizVerdict(3, 3)).contains("Perfect")
        assertThat(GamesLogic.quizVerdict(0, 3)).contains("another go")
        assertThat(GamesLogic.quizVerdict(2, 3)).contains("So close")
    }

    @Test
    fun `reflex - a fair random wait and a friendly verdict`() {
        assertThat(GamesLogic.reactionDelay { 0.0 }).isEqualTo(1200)
        assertThat(GamesLogic.reactionDelay { 1.0 }).isEqualTo(3400)
        assertThat(GamesLogic.reactionVerdict(200)).contains("Lightning")
        assertThat(GamesLogic.reactionVerdict(300)).contains("Sharp")
        assertThat(GamesLogic.reactionVerdict(400)).contains("Nicely")
        assertThat(GamesLogic.reactionVerdict(600)).contains("steady")
        assertThat(GamesLogic.average(listOf(200L, 300L, 401L))).isEqualTo(300)
        assertThat(GamesLogic.reactionCounts(40)).isFalse()
        assertThat(GamesLogic.reactionCounts(180)).isTrue()
    }

    @Test
    fun `word scramble - never the word itself, and any case solves it`() {
        GamesLogic.WORDS.forEach { (w) ->
            val s = GamesLogic.scrambleWord(w, seq(0.99, 0.98, 0.97, 0.96, 0.95, 0.94))
            assertThat(s.joinToString("")).isNotEqualTo(w)
            assertThat(s.sorted()).isEqualTo(w.map { it.toString() }.sorted())
        }
        assertThat(GamesLogic.scrambleSolved(" planet ", "PLANET")).isTrue()
        assertThat(GamesLogic.scrambleSolved("PLANTE", "PLANET")).isFalse()
    }

    @Test
    fun `emoji riddles - four options, one right`() {
        GamesLogic.RIDDLES.forEach { r ->
            val (options, answer) = GamesLogic.riddleOptions(r, seq(0.4, 0.1))
            assertThat(options).hasSize(4)
            assertThat(options[answer]).isEqualTo(r.a)
            assertThat(options.toSet()).hasSize(4)
        }
    }

    @Test
    fun `no em dashes in the words`() {
        val all = listOf(GamesLogic.TRIVIA, GamesLogic.RIDDLES, GamesLogic.WORDS, GamesLogic.Verdict.all).toString()
        assertThat(all.contains('—')).isFalse()
    }
}
