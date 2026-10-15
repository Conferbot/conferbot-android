package com.conferbot.sdk.services

import android.util.Log
import com.conferbot.sdk.core.helpcentre.HelpCentre
import com.conferbot.sdk.models.*
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Knowledge Base Service: the help centre's data and the calls it makes.
 *
 * The categories and articles come with the bot's data (socket
 * `fetched-chatbot-data` -> `knowledgeBaseData`, see core/helpcentre/HelpCentre.kt)
 * and are handed in as [knowledgeBase]; nothing is asked for to list them.
 * An article's body is fetched when it opens (`get-kb-article`), once per
 * session, and search adds the server's matches inside article text
 * (`search-kb-articles`), as the web widget does (ts/knowledgeBase/kbContent.ts).
 * Views, reading and votes go to `track-article-view`,
 * `track-article-engagement` and `rate-article`.
 */
class KnowledgeBaseService(
    private val socketClient: SocketClient,
    private val visitorId: String,
    private val chatSessionId: String,
    private val chatbotId: String = "",
    knowledgeBase: StateFlow<KnowledgeBaseData?> = MutableStateFlow(null)
) {
    companion object {
        private const val TAG = "KnowledgeBaseService"

        // Socket events (embed-server src/socket/handlers/chatbotHandler.js)
        const val EVENT_GET_ARTICLE = "get-kb-article"
        const val EVENT_SEARCH_ARTICLES = "search-kb-articles"
        const val EVENT_TRACK_ARTICLE_VIEW = "track-article-view"
        const val EVENT_TRACK_ARTICLE_ENGAGEMENT = "track-article-engagement"
        const val EVENT_RATE_ARTICLE = "rate-article"
        const val EVENT_ARTICLE_RATED = "article-rated"

        private const val FETCH_TIMEOUT_MS = 8_000L
        private const val SEARCH_TIMEOUT_MS = 5_000L
    }

    /** Categories, recent articles: from the bot's data. */
    val knowledgeBaseData: StateFlow<KnowledgeBaseData?> = knowledgeBase

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // articleId -> body HTML, for the session.
    private val _bodies = MutableStateFlow<Map<String, String>>(emptyMap())
    val articleBodies: StateFlow<Map<String, String>> = _bodies.asStateFlow()

    // Articles whose body could not be loaded (the view says so instead of spinning).
    private val _failedBodies = MutableStateFlow<Set<String>>(emptySet())
    val failedBodies: StateFlow<Set<String>> = _failedBodies.asStateFlow()

    private val inflight = mutableSetOf<String>()
    private val remoteSearches = mutableMapOf<String, List<KnowledgeBaseArticle>>()

    // Track viewed and rated articles in session
    private val viewedArticlesInSession = mutableSetOf<String>()
    private val ratedArticlesInSession = mutableSetOf<String>()

    // Current article engagement tracking
    private var currentEngagement: ArticleEngagementState? = null

    private data class ArticleEngagementState(
        val articleId: String,
        val startTime: Long,
        var maxScrollDepth: Int = 0,
        var isCompleted: Boolean = false
    )

    /** Kept for callers of the old API: there is nothing to listen for. */
    fun initialize() {}

    // ==================== Public API ====================

    /**
     * The help centre arrives with the bot's data; there is no separate
     * request (the old `get-knowledge-base-data` had no server handler).
     * Kept so existing callers still compile.
     */
    fun fetchKnowledgeBase() {
        _isLoading.value = false
        _error.value = null
    }

    /**
     * The article's body: the inline one, the cached one, or fetched now
     * with `get-kb-article`. [articleBodies] gets it, or [failedBodies] the id.
     */
    fun loadArticleBody(articleId: String) {
        if (articleId.isBlank()) return
        val inline = knowledgeBaseData.value?.getArticleById(articleId)?.content
        if (inline != null) {
            _bodies.value = _bodies.value + (articleId to inline)
            return
        }
        synchronized(inflight) {
            if (_bodies.value.containsKey(articleId) || !inflight.add(articleId)) return
        }
        _failedBodies.value = _failedBodies.value - articleId
        val payload = JSONObject().apply {
            put("chatbotId", chatbotId)
            put("articleId", articleId)
        }
        socketClient.emitWithAck(EVENT_GET_ARTICLE, payload, FETCH_TIMEOUT_MS) { reply ->
            synchronized(inflight) { inflight.remove(articleId) }
            val article = HelpCentre.parseArticleReply((reply as? JSONObject)?.let { toMap(it) })
            val body = article?.content
            if (body != null) {
                _bodies.value = _bodies.value + (articleId to body)
            } else {
                Log.w(TAG, "No body for article $articleId")
                _failedBodies.value = _failedBodies.value + articleId
            }
        }
    }

    /** The article with its body when it has arrived. */
    fun withBody(article: KnowledgeBaseArticle): KnowledgeBaseArticle =
        _bodies.value[article.id]?.let { article.copy(content = it) } ?: article

    /** Matches inside article text from the server; null on failure (not cached). */
    private suspend fun remoteSearch(query: String): List<KnowledgeBaseArticle>? {
        val key = query.trim().lowercase()
        if (key.length < 2) return emptyList()
        remoteSearches[key]?.let { return it }
        val payload = JSONObject().apply {
            put("chatbotId", chatbotId)
            put("query", key)
            put("limit", 10)
        }
        val reply = suspendCancellableCoroutine<Any?> { cont ->
            socketClient.emitWithAck(EVENT_SEARCH_ARTICLES, payload, SEARCH_TIMEOUT_MS) { if (cont.isActive) cont.resume(it) }
        }
        val hits = HelpCentre.parseSearchReply((reply as? JSONObject)?.let { toMap(it) }) ?: return null
        remoteSearches[key] = hits
        return hits
    }

    /**
     * Get all articles as Flow
     */
    fun getArticles(): Flow<List<KnowledgeBaseArticle>> {
        return knowledgeBaseData.map { data ->
            data?.getAllArticles() ?: emptyList()
        }
    }

    /**
     * Get all categories as Flow
     */
    fun getCategories(): Flow<List<KnowledgeBaseCategory>> {
        return knowledgeBaseData.map { data ->
            data?.categories ?: emptyList()
        }
    }

    /**
     * Search: titles, descriptions, tags (and bodies already here) at once,
     * then the server's matches inside article text added after them.
     */
    fun searchArticles(query: String): Flow<List<KnowledgeBaseArticle>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val data = knowledgeBaseData.value
        val local = data?.searchArticles(query)?.map { it.article } ?: emptyList()
        emit(local)
        if (query.trim().length >= 2) {
            val remote = remoteSearch(query)
            if (!remote.isNullOrEmpty()) emit(HelpCentre.mergeSearch(local, remote, data))
        }
    }

    /**
     * Search articles with match information
     */
    fun searchArticlesWithScore(query: String): Flow<List<ArticleSearchResult>> {
        return knowledgeBaseData.map { data ->
            if (query.isBlank()) {
                emptyList()
            } else {
                data?.searchArticles(query) ?: emptyList()
            }
        }
    }

    /**
     * Get article by ID
     */
    fun getArticleById(id: String): Flow<KnowledgeBaseArticle?> {
        return knowledgeBaseData.map { data ->
            data?.getArticleById(id)
        }
    }

    /**
     * Get category by ID
     */
    fun getCategoryById(id: String): Flow<KnowledgeBaseCategory?> {
        return knowledgeBaseData.map { data ->
            data?.getCategoryById(id)
        }
    }

    /**
     * Get related articles for a given article
     */
    fun getRelatedArticles(article: KnowledgeBaseArticle, limit: Int = 3): Flow<List<KnowledgeBaseArticle>> {
        return knowledgeBaseData.map { data ->
            data?.getRelatedArticles(article, limit) ?: emptyList()
        }
    }

    /**
     * Track article view (only once per session)
     */
    fun trackArticleView(article: KnowledgeBaseArticle) {
        if (viewedArticlesInSession.contains(article.id)) {
            return // Already viewed in this session
        }

        viewedArticlesInSession.add(article.id)

        val data = JSONObject().apply {
            put("articleId", article.id)
            put("visitorId", visitorId)
            put("sessionId", chatSessionId)
            put("device", "mobile")
        }

        socketClient.emit(EVENT_TRACK_ARTICLE_VIEW, data)
        Log.d(TAG, "Tracked view for article: ${article.id}")
    }

    /**
     * Start tracking engagement for an article
     */
    fun startArticleEngagement(articleId: String) {
        // Send previous engagement if exists
        sendCurrentEngagement()

        // Start new engagement tracking
        currentEngagement = ArticleEngagementState(
            articleId = articleId,
            startTime = System.currentTimeMillis()
        )

        Log.d(TAG, "Started engagement tracking for article: $articleId")
    }

    /**
     * Update scroll depth during article reading
     */
    fun updateScrollDepth(scrollDepth: Int) {
        currentEngagement?.let { engagement ->
            if (scrollDepth > engagement.maxScrollDepth) {
                engagement.maxScrollDepth = scrollDepth
                // Mark as completed if scrolled past 90%
                if (scrollDepth >= 90) {
                    engagement.isCompleted = true
                }
            }
        }
    }

    /**
     * Send current article engagement data to server
     */
    fun sendCurrentEngagement() {
        currentEngagement?.let { engagement ->
            val timeSpent = ((System.currentTimeMillis() - engagement.startTime) / 1000).toInt()

            // Only send if user spent at least 2 seconds
            if (timeSpent < 2) {
                currentEngagement = null
                return
            }

            val data = JSONObject().apply {
                put("articleId", engagement.articleId)
                put("visitorId", visitorId)
                put("sessionId", chatSessionId)
                put("timeSpent", timeSpent)
                put("scrollDepth", engagement.maxScrollDepth)
                put("isCompleted", engagement.isCompleted)
                put("device", "mobile")
            }

            socketClient.emit(EVENT_TRACK_ARTICLE_ENGAGEMENT, data)
            Log.d(TAG, "Sent engagement for article: ${engagement.articleId}, time: ${timeSpent}s, scroll: ${engagement.maxScrollDepth}%")

            currentEngagement = null
        }
    }

    /**
     * Rate an article (helpful/not helpful)
     * Can only rate once per session per article
     */
    fun rateArticle(articleId: String, helpful: Boolean): Flow<Boolean> = callbackFlow {
        if (ratedArticlesInSession.contains(articleId)) {
            trySend(false)
            close()
            return@callbackFlow
        }

        ratedArticlesInSession.add(articleId)

        val data = JSONObject().apply {
            put("articleId", articleId)
            put("visitorId", visitorId)
            put("sessionId", chatSessionId)
            put("helpful", helpful)
            put("rating", if (helpful) 5 else 1)
        }

        val listener = Emitter.Listener { args ->
            val response = args.firstOrNull() as? JSONObject
            val success = response?.optBoolean("success", false) ?: false
            trySend(success)
            close()
        }

        socketClient.on(EVENT_ARTICLE_RATED, listener)
        socketClient.emit(EVENT_RATE_ARTICLE, data)

        awaitClose {
            socketClient.off(EVENT_ARTICLE_RATED, listener)
        }
    }

    /**
     * Check if article has been rated in this session
     */
    fun hasRatedArticle(articleId: String): Boolean {
        return ratedArticlesInSession.contains(articleId)
    }

    /**
     * Check if article has been viewed in this session
     */
    fun hasViewedArticle(articleId: String): Boolean {
        return viewedArticlesInSession.contains(articleId)
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Dispose of service resources
     */
    fun dispose() {
        sendCurrentEngagement()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toMap(json: JSONObject): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        json.keys().forEach { k -> val key = k as String; out[key] = plain(json.opt(key)) }
        return out
    }

    private fun plain(v: Any?): Any? = when (v) {
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { plain(v.opt(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}
