package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*

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
 * Manages live agent handover flow
 */
class HumanHandoverNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HUMAN_HANDOVER

    // Track pre-chat question index per node
    private val preChatIndices = mutableMapOf<String, Int>()
    private val postChatIndices = mutableMapOf<String, Int>()
    private var handoverState = mutableMapOf<String, NodeUIState.HumanHandover.HandoverState>()

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val enablePreChatQuestions = getBoolean(nodeData, "enablePreChatQuestions", false)
        val preChatQuestions = getList<Map<String, Any?>>(nodeData, "preChatQuestions")

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
                handlePostChatResponse(response, nodeData, nodeId)
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

    private fun handlePostChatResponse(
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

            val firstQuestion = postChatQuestions[0]
            state.addToTranscript("bot", firstQuestion["questionText"]?.toString() ?: "")

            return NodeResult.DisplayUI(
                NodeUIState.HumanHandover(
                    state = NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY,
                    preChatQuestions = postChatQuestions.mapIndexed { i, q ->
                        NodeUIState.HumanHandover.PreChatQuestion(
                            id = q["id"]?.toString() ?: "q$i",
                            questionText = q["questionText"]?.toString() ?: "",
                            answerType = q["answerVariable"]?.toString() ?: "text",
                            answerKey = q["id"]?.toString() ?: "postchat_$i"
                        )
                    },
                    currentQuestionIndex = 0,
                    nodeId = nodeId
                )
            )
        }

        // No post-chat survey, proceed to next node
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    private fun cleanup(nodeId: String) {
        handoverState.remove(nodeId)
        preChatIndices.remove(nodeId)
        postChatIndices.remove(nodeId)
    }
}
