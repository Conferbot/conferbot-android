package com.conferbot.sdk.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** "Helpful?" as the web's answerFeedback.ts, the cases of RN's __tests__/core/answerFeedback.test.ts. */
class AnswerFeedbackTest {

    private fun play(start: Helpful.State, vararg taps: Helpful.Tap): Pair<Helpful.State, List<AnswerFeedback>> {
        var s = start
        val sent = mutableListOf<AnswerFeedback>()
        taps.forEach { tap ->
            val r = Helpful.step(s, tap)
            r.send?.let { sent += it }
            s = r.state
        }
        return s to sent
    }

    @Test
    fun `sends a No at once, without waiting for a reason`() {
        val r = Helpful.step(Helpful.start(), Helpful.Tap.Vote(false))
        assertThat(r.send).isEqualTo(AnswerFeedback(false))
        assertThat(r.state.view).isEqualTo(Helpful.View.DOWN)
    }

    @Test
    fun `a reason, then a comment, each go as they are given`() {
        val (s, sent) = play(Helpful.start(), Helpful.Tap.Vote(false), Helpful.Tap.Reason("outdated"), Helpful.Tap.Comment("  the   price changed \n"))
        assertThat(sent).containsExactly(
            AnswerFeedback(false),
            AnswerFeedback(false, "outdated"),
            AnswerFeedback(false, "outdated", "the price changed")
        ).inOrder()
        assertThat(s.commented).isTrue()
        assertThat(Helpful.ratedLabel(s.current)).isEqualTo("Not helpful · Outdated")
    }

    @Test
    fun `a comment without a reason, and an empty one sends nothing`() {
        assertThat(play(Helpful.start(), Helpful.Tap.Vote(false), Helpful.Tap.Comment("meh")).second[1]).isEqualTo(AnswerFeedback(false, comment = "meh"))
        val (s, sent) = play(Helpful.start(), Helpful.Tap.Vote(false), Helpful.Tap.Comment("   "))
        assertThat(sent).containsExactly(AnswerFeedback(false))
        assertThat(s.commented).isTrue()
    }

    @Test
    fun `Yes thanks, then settles into the rated line`() {
        val (s, sent) = play(Helpful.start(), Helpful.Tap.Vote(true))
        assertThat(sent).containsExactly(AnswerFeedback(true))
        assertThat(s.view).isEqualTo(Helpful.View.THANKS)
        assertThat(Helpful.step(s, Helpful.Tap.Settle).state.view).isEqualTo(Helpful.View.RATED)
    }

    @Test
    fun `the vote can change, the same thumb again sends nothing`() {
        val start = Helpful.start(mapOf("helpful" to true))
        assertThat(start.view).isEqualTo(Helpful.View.RATED)
        assertThat(Helpful.step(start, Helpful.Tap.Vote(true)).send).isNull()
        val (s, sent) = play(start, Helpful.Tap.Vote(false))
        assertThat(sent).containsExactly(AnswerFeedback(false))
        assertThat(s.view).isEqualTo(Helpful.View.DOWN)
        // A settle after switching to No does not hide the reasons.
        assertThat(Helpful.step(s, Helpful.Tap.Settle).state.view).isEqualTo(Helpful.View.DOWN)
    }

    @Test
    fun `Talk to a person asks in the visitor's words, Ask another way focuses the composer`() {
        val down = play(Helpful.start(), Helpful.Tap.Vote(false)).first
        val human = Helpful.step(down, Helpful.Tap.Human)
        assertThat(human.ask).isEqualTo(Helpful.TALK_TO_PERSON_ASK)
        assertThat(human.state.view).isEqualTo(Helpful.View.RATED)
        val retry = Helpful.step(down, Helpful.Tap.Retry)
        assertThat(retry.focus).isTrue()
        assertThat(retry.state.view).isEqualTo(Helpful.View.RATED)
    }

    @Test
    fun `reads a stored vote as the web does`() {
        assertThat(Helpful.normalize(null)).isNull()
        assertThat(Helpful.normalize(mapOf("helpful" to "no"))).isNull()
        assertThat(Helpful.normalize(mapOf("helpful" to true, "reason" to "wrong", "comment" to "x"))).isEqualTo(AnswerFeedback(true))
        assertThat(Helpful.normalize(mapOf("helpful" to false, "reason" to "bogus", "comment" to " ok "))).isEqualTo(AnswerFeedback(false, comment = "ok"))
        assertThat(Helpful.normalize(mapOf("helpful" to false, "comment" to "a".repeat(600)))!!.comment).hasLength(500)
        assertThat(Helpful.start(AnswerFeedback(false, "wrong")).current).isEqualTo(AnswerFeedback(false, "wrong"))
    }

    @Test
    fun `the vote as the record entry keeps it, and as it is sent`() {
        assertThat(Helpful.entryFeedback(AnswerFeedback(false, "wrong"), "2026-10-16T10:00:00.000Z"))
            .isEqualTo(mapOf("helpful" to false, "reason" to "wrong", "at" to "2026-10-16T10:00:00.000Z"))
        assertThat(Helpful.payload("s1", "a1", AnswerFeedback(false, comment = "hm")))
            .isEqualTo(mapOf("chatSessionId" to "s1", "agentMessageId" to "a1", "helpful" to false, "comment" to "hm"))
    }

    @Test
    fun `only the newest answer asks, a rated one keeps its line`() {
        assertThat(Helpful.showsRow("a2", rated = false, newestRateableId = "a2")).isTrue()
        assertThat(Helpful.showsRow("a1", rated = false, newestRateableId = "a2")).isFalse()
        assertThat(Helpful.showsRow("a1", rated = true, newestRateableId = "a2")).isTrue()
    }
}
