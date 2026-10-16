package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.livechat.HandoverWaitingTest
import com.conferbot.sdk.core.waiting.GamesLogic.Hand
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The waiting games' card (core/waiting/GamesCard.kt), the cases of the
 * React Native SDK's __tests__/core/gamesCard.test.ts (web games/ui.ts,
 * tests/audit-games-card.test.js), on a fake clock: the menu, each game
 * played through, the footer, and folding away when a teammate joins.
 */
class GamesCardTest {

    private class Made(val k: HandoverWaitingTest.FakeClock, val card: GamesCard, val dismissed: () -> Int)

    // rng 0.9: no slip in tic-tac-toe, the bot plays its best move.
    private fun make(
        customizations: Map<String, Any?> = emptyMap(),
        rng: Rng = { 0.9 },
        reducedMotion: Boolean = false,
        mode: String = "offer",
        categories: List<Any?> = emptyList()
    ): Made {
        val k = HandoverWaitingTest.FakeClock()
        var dismissed = 0
        val card = GamesCard.create(
            customizations = customizations, timer = k, categories = categories, mode = mode, rng = rng,
            reducedMotion = reducedMotion, onDismiss = { dismissed++ }, now = { k.now }
        )!!
        return Made(k, card, { dismissed })
    }

    private val GamesCard.s get() = state
    private val GamesCard.rps get() = state.board as Board.Rps
    private val GamesCard.ttt get() = state.board as Board.Ttt
    private val GamesCard.mem get() = state.board as Board.Memory
    private val GamesCard.choice get() = state.board as Board.Choice
    private val GamesCard.pad get() = state.board as Board.Reaction
    private val GamesCard.scr get() = state.board as Board.Scramble

    @Test
    fun `an offer opens on the owner's line with a chip per game and Not now`() {
        val card = make().card
        assertThat(card.s.view).isEqualTo("menu")
        assertThat(card.s.title).isEqualTo(WaitingGames.DEFAULT_OFFER_TEXT)
        assertThat(card.s.chips.map { it.id }).containsExactly("rps", "ttt", "quiz", "memory", "reaction", "emoji", "scramble").inOrder()
        assertThat(card.s.foot.map { it.key }).containsExactly("notNow")
        assertThat(card.playing()).isFalse()
    }

    @Test
    fun `the owner's own line and games - asked for, it says Pick a game`() {
        val own = make(customizations = mapOf("waitingGames" to mapOf("offerText" to "Bored? Play!", "games" to listOf("ttt")))).card
        assertThat(own.s.title).isEqualTo("Bored? Play!")
        assertThat(own.s.chips.map { it.id }).containsExactly("ttt")
        assertThat(make(mode = "menu").card.s.title).isEqualTo("Pick a game")
    }

    @Test
    fun `no game left for the language - no card`() {
        assertThat(GamesCard.create(mapOf("waitingGames" to mapOf("games" to listOf("emoji"))), HandoverWaitingTest.FakeClock(), lang = "de")).isNull()
    }

    @Test
    fun `Not now and the x close it, once`() {
        val m = make()
        m.card.press("notNow")
        assertThat(m.card.s.view).isEqualTo("gone")
        assertThat(m.card.up()).isFalse()
        m.card.dismiss()
        assertThat(m.dismissed()).isEqualTo(1)
    }

    @Test
    fun `a game the owner turned off does not open`() {
        val card = make(customizations = mapOf("waitingGames" to mapOf("games" to listOf("ttt")))).card
        card.open("rps")
        assertThat(card.s.view).isEqualTo("menu")
    }

