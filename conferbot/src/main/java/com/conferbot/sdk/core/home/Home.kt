package com.conferbot.sdk.core.home

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.customization.Customizations
import com.conferbot.sdk.core.customization.MascotArt
import com.conferbot.sdk.core.customization.ThemeTokens
import com.conferbot.sdk.core.livechat.ReplyTime
import com.conferbot.sdk.core.livechat.ReplyTone
import com.conferbot.sdk.core.requests.RequestState
import com.conferbot.sdk.core.requests.RequestSummary
import com.conferbot.sdk.core.requests.Requests
import com.conferbot.sdk.core.waiting.WaitArticles
import com.conferbot.sdk.core.waiting.WaitArticles.HelpArticle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Date

/**
 * The home screen: what the visitor sees when the chat opens, before they
 * have said anything, and the shortest path from there to an answer.
 * Ported from the React Native SDK's src/core/home.ts, itself from the web
 * widget (all under conferbot-widget/src/):
 *
 *   ts/home/firstImpression.ts  enhanceHome, buildHelpCard: what shows and in what order
 *   ts/home/homeCopy.ts          honest words for early bots' placeholder copy
 *   ts/home/homeUtils.ts         salutation, topCategories, isLight, homeBrand, homeCardAvatar
 *   ts/home/askBox.ts            "Ask anything": askBoxOn, askPlaceholder, the list as rows
 *   ts/home/trust.ts             the team's faces, the rating, the customer logos
 *   ts/home/visitorMemory.ts     "Welcome back, Jamie", "Pick up where you left off"
 *   ts/home/requests/card.ts     "Your requests" on the home screen
 *   ts/chat/replyTimeView.ts     decorateHomeReply: the card's status line
 *   core/helpCentre.ts           showsRecentArticles, publishedArticles
 *   templates/shared.ts          generateInitialPage: the template the above decorate
 *   core/customizations.ts       enableInitialPage, initialPageCardButtonText, homeAskBox...
 *   ts/i18n/en.ts                home.*, trust.*, requests.* (English)
 *
 * The home exists only when the owner turned it on (`enableInitialPage`);
 * with it off the chat opens straight on the conversation, as the web's
 * templates and core/state.ts decide.
 *
 * Not in an app, on purpose: the page-aware parts (ts/home/pageAware.ts -
 * "For this page" articles and a greeting page rule's own questions; an app
 * has no page address or title, so the agent's own questions are used), the
 * help card's own search's "For this page", and the floating panel's
 * three-article cap (wider than 500px only). The web's home draws no mascot
 * (it appears on the launcher, the greeting card, the typing bubble and the
 * idle call); [HomeModel.mascot] is the owner's, for an app's own spot
 * (`HomeScreenView(mascot = { m -> Mascot(m.look, m.accent) })`).
 *
 * Pure: everything the view needs comes out of [Home.model]. The help
 * centre is the raw `knowledgeBaseData` of fetched-chatbot-data (lists of
 * maps), as [WaitArticles] reads it.
 */

/** The views of the open chat. */
enum class HomeView(val key: String) { HOME("home"), CHAT("chat"), HELP("help") }

/** What the device remembers for "Welcome back" (web visitorMemory.ts). */
data class VisitorMemory(
    /** Last time they wrote. */
    val at: Long,
    val name: String? = null,
    /** The first real question of their latest conversation. */
    val topic: String? = null,
    /** The conversation that question came from. */
    val session: String? = null
)

/** The bot's AI agent (a no-flow agent bot). */
data class HomeAgent(val displayName: String? = null, val starters: List<String> = emptyList(), val avatar: String? = null)

/** A run of text, marked where it matches the question (web kbUtils.ts highlight). */
data class TextRun(val text: String, val mark: Boolean = false)

/** A row under the ask box. */
sealed class AskRow {
    abstract val key: String
    /** Ask the agent this question: the chat opens and asks. */
    data class Ask(override val key: String, val query: String, val sub: String) : AskRow()
    data class Label(override val key: String, val text: String) : AskRow()
    data class Article(override val key: String, val id: String, val title: List<TextRun>, val sub: String) : AskRow()
    data class None(override val key: String, val text: String) : AskRow()
    /** A flow bot with nothing found: open the chat. */
    data class Chat(override val key: String, val title: String, val sub: String) : AskRow()
}

/** Matches inside article text from the server, for the query they were asked for. */
data class RemoteHits(val query: String, val hits: List<Pair<String, String>>)

/** The help card's own search, when the ask box is off (web buildHelpCard render). */
sealed class HelpSearch {
    data class Row(val id: String, val title: List<TextRun>, val sub: String)
    data class Results(val rows: List<Row>) : HelpSearch()
    data class Empty(val text: String, val action: String, val asks: Boolean) : HelpSearch()
}

data class HomeCopy(
    /** Who answers: the owner's card title, else the bot's (or agent's) name. */
    val cardTitle: String,
    val cardTagline: String,
    val description: String,
    /** Whether the tagline is ours (so the status line replaces it). */
    val online: Boolean
)

