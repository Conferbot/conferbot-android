package com.conferbot.sdk.core.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GreetingTest {

    private val new = Greeting.Facts(returning = false, signedIn = false)
    private val now = java.time.Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    private fun rule(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        mapOf<String, Any?>("matchType" to "contains", "pattern" to "/") + pairs

    @Test
    fun `shows nothing when the greeting is off`() {
        assertThat(Greeting.decide(mapOf("chatIconCtaText" to "Hi"), new)).isNull()
        assertThat(Greeting.decide(null, new)).isNull()
    }

    @Test
    fun `shows the default message after 3s, opening the chat on a tap`() {
        val g = Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to " Hi there "), new)!!
        assertThat(g.text).isEqualTo("Hi there")
        assertThat(g.delay).isEqualTo(3)
        assertThat(g.click).isEqualTo("open")
        val capped = Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi", "greetingDelay" to 9999, "greetingClickAction" to "send"), new)!!
        assertThat(capped.delay).isEqualTo(3600)
        assertThat(capped.click).isEqualTo("send")
    }

    @Test
    fun `uses a rule for every page, never one for a given page`() {
        val c = mapOf(
            "chatIconCta" to true,
            "chatIconCtaText" to "Default",
            "greetingRules" to listOf(
                rule("pattern" to "/pricing", "text" to "Pricing help?"),
                mapOf("matchType" to "glob", "pattern" to "*", "text" to "Everywhere", "delay" to 1, "clickAction" to "send", "starters" to listOf("A", "B")),
            ),
        )
        val g = Greeting.decide(c, new)!!
        assertThat(g.text).isEqualTo("Everywhere")
        assertThat(g.delay).isEqualTo(1)
        assertThat(g.click).isEqualTo("send")
        assertThat(g.starters).containsExactly("A", "B").inOrder()
    }

    @Test
    fun `ranks rules by priority, and lets a show-nothing rule silence the card`() {
        val rules = listOf(rule("text" to "Low", "priority" to 10), rule("text" to "High", "priority" to 90))
        assertThat(Greeting.decide(mapOf("chatIconCta" to true, "greetingRules" to rules), new)!!.text).isEqualTo("High")
        val silent = rules + rule("text" to "", "action" to mapOf("type" to "none"), "signedIn" to "yes")
        assertThat(Greeting.decide(mapOf("chatIconCta" to true, "greetingRules" to silent), Greeting.Facts(returning = false, signedIn = true))).isNull()
        assertThat(Greeting.decide(mapOf("chatIconCta" to true, "greetingRules" to silent), new)!!.text).isEqualTo("High")
    }

    @Test
    fun `honours who and when, skips what only a browser knows`() {
        fun c(vararg extra: Pair<String, Any?>) = mapOf("chatIconCta" to true, "chatIconCtaText" to "Default", "greetingRules" to listOf(rule("text" to "Rule", *extra)))
        assertThat(Greeting.decide(c("visitors" to "returning"), new)!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c("visitors" to "returning"), Greeting.Facts(returning = true, signedIn = false))!!.text).isEqualTo("Rule")
        assertThat(Greeting.decide(c("endsAt" to "2026-10-01T00:00:00Z"), new.copy(now = now))!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c("startsAt" to "2026-10-01T00:00:00.000Z"), new.copy(now = now))!!.text).isEqualTo("Rule")
        assertThat(Greeting.decide(c("countries" to listOf("GB")), new)!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c("source" to mapOf("utmSource" to "ads")), new)!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c("patterns" to listOf("pricing")), new)!!.text).isEqualTo("Default")
        // Unset browser behaviour (null, 0, empty) does not block a rule.
        assertThat(Greeting.decide(c("behavior" to mapOf("minPagesThisVisit" to 0, "paths" to emptyList<String>(), "minSeconds" to null)), new)!!.text).isEqualTo("Rule")
    }

    @Test
    fun `carries the old proactive teaser over as the web does`() {
        val g = Greeting.decide(mapOf("proactivePromptEnabled" to true, "proactivePromptText" to "Need help?"), new)!!
        assertThat(g.text).isEqualTo("Need help?")
        assertThat(g.delay).isEqualTo(8)
        assertThat(g.click).isEqualTo("send")
        assertThat(Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi", "proactivePromptEnabled" to true, "proactivePromptText" to "Teaser"), new)!!.text).isEqualTo("Teaser")
        assertThat(Greeting.decide(mapOf("smartOnly" to true), new)).isNull()
    }

    @Test
    fun `follows the frequency - every launch with a rest after closing, once a session, once in N days`() {
        val t = 1_000_000_000_000L
        assertThat(Greeting.frequencyAllows(emptyMap(), Greeting.Seen(), t)).isTrue()
        assertThat(Greeting.frequencyAllows(emptyMap(), Greeting.Seen(closedAt = t - 10_000), t)).isFalse()
        assertThat(Greeting.frequencyAllows(emptyMap(), Greeting.Seen(closedAt = t - Greeting.PAGE_COOLDOWN_SECONDS * 1000L), t)).isTrue()
        assertThat(Greeting.frequencyAllows(mapOf("greetingFrequency" to "session"), Greeting.Seen(shownThisSession = true), t)).isFalse()
        val visitor = mapOf("greetingFrequency" to "visitor", "greetingFrequencyDays" to 2)
        assertThat(Greeting.frequencyAllows(visitor, Greeting.Seen(shownAt = t - day), t)).isFalse()
        assertThat(Greeting.frequencyAllows(visitor, Greeting.Seen(shownAt = t - 3 * day), t)).isTrue()
    }

    @Test
    fun `heads the card with the avatar, name and Online`() {
        assertThat(Greeting.head(mapOf("avatar" to "a.png", "logoText" to "Acme"))).isEqualTo(Greeting.Head("a.png", "Acme", true))
        assertThat(Greeting.head(mapOf("hideAvatar" to true, "avatar" to "a.png", "logo" to "l.png", "hideOnlineStatus" to true), "Bot"))
            .isEqualTo(Greeting.Head("l.png", "Bot", false))
        assertThat(Greeting.normalizeStyle("nope")).isEqualTo("imessage")
        assertThat(Greeting.normalizeStyle("sticky")).isEqualTo("sticky")
    }

    @Test
    fun `reads the smart rules as the web - visits, chatted, limits, and customers kept quiet`() {
        val smart = listOf(
            rule("id" to "sgcust", "text" to "", "signedIn" to "yes", "action" to mapOf("type" to "none"), "priority" to 95),
            rule("id" to "sgretn", "text" to "Welcome back!", "behavior" to mapOf("minVisits" to 2), "priority" to 24,
                "limits" to mapOf("maxShows" to 1, "windowDays" to 3, "quietDaysAfterDismiss" to 14)),
            rule("id" to "sgengd", "text" to "Finding what you need?", "behavior" to mapOf("minPagesThisVisit" to 3), "priority" to 22),
        )
        val c = mapOf("chatIconCta" to true, "chatIconCtaText" to "Default", "greetingRules" to smart)
        val returning = Greeting.Facts(returning = true, signedIn = false, visits = 2, now = now)
        assertThat(Greeting.decide(c, Greeting.Facts(returning = false, signedIn = false, visits = 1, now = now))!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c, returning)!!.text).isEqualTo("Welcome back!")
        assertThat(Greeting.decide(c, returning.copy(shows = mapOf("sgretn" to listOf(now - day))))!!.text).isEqualTo("Default")
        assertThat(Greeting.decide(c, returning.copy(dismissed = mapOf("sgretn" to now - 5 * day)))!!.text).isEqualTo("Default")
        // Signed-in customers: quiet, unless the owner has a rule of their own for them.
        assertThat(Greeting.decide(c, Greeting.Facts(returning = false, signedIn = true, now = now))).isNull()
        val own = c + ("greetingRules" to smart + rule("text" to "Hi customer", "signedIn" to "yes"))
        assertThat(Greeting.decide(own, Greeting.Facts(returning = false, signedIn = true, now = now))!!.text).isEqualTo("Hi customer")
    }

    @Test
    fun `dresses the card in the bot's colours`() {
        assertThat(Greeting.accent(mapOf("brandColor" to "#FFFFFF", "headerBgColor" to "#0F766E"))).isEqualTo("#0f766e")
        assertThat(Greeting.accent(mapOf("headerBgColor" to "#fef08a", "widgetIconBgColor" to "#fde68a"))).isEqualTo("#4f46e5")
        assertThat(Greeting.bubble(mapOf("widgetIconBgColor" to "#0F766E"))).isEqualTo(Greeting.Bubble(listOf("#0f766e"), "#0f766e", "#ffffff"))
        assertThat(Greeting.bubble(mapOf("widgetIconBgColor" to "#fde68a")).fg).isEqualTo("#111827")
        val g = Greeting.bubble(mapOf("widgetIconThemeType" to "Gradient", "widgetGradientBgOne" to "#7c3aed", "widgetGradientBgTwo" to "#0d9488"))
        assertThat(g.bg).containsExactly("#7c3aed", "#0d9488").inOrder()
        assertThat(g.tail).isEqualTo("#0d9488")
        assertThat(Greeting.font(mapOf("font" to "Arial, sans-serif"))).isNull()
        assertThat(Greeting.font(mapOf("font" to "Inter"))).isEqualTo("Inter")
    }

    /** The smart rules as embed-server serves them after the owner's (smartGreetings.js), the payload the emulator got. */
    private val smart = listOf(
        rule("id" to "sgchko", "priority" to 95, "pattern" to "", "patterns" to listOf("checkout", "/cart/"), "text" to "", "action" to mapOf("type" to "none")),
        rule("id" to "sgcust", "priority" to 95, "text" to "", "signedIn" to "yes", "action" to mapOf("type" to "none")),
        rule("id" to "sgpric", "priority" to 30, "pattern" to "", "patterns" to listOf("pricing"), "text" to "Comparing plans?", "action" to mapOf("type" to "open")),
        rule("id" to "sgads0", "priority" to 28, "text" to "Looking at our offer?", "source" to mapOf("ads" to "yes"), "action" to mapOf("type" to "open")),
        rule("id" to "sgretn", "priority" to 24, "text" to "Welcome back!", "behavior" to mapOf("minVisits" to 2), "action" to mapOf("type" to "open"), "limits" to mapOf("maxShows" to 1, "windowDays" to 3, "quietDaysAfterDismiss" to 14)),
        rule("id" to "sgengd", "priority" to 22, "text" to "Finding what you need?", "behavior" to mapOf("minPagesThisVisit" to 3), "action" to mapOf("type" to "open")),
    )

    @Test
    fun `with the served smart rules - the default, welcome back once, nothing for a signed-in visitor`() {
        val c = mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi, how can I help?", "greetingRules" to smart)
        assertThat(Greeting.decide(c, new)!!.text).isEqualTo("Hi, how can I help?")
        val back = Greeting.Facts(returning = true, signedIn = false, visits = 2, now = now)
        assertThat(Greeting.decide(c, back)!!.key).isEqualTo("sgretn")
        // Its limit (once in 3 days) spent: the default message again.
        assertThat(Greeting.decide(c, back.copy(shows = mapOf("sgretn" to listOf(now - day))))!!.text).isEqualTo("Hi, how can I help?")
        // Signed in (Conferbot.identify, or a user at initialize): the customers rule says nothing,
        // the default message included - why the emulator showed no card after "Identify Test User".
        assertThat(Greeting.decide(c, Greeting.Facts(returning = false, signedIn = true))).isNull()
        // The owner's own talking rule for the same visitor wins over it.
        val own = c + ("greetingRules" to listOf(rule("id" to "own001", "text" to "Hello again")) + smart)
        assertThat(Greeting.decide(own, Greeting.Facts(returning = false, signedIn = true))!!.text).isEqualTo("Hello again")
    }

    @Test
    fun `reads ISO dates without java time, which API 21 to 25 lack`() {
        // 2026-10-16T09:30:00Z
        val t = 1792143000000L
        assertThat(Greeting.instant("2026-10-16T09:30:00Z")).isEqualTo(t)
        assertThat(Greeting.instant("2026-10-16T09:30:00.250Z")).isEqualTo(t + 250)
        assertThat(Greeting.instant("2026-10-16T13:30:00+04:00")).isEqualTo(t)
        assertThat(Greeting.instant("2026-10-16T05:30:00-0400")).isEqualTo(t)
        assertThat(Greeting.instant("2026-10-16T09:30Z")).isEqualTo(t)
        assertThat(Greeting.instant("2026-10-16")).isEqualTo(1792108800000L)
        assertThat(Greeting.instant("not a date")).isNull()
        assertThat(Greeting.instant("")).isNull()
        assertThat(Greeting.instant(null)).isNull()
    }
}
