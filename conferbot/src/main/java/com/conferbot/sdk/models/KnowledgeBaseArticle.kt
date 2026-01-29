package com.conferbot.sdk.models

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Knowledge Base article model
 * Matches the web widget's article structure
 */
data class KnowledgeBaseArticle(
    @SerializedName("_id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("categoryId")
    val categoryId: String? = null,

    @SerializedName("categoryName")
    val categoryName: String? = null,

    @SerializedName("coverImage")
    val coverImage: String? = null,

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName("author")
    val author: ArticleAuthor? = null,

    @SerializedName("publishedDate")
    val publishedDate: Date? = null,

    @SerializedName("updatedAt")
    val updatedAt: Date? = null,

    @SerializedName("viewCount")
    val viewCount: Int = 0,

    @SerializedName("helpfulCount")
    val helpfulCount: Int = 0,

    @SerializedName("notHelpfulCount")
    val notHelpfulCount: Int = 0,

    @SerializedName("rating")
    val rating: Float = 0f,

    @SerializedName("isPublished")
    val isPublished: Boolean = true,

    @SerializedName("order")
    val order: Int = 0
) {
    /**
     * Calculate reading time based on content length
     * Average reading speed: 200 words per minute
     */
    fun calculateReadingTime(): String {
        val text = content.replace(Regex("<[^>]*>"), "") // Strip HTML tags
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val minutes = (wordCount / 200).coerceAtLeast(1)
        return if (minutes == 1) "1 min read" else "$minutes min read"
    }

    /**
     * Get a truncated description for preview
     */
    fun getPreviewDescription(maxLength: Int = 120): String {
        val desc = description ?: content.replace(Regex("<[^>]*>"), "")
        return if (desc.length > maxLength) {
            desc.substring(0, maxLength).trimEnd() + "..."
        } else {
            desc
        }
    }
}

/**
 * Article author information
 */
data class ArticleAuthor(
    @SerializedName("_id")
    val id: String? = null,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,

    @SerializedName("title")
    val title: String? = null
)

/**
 * Article rating request
 */
data class ArticleRatingRequest(
    val articleId: String,
    val visitorId: String,
    val sessionId: String,
    val helpful: Boolean,
    val rating: Int
)

/**
 * Article view tracking request
 */
data class ArticleViewRequest(
    val articleId: String,
    val visitorId: String,
    val sessionId: String,
    val referrer: String? = null,
    val device: String = "mobile"
)

/**
 * Article engagement tracking request
 */
data class ArticleEngagementRequest(
    val articleId: String,
    val visitorId: String,
    val sessionId: String,
    val timeSpent: Int, // in seconds
    val scrollDepth: Int, // percentage 0-100
    val isCompleted: Boolean,
    val device: String = "mobile"
)

/**
 * Search result with highlighted matches
 */
data class ArticleSearchResult(
    val article: KnowledgeBaseArticle,
    val matchScore: Float = 0f,
    val matchedFields: List<String> = emptyList()
)
