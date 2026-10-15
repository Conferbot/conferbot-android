package com.conferbot.sdk.core.flow

/**
 * Text helpers the flow steps share, ported from the web widget
 * (conferbot-widget/src/ts/chat/nodeLogic.ts, richNodes.ts plainText,
 * utils/helpers.ts _stripHtmlAndDecodeEntities) by way of the React Native
 * SDK's src/core/flow/text.ts. Pure: no state.
 *
 * The flow-node spec (conferbot-react-native scripts/parity/flows,
 * nodes.spec.json engine.interpolation) is the reference: {x}, ${x}, {{x}},
 * ${ANSWERS.x} and $${x} fill from the answers; an unknown, null or "" value
 * shows nothing and the gap it leaves is tidied; values are HTML-escaped.
 */
typealias VariableLookup = (String) -> Any?

object FlowText {
    private fun plainScalar(v: Any?) = v == null || (v !is Map<*, *> && v !is List<*>)
    private fun plainList(v: Any?) = v is List<*> && v.all { plainScalar(it) }

    /**
     * A saved value as text: a list reads "a, b"; a matrix answer
     * ({row: column} or a list of columns per row) "Row: Col · Row2: Col2";
     * any other object is JSON.
     */
    fun variableText(value: Any?): String {
        if (plainList(value)) return (value as List<*>).joinToString(", ") { Js.str(it) }
        if (value is Map<*, *>) {
            val entries = value.entries
            if (entries.isNotEmpty() && entries.all { plainScalar(it.value) || plainList(it.value) }) {
                return entries.joinToString(" · ") { (k, v) -> "$k: ${if (v is List<*>) v.joinToString(", ") { Js.str(it) } else Js.str(v)}" }
            }
        }
        return if (value is Map<*, *> || value is List<*>) Js.stringify(value) else Js.str(value)
    }

    /** Text made safe inside HTML. */
    fun escapeHtmlText(value: Any?): String = Js.str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /** {x}, ${x}, $${x}, {{x}} and ${ANSWERS.x}: group 1/3 the "$" prefix, 2/4 the key. */
    private val PLACEHOLDER = Regex("(\\$\\$|\\$)?\\{\\{\\s*((?:ANSWERS\\.)?\\w+)\\s*\\}\\}|(\\$\\$|\\$)?\\{\\s*((?:ANSWERS\\.)?\\w+)\\s*\\}")
    private const val GAP = "\u0000"

    private val GAP1 = Regex("(^|>)[ \\t ]*\u0000+[ \\t ]*[,;:]?[ \\t ]*")
    private val GAP2 = Regex("[ \\t ]*\u0000+(?=[ \\t ]*([,.!?;:)]|<|$))")
    private val GAP3 = Regex("([ \\t ])\u0000+[ \\t ]+")

    /** A gap left by an empty placeholder, tidied ("Hey ${name}, welcome" -> "Hey, welcome"). */
    private fun closeGaps(text: String): String =
        if (!text.contains(GAP)) text
        else text.replace(GAP1, "$1").replace(GAP2, "").replace(GAP3, "$1").replace(GAP, "")

    private fun lookupKey(lookup: VariableLookup, key: String): Any? {
        var value = lookup(key)
        if (value == null && key.startsWith("ANSWERS.")) value = lookup(key.substring(8))
        return value
    }

    /** Fill the placeholders in owner text from the answers (web nodeLogic.fillVariables). "raw" leaves values unescaped. */
    fun fillVariables(text: Any?, lookup: VariableLookup, raw: Boolean = false): String {
        val filled = PLACEHOLDER.replace(Js.str(text)) { m ->
            val prefix = m.groups[1]?.value ?: m.groups[3]?.value
            val key = m.groups[2]?.value ?: m.groups[4]?.value ?: ""
            val value = lookupKey(lookup, key)
            if (value == null || value == "") GAP
            else {
                val shown = if (raw) variableText(value) else escapeHtmlText(variableText(value))
                if (prefix == "$$") "$$shown" else shown
            }
        }
        return closeGaps(filled)
    }

