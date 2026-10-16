package com.conferbot.sdk.core.livechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The header's status (core/livechat/ReplyTime.kt): the cases of the React
 * Native SDK's __tests__/core/replyTime.test.ts (the web widget's
 * tests/replyTime.test.js), plus a person in the chat (headerPresence.ts).
 */
class ReplyTimeTest {

    private val tz = TimeZone.getTimeZone("Europe/London")
    private fun online(bucket: String?) = ReplyTimeInfo(mode = "auto", handover = true, state = "online", bucket = bucket, leaveMessage = true)
    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Date =
        Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, min) }.time
    private fun iso(d: Date) = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d)

    @Test
    fun `a bucket reads as an honest phrase`() {
        assertThat(ReplyTime.headerLine(online("few_minutes"))).isEqualTo("Typically replies in a few minutes")
        assertThat(ReplyTime.headerLine(online("day"))).isEqualTo("Typically replies within a day")
    }

    @Test
    fun `no data, an older server, or no handover - Online stays`() {
        assertThat(ReplyTime.headerLine(online(null))).isNull()
        assertThat(ReplyTime.headerLine(null)).isNull()
        assertThat(ReplyTime.headerLine(online("few_minutes").copy(handover = false))).isNull()
    }

    @Test
    fun `away - Online, and when the team is back in the visitor's time`() {
        val now = local(2026, 9, 3, 20)
        val backAt = iso(local(2026, 9, 5, 9))
        val rt = online("few_minutes").copy(state = "away", backAt = backAt)
        assertThat(ReplyTime.headerLine(rt, now = now, tz = tz)).isEqualTo("Online · team back Monday 9 AM")
        assertThat(ReplyTime.headerLine(rt.copy(backAt = null), now = now, tz = tz)).isEqualTo("Online · team replies by email")
        assertThat(ReplyTime.backWhen(iso(local(2026, 9, 4, 9, 30)), now, Locale.US, tz)).isEqualTo("tomorrow 9:30 AM")
        assertThat(ReplyTime.backWhen(iso(local(2026, 9, 3, 22)), now, Locale.US, tz)).isEqualTo("at 10 PM")
        assertThat(ReplyTime.backWhen(iso(local(2026, 9, 14, 9)), now, Locale.US, tz)).isEqualTo("Wed, Oct 14 9 AM")
    }

    @Test
    fun `offline - email only when leave-a-message is on`() {
        val rt = online("few_minutes").copy(state = "offline")
        assertThat(ReplyTime.headerLine(rt)).isEqualTo("Online · team replies by email")
        assertThat(ReplyTime.headerLine(rt.copy(leaveMessage = false))).isEqualTo("Online · team away right now")
    }

    @Test
    fun `the owner's fixed words, or nothing, and an AI agent bot shows none`() {
        assertThat(ReplyTime.headerLine(online(null).copy(mode = "fixed", text = "We reply within 2 hours"))).isEqualTo("We reply within 2 hours")
        assertThat(ReplyTime.headerLine(ReplyTimeInfo(mode = "hide", handover = true))).isNull()
        assertThat(ReplyTime.headerLine(online("few_minutes"), agent = true)).isNull()
    }

    @Test
    fun `the reply time before anyone joins, plain Online once a person is in the chat`() {
        val rt = online("few_minutes").copy(state = "offline")
        assertThat(ReplyTime.headerStatus(rt)).isEqualTo("Online · team replies by email")
        assertThat(ReplyTime.headerStatus(rt, personInChat = true)).isEqualTo("Online")
        assertThat(ReplyTime.headerStatus(null)).isEqualTo("Online")
        assertThat(ReplyTime.headerStatus(rt, hidden = true)).isNull()
        assertThat(ReplyTime.headerStatus(online("few_minutes"), agentBot = true)).isEqualTo("Online")
    }

    @Test
    fun `reads customizations replyTime as the server sends it`() {
        val rt = ReplyTime.of(mapOf("mode" to "auto", "handover" to true, "state" to "online", "bucket" to "few_minutes", "leaveMessage" to false))
        assertThat(rt).isEqualTo(ReplyTimeInfo(mode = "auto", handover = true, state = "online", bucket = "few_minutes", leaveMessage = false))
        assertThat(ReplyTime.of(null)).isNull()
        assertThat(ReplyTime.of("x")).isNull()
    }

    // The web's home card cases (tests/replyTime.test.js homeLine, homePeople), as RN's.

    @Test
    fun `the home card line for each case`() {
        val opts = Date()
        assertThat(ReplyTime.homeLine(online("few_minutes"), tz = tz)).isEqualTo(HomeLine("The team typically replies in a few minutes", ReplyTone.ONLINE))
        assertThat(ReplyTime.homeLine(online(null), tz = tz)).isNull()
        assertThat(ReplyTime.homeLine(null, now = opts, tz = tz)).isNull()
        val now = local(2026, 9, 3, 20)
        val backAt = iso(local(2026, 9, 5, 9))
        assertThat(ReplyTime.homeLine(online("few_minutes").copy(state = "away", backAt = backAt), now = now, tz = tz))
            .isEqualTo(HomeLine("We're away right now · back Monday 9 AM", ReplyTone.AWAY))
        assertThat(ReplyTime.homeLine(online("few_minutes").copy(state = "offline"), tz = tz))
            .isEqualTo(HomeLine("The team is offline · we'll reply by email", ReplyTone.AWAY))
        assertThat(ReplyTime.homeLine(online("few_minutes").copy(state = "offline", leaveMessage = false), tz = tz)!!.text).isEqualTo("The team is offline right now")
        assertThat(ReplyTime.homeLine(ReplyTimeInfo(mode = "hide", handover = true), tz = tz)).isEqualTo(HomeLine(hide = true))
        assertThat(ReplyTime.homeLine(online("few_minutes"), agent = true, tz = tz)).isEqualTo(HomeLine("Answers instantly · team replies in a few minutes", ReplyTone.ONLINE))
        assertThat(ReplyTime.homeLine(online("few_minutes").copy(state = "away", backAt = backAt), agent = true, now = now, tz = tz)!!.text)
            .isEqualTo("Answers instantly · team back Monday 9 AM")
        assertThat(ReplyTime.homeLine(ReplyTimeInfo(mode = "hide", handover = true), agent = true, tz = tz)).isNull()
    }

    @Test
    fun `the home card's promise - plain words, never a speed promise when nobody is there`() {
        val now = local(2026, 9, 8, 22)
        fun p(rt: ReplyTimeInfo?, agent: Boolean = false) = ReplyTime.homePeople(rt, agent = agent, now = now, tz = tz)
        val tomorrow9 = iso(local(2026, 9, 9, 9))
        assertThat(p(online("few_minutes"))).isEqualTo(ReplyLine("Usually replies in a few minutes", ReplyTone.ONLINE))
        assertThat(p(online("few_minutes").copy(mode = "fixed", text = "We reply within 2 hours"))).isEqualTo(ReplyLine("We reply within 2 hours", ReplyTone.ONLINE))
        assertThat(p(online(null))).isEqualTo(ReplyLine("Usually replies instantly", ReplyTone.ONLINE))
        assertThat(p(null)).isEqualTo(ReplyLine("Answers instantly, day or night", ReplyTone.ONLINE))
        assertThat(p(online("few_minutes").copy(handover = false))).isEqualTo(ReplyLine("Answers instantly, day or night", ReplyTone.ONLINE))
        assertThat(p(online("few_minutes").copy(state = "offline")).text).isEqualTo("Team replies by email")
        assertThat(p(online("few_minutes").copy(state = "offline", backAt = tomorrow9)).text).isEqualTo("Team back tomorrow 9 AM")
        assertThat(p(online("few_minutes").copy(state = "away", backAt = tomorrow9)).text).isEqualTo("Team back tomorrow 9 AM")
        assertThat(p(online("few_minutes").copy(state = "away", leaveMessage = false)).text).isEqualTo("Team away right now")
        assertThat(p(ReplyTimeInfo(mode = "hide", handover = true)).text).isEqualTo("")
        assertThat(p(null, agent = true)).isEqualTo(ReplyLine("Answers instantly, day or night", ReplyTone.ONLINE))
        assertThat(p(online("few_minutes").copy(state = "offline"), agent = true)).isEqualTo(ReplyLine("Answers instantly, day or night", ReplyTone.ONLINE))
        assertThat(p(ReplyTimeInfo(mode = "hide", handover = true), agent = true).text).isEqualTo("Answers instantly, day or night")
    }

    // ---- typicalWait (the wait card, web replyTime.ts typicalWait) ----

    @Test
    fun `the wait card says the routed team's usual wait first, else the workspace's`() {
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "auto", bucket = "few_hours"), mapOf("bucket" to "under_minute"))).isEqualTo("Typical wait: under a minute")
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "auto", bucket = "few_hours"), null)).isEqualTo("Typical wait: a few hours")
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "auto", bucket = "unknown", wait = "about 3 minutes"), null)).isEqualTo("Typical wait: about 3 minutes")
        assertThat(ReplyTime.typicalWait(null, mapOf("bucket" to "day"))).isEqualTo("Typical wait: up to a day")
    }

    @Test
    fun `the wait card says nothing when the owner chose Fixed or Hide, or there is no data`() {
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "hide", bucket = "few_minutes"), mapOf("bucket" to "few_minutes"))).isNull()
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "fixed", text = "Within a day", bucket = "few_minutes"), null)).isNull()
        assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "auto"), null)).isNull()
        assertThat(ReplyTime.typicalWait(null, null)).isNull()
    }

    @Test
    fun `the usual wait in the visitor's language`() {
        com.conferbot.sdk.core.i18n.I18n.setWidgetLanguage("de", emptyList())
        try {
            assertThat(ReplyTime.typicalWait(ReplyTimeInfo(mode = "auto", bucket = "few_minutes"))).doesNotContain("Typical")
        } finally {
            com.conferbot.sdk.core.i18n.I18n.setWidgetLanguage("en", emptyList())
        }
    }
}