    @Test
    fun `rock paper scissors - the hands shake for 900ms, then the point - first to two wins`() {
        // rng 0.9: the bot always plays scissors.
        val (k, card) = make().let { it.k to it.card }
        card.open("rps")
        assertThat(card.s.title).isEqualTo("Rock paper scissors")
        assertThat(card.s.titleEmoji).isEqualTo("✊")
        assertThat(card.s.meta).isEqualTo("You 0 · 0 Bot")
        assertThat(card.s.status).isEqualTo("First to two wins. Pick your hand!")
        card.input(GameInput.Rps(Hand.ROCK))
        assertThat(card.rps.shaking).isTrue()
        assertThat(card.s.status).isEqualTo("Rock... paper... scissors...")
        // Busy: a second pick is ignored.
        card.input(GameInput.Rps(Hand.PAPER))
        k.tick(900)
        assertThat(card.rps.shaking).isFalse()
        assertThat(card.rps.you).isEqualTo("✊")
        assertThat(card.rps.bot).isEqualTo("✌️")
        assertThat(card.rps.outcome).isEqualTo("win")
        assertThat(card.rps.busy).isFalse()
        assertThat(card.s.status).isEqualTo("Rock beats scissors - point to you!")
        assertThat(card.s.meta).isEqualTo("You 1 · 0 Bot")
        card.input(GameInput.Rps(Hand.ROCK))
        k.tick(900)
        assertThat(card.s.result).isEqualTo("win")
        assertThat(card.s.status).isEqualTo("Rock beats scissors - point to you! You win 2-0! 🎉")
        assertThat(card.s.confetti).isEqualTo(1)
        assertThat(card.s.foot.map { it.key }).containsExactly("again", "another").inOrder()
        assertThat(card.playing()).isFalse()
    }

    @Test
    fun `rock paper scissors - with reduced motion the hands show at once`() {
        val card = make(reducedMotion = true).card
        card.open("rps")
        card.input(GameInput.Rps(Hand.PAPER))
        assertThat(card.s.status).isEqualTo("Scissors beats paper - point to the bot.")
        assertThat(card.rps.shaking).isFalse()
    }

    @Test
    fun `tic-tac-toe - the visitor goes first, the bot thinks for 550ms, then it is their move`() {
        val m = make()
        val card = m.card
        card.open("ttt")
        assertThat(card.s.status).isEqualTo("You go first - tap a square")
        assertThat(card.s.meta).isEqualTo("You ✕ · Bot ◯")
        card.input(GameInput.Ttt(0))
        assertThat(card.ttt.thinking).isTrue()
        card.input(GameInput.Ttt(1))
        assertThat(card.ttt.cells.count { it != null }).isEqualTo(1)
        m.k.tick(550)
        assertThat(card.ttt.cells.count { it == "O" }).isEqualTo(1)
        assertThat(card.s.status).isEqualTo("Your move")
    }

    @Test
    fun `tic-tac-toe - the bot opens every second game`() {
        val m = make()
        m.card.open("ttt")
        m.card.press("another")
        m.card.open("ttt")
        assertThat(m.card.s.status).isEqualTo("The bot is thinking...")
        m.k.tick(550)
        assertThat(m.card.ttt.cells.count { it == "O" }).isEqualTo(1)
    }

    @Test
    fun `a teammate joins while the bot is thinking - Finish my game makes its move, the board is not locked`() {
        val m = make()
        val card = m.card
        card.open("ttt")
        card.input(GameInput.Ttt(0))
        assertThat(m.k.size).isEqualTo(1)
        card.wrapUp("Sam")
        assertThat(card.s.view).isEqualTo("paused")
        assertThat(card.s.titleEmoji).isEqualTo("👋")
        assertThat(card.s.title).isEqualTo("Sam's here!")
        assertThat(card.s.status).isEqualTo("Your game is saved - finish it later?")
        assertThat(card.s.foot).containsExactly(FootButton("resume", "Finish my game", false))
        m.k.tick(5000)
        assertThat(card.ttt.cells.count { it == "O" }).isEqualTo(0)
        card.input(GameInput.Ttt(4))
        assertThat(card.ttt.cells.count { it != null }).isEqualTo(1)
        card.press("resume")
        assertThat(card.ttt.cells.count { it == "O" }).isEqualTo(1)
        assertThat(card.ttt.thinking).isFalse()
        val free = card.ttt.cells.indexOfFirst { it == null }
        card.input(GameInput.Ttt(free))
        assertThat(card.ttt.cells.count { it == "X" }).isEqualTo(2)
    }

