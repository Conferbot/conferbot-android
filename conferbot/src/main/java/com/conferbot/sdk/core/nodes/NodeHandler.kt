package com.conferbot.sdk.core.nodes

import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry

/**
 * Result of processing a node
 */
sealed class NodeResult {
    /**
     * Node requires UI display and user interaction
     * The handler produces UI state for rendering
     */
    data class DisplayUI(
        val uiState: NodeUIState
    ) : NodeResult()

    /**
     * Node processed successfully, proceed to next node
     */
    data class Proceed(
        val targetPort: String? = null  // null = default "source", "source-0", "source-1", etc.
    ) : NodeResult()

    /**
     * Node processed with delay before proceeding
     */
    data class DelayedProceed(
        val delayMs: Long,
        val targetPort: String? = null
    ) : NodeResult()

    /**
     * Jump to specific node by ID
     */
    data class JumpTo(
        val targetNodeId: String
    ) : NodeResult()

    /**
     * Error occurred during processing
     */
    data class Error(
        val message: String,
        val shouldProceed: Boolean = true
    ) : NodeResult()

    /**
     * Execute a native integration via socket and wait for the result
     * Used for Stripe, Google integrations, etc. that require server-side processing
     */
    data class ExecuteIntegration(
        val nodeType: String,
        val nodeId: String,
        val nodeData: Map<String, Any?>,
        val onResult: (IntegrationResultData) -> NodeResult
    ) : NodeResult()
}

/**
 * Data class for integration result callback
 */
data class IntegrationResultData(
    val success: Boolean,
    val error: String? = null,
    val data: Map<String, Any?>? = null,
    val message: String? = null,
    val answerVariable: String? = null,
    val answerValue: Any? = null
)

/**
 * UI state for nodes that require display
 */