/** The home screen's surfaces (the widget theme's, else the classic widget's). */
data class HomePalette(val page: String, val card: String, val soft: String, val ink: String, val muted: String, val line: String)

data class HomeRating(val average: String, val count: String, val fill: Double, val label: String)

data class HomeArticleRow(
    val id: String,
    val title: String,
    val description: String,
    /** The cover image, when it has one (else the document icon). */
    val image: String?
)

data class HomeRequestRow(val id: String, val title: String, val line: String, val state: RequestState, val fresh: Boolean)

enum class HomeSection { ASK, RESUME, REQUESTS, TALK, HELP, TRUST }

/** The owner's mascot and the colour it is drawn in when they chose none (the launcher's). */
data class HomeMascot(val look: MascotArt.Look = MascotArt.Look(), val accent: String = "#4f46e5")

data class HomeModel(
    /** "Good afternoon", or "Welcome back, Jamie". */
    val hello: String,
    val brand: Brand?,
    /** The owner's title (may be empty, as the web prints it). */
    val title: String,
    val description: String,
    /** The owner's line above the card (initialPageMessage), or "". */
    val message: String,
    /** The band behind the top: the launcher's colours; light means dark ink on it. */
    val band: Pair<String, String>,
    val light: Boolean,
    /** Chips, links and the ask box's ring (web --cb-accent). */
    val accent: String,
    val talk: Talk,
    val ask: Ask?,
    val resume: Resume?,
    val requests: RequestsCard?,
    val help: Help?,
    val trust: Trust?,
    /** The cards from the top down. */
    val order: List<HomeSection>,
    /** Every article the ask box and the help card search. */
    val articles: List<HelpArticle>,
    /** The owner's mascot (art3d mascotLookOf) and its colour, for an app that draws it on its home; the web's home has none. */
    val mascot: HomeMascot = HomeMascot()
) {
    data class Brand(val logo: String, val name: String)
    data class Talk(
        val avatar: String,
        val title: String,
        /** The owner's own tagline (kept), else null (the status line takes its place). */
        val tagline: String?,
        /** "Online · Usually replies in a few minutes"; null when there is nothing to say. */
        val status: String?,
        val tone: ReplyTone,
        /** The green dot on the avatar (off with hideOnlineStatus). */
        val dot: Boolean,
        val button: String,
        /** An AI agent's suggested questions: one tap opens the chat and asks. */
        val starters: List<String>,
        /** The team's photos, a small stack at the end of the name row. */
        val faces: List<String>,
        val rating: HomeRating?
    )
    data class Ask(val placeholder: String, val label: String, val agentName: String?)
    data class Resume(val topic: String, val line: String, val resumable: Boolean)
    data class RequestsCard(val rows: List<HomeRequestRow>, val total: Int, val seeAll: String?, val fresh: Boolean)
    data class Help(
        val title: String,
        val recent: List<HomeArticleRow>,
        val seeAll: Boolean,
        val topics: List<Topic>,
        /** The card's own search (only when the ask box is not there). */
        val search: Boolean
    )
    data class Topic(val id: String, val name: String, val count: Int)
    data class Trust(val label: String, val logos: List<String>)
}

object Home {

    // ------------------------------------------------------------ words (web ts/i18n home.*, trust.*, requests.*), read in the active language

    val TITLE: String get() = t("home.title")
    val DESCRIPTION: String get() = t("home.description")
    val ONLINE_NOW: String get() = t("home.onlineNow")
    val SUPPORT: String get() = t("home.support")
    val CHAT_WITH_US: String get() = t("home.chatWithUs")
    val OUR_ASSISTANT: String get() = t("home.ourAssistant")
    val RESUME: String get() = t("home.resume")
    val CONTINUE_CONVERSATION: String get() = t("home.continueConversation")
    val ASK_AGAIN: String get() = t("home.askAgain")
    val HELP_CENTRE: String get() = t("home.helpCentre")
    val RECENT_ARTICLES: String get() = t("home.recentArticles")
    val SEE_ALL: String get() = t("home.seeAll")
    val SEARCH_PLACEHOLDER: String get() = t("home.searchPlaceholder")
    val SEARCH_LABEL: String get() = t("kb.searchLabel")
    fun noMatch(query: String) = t("kb.noMatch", "query" to query)
    val ASK_INSTEAD: String get() = t("home.askInstead")
    val CHAT_INSTEAD: String get() = t("home.chatInstead")
    val ASK_PLACEHOLDER: String get() = t("home.askPlaceholder")
    fun askLabel(name: String) = t("home.askLabel", "name" to name)

    /**
     * "Ask “<query>”": the words around the query, which is drawn bold (web
     * home.askQuery "Ask {query}"): ("Ask ", "") in English, ("", " fragen") in German.
     */
    fun askQueryParts(): Pair<String, String> {
        val parts = t("home.askQuery").split("{query}")
        return parts[0] to parts.getOrElse(1) { "" }
    }