    /**
     * Fill the variables in a link: a value in the query or hash is URL-encoded,
     * one before it goes in as is; an unknown one stays as written.
     */
    fun fillLinkVariables(link: Any?, lookup: VariableLookup): String {
        val text = Js.str(link).trim()
        val out = StringBuilder()
        var last = 0
        for (m in PLACEHOLDER.findAll(text)) {
            out.append(text, last, m.range.first)
            last = m.range.last + 1
            val key = m.groups[2]?.value ?: m.groups[4]?.value ?: ""
            val value = lookupKey(lookup, key)
            if (value == null) {
                out.append(m.value)
                continue
            }
            val shown = variableText(value).trim()
            val inQuery = out.contains('?') || out.contains('#')
            val prefix = m.groups[1]?.value ?: m.groups[3]?.value
            out.append(if (prefix == "$$") "$" else "").append(if (inQuery) Js.encodeURIComponent(shown) else shown)
        }
        return out.append(text.substring(last)).toString()
    }

    /** The visitor's first name, capitalised: "deepak gopal" -> "Deepak". */
    fun firstNameOf(value: Any?): String {
        val first = Js.str(value).trim().split(Regex("\\s+")).firstOrNull() ?: ""
        return if (first.isNotEmpty()) first.substring(0, 1).uppercase() + first.substring(1) else ""
    }

