package com.conferbot.sdk.core.livechat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The chat header's status under the bot name: "Online", the team's reply
 * time, or when the team is back. Ported from the React Native SDK's
 * src/core/replyTime.ts, itself from the web widget's src/ts/chat/replyTime.ts
 * (headerLine, backWhen, the English words of ts/i18n/en.ts reply.*) and
 * src/ts/chat/headerPresence.ts (a person in the chat puts it back to a plain
 * "Online").
 *
 * The server works the reply time out (embed-server liveChat/replyTimeService)
 * and sends it with the bot data as customizations.replyTime. The bot itself
 * is always Online; the words say when people answer. The header never
 * changes to the agent: it keeps the bot's name. Pure.
 */
data class ReplyTimeInfo(
    val mode: String? = null,
    val handover: Boolean = false,
    val state: String? = null,
    val bucket: String? = null,
    val phrase: String? = null,
    val text: String? = null,
    val backAt: String? = null,
    val leaveMessage: Boolean? = null
)

object ReplyTime {
    const val ONLINE = "Online"
    private const val SEP = " · "

    /** "The team typically replies <phrase>" */
    val PHRASES = mapOf(
        "under_minute" to "in under a minute",
        "few_minutes" to "in a few minutes",
        "under_15_minutes" to "in under 15 minutes",
        "under_hour" to "in under an hour",
        "few_hours" to "in a few hours",
        "day" to "within a day",
        "few_days" to "in a few days"
    )

    /** customizations.replyTime as the server sends it; null when absent. */
    fun of(raw: Any?): ReplyTimeInfo? {
        val m = raw as? Map<*, *> ?: return null
        fun s(k: String) = m[k] as? String
        return ReplyTimeInfo(
            mode = s("mode"),
            handover = m["handover"] == true,
            state = s("state"),
            bucket = s("bucket"),
            phrase = s("phrase"),
            text = s("text"),
            backAt = s("backAt"),
            leaveMessage = m["leaveMessage"] as? Boolean
        )
    }

    private fun parseIso(v: String): Date? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mmX")
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse(v)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * When the team is back, in the visitor's time: "at 9 AM" (today),
     * "tomorrow 9:30 AM", "Monday 9 AM", "Mon, Oct 12 9 AM".
     */
    fun backWhen(backAt: String?, now: Date = Date(), locale: Locale = Locale.US, tz: TimeZone = TimeZone.getDefault()): String {
        if (backAt.isNullOrBlank()) return ""
        val d = parseIso(backAt) ?: return ""
        val cal = Calendar.getInstance(tz, locale).apply { time = d }
        fun fmt(p: String) = SimpleDateFormat(p, locale).apply { timeZone = tz }.format(d)
        val time = if (cal.get(Calendar.MINUTE) != 0) fmt("h:mm a") else fmt("h a")
        fun dayStart(x: Date): Long = Calendar.getInstance(tz, locale).apply {
            this.time = x
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = Math.round((dayStart(d) - dayStart(now)) / 86_400_000.0)
        return when {
            days <= 0 -> "at $time"
            days == 1L -> "tomorrow $time"
            days < 7 -> "${fmt("EEEE")} $time"
            else -> "${fmt("EEE, MMM d")} $time"
        }
    }

    /**
     * The header's line from the server's reply time; null keeps "Online".
     * The bot stays Online when the team is away or offline ("Online · team
     * back at 9 AM", "Online · team replies by email"). [agent]: an AI agent
     * bot, which answers itself whatever the hour (no line).
     */
    fun headerLine(
        rt: ReplyTimeInfo?,
        agent: Boolean = false,
        now: Date = Date(),
        locale: Locale = Locale.US,
        tz: TimeZone = TimeZone.getDefault()
    ): String? {
        if (rt == null || rt.mode == "hide" || !rt.handover || agent) return null
        if (rt.state == "away" || rt.state == "offline") {
            val whenBack = backWhen(rt.backAt, now, locale, tz)
            val words = when {
                whenBack.isNotEmpty() -> "team back $whenBack"
                rt.leaveMessage == false -> "team away right now"
                else -> "team replies by email"
            }
            return ONLINE + SEP + words
        }
        val fixed = if (rt.mode == "fixed") rt.text?.trim().orEmpty() else ""
        if (fixed.isNotEmpty()) return fixed
        val words = rt.bucket?.let { PHRASES[it] } ?: rt.phrase.orEmpty()
        return if (words.isNotEmpty()) "Typically replies $words" else null
    }

    /**
     * What the header shows under the bot name: the reply-time line, or plain
     * "Online" once a person is in the chat (the snapshot about an away team
     * is no longer true), or nothing when the owner turned it off.
     */
    fun headerStatus(
        rt: ReplyTimeInfo?,
        personInChat: Boolean = false,
        agentBot: Boolean = false,
        hidden: Boolean = false,
        now: Date = Date(),
        locale: Locale = Locale.US,
        tz: TimeZone = TimeZone.getDefault()
    ): String? {
        if (hidden) return null
        if (personInChat) return ONLINE
        return headerLine(rt, agentBot, now, locale, tz) ?: ONLINE
    }
}