    /** "Ask" before the bold query (see [askQueryParts] for languages that put it after). */
    val ASK_QUERY: String get() = askQueryParts().first.trim()
    fun answersRightAway(name: String) = t("home.answersRightAway", "name" to name)
    val WELL_HELP: String get() = t("home.wellHelp")
    val ARTICLES: String get() = t("tabs.articles")
    val GOOD_MORNING: String get() = t("home.goodMorning")
    val GOOD_AFTERNOON: String get() = t("home.goodAfternoon")
    val GOOD_EVENING: String get() = t("home.goodEvening")
    val HELLO: String get() = t("home.hello")
    fun welcomeBackName(name: String) = t("home.welcomeBackName", "name" to name)
    val WELCOME_BACK: String get() = t("home.welcomeBack")
    val REQUESTS_TITLE: String get() = t("requests.title")
    fun requestsSeeAll(n: Int) = t("requests.seeAll", "n" to n)
    val REVIEW: String get() = t("trust.review")
    val REVIEWS: String get() = t("trust.reviews")
    fun rated(avg: String, count: Int, reviews: String) = t("trust.rated", "avg" to avg, "count" to count, "reviews" to reviews)
    val OUR_TEAM: String get() = t("trust.ourTeam")
    val CUSTOMER_LOGO: String get() = t("trust.customerLogo")
    val CLOSE: String get() = t("common.close")
    val BACK: String get() = t("common.back")

    // ------------------------------------------------------------ helpers

    private fun truthy(v: Any?): Boolean = when (v) {
        null, false, "" -> false
        is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
        else -> true
    }

    private fun list(v: Any?): List<Any?> = v as? List<Any?> ?: emptyList()
    private fun str(v: Any?): String = v as? String ?: ""
    private val SPACES = Regex("\\s+")
    private val TAGS = Regex("<[^>]*>")

    // ------------------------------------------------------------ on or off

    /**
     * Is there a home screen at all? The web's templates draw it when
     * `enableInitialPage` is truthy, and the panel opens on it.
     */
    fun on(c: Map<String, Any?>?): Boolean = c != null && truthy(c["enableInitialPage"])

    /** A view that cannot be shown falls back (web core/helpCentre.ts safeView). */
    fun safeView(c: Map<String, Any?>?, view: HomeView?): HomeView {
        val home = on(c)
        return when (view) {
            HomeView.HELP -> if (WaitArticles.helpCentreOn(c)) HomeView.HELP else if (home) HomeView.HOME else HomeView.CHAT
            HomeView.HOME -> if (home) HomeView.HOME else HomeView.CHAT
            HomeView.CHAT -> HomeView.CHAT
            null -> if (home) HomeView.HOME else HomeView.CHAT
        }
    }

    // ------------------------------------------------------------ the help centre's articles

    /** Drafts never show (web core/helpCentre.ts publishedArticles). */
    fun publishedArticles(list: List<Any?>?): List<Map<*, *>> =
        list.orEmpty().mapNotNull { it as? Map<*, *> }.filter {
            truthy(it["_id"]) && (it["reviewStatus"] == null || it["reviewStatus"] == "published")
        }

    private fun publishedCategories(categories: List<Any?>?): List<Any?> =
        categories.orEmpty().map { c ->
            val m = c as? Map<*, *>
            if (m != null && m["articles"] is List<*>) {
                @Suppress("UNCHECKED_CAST")
                (m as Map<Any?, Any?>) + ("articles" to publishedArticles(m["articles"] as List<Any?>))
            } else c
        }

    /**
     * The help centre counts as on: the owner's switch, and at least one
     * published article (web index.ts: a help centre with no published
     * articles is a blank screen and counts as off).
     */
    fun helpOn(c: Map<String, Any?>?, categories: List<Any?>?, recent: List<Any?>?): Boolean {
        if (!WaitArticles.helpCentreOn(c)) return false
        val inCategories = publishedCategories(categories).any { list((it as? Map<*, *>)?.get("articles")).isNotEmpty() }
        return inCategories || publishedArticles(recent).isNotEmpty()
    }

    /** The home screen's Recent articles list (web core/helpCentre.ts showsRecentArticles). */
    fun showsRecentArticles(c: Map<String, Any?>?, articles: List<Any?>?): Boolean =
        c != null && c["enableInitialPage"] == true && WaitArticles.helpCentreOn(c) && c["hideRecentArticles"] != true &&
            publishedArticles(articles).isNotEmpty()

    /** The categories worth a shortcut: named, not empty, most articles first (ties keep order). */
    fun topCategories(categories: List<Any?>?, max: Int = 4): List<Map<*, *>> =
        categories.orEmpty()
            .mapIndexedNotNull { i, c -> (c as? Map<*, *>)?.let { Triple(it, i, list(it["articles"]).size) } }
            .filter { (c, _, n) -> n > 0 && str(c["name"]).isNotBlank() }
            .sortedWith(compareByDescending<Triple<Map<*, *>, Int, Int>> { it.third }.thenBy { it.second })
            .take(max)
            .map { it.first }