    /** True when owner HTML shows nothing ("<p><br></p>"). */
    fun isBlankHtml(html: Any?): Boolean {
        val s = Js.str(html)
        if (Regex("<(img|iframe|video|audio|svg)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)) return false
        return s.replace(Regex("</?[^>]+(>|$)"), "")
            .replace(Regex("&nbsp;|&#160;| |\ufeff|&#xFEFF;", RegexOption.IGNORE_CASE), "")
            .trim() == ""
    }

    /**
     * The ask-name greeting by first name: none set at all is the default
     * "Nice to meet you, X"; one cleared in the builder is "" (no bubble).
     */
    fun nameGreetingHtml(template: Any?, name: Any?): String {
        val first = escapeHtmlText(firstNameOf(name))
        if (template == null) return if (first.isNotEmpty()) "Nice to meet you, $first" else ""
        if (isBlankHtml(template)) return ""
        return closeGaps(Js.str(template).replace(Regex("\\$?\\{\\{?\\s*name\\s*\\}?\\}")) { first.ifEmpty { GAP } })
    }

    /** Owner HTML as one line of plain text (web richNodes.plainText). */
    fun plainText(value: Any?): String = Js.str(value)
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("</?[^>]+(>|$)"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Tags stripped and entities decoded, trimmed (web helpers _stripHtmlAndDecodeEntities). */
    fun stripHtmlAndDecode(html: Any?): String {
        if (html == null || html == "" || html == false) return ""
        return Js.str(html)
            .replace(Regex("</?[^>]+(>|$)"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace(Regex("&#x([0-9A-Fa-f]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .trim()
    }

    /**
     * Owner HTML as the text a native bubble shows: paragraphs and <br> keep
     * their line breaks, tags go, entities are decoded.
     */
    fun bubbleText(html: Any?): String = Js.str(html)
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * Owner HTML for the HTML step, made safe: no <script>, no on* handlers,
     * no javascript:/vbscript: URLs (web nodeLogic.sanitizeOwnerHtml).
     */
    fun sanitizeOwnerHtml(html: Any?): String = Js.str(html)
        .replace(Regex("<script\\b[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<script\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<(object|embed|base|meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex("\\s(href|src|action|formaction|xlink:href)\\s*=\\s*(\"|')?\\s*(javascript|vbscript|data:text/html)[^\"'\\s>]*(\"|')?", RegexOption.IGNORE_CASE),
            " $1=\"#\""
        )

    /** The field holding the text a step shows, by type (web nodeLogic PROMPT_FIELD). */
    val PROMPT_FIELD: Map<String, String> = mapOf(
        "welcome-node" to "text",
        "message-node" to "text",
        "ask-name-node" to "questionText",
        "ask-email-node" to "questionText",
        "ask-phone-number-node" to "questionText",
        "ask-number-node" to "questionText",
        "ask-url-node" to "questionText",
        "ask-location-node" to "questionText",
        "ask-custom-question-node" to "questionText",
        "ask-file-node" to "questionText",
        "ask-multiple-questions-node" to "message",
        "calendar-node" to "calendarPrompt",
        "n-choices-node" to "choicePrompt",
        "n-select-option-node" to "selectPrompt",
        "n-check-options-node" to "checkOptionsPrompt",
        "image-choice-node" to "questionText",
        "carousel-node" to "questionText",
        "rating-choice-node" to "questionText",
        "yes-or-no-choice-node" to "questionText",
        "opinion-scale-choice-node" to "questionText",
        "user-redirect-node" to "redirectPrompt",
        "navigate-node" to "navigatePrompt",
        "quiz-node" to "quizPrompt",
        "human-handover-node" to "handoverMessage",
        "dynamic-choices-node" to "questionText",
        "consent-node" to "questionText",
        "product-card-node" to "questionText",
        "slider-node" to "questionText",
        "datetime-node" to "questionText",
        "ranking-node" to "questionText",
        "otp-node" to "questionText",
        "address-node" to "questionText",
        "matrix-node" to "questionText",
        "ai-extract-node" to "questionText",
        "ai-route-node" to "questionText",
        "kb-answer-node" to "questionText",
        "signature-node" to "questionText"
    )

    /** A step's own text with the variables filled in; null when it shows none. */
    fun renderedTextFor(type: String, data: Map<String, Any?>, lookup: VariableLookup): String? {
        val field = PROMPT_FIELD[type] ?: return null
        val raw = data[field] as? String ?: return null
        if (raw.trim().isEmpty()) return null
        return fillVariables(raw, lookup)
    }

    /** A message with no text or an image with no picture is skipped (no typing). */
    fun isEmptyContentStep(type: String, data: Map<String, Any?>): Boolean = when (type) {
        "message-node" -> isBlankHtml(data["text"])
        "image-node" -> Js.str(data["image"]).trim() == ""
        else -> false
    }

    /** Ticked options in list order, not click order. */
    fun inListOrder(picked: List<String>, list: List<String>): List<String> {
        fun rank(v: String): Int = list.indexOf(v).let { if (it == -1) Int.MAX_VALUE else it }
        return picked.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
    }

    private fun clamp(n: Double, lo: Double, hi: Double) = Math.max(lo, Math.min(hi, n))

    /** A step's length for pacing: its message or question, tags stripped. */
    private fun wordsOf(data: Map<String, Any?>?): Int {
        val d = data ?: emptyMap()
        val raw = d["text"] ?: d["questionText"] ?: d["calendarPrompt"] ?: d["message"] ?: ""
        return Js.str(raw).replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim().length
    }

    /**
     * How long the typing dots show before a bot step (web nodeLogic.paceFor):
     * after the visitor spoke, clamp(500 + len*12, 700, 1800); right after a
     * step that moved on by itself, time to read it, then a shorter beat.
     */
    fun paceFor(previous: Map<String, Any?>?, next: Map<String, Any?>?, followsBotStep: Boolean): Long {
        val nextLen = wordsOf(next)
        if (!followsBotStep) return Math.round(clamp(500.0 + nextLen * 12, 700.0, 1800.0))
        val read = clamp(wordsOf(previous) * 22.0, 350.0, 2200.0)
        return Math.round(read + clamp(250.0 + nextLen * 6, 300.0, 900.0))
    }

    private val AUTO_ADVANCE_TYPES = setOf(
        "welcome-node", "message-node", "image-node", "video-node", "audio-node", "file-node", "html-node", "user-redirect-node"
    )

    /** True when the step before was a bot step that moved on by itself. */
    fun followsAutoAdvanceStep(previousType: String?): Boolean = previousType != null && previousType in AUTO_ADVANCE_TYPES
}
