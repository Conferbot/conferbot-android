package com.conferbot.sdk.core.flow

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * The rich steps' rules, the pure half - ported from the web widget
 * (conferbot-widget/src/ts/chat/richNodes.ts) with its English texts
 * (ts/i18n/en.ts), by way of RN src/core/flow/rich.ts and i18nEn.ts.
 */
object FlowRich {
    // ----- English texts (web i18n en.ts) -----
    private val EN_TEXT = mapOf(
        "address.busy" to "Address search is busy right now. Try again in a moment, or enter it manually.",
        "address.denied" to "We couldn't get your location - location access is turned off. Type your address instead.",
        "address.failed" to "Address search isn't working right now. You can enter it manually.",
        "address.noResults" to "No matches. Try adding the city, or enter it manually.",
        "address.unavailable" to "We couldn't find your location. Type your address instead.",
        "common.and" to "and",
        "common.cancel" to "Cancel",
        "common.continue" to "Continue",
        "common.send" to "Send",
        "common.skip" to "Skip",
        "common.somethingWrong" to "Something went wrong. Please try again.",
        "common.submit" to "Submit",
        "consent.agreed" to "Agreed",
        "consent.agreedTo" to "Agreed to {docs}",
        "consent.declined" to "Didn't agree",
        "consent.tickBox" to "Please tick the box to continue.",
        "dates.closedOn" to "Closed on {days}",
        "dates.dayUnavailable" to "{day} aren't available. Please pick another day.",
        "dates.onOrAfter" to "Please pick a date on or after {date}.",
        "dates.onOrBefore" to "Please pick a date on or before {date}.",
        "dates.pickDate" to "Please pick a date.",
        "dates.pickTime" to "Please pick a time.",
        "dates.timeFrom" to "Please pick a time from {time}.",
        "dates.timeUntil" to "Please pick a time until {time}.",
        "kb.askPrompt" to "What would you like to know?",
        "kb.helpArticle" to "Help article",
        "kb.notFound" to "I couldn't find that in our help articles.",
        "matrix.everyRow" to "Answer every row - {n} left",
        "matrix.oneRow" to "Answer at least one row",
        "matrix.progress" to "{n} of {total} answered",
        "otp.allDigits" to "Enter all {n} digits.",
        "otp.expired" to "That code has expired. Send a new one.",
        "otp.invalidEmail" to "Please enter a valid email address.",
        "otp.locked" to "Too many wrong codes.",
        "otp.noCode" to "Send a code first.",
        "otp.notVerified" to "Not verified",
        "otp.resend" to "Resend code",
        "otp.resendIn" to "Resend in {time}",
        "otp.resendWait" to "Please wait {n} seconds before asking for a new code.",
        "otp.smsUnavailable" to "Codes by text message aren't available yet.",
        "otp.tooMany" to "Too many codes were asked for. Please try again later.",
        "otp.verifiedAs" to "Verified: {email}",
        "otp.wrong" to "That code isn't right.",
        "otp.wrongLeft.one" to "That code isn't right. {n} try left.",
        "otp.wrongLeft.other" to "That code isn't right. {n} tries left.",
        "pay.cancelled" to "Payment cancelled",
        "pay.failed" to "Payment couldn't be started. Please try again.",
        "pay.invalidAmount" to "The amount to pay isn't valid.",
        "pay.ivePaid" to "I've paid",
        "pay.network" to "We couldn't reach the payment service. Check your connection and try again.",
        "pay.notConfigured" to "Payments aren't set up for this chat yet.",
        "pay.notYet" to "We haven't received the payment yet. Finish it in the Stripe tab, then check again.",
        "pay.paid" to "Paid",
        "pay.paidAmount" to "Paid {amount}",
        "pay.payNow" to "Pay now",
        "pay.rateLimited" to "Too many tries. Please wait a moment.",
        "pay.timedOut" to "Payment timed out",
        "rich.nothingToChoose" to "There's nothing to choose from right now.",
        "signature.sign" to "Sign",
        "signature.typeToSign" to "Type your full name to sign"
    )

    /** A text by key, {name} placeholders filled (web i18n t). */
    fun t(key: String, vars: Map<String, Any?>? = null): String {
        val text = EN_TEXT[key] ?: key
        if (vars == null) return text
        return Regex("\\{(\\w+)}").replace(text) { m -> vars[m.groupValues[1]]?.let { Js.str(it) } ?: m.value }
    }

    fun plainText(value: Any?): String = FlowText.plainText(value)

    // ----- paths into data -----

    /** Read a dotted path; null when any step is missing. */
    fun getPath(value: Any?, path: Any?): Any? {
        val p = Js.str(path).trim()
        if (p.isEmpty()) return value
        val parts = p.replace(Regex("\\[(\\d+)]"), ".$1").split(".").filter { it.isNotEmpty() }
        var cur = value
        for (key in parts) {
            val c = cur
            cur = when (c) {
                is Map<*, *> -> c[key]
                is List<*> -> key.toIntOrNull()?.let { c.getOrNull(it) }
                else -> return null
            }
        }
        return cur
    }

