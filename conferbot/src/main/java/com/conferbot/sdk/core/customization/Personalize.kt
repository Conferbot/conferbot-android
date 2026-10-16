package com.conferbot.sdk.core.customization

/**
 * Personalised texts: "Hi {{first_name|there}} 👋" -> "Hi Sam 👋" (or
 * "Hi there 👋"). Ported from the React Native SDK's src/core/personalize.ts,
 * itself from the web widget's src/ts/layout/ownerLayout.ts (visitorVars,
 * personalize, PERSONALIZED_KEYS, personalizeCustomizations), applied right
 * after the bot's data arrives. This is the dashboard's "Greet by name"
 * (Customize > Welcome).
 *
 * One difference, on purpose: the web HTML-escapes the values because it
 * draws these texts as markup; the app draws them as text, so they stay as
 * typed. Pure.
 */
object Personalize {

    private fun firstString(vararg values: Any?): String {
        for (v in values) if (v is String && v.isNotBlank()) return v.trim()
        return ""
    }

    /**
     * What a visitor is known by: the app's user ([context]: its metadata
     * and name, as the web's identify data) and a name the chat already
     * asked for ([askedName]).
     */
    fun visitorVars(context: Map<String, Any?>?, askedName: String? = null): Map<String, String> {
        val c = context ?: emptyMap()
        val parts = listOf(c["firstName"] ?: c["first_name"], c["lastName"] ?: c["last_name"])
            .mapNotNull { (it as? String)?.takeIf { s -> s.isNotEmpty() } }
            .joinToString(" ")
        val full = firstString(c["name"], c["fullName"], c["full_name"], askedName, parts)
        return mapOf(
            "name" to full,
            "first_name" to firstString(c["firstName"], c["first_name"], full.split(Regex("\\s+")).firstOrNull()),
            "company" to firstString(c["company"], c["companyName"], c["company_name"], c["organization"])
        )
    }

    /** The app's user as [visitorVars] reads it: `{ ...metadata, name }`. */
    fun userContext(metadata: Map<String, Any?>?, name: String?): Map<String, Any?>? {
        if (metadata == null && name.isNullOrBlank()) return null
        return (metadata ?: emptyMap()) + (if (!name.isNullOrBlank()) mapOf("name" to name) else emptyMap())
    }

    // {{ key }} or {{ key | fallback }}. Every brace is escaped: Android's
    // ICU regex refuses a bare "}" that the JVM accepts (#70).
    private val TOKEN = Regex("\\{\\{\\s*([a-zA-Z_]+)\\s*(?:\\|([^\\}]*))?\\}\\}")
    private const val DROP = '\u0000'
    private val DROPPED = Regex("[ \\t]*\\u0000")

    /**
     * Fill the tokens. A token with no value and no fallback is dropped along
     * with the space before it ("Hi {{name}}!" -> "Hi!"); the fallback is the
     * owner's own text.
     */
    fun personalize(text: String?, vars: Map<String, String>): String? {
        if (text == null || !text.contains("{{")) return text
        return text
            .replace(TOKEN) { m ->
                val key = m.groupValues[1].lowercase().let { if (it == "firstname") "first_name" else it }
                val v = vars[key]
                when {
                    !v.isNullOrEmpty() -> v
                    m.groups[2] != null -> m.groupValues[2].trim()
                    else -> DROP.toString()
                }
            }
            .replace(DROPPED, "")
            .replace(Regex("[ \\t]{2,}"), " ")
    }

    /** The visitor-facing texts that take {{name}} / {{first_name}} / {{company}}. */
    val PERSONALIZED_KEYS = listOf("chatIconCtaText", "initialPageTitle", "initialPageDescription", "initialPageCardButtonText")

    /**
     * The bot's settings with those texts (and each greeting rule's text)
     * filled in. The same map comes back when nothing in it changes.
     */
    @Suppress("UNCHECKED_CAST")
    fun customizations(c: Map<String, Any?>, vars: Map<String, String>): Map<String, Any?> {
        var out: MutableMap<String, Any?>? = null
        fun put(k: String, v: Any?) {
            if (out == null) out = LinkedHashMap(c)
            out!![k] = v
        }
        PERSONALIZED_KEYS.forEach { k ->
            val v = c[k] as? String ?: return@forEach
            val p = personalize(v, vars)
            if (p != v) put(k, p)
        }
        val rules = c["greetingRules"] as? List<*>
        if (rules != null && rules.any { ((it as? Map<*, *>)?.get("text") as? String)?.contains("{{") == true }) {
            put("greetingRules", rules.map { r ->
                val m = r as? Map<*, *>
                val t = m?.get("text") as? String
                if (m != null && t != null && t.contains("{{")) (m as Map<Any?, Any?>) + ("text" to personalize(t, vars)) else r
            })
        }
        return out ?: c
    }
}
