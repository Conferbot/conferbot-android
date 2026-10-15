package com.conferbot.sdk.core.helpcentre

import com.conferbot.sdk.models.ArticleAuthor
import com.conferbot.sdk.models.KnowledgeBaseArticle
import com.conferbot.sdk.models.KnowledgeBaseCategory
import com.conferbot.sdk.models.KnowledgeBaseData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The help centre rules and the server's help centre payload, as the web
 * widget has them (conferbot-widget/src/core/helpCentre.ts and
 * ts/knowledgeBase/kbContent.ts). Pure: plain maps in, models out, so the
 * tests run it on a payload captured from the embed server.
 *
 *  1. The help centre is on only when `enableKnowledgeBase` is true. A help
 *     centre with no published article counts as off (no tab, no list).
 *  2. Recent articles show while it is on, unless the owner hid them
 *     (`hideRecentArticles`). The app has no home screen, so the list sits
 *     at the top of the help centre.
 *  3. An article opens only while the help centre is on. An article the
 *     payload does not list (an AI source) opens from its id and title; its
 *     body loads on open.
 *  4. Drafts never show: an article without an id, or one that says it is
 *     anything but published, is dropped.
 *
 * Where the data comes from: socket `fetched-chatbot-data` carries
 * `knowledgeBaseData: {recentArticles, categories}` without the bodies. A
 * body comes from `get-kb-article {chatbotId, articleId}` (ack
 * `{success, articleId, article}`), body search from `search-kb-articles
 * {chatbotId, query, limit}` (ack `{success, query, articles[{_id, title,
 * description, category, snippet}]}`). An older server sent bodies inline:
 * an article that has `content` is used as is.
 */
object HelpCentre {

    /** Rule 1: the owner's switch. */
    fun on(customizations: Map<String, Any?>?): Boolean =
        customizations?.get("enableKnowledgeBase") == true

    /** Rule 1, all of it: on, and something published to show. */
    fun visible(customizations: Map<String, Any?>?, data: KnowledgeBaseData?): Boolean =
        on(customizations) && data != null && data.hasArticles

    /** Rule 2: the recent articles list. */
    fun showsRecent(customizations: Map<String, Any?>?, data: KnowledgeBaseData?): Boolean =
        visible(customizations, data) && customizations?.get("hideRecentArticles") != true &&
            data!!.recentArticles.isNotEmpty()

    /** Rule 3: may this article open in the help centre? */
    fun canOpen(customizations: Map<String, Any?>?, data: KnowledgeBaseData?, articleId: String?): Boolean =
        visible(customizations, data) && !articleId.isNullOrBlank()

    /**
     * `knowledgeBaseData` from `fetched-chatbot-data`. Empty while the help
     * centre is off, whatever an older server still sends (rule 1).
     */
    @Suppress("UNCHECKED_CAST")
    fun parse(knowledgeBaseData: Map<String, Any?>?, customizations: Map<String, Any?>?): KnowledgeBaseData {
        val enabled = on(customizations)
        if (!enabled || knowledgeBaseData == null) return KnowledgeBaseData(isEnabled = enabled)
        val categories = list(knowledgeBaseData["categories"]).mapNotNull { raw ->
            val m = raw as? Map<String, Any?> ?: return@mapNotNull null
            val id = str(m["_id"]) ?: return@mapNotNull null
            val name = str(m["name"]) ?: ""
            val articles = list(m["articles"]).mapNotNull { a ->
                (a as? Map<String, Any?>)?.let { parseArticle(it, id, name) }
            }
            KnowledgeBaseCategory(
                id = id,
                name = name,
                description = str(m["description"]) ?: "",
                icon = str(m["icon"]),
                color = str(m["color"]),
                articles = articles,
                order = num(m["order"])?.toInt() ?: 0
            )
        }.filter { it.articles.isNotEmpty() }
        val recent = list(knowledgeBaseData["recentArticles"]).mapNotNull { a ->
            (a as? Map<String, Any?>)?.let { parseArticle(it) }
        }.map { r ->
            // The category's copy knows its topic name even when the recent
            // row's category was not populated.
            if (r.categoryName != null) r else categories.firstOrNull { it.id == r.categoryId }?.let { r.copy(categoryName = it.name) } ?: r
        }
        return KnowledgeBaseData(
            categories = categories,
            isEnabled = true,
            recentArticles = recent,
            contentIncluded = knowledgeBaseData["contentIncluded"] == true
        )
    }

