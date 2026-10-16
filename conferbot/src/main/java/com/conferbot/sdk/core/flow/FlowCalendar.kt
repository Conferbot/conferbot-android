package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.i18n.I18n
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar step rules, ported from the web widget
 * (conferbot-widget/src/ts/chat/nodeLogic.ts: expandSlots, futureSlots,
 * dayKeyInZone, noonInZone; displayStep.ts calendar-node) by way of RN
 * src/core/flow/calendar.ts. Slot times are the bot's clock (data.timeZone);
 * the visitor sees their own. java.util time only (minSdk 21).
 */
object FlowCalendar {
    data class Slot(val start: String, val end: String)

    /** An instant read on a zone's clock. */
    data class Parts(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val weekday: Int) {
        val date get() = "%04d-%02d-%02d".format(year, month, day)
        val time get() = "%02d:%02d".format(hour, minute)
    }

    val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** A zone by IANA id; null when the id is unknown. */
    fun validZone(zone: Any?): TimeZone? {
        val z = Js.str(zone).trim()
        if (z.isEmpty()) return null
        val tz = TimeZone.getTimeZone(z)
        return if (tz.id == z || z == "UTC" || z == "GMT") tz else null
    }

    fun zone(id: String): TimeZone = validZone(id) ?: TimeZone.getTimeZone("UTC")

    fun parts(epochMs: Long, tz: TimeZone): Parts {
        val c = Calendar.getInstance(tz, Locale.US)
        c.timeInMillis = epochMs
        return Parts(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.DAY_OF_WEEK) - 1
        )
    }

    private fun toMinutes(hhmm: Any?): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(Js.str(hhmm)) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    private fun toHhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** A day's windows cut into slotDuration-minute slots with bufferMinutes between. */
    fun expandSlots(windows: Any?, slotDuration: Any?, bufferMinutes: Any?): List<Slot> {
        val list = Js.list(windows).mapNotNull { Js.map(it) }
        val lengthD = Js.number(slotDuration)
        val length = if (Js.isFinite(lengthD)) Math.floor(lengthD).toInt() else 0
        val bufferD = Js.number(bufferMinutes)
        val buffer = Math.max(0, if (Js.isFinite(bufferD)) Math.floor(bufferD).toInt() else 0)
        if (length <= 0) return list.filter { Js.truthy(it["start"]) && Js.truthy(it["end"]) }.map { Slot(Js.str(it["start"]), Js.str(it["end"])) }
        val out = mutableListOf<Slot>()
        for (w in list) {
            val from = toMinutes(w["start"]) ?: continue
            val to = toMinutes(w["end"]) ?: continue
            if (to <= from) continue
            var t = from
            while (t + length <= to) {
                out.add(Slot(toHhmm(t), toHhmm(t + length)))
                t += length + buffer
            }
        }
        return out
    }

    fun slotLabel(slot: Slot) = "${slot.start} - ${slot.end}"

    /** "YYYY-MM-DD" of an instant on a zone's clock. */
    fun dayKeyInZone(epochMs: Long, timeZone: String): String = parts(epochMs, zone(timeZone)).date

    /** Today's slots that have not started yet (bot's clock); other days keep all. */
    fun futureSlots(slots: List<Slot>, dayKey: String, timeZone: String, nowMs: Long): List<Slot> {
        val p = parts(nowMs, zone(timeZone))
        if (dayKey != p.date) return slots
        val nowMin = p.hour * 60 + p.minute
        return slots.filter { (toMinutes(it.start) ?: 0) > nowMin }
    }

    /** The instant (epoch ms) it is hh:mm on `dateString` on `timeZone`'s clock. */
    fun wallTimeInZone(dateString: String, hhmm: String, timeZone: String): Long {
        val p = dateString.split("-").map { it.toIntOrNull() ?: 1 }
        val mins = toMinutes(hhmm) ?: 12 * 60
        val c = Calendar.getInstance(zone(timeZone), Locale.US)
        c.clear()
        c.set(p[0], p.getOrElse(1) { 1 } - 1, p.getOrElse(2) { 1 }, mins / 60, mins % 60, 0)
        return c.timeInMillis
    }

    /** 12:00 on that day on that zone's clock (the picked day, read the same everywhere). */
    fun noonInZone(dateString: String, timeZone: String): Long = wallTimeInZone(dateString, "12:00", timeZone)

    /** "HH:MM" of an instant on a zone's clock. */
    fun hhmmInZone(epochMs: Long, timeZone: String): String = parts(epochMs, zone(timeZone)).time

    /**
     * "Tuesday, October 20, 2026"; in another [locale] its full date
     * ("Dienstag, 20. Oktober 2026"). The bot's date stays en-US.
     */
    fun longDate(epochMs: Long, timeZone: String, locale: Locale? = null): String {
        if (locale != null && locale.language != "en") {
            runCatching {
                return java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale).apply { this.timeZone = zone(timeZone) }.format(Date(epochMs))
            }
        }
        val p = parts(epochMs, zone(timeZone))
        return "${DAY_NAMES[p.weekday]}, ${MONTHS[p.month - 1]} ${p.day}, ${p.year}"
    }

    /**
     * The locale a visitor's own date is written in: the widget's (web
     * displayStep.ts formattedVisitorDate uses widgetLocale()), null (en-US)
     * for English. The bot's date stays en-US, as the web keeps it.
     */
    fun visitorLocale(): Locale? = if (I18n.language == I18n.EN) null else I18n.locale

    /** The weekday name of a day on the bot's clock (the owner's day names, weeklyHours, are English: kept so). */
    fun dayNameInZone(epochMs: Long, timeZone: String): String = DAY_NAMES[parts(epochMs, zone(timeZone)).weekday]

    /** The date a day before "YYYY-MM-DD". */
    fun previousDate(ymd: String): String {
        val utc = zone("UTC")
        return parts(wallTimeInZone(ymd, "12:00", "UTC") - 86_400_000L, utc).date
    }

    /** Weekday (0 = Sunday) of "YYYY-MM-DD". */
    fun weekdayOf(ymd: String): Int = parts(wallTimeInZone(ymd, "12:00", "UTC"), zone("UTC")).weekday

    /** ISO string of an instant, as JavaScript's toISOString. */
    fun iso(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))

    /** Date.parse for an ISO date-time; null when it does not read. */
    fun parseIso(s: Any?): Long? {
        val text = Js.str(s).trim()
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:T(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,3})\\d*)?)?)?(Z|[+-]\\d{2}:?\\d{2})?$").find(text) ?: return null
        val g = m.groupValues
        val tz = when {
            g[8].isEmpty() -> if (g[4].isEmpty()) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
            g[8] == "Z" -> TimeZone.getTimeZone("UTC")
            else -> TimeZone.getTimeZone("GMT" + g[8].let { if (it.contains(':')) it else it.substring(0, 3) + ":" + it.substring(3) })
        }
        val c = Calendar.getInstance(tz, Locale.US)
        c.clear()
        c.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0)
        c.set(Calendar.MILLISECOND, g[7].padEnd(3, '0').toIntOrNull() ?: 0)
        return c.timeInMillis
    }

    /** The device's own time zone. */
    fun visitorTimeZone(): String = try {
        TimeZone.getDefault().id ?: "UTC"
    } catch (e: Exception) {
        "UTC"
    }
}