    @Test
    fun `tic-tac-toe - a win marks the line and finishes`() {
        // rng 0 every time: a slip each move, the bot's casual move is the first free square.
        val m = make(rng = { 0.0 })
        val card = m.card
        card.open("ttt")
        card.input(GameInput.Ttt(4))
        m.k.tick(550)
        card.input(GameInput.Ttt(2))
        m.k.tick(550)
        card.input(GameInput.Ttt(8))
        m.k.tick(550)
        for (i in 0 until 9) {
            if (card.s.result != null) break
            val free = card.ttt.cells.indexOfFirst { it == null }
            if (free < 0) break
            card.input(GameInput.Ttt(free))
            m.k.tick(550)
        }
        assertThat(card.s.result).isNotNull()
        assertThat(card.ttt.over).isTrue()
        assertThat(card.s.status).isAnyOf("Three in a row - you beat the bot! 🎉", "The bot got this one. Rematch? 🤖", "A draw - great minds think alike 🤝")
    }

    private fun byFace(b: Board.Memory): List<List<Int>> =
        b.cards.indices.groupBy { b.cards[it].face }.values.toList()

    @Test
    fun `memory - a pair pops the two cards just matched - a miss turns back after 850ms`() {
        val m = make()
        val card = m.card
        card.open("memory")
        assertThat(card.s.meta).isEqualTo("Moves 0 · Pairs 0/6")
        val pairs = byFace(card.mem)
        card.input(GameInput.Memory(pairs[0][0]))
        card.input(GameInput.Memory(pairs[0][1]))
        assertThat(card.s.status).isEqualTo("A pair! ✨")
        assertThat(card.mem.pop.sorted()).isEqualTo(pairs[0].sorted())
        card.input(GameInput.Memory(pairs[1][0]))
        card.input(GameInput.Memory(pairs[2][0]))
        assertThat(card.s.status).isEqualTo("Not a pair")
        assertThat(card.mem.cards.count { it.up }).isEqualTo(4)
        // Waiting for them to turn back: taps are ignored.
        card.input(GameInput.Memory(pairs[3][0]))
        m.k.tick(850)
        assertThat(card.mem.cards.count { it.up }).isEqualTo(2)
        assertThat(card.s.meta).isEqualTo("Moves 2 · Pairs 1/6")
    }

    @Test
    fun `memory - all six pairs, the moves and the stars`() {
        val card = make().card
        card.open("memory")
        byFace(card.mem).forEach { (a, b) ->
            card.input(GameInput.Memory(a))
            card.input(GameInput.Memory(b))
        }
        assertThat(card.s.result).isEqualTo("win")
        assertThat(card.s.status).isEqualTo("All pairs in 6 moves! ⭐⭐⭐")
    }

    private fun answerAll(card: GamesCard, right: Boolean) {
        for (r in 0 until 3) {
            val b = card.choice
            card.input(GameInput.Choice(if (right) b.answer else (b.answer + 1) % b.options.size))
            if (r < 2) card.press("action")
        }
    }

    @Test
    fun `quiz - three trivia rounds - feedback, Next, Last one, the score`() {
        val card = make().card
        card.open("quiz")
        assertThat(card.s.status).isEqualTo("Three quick questions")
        assertThat(card.s.meta).isEqualTo("1 of 3 · 0 right")
        var b = card.choice
        assertThat(b.options).hasSize(3)
        assertThat(b.tag).isNull()
        card.input(GameInput.Choice(b.answer))
        assertThat(card.s.meta).isEqualTo("1 of 3 · 1 right")
        assertThat(card.s.cheer).isEqualTo(1)
        assertThat(card.s.foot.map { it.label }).containsExactly("Another game", "Next →").inOrder()
        // A second pick on the same round is ignored.
        card.input(GameInput.Choice((b.answer + 1) % 3))
        assertThat(card.s.meta).isEqualTo("1 of 3 · 1 right")
        card.press("action")
        assertThat(card.s.meta).isEqualTo("2 of 3 · 1 right")
        b = card.choice
        card.input(GameInput.Choice((b.answer + 1) % 3))
        assertThat(card.s.status).isEqualTo("Not quite - it's ${b.options[b.answer]}")
        assertThat(card.s.foot.map { it.label }).containsExactly("Another game", "Last one →").inOrder()
        card.press("action")
        b = card.choice
        card.input(GameInput.Choice(b.answer))
        assertThat(card.s.result).isEqualTo("done")
        assertThat(card.s.status).endsWith(". 2 out of 3. So close! 🌟")
    }

