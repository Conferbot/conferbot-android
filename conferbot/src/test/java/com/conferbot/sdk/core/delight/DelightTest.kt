package com.conferbot.sdk.core.delight

import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.livechat.SignalTimer
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The conversation's human touches (core/delight/Delight.kt): the cases of
 * the React Native SDK's __tests__/core/delight.test.ts (web
 * ts/chat/delight/rules.ts, index.ts, stallNudge.ts, celebrate.ts, ratingFx.ts).
 */
class DelightTest {

    @After
    fun tearDown() { I18n.setWidgetLanguage("en", emptyList()) }

    @Test
    fun `on unless the owner set it to false`() {
        assertThat(Delight.touchOn(emptyMap(), Delight.CELEBRATIONS)).isTrue()
        assertThat(Delight.touchOn(null, Delight.BOT_REACTIONS)).isTrue()
        assertThat(Delight.touchOn(mapOf("celebrations" to false), Delight.CELEBRATIONS)).isFalse()
        assertThat(Delight.touchOn(mapOf("stallNudges" to 0), Delight.STALL_NUDGES)).isTrue()
    }

    @Test
    fun `questions can be changed while only safe steps ran since`() {
        assertThat(Delight.canChangeAnswer("ask-email-node", emptyList())).isTrue()
        assertThat(Delight.canChangeAnswer("ask-email-node", listOf("message-node", "condition-node", "delay-node", "ask-name-node"))).isTrue()
        assertThat(Delight.canChangeAnswer("ask-email-node", listOf("calendar-node"))).isFalse()
        assertThat(Delight.canChangeAnswer("ask-email-node", listOf("webhook-node"))).isFalse()
        assertThat(Delight.canChangeAnswer("ask-email-node", listOf("agent"))).isFalse()
        assertThat(Delight.canChangeAnswer("calendar-node", emptyList())).isFalse()
        assertThat(Delight.isSafeToUndo("welcome-node")).isFalse()
        assertThat(Delight.waitsForAnswer("payment-node")).isTrue()
        assertThat(Delight.waitsForAnswer("message-node")).isFalse()
    }

    @Test
    fun `Edit only on the latest answer, when the bot is quiet and nothing locks the chat`() {
        fun show(c: Map<String, Any?> = emptyMap(), locked: Boolean = false, busy: Boolean = false, latest: Boolean = true) =
            Delight.showChangeFor(c, locked, busy, latest, "ask-name-node", emptyList())
        assertThat(show()).isTrue()
        assertThat(show(c = mapOf("allowChangeAnswer" to false))).isFalse()
        assertThat(show(locked = true)).isFalse()
        assertThat(show(busy = true)).isFalse()
        assertThat(show(latest = false)).isFalse()
    }

    @Test
    fun `a score read from the answer text`() {
        assertThat(Delight.scoreShare("★★★★ Good")).isWithin(1e-9).of(0.8)
        assertThat(Delight.scoreShare("⭐⭐⭐⭐⭐")).isEqualTo(1.0)
        assertThat(Delight.scoreShare("8/10")).isWithin(1e-9).of(0.8)
        assertThat(Delight.scoreShare("7 / 10")).isWithin(1e-9).of(0.7)
        assertThat(Delight.scoreShare("hello")).isNull()
        assertThat(Delight.scoreText("rating-choice-node", 4.0, 5.0)).isEqualTo("4/5")
        assertThat(Delight.scoreText("ask-name-node", 4.0, 5.0)).isEmpty()
    }

    @Test
    fun `reacts as a person would - a wave, a party, a heart, a thumbs up, a thank-you`() {
        assertThat(Delight.reactionFor("ask-name-node", "Jamie")).isEqualTo(Delight.WAVE)
        assertThat(Delight.reactionFor("calendar-node", "Tue 10:00")).isEqualTo(Delight.PARTY)
        assertThat(Delight.reactionFor("payment-node", "Paid")).isEqualTo(Delight.PARTY)
        assertThat(Delight.reactionFor("rating-choice-node", "5/5")).isEqualTo(Delight.HEART)
        assertThat(Delight.reactionFor("opinion-scale-choice-node", "6/10")).isEqualTo(Delight.THUMBS_UP)
        assertThat(Delight.reactionFor("user-rating-node", "2/5")).isEqualTo(Delight.THANKS)
        assertThat(Delight.reactionFor("rating-choice-node", "great")).isNull()
        assertThat(Delight.reactionFor("ask-email-node", "a@b.co")).isNull()
        assertThat(Delight.reactionLabel(Delight.WAVE)).isEqualTo("The bot waved")
        assertThat(Delight.reactionLabel("🦄")).isEqualTo("The bot reacted")
    }

    @Test
    fun `celebrates a booking, a payment, a perfect score - not the rating card, which sparks`() {
        assertThat(Delight.celebrates("calendar-node", "")).isTrue()
        assertThat(Delight.celebrates("payment-node", "")).isTrue()
        assertThat(Delight.celebrates("opinion-scale-choice-node", "10/10")).isTrue()
        assertThat(Delight.celebrates("opinion-scale-choice-node", "9/10")).isFalse()
        assertThat(Delight.celebrates("rating-choice-node", "5/5")).isFalse()
        assertThat(Delight.celebrates("ask-email-node", "")).isFalse()
    }

