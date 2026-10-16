package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.models.ConferBotConfig
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Post-chat redirect (core/livechat/PostChatRedirect.kt), the cases of the
 * web widget's tests/postChatRedirect.test.js, React Native's
 * __tests__/core/postChatRedirect.test.ts and
 * __tests__/context/usePostChatRedirect.test.ts and iOS's
 * PostChatRedirectTests.swift: which URLs are followed, the 10-second line
 * with Cancel, once per conversation (also after a restart), the app's hook
 * first, waiting for the rating, what stops it, and a "Continue to" button
 * when the system cannot open the URL. On a hand-moved clock.
 */
class PostChatRedirectTest {

    private class FakeTimer : SignalTimer {
        var clock = 0L
        private val due = mutableListOf<Triple<Long, Any, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any = Any().also { due += Triple(clock + ms, it, fn) }
        override fun clear(handle: Any) { due.removeAll { it.second === handle } }
        /** Moves the clock [ms], running what falls due in order (a timer set by one runs too when due). */
        fun tick(ms: Long) {
            val end = clock + ms
            while (true) {
                val next = due.filter { it.first <= end }.minByOrNull { it.first } ?: break
                due.remove(next)
                clock = next.first
                next.third()
            }
            clock = end
        }
    }

    private class MemoryStore : RedirectStore {
        val m = mutableMapOf<String, String>()
        override fun read(key: String) = m[key]
        override fun write(key: String, value: String) { m[key] = value }
    }

    /** A chat on screen. */
    private class Chat(val store: MemoryStore? = MemoryStore()) {
        val clock = FakeTimer()
        var session: String? = "s1"
        var visible = true
        var blocked = false
        var live = false
        var rating = false
        var disabled = false
        var foreground = true
        var url: Any? = "https://www.example.com/thanks"
        var hook: ((String) -> Boolean)? = null
        var refuse = false
        val went = mutableListOf<String>()
        val said = mutableListOf<String>()
        val r = PostChatRedirectController(
            PostChatRedirectController.Hooks(
                redirectUrl = { url },
                sessionId = { session },
                chatVisible = { visible },
                blocked = { blocked },
                live = { live },
                ratingPending = { rating },
                open = { href -> if (refuse) false else { went += href; true } },
                disabled = { disabled },
                onPostChatRedirect = { hook },
                store = { store },
                appInForeground = { foreground },
                announce = { said += it }
            ),
            clock
        )
        val line get() = r.line.value

        /** The conversation ended, and the rating's 1.5 s passed. */
        fun end() {
            r.conversationEnded()
            clock.tick(PostChatRedirect.SETTLE_MS)
        }
    }

    @Before
    fun setUp() { I18n.setWidgetLanguage("en", emptyList()) }

    @After
    fun tearDown() { I18n.setWidgetLanguage("en", emptyList()) }

    @Test
    fun `only plain http(s) addresses are followed`() {
        assertThat(PostChatRedirect.safeUrl("https://example.com/thanks")).isEqualTo("https://example.com/thanks")
        assertThat(PostChatRedirect.safeUrl("  http://example.com  ")).isEqualTo("http://example.com/")
        assertThat(PostChatRedirect.safeUrl("HTTPS://Example.com/a?b=1#c")).isEqualTo("https://example.com/a?b=1#c")
        val bad = listOf<Any?>(
            "javascript:alert(1)",
            " JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "mailto:a@b.co",
            "//example.com",
            "example.com",
            "ftp://example.com",
            "https://user:pass@example.com",
            "https://",
            "",
            null,
            42,
            "https://example.com/" + "a".repeat(3000),
        )
        for (b in bad) assertThat(PostChatRedirect.safeUrl(b)).isNull()
    }

    @Test
    fun `writes the address as a browser does`() {
        assertThat(PostChatRedirect.safeUrl("https://example.com?x=1")).isEqualTo("https://example.com/?x=1")
        assertThat(PostChatRedirect.safeUrl("https://example.com:443/a")).isEqualTo("https://example.com/a")
        assertThat(PostChatRedirect.safeUrl("http://example.com:8080/a")).isEqualTo("http://example.com:8080/a")
        assertThat(PostChatRedirect.safeUrl("http://example.com:080/a")).isEqualTo("http://example.com/a")
        assertThat(PostChatRedirect.safeUrl("https://[::1]/a")).isEqualTo("https://[::1]/a")
        assertThat(PostChatRedirect.safeUrl("https://example.com\\a b")).isEqualTo("https://example.com/a%20b")
        assertThat(PostChatRedirect.safeUrl("https://user@example.com")).isNull()
        assertThat(PostChatRedirect.safeUrl("https://exa mple.com")).isNull()
        assertThat(PostChatRedirect.safeUrl("https://example.com:99999")).isNull()
        assertThat(PostChatRedirect.safeUrl("https://example.com:abc")).isNull()
        assertThat(PostChatRedirect.safeUrl("https://.../")).isNull()
        // At most 2048 characters.
        val longest = "https://example.com/" + "a".repeat(2048 - 20)
        assertThat(PostChatRedirect.safeUrl(longest)).isEqualTo(longest)
        assertThat(PostChatRedirect.safeUrl(longest + "a")).isNull()
    }

