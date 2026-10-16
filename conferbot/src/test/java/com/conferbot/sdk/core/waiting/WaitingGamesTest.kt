package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.livechat.HandoverWaitingTest
import com.conferbot.sdk.core.waiting.WaitingGames.DEFAULT_OFFER_TEXT
import com.conferbot.sdk.core.waiting.WaitingGames.OFFER_DELAY_MS
import com.conferbot.sdk.core.waiting.WaitingGames.OFFER_RETRY_MS
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The waiting games' settings and the offer's timing
 * (core/waiting/WaitingGames.kt), the cases of the React Native SDK's
 * __tests__/core/waitingGames.test.ts (web games/settings.ts, wire.ts), on a
 * fake clock.
 */
class WaitingGamesTest {

    private class FakeCard : OfferCard {
        var on = true
        var game = false
        var menus = 0
        var removed = 0
        val wrapped = mutableListOf<String>()
        override fun up() = on
        override fun playing() = game
        override fun menu() { menus++ }
        override fun remove() { on = false; removed++ }
        override fun wrapUp(name: String) { wrapped += name }
    }

    private class Setup(enabled: Boolean = true, failFirst: Int = 0) {
        val k = HandoverWaitingTest.FakeClock()
        val mounts = mutableListOf<String>()
        var last = FakeCard()
        private var fails = failFirst
        val offer = GamesOffer(
            enabled = { enabled },
            mount = { mode ->
                mounts += mode
                if (fails > 0) {
                    fails--
                    null
                } else FakeCard().also { last = it }
            },
            timer = k
        )
    }

    @Test
    fun `unset is on with every game and the default line`() {
        assertThat(WaitingGames.settingsOf(emptyMap())).isEqualTo(WaitingGames.Settings(true, WaitingGames.GAME_IDS, DEFAULT_OFFER_TEXT))
        assertThat(WaitingGames.GAME_IDS).hasSize(7)
    }

    @Test
    fun `off, a subset, unknown games dropped, no games is off, the line cleaned`() {
        assertThat(WaitingGames.settingsOf(mapOf("waitingGames" to mapOf("enabled" to false))).enabled).isFalse()
        assertThat(WaitingGames.settingsOf(mapOf("waitingGames" to mapOf("games" to listOf("ttt", "nope", "ttt", "rps")))).games).containsExactly("ttt", "rps").inOrder()
        assertThat(WaitingGames.settingsOf(mapOf("waitingGames" to mapOf("games" to emptyList<String>()))).enabled).isFalse()
        assertThat(WaitingGames.settingsOf(mapOf("waitingGames" to mapOf("offerText" to "  Bored?   Play!  "))).offerText).isEqualTo("Bored? Play!")
        assertThat(WaitingGames.settingsOf(mapOf("waitingGames" to mapOf("offerText" to "x".repeat(300)))).offerText).hasLength(WaitingGames.OFFER_TEXT_MAX)
    }

    @Test
    fun `the emoji riddles only in English`() {
        assertThat(WaitingGames.gamesForLanguage(WaitingGames.GAMES, "en").map { it.id }).contains("emoji")
        assertThat(WaitingGames.gamesForLanguage(WaitingGames.GAMES, "de").map { it.id }).doesNotContain("emoji")
    }

    @Test
    fun `typing play or games opens the menu, a real message does not`() {
        listOf("play", "Play!", "games", "game", "let's play", "lets play", "play a game", "I want to play").forEach { assertThat(WaitingGames.isPlay(it)).isTrue() }
        listOf("how do I play the video", "my game crashed", "playlist", "").forEach { assertThat(WaitingGames.isPlay(it)).isFalse() }
    }

    @Test
    fun `the accent - the header colour unless too pale, then the bot bubble, then Conferbot blue`() {
        assertThat(WaitingGames.pickAccent(mapOf("headerBgColor" to "#123456"))).isEqualTo("#123456")
        assertThat(WaitingGames.pickAccent(mapOf("headerBgColor" to "#ffe4ec", "botMsgColor" to "#e65100"))).isEqualTo("#e65100")
        assertThat(WaitingGames.pickAccent(mapOf("headerBgColor" to "#ffffff", "botMsgColor" to "red"))).isEqualTo("#0808EE")
        assertThat(WaitingGames.pickAccent(null)).isEqualTo("#0808EE")
    }

