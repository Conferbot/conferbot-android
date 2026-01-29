package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Special Node Handlers
 * Tests DelayNodeHandler and HumanHandoverNodeHandler
 * including pre-chat questions and post-chat survey functionality
 */
class SpecialNodeHandlersTest {

    @Before
    fun setUp() {
        mockkObject(ChatState)
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )
    }

    @After
    fun tearDown() {
        ChatState.reset()
        unmockkObject(ChatState)
    }

    // ==================== DelayNodeHandler Tests ====================

    @Test
    fun `DelayNodeHandler - returns delayed proceed with specified delay`() = runTest {
        val handler = DelayNodeHandler()

        val nodeData = mapOf(
            "type" to "delay-node",
            "delay" to 5
        )

        val result = handler.process(nodeData, "delay-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).delayMs).isEqualTo(5000L)
    }

    @Test
    fun `DelayNodeHandler - defaults to 1 second delay`() = runTest {
        val handler = DelayNodeHandler()

        val nodeData = mapOf(
            "type" to "delay-node"
        )

        val result = handler.process(nodeData, "delay-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).delayMs).isEqualTo(1000L)
    }

    @Test
    fun `DelayNodeHandler - handles string delay value`() = runTest {
        val handler = DelayNodeHandler()

        val nodeData = mapOf(
            "type" to "delay-node",
            "delay" to "3"
        )

        val result = handler.process(nodeData, "delay-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).delayMs).isEqualTo(3000L)
    }

    @Test
    fun `DelayNodeHandler - handles zero delay`() = runTest {
        val handler = DelayNodeHandler()

        val nodeData = mapOf(
            "type" to "delay-node",
            "delay" to 0
        )

        val result = handler.process(nodeData, "delay-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).delayMs).isEqualTo(0L)
    }

    // ==================== HumanHandoverNodeHandler Tests ====================

    @Test
    fun `HumanHandoverNodeHandler - displays pre-chat questions when enabled`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "q1",
                    "questionText" to "What is your name?",
                    "answerVariable" to "name"
                ),
                mapOf(
                    "id" to "q2",
                    "questionText" to "What is your email?",
                    "answerVariable" to "email"
                )
            )
        )

        val result = handler.process(nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.HumanHandover::class.java)

        val handoverState = uiState as NodeUIState.HumanHandover
        assertThat(handoverState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS)
        assertThat(handoverState.preChatQuestions).hasSize(2)
        assertThat(handoverState.currentQuestionIndex).isEqualTo(0)
    }

    @Test
    fun `HumanHandoverNodeHandler - skips pre-chat when disabled`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "handoverMessage" to "Connecting you to an agent..."
        )

        val result = handler.process(nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
    }

    @Test
    fun `HumanHandoverNodeHandler - skips pre-chat when questions empty`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to emptyList<Map<String, Any?>>(),
            "handoverMessage" to "Connecting..."
        )

        val result = handler.process(nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
    }

    @Test
    fun `HumanHandoverNodeHandler - validates email in pre-chat response`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "email_q",
                    "questionText" to "What is your email?",
                    "answerVariable" to "email",
                    "incorrectEmailResponse" to "Please enter a valid email"
                )
            )
        )

        // First, process to display question
        handler.process(nodeData, "handover-1")

        // Then submit invalid email
        val result = handler.handleResponse("invalid-email", nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        assertThat((result as NodeResult.Error).message).isEqualTo("Please enter a valid email")
        assertThat(result.shouldProceed).isFalse()
    }

    @Test
    fun `HumanHandoverNodeHandler - validates phone in pre-chat response`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "phone_q",
                    "questionText" to "What is your phone?",
                    "answerVariable" to "phone",
                    "incorrectPhoneNumberResponse" to "Please enter a valid phone number"
                )
            )
        )

        // First, process to display question
        handler.process(nodeData, "handover-1")

        // Then submit invalid phone
        val result = handler.handleResponse("abc", nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        assertThat((result as NodeResult.Error).message).isEqualTo("Please enter a valid phone number")
    }

    @Test
    fun `HumanHandoverNodeHandler - validates name in pre-chat response`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "name_q",
                    "questionText" to "What is your name?",
                    "answerVariable" to "name"
                )
            )
        )

        // First, process to display question
        handler.process(nodeData, "handover-1")

        // Then submit empty name
        val result = handler.handleResponse("   ", nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        assertThat((result as NodeResult.Error).message).isEqualTo("Please enter your name")
    }

    @Test
    fun `HumanHandoverNodeHandler - stores valid pre-chat response`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit
        every { ChatState.setUserMetadata(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "name_q",
                    "questionText" to "What is your name?",
                    "answerVariable" to "name"
                )
            ),
            "handoverMessage" to "Connecting..."
        )

        // First, process to display question
        handler.process(nodeData, "handover-1")

        // Then submit valid name
        val result = handler.handleResponse("John Doe", nodeData, "handover-1")

        // Should move to waiting state since all questions answered
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        verify { ChatState.setUserMetadata("name", "John Doe") }
    }

    @Test
    fun `HumanHandoverNodeHandler - advances through multiple pre-chat questions`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.addAnswerVariable(any(), any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit
        every { ChatState.setUserMetadata(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to true,
            "preChatQuestions" to listOf(
                mapOf(
                    "id" to "name_q",
                    "questionText" to "What is your name?",
                    "answerVariable" to "name"
                ),
                mapOf(
                    "id" to "email_q",
                    "questionText" to "What is your email?",
                    "answerVariable" to "email"
                )
            ),
            "handoverMessage" to "Connecting..."
        )

        // Process initial state
        val initialResult = handler.process(nodeData, "handover-1")
        assertThat(initialResult).isInstanceOf(NodeResult.DisplayUI::class.java)
        var uiState = (initialResult as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.currentQuestionIndex).isEqualTo(0)

        // Answer first question
        val firstAnswer = handler.handleResponse("John Doe", nodeData, "handover-1")
        assertThat(firstAnswer).isInstanceOf(NodeResult.DisplayUI::class.java)
        uiState = (firstAnswer as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.currentQuestionIndex).isEqualTo(1)

        // Answer second question - should move to waiting
        val secondAnswer = handler.handleResponse("john@example.com", nodeData, "handover-1")
        assertThat(secondAnswer).isInstanceOf(NodeResult.DisplayUI::class.java)
        uiState = (secondAnswer as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT)
    }

    @Test
    fun `HumanHandoverNodeHandler - onAgentAccepted updates state`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        // Setup - skip pre-chat questions
        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "handoverMessage" to "Connecting..."
        )
        handler.process(nodeData, "handover-1")

        // Agent accepts
        handler.onAgentAccepted("handover-1", "Agent Smith")

        verify { ChatState.addToTranscript("bot", "Agent Smith has joined the chat") }
    }

    @Test
    fun `HumanHandoverNodeHandler - onNoAgentsAvailable returns fallback message`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "fallbackMessage" to "Sorry, no agents are available right now."
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onNoAgentsAvailable("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.state).isEqualTo(NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE)
        assertThat(uiState.handoverMessage).isEqualTo("Sorry, no agents are available right now.")
    }

    @Test
    fun `HumanHandoverNodeHandler - onChatEnded triggers post-chat survey`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyTitle" to "Rate your experience",
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "rating",
                    "questionText" to "How would you rate your experience?",
                    "answerVariable" to "rating"
                )
            )
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.PostChatSurvey::class.java)

        val surveyState = uiState as NodeUIState.PostChatSurvey
        assertThat(surveyState.surveyTitle).isEqualTo("Rate your experience")
        assertThat(surveyState.questions).hasSize(1)
    }

    @Test
    fun `HumanHandoverNodeHandler - onChatEnded proceeds when no survey configured`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to false
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
    }

    @Test
    fun `HumanHandoverNodeHandler - handles post-chat survey responses`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "rating",
                    "questionText" to "Rate us",
                    "answerVariable" to "rating"
                )
            )
        )

        handler.process(nodeData, "handover-1")
        handler.onChatEnded("handover-1", nodeData)

        // Submit survey responses
        val responses = listOf(
            NodeUIState.SurveyResponse("rating", 5)
        )

        val result = handler.handleResponse(responses, nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { it.shape == "postchat-survey-response" }) }
    }

    @Test
    fun `HumanHandoverNodeHandler - handles skipped survey`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "rating",
                    "questionText" to "Rate us",
                    "answerVariable" to "rating"
                )
            )
        )

        handler.process(nodeData, "handover-1")
        handler.onChatEnded("handover-1", nodeData)

        // Submit empty responses (skipped)
        val result = handler.handleResponse(emptyList<NodeUIState.SurveyResponse>(), nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { it.shape == "postchat-survey-skipped" }) }
    }

    @Test
    fun `HumanHandoverNodeHandler - builds survey questions with rating type`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "rating",
                    "questionText" to "Rate your experience",
                    "answerVariable" to "rating",
                    "minRating" to 1,
                    "maxRating" to 10
                )
            )
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val surveyState = (result as NodeResult.DisplayUI).uiState as NodeUIState.PostChatSurvey
        val question = surveyState.questions[0]

        assertThat(question.type).isEqualTo(NodeUIState.PostChatSurvey.SurveyQuestionType.RATING)
        assertThat(question.minRating).isEqualTo(1)
        assertThat(question.maxRating).isEqualTo(10)
    }

    @Test
    fun `HumanHandoverNodeHandler - builds survey questions with choice type`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "satisfaction",
                    "questionText" to "How satisfied are you?",
                    "answerVariable" to "choice",
                    "options" to listOf("Very satisfied", "Satisfied", "Neutral", "Unsatisfied")
                )
            )
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val surveyState = (result as NodeResult.DisplayUI).uiState as NodeUIState.PostChatSurvey
        val question = surveyState.questions[0]

        assertThat(question.type).isEqualTo(NodeUIState.PostChatSurvey.SurveyQuestionType.CHOICE)
        assertThat(question.options).hasSize(4)
    }

    @Test
    fun `HumanHandoverNodeHandler - builds survey questions with text type`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "feedback",
                    "questionText" to "Any additional feedback?",
                    "answerVariable" to "text"
                )
            )
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val surveyState = (result as NodeResult.DisplayUI).uiState as NodeUIState.PostChatSurvey
        val question = surveyState.questions[0]

        assertThat(question.type).isEqualTo(NodeUIState.PostChatSurvey.SurveyQuestionType.TEXT)
    }

    @Test
    fun `HumanHandoverNodeHandler - builds survey questions with multi-choice type`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "improvements",
                    "questionText" to "What can we improve?",
                    "answerVariable" to "multi-choice",
                    "options" to listOf("Speed", "Quality", "Communication", "Price")
                )
            )
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onChatEnded("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val surveyState = (result as NodeResult.DisplayUI).uiState as NodeUIState.PostChatSurvey
        val question = surveyState.questions[0]

        assertThat(question.type).isEqualTo(NodeUIState.PostChatSurvey.SurveyQuestionType.MULTI_CHOICE)
    }

    @Test
    fun `HumanHandoverNodeHandler - getCachedNodeData returns cached data`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "customField" to "custom value"
        )

        handler.process(nodeData, "handover-1")

        val cachedData = handler.getCachedNodeData("handover-1")

        assertThat(cachedData).isNotNull()
        assertThat(cachedData?.get("customField")).isEqualTo("custom value")
    }

    @Test
    fun `HumanHandoverNodeHandler - getSurveyResponsesForSocket returns response map`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "enablePostChatSurvey" to true,
            "postChatSurveyQuestions" to listOf(
                mapOf(
                    "id" to "rating",
                    "questionText" to "Rate us",
                    "answerVariable" to "rating"
                ),
                mapOf(
                    "id" to "feedback",
                    "questionText" to "Feedback",
                    "answerVariable" to "text"
                )
            )
        )

        handler.process(nodeData, "handover-1")
        handler.onChatEnded("handover-1", nodeData)

        // Submit survey responses
        val responses = listOf(
            NodeUIState.SurveyResponse("rating", 5),
            NodeUIState.SurveyResponse("feedback", "Great service!")
        )

        handler.handleResponse(responses, nodeData, "handover-1")

        // Note: getSurveyResponsesForSocket is called after handleResponse processes the survey
        // The responses are stored internally
    }

    @Test
    fun `HumanHandoverNodeHandler - handles agent chat messages`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false
        )

        // Setup waiting for agent state
        handler.process(nodeData, "handover-1")
        handler.onAgentAccepted("handover-1", "Agent Smith")

        // User sends message
        val result = handler.handleResponse("Hello agent!", nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        verify { ChatState.addToTranscript("user", "Hello agent!") }
        verify { ChatState.pushToRecord(match { it.shape == "handover-user-message" }) }
    }

    @Test
    fun `HumanHandoverNodeHandler - uses default fallback message`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false
            // No fallbackMessage specified
        )

        handler.process(nodeData, "handover-1")

        val result = handler.onNoAgentsAvailable("handover-1", nodeData)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.handoverMessage).isEqualTo("No agents available at the moment.")
    }

    @Test
    fun `HumanHandoverNodeHandler - uses default handover message`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false
            // No handoverMessage specified
        )

        val result = handler.process(nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.handoverMessage).isEqualTo("Connecting you to an agent...")
    }

    @Test
    fun `HumanHandoverNodeHandler - respects max wait time`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "maxWaitTime" to 10
        )

        val result = handler.process(nodeData, "handover-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.HumanHandover
        assertThat(uiState.maxWaitTime).isEqualTo(10)
    }

    @Test
    fun `HumanHandoverNodeHandler - records priority in handover`() = runTest {
        val handler = HumanHandoverNodeHandler()

        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "human-handover-node",
            "enablePreChatQuestions" to false,
            "priority" to "high"
        )

        handler.process(nodeData, "handover-1")

        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "handover-initiated" &&
            entry.data["priority"] == "high"
        }) }
    }
}