    /**
     * One article row. `category` is an id (in a category's list) or
     * `{_id, name}` (recent, search, get-kb-article). Rule 4 drops drafts.
     */
    @Suppress("UNCHECKED_CAST")
    fun parseArticle(m: Map<String, Any?>, categoryId: String? = null, categoryName: String? = null): KnowledgeBaseArticle? {
        val id = str(m["_id"]) ?: return null
        val status = m["reviewStatus"]
        if (status != null && status != "published") return null
        val cat = m["category"]
        val catId = (cat as? Map<String, Any?>)?.let { str(it["_id"]) } ?: str(cat) ?: str(m["categoryId"]) ?: categoryId
        val catName = (cat as? Map<String, Any?>)?.let { str(it["name"]) } ?: str(m["categoryName"])
            ?: categoryName.takeIf { catId == categoryId }
        val author = (m["author"] as? Map<String, Any?>)?.let { a ->
            str(a["name"])?.let { ArticleAuthor(id = str(a["_id"]), name = it, avatar = str(a["avatar"]), title = str(a["title"])) }
        }
        return KnowledgeBaseArticle(
            id = id,
            title = str(m["title"]) ?: "",
            content = m["content"] as? String,
            description = str(m["description"]),
            categoryId = catId,
            categoryName = catName,
            coverImage = str(m["coverImage"]),
            tags = list(m["tags"]).mapNotNull { it as? String },
            author = author,
            publishedDate = date(m["publishedDate"]),
            updatedAt = date(m["updatedAt"]),
            viewCount = num(m["viewCount"])?.toInt() ?: 0,
            helpfulCount = num(m["helpfulCount"])?.toInt() ?: 0,
            notHelpfulCount = num(m["notHelpfulCount"])?.toInt() ?: 0,
            order = num(m["order"])?.toInt() ?: 0,
            slug = str(m["slug"]),
            readingTime = num(m["readingTime"])?.toInt()?.takeIf { it > 0 },
            snippet = str(m["snippet"])
        )
    }

    /** The `get-kb-article` reply: the article with its body, or null. */
    @Suppress("UNCHECKED_CAST")
    fun parseArticleReply(reply: Map<String, Any?>?): KnowledgeBaseArticle? {
        if (reply?.get("success") != true) return null
        val article = reply["article"] as? Map<String, Any?> ?: return null
        if (article["content"] !is String) return null
        return parseArticle(article)
    }

    /** The `search-kb-articles` reply: matches inside article text (rule 4 applies). */
    @Suppress("UNCHECKED_CAST")
    fun parseSearchReply(reply: Map<String, Any?>?): List<KnowledgeBaseArticle>? {
        if (reply?.get("success") != true) return null
        return list(reply["articles"]).mapNotNull { (it as? Map<String, Any?>)?.let { a -> parseArticle(a) } }
    }

    /**
     * An article by id: in a category first (it has the topic for Back), then
     * the recent list, else one built from the id and title (rule 3).
     */
    fun findArticle(data: KnowledgeBaseData?, articleId: String, title: String? = null): Pair<KnowledgeBaseArticle?, KnowledgeBaseCategory?> {
        data?.categories?.forEach { c ->
            c.articles.firstOrNull { it.id == articleId }?.let { return it.copy(categoryId = c.id, categoryName = c.name) to c }
        }
        data?.recentArticles?.firstOrNull { it.id == articleId }?.let { return it to data.getCategoryById(it.categoryId ?: "") }
        val fallback = title?.takeIf { it.isNotBlank() }?.let { KnowledgeBaseArticle(id = articleId, title = it) }
        return fallback to null
    }

    /**
     * Local matches (title, description, tags, a body that has arrived)
     * first, then the server's matches inside article text, each article once.
     */
    fun mergeSearch(local: List<KnowledgeBaseArticle>, remote: List<KnowledgeBaseArticle>?, data: KnowledgeBaseData?): List<KnowledgeBaseArticle> {
        if (remote.isNullOrEmpty()) return local
        val seen = local.map { it.id }.toMutableSet()
        val extra = remote.filter { seen.add(it.id) }.map { hit ->
            val known = data?.getArticleById(hit.id)
            known?.copy(snippet = hit.snippet ?: known.snippet) ?: hit
        }
        return local + extra
    }

    // ---- plain values ----

    private fun list(v: Any?): List<Any?> = (v as? List<*>) ?: emptyList<Any?>()

    private fun str(v: Any?): String? = (v as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun num(v: Any?): Number? = when (v) {
        is Number -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private val isoFormats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")

    private fun date(v: Any?): Date? {
        val s = v as? String ?: return null
        for (f in isoFormats) {
            val parsed = runCatching {
                SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
