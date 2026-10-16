package com.conferbot.sdk.core.livechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Typing preview (core/livechat/TypingPreview.kt): RN's __tests__/core/typingPreview.test.ts. */
class TypingPreviewTest {

    private class Clock : SignalTimer {
        var now = 0L
        private var seq = 0
        private val timers = mutableListOf<Triple<Long, Int, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any { val id = ++seq; timers.add(Triple(now + ms, id, fn)); return id }
        override fun clear(handle: Any) { timers.removeAll { it.second == handle } }
        fun advance(ms: Long) {
            now += ms
            while (true) {
                val next = timers.filter { it.first <= now }.minByOrNull { it.first } ?: break
                timers.remove(next)
                next.third()
            }
        }
    }

    @Test
    fun `trims and caps a draft`() {
        assertThat(TypingPreview.cleanDraft("  hi  ")).isEqualTo("hi")
        assertThat(TypingPreview.cleanDraft("x".repeat(TypingPreview.MAX + 10))).hasLength(TypingPreview.MAX)
        assertThat(TypingPreview.cleanDraft(5)).isEmpty()
    }

    @Test
    fun `at most every 300 ms, the latest text trailing, and one empty draft to clear`() {
        val c = Clock()
        val sent = mutableListOf<String>()
        val s = TypingPreview.DraftSender({ sent.add(it) }, c, { c.now })
        s.update("h")
        s.update("he")
        s.update("hel")
        assertThat(sent).containsExactly("h")
        c.advance(299)
        assertThat(sent).containsExactly("h")
        c.advance(1)
        assertThat(sent).containsExactly("h", "hel").inOrder()
        s.update("hel ")
        c.advance(300)
        assertThat(sent).containsExactly("h", "hel").inOrder() // the same text after trimming: nothing new
        s.clear()
        s.clear()
        assertThat(sent).containsExactly("h", "hel", "").inOrder()
        s.update("   ")
        assertThat(sent).containsExactly("h", "hel", "").inOrder()
    }

    @Test
    fun `never previews a secret field`() {
        assertThat(TypingPreview.isSensitiveField(password = true)).isTrue()
        assertThat(TypingPreview.isSensitiveField(autofillHints = listOf("smsOTPCode"))).isTrue()
        assertThat(TypingPreview.isSensitiveField(autofillHints = listOf("creditCardNumber"))).isTrue()
        assertThat(TypingPreview.isSensitiveField(autofillHints = listOf("cc-number"))).isTrue()
        assertThat(TypingPreview.isSensitiveField(autofillHints = listOf("password"))).isTrue()
        assertThat(TypingPreview.isSensitiveField(autofillHints = listOf("emailAddress"))).isFalse()
        assertThat(TypingPreview.isSensitiveField()).isFalse()
    }

    @Test
    fun `on only with a person in the chat, unless the owner turned it off`() {
        assertThat(TypingPreview.on(emptyMap(), true)).isTrue()
        assertThat(TypingPreview.on(mapOf("visitorDraftPreview" to false), true)).isFalse()
        assertThat(TypingPreview.on(emptyMap(), false)).isFalse()
    }
}