    /** A variable's value as data: JSON text (a webhook reply saved as text) is parsed. */
    fun asData(value: Any?): Any? {
        if (value !is String) return value
        val t = value.trim()
        if (!(t.startsWith("[") || t.startsWith("{"))) return value
        return Js.parse(t) ?: value
    }

    // ----- (1) dynamic choices -----
    const val DYNAMIC_MAX_OPTIONS = 50
    const val DYNAMIC_DEFAULT_MAX = 10
    const val DYNAMIC_EMPTY_HANDLE = "source-empty"

    data class DynamicOption(val id: String, val label: String, val value: Any?)

    fun dynamicSourceVariable(data: Map<String, Any?>?) = Js.str(data?.get("sourceVariable")).trim()

    private fun scalar(v: Any?) = v == null || v is String || v is Number || v is Boolean

    fun dynamicOptions(data: Map<String, Any?>?, sourceValue: Any?): List<DynamicOption> {
        val root = asData(sourceValue)
        val list = asData(getPath(root, data?.get("listPath"))) as? List<*> ?: return emptyList()
        val labelField = Js.str(data?.get("labelField")).trim()
        val valueField = Js.str(data?.get("valueField")).trim()
        val maxRaw = Math.floor(Js.number(data?.get("maxOptions") ?: Double.NaN))
        val max = Math.max(1, Math.min(DYNAMIC_MAX_OPTIONS, if (Js.isFinite(maxRaw) && maxRaw != 0.0) maxRaw.toInt() else DYNAMIC_DEFAULT_MAX))
        val seen = mutableSetOf<String>()
        val out = mutableListOf<DynamicOption>()
        for (item in list) {
            if (out.size >= max) break
            var label: Any?
            var value: Any?
            if (scalar(item)) {
                if (item == null) continue
                label = item
                value = item
            } else if (item is Map<*, *>) {
                label = if (labelField.isNotEmpty()) getPath(item, labelField) else item["label"] ?: item["name"] ?: item["title"] ?: item["text"]
                value = if (valueField.isNotEmpty()) getPath(item, valueField) else item["value"] ?: item["id"] ?: label
            } else continue
            val text = plainText(if (scalar(label)) label else "")
            if (text.isEmpty()) continue
            if (!scalar(value) || value == null) value = text
            val key = "$text\u0000${Js.str(value)}"
            if (!seen.add(key)) continue
            out.add(DynamicOption("opt-${out.size}", text, value))
        }
        return out
    }

    // ----- (2) hidden fields -----
    const val HIDDEN_MAX_VALUE = 500

    private fun clip(v: Any?) = Js.str(v).trim().take(HIDDEN_MAX_VALUE)
    fun isWebAddress(v: Any?) = Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(Js.str(v))

    private fun queryParams(url: String?): Map<String, String> {
        if (url.isNullOrEmpty()) return emptyMap()
        val q = url.substringAfter('?', "").substringBefore('#')
        val out = linkedMapOf<String, String>()
        q.split("&").filter { it.isNotEmpty() }.forEach { pair ->
            val k = Js.decodeURIComponent(pair.substringBefore('=').replace('+', ' '))
            val v = Js.decodeURIComponent(pair.substringAfter('=', "").replace('+', ' '))
            if (!out.containsKey(k)) out[k] = v
        }
        return out
    }

    /** What a hidden fields node captures: { variable: value } for each parameter present, the page URL and the referrer. */
    fun captureHiddenFields(data: Map<String, Any?>?, href: String?, referrer: String?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val current = queryParams(href)
        Js.list(data?.get("params")).mapNotNull { Js.map(it) }.forEach { p ->
            val name = Js.str(p["name"]).trim()
            val variable = Js.str(p["variable"]).trim()
            if (name.isEmpty() || variable.isEmpty()) return@forEach
            val v = current[name]
            if (v != null && clip(v) != "") out[variable] = clip(v)
        }
        val pageVar = Js.str(data?.get("pageUrlVariable")).trim()
        if (data?.get("capturePageUrl") != false && pageVar.isNotEmpty() && isWebAddress(href)) out[pageVar] = clip(href)
        val refVar = Js.str(data?.get("referrerVariable")).trim()
        if (data?.get("captureReferrer") != false && refVar.isNotEmpty() && isWebAddress(referrer)) out[refVar] = clip(referrer)
        return out
    }

