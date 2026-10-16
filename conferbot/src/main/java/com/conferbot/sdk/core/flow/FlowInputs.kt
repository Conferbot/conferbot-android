package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.i18n.I18n

/**
 * Checks on what the visitor types or picks, ported from the web widget
 * (conferbot-widget/src/ts/chat/nodeKit.ts, nodeLogic.ts, utils/emailTypos.ts)
 * with its messages (ts/i18n) in the active language, by way of RN
 * src/core/flow/inputs.ts.
 */
object FlowInputs {
    /** The steps' plain texts by key (web i18n: validation.*, upload.*, common.*). */
    private val EN_KEYS = listOf(
        "validation.email", "validation.phone", "validation.numberLike", "validation.link",
        "validation.number", "validation.url", "upload.tapRetry", "common.skipped", "common.done", "common.somethingWrong",
    )

    /**
     * The texts the steps say, in the active language at each read (the name
     * stays from when they were English only).
     */
    object EN {
        val emailInvalid: String get() = I18n.t("validation.email")
        fun emailTypo(email: String) = I18n.t("validation.emailTypo", "email" to email)
        val phoneInvalid: String get() = I18n.t("validation.phone")
        val numberLike: String get() = I18n.t("validation.numberLike")
        fun numberBetween(min: Any?, max: Any?) = I18n.t("validation.numberBetween", "min" to show(min), "max" to show(max))
        fun numberMin(min: Any?) = I18n.t("validation.numberMin", "min" to show(min))
        fun numberMax(max: Any?) = I18n.t("validation.numberMax", "max" to show(max))
        val link: String get() = I18n.t("validation.link")
        val numberInvalid: String get() = I18n.t("validation.number")
        val urlInvalid: String get() = I18n.t("validation.url")
        fun uploadOverSize(mb: Double) = I18n.t("upload.overSize", "mb" to Js.num(mb))
        fun uploadNotAllowed(types: String) = I18n.t("upload.notAllowed", "types" to types)
        val uploadRetry: String get() = I18n.t("upload.tapRetry")
        val skipped: String get() = I18n.t("common.skipped")
        val done: String get() = I18n.t("common.done")
        val somethingWrong: String get() = I18n.t("common.somethingWrong")
        private fun show(v: Any?) = if (v is Double) Js.num(v) else Js.str(v)
    }

    /**
     * The owner's own error text, or the default when they left it empty or kept
     * the stock English (web i18n ownerTextOr). [fallback] is drawn in the
     * active language; its English is stock too.
     */
    fun ownerTextOr(value: Any?, fallback: String, stockEnglish: List<String> = emptyList()): String {
        val v = if (value is String) FlowText.plainText(value) else ""
        val key = EN_KEYS.firstOrNull { I18n.t(it) == fallback }
        val stock = listOf(fallback) + listOfNotNull(key?.let { I18n.tFor(I18n.EN, it) }) + stockEnglish
        if (v.isEmpty() || stock.any { FlowText.plainText(it) == v }) return fallback
        return value as String
    }

    private val EMAIL_RE = Regex(
        "^(([^<>()\\[\\]\\\\.,;:\\s@\"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@\"]+)*)|(\".+\"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$"
    )

    fun isValidEmail(value: Any?): Boolean = EMAIL_RE.matches(Js.str(value).trim().lowercase())

    /** "+1 (555) 123-4567" -> "+15551234567"; a leading "00" becomes "+". */
    fun normalizePhone(value: Any?): String {
        val raw = Js.str(value).trim()
        val plus = if (raw.startsWith("+") || raw.startsWith("00")) "+" else ""
        var digits = raw.replace(Regex("[\\s().\\-/ ]"), "").replace(Regex("^\\+"), "")
        if (plus.isNotEmpty() && digits.startsWith("00")) digits = digits.substring(2)
        return plus + digits
    }

    fun isValidPhone(value: Any?): Boolean = Regex("^\\+?\\d{6,15}$").matches(normalizePhone(value))

    /** "1,200" -> 1200, "3,5" -> 3.5; anything else null. */
    fun parseNumberInput(value: Any?): Double? {
        val raw = Js.str(value).trim().replace(Regex("[\\s_' ]"), "")
        if (!Regex("^[-+]?(\\d+|\\d{1,3}(,\\d{3})+)?([.,]\\d+)?$").matches(raw) || !Regex("\\d").containsMatchIn(raw)) return null
        val normalized = if (Regex("^[-+]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$").matches(raw)) raw.replace(",", "") else raw.replaceFirst(",", ".")
        val n = Js.number(normalized)
        return if (Js.isFinite(n)) n else null
    }

