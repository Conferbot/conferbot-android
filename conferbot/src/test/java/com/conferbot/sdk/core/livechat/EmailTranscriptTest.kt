package com.conferbot.sdk.core.livechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Email me this chat (web emailTranscript.ts), as RN's __tests__/core/emailTranscript.test.ts. */
class EmailTranscriptTest {

    @Test
    fun `checks the address as the web does`() {
        assertThat(EmailTranscript.isEmail("ada@example.com")).isTrue()
        assertThat(EmailTranscript.isEmail("  ada@example.co ")).isTrue()
        assertThat(EmailTranscript.isEmail("ada@example.c")).isFalse()
        assertThat(EmailTranscript.isEmail("ada@example")).isFalse()
        assertThat(EmailTranscript.isEmail("a da@example.com")).isFalse()
        assertThat(EmailTranscript.isEmail("")).isFalse()
        assertThat(EmailTranscript.isEmail(null)).isFalse()
    }

    @Test
    fun `posts the session and the address to the mobile path, the bot is the header's`() {
        assertThat(EmailTranscript.PATH).isEqualTo("chat/transcript-email")
        assertThat(EmailTranscript.body("s-1", " ada@example.com ")).isEqualTo(mapOf("chatSessionId" to "s-1", "email" to "ada@example.com"))
    }

    @Test
    fun `reads the answer - sent only on success true`() {
        assertThat(EmailTranscript.result(200, mapOf("success" to true))).isEqualTo(EmailTranscript.Result(true))
        assertThat(EmailTranscript.result(200, mapOf("success" to false, "message" to "We couldn't email the transcript. Please try again later.")))
            .isEqualTo(EmailTranscript.Result(false, "We couldn't email the transcript. Please try again later."))
        assertThat(EmailTranscript.result(400, mapOf("success" to false, "message" to "Please enter a valid email address.")).message)
            .isEqualTo("Please enter a valid email address.")
        assertThat(EmailTranscript.result(429, mapOf("error" to true, "message" to "Too many requests. Please try again later.")))
            .isEqualTo(EmailTranscript.Result(false, "Too many requests. Please try again later."))
        assertThat(EmailTranscript.result(500, mapOf("success" to false))).isEqualTo(EmailTranscript.Result(false))
        assertThat(EmailTranscript.result(201, mapOf("success" to "yes"))).isEqualTo(EmailTranscript.Result(false))
        assertThat(EmailTranscript.result(200, null)).isEqualTo(EmailTranscript.Result(false))
    }

    @Test
    fun `says the server's reason, else ours`() {
        assertThat(EmailTranscript.errorText(EmailTranscript.Result(false, "Too many requests. Please try again later."))).isEqualTo("Too many requests. Please try again later.")
        assertThat(EmailTranscript.errorText(EmailTranscript.Result(false))).isEqualTo(EmailTranscript.SEND_FAILED)
        assertThat(EmailTranscript.errorText(null)).isEqualTo(EmailTranscript.SEND_FAILED)
        assertThat(EmailTranscript.sentText("ada@example.com")).isEqualTo("Sent! Check your inbox at ada@example.com")
    }
}