    // ----- (3) consent -----
    fun safeLink(url: Any?): String {
        val raw = Js.str(url).trim()
        if (Regex("^(https?://|mailto:)\\S+$", RegexOption.IGNORE_CASE).matches(raw)) return raw
        if (Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?$", RegexOption.IGNORE_CASE).matches(raw)) return "https://$raw"
        return ""
    }

    private val MD_LINK = Regex("\\[([^\\]\\n]{1,200})]\\(((?:[^()\\s]|\\([^()\\s]*\\)){1,500})\\)")

    fun markdownLitePlain(md: Any?): String = Js.str(md)
        .replace(MD_LINK, "$1")
        .replace(Regex("\\*\\*([^*\\n]+)\\*\\*"), "$1")
        .replace(Regex("(^|[\\s(])[*_]([^*_\\n]+)[*_](?=[\\s).,!?:;]|$)"), "$1$2")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun consentDocuments(md: Any?): List<String> {
        val out = mutableListOf<String>()
        MD_LINK.findAll(Js.str(md)).forEach { m ->
            val t = m.groupValues[1].trim()
            if (t.isNotEmpty() && safeLink(m.groupValues[2]).isNotEmpty() && t !in out) out.add(t)
        }
        return out
    }

    fun joinAnd(items: List<String>): String =
        if (items.size <= 1) items.joinToString("") else "${items.dropLast(1).joinToString(", ")} ${t("common.and")} ${items.last()}"

    fun consentReply(md: Any?, agreed: Boolean): String {
        if (!agreed) return t("consent.declined")
        val docs = consentDocuments(md)
        return if (docs.isNotEmpty()) t("consent.agreedTo", mapOf("docs" to joinAnd(docs))) else t("consent.agreed")
    }

    fun consentContinueLabel(data: Map<String, Any?>?) = Js.str(data?.get("continueLabel")).trim().ifEmpty { t("common.continue") }
    fun consentRequired(data: Map<String, Any?>?) = data?.get("required") != false

    // ----- (4) product card -----
    const val PRODUCT_CARD_ID = "product"

    fun productCarouselData(data: Map<String, Any?>?): Map<String, Any?> = mapOf(
        "cards" to listOf(
            mapOf(
                "id" to PRODUCT_CARD_ID,
                "image" to (data?.get("image") ?: ""),
                "title" to (data?.get("title") ?: ""),
                "description" to (data?.get("description") ?: ""),
                "price" to (data?.get("price") ?: ""),
                "buttons" to Js.list(data?.get("buttons")).take(3)
            )
        )
    )

    // ----- (5a) slider -----
    private fun numOr(v: Any?, d: Double): Double {
        if (v == null || v == "") return d
        val n = Js.number(v)
        return if (Js.isFinite(n)) n else d
    }

    data class SliderConfig(val min: Double, val max: Double, val step: Double, val start: Double, val unit: String, val unitPosition: String)

    private fun decimalsOf(n: Double): Int {
        val s = Js.num(n)
        return if (s.contains('.')) s.substringAfter('.').length else 0
    }

    fun snapToStep(value: Double, min: Double, max: Double, step: Double): Double {
        val steps = Math.round((value - min) / step)
        val v = Math.min(max, Math.max(min, min + steps * step))
        return java.math.BigDecimal(v).setScale(Math.max(decimalsOf(step), decimalsOf(min)), java.math.RoundingMode.HALF_UP).toDouble()
    }

    fun sliderConfig(data: Map<String, Any?>?): SliderConfig {
        val min = numOr(data?.get("min"), 0.0)
        var max = numOr(data?.get("max"), 100.0)
        if (max <= min) max = min + 1
        var step = numOr(data?.get("step"), 1.0)
        if (!(step > 0) || step > max - min) step = Math.min(1.0, max - min).let { if (it == 0.0) 1.0 else it }
        val wanted = numOr(data?.get("defaultValue"), (min + max) / 2)
        return SliderConfig(
            min, max, step, snapToStep(wanted, min, max, step),
            Js.str(data?.get("unit")).trim(),
            if (data?.get("unitPosition") == "prefix") "prefix" else "suffix"
        )
    }

    fun sliderLabel(value: Any?, cfg: SliderConfig): String {
        val v = if (value is Number) Js.num(value.toDouble()) else Js.str(value)
        if (cfg.unit.isEmpty()) return v
        return if (cfg.unitPosition == "prefix") "${cfg.unit}$v" else "$v ${cfg.unit}"
    }

    fun submitLabel(data: Map<String, Any?>?) = Js.str(data?.get("submitLabel")).trim().ifEmpty { t("common.send") }

    // ----- (5b) date / time picker -----
    fun dateMode(data: Map<String, Any?>?): String = data?.get("mode").let { if (it == "time" || it == "datetime") it as String else "date" }

    private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val TIME_RE = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
    private val WEEKDAYS = FlowCalendar.DAY_NAMES

    fun validYmd(s: Any?): Boolean {
        val m = DATE_RE.find(Js.str(s)) ?: return false
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d < 1) return false
        val c = java.util.GregorianCalendar(y, mo - 1, 1)
        return d <= c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    /** A bound as a date: "2026-10-05", "today", "+7" / "-3" (days from today, `today` "YYYY-MM-DD"). */
    fun resolveDateBound(bound: Any?, today: String): String? {
        val b = Js.str(bound).trim().lowercase()
        if (b.isEmpty()) return null
        if (b == "today") return today
        val rel = Regex("^([+-])(\\d{1,4})$").find(b)
        if (rel != null) {
            val days = (if (rel.groupValues[1] == "-") -1 else 1) * rel.groupValues[2].toLong()
            return FlowCalendar.parts(FlowCalendar.wallTimeInZone(today, "12:00", "UTC") + days * 86_400_000L, FlowCalendar.zone("UTC")).date
        }
        return if (validYmd(b)) b else null
    }

    fun disabledWeekdaysOf(data: Map<String, Any?>?): List<Int> =
        Js.list(data?.get("disabledWeekdays")).map { Js.number(it) }.filter { it == Math.floor(it) && it in 0.0..6.0 }.map { it.toInt() }

    private fun weekdaysName(wd: Int) = "${WEEKDAYS[wd]}s"

    /** Why an ISO value isn't allowed, or null. `today` is the visitor's "YYYY-MM-DD". */
    fun dateTimeError(data: Map<String, Any?>?, iso: Any?, today: String): String? {
        val mode = dateMode(data)
        val s = Js.str(iso)
        val (datePart, timePart) = when (mode) {
            "datetime" -> s.substringBefore('T') to (if (s.contains('T')) s.substringAfter('T').substringBefore('T') else "")
            "time" -> "" to s
            else -> s to ""
        }
        if (mode != "time") {
            if (!validYmd(datePart)) return t("dates.pickDate")
            val min = resolveDateBound(data?.get("minDate"), today)
            val max = resolveDateBound(data?.get("maxDate"), today)
            if (min != null && datePart < min) return t("dates.onOrAfter", mapOf("date" to min))
            if (max != null && datePart > max) return t("dates.onOrBefore", mapOf("date" to max))
            val wd = FlowCalendar.weekdayOf(datePart)
            if (wd in disabledWeekdaysOf(data)) return t("dates.dayUnavailable", mapOf("day" to weekdaysName(wd)))
        }
        if (mode != "date") {
            if (!TIME_RE.matches(timePart)) return t("dates.pickTime")
            val minT = Js.str(data?.get("minTime"))
            val maxT = Js.str(data?.get("maxTime"))
            if (minT.isNotEmpty() && TIME_RE.matches(minT) && timePart < minT) return t("dates.timeFrom", mapOf("time" to minT))
            if (maxT.isNotEmpty() && TIME_RE.matches(maxT) && timePart > maxT) return t("dates.timeUntil", mapOf("time" to maxT))
        }
        return null
    }

    fun isoFromInput(mode: String, value: Any?): String {
        val v = Js.str(value).trim()
        return when (mode) {
            "time" -> v.take(5)
            "datetime" -> v.take(16)
            else -> v.take(10)
        }
    }

    private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun clock12(h: Int, m: Int) = "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "AM" else "PM"}"

    /** The picked value for people (en-US medium date, short time). */
    fun dateTimeDisplay(data: Map<String, Any?>?, iso: Any?): String {
        val mode = dateMode(data)
        val s = Js.str(iso)
        return try {
            if (mode == "time") {
                val (h, m) = s.split(":").map { it.toInt() }
                clock12(h, m)
            } else {
                val dp = s.substringBefore('T')
                val tp = if (s.contains('T')) s.substringAfter('T') else "00:00"
                val (y, mo, d) = dp.split("-").map { it.toInt() }
                val date = "${MONTHS_SHORT[mo - 1]} $d, $y"
                if (mode == "date") date else {
                    val (h, mi) = tp.split(":").map { it.toInt() }
                    "$date, ${clock12(h, mi)}"
                }
            }
        } catch (e: Exception) {
            s
        }
    }

    // ----- (6) ranking -----
    const val RANKING_MAX = 10

    data class RankItem(val id: String, val text: String)

    fun rankingItems(data: Map<String, Any?>?): List<RankItem> =
        Js.list(data?.get("items")).mapIndexed { i, it -> RankItem(Js.str(Js.map(it)?.get("id") ?: i), plainText(Js.map(it)?.get("text"))) }
            .filter { it.text.isNotEmpty() }.take(RANKING_MAX)

    fun rankingText(order: List<RankItem>) = order.mapIndexed { i, it -> "${i + 1}. ${it.text}" }.joinToString(" · ")

    // ----- (7) verification code (OTP) -----
    const val OTP_LENGTH = 6
    const val OTP_RESEND_SECONDS = 30

    private val EMAIL_RE = Regex("^[^\\s@<>()\\[\\],;:\"]+@[^\\s@<>()\\[\\],;:\"]+\\.[^\\s@<>()\\[\\],;:\"]{2,}$")
    fun isEmailAddress(v: Any?) = v is String && v.length <= 254 && EMAIL_RE.matches(v.trim())

    fun otpChannel(data: Map<String, Any?>?) = if (data?.get("channel") == "sms") "sms" else "email"

    fun otpPresetEmail(data: Map<String, Any?>?, lookup: VariableLookup): String {
        if (data?.get("emailSource") != "variable") return ""
        val key = Js.str(data["emailVariable"]).trim()
        val v = if (key.isNotEmpty()) Js.str(lookup(key)).trim() else ""
        return if (isEmailAddress(v)) v.lowercase() else ""
    }

    fun otpErrorText(code: Any?, attemptsLeft: Any? = null, retryAfter: Any? = null): String = when (code) {
        "invalid_email" -> t("otp.invalidEmail")
        "wrong_code" -> {
            val n = Js.number(attemptsLeft ?: Double.NaN)
            if (Js.isFinite(n)) t(if (n == 1.0) "otp.wrongLeft.one" else "otp.wrongLeft.other", mapOf("n" to n)) else t("otp.wrong")
        }
        "invalid_code" -> t("otp.allDigits", mapOf("n" to OTP_LENGTH))
        "expired" -> t("otp.expired")
        "no_code" -> t("otp.noCode")
        "locked" -> t("otp.locked")
        "resend_wait" -> t("otp.resendWait", mapOf("n" to Math.max(1.0, Js.number(retryAfter).let { if (Js.isFinite(it) && it != 0.0) it else OTP_RESEND_SECONDS.toDouble() })))
        "too_many_sends", "rate_limited" -> t("otp.tooMany")
        "sms_unavailable" -> t("otp.smsUnavailable")
        else -> t("common.somethingWrong")
    }

    fun otpEndsStep(code: Any?) = Js.str(code) in setOf("locked", "sms_unavailable", "node_not_found", "send_failed")

    fun otpReplyText(verified: Boolean, email: String?) = if (verified) t("otp.verifiedAs", mapOf("email" to (email ?: ""))) else t("otp.notVerified")

    // ----- (8) address -----
    data class AddressValue(
        val formatted: String, val line1: String, val city: String, val region: String, val postal: String,
        val country: String, val countryCode: String, val lat: Double?, val lng: Double?
    ) {
        fun toMap(): Map<String, Any?> = linkedMapOf(
            "formatted" to formatted, "line1" to line1, "city" to city, "region" to region, "postal" to postal,
            "country" to country, "countryCode" to countryCode, "lat" to lat?.let { Js.tidy(it) }, "lng" to lng?.let { Js.tidy(it) }
        )
    }

    private fun clip200(v: Any?) = Js.str(v).replace(Regex("\\s+"), " ").trim().take(200)
    private fun finiteOrNull(v: Any?): Double? {
        if (v == null || v == "") return null
        val n = Js.number(v)
        return if (Js.isFinite(n)) n else null
    }

    fun formatAddress(line1: String, city: String, region: String, postal: String, country: String): String {
        val cityLine = listOf(city, listOf(if (region != city) region else "", postal).filter { it.isNotEmpty() }.joinToString(" ")).filter { it.isNotEmpty() }.joinToString(", ")
        return listOf(clip200(line1), cityLine, clip200(country)).filter { it.isNotEmpty() }.joinToString(", ")
    }

    fun normalizeAddress(v: Map<String, Any?>?): AddressValue {
        val cc = Js.str(v?.get("countryCode")).trim()
        var lat = finiteOrNull(v?.get("lat"))
        var lng = finiteOrNull(v?.get("lng"))
        if (lat == null || lng == null || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            lat = null
            lng = null
        }
        val line1 = clip200(v?.get("line1"))
        val city = clip200(v?.get("city"))
        val region = clip200(v?.get("region"))
        val postal = clip200(v?.get("postal"))
        val country = clip200(v?.get("country"))
        val formatted = clip200(v?.get("formatted")).ifEmpty { formatAddress(line1, city, region, postal, country) }
        return AddressValue(formatted, line1, city, region, postal, country, if (Regex("^[a-z]{2}$", RegexOption.IGNORE_CASE).matches(cc)) cc.lowercase() else "", lat, lng)
    }

    /** The visitor's own entry; null when there is no street line. */
    fun manualAddress(fields: Map<String, Any?>?): AddressValue? {
        val v = normalizeAddress(mapOf("line1" to fields?.get("line1"), "city" to fields?.get("city"), "region" to fields?.get("region"), "postal" to fields?.get("postal"), "country" to fields?.get("country")))
        if (v.line1.isEmpty()) return null
        return v.copy(formatted = formatAddress(v.line1, v.city, v.region, v.postal, v.country))
    }

    /** The variables an address saves: { k: formatted, k_line1, k_city, ... } (empty parts left out). */
    fun addressVariables(key: Any?, value: AddressValue): Map<String, Any?> {
        val k = Js.str(key).trim()
        val out = linkedMapOf<String, Any?>()
        if (k.isEmpty()) return out
        out[k] = value.formatted
        listOf("line1" to value.line1, "city" to value.city, "region" to value.region, "postal" to value.postal, "country" to value.country,
            "country_code" to value.countryCode, "lat" to value.lat?.let { Js.tidy(it) }, "lng" to value.lng?.let { Js.tidy(it) })
            .forEach { (suffix, v) -> if (v != null && v != "") out["${k}_$suffix"] = v }
        return out
    }

    fun addressErrorText(code: Any?): String = when (code) {
        "denied" -> t("address.denied")
        "unavailable" -> t("address.unavailable")
        "busy", "rate_limited" -> t("address.busy")
        "no_results" -> t("address.noResults")
        else -> t("address.failed")
    }

    // ----- (9) matrix -----
    const val MATRIX_MAX_ROWS = 12
    const val MATRIX_MAX_COLUMNS = 7

    data class MatrixItem(val id: String, val text: String)

    private fun matrixItems(list: Any?, max: Int, prefix: String): List<MatrixItem> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<MatrixItem>()
        Js.list(list).forEachIndexed { i, it ->
            val m = Js.map(it)
            val text = plainText(m?.get("text"))
            if (text.isEmpty() || out.size >= max) return@forEachIndexed
            var id = Js.str(m?.get("id") ?: "$prefix$i")
            if (id in seen) id = "${id}_$i"
            seen.add(id)
            out.add(MatrixItem(id, text))
        }
        return out
    }

