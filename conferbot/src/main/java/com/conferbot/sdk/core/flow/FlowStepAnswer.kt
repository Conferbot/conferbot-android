package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.nodes.NodeUIState
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The answers the chat's step cards send, in the flow-node spec's shapes
 * (conferbot-react-native scripts/parity/flows/nodes.spec.json): what each
 * step's handleResponse takes. The Compose cards (ui/compose/steps) build
 * their answers here, the step-card tests play the fixtures through these
 * same builders, and a custom UI can use them with Conferbot.answerStep.
 *
 * Pure: no Android, no java.time (minSdk 21 without desugaring, #68).
 */
object FlowStepAnswer {
    // ----- scores and values -----

    /** A slider's value (or a rating / scale number). */
    fun value(v: Double): Map<String, Any?> = mapOf("value" to Js.tidy(v))

    /** A date / time step's pick: "2026-10-19", "14:30" or "2026-10-19T14:30". */
    fun dateTime(iso: String): Map<String, Any?> = mapOf("value" to iso)

    /** A calendar step's day ("YYYY-MM-DD") and, with time selection, the slot's start ("HH:MM"). */
    fun calendar(date: String, slot: String? = null): Map<String, Any?> =
        if (slot.isNullOrEmpty()) mapOf("date" to date) else mapOf("date" to date, "slot" to slot)

    // ----- several options -----

    /** The ticked options of a check-options step. */
    fun checked(ids: List<String>): Map<String, Any?> = mapOf("buttonIds" to ids)

    /** A ranking's items, best first. */
    fun ranking(order: List<String>): Map<String, Any?> = mapOf("order" to order)

    // ----- cards and links -----

    /** A card's path button (carousel, product card). */
    fun cardButton(buttonId: String, cardId: String? = null): Map<String, Any?> =
        if (cardId == null) mapOf("buttonId" to buttonId) else mapOf("buttonId" to buttonId, "cardId" to cardId)

    /** Continue under cards that only have link buttons. */
    val cardsContinue: Map<String, Any?> = mapOf("action" to "continue")

    /** A navigate step's link (it opens; the step keeps waiting) and its Done. */
    fun navigateLink(index: Int): Map<String, Any?> = mapOf("action" to "link", "index" to index)
    val navigateDone: Map<String, Any?> = mapOf("action" to "done")

    // ----- matrix -----

    /** A matrix's picks: row id -> column id (single) or ids (multiple). */
    fun matrix(picks: MatrixPicks): Map<String, Any?> {
        val cells = LinkedHashMap<String, Any?>()
        picks.selection.forEach { (row, cols) -> if (cols.isNotEmpty()) cells[row] = if (picks.mode == "single") cols[0] else cols }
        return mapOf("cells" to cells)
    }

    // ----- the rich steps -----

    /** The consent box, ticked or not, then Continue. */
    fun consent(agreed: Boolean): Map<String, Any?> = mapOf("agreed" to agreed)

    fun otpSend(email: String): Map<String, Any?> = mapOf("action" to "send", "email" to email.trim())
    fun otpVerify(code: String): Map<String, Any?> = mapOf("action" to "verify", "code" to code)
    val otpResend: Map<String, Any?> = mapOf("action" to "resend")
    val otpSkip: Map<String, Any?> = mapOf("action" to "skip")

    fun addressSearch(q: String): Map<String, Any?> = mapOf("action" to "search", "q" to q)
    fun addressPick(index: Int): Map<String, Any?> = mapOf("action" to "pick", "index" to index)
    fun addressManual(line1: String, city: String = "", region: String = "", postal: String = "", country: String = ""): Map<String, Any?> {
        fun v(s: String): Any? = s.trim().ifEmpty { null }
        return mapOf("action" to "manual", "line1" to v(line1), "city" to v(city), "region" to v(region), "postal" to v(postal), "country" to v(country))
    }

    /** A typed signature (and the full name when the step asks for it). */
    fun signTyped(name: String, fullName: String? = null): Map<String, Any?> =
        linkedMapOf<String, Any?>("action" to "sign", "method" to "typed", "name" to name).apply { if (!fullName.isNullOrBlank()) put("fullName", fullName) }

    /** A drawn signature as a PNG (sent as a data URL; the step uploads it). */
    fun signDrawn(png: ByteArray, width: Int, height: Int, fullName: String? = null): Map<String, Any?> =
        linkedMapOf<String, Any?>("action" to "sign", "method" to "drawn", "dataUrl" to "data:image/png;base64,${base64(png)}", "width" to width, "height" to height)
            .apply { if (!fullName.isNullOrBlank()) put("fullName", fullName) }

    /** The payment card's buttons: "open" (Checkout), "check" (I've paid), "cancel". */
    fun payment(action: String): Map<String, Any?> = mapOf("action" to action)

    // ----- helpers the cards share -----

    /** "p***@acme.io" */
    fun maskEmail(email: Any?): String {
        val e = Js.str(email).trim().lowercase()
        val at = e.lastIndexOf('@')
        return if (at < 1) "***" else "${e[0]}***${e.substring(at)}"
    }

    /** The digits of a typed or pasted code, at most [length] ("123 456" -> "123456", Arabic digits too). */
    fun digitsFromPaste(text: Any?, length: Int = FlowRich.OTP_LENGTH): String {
        val sb = StringBuilder()
        for (ch in Js.str(text)) {
            val d = when (ch) {
                in '0'..'9' -> ch
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> null
            }
            if (d != null) sb.append(d)
            if (sb.length >= length) break
        }
        return sb.toString()
    }

    /** "Resend in 0:27" / "Resend code" */
    fun resendLabel(seconds: Int): String =
        if (seconds > 0) FlowRich.t("otp.resendIn", mapOf("time" to "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")) else FlowRich.t("otp.resend")

    /** A moment as the date / time step's value for its mode, on [zone]. */
    fun dateTimeValue(epochMs: Long, mode: String, zone: TimeZone = TimeZone.getDefault()): String {
        val p = FlowCalendar.parts(epochMs, zone)
        return when (mode) {
            "time" -> p.time
            "datetime" -> "${p.date}T${p.time}"
            else -> p.date
        }
    }

    /** "2026-10" -> the month's days as "YYYY-MM-DD", null for the blanks before the 1st (weeks start on Sunday). */
    fun monthGrid(year: Int, month: Int): List<String?> {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, 1) }
        val first = c.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val days = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        return List(first) { null } + (1..days).map { d -> "%04d-%02d-%02d".format(year, month, d) }
    }

    /** The month after (+1) or before (-1) "YYYY-MM". */
    fun addMonths(ym: String, n: Int): String {
        val y = ym.take(4).toInt()
        val m = ym.substring(5, 7).toInt() - 1 + n
        // No Math.floorDiv / floorMod: they are API 24+.
        val mm = ((m % 12) + 12) % 12
        return "%04d-%02d".format(y + (m - mm) / 12, mm + 1)
    }

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** "2026-10" -> "October 2026" (in the widget's language: "Oktober 2026"). */
    fun monthTitle(ym: String): String =
        "${com.conferbot.sdk.core.i18n.I18n.monthNames("long", MONTHS)[ym.substring(5, 7).toInt() - 1]} ${ym.take(4)}"

    /** A day within a date step's minDate / maxDate (today is the visitor's "YYYY-MM-DD"). */
    fun dayInBounds(data: Map<String, Any?>?, day: String, today: String): Boolean {
        val min = FlowRich.resolveDateBound(data?.get("minDate"), today)
        val max = FlowRich.resolveDateBound(data?.get("maxDate"), today)
        return (min == null || day >= min) && (max == null || day <= max)
    }

    /** A day the date step takes: within its bounds and not a closed weekday. */
    fun dayAllowed(data: Map<String, Any?>?, day: String, today: String): Boolean =
        dayInBounds(data, day, today) && FlowCalendar.weekdayOf(day) !in FlowRich.disabledWeekdaysOf(data)

    /** The times a time step offers, every timeStep minutes (30 by default) between minTime and maxTime. */
    fun timeChoices(data: Map<String, Any?>?): List<String> {
        val re = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
        fun minutes(v: Any?): Int? = Js.str(v).let { s -> re.find(s)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() } }
        val stepRaw = data?.get("timeStep").let { if (it == null || it == "") Double.NaN else Js.number(it) }
        val step = if (Js.isFinite(stepRaw)) Math.min(720, Math.max(1, Math.floor(stepRaw).toInt())) else 30
        val from = minutes(data?.get("minTime")) ?: 0
        val to = minutes(data?.get("maxTime")) ?: (24 * 60 - 1)
        return generateSequence(from) { it + step }.takeWhile { it <= to }.map { "%02d:%02d".format(it / 60, it % 60) }.toList()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Base64 (android.util.Base64 is a stub in unit tests, java.util.Base64 is API 26+). */
    fun base64(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1
            sb.append(B64[b0 shr 2])
            sb.append(B64[((b0 and 3) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            sb.append(if (b1 < 0) '=' else B64[((b1 and 15) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            sb.append(if (b2 < 0) '=' else B64[b2 and 63])
            i += 3
        }
        return sb.toString()
    }
}

/** A ranking being reordered: the items' ids, best first. */
data class RankingOrder(val ids: List<String>) {
    /** Move an item up (-1) or down (+1); at either end nothing moves. */
    fun move(id: String, by: Int): RankingOrder {
        val i = ids.indexOf(id)
        val j = i + by
        if (i < 0 || j < 0 || j >= ids.size) return this
        return RankingOrder(ids.toMutableList().apply { this[i] = ids[j]; this[j] = ids[i] })
    }
}

/** A matrix being filled in: one column per row (single) or several (multiple). */
data class MatrixPicks(val mode: String, val selection: Map<String, List<String>> = emptyMap()) {
    fun isOn(row: String, col: String) = selection[row]?.contains(col) == true

    /** A tap on a cell: single picks it in place of the row's other; multiple ticks or unticks it. */
    fun tap(row: String, col: String): MatrixPicks {
        val cur = selection[row] ?: emptyList()
        val next = if (mode == "multiple") (if (col in cur) cur - col else cur + col) else listOf(col)
        return copy(selection = LinkedHashMap(selection).apply { if (next.isEmpty()) remove(row) else put(row, next) })
    }

    val answered: Int get() = selection.values.count { it.isNotEmpty() }

    companion object {
        fun of(data: Map<String, Any?>?) = MatrixPicks(FlowRich.matrixMode(data))
    }
}

/**
 * Which steps the Compose chat draws with their own card (ui/compose/steps),
 * and what that means for the chat around it.
 */
object FlowStepCards {
    /** Steps drawn by a card under the bot's question. */
    val CARD_TYPES = setOf(
        "consent-node", "otp-node", "address-node", "signature-node", "matrix-node", "ranking-node", "slider-node",
        "datetime-node", "calendar-node", "payment-node", "carousel-node", "product-card-node", "navigate-node"
    )

    /** Steps whose card takes the visitor's typing: the composer is hidden under them. */
    val OWN_INPUT = setOf("otp-node", "address-node", "signature-node")

    /** The step [ui] waits on when it is drawn by a card, else null. */
    fun cardStep(ui: NodeUIState?): NodeUIState.StepInfo? = ui?.step?.takeIf { it.type in CARD_TYPES }

    /** The composer is hidden while [ui]'s card takes the typing. */
    fun hidesComposer(ui: NodeUIState?): Boolean = ui?.step?.type in OWN_INPUT

    /**
     * The bot's question above a card, said once per visit; null when the
     * step says it itself (matrix's prompt is its own bubble).
     */
    fun question(step: NodeUIState.StepInfo): Any? = when (step.type) {
        "matrix-node" -> null
        "calendar-node" -> step.node["calendarPrompt"]
        "navigate-node" -> step.node["navigatePrompt"]
        else -> step.node["questionText"]
    }
}
