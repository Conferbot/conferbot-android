package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.I18n
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * The post-chat rating at the flow's end (#91; web index.ts: the end of a
 * path that is not a sub-flow's shows the rating 1.5 s later), and what the
 * post-chat redirect waits for ([LiveChatExtras.ratingPending]). On virtual
 * time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowEndRatingTest {

    @After
    fun tearDown() { I18n.setWidgetLanguage("en", emptyList()) }

    private fun TestScope.extras(rating: Boolean = true) =
        LiveChatExtras(scope = backgroundScope, socket = { mockk(relaxed = true) }, sessionId = { "s1" }, addMessage = {}).also {
            if (rating) it.setChatbotData(mapOf("csatConfig" to mapOf("enabled" to true, "ratingType" to "stars")))
        }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `the flow's end opens the rating 1_5 s later, and the redirect waits for it`() = runTest {
        val x = extras()
        assertThat(x.ratingPending).isFalse()
        x.flowEnded()
        // Due: the redirect already waits, the card is not up yet.
        assertThat(x.csatOpen.value).isFalse()
        assertThat(x.ratingPending).isTrue()
        wait(1499)
        assertThat(x.csatOpen.value).isFalse()
        wait(2)
        assertThat(x.csatOpen.value).isTrue()
        assertThat(x.csatDue.value).isFalse()
        assertThat(x.ratingPending).isTrue()
        // Sent: the thank-you stays, the redirect goes on.
        x.submitRating(5, "")
        assertThat(x.ratingPending).isFalse()
        // A second end in the same conversation asks nothing more.
        x.flowEnded()
        wait(5000)
        assertThat(x.csatDue.value).isFalse()
    }

    @Test
    fun `Not now puts it away, and the redirect goes on`() = runTest {
        val x = extras()
        x.flowEnded()
        wait(1500)
        x.dismissCsat()
        assertThat(x.ratingPending).isFalse()
    }

    @Test
    fun `no rating when the bot has none, or for a blocked visitor`() = runTest {
        val none = extras(rating = false)
        none.flowEnded()
        assertThat(none.ratingPending).isFalse()
        wait(5000)
        assertThat(none.csatOpen.value).isFalse()

        val blocked = extras()
        blocked.block(emptyMap())
        blocked.flowEnded()
        wait(5000)
        assertThat(blocked.csatOpen.value).isFalse()
        assertThat(blocked.ratingPending).isFalse()
    }

    @Test
    fun `a block or a new chat while it is due takes it away`() = runTest {
        val x = extras()
        x.flowEnded()
        x.block(emptyMap())
        wait(5000)
        assertThat(x.csatOpen.value).isFalse()
        assertThat(x.csatDue.value).isFalse()

        val y = extras()
        y.flowEnded()
        y.reset()
        y.setChatbotData(mapOf("csatConfig" to mapOf("enabled" to true)))
        wait(5000)
        assertThat(y.csatOpen.value).isFalse()
        assertThat(y.ratingPending).isFalse()
    }

    @Test
    fun `an agent's rating already up is not opened twice`() = runTest {
        val x = extras()
        x.flowEnded()
        wait(1500)
        assertThat(x.csatOpen.value).isTrue()
        x.flowEnded()
        assertThat(x.csatDue.value).isFalse()
    }
}
