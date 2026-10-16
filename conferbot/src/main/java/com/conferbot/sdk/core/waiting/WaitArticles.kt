package com.conferbot.sdk.core.waiting

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.livechat.SignalTimer

/**
 * While you wait for a person: the help that may answer the visitor
 * meanwhile, as one card. Ported from the React Native SDK's
 * src/core/waitArticles.ts, itself from the web widget:
 *
 *   - conferbot-widget src/ts/chat/waitArticles.ts: WAIT_BEFORE_MS, the
 *     articles picked for the visitor's last real question
 *     (waitArticlesFor), and the card (mountHelpCard): up to two rows,
 *     "That solved it" when the wait can end there.
 *   - src/ts/chat/handlers/special.ts: the timer's start and stop points
 *     (waitForPerson), lastVisitorText (ASKS_FOR_PERSON), queueServerSeen,
 *     and _wireQueue: the server's own pick (queue-help, or queue-status's
 *     `help`) replacing the card when it has new items, and "That solved it"
 *     sending queue-self-resolve {chatSessionId, articleId}.
 *   - src/ts/home/askBox.ts answersFor / keywordsOf, homeUtils.ts
 *     flattenArticles, knowledgeBase/kbUtils.ts tokenize / searchArticles,
 *     home/visitorMemory.ts isRealQuestion, core/helpCentre.ts helpCentreOn.
 *   - src/ts/chat/handoverState.ts: once the wait is over the card stays as
 *     history under "Articles that might help".
 *
 * The server (embed-server liveChat/queueHelp.js) emits queue-help
 * { chatSessionId, items: [{ kind: 'article' | 'page', id, title,
 * description, url?, score }], offer } to the chat room; at most two cards
 * per wait. Pure: [WaitingExtras] feeds it. The help centre is the raw
 * `knowledgeBaseData` of fetched-chatbot-data (categories, recentArticles).
 */
object WaitArticles {

    /** The articles card comes this long into a wait for a person. */
    const val WAIT_BEFORE_MS = 6000L
    /** "That solved it" waits this long for the server's answer. */
    const val SOLVE_TIMEOUT_MS = 8000L

    // The card's words (web wait.*, handover.articlesTitle, queue.leftHelped), read in the active language.
    val LABEL: String get() = t("wait.label")
    val TITLE: String get() = t("wait.title")
    /** Once nobody is waiting any more (web handover.articlesTitle). */
    val SETTLED_TITLE: String get() = t("handover.articlesTitle")
    val READ: String get() = t("common.read")
    val SOLVED: String get() = t("wait.solved")
    val DONE: String get() = t("wait.done")
    /** The line drawn for a queue left this way (web queue.leftHelped). */
    val LEFT_HELPED: String get() = t("queue.leftHelped")
    /** What the record keeps (web special.ts leave()): English, for the team. */
    const val RECORD_TEXT = "Resolved while waiting"

    // ------------------------------------------------------------ help centre

    /** The help centre is on only when enableKnowledgeBase is true (web core/helpCentre.ts). */
    fun helpCentreOn(c: Map<String, Any?>?): Boolean = c?.get("enableKnowledgeBase") == true

    data class HelpArticle(val id: String, val title: String, val description: String, val categoryName: String, val text: String)