    // ------------------------------------------------------------ the header

    /** "Good morning" / "Good afternoon" / "Good evening" by the visitor's clock; noon when unknown. */
    fun salutation(hour: Int?): String {
        val h = (((hour ?: 12) % 24) + 24) % 24
        return when {
            h in 5..11 -> GOOD_MORNING
            h in 12..17 -> GOOD_AFTERNOON
            h in 18..22 -> GOOD_EVENING
            else -> HELLO
        }
    }

    private val LOGO_OK = Regex("^(https?:|data:image/|blob:|/)", RegexOption.IGNORE_CASE)

    /**
     * Who the home screen is from: the logo and name the chat header shows.
     * Null when the owner hid the logo or there is no usable image.
     */
    fun brand(c: Map<String, Any?>?, fallbackName: String = ""): HomeModel.Brand? {
        if (c == null || truthy(c["hideLogo"])) return null
        val logo = str(c["logo"]).trim()
        if (logo.isEmpty() || !LOGO_OK.containsMatchIn(logo)) return null
        val name = (str(c["logoText"]).trim().ifEmpty { fallbackName }).replace(SPACES, " ").trim()
        return HomeModel.Brand(logo, name)
    }

    const val CONFERBOT_FACE = "https://prd.media.cdn.conferbot.com/62829a1c49f355163dfdbfb2/conferbot-logo-1710782074234.png"
    private val CONFERBOT_LOGO = Regex("conferbot-logo", RegexOption.IGNORE_CASE)
    private fun ownImage(v: Any?): String = (v as? String)?.trim()?.takeIf { it.isNotEmpty() && !CONFERBOT_LOGO.containsMatchIn(it) }.orEmpty()

    /** The card's face when the owner set none: the bot's avatar, then its logo, then Conferbot's. */
    fun cardAvatar(c: Map<String, Any?>?): String =
        str(c?.get("initialPageAvatar")).trim().ifEmpty { null }
            ?: (if (!truthy(c?.get("hideAvatar"))) ownImage(c?.get("avatar")) else "").ifEmpty { null }
            ?: (if (!truthy(c?.get("hideLogo"))) ownImage(c?.get("logo")) else "").ifEmpty { null }
            ?: CONFERBOT_FACE

    // ------------------------------------------------------------ honest words (web homeCopy.ts)

    private fun norm(v: Any?): String = (v as? String)?.replace(SPACES, " ")?.trim()?.lowercase().orEmpty()
    private val LEGACY_TITLES = listOf("our usual reply time", "our usual reply time is")
    private val LEGACY_TAGLINES = listOf("later today", "a few hours", "within a day")
    private fun isLegacyDescription(v: Any?): Boolean {
        val n = norm(v)
        return n.isEmpty() || n.startsWith("welcome to the conferbot") || n == "we help your business to connect with your customer"
    }

    fun copy(c: Map<String, Any?>?, name: String): HomeCopy {
        val title = norm(c?.get("initialPageCardSubTitle"))
        val tagline = norm(c?.get("initialPageCardTagline"))
        val ownTagline = tagline.isNotEmpty() && tagline !in LEGACY_TAGLINES
        return HomeCopy(
            cardTitle = if (title.isEmpty() || title in LEGACY_TITLES) name.ifEmpty { SUPPORT } else str(c?.get("initialPageCardSubTitle")).trim(),
            cardTagline = if (ownTagline) str(c?.get("initialPageCardTagline")).trim() else ONLINE_NOW,
            description = if (isLegacyDescription(c?.get("initialPageDescription"))) DESCRIPTION else str(c?.get("initialPageDescription")),
            online = !ownTagline
        )
    }

    private fun plainText(v: String) = v.replace(TAGS, " ").replace(SPACES, " ").trim()


    // ------------------------------------------------------------ colours

    private val HEX = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
    private val RGB = Regex("^rgba?\\(\\s*(\\d+)[,\\s]+(\\d+)[,\\s]+(\\d+)", RegexOption.IGNORE_CASE)

    /** Parse "#abc", "#aabbcc" or "rgb(a, b, c)" to (r, g, b); null for anything else. */
    fun parseColor(value: String?): Triple<Int, Int, Int>? {
        val s = value.orEmpty().trim()
        HEX.find(s)?.let { m ->
            var hex = m.groupValues[1]
            if (hex.length == 3) hex = hex.map { "$it$it" }.joinToString("")
            val n = hex.toInt(16)
            return Triple((n shr 16) and 255, (n shr 8) and 255, n and 255)
        }
        val rgb = RGB.find(s) ?: return null
        return Triple(rgb.groupValues[1].toInt(), rgb.groupValues[2].toInt(), rgb.groupValues[3].toInt())
    }