    fun matrixRows(data: Map<String, Any?>?) = matrixItems(data?.get("rows"), MATRIX_MAX_ROWS, "r")
    fun matrixColumns(data: Map<String, Any?>?) = matrixItems(data?.get("columns"), MATRIX_MAX_COLUMNS, "c")
    fun matrixMode(data: Map<String, Any?>?) = if (data?.get("mode") == "multiple") "multiple" else "single"
    fun matrixRequireAll(data: Map<String, Any?>?) = data?.get("requireAll") != false

    fun matrixToggle(sel: Map<String, List<String>>, rowId: String, colId: String, mode: String): Map<String, List<String>> {
        val cur = sel[rowId] ?: emptyList()
        val next = if (mode == "single") listOf(colId) else if (colId in cur) cur else cur + colId
        return LinkedHashMap(sel).apply { if (next.isNotEmpty()) put(rowId, next) else remove(rowId) }
    }

    fun matrixMissing(sel: Map<String, List<String>>, rows: List<MatrixItem>) = rows.filter { sel[it.id].isNullOrEmpty() }

    fun matrixValue(sel: Map<String, List<String>>, rows: List<MatrixItem>, cols: List<MatrixItem>, mode: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        rows.forEach { r ->
            val picked = cols.filter { (sel[r.id] ?: emptyList()).contains(it.id) }.map { it.text }
            if (picked.isEmpty()) return@forEach
            out[r.text] = if (mode == "single") picked[0] else picked
        }
        return out
    }