    @Test
    fun `the line names the site without www`() {
        assertThat(PostChatRedirect.host("https://www.example.com/x")).isEqualTo("example.com")
        assertThat(PostChatRedirect.host("https://shop.example.co.uk/")).isEqualTo("shop.example.co.uk")
    }

    @Test
    fun `counts down 10 seconds, then opens the URL`() {
        val c = Chat()
        assertThat(c.r.start("s1")).isEqualTo(PostChatRedirectController.Start.NONE) // not ended yet
        c.end()
        assertThat(c.line?.text).isEqualTo(I18n.tFor("en", "redirect.countdown", mapOf("host" to "example.com", "n" to 10)))
        assertThat(c.line?.text).isEqualTo("Taking you to example.com in 10s")
        assertThat(c.line?.cancelText).isEqualTo("Cancel")
        assertThat(c.line?.cancelLabel).isEqualTo("Cancel going to example.com")
        c.clock.tick(3000)
        assertThat(c.line?.text).isEqualTo("Taking you to example.com in 7s")
        // Said once, not every second.
        assertThat(c.line?.said).isEqualTo("Taking you to example.com in 10s")
        assertThat(c.said).containsExactly("Taking you to example.com in 10s")
        c.clock.tick(6000)
        assertThat(c.went).isEmpty()
        c.clock.tick(1000)
        assertThat(c.went).containsExactly("https://www.example.com/thanks")
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.GOING)
        assertThat(c.line?.text).isEqualTo("Taking you to example.com…")
        c.clock.tick(5000)
        assertThat(c.went).hasSize(1)
    }

    @Test
    fun `Cancel stops it and takes the line away`() {
        val c = Chat()
        c.end()
        c.r.cancel()
        assertThat(c.line).isNull()
        c.clock.tick(20_000)
        assertThat(c.went).isEmpty()
    }

    @Test
    fun `once per conversation, also after a restart`() {
        val store = MemoryStore()
        var c = Chat(store)
        c.end()
        c.r.cancel()
        // The agent ends it again.
        c.end()
        assertThat(c.line).isNull()
        // The ended chat restored after a restart.
        c = Chat(store)
        assertThat(PostChatRedirect.alreadyOffered("s1", store)).isTrue()
        assertThat(store.m[PostChatRedirect.DONE_KEY_PREFIX + "s1"]).isEqualTo("1")
        c.end()
        assertThat(c.line).isNull()
        // A new conversation gets its own.
        c.session = "s2"
        c.end()
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
    }

    @Test
    fun `without storage, once per conversation while the app runs`() {
        val c = Chat(store = null)
        c.end()
        c.r.cancel()
        assertThat(c.r.start("s1")).isEqualTo(PostChatRedirectController.Start.NONE)
        c.end()
        assertThat(c.line).isNull()
    }

    @Test
    fun `nothing for a bad URL, no URL, or the app turning it off`() {
        val setups = listOf<(Chat) -> Unit>(
            { it.url = "javascript:alert(1)" },
            { it.url = "" },
            { it.url = null },
            { it.disabled = true },
        )
        for (setup in setups) {
            val c = Chat()
            setup(c)
            c.end()
            assertThat(c.line).isNull()
            assertThat(c.store!!.m).isEmpty()
        }
    }

    @Test
    fun `the app's hook gets the URL first`() {
        val got = mutableListOf<String>()
        val c = Chat()
        c.hook = { got += it; true }
        c.end()
        assertThat(got).containsExactly("https://www.example.com/thanks")
        assertThat(c.line).isNull()
        c.clock.tick(20_000)
        assertThat(c.went).isEmpty()
        // Remembered: not offered again.
        c.end()
        assertThat(got).hasSize(1)
        // false, or a throw: the SDK counts down.
        for (hook in listOf<(String) -> Boolean>({ false }, { throw IllegalStateException("x") })) {
            val d = Chat()
            d.hook = hook
            d.end()
            assertThat(d.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
        }
    }

    @Test
    fun `waits for the rating to be sent or put away`() {
        val c = Chat()
        c.rating = true
        c.end()
        c.clock.tick(30_000)
        assertThat(c.line).isNull()
        c.rating = false
        c.r.update()
        c.clock.tick(PostChatRedirect.SETTLE_MS - 1)
        assertThat(c.line).isNull()
        c.clock.tick(1)
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
    }

    @Test
    fun `the rating coming up within the settle waits for it`() {
        val c = Chat()
        c.r.conversationEnded()
        c.clock.tick(500)
        c.rating = true
        c.r.update()
        c.clock.tick(5000)
        assertThat(c.line).isNull()
        c.rating = false
        c.r.update()
        c.clock.tick(PostChatRedirect.SETTLE_MS)
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
    }

    @Test
    fun `a closed chat, a new conversation, a block, a person or the background stops it`() {
        val changes = listOf<(Chat) -> Unit>(
            { it.visible = false },
            { it.session = "s2" },
            { it.blocked = true },
            { it.live = true },
            { it.foreground = false },
        )
        for (change in changes) {
            val c = Chat()
            c.end()
            c.clock.tick(2000)
            assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
            change(c)
            c.clock.tick(20_000)
            assertThat(c.line).isNull()
            assertThat(c.went).isEmpty()
        }
        // update() stops it at once (the SDK calls it on every change).
        val c = Chat()
        c.end()
        c.visible = false
        c.r.update()
        assertThat(c.line).isNull()
    }

    @Test
    fun `a chat that ended off screen does not count down when opened later`() {
        val c = Chat()
        c.visible = false
        c.end()
        c.visible = true
        c.r.update()
        c.clock.tick(20_000)
        assertThat(c.line).isNull()
        assertThat(c.store!!.m).isEmpty()
        // An agent ending it again, on screen: tried again.
        c.end()
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
    }

    @Test
    fun `no session, no end`() {
        val c = Chat()
        c.session = null
        c.end()
        assertThat(c.line).isNull()
    }

    @Test
    fun `a restart - the next conversation gets its own`() {
        val c = Chat()
        c.end()
        c.r.reset()
        assertThat(c.line).isNull()
        c.session = "s2"
        c.end()
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.COUNTING)
        assertThat(c.store!!.m).hasSize(2)
    }

    @Test
    fun `one at a time`() {
        val c = Chat()
        c.end()
        assertThat(c.r.activeSession).isEqualTo("s1")
        assertThat(c.r.start("s2")).isEqualTo(PostChatRedirectController.Start.NONE)
    }

    @Test
    fun `when the system cannot open it, a Continue button`() {
        val c = Chat()
        c.url = "https://example.com/next"
        c.refuse = true
        c.end()
        c.clock.tick(10_000)
        assertThat(c.line?.phase).isEqualTo(RedirectLine.Phase.CONTINUE)
        assertThat(c.line?.text).isEqualTo("Continue to example.com")
        assertThat(c.line?.href).isEqualTo("https://example.com/next")
        c.refuse = false
        assertThat(c.r.continueNow()).isTrue()
        assertThat(c.went).containsExactly("https://example.com/next")
        assertThat(c.line).isNull()
        // Nothing to continue any more.
        assertThat(c.r.continueNow()).isFalse()
    }

    @Test
    fun `translated in every widget language`() {
        val keys = listOf("redirect.countdown", "redirect.cancel", "redirect.going", "redirect.continue")
        val vars = mapOf("host" to "example.com", "n" to 10)
        for (lang in listOf("nl", "fr", "de", "es")) {
            for (key in keys) {
                val s = I18n.tFor(lang, key, vars)
                assertThat(s).isNotEqualTo(I18n.tFor("en", key, vars))
                assertThat(s).contains("example.com")
            }
            assertThat(I18n.tFor(lang, "redirect.countdown", mapOf("host" to "x.io", "n" to 7))).contains("7")
        }
    }

    @Test
    fun `config defaults`() {
        val config = ConferBotConfig()
        assertThat(config.onPostChatRedirect).isNull()
        assertThat(config.disablePostChatRedirect).isFalse()
        assertThat(ConferBotConfig(disablePostChatRedirect = true).disablePostChatRedirect).isTrue()
    }
}
