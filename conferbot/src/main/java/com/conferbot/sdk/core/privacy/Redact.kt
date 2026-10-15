package com.conferbot.sdk.core.privacy

/**
 * Sensitive details visitors type, masked before anything is stored or sent:
 * payment cards (Luhn), bank accounts (IBAN, mod-97), government ids,
 * passwords and one-time codes said in a sentence, secret keys. Emails and
 * phone numbers are never masked.
 *
 * A port of the web widget's conferbot-widget/src/ts/chat/privacy/redact.ts
 * and privacy/index.ts (maskRecordEntry, maskValue), by way of RN
 * src/core/privacy/redact.ts. The web applies it to the answer variables and
 * to the visitor's own record entries; the transcript keeps what was typed.
 * Owners switch it off with customizations.redactSensitive === false. The
 * embed server runs the same rules as a backstop - keep the three in step.
 */
object Redact {
    fun luhnOk(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    fun ibanOk(raw: String): Boolean {
        val s = raw.replace(Regex("\\s+"), "").uppercase()
        if (!Regex("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$").matches(s)) return false
        val moved = s.substring(4) + s.substring(0, 4)
        var rem = 0
        for (ch in moved) {
            val v = if (ch in 'A'..'Z') (ch.code - 55).toString() else ch.toString()
            for (c in v) rem = (rem * 10 + (c - '0')) % 97
        }
        return rem == 1
    }

    private class Rule(val kind: String, val re: Regex, val test: ((String) -> Boolean)? = null, val label: (MatchResult) -> String)

    private fun g(m: MatchResult, i: Int) = m.groups[i]?.value ?: ""

    /** Rules in order: each replaces what it finds with a short label. */
    private val RULES = listOf(
        // Payment cards: 13-19 digits, optionally in groups, passing Luhn.
        Rule(
            "card", Regex("\\b(?:\\d[ -]?){12,18}\\d\\b"),
            test = { m ->
                val d = m.replace(Regex("\\D"), "")
                d.length in 13..19 && luhnOk(d) && !Regex("^(\\d)\\1+$").matches(d)
            },
            label = { m -> "[card •••• ${m.value.replace(Regex("\\D"), "").takeLast(4)}]" }
        ),
        // Bank accounts (IBAN), with or without spaces.
        Rule("iban", Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){2,7}(?:[ ]?[A-Z0-9]{1,4})?\\b", RegexOption.IGNORE_CASE), test = { ibanOk(it) }, label = { "[bank account hidden]" }),
        // CVV / security code said with its name.
        Rule("cvv", Regex("\\b(cvv2?|cvc2?|security code)\\s*(?:is|:|=|-)?\\s*\\d{3,4}\\b", RegexOption.IGNORE_CASE), label = { m -> "${g(m, 1)} [hidden]" }),
        // Passwords and PINs said in a sentence: "my password is hunter2".
        Rule(
            "password",
            Regex("\\b(password|passcode|passwd|pwd|pin)\\b(\\s*(?:is|was|:|=|-)\\s*)(\"[^\"]{3,64}\"|'[^']{3,64}'|\\S{2,63}[^\\s.,;:!?)])", RegexOption.IGNORE_CASE),
            label = { m -> "${g(m, 1)}${g(m, 2)}[hidden]" }
        ),
        // One-time codes said in a sentence: "the code is 482913".
        Rule(
            "code",
            Regex("\\b((?:verification|one[- ]time|otp|2fa|login|security)\\s+code|otp)\\b(\\s*(?:is|:|=|-)?\\s*)(\\d{4,8})\\b", RegexOption.IGNORE_CASE),
            label = { m -> "${g(m, 1)}${g(m, 2)}[hidden]" }
        ),
        // US social security number.
        Rule("id", Regex("\\b(?!000|666|9\\d\\d)\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b"), label = { "[id hidden]" }),
        // UAE Emirates ID: 784-YYYY-NNNNNNN-N.
        Rule("id", Regex("\\b784[- ]?\\d{4}[- ]?\\d{7}[- ]?\\d\\b"), label = { "[id hidden]" }),
        // India Aadhaar: 12 digits in groups of 4 (first digit 2-9).
        Rule("id", Regex("\\b[2-9]\\d{3}[ -]\\d{4}[ -]\\d{4}\\b"), label = { "[id hidden]" }),
        // Secret keys and tokens people paste by mistake.
        Rule(
            "secret",
            Regex("\\b(?:sk|rk|pk)_(?:live|test)_[A-Za-z0-9]{16,}\\b|\\bsk-[A-Za-z0-9_-]{20,}\\b|\\bAKIA[0-9A-Z]{16}\\b|\\bgh[pousr]_[A-Za-z0-9]{30,}\\b|\\bxox[abprs]-[A-Za-z0-9-]{10,}\\b"),
            label = { "[secret key hidden]" }
        )
    )

    // Email addresses are left whole: digits in one are not a card number.
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    private fun maskPart(part: String, found: MutableList<String>): String {
        var out = part
        for (rule in RULES) {
            out = rule.re.replace(out) { m ->
                if (rule.test != null && !rule.test.invoke(m.value)) m.value
                else {
                    found.add(rule.kind)
                    rule.label(m)
                }
            }
        }
        return out
    }

    data class Result(val text: String, val found: List<String>)

    /** Mask what is sensitive in `text`: the text and the kinds masked. */
    fun redactText(text: String): Result {
        if (text.isEmpty()) return Result(text, emptyList())
        val found = mutableListOf<String>()
        val sb = StringBuilder()
        var last = 0
        for (m in EMAIL.findAll(text)) {
            sb.append(maskPart(text.substring(last, m.range.first), found)).append(m.value)
            last = m.range.last + 1
        }
        sb.append(maskPart(text.substring(last), found))
        return Result(sb.toString(), found.distinct())
    }

    /** Text fields of a visitor's record entry that are masked. */
    val TEXT_FIELDS = listOf("text", "message", "userInput", "value", "display", "answer")

    /** A value saved from an answer, masked when it is a string. */
    fun maskValue(value: Any?): Any? = if (value is String) redactText(value).text else value
}