    fun matrixRowAnswers(sel: Map<String, List<String>>, rows: List<MatrixItem>, cols: List<MatrixItem>, mode: String): List<Map<String, Any?>> =
        rows.map { r ->
            val picked = cols.filter { (sel[r.id] ?: emptyList()).contains(it.id) }.map { it.text }
            linkedMapOf("id" to r.id, "text" to r.text, "answer" to if (picked.isNotEmpty()) (if (mode == "single") picked[0] else picked) else null)
        }

    fun matrixDisplay(value: Map<String, Any?>) = value.entries.joinToString(" · ") { (k, v) -> "$k: ${if (v is List<*>) v.joinToString(", ") else Js.str(v)}" }

    fun matrixError(sel: Map<String, List<String>>, rows: List<MatrixItem>, requireAll: Boolean): String {
        val missing = matrixMissing(sel, rows)
        if (requireAll && missing.isNotEmpty()) return t("matrix.everyRow", mapOf("n" to missing.size))
        if (missing.size == rows.size) return t("matrix.oneRow")
        return ""
    }

    // ----- (10) AI extract / route -----
    const val AI_MIN_THINK_MS = 500L
    const val AI_CLIENT_TIMEOUT_MS = 15000L
    const val AI_MAX_INPUT = 2000

    fun aiSourceOf(data: Map<String, Any?>?): String = data?.get("source").let { if (it == "last_message" || it == "variable") it as String else "ask" }

