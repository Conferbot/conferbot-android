package com.conferbot.sdk.services

import android.util.Log
import com.conferbot.sdk.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Knowledge Base Service
 *
 * Handles all knowledge base operations including:
 * - Fetching articles and categories
 * - Searching articles
 * - Tracking article views and engagement
 * - Rating articles
 */
class KnowledgeBaseService(
    private val socketClient: SocketClient,
    private val visitorId: String,
    private val chatSessionId: String
) {
    companion object {
        private const val TAG = "KnowledgeBaseService"

        // Socket events
        const val EVENT_GET_KB_DATA = "get-knowledge-base-data"
        const val EVENT_KB_DATA_RECEIVED = "knowledge-base-data"
        const val EVENT_TRACK_ARTICLE_VIEW = "track-article-view"
        const val EVENT_TRACK_ARTICLE_ENGAGEMENT = "track-article-engagement"
        const val EVENT_RATE_ARTICLE = "rate-article"
        const val EVENT_ARTICLE_RATED = "article-rated"
        const val EVENT_SEARCH_ARTICLES = "search-articles"
        const val EVENT_SEARCH_RESULTS = "search-results"
    }

    private val gson = Gson()

    // State
    private val _knowledgeBaseData = MutableStateFlow<KnowledgeBaseData?>(null)
    val knowledgeBaseData: StateFlow<KnowledgeBaseData?> = _knowledgeBaseData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Track viewed and rated articles in session
    private val viewedArticlesInSession = mutableSetOf<String>()
    private val ratedArticlesInSession = mutableSetOf<String>()

    // Current article engagement tracking
    private var currentEngagement: ArticleEngagementState? = null

    /**
     * Internal engagement tracking state
     */
    private data class ArticleEngagementState(
        val articleId: String,
        val startTime: Long,
        var maxScrollDepth: Int = 0,
        var isCompleted: Boolean = false
    )

    /**
     * Initialize service and setup socket listeners
     */
    fun initialize() {
        setupSocketListeners()
    }

    /**
     * Setup socket event listeners
     */
    private fun setupSocketListeners() {
        socketClient.on(EVENT_KB_DATA_RECEIVED, Emitter.Listener { args ->
            handleKnowledgeBaseData(args)
        })

        socketClient.on(EVENT_ARTICLE_RATED, Emitter.Listener { args ->
            handleArticleRated(args)
        })

        socketClient.on(EVENT_SEARCH_RESULTS, Emitter.Listener { args ->
            handleSearchResults(args)
        })
    }

    /**
     * Handle received knowledge base data
     */
    private fun handleKnowledgeBaseData(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.d(TAG, "Received KB data: $data")

            val categoriesJson = data.optJSONArray("categories")
            val isEnabled = data.optBoolean("enableKnowledgeBase", false)

            if (categoriesJson != null) {
                val categories = mutableListOf<KnowledgeBaseCategory>()

                for (i in 0 until categoriesJson.length()) {
                    val categoryJson = categoriesJson.getJSONObject(i)
                    val category = parseCategoryFromJson(categoryJson)
                    categories.add(category)
                }

                _knowledgeBaseData.value = KnowledgeBaseData(
                    categories = categories,
                    isEnabled = isEnabled
                )
            }

            _isLoading.value = false
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing KB data", e)
            _error.value = "Failed to load knowledge base"
            _isLoading.value = false
        }
    }

    /**
     * Parse category from JSON
     */
    private fun parseCategoryFromJson(json: JSONObject): KnowledgeBaseCategory {
        val articlesJson = json.optJSONArray("articles")
        val articles = mutableListOf<KnowledgeBaseArticle>()

        if (articlesJson != null) {
            for (i in 0 until articlesJson.length()) {
                val articleJson = articlesJson.getJSONObject(i)
                val article = parseArticleFromJson(articleJson)
                articles.add(article)
            }
        }

        return KnowledgeBaseCategory(
            id = json.optString("_id"),
            name = json.optString("name"),
            description = json.optString("description", ""),
            icon = json.optString("icon", null),
            articles = articles,
            order = json.optInt("order", 0),
            isActive = json.optBoolean("isActive", true)
        )
    }

    /**
     * Parse article from JSON
     */
    private fun parseArticleFromJson(json: JSONObject): KnowledgeBaseArticle {
        val authorJson = json.optJSONObject("author")
        val author = authorJson?.let {
            ArticleAuthor(
                id = it.optString("_id"),
                name = it.optString("name"),
                email = it.optString("email", null),
                avatar = it.optString("avatar", null),
                title = it.optString("title", null)
            )
        }

        val tagsJson = json.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsJson != null) {
            for (i in 0 until tagsJson.length()) {
                tags.add(tagsJson.getString(i))
            }
        }

        return KnowledgeBaseArticle(
            id = json.optString("_id"),
            title = json.optString("title"),
            content = json.optString("content"),
            description = json.optString("description", null),
            categoryId = json.optString("categoryId", null),
            categoryName = json.optString("categoryName", null),
            coverImage = json.optString("coverImage", null),
            tags = tags,
            author = author,
            viewCount = json.optInt("viewCount", 0),
            helpfulCount = json.optInt("helpfulCount", 0),
            notHelpfulCount = json.optInt("notHelpfulCount", 0),
            rating = json.optDouble("rating", 0.0).toFloat(),
            isPublished = json.optBoolean("isPublished", true),
            order = json.optInt("order", 0)
        )
    }

    /**
     * Handle article rated response
     */
    private fun handleArticleRated(args: Array<Any>) {
        val data = args.firstOrNull() as? JSONObject ?: return
        val success = data.optBoolean("success", false)
        val articleId = data.optString("articleId")

        if (success && articleId.isNotEmpty()) {
            Log.d(TAG, "Article $articleId rated successfully")
        }
    }

    /**
     * Handle search results
     */
    private fun handleSearchResults(args: Array<Any>) {
        // Search results are handled via callback flow
    }

    // ==================== Public API ====================

    /**
     * Fetch all categories with articles
     */
    fun fetchKnowledgeBase() {
        _isLoading.value = true
        _error.value = null

        val data = JSONObject().apply {
            put("visitorId", visitorId)
            put("sessionId", chatSessionId)
        }

        socketClient.emit(EVENT_GET_KB_DATA, data)
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
     * Search articles locally (client-side search)
     */
    fun searchArticles(query: String): Flow<List<KnowledgeBaseArticle>> {
        return knowledgeBaseData.map { data ->
            if (query.isBlank()) {
                emptyList()
            } else {
                data?.searchArticles(query)?.map { it.article } ?: emptyList()
            }
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
        socketClient.off(EVENT_KB_DATA_RECEIVED)
        socketClient.off(EVENT_ARTICLE_RATED)
        socketClient.off(EVENT_SEARCH_RESULTS)
    }
}
