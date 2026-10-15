package com.conferbot.sdk.core.customization

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The greeting card by the closed launcher, as the web widget decides it
 * (conferbot-widget ts/visibility/nudges.ts greetingSettings / decideGreeting,
 * ts/home/firstImpression.ts, ts/home/greetingStyle.ts): which message, after
 * how long, what a tap does, how often, and how it is dressed. Pure.
 *
 * An app has no page address, so it counts as the site's home page ("/"): a
 * rule for every page applies, a rule for a given page does not. Rules that
 * need what only a browser knows (where the visitor came from, pages viewed,
 * the visitor's country, page attributes) never apply here.
 */
object Greeting {

    val STYLES = listOf("card", "note", "imessage", "postcard", "sticky", "typewriter", "glass", "notification", "neon", "voice", "aurora")
    const val DEFAULT_STYLE = "imessage"
    const val DEFAULT_DELAY = 3
    const val DEFAULT_FREQUENCY_DAYS = 7
    /** Closing the card (or opening the chat) rests it this long on "every page". */
    const val PAGE_COOLDOWN_SECONDS = 300
    private const val TEXT_MAX = 140
    private const val LEGACY_PROMPT_DELAY = 8
    private const val DAY_MS = 86_400_000L

    // The smart greetings' own ids (embed-server services/chatbot/greeting/smartGreetings.js).
    private val SMART_RULE_IDS = setOf("sgpric", "sgads0", "sgretn", "sgengd", "sgcust", "sgchko")

    data class Resolved(
        val text: String,
        /** Seconds after the launcher shows. */
        val delay: Int,
        /** "open", "send" or "link". */
        val click: String,
        val linkUrl: String,
        /** The rule's own suggested questions (the agent's are added by the caller). */
        val starters: List<String>,
        /** Results key: the rule's id or its words. */
        val key: String,
    )

    data class Facts(
        /** This visitor has been here before (a previous app session). */
        val returning: Boolean,
        /** The app identified the visitor (Conferbot.identify / initialize user). */
        val signedIn: Boolean,
        /** App sessions so far, this one included. */
        val visits: Int = 1,
        /** The visitor has chatted before. */
        val chatted: Boolean = false,
        /** Each rule's shows (ms) and last close, for its limits. */
        val shows: Map<String, List<Long>> = emptyMap(),
        val dismissed: Map<String, Long> = emptyMap(),
        val now: Long = System.currentTimeMillis(),
    )

    data class Seen(
        /** When the card last showed (ms). */
        val shownAt: Long? = null,
        /** When it was last closed or the chat opened from it (ms). */
        val closedAt: Long? = null,
        /** Shown in this app session already. */
        val shownThisSession: Boolean = false,
    )

    data class Head(val avatar: String?, val name: String, val online: Boolean)

    /** The launcher's colours for the phone text and the voice note: a solid or two gradient stops, the tail, the ink. */
    data class Bubble(val bg: List<String>, val tail: String, val fg: String)

    private fun str(v: Any?): String = v as? String ?: ""

    private fun num(v: Any?, fallback: Int, lo: Int, hi: Int): Int {
        val n = when (v) {
            null -> return fallback
            is Number -> v.toDouble()
            is String -> if (v.isEmpty()) return fallback else v.toDoubleOrNull()
            else -> null
        } ?: return fallback
        if (!n.isFinite()) return fallback
        return min(max(n.roundToInt(), lo), hi)
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>
    private fun list(v: Any?): List<Any?> = v as? List<Any?> ?: emptyList()

    fun normalizeStyle(v: Any?): String = (v as? String)?.takeIf { it in STYLES } ?: DEFAULT_STYLE

    /** A rule's results key: its id, else its words. */
    fun ruleKey(rule: Map<String, Any?>): String = str(rule["id"]).ifEmpty { str(rule["text"]).trim().take(40) }

    /** A rule's address pattern, asked of the app's "/" (nudges.ts ruleMatches). */
    private fun matchesHome(rule: Map<String, Any?>): Boolean {
        if (list(rule["patterns"]).isNotEmpty()) return false // a smart greeting's detected pages
        val p = str(rule["pattern"]).trim().lowercase()
        if (p.isEmpty()) return false
        return when (rule["matchType"]) {
            "glob" -> p in setOf("*", "**", "/*", "/**")
            "startsWith", "exact" -> p == "/"
            else -> "/".contains(p)
        }
    }

    private fun instant(v: Any?): Long? {
        val s = v as? String ?: return null
        if (s.isEmpty()) return null
        return try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun isUnset(v: Any?): Boolean = v == null || (v is Number && v.toDouble() == 0.0) || (v is List<*> && v.isEmpty())

    /** Who and when (nudges.ts audienceMatches, scheduleMatches); what an app cannot know never matches. */
    private fun applies(rule: Map<String, Any?>, facts: Facts): Boolean {
        if (!matchesHome(rule)) return false
        val visitors = str(rule["visitors"]).ifEmpty { "all" }
        if (visitors == "new" && facts.returning) return false
        if (visitors == "returning" && !facts.returning) return false
        val signedIn = str(rule["signedIn"]).ifEmpty { "any" }
        if (signedIn == "yes" && !facts.signedIn) return false
        if (signedIn == "no" && facts.signedIn) return false
        if (list(rule["countries"]).isNotEmpty()) return false
        val now = facts.now
        val start = instant(rule["startsAt"])
        val end = instant(rule["endsAt"])
        if (start != null && now < start) return false
        if (end != null && now > end) return false
        if (obj(rule["source"])?.isNotEmpty() == true) return false
        obj(rule["behavior"])?.let { b ->
            // Pages, time and paths are a browser's; visits and "chatted" an app has.
            if (b.any { (k, v) -> k != "minVisits" && k != "chatted" && !isUnset(v) }) return false
            val minVisits = (b["minVisits"] as? Number)?.toInt() ?: 0
            if (minVisits > 0 && facts.visits < minVisits) return false
            if (b["chatted"] == "yes" && !facts.chatted) return false
            if (b["chatted"] == "no" && facts.chatted) return false
        }
        if (list(rule["attributes"]).isNotEmpty()) return false
        // Its own limits: at most N shows in a window, quiet for days after a close.
        obj(rule["limits"])?.let { l ->
            val key = ruleKey(rule)
            val maxShows = (l["maxShows"] as? Number)?.toInt() ?: 0
            if (maxShows > 0) {
                val windowDays = (l["windowDays"] as? Number)?.toDouble()?.takeIf { it > 0 } ?: DEFAULT_FREQUENCY_DAYS.toDouble()
                val since = now - (windowDays * DAY_MS).toLong()
                if ((facts.shows[key] ?: emptyList()).count { it > since } >= maxShows) return false
            }
            val quiet = (l["quietDaysAfterDismiss"] as? Number)?.toDouble() ?: 0.0
            val d = facts.dismissed[key]
            if (quiet > 0 && d != null && d > 0 && now - d < quiet * DAY_MS) return false
        }
        return true
    }

    private fun isSmart(rule: Map<String, Any?>) = str(rule["id"]) in SMART_RULE_IDS
    private fun isNone(rule: Map<String, Any?>) = obj(rule["action"])?.get("type") == "none"

    /**
     * The greeting to show, or null: the highest-priority rule that applies
     * (a "show nothing" rule that applies silences it), else the default
     * message when the greeting is on.
     */
    fun decide(c: Map<String, Any?>?, facts: Facts): Resolved? {
        val raw = c ?: emptyMap()
        var on = raw["chatIconCta"] == true
        var text = str(raw["chatIconCtaText"]).trim()
        var delay = num(raw["greetingDelay"], DEFAULT_DELAY, 0, 3600)
        var click = (raw["greetingClickAction"] as? String)?.takeIf { it == "send" || it == "link" } ?: "open"
        // Smart greetings with the owner's own greeting off: no default message.
        if (raw["smartOnly"] == true && !on) {
            on = true
            text = ""
        }
        val rules = list(raw["greetingRules"]).mapNotNull { obj(it) }.toMutableList()
        // The old proactive teaser, carried over as the web does: its page rules
        // (never the app's "/"), and its every-page message as the default
        // message, or as a rule when the greeting already has one.
        val legacyText = if (raw["proactivePromptEnabled"] == true) str(raw["proactivePromptText"]).trim() else ""
        val legacyRules = list(raw["proactivePromptRules"]).mapNotNull { obj(it) }
            .filter { str(it["text"]).isNotBlank() && str(it["urlPattern"]).isNotBlank() }
        if (legacyRules.isNotEmpty() || legacyText.isNotEmpty()) {
            if (!on) text = ""
            if (legacyText.isNotEmpty()) {
                val legacyDelay = num(raw["proactivePromptDelay"], LEGACY_PROMPT_DELAY, 0, 3600)
                if (text.isNotEmpty()) {
                    rules.add(mapOf("matchType" to "contains", "pattern" to "/", "text" to legacyText, "delay" to legacyDelay, "clickAction" to "send"))
                } else {
                    text = legacyText
                    delay = legacyDelay
                    click = "send"
                }
            }
            on = true
        }
        if (!on) return null
        // "Show nothing" wins for the visitors it fits - a smart one yields to
        // the owner's own talking rule for the same visitor.
        val ownTalks = rules.any { !isSmart(it) && !isNone(it) && str(it["text"]).isNotBlank() && applies(it, facts) }
        if (rules.any { isNone(it) && applies(it, facts) && !(isSmart(it) && ownTalks) }) return null
        val ranked = rules.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Map<String, Any?>>> { (it.value["priority"] as? Number)?.toDouble() ?: 50.0 }.thenBy { it.index })
            .map { it.value }
        for (rule in ranked) {
            val ruleText = str(rule["text"]).trim()
            if (ruleText.isEmpty() || isNone(rule) || !applies(rule, facts)) continue
            val ruleClick = (rule["clickAction"] as? String)?.takeIf { it == "send" || it == "open" || it == "link" } ?: click
            return Resolved(
                text = ruleText.take(TEXT_MAX),
                delay = if (rule["delay"] is Number) num(rule["delay"], delay, 0, 3600) else delay,
                click = ruleClick,
                linkUrl = if (ruleClick == "link") str(rule["linkUrl"]) else "",
                starters = list(rule["starters"]).mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }.take(3),
                key = ruleKey(rule),
            )
        }
        if (text.isEmpty()) return null
        return Resolved(text.take(TEXT_MAX), delay, if (click == "link") "open" else click, "", emptyList(), text.take(40))
    }

    /** Whether the frequency lets the card show now (nudges.ts frequencyAllows). */
    fun frequencyAllows(c: Map<String, Any?>?, seen: Seen, now: Long = System.currentTimeMillis()): Boolean {
        val frequency = (c?.get("greetingFrequency") as? String)?.takeIf { it == "session" || it == "visitor" } ?: "page"
        val days = num(c?.get("greetingFrequencyDays"), DEFAULT_FREQUENCY_DAYS, 1, 365)
        return when (frequency) {
            "session" -> !seen.shownThisSession
            "visitor" -> seen.shownAt == null || now - seen.shownAt >= days * DAY_MS
            // Every page (each launch in an app): a close rests it a few minutes.
            else -> seen.closedAt == null || now - seen.closedAt >= PAGE_COOLDOWN_SECONDS * 1000L
        }
    }

    /** The card's header: the agent or bot's avatar and name, and "Online" unless the owner hid it. */
    fun head(c: Map<String, Any?>?, botName: String = ""): Head {
        val x = c ?: emptyMap()
        val avatar = str(x["avatar"]).takeIf { x["hideAvatar"] != true && it.isNotEmpty() }
            ?: str(x["initialPageAvatar"]).ifEmpty { null }
            ?: str(x["logo"]).takeIf { x["hideLogo"] != true && it.isNotEmpty() }
        return Head(avatar, str(x["logoText"]).ifEmpty { botName }.ifEmpty { "Support" }, x["hideOnlineStatus"] != true)
    }

    private fun channel(v: Double): Double {
        val s = v / 255
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: WidgetTheme.Rgba) = 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)

    private fun hex(c: WidgetTheme.Rgba): String =
        "#" + listOf(c.r, c.g, c.b).joinToString("") { it.roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }

    private fun gradientStops(c: Map<String, Any?>): Pair<String, String>? {
        val one = str(c["widgetGradientBgOne"])
        val two = str(c["widgetGradientBgTwo"])
        return if (c["widgetIconThemeType"] == "Gradient" && one.isNotEmpty() && two.isNotEmpty()) one to two else null
    }

    /** The card's accent (firstImpression.ts accentFor): the first bot colour that is not too light. */
    fun accent(c: Map<String, Any?>?): String {
        val x = c ?: emptyMap()
        val gradient = gradientStops(x)
        val brand = str(x["brandColor"]).takeIf { x["_themePalette"] == null && it.isNotEmpty() }
            ?: str(x["headerBgColor"]).ifEmpty { "#1b55f3" }
        val launcher = gradient?.toList() ?: listOf(str(x["widgetIconBgColor"]).ifEmpty { str(x["headerBgColor"]) }.ifEmpty { "#4f46e5" })
        for (v in listOf(brand, x["headerBgColor"]) + launcher) {
            val rgb = WidgetTheme.parseColor(v) ?: continue
            if (luminance(rgb) <= 0.55) return hex(rgb)
        }
        return "#4f46e5"
    }

    /** The launcher's colours for the phone text and the voice note (greetingStyle.ts). */
    fun bubble(c: Map<String, Any?>?): Bubble {
        val x = c ?: emptyMap()
        val gradient = gradientStops(x)
        val solidRaw = gradient?.first ?: x["widgetIconBgColor"]
        val solidRgb = WidgetTheme.parseColor(solidRaw)
        val solid = solidRgb?.let { hex(it) } ?: "#1b55f3"
        val fg = if (luminance(WidgetTheme.parseColor(solid)!!) > 0.6) "#111827" else "#ffffff"
        return if (gradient != null) Bubble(listOf(gradient.first, gradient.second), gradient.second, fg) else Bubble(listOf(solid), solid, fg)
    }

    /** The bot's own font for the card, or null for the system font (Arial and Gill Sans read as the default). */
    fun font(c: Map<String, Any?>?): String? =
        str(c?.get("font")).trim().takeIf { it.isNotEmpty() && !Regex("^(arial|gill sans)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
}
