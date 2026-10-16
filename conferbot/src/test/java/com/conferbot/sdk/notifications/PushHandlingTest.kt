package com.conferbot.sdk.notifications

import com.conferbot.sdk.core.Conferbot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Conferbot.handlePushNotification before the SDK is set up: no Firebase, no network. */
class PushHandlingTest {

    private val data = mapOf(
        "source" to "conferbot",
        "type" to "conferbot_message",
        "botId" to "bot-1",
        "chatSessionId" to "chat-1",
        "messageId" to "m-1",
        "senderType" to "bot",
        "senderName" to "Acme",
        "body" to "Hi"
    )

    @Test
    fun `another library's notification is not ours`() {
        assertThat(Conferbot.handlePushNotification(mapOf("title" to "Sale!"))).isFalse()
        assertThat(Conferbot.handlePushNotification(data + ("source" to "other"), open = false)).isFalse()
        assertThat(Conferbot.handlePushNotification(data + ("type" to "agent_joined"))).isFalse()
    }

    @Test
    fun `one that arrived counts unread, a tap before the SDK is ready is kept`() {
        val before = Conferbot.unreadCount.value
        assertThat(Conferbot.handlePushNotification(data, open = false)).isTrue()
        assertThat(Conferbot.unreadCount.value).isEqualTo(before + 1)
        assertThat(Conferbot.handlePushNotification(data)).isTrue()
        Conferbot.resetUnreadCount()
    }
}
