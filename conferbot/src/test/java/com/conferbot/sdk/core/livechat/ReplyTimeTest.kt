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
}