    @Test
    fun `quiz - with the help centre on, questions from its articles come first, tagged`() {
        val categories = listOf(
            mapOf("name" to "Billing", "articles" to listOf(
                mapOf("_id" to "1", "title" to "Downloading invoices", "description" to "Find every invoice in one place."),
                mapOf("_id" to "2", "title" to "Changing your plan", "description" to "Upgrade or cancel.")
            )),
            mapOf("name" to "Account", "articles" to listOf(mapOf("_id" to "3", "title" to "Resetting your password", "description" to "Forgot it? Reset it.")))
        )
        val card = make(customizations = mapOf("enableKnowledgeBase" to true), categories = categories).card
        card.open("quiz")
        assertThat(card.s.status).isEqualTo("Three questions - some about our help centre")
        assertThat(card.choice.tag).isEqualTo("From our help centre")
        val off = make(categories = categories).card
        off.open("quiz")
        assertThat(off.choice.tag).isNull()
    }

    @Test
    fun `emoji riddles - the picture, four options, all right is a win`() {
        val card = make().card
        card.open("emoji")
        val b = card.choice
        assertThat(b.big).isNotEmpty()
        assertThat(b.q).isEqualTo("What's the phrase?")
        assertThat(b.options).hasSize(4)
        answerAll(card, true)
        assertThat(card.s.result).isEqualTo("win")
        assertThat(card.s.status).endsWith("3 of 3 riddles solved. Perfect score! 🏆")
    }

    @Test
    fun `reflex - wait for green, tap - too soon - a lucky guess - three rounds and the average`() {
        // rng 0.9: green after 1200 + 1980 = 3180ms.
        val m = make()
        val card = m.card
        card.open("reaction")
        assertThat(card.pad.state).isEqualTo("idle")
        assertThat(card.pad.line).isEqualTo("Tap to start")
        card.input(GameInput.Reaction)
        assertThat(card.pad.state).isEqualTo("wait")
        assertThat(card.pad.big).isEqualTo("👀")
        card.input(GameInput.Reaction)
        assertThat(card.pad.state).isEqualTo("between")
        assertThat(card.pad.line).isEqualTo("Too soon! Tap to try again")
        assertThat(card.s.status).isEqualTo("Too soon - wait for green")
        card.input(GameInput.Reaction)
        m.k.tick(3180)
        assertThat(card.pad.state).isEqualTo("go")
        assertThat(card.pad.line).isEqualTo("Tap now!")
        m.k.tick(50)
        card.input(GameInput.Reaction)
        assertThat(card.pad.line).isEqualTo("A lucky guess! Tap to try again")
        for (ms in listOf(200L, 300L, 400L)) {
            card.input(GameInput.Reaction)
            m.k.tick(3180)
            m.k.tick(ms)
            card.input(GameInput.Reaction)
        }
        assertThat(card.s.result).isEqualTo("win")
        assertThat(card.s.status).isEqualTo("Sharp reflexes 🎯 - 300 ms on average.")
        assertThat(card.s.meta).isEqualTo("200 · 300 · 400 ms")
        assertThat(card.pad.state).isEqualTo("done")
        assertThat(card.pad.line).isEqualTo("300 ms average")
    }

    @Test
    fun `reflex - a teammate joining mid-round - the round is reset, Tap to carry on`() {
        val m = make()
        m.card.open("reaction")
        m.card.input(GameInput.Reaction)
        m.card.wrapUp("")
        assertThat(m.card.s.title).isEqualTo("Someone's here!")
        m.k.tick(10_000)
        m.card.press("resume")
        assertThat(m.card.pad).isEqualTo(Board.Reaction("between", "⏸️", "Tap to carry on"))
    }

