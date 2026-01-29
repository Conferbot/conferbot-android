package com.conferbot.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Knowledge Base category model
 * Matches the web widget's category structure
 */
data class KnowledgeBaseCategory(
    @SerializedName("_id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("icon")
    val icon: String? = null,

    @SerializedName("articles")
    val articles: List<KnowledgeBaseArticle> = emptyList(),

    @SerializedName("order")
    val order: Int = 0,

    @SerializedName("isActive")
    val isActive: Boolean = true
) {
    /**
     * Get the article count for this category
     */
    val articleCount: Int
        get() = articles.size

    /**
     * Get formatted article count text
     */
    fun getArticleCountText(): String {
        return if (articleCount == 1) "1 article" else "$articleCount articles"
    }

    /**
     * Get default icon based on category name keywords
     */
    fun getDefaultIcon(): CategoryIcon {
        val nameLower = name.lowercase()
        return when {
            nameLower.contains("start") || nameLower.contains("begin") || nameLower.contains("intro") -> CategoryIcon.GETTING_STARTED
            nameLower.contains("setting") || nameLower.contains("config") || nameLower.contains("setup") -> CategoryIcon.SETTINGS
            nameLower.contains("faq") || nameLower.contains("question") || nameLower.contains("help") -> CategoryIcon.FAQ
            nameLower.contains("guide") || nameLower.contains("tutorial") || nameLower.contains("how") -> CategoryIcon.GUIDE
            nameLower.contains("account") || nameLower.contains("profile") || nameLower.contains("user") -> CategoryIcon.ACCOUNT
            nameLower.contains("billing") || nameLower.contains("payment") || nameLower.contains("price") -> CategoryIcon.BILLING
            nameLower.contains("security") || nameLower.contains("privacy") || nameLower.contains("safe") -> CategoryIcon.SECURITY
            nameLower.contains("integrat") || nameLower.contains("connect") || nameLower.contains("api") -> CategoryIcon.INTEGRATION
            nameLower.contains("troubleshoot") || nameLower.contains("issue") || nameLower.contains("problem") -> CategoryIcon.TROUBLESHOOTING
            else -> CategoryIcon.DOCUMENT
        }
    }
}

/**
 * Predefined category icons
 */
enum class CategoryIcon {
    GETTING_STARTED,
    SETTINGS,
    FAQ,
    GUIDE,
    ACCOUNT,
    BILLING,
    SECURITY,
    INTEGRATION,
    TROUBLESHOOTING,
    DOCUMENT
}

/**
 * Knowledge Base data container
 * Contains all categories with their articles
 */
data class KnowledgeBaseData(
    @SerializedName("categories")
    val categories: List<KnowledgeBaseCategory> = emptyList(),

    @SerializedName("enableKnowledgeBase")
    val isEnabled: Boolean = false
) {
    /**
     * Get all articles from all categories
     */
    fun getAllArticles(): List<KnowledgeBaseArticle> {
        return categories.flatMap { category ->
            category.articles.map { article ->
                article.copy(
                    categoryId = category.id,
                    categoryName = category.name
                )
            }
        }
    }

    /**
     * Search articles across all categories
     */
    fun searchArticles(query: String): List<ArticleSearchResult> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase().trim()
        val results = mutableListOf<ArticleSearchResult>()

        getAllArticles().forEach { article ->
            val matchedFields = mutableListOf<String>()
            var score = 0f

            // Title match (highest priority)
            if (article.title.lowercase().contains(queryLower)) {
                matchedFields.add("title")
                score += 3f
                // Bonus for exact match
                if (article.title.lowercase() == queryLower) score += 2f
            }

            // Description match
            article.description?.let { desc ->
                if (desc.lowercase().contains(queryLower)) {
                    matchedFields.add("description")
                    score += 2f
                }
            }

            // Content match
            val contentText = article.content.replace(Regex("<[^>]*>"), "").lowercase()
            if (contentText.contains(queryLower)) {
                matchedFields.add("content")
                score += 1f
            }

            // Tags match
            if (article.tags.any { it.lowercase().contains(queryLower) }) {
                matchedFields.add("tags")
                score += 1.5f
            }

            if (matchedFields.isNotEmpty()) {
                results.add(
                    ArticleSearchResult(
                        article = article,
                        matchScore = score,
                        matchedFields = matchedFields
                    )
                )
            }
        }

        return results.sortedByDescending { it.matchScore }
    }

    /**
     * Get related articles based on category and tags
     */
    fun getRelatedArticles(
        currentArticle: KnowledgeBaseArticle,
        limit: Int = 3
    ): List<KnowledgeBaseArticle> {
        val allArticles = getAllArticles()

        return allArticles
            .filter { it.id != currentArticle.id }
            .sortedByDescending { article ->
                var score = 0

                // Same category gets higher score
                if (article.categoryId == currentArticle.categoryId) {
                    score += 10
                }

                // Matching tags
                val matchingTags = article.tags.intersect(currentArticle.tags.toSet())
                score += matchingTags.size * 2

                score
            }
            .take(limit)
    }

    /**
     * Get article by ID
     */
    fun getArticleById(articleId: String): KnowledgeBaseArticle? {
        return getAllArticles().find { it.id == articleId }
    }

    /**
     * Get category by ID
     */
    fun getCategoryById(categoryId: String): KnowledgeBaseCategory? {
        return categories.find { it.id == categoryId }
    }
}