sealed class NodeUIState {
    /**
     * Simple text message from bot
     */
    data class Message(
        val text: String,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Image display
     */
    data class Image(
        val url: String,
        val caption: String? = null,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Video display
     */
    data class Video(
        val url: String,
        val caption: String? = null,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Audio playback
     */
    data class Audio(
        val url: String,
        val caption: String? = null,
        val nodeId: String
    ) : NodeUIState()

    /**
     * File download
     */
    data class File(
        val url: String,
        val fileName: String,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Text input field
     */
    data class TextInput(
        val questionText: String,
        val inputType: InputType,
        val placeholder: String? = null,
        val validationRegex: String? = null,
        val errorMessage: String? = null,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        enum class InputType {
            TEXT, NAME, EMAIL, PHONE, NUMBER, URL, LOCATION
        }
    }

    /**
     * File upload
     */
    data class FileUpload(
        val questionText: String,
        val maxSizeMb: Int = 5,
        val allowedTypes: List<String>? = null,
        val allowMultiple: Boolean = false,
        val maxFiles: Int = 5,
        val useSignedUrl: Boolean = true,
        val nodeId: String,
        val answerKey: String,
        val chatSessionId: String? = null
    ) : NodeUIState()

    /**
     * Single choice selection (buttons)
     */
    data class SingleChoice(
        val questionText: String?,
        val choices: List<Choice>,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        data class Choice(
            val id: String,
            val text: String,
            val imageUrl: String? = null,
            val targetPort: String? = null
        )
    }

    /**
     * Multiple choice selection (checkboxes)
     */
    data class MultipleChoice(
        val questionText: String?,
        val options: List<Option>,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        data class Option(
            val id: String,
            val text: String
        )
    }

    /**
     * Rating selection (stars/numbers)
     */
    data class Rating(
        val questionText: String?,
        val ratingType: RatingType,
        val minValue: Int,
        val maxValue: Int,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        enum class RatingType {
            STAR, NUMBER, SMILEY, OPINION_SCALE
        }
    }

    /**
     * Dropdown select
     */
    data class Dropdown(
        val questionText: String?,
        val options: List<Option>,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        data class Option(
            val id: String,
            val text: String
        )
    }

    /**
     * Range slider
     */
    data class Range(
        val questionText: String?,
        val minValue: Int,
        val maxValue: Int,
        val defaultValue: Int? = null,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState()

    /**
     * Calendar/Date picker
     */
    data class Calendar(
        val questionText: String?,
        val showTimeSelection: Boolean,
        val timezone: String?,
        val availableSlots: List<TimeSlot>? = null,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        data class TimeSlot(
            val date: String,
            val time: String?,
            val available: Boolean = true
        )
    }

    /**
     * Image choice grid
     */
    data class ImageChoice(
        val questionText: String?,
        val images: List<ImageOption>,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState() {
        data class ImageOption(
            val id: String,
            val imageUrl: String,
            val label: String,
            val targetPort: String? = null
        )
    }

    /**
     * Quiz question
     */
    data class Quiz(
        val questionText: String,
        val options: List<String>,
        val correctAnswerIndex: Int,
        val nodeId: String,
        val answerKey: String
    ) : NodeUIState()

    /**
     * Multiple sequential questions
     */
    data class MultipleQuestions(
        val questions: List<Question>,
        val currentIndex: Int,
        val nodeId: String
    ) : NodeUIState() {
        data class Question(
            val questionText: String,
            val answerType: String,  // "name", "email", "phone", "text"
            val answerKey: String
        )
    }

    /**
     * Human handover - pre-chat questions or waiting for agent
     */
    data class HumanHandover(
        val state: HandoverState,
        val preChatQuestions: List<PreChatQuestion>? = null,
        val currentQuestionIndex: Int = 0,
        val handoverMessage: String? = null,
        val maxWaitTime: Int? = null,  // minutes
        val agentName: String? = null,
        val nodeId: String
    ) : NodeUIState() {
        enum class HandoverState {
            PRE_CHAT_QUESTIONS,
            WAITING_FOR_AGENT,
            AGENT_CONNECTED,
            NO_AGENTS_AVAILABLE,
            POST_CHAT_SURVEY
        }

        data class PreChatQuestion(
            val id: String,
            val questionText: String,
            val answerType: String,
            val answerKey: String
        )
    }

    /**
     * HTML content
     */
    data class Html(
        val htmlContent: String,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Payment/Stripe
     */
    data class Payment(
        val paymentUrl: String,
        val amount: Double? = null,
        val currency: String? = null,
        val description: String? = null,
        val nodeId: String
    ) : NodeUIState()

    /**
     * External redirect
     */
    data class Redirect(
        val url: String,
        val openInNewTab: Boolean = true,
        val nodeId: String
    ) : NodeUIState()

    /**
     * Post-chat survey for human handover
     * Displays survey questions after agent chat ends
     */
    data class PostChatSurvey(
        val questions: List<SurveyQuestion>,
        val currentQuestionIndex: Int = 0,
        val nodeId: String,
        val surveyTitle: String? = null,
        val surveyDescription: String? = null,
        val isSubmitting: Boolean = false
    ) : NodeUIState() {
        /**
         * Individual survey question
         */
        data class SurveyQuestion(
            val id: String,
            val type: SurveyQuestionType,
            val question: String,
            val options: List<String>? = null,
            val required: Boolean = true,
            val minRating: Int = 1,
            val maxRating: Int = 5
        )

        /**
         * Types of survey questions
         */
        enum class SurveyQuestionType {
            RATING,      // 1-5 star rating
            TEXT,        // Free text feedback
            CHOICE,      // Single choice selection
            MULTI_CHOICE // Multiple choice selection
        }
    }

    /**
     * Survey response data
     */
    data class SurveyResponse(
        val questionId: String,
        val value: Any  // Int for rating, String for text, String for choice, List<String> for multi-choice
    )
}

/**
 * Base interface for all node handlers
 */
interface NodeHandler {
    /**
     * The node type this handler processes
     */
    val nodeType: String

    /**
     * Process the node and return result
     * @param nodeData The node's data/configuration
     * @param nodeId The unique node ID
     * @return NodeResult indicating what to do next
     */
    suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult

    /**
     * Handle user response for interactive nodes
     * @param response The user's response
     * @param nodeData The node's data/configuration
     * @param nodeId The unique node ID
     * @return NodeResult indicating what to do next
     */
    suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult = NodeResult.Proceed()
}

/**
 * Base class with common functionality for handlers
 */
abstract class BaseNodeHandler : NodeHandler {

    protected val state: ChatState = ChatState

    /**
     * Record a user response
     */
    protected fun recordResponse(
        nodeId: String,
        shape: String,
        text: String?,
        type: String? = null,
        additionalData: Map<String, Any?> = emptyMap()
    ) {
        val entry = RecordEntry(
            id = nodeId,
            shape = shape,
            type = type,
            text = text,
            data = additionalData.toMutableMap()
        )
        state.pushToRecord(entry)
    }

    /**
     * Get string from nodeData with default
     */
    protected fun getString(nodeData: Map<String, Any?>, key: String, default: String = ""): String {
        return nodeData[key]?.toString() ?: default
    }

    /**
     * Get int from nodeData with default
     */
    protected fun getInt(nodeData: Map<String, Any?>, key: String, default: Int = 0): Int {
        return when (val value = nodeData[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    /**
     * Get boolean from nodeData with default
     */
    protected fun getBoolean(nodeData: Map<String, Any?>, key: String, default: Boolean = false): Boolean {
        return when (val value = nodeData[key]) {
            is Boolean -> value
            is String -> value.lowercase() == "true"
            else -> default
        }
    }

    /**
     * Get list from nodeData
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getList(nodeData: Map<String, Any?>, key: String): List<T> {
        return (nodeData[key] as? List<T>) ?: emptyList()
    }

    /**
     * Get map from nodeData
     */
    @Suppress("UNCHECKED_CAST")
    protected fun getMap(nodeData: Map<String, Any?>, key: String): Map<String, Any?> {
        return (nodeData[key] as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * Strip HTML tags and decode entities
     */
    protected fun stripHtml(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    /**
     * Validate email format
     */
    protected fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email.trim())
    }

    /**
     * Validate phone number format
     */
    protected fun isValidPhone(phone: String): Boolean {
        val phoneRegex = Regex("^[+]?[0-9]{7,15}$")
        return phoneRegex.matches(phone.replace(Regex("[\\s-()]"), ""))
    }

    /**
     * Validate URL format
     */
    protected fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url.trim())
            uri.scheme != null && uri.host != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate number format
     */
    protected fun isValidNumber(value: String): Boolean {
        return value.trim().toDoubleOrNull() != null
    }
}