    @Test
    fun `offers once, OFFER_DELAY_MS after the waiting signal`() {
        val s = Setup()
        s.offer.waiting()
        s.k.tick(OFFER_DELAY_MS - 1)
        assertThat(s.mounts).isEmpty()
        s.k.tick(1)
        assertThat(s.mounts).containsExactly("offer")
        // A second signal in the same wait changes nothing.
        s.offer.waiting()
        s.k.tick(10_000)
        assertThat(s.mounts).containsExactly("offer")
    }

    @Test
    fun `a card that could not be drawn is tried three more times, 1,5s apart`() {
        val s = Setup(failFirst = 10)
        s.offer.waiting()
        s.k.tick(OFFER_DELAY_MS)
        s.k.tick(OFFER_RETRY_MS * 3)
        assertThat(s.mounts).hasSize(4)
        s.k.tick(60_000)
        assertThat(s.mounts).hasSize(4)
    }

    @Test
    fun `off - no offer, and play is sent as words`() {
        val s = Setup(enabled = false)
        s.offer.waiting()
        s.k.tick(20_000)
        assertThat(s.mounts).isEmpty()
        assertThat(s.offer.play("play")).isFalse()
    }

    @Test
    fun `a person joining before the offer - none comes`() {
        val s = Setup()
        s.offer.waiting()
        s.k.tick(1000)
        s.offer.joined("Sam")
        s.k.tick(10_000)
        assertThat(s.mounts).isEmpty()
        assertThat(s.k.size).isEqualTo(0)
    }

    @Test
    fun `a person joining - the card wraps up with their name`() {
        val s = Setup()
        s.offer.waiting()
        s.k.tick(OFFER_DELAY_MS)
        s.offer.joined("Sam")
        assertThat(s.last.wrapped).containsExactly("Sam")
        s.offer.joined(mapOf("odd" to true))
        assertThat(s.last.wrapped).containsExactly("Sam", "").inOrder()
    }

    @Test
    fun `the wait over - an unused card leaves, a game being played stays`() {
        val a = Setup()
        a.offer.waiting()
        a.k.tick(OFFER_DELAY_MS)
        a.offer.waitOver()
        assertThat(a.last.removed).isEqualTo(1)

        val b = Setup()
        b.offer.waiting()
        b.k.tick(OFFER_DELAY_MS)
        b.last.game = true
        b.offer.waitOver()
        assertThat(b.last.removed).isEqualTo(0)
    }

    @Test
    fun `the wait over before the offer - none comes`() {
        val s = Setup()
        s.offer.waiting()
        s.offer.waitOver()
        s.k.tick(10_000)
        assertThat(s.mounts).isEmpty()
    }

    @Test
    fun `Not now - no more offers this session, even in a later wait - typing play still opens the menu`() {
        val s = Setup()
        s.offer.waiting()
        s.k.tick(OFFER_DELAY_MS)
        s.offer.dismissed()
        s.offer.waitOver()
        s.offer.waiting()
        s.k.tick(10_000)
        assertThat(s.mounts).containsExactly("offer")
        assertThat(s.offer.play("let's play")).isTrue()
        assertThat(s.mounts).containsExactly("offer", "menu").inOrder()
        // A new conversation keeps a "Not now" (the web: this page view).
        s.offer.dismissed()
        s.offer.reset()
        s.offer.waiting()
        s.k.tick(10_000)
        assertThat(s.mounts).containsExactly("offer", "menu").inOrder()
    }

    @Test
    fun `typing play only while waiting - on a card already up it goes back to the menu, mid-game it does not`() {
        val s = Setup()
        assertThat(s.offer.play("play")).isFalse()
        s.offer.waiting()
        assertThat(s.offer.play("my game crashed")).isFalse()
        s.k.tick(OFFER_DELAY_MS)
        assertThat(s.offer.play("games")).isTrue()
        assertThat(s.last.menus).isEqualTo(1)
        s.last.game = true
        assertThat(s.offer.play("play")).isTrue()
        assertThat(s.last.menus).isEqualTo(1)
        assertThat(s.mounts).containsExactly("offer")
    }
}
