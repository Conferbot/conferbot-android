package com.conferbot.sdk.core.state

import java.util.Locale

/**
 * The visitor's language, sent with every response record as the web
 * widget's `visitorInsights.language.browser` (navigator.language,
 * conferbot-widget ts/visitors/insights.ts). The server reads it
 * (embed-server socket/handlers/messageHandler.js, `browserLanguage`) so the
 * AI answers a message too short to tell ("ok", "hi") in the visitor's own
 * language. An app has no page, so `language.page` and the browsing history
 * the web also sends are never set. Ported from the React Native SDK's
 * src/core/deviceLanguage.ts.
 *
 * Pure apart from [deviceLocale], which reads the app's default locale
 * (Locale.getDefault: the first of the phone's languages; no LocaleList,
 * which needs API 24).
 */
object DeviceLanguage {

    // The web's own cut: whitespace collapsed, trimmed, at most n characters.
    private fun cut(v: Any?, n: Int): String = (v?.toString() ?: "").replace(Regex("\\s+"), " ").trim().take(n)

    private val LANGUAGE = Regex("^[A-Za-z]{2,3}(-|$)")
    private val UNICODE_EXT = Regex("-u-", RegexOption.IGNORE_CASE)

    /**
     * A locale as the browser writes it: BCP 47 ("en-US"), not "en_US" or
     * "en_US@calendar=..." / "en-US-u-ca-...". "" when it is not a locale.
     */
    fun normalize(raw: Any?): String {
        val s = cut(raw, 64).substringBefore('@').replace('_', '-')
        if (!LANGUAGE.containsMatchIn(s)) return ""
        val ext = UNICODE_EXT.find(s)?.range?.first ?: -1
        return cut(if (ext > 0) s.substring(0, ext) else s, 16)
    }

    /** The first of these that is a locale, or "". */
    fun localeOf(vararg candidates: Any?): String {
        for (c in candidates) {
            val locale = normalize(c)
            if (locale.isNotEmpty()) return locale
        }
        return ""
    }

    /**
     * The response record's `visitorInsights`: the web's snapshot shape with
     * only the language. Null when the language is unknown.
     */
    fun visitorInsightsOf(locale: String?): Map<String, Any?>? {
        val browser = cut(locale, 16)
        return if (browser.isEmpty()) null else mapOf("v" to 1, "language" to mapOf("browser" to browser))
    }

    /** The device's language now ("de-DE"), or "" when nothing says. */
    fun deviceLocale(): String {
        val locale = Locale.getDefault()
        return localeOf(runCatching { locale.toLanguageTag() }.getOrNull(), locale.toString())
    }
}