    /** A link people would type: no spaces, a dot, a 2+ letter ending. */
    fun isValidLink(value: Any?): Boolean {
        val raw = Js.str(value).trim()
        if (raw.isEmpty() || Regex("\\s").containsMatchIn(raw)) return false
        val host = raw.replace(Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE), "").split(Regex("[/?#]"))[0]
        return Regex("^[^.@]+(\\.[^.]+)*\\.[a-z]{2,}(:\\d+)?$", RegexOption.IGNORE_CASE).matches(host.replace(Regex("^[^@]*@"), ""))
    }

    /** "conferbot.com" -> "https://conferbot.com"; mailto/tel kept; other schemes refused. */
    fun linkHref(value: Any?): String {
        val raw = Js.str(value).trim()
        if (raw.isEmpty() || Regex("\\s").containsMatchIn(raw)) return ""
        if (Regex("^(https?://|mailto:|tel:)", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return raw
        if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(raw) || raw.startsWith("//")) return ""
        return if (isValidLink(raw)) "https://$raw" else ""
    }

    private fun numOrNull(v: Any?): Double? {
        if (v == null || v == "") return null
        val n = Js.number(v)
        return if (Js.isFinite(n)) n else null
    }

    /** ask-number-node: the refusal message, or null when the answer is fine. */
    fun numberProblem(value: Any?, data: Map<String, Any?>?): String? {
        val n = parseNumberInput(value)
        val cond = Js.map(data?.get("enableConditions")) ?: emptyMap()
        val min = numOrNull(cond["minVal"])
        val max = numOrNull(cond["maxVal"])
        val message: String? = when {
            n == null -> EN.numberLike
            min != null && max != null && (n < min || n > max) -> EN.numberBetween(min, max)
            min != null && n < min -> EN.numberMin(min)
            max != null && n > max -> EN.numberMax(max)
            else -> null
        }
        if (message == null) return null
        val custom = Js.str(data?.get("invalidNumberResponse")).replace(Regex("</?[^>]+(>|$)"), "").trim()
        if (custom.isEmpty()) return message
        fun show(v: Any?) = if (v == null || v == "") "" else Js.str(v)
        return custom.replace("{min}", show(cond["minVal"])).replace("{max}", show(cond["maxVal"]))
    }

    /** The owner's phone error (new or older field name), or the default. */
    fun phoneErrorText(data: Map<String, Any?>?): String {
        fun textOf(v: Any?) = Js.str(v).replace(Regex("</?[^>]+(>|$)"), "").trim()
        return ownerTextOr(textOf(data?.get("invalidPhoneResponse")).ifEmpty { textOf(data?.get("incorrectPhoneNumberResponse")) }, EN.phoneInvalid)
    }

    // ----- e-mail typos (utils/emailTypos.ts) -----
    private val DOMAINS = listOf(
        "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.uk", "hotmail.com", "hotmail.co.uk", "outlook.com", "live.com", "msn.com",
        "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com", "gmx.com", "gmx.de", "web.de", "yandex.com", "mail.com", "zoho.com"
    )
    private val TLD_FIXES = mapOf("con" to "com", "cmo" to "com", "ocm" to "com", "comm" to "com", "nt" to "net", "orgg" to "org")

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1).also { it[0] = i } }
        for (j in 1..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** "jamie@gmial.com" -> "jamie@gmail.com"; null when the address looks right. */
    fun suggestEmailFix(email: String): String? {
        val m = Regex("^([^\\s@]+)@([^\\s@]+)$").find(email.trim().lowercase()) ?: return null
        val user = m.groupValues[1]
        val domain = m.groupValues[2]
        if (domain in DOMAINS) return null
        var best: String? = null
        var bestD = 3
        DOMAINS.forEach { d ->
            val dist = distance(domain, d)
            if (dist < bestD && dist <= (if (d.length > 8) 2 else 1)) {
                bestD = dist
                best = d
            }
        }
        if (best != null) return "$user@$best"
        val parts = domain.split(".")
        val tld = parts.last()
        if (parts.size >= 2 && TLD_FIXES.containsKey(tld)) return "$user@${(parts.dropLast(1) + TLD_FIXES.getValue(tld)).joinToString(".")}"
        return null
    }

    // ----- files -----
    const val DEFAULT_MAX_FILE_MB = 5.0

    /** The allowed types in words: "PNG or IMAGE/JPEG", "images or PDF". */
    fun fileTypesWords(types: Any?): String {
        val list = Js.list(types).map { Js.str(it).trim().lowercase() }.filter { it.isNotEmpty() }.map { x ->
            if (x.endsWith("/")) when (x) {
                "image/" -> I18n.t("file.images")
                "video/" -> I18n.t("file.videos")
                else -> x.replace("/", "")
            } else x.replace(Regex("^\\."), "").replace(Regex("^application/"), "").uppercase()
        }.map { if (it == "JPEG") "JPG" else it }
        val unique = list.distinct()
        if (unique.isEmpty()) return ""
        return if (unique.size == 1) unique[0] else "${unique.dropLast(1).joinToString(", ")} ${I18n.t("common.or")} ${unique.last()}"
    }

    data class PickedFile(val name: String?, val type: String?, val size: Double?)

    /** Why a picked file can't be sent, or null (web nodeLogic.fileUploadProblem). */
    fun fileUploadProblem(file: PickedFile, data: Map<String, Any?>?): String? {
        val mbSetting = Js.number(data?.get("maxFileSizeMB"))
        val maxMb = if (mbSetting > 0) mbSetting else DEFAULT_MAX_FILE_MB
        if ((file.size ?: 0.0) > maxMb * 1024 * 1024) return EN.uploadOverSize(maxMb)
        val allowed = Js.list(data?.get("allowedFileTypes")).map { Js.str(it).trim().lowercase() }.filter { it.isNotEmpty() }
        if (allowed.isEmpty()) return null
        val name = (file.name ?: "").lowercase()
        val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
        val mime = (file.type ?: "").lowercase()
        val ok = allowed.any { t ->
            if (t.contains('/')) {
                if (t.endsWith("/") || t.endsWith("/*")) mime.startsWith(t.removeSuffix("*")) else mime == t
            } else ext == t.removePrefix(".")
        }
        return if (ok) null else EN.uploadNotAllowed(fileTypesWords(allowed).ifEmpty { allowed.joinToString(", ") })
    }

    /** Optional upload: flagged, or "(optional)" in the question. */
    fun isOptionalUpload(data: Map<String, Any?>?): Boolean =
        data?.get("optional") == true || Regex("\\(optional\\)", RegexOption.IGNORE_CASE).containsMatchIn(Js.str(data?.get("questionText")))
}