    private fun plain(html: Any?): String = (html?.toString() ?: "")
        .replace(Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&[a-z#0-9]+;", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun list(v: Any?): List<Any?> = v as? List<Any?> ?: emptyList()
    private fun str(v: Any?): String = v?.toString().orEmpty()

    /**
     * Every article the visitor can open, once each: the categories' articles
     * first (they carry the category name), then any recent article not
     * already listed. Only ones with an id and a title (web homeUtils.ts).
     */
    fun flattenArticles(categories: List<Any?>?, recent: List<Any?>? = emptyList()): List<HelpArticle> {
        val out = mutableListOf<HelpArticle>()
        val seen = mutableSetOf<String>()
        fun add(a: Any?, categoryName: String) {
            val m = a as? Map<*, *> ?: return
            val id = m["_id"]?.toString().orEmpty()
            val title = (m["title"] as? String)?.trim().orEmpty()
            if (id.isEmpty() || title.isEmpty() || !seen.add(id)) return
            out.add(HelpArticle(id, title, (m["description"] as? String).orEmpty(), categoryName, plain(m["content"])))
        }
        list(categories).forEach { c ->
            val m = c as? Map<*, *>
            list(m?.get("articles")).forEach { add(it, str(m?.get("name"))) }
        }
        list(recent).forEach { a -> add(a, str(((a as? Map<*, *>)?.get("category") as? Map<*, *>)?.get("name"))) }
        return out
    }

    /** An article by id among the bot's help centre data, or null. */
    fun findArticle(id: String, categories: List<Any?>?, recent: List<Any?>?): Map<*, *>? {
        if (id.isEmpty()) return null
        for (c in list(categories)) {
            list((c as? Map<*, *>)?.get("articles")).firstOrNull { (it as? Map<*, *>)?.get("_id")?.toString() == id }?.let { return it as Map<*, *> }
        }
        return list(recent).firstOrNull { (it as? Map<*, *>)?.get("_id")?.toString() == id } as? Map<*, *>
    }

    // ------------------------------------------------------------ search (web kbUtils.ts, askBox.ts)

    private val NON_WORD = Regex("[^a-z0-9\\u00C0-\\uFFFF]+")

    fun tokenize(query: String?): List<String> {
        val words = query.orEmpty().lowercase().split(NON_WORD).filter { it.isNotEmpty() }
        // One-letter words only count when they are all there is.
        val long = words.filter { it.length > 1 }
        return (if (long.isNotEmpty()) long else words).distinct()
    }

    private fun isWordChar(ch: Char) = ch in 'a'..'z' || ch in '0'..'9' || ch >= 'À'

    // The word starts a word of the title (web: (^|[^a-z0-9À-￿])word).
    private fun startsAWord(title: String, t: String): Boolean {
        var i = title.indexOf(t)
        while (i >= 0) {
            if (i == 0 || !isWordChar(title[i - 1])) return true
            i = title.indexOf(t, i + 1)
        }
        return false
    }

    /**
     * Rank articles for a query. Every word must appear somewhere (title,
     * summary, category or body); titles weigh most, then summaries, then the
     * category, then the body. A title that starts with the query wins.
     */
    fun searchArticles(articles: List<HelpArticle>, query: String?): List<HelpArticle> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val phrase = query.orEmpty().trim().lowercase()
        data class Scored(val article: HelpArticle, val score: Int, val index: Int)
        val scored = mutableListOf<Scored>()
        articles.forEachIndexed { index, article ->
            val title = article.title.lowercase()
            val desc = article.description.lowercase()
            val cat = article.categoryName.lowercase()
            val body = article.text.lowercase()
            var score = 0
            for (t in tokens) {
                val inTitle = title.contains(t)
                val inDesc = desc.contains(t)
                val inCat = cat.contains(t)
                val inBody = body.contains(t)
                if (!inTitle && !inDesc && !inCat && !inBody) return@forEachIndexed // every word must match
                if (inTitle) score += if (startsAWord(title, t)) 12 else 8
                if (inDesc) score += 4
                if (inCat) score += 3
                if (inBody) score += 1
            }
            if (phrase.isNotEmpty() && title.startsWith(phrase)) score += 10
            else if (phrase.isNotEmpty() && title.contains(phrase)) score += 5
            scored.add(Scored(article, score, index))
        }
        return scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.index }).map { it.article }
    }

    // Words that say nothing about what the question is about.
    private val STOP: Set<String> = (
        "a an and are as at be but by can could did do does for from get got had has have how i if in is it its " +
            "me my of on or our so that the their them then there this to us was we what when where which who why " +
            "will with would you your yours about any anyone need want please hi hello hey tell know help there's " +
            "i'm im ive i've dont don't cant can't"
        ).split(Regex("\\s+")).toSet()

    /** The words of a question worth matching on (no "how do I ..."). */
    fun keywordsOf(query: String?): List<String> = tokenize(query).filter { (it.length > 2 || it.any { ch -> ch in '0'..'9' }) && it !in STOP }

    // A word and its plain forms: "changing" also finds "change", "plans" "plan".
    private fun stemsOf(w: String): List<String> {
        val out = mutableListOf(w)
        if (w.length > 5 && w.endsWith("ing")) out += listOf(w.dropLast(3), "${w.dropLast(3)}e")
        if (w.length > 4 && w.endsWith("es")) out += w.dropLast(2)
        if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) out += w.dropLast(1)
        if (w.length > 4 && w.endsWith("ed")) out += listOf(w.dropLast(2), w.dropLast(1))
        return out
    }

    private fun has(text: String, w: String) = stemsOf(w).any { text.contains(it) }

    /**
     * The articles that answer a question, best first: those matching every
     * word, then those matching most of its key words, weighted to titles.
     */
    fun answersFor(articles: List<HelpArticle>, query: String?, max: Int = 4): List<HelpArticle> {
        val strict = searchArticles(articles, query)
        val seen = strict.map { it.id }.toSet()
        val words = keywordsOf(query)
        val need = maxOf(1, Math.ceil(words.size / 2.0).toInt())
        data class Row(val a: HelpArticle, val hits: Int, val score: Int, val index: Int)
        val loose = if (words.isEmpty()) emptyList() else articles
            .filter { it.id !in seen }
            .mapIndexed { index, a ->
                val title = a.title.lowercase()
                val desc = "${a.description} ${a.categoryName}".lowercase()
                val body = a.text.lowercase()
                var hits = 0
                var score = 0
                words.forEach { w ->
                    val t = has(title, w)
                    val d = has(desc, w)
                    val b = has(body, w)
                    if (t || d || b) hits += 1
                    score += (if (t) 6 else 0) + (if (d) 3 else 0) + (if (b) 1 else 0)
                }
                Row(a, hits, score, index)
            }
            .filter { it.hits >= need }
            .sortedWith(compareByDescending<Row> { it.hits }.thenByDescending { it.score }.thenBy { it.index })
            .map { it.a }
        return (strict + loose).take(max)
    }

    // ------------------------------------------------------------ the question

    private val GREETING_ONLY = Regex(
        "^(hi+|hey+|hello+|yo|hiya|ok(ay)?|thanks?|thank you|thx|ty|yes|no|yep|nope|sure|cool|great|bye|good (morning|afternoon|evening))[\\s!.?,]*$",
        RegexOption.IGNORE_CASE
    )
    private val EMAIL_ONLY = Regex("^\\S+@\\S+\\.\\S+$")
    private val PHONE_ONLY = Regex("^[+\\d\\s().-]{7,}$")
    private val A_WORD = Regex("[a-z\\u00C0-\\uFFFF]{2,}", RegexOption.IGNORE_CASE)

    /** Is this a question worth matching (web visitorMemory.ts)? */
    fun isRealQuestion(text: String?): Boolean {
        val t = text.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (t.length < 10 || t.length > 300) return false
        if (GREETING_ONLY.containsMatchIn(t)) return false
        if (EMAIL_ONLY.containsMatchIn(t) || PHONE_ONLY.containsMatchIn(t)) return false
        return t.split(" ").count { A_WORD.containsMatchIn(it) } >= 3
    }

    /** "Can I talk to a person" says nothing about the problem (web special.ts). */
    val ASKS_FOR_PERSON = Regex(
        "\\b(human|person|agent|someone|somebody|representative|operator|real people|staff|talk to|speak to|speak with|call me)\\b",
        RegexOption.IGNORE_CASE
    )

    private val TYPED = setOf("user-message", "user-input-response", "user-live-message", "user-input")

    /**
     * The visitor's last real question in their own words (web special.ts
     * lastVisitorText): the newest typed message that is a real question and
     * not an ask for a person. [record] is the record as the server gets it
     * (ChatState.getRecordForServer): a typed entry's `type` or `shape`.
     */
    fun waitQuestion(record: List<Map<String, Any?>>?): String {
        val list = record ?: return ""
        for (r in list.asReversed()) {
            val kind = (r["type"] ?: r["shape"])?.toString().orEmpty()
            if (kind !in TYPED && r["shape"]?.toString() !in TYPED) continue
            val text = (r["text"] ?: r["value"])?.toString().orEmpty().replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            if (isRealQuestion(text) && !ASKS_FOR_PERSON.containsMatchIn(text)) return text
        }
        return ""
    }

    // ------------------------------------------------------------ the card

    data class HelpItem(
        /** "article" or "page". */
        val kind: String,
        val id: String,
        val title: String,
        val description: String? = null,
        /** A page's address (http/https only). */
        val url: String? = null
    )

    /** The articles to offer for this question (none when the help centre is off). */
    fun waitArticlesFor(c: Map<String, Any?>?, question: String?, categories: List<Any?>?, recent: List<Any?>?): List<HelpItem> {
        if (!helpCentreOn(c) || question.isNullOrBlank()) return emptyList()
        return answersFor(flattenArticles(categories, recent), question, 2).map {
            HelpItem(kind = "article", id = it.id, title = it.title, description = it.description.ifEmpty { null })
        }
    }

    private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)
    private fun safeUrl(u: Any?): String = (u as? String)?.takeIf { HTTP.containsMatchIn(it) }.orEmpty()

    /** The server's items (queue-help, queue-status help), cleaned: an id and a title each. */
    fun helpItemsOf(data: Map<*, *>?): List<HelpItem> =
        list(data?.get("items")).mapNotNull { raw ->
            val i = raw as? Map<*, *> ?: return@mapNotNull null
            // Truthy, as the web's `i.id`.
            val rawId = i["id"]
            if (rawId == null || rawId == false || rawId == "" || (rawId is Number && rawId.toDouble() == 0.0)) return@mapNotNull null
            val id = rawId.toString()
            val title = (i["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val kind = if (i["kind"] == "page") "page" else "article"
            val url = if (kind == "page") safeUrl(i["url"]) else ""
            HelpItem(
                kind = kind,
                id = id,
                title = title,
                description = (i["description"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                url = url.ifEmpty { null }
            )
        }
}

data class HelpCardState(
    /** Bumped each time a new card replaces the old (it is drawn at the end again). */
    val key: Int,
    val items: List<WaitArticles.HelpItem>,
    /** The wait can end here ("That solved it"): only the server's pick. */
    val solvable: Boolean,
    /** The wait is over: the history title, no button. */
    val settled: Boolean,
    /** "That solved it" is waiting for the server. */
    val busy: Boolean,
    /** Left the queue this way: "Glad that helped." */
    val done: Boolean
)

/**
 * The help card's rules (RN createWaitHelp). [startWait] where a wait for a
 * person begins (with no AI agent taking it first), [endWait] wherever it
 * ends; feed the server's queue-status and queue-help in; [solving] /
 * [solved] around the queue-self-resolve call.
 */
class WaitHelp(
    /** The bot's customizations (the help centre switch). */
    private val customizations: () -> Map<String, Any?>?,
    /** The visitor's last real question (waitQuestion of the record). */
    private val question: () -> String,
    /** The bot's help centre: categories and recent articles, as sent. */
    private val articles: () -> Pair<List<Any?>, List<Any?>>,
    /** A person is here already (a join the timer missed). */
    private val joined: () -> Boolean,
    /** The card changed (null: none). */
    private val onChange: (HelpCardState?) -> Unit,
    private val timer: SignalTimer
) {
    private var handle: Any? = null
    // The server keeps a queue: it picks the help itself, ours stays out.
    private var serverSeen = false
    private var helpKey = ""
    private var done = false
    private var opened: WaitArticles.HelpItem? = null
    private var card: HelpCardState? = null
    private var key = 0

    private fun set(next: HelpCardState?) {
        card = next
        onChange(next)
    }

    private fun cancel() {
        handle?.let(timer::clear)
        handle = null
    }

    private fun mount(items: List<WaitArticles.HelpItem>, solvable: Boolean) {
        if (items.isEmpty()) return
        opened = null
        key += 1
        set(HelpCardState(key, items, solvable, settled = false, busy = false, done = false))
    }

    private fun showServerHelp(data: Map<*, *>?) {
        val items = WaitArticles.helpItemsOf(data)
        val k = items.joinToString(",") { it.id }
        if (done || items.isEmpty() || k == helpKey) return
        helpKey = k
        mount(items, true)
    }

    /** A wait for a person began: after WAIT_BEFORE_MS, the articles for their question. */
    fun startWait() {
        cancel()
        // A new wait: the server's pick and "That solved it" start over (web _wireQueue).
        helpKey = ""
        done = false
        handle = timer.set(WaitArticles.WAIT_BEFORE_MS) {
            handle = null
            val q = question()
            if (q.isEmpty() || serverSeen) return@set
            if (joined()) return@set
            val (categories, recent) = articles()
            mount(WaitArticles.waitArticlesFor(customizations(), q, categories, recent), false)
        }
    }

    /** The wait ended (a person, nobody, left, ended): no card yet to come; one drawn stays as history. */
    fun endWait() {
        cancel()
        val c = card
        if (c != null && !c.settled) set(c.copy(settled = true, solvable = if (c.done) c.solvable else false, busy = false))
    }

    /** queue-status (event or answer): a waiting status means the server picks the help. */
    fun queueStatus(data: Map<*, *>?) {
        if (done || data == null || data["status"] != "waiting") return
        serverSeen = true
        (data["help"] as? Map<*, *>)?.let(::showServerHelp)
    }

    /** queue-help: the server's pick for this wait. */
    fun queueHelp(data: Map<*, *>?) {
        serverSeen = true
        showServerHelp(data)
    }

    /** A row was opened (it is the article "That solved it" names). */
    fun open(id: String) {
        opened = card?.items?.firstOrNull { it.id == id }
    }

    /** "That solved it" pressed: the article to send, or null when it cannot go now. */
    fun solving(): WaitArticles.HelpItem? {
        val c = card ?: return null
        if (!c.solvable || c.busy || c.done || c.settled) return null
        set(c.copy(busy = true))
        return opened ?: c.items.firstOrNull()
    }

    /** The server's answer: ok leaves the queue ("Glad that helped."), else the button comes back. */
    fun solved(ok: Boolean) {
        val c = card ?: return
        if (!ok) {
            set(c.copy(busy = false))
            return
        }
        done = true
        cancel()
        set(c.copy(busy = false, done = true))
    }

    /** Left the queue another way (queue-left from another device): no more server cards. */
    fun left() {
        done = true
        cancel()
    }

    fun card(): HelpCardState? = card

    /** A new conversation. */
    fun reset() {
        cancel()
        serverSeen = false
        helpKey = ""
        done = false
        opened = null
        if (card != null) set(null)
    }
}
