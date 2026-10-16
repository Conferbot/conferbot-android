package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowInputs
import com.conferbot.sdk.core.flow.FlowInputs.EN
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry

/**
 * delay-node: shows nothing, waits data.delay seconds (missing: no wait),
 * then the first edge.
 */
class DelayNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.DELAY

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val seconds = Js.number(nodeData["delay"] ?: Double.NaN)
        val delayMs = if (Js.isFinite(seconds) && seconds > 0) Math.min(seconds * 1000, 10 * 60 * 1000.0).toLong() else 0L
        if (delayMs == 0L) return NodeResult.Proceed()
        return NodeResult.DelayedProceed(delayMs = delayMs)
    }
}

/**
 * human-handover-node, as the web runs it (ts/chat/handlers/special.ts) by
 * way of RN's HumanHandoverHandler:
 *
 * - The builder's pre-chat questions (enablePreChatQuestions) are asked one by
 *   one in the composer, after 2000 ms of typing each; an "email" or "phone"
 *   answer is checked.
 * - Then the handoverMessage after 1000 ms of typing, the record, and
 *   initiate-handover; the step waits for the team.
 * - Nobody free (no-agents-available): the owner's fallback message after a
 *   beat of typing; with "leave a message" off the flow carries on along the
 *   first edge (otherwise the leave-a-message form carries it on once sent or
 *   skipped).
 *
 * The live chat after a person joins (agent connected, the survey when it
 * ends) is the handover lifecycle's (Conferbot, core/livechat).
 */
class HumanHandoverNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HUMAN_HANDOVER

    private var handoverState = mutableMapOf<String, NodeUIState.HumanHandover.HandoverState>()

    // Store survey responses per node
    private val surveyResponses = mutableMapOf<String, MutableList<NodeUIState.SurveyResponse>>()

    // Track node data for post-chat survey
    private val nodeDataCache = mutableMapOf<String, Map<String, Any?>>()

    private fun preKey(nodeId: String) = "_preChat_$nodeId"

    private fun preChatQuestions(data: Map<String, Any?>) =
        if (Js.truthy(data["enablePreChatQuestions"])) Js.list(data["preChatQuestions"]).map { Js.map(it) ?: emptyMap() } else emptyList()

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        nodeDataCache[nodeId] = nodeData
        val preChat = preChatQuestions(nodeData)
        if (preChat.isNotEmpty() && ChatState.getVariable(preKey(nodeId)) == null) {
            preChat.forEach { q ->
                val key = Js.str(q["answerVariable"])
                if (key.isNotEmpty()) ChatState.registerAnswerVariable(Js.str(q["id"]), key)
            }
            return askPreChat(nodeData, nodeId, 0)
        }
        return initiateHandover(nodeData, nodeId)
    }

    private fun preChatUI(nodeData: Map<String, Any?>, nodeId: String, index: Int): NodeUIState.TextInput {
        val q = preChatQuestions(nodeData).getOrNull(index) ?: emptyMap()
        val variable = Js.str(q["answerVariable"]).lowercase()
        return NodeUIState.TextInput(
            questionText = FlowKit.shownText(q["questionText"]),
            inputType = when (variable) {
                "email" -> NodeUIState.TextInput.InputType.EMAIL
                "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
                "name" -> NodeUIState.TextInput.InputType.NAME
                else -> NodeUIState.TextInput.InputType.TEXT
            },
            nodeId = nodeId,
            answerKey = Js.str(q["answerVariable"])
        )
    }

    /** Pre-chat question `index`, after 2000 ms of typing. */
    private suspend fun askPreChat(nodeData: Map<String, Any?>, nodeId: String, index: Int): NodeResult {
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS
        flowContext().typing(2000)
        ChatState.setVariable(preKey(nodeId), index)
        val ui = preChatUI(nodeData, nodeId, index)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    /** An answer to a pre-chat question: checked, saved, then the next one or the handover. */
    private suspend fun answerPreChat(response: Any, nodeData: Map<String, Any?>, nodeId: String, index: Int): NodeResult {
        val questions = preChatQuestions(nodeData)
        val q = questions.getOrNull(index) ?: emptyMap()
        val value = FlowKit.typedText(response)
        val ui = preChatUI(nodeData, nodeId, index)
        if (value.isEmpty()) return NodeResult.DisplayUI(ui)
        val variable = Js.str(q["answerVariable"])
        if (variable == "email" && !FlowInputs.isValidEmail(value)) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(q["incorrectEmailResponse"], EN.emailInvalid, listOf("Please enter a valid email")), ui)
        }
        if ((variable == "mobile" || variable == "phone") && !FlowInputs.isValidPhone(value)) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(q["incorrectPhoneNumberResponse"], EN.phoneInvalid), ui)
        }
        if (variable == "name") ChatState.setUserMetadata("name", value)
        if (variable == "email") ChatState.setUserMetadata("email", value)
        if (variable == "mobile" || variable == "phone") ChatState.setUserMetadata("phone", FlowInputs.normalizePhone(value))
        ChatState.addRecord(
            RecordEntry(
                id = "pre-chat-answer-$nodeId-$index", shape = "pre-chat-question-response", type = "pre-chat-question-response",
                text = value, time = FlowKit.nowIso(), fields = mapOf("questionId" to q["id"])
            )
        )
        FlowKit.userSay(value)
        ChatState.fillAnswerVariable(Js.str(q["id"]), value)
        if (index + 1 < questions.size) return askPreChat(nodeData, nodeId, index + 1)
        ChatState.setVariable(preKey(nodeId), "done")
        return initiateHandover(nodeData, nodeId)
    }

    private suspend fun initiateHandover(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT
        // The builder writes it as rich text (<p>...</p>); one cleared in the builder shows nothing.
        val message = FlowKit.shownText(nodeData["handoverMessage"] ?: nodeData["waitMessage"] ?: "")
        if (message.isNotEmpty()) {
            ctx.typing(1000)
            FlowKit.botSay(message)
        }
        val maxWaitTime = com.conferbot.sdk.core.agent.HandoverPayload.waitMinutesOf(nodeData["maxWaitTime"])

        // In the chat's room, and the record sent BEFORE initiate-handover so
        // the server has the Response document when it makes the ticket.
        ctx.joinChatRoom()
        ctx.sendRecord()
        com.conferbot.sdk.core.agent.HandoverPayload.build(nodeData)?.let { ctx.emit("initiate-handover", it) }
        ChatState.setVariable("_handoverInitiated", true)

        // Nobody free: the owner's fallback, then (leave a message off) on.
        var off: () -> Unit = {}
        off = ctx.on("no-agents-available") {
            off()
            val fallback = FlowKit.shownText(nodeData["fallbackMessage"] ?: "")
            if (fallback.isNotEmpty()) {
                ctx.typing(1000)
                if (ctx.isStale()) return@on
                FlowKit.botSay(fallback, side = true)
                val fallbackId = Js.str(nodeData["fallbackMessage"])
                ChatState.addRecord(
                    RecordEntry(
                        id = fallbackId, shape = "", type = "agent-declined-message", time = FlowKit.nowIso(),
                        fields = mapOf("message" to fallback, "renderedText" to fallback)
                    )
                )
            }
            if (ctx.customizations["chatLeaveMessage"] == false) ctx.respond(mapOf("action" to "continue"))
        }

        return NodeResult.DisplayUI(
            NodeUIState.HumanHandover(
                state = NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT,
                handoverMessage = message,
                maxWaitTime = maxWaitTime.toInt(),
                nodeId = nodeId
            )
        )
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val action = (response as? Map<*, *>)?.get("action")
        val preIndex = ChatState.getVariable(preKey(nodeId))
        if (preIndex is Number && action == null && response !is List<*>) return answerPreChat(response, nodeData, nodeId, preIndex.toInt())

        // The visitor left the queue (the wait card's Cancel), or the wait is
        // over: the flow carries on from this step, as on the web.
        when (action) {
            "cancel" -> {
                // Out of the queue: the team must stop seeing the visitor waiting
                // (web special.ts). Conferbot.leaveQueue sends it itself and
                // waits for the answer (queueLeft); a headless caller gets it here.
                if ((response as Map<*, *>)["queueLeft"] != true) {
                    ChatState.chatSessionId?.let { flowContext().emit("queue-self-resolve", com.conferbot.sdk.core.livechat.HandoverState.queueSelfResolvePayload(it)) }
                }
                ChatState.addToTranscript("bot", "You left the queue")
                recordResponse(nodeId = nodeId, shape = "handover-cancelled", text = "You left the queue", type = nodeType)
                cleanup(nodeId)
                return NodeResult.Proceed()
            }
            "continue" -> {
                cleanup(nodeId)
                return NodeResult.Proceed()
            }
        }
        return when (handoverState[nodeId]) {
            NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY -> handlePostChatSurveyResponse(response, nodeId)
            NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED -> handleAgentChatMessage(response, nodeData, nodeId)
            else -> NodeResult.DisplayUI(buildHandoverUIState(nodeData, nodeId))
        }
    }

    private fun buildHandoverUIState(nodeData: Map<String, Any?>, nodeId: String): NodeUIState.HumanHandover = NodeUIState.HumanHandover(
        state = handoverState[nodeId] ?: NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT,
        handoverMessage = FlowText.bubbleText(ChatState.resolveVariables(Js.str(nodeData["handoverMessage"]), html = true)),
        maxWaitTime = com.conferbot.sdk.core.agent.HandoverPayload.waitMinutesOf(nodeData["maxWaitTime"]).toInt(),
        nodeId = nodeId
    )

    /**
     * Handle post-chat survey response: a list of SurveyResponse from the
     * PostChatSurveyView (empty: skipped).
     */
    private fun handlePostChatSurveyResponse(response: Any, nodeId: String): NodeResult {
        val responses = (response as? List<*>)?.filterIsInstance<NodeUIState.SurveyResponse>() ?: emptyList()
        if (responses.isEmpty()) {
            ChatState.addToTranscript("bot", "Survey skipped")
            recordResponse(nodeId = nodeId, shape = "postchat-survey-skipped", text = "Survey skipped by user", type = nodeType)
            cleanup(nodeId)
            return NodeResult.Proceed()
        }
        surveyResponses[nodeId] = responses.toMutableList()
        val surveyData = mutableMapOf<String, Any>()
        responses.forEach { r ->
            surveyData[r.questionId] = r.value
            val valueStr = when (val value = r.value) {
                is List<*> -> value.joinToString(", ")
                else -> value.toString()
            }
            ChatState.addToTranscript("user", "Survey response: $valueStr")
        }
        recordResponse(
            nodeId = nodeId, shape = "postchat-survey-response", text = "Survey completed", type = nodeType,
            additionalData = mapOf("surveyResponses" to surveyData, "responseCount" to responses.size)
        )
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    private fun handleAgentChatMessage(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val message = FlowKit.typedText(response)
        if (message.isNotEmpty()) {
            ChatState.addToTranscript("user", message)
            recordResponse(nodeId = nodeId, shape = "handover-user-message", text = message, type = nodeType)
        }
        return NodeResult.DisplayUI(buildHandoverUIState(nodeData, nodeId))
    }

    /** Called when an agent accepts the handover */
    fun onAgentAccepted(nodeId: String, agentName: String) {
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED
        ChatState.addToTranscript("bot", "$agentName has joined the chat")
    }

    /** Called when no agents are available (a caller outside the step's own listener). */
    fun onNoAgentsAvailable(nodeId: String, nodeData: Map<String, Any?>): NodeResult {
        handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE
        val fallbackMessage = FlowText.bubbleText(ChatState.resolveVariables(Js.str(nodeData["fallbackMessage"] ?: "No agents available at the moment."), html = true))
        ChatState.addToTranscript("bot", fallbackMessage)
        return NodeResult.DisplayUI(
            NodeUIState.HumanHandover(state = NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE, handoverMessage = fallbackMessage, nodeId = nodeId)
        )
    }

    /** Called when the agent chat ends: the post-chat survey if enabled, else on. */
    fun onChatEnded(nodeId: String, nodeData: Map<String, Any?>): NodeResult {
        // The builder's key is enablePostChatSurveyQuestions (web
        // agentListeners.ts onChatEnded, flow-node spec); the older
        // enablePostChatSurvey still counts.
        val enablePostChatSurvey = getBoolean(nodeData, "enablePostChatSurveyQuestions", false) ||
            getBoolean(nodeData, "enablePostChatSurvey", false)
        val postChatQuestions = getList<Map<String, Any?>>(nodeData, "postChatSurveyQuestions")
        if (enablePostChatSurvey && postChatQuestions.isNotEmpty()) {
            handoverState[nodeId] = NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY
            nodeDataCache[nodeId] = nodeData
            val surveyTitle = getString(nodeData, "postChatSurveyTitle", "How was your experience?")
            val surveyDescription = getString(nodeData, "postChatSurveyDescription", "")
            ChatState.addToTranscript("bot", surveyTitle)
            return NodeResult.DisplayUI(
                NodeUIState.PostChatSurvey(
                    questions = buildSurveyQuestions(postChatQuestions),
                    currentQuestionIndex = 0,
                    nodeId = nodeId,
                    surveyTitle = surveyTitle,
                    surveyDescription = surveyDescription.ifEmpty { null }
                )
            )
        }
        cleanup(nodeId)
        return NodeResult.Proceed()
    }

    /** Survey questions from the node's configuration. */
    private fun buildSurveyQuestions(questionConfigs: List<Map<String, Any?>>): List<NodeUIState.PostChatSurvey.SurveyQuestion> =
        questionConfigs.mapIndexed { index, config ->
            val answerType = config["answerVariable"]?.toString() ?: config["type"]?.toString() ?: "text"
            val questionType = when (answerType.lowercase()) {
                "rating", "star", "stars" -> NodeUIState.PostChatSurvey.SurveyQuestionType.RATING
                "choice", "single", "single-choice", "radio" -> NodeUIState.PostChatSurvey.SurveyQuestionType.CHOICE
                "multi", "multiple", "multi-choice", "checkbox" -> NodeUIState.PostChatSurvey.SurveyQuestionType.MULTI_CHOICE
                else -> NodeUIState.PostChatSurvey.SurveyQuestionType.TEXT
            }
            @Suppress("UNCHECKED_CAST")
            val options = (config["options"] as? List<String>)
                ?: (config["choices"] as? List<String>)
                ?: (config["answers"] as? List<Map<String, Any?>>)?.map { it["text"]?.toString() ?: "" }
            fun intOf(v: Any?, d: Int) = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: d
                else -> d
            }
            val required = when (val req = config["required"]) {
                is Boolean -> req
                is String -> req.lowercase() == "true"
                else -> true
            }
            NodeUIState.PostChatSurvey.SurveyQuestion(
                id = config["id"]?.toString() ?: "survey_q_$index",
                type = questionType,
                question = config["questionText"]?.toString() ?: config["question"]?.toString() ?: "",
                options = options,
                required = required,
                minRating = intOf(config["minRating"] ?: config["min"], 1),
                maxRating = intOf(config["maxRating"] ?: config["max"], 5)
            )
        }

    /** Cached node data for a node (socket events that don't carry it). */
    fun getCachedNodeData(nodeId: String): Map<String, Any?>? = nodeDataCache[nodeId]

    private fun cleanup(nodeId: String) {
        handoverState.remove(nodeId)
        surveyResponses.remove(nodeId)
        nodeDataCache.remove(nodeId)
        ChatState.setVariable(preKey(nodeId), null)
    }

    /** The survey's answers by question, for the server. */
    fun getSurveyResponsesForSocket(nodeId: String): Map<String, Any>? {
        val responses = surveyResponses[nodeId] ?: return null
        if (responses.isEmpty()) return null
        return responses.associate { it.questionId to it.value }
    }
}