    fun aiPresetText(data: Map<String, Any?>?, lastUserText: Any?, variableValue: Any?): String? {
        val source = aiSourceOf(data)
        if (source == "ask") return null
        val v = if (source == "variable") variableValue else lastUserText
        if (v == null) return ""
        return (if (v is Map<*, *> || v is List<*>) Js.stringify(v) else Js.str(v)).trim().take(AI_MAX_INPUT)
    }

    private val AI_VAR_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val AI_FIELD_TYPES = setOf("text", "number", "email", "phone", "date", "boolean", "list")

    data class AiField(val name: String, val type: String, val required: Boolean)

    fun aiFieldsOf(data: Map<String, Any?>?): List<AiField> {
        val all = Js.list(data?.get("fields")).map { f ->
            val m = Js.map(f)
            AiField(Js.str(m?.get("name")).trim(), m?.get("type").let { if (it in AI_FIELD_TYPES) it as String else "text" }, m?.get("required") == true)
        }
        return all.filterIndexed { i, f -> AI_VAR_RE.matches(f.name) && all.indexOfFirst { it.name == f.name } == i }.take(15)
    }

    data class AiExtractResult(val values: Map<String, Any?>, val missing: List<String>, val ok: Boolean, val error: String?)
    data class AiRouteResult(val intentId: String?, val intent: String?, val confidence: Double, val fallback: Boolean, val error: String?)

    fun aiExtractResultOf(data: Map<String, Any?>?, json: Any?, failed: String?): AiExtractResult {
        val required = aiFieldsOf(data).filter { it.required }.map { it.name }
        val body = Js.map(json)
        if (failed != null || body == null) return AiExtractResult(emptyMap(), required, false, failed ?: "bad_reply")
        val values = Js.map(body["values"]) ?: emptyMap()
        val missing = (body["missing"] as? List<*>)?.map { Js.str(it) } ?: required.filter { !values.containsKey(it) }
        return AiExtractResult(values, missing, body["ok"] == true && missing.isEmpty(), body["error"]?.let { Js.str(it) })
    }

