package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState

/**
 * Handler for delay-node
 * Delays before proceeding to next node
 */
class DelayNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.DELAY

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val delaySeconds = getInt(nodeData, "delay", 1)
        val delayMs = delaySeconds * 1000L

        return NodeResult.DelayedProceed(delayMs = delayMs)
    }
}

/**
 * Handler for human-handover-node
 * Manages live agent handover flow including post-chat survey
 */
class HumanHandoverNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HUMAN_HANDOVER

    // Track pre-chat question index per node
    private val preChatIndices = mutableMapOf<String, Int>()
    private val postChatIndices = mutableMapOf<String, Int>()
    private var handoverState = mutableMapOf<String, NodeUIState.HumanHandover.HandoverState>()

    // Store survey responses per node
    private val surveyResponses = mutableMapOf<String, MutableList<NodeUIState.SurveyResponse>>()

    // Track node data for post-chat survey
    private val nodeDataCache = mutableMapOf<String, Map<String, Any?>>()

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val enablePreChatQuestions = getBoolean(nodeData, "enablePreChatQuestions", false)
        val preChatQuestions = getList<Map<String, Any?>>(nodeData, "preChatQuestions")

        // Cache node data for later use (e.g., post-chat survey)
        nodeDataCache[nodeId] = nodeData

        // Initialize state for this node
        if (!handoverState.containsKey(nodeId)) {
            handoverState[nodeId] = if (enablePreChatQuestions && preChatQuestions.isNotEmpty()) {
                NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS
            } else {
                NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
            }
        }

        return when (handoverState[nodeId]) {
            NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS -> {
                displayPreChatQuestion(nodeData, nodeId, preChatQuestions)
            }
            NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT -> {
                initiateHandover(nodeData, nodeId)
            }
            else -> {
                // Already connected or survey
                NodeResult.DisplayUI(buildHandoverUIState(nodeData, nodeId))
            }
        }
    }

    private fun displayPreChatQuestion(
        nodeData: Map<String, Any?>,
        nodeId: String,
        questions: List<Map<String, Any?>>
    ): NodeResult {
        val currentIndex = preChatIndices[nodeId] ?: 0

        if (currentIndex >= questions.size) {
            // All pre-chat questions answered, start handover
            handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
            return initiateHandover(nodeData, nodeId)
        }

        val question = questions[currentIndex]
        val questionText = question["questionText"]?.toString() ?: "Please answer"
        val answerType = question["answerVariable"]?.toString() ?: "text"
        val answerKey = question["id"]?.toString() ?: "prechat_$currentIndex"

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        val preChatList = questions.mapIndexed { i, q ->
            NodeUIState.HumanHandover.PreChatQuestion(
                id = q["id"]?.toString() ?: "q$i",
                questionText = q["questionText"]?.toString() ?: "",
                answerType = q["answerVariable"]?.toString() ?: "text",
                answerKey = q["id"]?.toString() ?: "prechat_$i"
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.HumanHandover(
                state = NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS,
                preChatQuestions = preChatList,
                currentQuestionIndex = currentIndex,
                nodeId = nodeId
            )
        )
    }

    private fun initiateHandover(
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val handoverMessage = getString(nodeData, "handoverMessage", "Connecting you to an agent...")
        val maxWaitTime = getInt(nodeData, "maxWaitTime", 5)  // minutes
        val priority = getString(nodeData, "priority", "normal")

        state.addToTranscript("bot", handoverMessage)

        recordResponse(
            nodeId = nodeId,
            shape = "handover-initiated",
            text = handoverMessage,
            type = nodeType,
            additionalData = mapOf(
                "priority" to priority,
                "maxWaitTime" to maxWaitTime
            )
        )

        return NodeResult.DisplayUI(
            NodeUIState.HumanHandover(
                state = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT,
                handoverMessage = handoverMessage,
                maxWaitTime = maxWaitTime,
                nodeId = nodeId
            )
        )
    }

    private fun buildHandoverUIState(
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeUIState.HumanHandover {
        return NodeUIState.HumanHandover(
            state = handoverState[nodeId] ?: NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT,
            handoverMessage = getString(nodeData, "handoverMessage", ""),
            maxWaitTime = getInt(nodeData, "maxWaitTime", 5),
            nodeId = nodeId
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        return when (handoverState[nodeId]) {
            NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS -> {
                handlePreChatResponse(response, nodeData, nodeId)
            }
            NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY -> {
                handlePostChatSurveyResponse(response, nodeData, nodeId)
            }
            NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED -> {
                handleAgentChatMessage(response, nodeData, nodeId)
            }
            else -> {
                // Waiting or other state
                NodeResult.DisplayUI(buildHandoverUIState(nodeData, nodeId))
            }
        }
    }

    private fun handlePreChatResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val questions = getList<Map<String, Any?>>(nodeData, "preChatQuestions")
        val currentIndex = preChatIndices[nodeId] ?: 0

        if (currentIndex >= questions.size) {
            handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
            return initiateHandover(nodeData, nodeId)
        }

        val question = questions[currentIndex]
        val answerType = question["answerVariable"]?.toString() ?: "text"
        val value = response.toString().trim()

        // Validate based on type
        when (answerType.lowercase()) {
            "email" -> {
                if (!isValidEmail(value)) {
                    val errorMsg = question["incorrectEmailResponse"]?.toString()
                        ?: "Please enter a valid email"
                    return NodeResult.Error(errorMsg, shouldProceed = false)
                }
                state.setUserMetadata("email", value)
            }
            "phone", "mobile" -> {
                if (!isValidPhone(value)) {
                    val errorMsg = question["incorrectPhoneNumberResponse"]?.toString()
                        ?: "Please enter a valid phone number"
                    return NodeResult.Error(errorMsg, shouldProceed = false)
                }
                state.setUserMetadata("phone", value)
            }
            "name" -> {
                if (value.isEmpty()) {
                    return NodeResult.Error("Please enter your name", shouldProceed = false)
                }
                state.setUserMetadata("name", value)
            }
        }

        // Store answer
        val answerKey = question["id"]?.toString() ?: "prechat_$currentIndex"
        state.setAnswerVariable(nodeId, value)
        state.setAnswerVariableByKey(answerKey, value)
        state.addToTranscript("user", value)

        recordResponse(
            nodeId = nodeId,
            shape = "prechat-question-response",
            text = value,
            type = nodeType,
            additionalData = mapOf("questionIndex" to currentIndex)
        )

        // Move to next question
        preChatIndices[nodeId] = currentIndex + 1

        // Check if more questions
        if (currentIndex + 1 < questions.size) {
            return displayPreChatQuestion(nodeData, nodeId, questions)
        }

        // All pre-chat questions answered
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
        return initiateHandover(nodeData, nodeId)
    }

    /**
     * Handle post-chat survey response
     * Receives list of SurveyResponse objects from the PostChatSurveyView
     */
    @Suppress("UNCHECKED_CAST")
    private fun handlePostChatSurveyResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        // Response is a list of SurveyResponse objects
        val responses = response as? List<NodeUIState.SurveyResponse> ?: emptyList()

        if (responses.isEmpty()) {
            // User skipped the survey
            state.addToTranscript("bot", "Survey skipped")
            recordResponse(
                nodeId = nodeId,
                shape = "postchat-survey-skipped",
                text = "Survey skipped by user",
                type = nodeType
            )
            cleanup(nodeId)
            return NodeResult.Proceed()
        }

        // Store all survey responses
        surveyResponses[nodeId] = responses.toMutableList()

        // Build response summary for transcript
        val surveyData = mutableMapOf<String, Any>()
        responses.forEach { surveyResponse ->
            surveyData[surveyResponse.questionId] = surveyResponse.value

            // Record individual responses
            val valueStr = when (val value = surveyResponse.value) {
                is Int -> value.toString()
                is List<*> -> value.joinToString(", ")
                else -> value.toString()
            }
            state.addToTranscript("user", "Survey response: $valueStr")
        }

        // Record the complete survey submission
        recordResponse(
            nodeId = nodeId,
            shape = "postchat-survey-response",
            text = "Survey completed",
            type = nodeType,
            additionalData = mapOf(
                "surveyResponses" to surveyData,
                "responseCount" to responses.size
            )
        )

        // Clean up and proceed to next node
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    /**
     * Legacy handler for question-by-question post-chat survey
     * Kept for backward compatibility
     */
    private fun handleLegacyPostChatResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val questions = getList<Map<String, Any?>>(nodeData, "postChatSurveyQuestions")
        val currentIndex = postChatIndices[nodeId] ?: 0

        val value = response.toString().trim()

        // Store answer
        state.addToTranscript("user", value)
        recordResponse(
            nodeId = nodeId,
            shape = "postchat-survey-response",
            text = value,
            type = nodeType,
            additionalData = mapOf("questionIndex" to currentIndex)
        )

        // Move to next question
        postChatIndices[nodeId] = currentIndex + 1

        // Check if more questions
        if (currentIndex + 1 < questions.size) {
            // Display next post-chat question
            val nextQuestion = questions[currentIndex + 1]
            state.addToTranscript("bot", nextQuestion["questionText"]?.toString() ?: "")
            return NodeResult.DisplayUI(
                NodeUIState.HumanHandover(
                    state = NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY,
                    preChatQuestions = questions.mapIndexed { i, q ->
                        NodeUIState.HumanHandover.PreChatQuestion(
                            id = q["id"]?.toString() ?: "q$i",
                            questionText = q["questionText"]?.toString() ?: "",
                            answerType = q["answerVariable"]?.toString() ?: "text",
                            answerKey = q["id"]?.toString() ?: "postchat_$i"
                        )
                    },
                    currentQuestionIndex = currentIndex + 1,
                    nodeId = nodeId
                )
            )
        }

        // All post-chat questions answered, proceed to next node
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    private fun handleAgentChatMessage(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val message = response.toString().trim()

        if (message.isNotEmpty()) {
            state.addToTranscript("user", message)
            recordResponse(
                nodeId = nodeId,
                shape = "handover-user-message",
                text = message,
                type = nodeType
            )
        }

        // Stay in agent connected state
        return NodeResult.DisplayUI(buildHandoverUIState(nodeData, nodeId))
    }

    /**
     * Called when agent accepts the handover
     */
    fun onAgentAccepted(nodeId: String, agentName: String) {
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED
        state.addToTranscript("bot", "$agentName has joined the chat")
    }

    /**
     * Called when no agents are available
     */
    fun onNoAgentsAvailable(nodeId: String, nodeData: Map<String, Any?>): NodeResult {
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE
        val fallbackMessage = getString(nodeData, "fallbackMessage", "No agents available at the moment.")

        state.addToTranscript("bot", fallbackMessage)

        return NodeResult.DisplayUI(
            NodeUIState.HumanHandover(
                state = NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE,
                handoverMessage = fallbackMessage,
                nodeId = nodeId
            )
        )
    }

    /**
     * Called when agent chat ends - start post-chat survey if enabled
     */
    fun onChatEnded(nodeId: String, nodeData: Map<String, Any?>): NodeResult {
        val enablePostChatSurvey = getBoolean(nodeData, "enablePostChatSurvey", false)
        val postChatQuestions = getList<Map<String, Any?>>(nodeData, "postChatSurveyQuestions")

        if (enablePostChatSurvey && postChatQuestions.isNotEmpty()) {
            handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY
            postChatIndices[nodeId] = 0

            // Cache the node data for survey response handling
            nodeDataCache[nodeId] = nodeData

            // Build survey questions for the new PostChatSurvey UI
            val surveyQuestions = buildSurveyQuestions(postChatQuestions)

            // Get survey configuration
            val surveyTitle = getString(nodeData, "postChatSurveyTitle", "How was your experience?")
            val surveyDescription = getString(nodeData, "postChatSurveyDescription", "")

            state.addToTranscript("bot", surveyTitle)

            return NodeResult.DisplayUI(
                NodeUIState.PostChatSurvey(
                    questions = surveyQuestions,
                    currentQuestionIndex = 0,
                    nodeId = nodeId,
                    surveyTitle = surveyTitle,
                    surveyDescription = surveyDescription.ifEmpty { null }
                )
            )
        }

        // No post-chat survey, proceed to next node
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    /**
     * Build survey questions from node configuration
     * Maps the server-side question format to our SurveyQuestion data class
     */
    private fun buildSurveyQuestions(
        questionConfigs: List<Map<String, Any?>>
    ): List<NodeUIState.PostChatSurvey.SurveyQuestion> {
        return questionConfigs.mapIndexed { index, config ->
            val questionId = config["id"]?.toString() ?: "survey_q_$index"
            val questionText = config["questionText"]?.toString() ?: config["question"]?.toString() ?: ""
            val answerType = config["answerVariable"]?.toString()
                ?: config["type"]?.toString()
                ?: "text"

            // Determine question type
            val questionType = when (answerType.lowercase()) {
                "rating", "star", "stars" -> NodeUIState.PostChatSurvey.SurveyQuestionType.RATING
                "choice", "single", "single-choice", "radio" -> NodeUIState.PostChatSurvey.SurveyQuestionType.CHOICE
                "multi", "multiple", "multi-choice", "checkbox" -> NodeUIState.PostChatSurvey.SurveyQuestionType.MULTI_CHOICE
                else -> NodeUIState.PostChatSurvey.SurveyQuestionType.TEXT
            }

            // Get options for choice questions
            @Suppress("UNCHECKED_CAST")
            val options = (config["options"] as? List<String>)
                ?: (config["choices"] as? List<String>)
                ?: (config["answers"] as? List<Map<String, Any?>>)?.map { it["text"]?.toString() ?: "" }

            // Get rating configuration
            val minRating = when (val min = config["minRating"] ?: config["min"]) {
                is Number -> min.toInt()
                is String -> min.toIntOrNull() ?: 1
                else -> 1
            }
            val maxRating = when (val max = config["maxRating"] ?: config["max"]) {
                is Number -> max.toInt()
                is String -> max.toIntOrNull() ?: 5
                else -> 5
            }

            // Check if required
            val required = when (val req = config["required"]) {
                is Boolean -> req
                is String -> req.lowercase() == "true"
                else -> true // Default to required
            }

            NodeUIState.PostChatSurvey.SurveyQuestion(
                id = questionId,
                type = questionType,
                question = questionText,
                options = options,
                required = required,
                minRating = minRating,
                maxRating = maxRating
            )
        }
    }

    /**
     * Get cached node data for a given nodeId
     * Useful when handling socket events that don't include full node data
     */
    fun getCachedNodeData(nodeId: String): Map<String, Any?>? {
        return nodeDataCache[nodeId]
    }

    private fun cleanup(nodeId: String) {
        handoverState.remove(nodeId)
        preChatIndices.remove(nodeId)
        postChatIndices.remove(nodeId)
        surveyResponses.remove(nodeId)
        nodeDataCache.remove(nodeId)
    }

    /**
     * Send survey responses via socket
     * Called by NodeFlowEngine after survey is submitted
     */
    fun getSurveyResponsesForSocket(nodeId: String): Map<String, Any>? {
        val responses = surveyResponses[nodeId] ?: return null
        if (responses.isEmpty()) return null

        val responseMap = mutableMapOf<String, Any>()
        responses.forEach { response ->
            responseMap[response.questionId] = response.value
        }
        return responseMap
    }
}