    /** True when white text would not read on this colour (relative luminance above ~0.55). */
    fun isLight(rgb: Triple<Int, Int, Int>?): Boolean {
        rgb ?: return false
        fun lin(ch: Int): Double {
            val s = ch / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(rgb.first) + 0.7152 * lin(rgb.second) + 0.0722 * lin(rgb.third) > 0.55
    }

    /** The band's two colours: the launcher's gradient, else its solid colour (firstImpression.ts brandColors). */
    fun brandColors(c: Map<String, Any?>?): Pair<String, String> {
        val one = str(c?.get("widgetGradientBgOne"))
        val two = str(c?.get("widgetGradientBgTwo"))
        if (c?.get("widgetIconThemeType") == "Gradient" && one.isNotEmpty() && two.isNotEmpty()) return one to two
        val solid = str(c?.get("widgetIconBgColor")).ifEmpty { str(c?.get("headerBgColor")) }.ifEmpty { "#4f46e5" }
        return solid to solid
    }

    /** The classic widget's surfaces (web polish.css / polish-home.css). */
    val CLASSIC_PALETTE = HomePalette(page = "#f6f7fb", card = "#ffffff", soft = "#f1f5f9", ink = "#0f172a", muted = "#64748b", line = "#f1f5f9")

    /**
     * The home screen's surfaces: the widget theme's canvas, cards, quiet
     * fills and text (bg2 is "home screen, help centre"), else the classic
     * widget's.
     */
    fun palette(tokens: ThemeTokens?): HomePalette =
        if (tokens != null && tokens.bg2.isNotEmpty() && tokens.card.isNotEmpty() && tokens.ink.isNotEmpty()) HomePalette(
            page = tokens.bg2,
            card = tokens.card,
            soft = tokens.card2.ifEmpty { tokens.bg2 },
            ink = tokens.ink,
            muted = tokens.muted.ifEmpty { tokens.ink },
            line = tokens.line.ifEmpty { tokens.card2.ifEmpty { tokens.bg2 } }
        ) else CLASSIC_PALETTE

    // ------------------------------------------------------------ trust (web trust.ts)

    private val SAFE_IMAGE = Regex("^https://[^\\s\"'<>]+$", RegexOption.IGNORE_CASE)

    /** The owner's images, https only, once each, at most [max]. */
    fun trustImages(list: Any?, max: Int): List<String> =
        list(list).map { (it as? String)?.trim().orEmpty() }.filter { SAFE_IMAGE.matches(it) }.distinct().take(max)

    /** A count as people say it: 120, 1.2k, 12k. */
    fun compactCount(n: Number?): String {
        val v = maxOf(0L, Math.floor(n?.toDouble()?.takeIf { !it.isNaN() } ?: 0.0).toLong())
        if (v < 1000) return v.toString()
        val k = v / 1000.0
        val shown = if (k < 10) Math.floor(k * 10) / 10 else Math.floor(k)
        return "${if (shown == Math.floor(shown)) shown.toLong().toString() else shown.toString()}k"
    }

    private fun num(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }

    /** The rating to show (the server's average of real post-chat scores), or null. */
    fun ratingLine(r: Any?): HomeRating? {
        val m = r as? Map<*, *> ?: return null
        val average = num(m["average"])
        val countD = Math.floor(num(m["count"]))
        if (average.isNaN() || average < 1 || average > 5 || countD.isNaN() || countD < 1) return null
        val count = countD.toInt()
        // As JavaScript's toFixed(1): the exact value, rounded half up.
        val avg = BigDecimal(average).setScale(1, RoundingMode.HALF_UP).toPlainString()
        val many = if (count == 1) REVIEW else REVIEWS
        return HomeRating(
            average = avg,
            count = "${compactCount(count)} $many",
            // The stars fill to the average shown, so they agree with the number.
            fill = Math.round(avg.toDouble() / 5 * 1000) / 10.0,
            label = rated(avg, count, many)
        )
    }

    // ------------------------------------------------------------ visitor memory (web visitorMemory.ts)

    /** Per bot, on the device only (web localStorage `cb_mem_<botId>`, RN `@conferbot:memory:<botId>`). */
    fun memoryKey(botId: String?) = "conferbot_memory_${botId?.takeIf { it.isNotEmpty() } ?: "default"}"
    private const val FORGET_AFTER_MS = 60L * 24 * 60 * 60 * 1000
    /** The web restores a live chat this long after its last message (BOT_STATE_EXPIRY). */
    const val LIVE_CHAT_MS = 30L * 60 * 1000

    private val gson = Gson()

    /** What the device remembers, or null (none, broken, or older than 60 days). */
    fun parseMemory(raw: String?, now: Long = System.currentTimeMillis()): VisitorMemory? = try {
        val m: Map<String, Any?>? = gson.fromJson(raw ?: "null", object : TypeToken<Map<String, Any?>>() {}.type)
        val at = (m?.get("at") as? Number)?.toLong()
        if (m == null || at == null || now - at > FORGET_AFTER_MS) null
        else VisitorMemory(
            at = at,
            name = (m["name"] as? String)?.take(40),
            topic = (m["topic"] as? String)?.take(160),
            session = m["session"] as? String
        )
    } catch (e: Exception) {
        null
    }

    /** What to store. */
    fun serializeMemory(m: VisitorMemory): String = gson.toJson(buildMap {
        put("at", m.at)
        m.name?.let { put("name", it) }
        m.topic?.let { put("topic", it) }
        m.session?.let { put("session", it) }
    })

    private fun fresh(m: VisitorMemory?, now: Long): VisitorMemory? = m?.takeIf { now - it.at <= FORGET_AFTER_MS }

    /** They wrote [text] in conversation [session] (web rememberMessage). */
    fun withMessage(m: VisitorMemory?, session: String?, text: String, now: Long = System.currentTimeMillis()): VisitorMemory {
        var next = (fresh(m, now) ?: VisitorMemory(at = now)).copy(at = now)
        // A new conversation's first real question becomes what they came for.
        if (!session.isNullOrEmpty() && next.session != session && WaitArticles.isRealQuestion(text)) {
            next = next.copy(topic = text.replace(SPACES, " ").trim().take(160), session = session)
        }
        return next
    }

    private val NOT_A_NAME = Regex("[@\\d<>]")

    /** They told us their name (web rememberName): a first name, or null when it is not one. */
    fun withName(m: VisitorMemory?, name: String?, now: Long = System.currentTimeMillis()): VisitorMemory? {
        val first = name.orEmpty().trim().split(SPACES).firstOrNull().orEmpty()
        if (first.isEmpty() || first.length > 40 || NOT_A_NAME.containsMatchIn(first)) return null
        return (fresh(m, now) ?: VisitorMemory(at = now)).copy(name = first.substring(0, 1).uppercase() + first.substring(1))
    }

    data class WelcomeBack(val hello: String, val topic: String?)

    /**
     * "Welcome back[, Name]" once their last chat is over (not while the web
     * would still restore it), and the question to pick up. Null for a first visit.
     */
    fun welcomeBack(m: VisitorMemory?, now: Long = System.currentTimeMillis()): WelcomeBack? {
        if (m == null || now - m.at < LIVE_CHAT_MS) return null
        if (m.topic.isNullOrEmpty() && m.name.isNullOrEmpty()) return null
        return WelcomeBack(if (!m.name.isNullOrEmpty()) welcomeBackName(m.name) else WELCOME_BACK, m.topic?.takeIf { it.isNotEmpty() })
    }

    /** The remembered conversation is the one on the device, so the card can open it (web canResume). */
    fun canResume(m: VisitorMemory?, chatSessionId: String?): Boolean =
        !m?.session.isNullOrEmpty() && !chatSessionId.isNullOrEmpty() && m?.session == chatSessionId

    // ------------------------------------------------------------ "Ask anything" (web askBox.ts)

    /** Whether the box shows at all: the owner's switch (on unless off), and an agent or an article to find. */
    fun askBoxOn(c: Map<String, Any?>?, hasAgent: Boolean, hasArticles: Boolean): Boolean =
        c?.get("homeAskBox") != false && (hasAgent || hasArticles)

    /** The box's words: a question for an agent, a search for articles alone. */
    fun askPlaceholder(hasAgent: Boolean, hasArticles: Boolean): String =
        if (hasAgent) ASK_PLACEHOLDER else if (hasArticles) SEARCH_PLACEHOLDER else ""

    // Each special character escaped by hand (ICU and the JVM read these alike; #70).
    private fun escapeRegex(s: String): String = buildString {
        s.forEach { ch -> if (ch in ".*+?^\${}()|[]\\") append('\\'); append(ch) }
    }

    fun highlightRuns(text: String?, tokens: List<String>): List<TextRun> {
        val source = text.orEmpty()
        val words = tokens.filter { it.isNotEmpty() }.sortedByDescending { it.length }
        if (words.isEmpty() || source.isEmpty()) return listOf(TextRun(source))
        val re = Regex("(${words.joinToString("|") { escapeRegex(it) }})", RegexOption.IGNORE_CASE)
        val out = mutableListOf<TextRun>()
        var last = 0
        re.findAll(source).forEach { m ->
            if (m.range.first > last) out += TextRun(source.substring(last, m.range.first))
            out += TextRun(m.value, mark = true)
            last = m.range.last + 1
        }
        if (last < source.length) out += TextRun(source.substring(last))
        return out
    }

    /**
     * The list under the box as the visitor types (web askBox.ts render): for
     * an agent first "Ask “...”"; the articles that answer it (best first, at
     * most 4, the server's body matches after the local ones); for a flow bot
     * with nothing found, "No articles match" and "Chat with us". Empty while
     * the box is empty. The first row that does something is what Enter picks.
     */
    fun askRows(query: String?, agentName: String?, articles: List<HelpArticle>, remote: RemoteHits? = null): List<AskRow> {
        val q = query.orEmpty().trim()
        if (q.isEmpty()) return emptyList()
        val words = WaitArticles.keywordsOf(q)
        val tokens = words.ifEmpty { WaitArticles.tokenize(q) }
        val found = WaitArticles.answersFor(articles, q, 4).toMutableList()
        if (remote != null && remote.query == q.lowercase()) {
            val byId = articles.associateBy { it.id }
            remote.hits
                .filter { (id, _) -> byId.containsKey(id) && found.none { it.id == id } }
                .take(maxOf(0, 4 - found.size))
                .forEach { (id, snippet) -> found += byId.getValue(id).copy(description = snippet) }
        }
        val rows = mutableListOf<AskRow>()
        if (agentName != null) rows += AskRow.Ask("ask", q, answersRightAway(agentName))
        if (found.isNotEmpty()) {
            rows += AskRow.Label("label", ARTICLES)
            found.forEach { a -> rows += AskRow.Article("a-${a.id}", a.id, highlightRuns(a.title, tokens), a.description.ifEmpty { a.categoryName }) }
        } else if (agentName == null) {
            rows += AskRow.None("none", noMatch(q))
            rows += AskRow.Chat("chat", CHAT_WITH_US, WELL_HELP)
        }
        return rows
    }

    /** What Enter does: the first row that does something. */
    fun askDefault(rows: List<AskRow>): AskRow? = rows.firstOrNull { it is AskRow.Ask || it is AskRow.Article || it is AskRow.Chat }

    /**
     * What to ask the server for matches inside article bodies (web
     * askBox.ts askRemote): the question's two longest key words, or null
     * when there is nothing to ask or no article lacks its body. [articles]
     * are the raw ones (maps).
     */
    fun remoteQueryFor(query: String?, articles: List<Any?>?): String? {
        if (articles.orEmpty().none { it is Map<*, *> && it["content"] !is String }) return null
        val words = WaitArticles.keywordsOf(query.orEmpty().trim())
        if (words.isEmpty()) return null
        val key = words.sortedByDescending { it.length }.take(2).joinToString(" ")
        return if (key.length < 2) null else key
    }

    /** The help card's own search, when the ask box is off. */
    fun helpSearch(query: String?, articles: List<HelpArticle>, hasAgent: Boolean): HelpSearch? {
        val q = query.orEmpty().trim()
        if (q.isEmpty()) return null
        val tokens = WaitArticles.tokenize(q)
        val found = WaitArticles.searchArticles(articles, q).take(5)
        if (found.isNotEmpty()) return HelpSearch.Results(found.map { HelpSearch.Row(it.id, highlightRuns(it.title, tokens), it.categoryName.ifEmpty { it.description }) })
        return HelpSearch.Empty(noMatch(q), if (hasAgent) ASK_INSTEAD else CHAT_INSTEAD, hasAgent)
    }

    // ------------------------------------------------------------ the model

    private fun truncate(text: Any?, max: Int): String {
        val s = text?.toString().orEmpty()
        return if (s.length > max) "${s.take(max)}..." else s
    }

    /** While the visitor types in the ask box, the list is the page: these go (web cb-home--asking). */
    val HIDDEN_WHILE_ASKING = listOf(HomeSection.RESUME, HomeSection.REQUESTS, HomeSection.TALK, HomeSection.HELP)

    /**
     * What the home screen shows, from the owner's settings ([c]: themed and
     * personalised, as the chat uses them) and what the device knows; null
     * while the owner has no home screen.
     */
    fun model(
        c: Map<String, Any?>?,
        botName: String? = null,
        agent: HomeAgent? = null,
        categories: List<Any?>? = null,
        recentArticles: List<Any?>? = null,
        memory: VisitorMemory? = null,
        chatSessionId: String? = null,
        requests: List<RequestSummary>? = null,
        seen: Map<String, Long> = emptyMap(),
        now: Long = System.currentTimeMillis(),
        hour: Int? = null
    ): HomeModel? {
        if (!on(c)) return null
        c!!
        val cats = publishedCategories(categories)
        val recent = publishedArticles(recentArticles)
        val kbOn = helpOn(c, cats, recent)
        val name = agent?.displayName?.ifEmpty { null } ?: str(c["logoText"]).ifEmpty { null } ?: botName.orEmpty()

        // The header: salutation (or welcome back), the brand, the owner's words.
        val back = welcomeBack(memory, now)
        val copy = copy(c, name)
        val band = brandColors(c)

        // "Talk to us": the status line replaces our own tagline (replyTimeView.ts decorateHomeReply).
        val hideStatus = c["hideOnlineStatus"] == true
        val people = ReplyTime.homePeople(ReplyTime.of(c["replyTime"]), agent = agent != null, now = Date(now))
        val status = listOf(if (hideStatus) "" else ReplyTime.ONLINE, people.text).filter { it.isNotEmpty() }
            .joinToString(ReplyTime.SEPARATOR).ifEmpty { null }
        val talk = HomeModel.Talk(
            avatar = cardAvatar(c),
            title = copy.cardTitle,
            tagline = if (copy.online) null else copy.cardTagline,
            status = status,
            tone = people.tone,
            dot = !hideStatus,
            // The owner's words, or the stock ones in the visitor's language (web core/customizations.ts ownerTextOr).
            button = I18n.ownerTextOr(c["initialPageCardButtonText"], "home.chatWithUs"),
            starters = agent?.starters.orEmpty().filter { it.isNotBlank() }.take(3),
            faces = trustImages(c["homeTeamPhotos"], 3),
            rating = ratingLine(c["homeRating"])
        )

        // Search first.
        val articles = if (kbOn) WaitArticles.flattenArticles(cats, recent) else emptyList()
        val agentName = if (agent != null) agent.displayName?.ifEmpty { null } ?: str(c["logoText"]).ifEmpty { null } ?: botName?.ifEmpty { null } ?: OUR_ASSISTANT else null
        val ask = if (askBoxOn(c, agent != null, articles.isNotEmpty())) HomeModel.Ask(
            placeholder = askPlaceholder(agent != null, articles.isNotEmpty()),
            label = if (agentName != null) askLabel(agentName) else SEARCH_LABEL,
            agentName = agentName
        ) else null

        // Pick up where they left off: an agent bot only.
        val resumable = canResume(memory, chatSessionId)
        val resume = if (back?.topic != null && agent != null)
            HomeModel.Resume(back.topic, if (resumable) CONTINUE_CONVERSATION else ASK_AGAIN, resumable) else null

        // Their requests: up to two, "See all (n)" when there are more; a new reply puts it above "Talk to us".
        val list = if (requests != null) Requests.ordered(requests, seen) else emptyList()
        val requestsCard = if (list.isNotEmpty()) HomeModel.RequestsCard(
            rows = list.take(Requests.ON_HOME).map { r ->
                HomeRequestRow(r.id, r.title, Requests.rowLine(r, seen, now), Requests.stateOf(r), Requests.isNewReply(r, seen))
            },
            total = list.size,
            seeAll = if (list.size > Requests.ON_HOME) requestsSeeAll(list.size) else null,
            fresh = list.any { Requests.isNewReply(it, seen) }
        ) else null

        // The help card: the recent list (unless hidden), the topics, See all.
        var help: HomeModel.Help? = null
        if (kbOn) {
            val topics = topCategories(cats, 6)
            val withList = showsRecentArticles(c, recent)
            if (articles.isNotEmpty() || topics.isNotEmpty()) {
                help = HomeModel.Help(
                    title = if (withList) RECENT_ARTICLES else HELP_CENTRE,
                    recent = if (withList) recent.map { r ->
                        HomeArticleRow(
                            id = r["_id"].toString(),
                            title = truncate(r["title"], 100),
                            description = truncate(r["description"], 120),
                            image = (r["coverImage"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    } else emptyList(),
                    seeAll = topics.isNotEmpty(),
                    topics = topics.map { t -> HomeModel.Topic((t["_id"] ?: t["id"] ?: t["name"]).toString(), str(t["name"]), list(t["articles"]).size) },
                    search = ask == null
                )
            }
        }

        // The owner's customer logos with their line, last.
        val logos = trustImages(c["homeTrustLogos"], 6)
        val trust = if (logos.isNotEmpty()) HomeModel.Trust(str(c["homeTrustLabel"]).trim(), logos) else null

        val order = buildList {
            if (ask != null) add(HomeSection.ASK)
            if (resume != null) add(HomeSection.RESUME)
            if (requestsCard?.fresh == true) add(HomeSection.REQUESTS)
            add(HomeSection.TALK)
            if (requestsCard != null && !requestsCard.fresh) add(HomeSection.REQUESTS)
            if (help != null) add(HomeSection.HELP)
            if (trust != null) add(HomeSection.TRUST)
        }

        return HomeModel(
            hello = back?.hello ?: salutation(hour ?: Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)),
            brand = brand(c, agent?.displayName?.ifEmpty { null } ?: botName.orEmpty()),
            // The stock "Hi there 👋" in the visitor's language (web core/customizations.ts stockText).
            title = I18n.stockText((c["initialPageTitle"] as? String)?.trim().orEmpty(), "home.title"),
            description = copy.description,
            message = (c["initialPageMessage"] as? String)?.let(::plainText).orEmpty(),
            band = band,
            light = isLight(parseColor(band.first)) && isLight(parseColor(band.second)),
            accent = Requests.accent(c),
            talk = talk,
            ask = ask,
            resume = resume,
            requests = requestsCard,
            help = help,
            trust = trust,
            order = order,
            articles = articles,
            mascot = HomeMascot(MascotArt.lookOf(c), Customizations.mascotAccent(c)),
        )
    }
}