    @Test
    fun `word scramble - tap letters in order - a wrong word shakes - tap a slot to take it back - solved moves on`() {
        val m = make()
        val card = m.card
        card.open("scramble")
        val b0 = card.scr
        assertThat(card.s.meta).isEqualTo("Word 1 of 3 · 0 solved")
        assertThat(b0.skipLabel).isEqualTo("Skip")
        // Wrong: the tiles as they lie (never the word itself).
        b0.letters.indices.forEach { card.input(GameInput.Tile(it)) }
        assertThat(card.scr.nope).isEqualTo(1)
        assertThat(card.s.status).isEqualTo("Not quite - tap letters to take them back")
        card.input(GameInput.Slot(0))
        assertThat(card.scr.placed).isEmpty()
        card.input(GameInput.Skip)
        val b = card.scr
        assertThat(b.revealed).isTrue()
        val revealed = b.placed.joinToString("") { b.letters[it] }
        assertThat(card.s.status).isEqualTo("It was $revealed.")
        m.k.tick(1500)
        assertThat(card.s.meta).isEqualTo("Word 2 of 3 · 0 solved")
        assertThat(card.scr.placed).isEmpty()
    }

    @Test
    fun `word scramble - three words solved - Word wizard`() {
        val m = make()
        val card = m.card
        card.open("scramble")
        val words = mutableListOf<String>()
        for (r in 0 until 3) {
            val b = card.scr
            val target = GamesLogic.WORDS.first { it.hint == b.hint }.w
            assertThat(b.letters.sorted()).isEqualTo(target.map { it.toString() }.sorted())
            words += target
            target.forEach { card.input(GameInput.Key(it.lowercaseChar().toString())) }
            if (r < 2) {
                assertThat(card.s.status).isEqualTo("$target - got it! 🎉")
                m.k.tick(1100)
            }
        }
        assertThat(card.s.result).isEqualTo("win")
        assertThat(card.s.status).isEqualTo("${words[2]} - got it! 🎉 You solved 3 of 3. Word wizard! 🧙")
    }

    @Test
    fun `Play again replays the same game - Another game goes back to the menu`() {
        val card = make().card
        card.open("memory")
        card.press("another")
        assertThat(card.s.view).isEqualTo("menu")
        assertThat(card.s.title).isEqualTo("Pick a game")
        card.open("rps")
        card.press("again")
        assertThat(card.s.view).isEqualTo("game")
        assertThat(card.s.game).isEqualTo("rps")
        assertThat(card.s.meta).isEqualTo("You 0 · 0 Bot")
    }

    @Test
    fun `a teammate joins - the menu leaves - a finished game stays as a result, with no buttons and no x`() {
        val a = make()
        a.card.wrapUp("Sam")
        assertThat(a.card.s.view).isEqualTo("gone")

        val b = make(reducedMotion = true)
        b.card.open("rps")
        b.card.input(GameInput.Rps(Hand.ROCK))
        b.card.input(GameInput.Rps(Hand.ROCK))
        assertThat(b.card.s.result).isEqualTo("win")
        b.card.wrapUp("Sam")
        assertThat(b.card.s.view).isEqualTo("game")
        assertThat(b.card.s.settled).isTrue()
        assertThat(b.card.s.closable).isFalse()
        assertThat(b.card.s.foot).isEmpty()
    }

    @Test
    fun `the menu on a paused card is back, with its x`() {
        val card = make().card
        card.open("rps")
        card.wrapUp("Sam")
        card.menu()
        assertThat(card.s.view).isEqualTo("menu")
        assertThat(card.s.closable).isTrue()
        assertThat(card.s.chips).hasSize(7)
    }

    @Test
    fun `removed - its timers stop and taps do nothing`() {
        val m = make()
        m.card.open("ttt")
        m.card.input(GameInput.Ttt(0))
        m.card.remove()
        assertThat(m.k.size).isEqualTo(0)
        m.card.input(GameInput.Ttt(1))
        assertThat(m.card.s.view).isEqualTo("gone")
    }

    @Test
    fun `every change reaches the subscribers`() {
        val card = make().card
        val seen = mutableListOf<String>()
        val off = card.subscribe { seen += it.view }
        card.open("quiz")
        card.press("another")
        off()
        card.open("rps")
        assertThat(seen).containsExactly("game", "menu").inOrder()
    }
}