    @Test
    fun `follows the owner's switches and reduced motion`() {
        assertThat(Delight.touchesFor(emptyMap(), "calendar-node", "", false)).isEqualTo(Delight.Touches(Delight.PARTY, true))
        assertThat(Delight.touchesFor(emptyMap(), "calendar-node", "", true)).isEqualTo(Delight.Touches(Delight.PARTY, false))
        assertThat(Delight.touchesFor(mapOf("celebrations" to false), "calendar-node", "", false)).isEqualTo(Delight.Touches(Delight.PARTY, false))
        assertThat(Delight.touchesFor(mapOf("botReactions" to false), "ask-name-node", "Jo", false)).isEqualTo(Delight.Touches(null, false))
    }

    @Test
    fun `the web's confetti fan and rating sparks`() {
        val pieces = Delight.confettiPieces { 0.5 }
        assertThat(pieces).hasSize(Delight.CONFETTI_PIECES)
        // The first piece at -160 degrees (no jitter at 0.5), 86 px out.
        val a = -160 * PI / 180
        assertThat(pieces[0]).isEqualTo(Delight.ConfettiPiece((cos(a) * 86).roundToInt(), (sin(a) * 86).roundToInt(), 41, 0, 45, 0, true))
        // The fan opens up: every piece flies upward.
        pieces.forEach { assertThat(it.dy).isLessThan(0) }
        assertThat(pieces.map { it.tone }.take(5)).containsExactly(0, 1, 2, 3, 0).inOrder()
        assertThat(Delight.ratingSparks(false)).containsExactly(20.0, 65.0, 110.0, 155.0, 200.0, 245.0, 290.0, 335.0).inOrder()
        assertThat(Delight.ratingSparks(true)).hasSize(12)
    }

    @Test
    fun `the nudge worded for typing or picking, in the visitor's language`() {
        assertThat(Delight.stallHintFor("ask-email-node")).isEqualTo("No rush - type your answer below whenever you're ready.")
        assertThat(Delight.stallHintFor("n-choices-node")).isEqualTo("No rush - pick whichever fits when you're ready.")
        I18n.setWidgetLanguage("de", emptyList())
        assertThat(Delight.stallHintFor("n-choices-node")).doesNotContain("No rush")
        assertThat(Delight.nudgesWhileWaiting("calendar-node")).isTrue()
        assertThat(Delight.nudgesWhileWaiting("ask-file-node")).isFalse()
    }

    // ---- The stall watch ----

    private class Harness(val visible: () -> Boolean = { true }, val composer: () -> String = { "" }) : SignalTimer {
        var now = 0L
        private var seq = 0
        private val timers = mutableListOf<Triple<Long, Int, () -> Unit>>()
        val shown = mutableListOf<String>()
        var hidden = 0
        val watch = Delight.StallWatch(visible, composer, { shown.add(it) }, { hidden++ }, this, { now })
        override fun set(ms: Long, fn: () -> Unit): Any { val id = ++seq; timers.add(Triple(now + ms, id, fn)); return id }
        override fun clear(handle: Any) { timers.removeAll { it.second == handle } }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val next = timers.filter { it.first <= end }.minByOrNull { it.first } ?: break
                timers.remove(next)
                now = next.first
                next.third()
            }
            now = end
        }
    }

    @Test
    fun `nudges once, 40 s into a question nobody touched`() {
        val h = Harness()
        h.advance(Delight.STALL_QUIET_MS) // quiet since it started
        h.watch.arm("n-choices-node")
        h.advance(Delight.STALL_AFTER_MS - 1)
        assertThat(h.shown).isEmpty()
        h.advance(1)
        assertThat(h.shown).containsExactly("No rush - pick whichever fits when you're ready.")
        h.watch.arm("n-choices-node")
        h.advance(Delight.STALL_AFTER_MS * 2)
        assertThat(h.shown).hasSize(1)
        h.watch.disarm()
        assertThat(h.hidden).isEqualTo(1)
    }

    @Test
    fun `waits while the visitor is busy, away or typing`() {
        var visible = false
        var typed = "hel"
        val h = Harness({ visible }, { typed })
        h.advance(Delight.STALL_QUIET_MS)
        h.watch.arm("ask-name-node")
        h.advance(Delight.STALL_AFTER_MS)
        assertThat(h.shown).isEmpty()
        visible = true
        h.advance(Delight.STALL_QUIET_MS)
        assertThat(h.shown).isEmpty() // still typing
        typed = ""
        h.watch.touch()
        h.advance(Delight.STALL_QUIET_MS - 1)
        assertThat(h.shown).isEmpty()
        h.advance(Delight.STALL_QUIET_MS)
        assertThat(h.shown).hasSize(1)
    }

    @Test
    fun `goes quietly when answered before the nudge`() {
        val h = Harness()
        h.advance(Delight.STALL_QUIET_MS)
        h.watch.arm("ask-name-node")
        h.advance(1000)
        h.watch.disarm()
        h.advance(Delight.STALL_AFTER_MS)
        assertThat(h.shown).isEmpty()
        assertThat(h.hidden).isEqualTo(0)
    }

    @Test
    fun `the burst's lighter tone is the accent mixed with white`() {
        assertThat(Delight.mixWithWhite(0x0808EE, 0.45)).isEqualTo(0x9090F7)
    }
}