    fun aiRouteResultOf(json: Any?, failed: String?): AiRouteResult {
        val body = Js.map(json)
        val conf = Js.number(body?.get("confidence") ?: Double.NaN).let { if (Js.isFinite(it)) it else 0.0 }
        if (failed != null || body == null || Js.truthy(body["fallback"]) || !Js.truthy(body["intentId"])) {
            return AiRouteResult(null, null, conf, true, failed ?: body?.get("error")?.let { if (Js.truthy(it)) Js.str(it) else null } ?: "no_match")
        }
        return AiRouteResult(Js.str(body["intentId"]), Js.str(body["intent"] ?: ""), conf, false, null)
    }

    fun aiExtractVariables(data: Map<String, Any?>?, r: AiExtractResult): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val names = aiFieldsOf(data).map { it.name }.toSet()
        r.values.forEach { (k, v) -> if (k in names) out[k] = v }
        val key = Js.str(data?.get("answerVariable")).trim()
        if (key.isNotEmpty()) {
            out[key] = LinkedHashMap(out)
            out["${key}_missing"] = r.missing
        }
        return out
    }

    fun aiRouteVariables(data: Map<String, Any?>?, r: AiRouteResult): Map<String, Any?> {
        val key = Js.str(data?.get("answerVariable")).trim()
        if (key.isEmpty()) return emptyMap()
        return linkedMapOf(key to (if (r.fallback) "" else r.intent ?: ""), "${key}_confidence" to Js.tidy(Math.round(r.confidence * 100) / 100.0))
    }

    /** The visitor's latest message in a transcript of (by, message). */
    fun lastUserMessage(transcript: List<Pair<String, String>>): String {
        for (i in transcript.indices.reversed()) {
            val (by, message) = transcript[i]
            if (by == "user" && plainText(message).isNotEmpty()) return plainText(message)
        }
        return ""
    }

    // ----- (11) knowledge-base answer -----
    const val KB_CLIENT_TIMEOUT_MS = 18000L
    const val KB_MAX_ANSWER = 700

    data class KbSource(val title: String, val url: String, val articleId: String?, val type: String) {
        fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>("title" to title, "url" to url).apply {
            if (articleId != null) put("articleId", articleId)
            put("type", type)
        }
    }

    data class KbResult(val found: Boolean, val answer: String, val sources: List<KbSource>, val error: String?)

    private val OBJECT_ID_RE = Regex("^[a-f0-9]{24}$", RegexOption.IGNORE_CASE)

    fun kbMaxSources(data: Map<String, Any?>?): Int {
        val n = Math.floor(Js.number(data?.get("maxSources") ?: Double.NaN))
        return if (Js.isFinite(n)) Math.min(5, Math.max(1, n.toInt())) else 3
    }

    fun kbResultOf(data: Map<String, Any?>?, json: Any?, errorCode: String?): KbResult {
        val body = Js.map(json)
        val answer = plainText(body?.get("answer")).take(KB_MAX_ANSWER + 1)
        if (body == null || body["found"] != true || answer.isEmpty()) {
            return KbResult(false, "", emptyList(), errorCode ?: (body?.get("error") as? String) ?: "not_found")
        }
        val seen = mutableSetOf<String>()
        val sources = mutableListOf<KbSource>()
        if (data?.get("showSources") != false) {
            for (s in Js.list(body["sources"])) {
                if (sources.size >= kbMaxSources(data)) break
                val m = Js.map(s)
                val url = Js.str(m?.get("url")).let { if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it)) it else "" }
                val articleId = Js.str(m?.get("articleId")).let { if (OBJECT_ID_RE.matches(it)) it else "" }
                val key = if (articleId.isNotEmpty()) "a:$articleId" else url
                if (key.isEmpty() || !seen.add(key)) continue
                val title = plainText(m?.get("title")).take(160).ifEmpty { if (url.isNotEmpty()) url.replace(Regex("^https?://"), "") else t("kb.helpArticle") }
                sources.add(KbSource(title, url, articleId.ifEmpty { null }, if (articleId.isNotEmpty()) "article" else "page"))
            }
        }
        return KbResult(true, answer, sources, null)
    }

    fun kbVariables(data: Map<String, Any?>?, r: KbResult): Map<String, Any?> {
        val k = Js.str(data?.get("answerVariable")).trim()
        if (k.isEmpty()) return emptyMap()
        return linkedMapOf(
            k to (if (r.found) r.answer else ""),
            "${k}_found" to r.found,
            "${k}_sources" to (if (r.found) r.sources.map { linkedMapOf("title" to it.title, "url" to it.url) } else emptyList())
        )
    }

    fun kbNotFoundHtml(data: Map<String, Any?>?): String = if (plainText(data?.get("notFoundMessage")).isNotEmpty()) Js.str(data?.get("notFoundMessage")) else "<p>${t("kb.notFound")}</p>"

    // ----- (12) signature -----
    fun isHexColor(v: Any?) = Regex("^#([0-9a-f]{3}|[0-9a-f]{6})$", RegexOption.IGNORE_CASE).matches(Js.str(v).trim())

    data class SignatureConfig(val allowTyped: Boolean, val requireName: Boolean, val submitLabel: String)

    fun signatureConfig(data: Map<String, Any?>?) = SignatureConfig(
        data?.get("allowTyped") != false,
        data?.get("requireName") == true,
        Js.str(data?.get("submitLabel")).trim().take(30).ifEmpty { t("signature.sign") }
    )

    fun isSignatureName(v: Any?): Boolean {
        val t = Js.str(v).replace(Regex("\\s+"), " ").trim()
        return t.length in 2..120 && Regex("\\p{L}").containsMatchIn(t)
    }

    fun cleanSignatureName(v: Any?) = Js.str(v).replace(Regex("\\s+"), " ").trim().take(120)

    fun signatureVariables(k: String, url: String, at: String, name: String?, method: String): Map<String, Any?> {
        val key = k.trim()
        if (key.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, Any?>(key to url, "${key}_at" to at, "${key}_method" to method)
        if (!name.isNullOrEmpty()) out["${key}_name"] = name
        return out
    }

    // ----- payment (payment-node) -----
    const val PAYMENT_FAILED_HANDLE = "source-failed"
    const val PAYMENT_TIMEOUT_HANDLE = "source-timeout"
    const val PAYMENT_DEFAULT_TIMEOUT_MIN = 15
    const val PAYMENT_PEEK_MS = 5000L

    private val PAY_ZERO_DECIMAL = setOf("bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf")
    private val PAY_THREE_DECIMAL = setOf("bhd", "jod", "kwd", "omr", "tnd")

    fun paymentDecimals(currency: Any?): Int {
        val c = Js.str(currency).lowercase()
        return if (c in PAY_ZERO_DECIMAL) 0 else if (c in PAY_THREE_DECIMAL) 3 else 2
    }

    /** Minor units -> "$25.00" (en-US; falls back to "USD 25.00"). */
    fun paymentAmountText(minor: Any?, currency: Any?): String {
        val c = Js.str(currency).trim().uppercase()
        val n = Js.number(minor)
        if (!Regex("^[A-Z]{3}$").matches(c) || !Js.isFinite(n)) return ""
        val major = n / Math.pow(10.0, paymentDecimals(c).toDouble())
        return try {
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                this.currency = Currency.getInstance(c)
                minimumFractionDigits = this.currency!!.defaultFractionDigits
                maximumFractionDigits = this.currency!!.defaultFractionDigits
            }.format(major)
        } catch (e: Exception) {
            "$c ${"%.${paymentDecimals(c)}f".format(Locale.US, major)}"
        }
    }

    fun paymentTimeoutMs(data: Map<String, Any?>?): Long {
        val n = Math.floor(Js.number(data?.get("timeoutMinutes") ?: Double.NaN))
        return (if (Js.isFinite(n) && n >= 1) Math.min(n, 1440.0).toLong() else PAYMENT_DEFAULT_TIMEOUT_MIN.toLong()) * 60 * 1000
    }

    private val PAY_OUTCOME = mapOf("paid" to "paid", "failed" to "failed", "cancelled" to "failed", "timeout" to "timeout", "expired" to "timeout")
    fun paymentOutcomeOf(status: Any?): String? = PAY_OUTCOME[Js.str(status)]
    fun paymentHandleOf(outcome: String) = when (outcome) {
        "paid" -> "source"
        "timeout" -> PAYMENT_TIMEOUT_HANDLE
        else -> PAYMENT_FAILED_HANDLE
    }

    fun isCheckoutUrl(url: Any?) = Regex("^https://([a-z0-9-]+\\.)*stripe\\.com/", RegexOption.IGNORE_CASE).containsMatchIn(Js.str(url))
    fun isReceiptUrl(url: Any?) = isCheckoutUrl(url)

    fun paymentVariables(key: Any?, status: String, amountMinor: Double?, currency: String, receiptUrl: String, paymentId: String): Map<String, Any?> {
        val k = Js.str(key).trim()
        if (k.isEmpty()) return emptyMap()
        val amount = if (amountMinor == null || !Js.isFinite(amountMinor)) null else Js.tidy(amountMinor / Math.pow(10.0, paymentDecimals(currency).toDouble()))
        val cur = currency.uppercase()
        val receipt = if (isReceiptUrl(receiptUrl)) receiptUrl else ""
        return linkedMapOf(
            k to linkedMapOf("status" to status, "amount" to amount, "currency" to cur, "receiptUrl" to receipt, "paymentId" to paymentId),
            "${k}_status" to status,
            "${k}_amount" to amount,
            "${k}_currency" to cur,
            "${k}_receipt_url" to receipt
        )
    }

    fun paymentReplyText(outcome: String, amountText: String = ""): String =
        if (outcome == "paid" && amountText.isNotEmpty()) t("pay.paidAmount", mapOf("amount" to amountText))
        else t(when (outcome) { "paid" -> "pay.paid"; "failed" -> "pay.cancelled"; else -> "pay.timedOut" })

    fun paymentErrorText(code: Any?): String = when (code) {
        "not_configured" -> t("pay.notConfigured")
        "invalid_amount" -> t("pay.invalidAmount")
        "rate_limited" -> t("pay.rateLimited")
        "network", "timeout" -> t("pay.network")
        else -> t("pay.failed")
    }
}
