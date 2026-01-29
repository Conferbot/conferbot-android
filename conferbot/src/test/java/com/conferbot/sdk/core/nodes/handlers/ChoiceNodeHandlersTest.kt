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
 * Unit tests for Choice Node Handlers
 * Tests TwoChoicesNodeHandler, ThreeChoicesNodeHandler, NChoicesNodeHandler,
 * ImageChoiceNodeHandler, RatingChoiceNodeHandler, and other choice-related nodes
 */
class ChoiceNodeHandlersTest {

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

    // ==================== TwoChoicesNodeHandler Tests ====================

    @Test
    fun `TwoChoicesNodeHandler - displays two choices`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "Yes",
            "choice2" to "No",
            "answerVariable" to "user_choice"
        )

        val result = handler.process(nodeData, "two-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.SingleChoice::class.java)

        val choiceState = uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices).hasSize(2)
        assertThat(choiceState.choices[0].text).isEqualTo("Yes")
        assertThat(choiceState.choices[1].text).isEqualTo("No")
        assertThat(choiceState.answerKey).isEqualTo("user_choice")
    }

    @Test
    fun `TwoChoicesNodeHandler - displays single choice when second disabled`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "Proceed",
            "choice2" to "Cancel",
            "disableSecondChoice" to true,
            "answerVariable" to "choice"
        )

        val result = handler.process(nodeData, "two-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices).hasSize(1)
        assertThat(choiceState.choices[0].text).isEqualTo("Proceed")
    }

    @Test
    fun `TwoChoicesNodeHandler - strips HTML from choices`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "<b>Bold Choice</b>",
            "choice2" to "<i>Italic Choice</i>",
            "answerVariable" to "choice"
        )

        val result = handler.process(nodeData, "two-choice-1")

        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices[0].text).isEqualTo("Bold Choice")
        assertThat(choiceState.choices[1].text).isEqualTo("Italic Choice")
    }

    @Test
    fun `TwoChoicesNodeHandler - handles response for first choice`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "Yes",
            "choice2" to "No",
            "answerVariable" to "choice"
        )

        val response = mapOf("id" to "0", "text" to "Yes")
        val result = handler.handleResponse(response, nodeData, "two-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-1")
        assertThat(result.delayMs).isEqualTo(600)

        verify { ChatState.setAnswerVariable("two-choice-1", "Yes") }
        verify { ChatState.addToTranscript("user", "Yes") }
    }

    @Test
    fun `TwoChoicesNodeHandler - handles response for second choice`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "Yes",
            "choice2" to "No",
            "answerVariable" to "choice"
        )

        val response = mapOf("id" to "1", "text" to "No")
        val result = handler.handleResponse(response, nodeData, "two-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-2")
    }

    @Test
    fun `TwoChoicesNodeHandler - uses default answer variable`() = runTest {
        val handler = TwoChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "two-choices-node",
            "choice1" to "A",
            "choice2" to "B"
            // No answerVariable specified
        )

        val result = handler.process(nodeData, "two-choice-1")

        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.answerKey).isEqualTo("two-choice-1")
    }

    // ==================== ThreeChoicesNodeHandler Tests ====================

    @Test
    fun `ThreeChoicesNodeHandler - displays three choices`() = runTest {
        val handler = ThreeChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "three-choices-node",
            "choice1" to "Option A",
            "choice2" to "Option B",
            "choice3" to "Option C",
            "answerVariable" to "selected"
        )

        val result = handler.process(nodeData, "three-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices).hasSize(3)
        assertThat(choiceState.choices[0].text).isEqualTo("Option A")
        assertThat(choiceState.choices[1].text).isEqualTo("Option B")
        assertThat(choiceState.choices[2].text).isEqualTo("Option C")
    }

    @Test
    fun `ThreeChoicesNodeHandler - handles response`() = runTest {
        val handler = ThreeChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "three-choices-node",
            "choice1" to "A",
            "choice2" to "B",
            "choice3" to "C",
            "answerVariable" to "choice"
        )

        val response = mapOf("id" to "2", "text" to "C")
        val result = handler.handleResponse(response, nodeData, "three-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-3")
    }

    @Test
    fun `ThreeChoicesNodeHandler - maps choice to node data when text not provided`() = runTest {
        val handler = ThreeChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "three-choices-node",
            "choice1" to "First",
            "choice2" to "Second",
            "choice3" to "Third",
            "answerVariable" to "choice"
        )

        val response = mapOf("id" to "1")  // No text provided
        val result = handler.handleResponse(response, nodeData, "three-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        verify { ChatState.setAnswerVariable("three-choice-1", "Second") }
    }

    // ==================== NChoicesNodeHandler Tests ====================

    @Test
    fun `NChoicesNodeHandler - displays dynamic choices`() = runTest {
        val handler = NChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-choices-node",
            "choices" to listOf(
                mapOf("id" to "0", "choiceText" to "Choice 1"),
                mapOf("id" to "1", "choiceText" to "Choice 2"),
                mapOf("id" to "2", "choiceText" to "Choice 3"),
                mapOf("id" to "3", "choiceText" to "Choice 4")
            ),
            "answerVariable" to "selection"
        )

        val result = handler.process(nodeData, "n-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices).hasSize(4)
        assertThat(choiceState.choices[0].text).isEqualTo("Choice 1")
        assertThat(choiceState.choices[3].text).isEqualTo("Choice 4")
    }

    @Test
    fun `NChoicesNodeHandler - handles text key in choice data`() = runTest {
        val handler = NChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-choices-node",
            "choices" to listOf(
                mapOf("id" to "a", "text" to "Text A"),
                mapOf("id" to "b", "text" to "Text B")
            ),
            "answerVariable" to "selection"
        )

        val result = handler.process(nodeData, "n-choice-1")

        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices[0].text).isEqualTo("Text A")
        assertThat(choiceState.choices[1].text).isEqualTo("Text B")
    }

    @Test
    fun `NChoicesNodeHandler - handles response with dynamic port`() = runTest {
        val handler = NChoicesNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-choices-node",
            "choices" to listOf(
                mapOf("id" to "opt-a", "choiceText" to "Option A"),
                mapOf("id" to "opt-b", "choiceText" to "Option B")
            ),
            "answerVariable" to "selection"
        )

        val response = mapOf("id" to "opt-b", "text" to "Option B")
        val result = handler.handleResponse(response, nodeData, "n-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-opt-b")
    }

    // ==================== ImageChoiceNodeHandler Tests ====================

    @Test
    fun `ImageChoiceNodeHandler - displays image choices`() = runTest {
        val handler = ImageChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "image-choice-node",
            "images" to listOf(
                mapOf(
                    "id" to "img1",
                    "image" to "https://example.com/image1.png",
                    "label" to "Image 1"
                ),
                mapOf(
                    "id" to "img2",
                    "image" to "https://example.com/image2.png",
                    "label" to "Image 2"
                )
            ),
            "answerVariable" to "selected_image"
        )

        val result = handler.process(nodeData, "image-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.ImageChoice::class.java)

        val imageState = uiState as NodeUIState.ImageChoice
        assertThat(imageState.images).hasSize(2)
        assertThat(imageState.images[0].imageUrl).isEqualTo("https://example.com/image1.png")
        assertThat(imageState.images[0].label).isEqualTo("Image 1")
        assertThat(imageState.images[1].id).isEqualTo("img2")
    }

    @Test
    fun `ImageChoiceNodeHandler - handles response`() = runTest {
        val handler = ImageChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "image-choice-node",
            "images" to listOf(
                mapOf("id" to "img1", "image" to "url1", "label" to "Label 1")
            ),
            "answerVariable" to "image"
        )

        val response = mapOf(
            "id" to "img1",
            "label" to "Label 1",
            "imageUrl" to "url1"
        )

        val result = handler.handleResponse(response, nodeData, "image-choice-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-img1")

        verify { ChatState.setAnswerVariable("image-choice-1", "Label 1") }
        verify { ChatState.addToTranscript("user", "Label 1") }
    }

    @Test
    fun `ImageChoiceNodeHandler - records image selection details`() = runTest {
        val handler = ImageChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "image-choice-node",
            "images" to emptyList<Map<String, Any?>>(),
            "answerVariable" to "image"
        )

        val response = mapOf(
            "id" to "selected",
            "label" to "Selected Image",
            "imageUrl" to "https://example.com/selected.png"
        )

        handler.handleResponse(response, nodeData, "image-choice-1")

        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "user-image-choice" &&
            entry.data["imageId"] == "selected" &&
            entry.data["imageUrl"] == "https://example.com/selected.png"
        }) }
    }

    // ==================== RatingChoiceNodeHandler Tests ====================

    @Test
    fun `RatingChoiceNodeHandler - displays 5-star rating by default`() = runTest {
        val handler = RatingChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "rating-choice-node",
            "answerVariable" to "rating"
        )

        val result = handler.process(nodeData, "rating-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Rating::class.java)

        val ratingState = uiState as NodeUIState.Rating
        assertThat(ratingState.ratingType).isEqualTo(NodeUIState.Rating.RatingType.STAR)
        assertThat(ratingState.minValue).isEqualTo(1)
        assertThat(ratingState.maxValue).isEqualTo(5)
    }

    @Test
    fun `RatingChoiceNodeHandler - displays smiley rating`() = runTest {
        val handler = RatingChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "rating-choice-node",
            "ratingType" to "smiley",
            "answerVariable" to "mood"
        )

        val result = handler.process(nodeData, "rating-1")

        val ratingState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Rating
        assertThat(ratingState.ratingType).isEqualTo(NodeUIState.Rating.RatingType.SMILEY)
        assertThat(ratingState.minValue).isEqualTo(1)
        assertThat(ratingState.maxValue).isEqualTo(5)
    }

    @Test
    fun `RatingChoiceNodeHandler - displays 10-point rating`() = runTest {
        val handler = RatingChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "rating-choice-node",
            "ratingType" to "10",
            "answerVariable" to "nps"
        )

        val result = handler.process(nodeData, "rating-1")

        val ratingState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Rating
        assertThat(ratingState.ratingType).isEqualTo(NodeUIState.Rating.RatingType.NUMBER)
        assertThat(ratingState.minValue).isEqualTo(1)
        assertThat(ratingState.maxValue).isEqualTo(10)
    }

    @Test
    fun `RatingChoiceNodeHandler - handles numeric response`() = runTest {
        val handler = RatingChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "rating-choice-node",
            "answerVariable" to "rating"
        )

        val result = handler.handleResponse(4, nodeData, "rating-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-3")  // 4-1=3

        verify { ChatState.setAnswerVariable("rating-1", 4) }
        verify { ChatState.addToTranscript("user", "4") }
    }

    @Test
    fun `RatingChoiceNodeHandler - handles string response`() = runTest {
        val handler = RatingChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "rating-choice-node",
            "answerVariable" to "rating"
        )

        val result = handler.handleResponse("5", nodeData, "rating-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-4")  // 5-1=4

        verify { ChatState.setAnswerVariable("rating-1", 5) }
    }

    // ==================== YesOrNoChoiceNodeHandler Tests ====================

    @Test
    fun `YesOrNoChoiceNodeHandler - displays default yes and no`() = runTest {
        val handler = YesOrNoChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "yes-or-no-choice-node",
            "answerVariable" to "confirmation"
        )

        val result = handler.process(nodeData, "yesno-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice

        assertThat(choiceState.choices).hasSize(2)
        assertThat(choiceState.choices[0].id).isEqualTo("yes")
        assertThat(choiceState.choices[0].text).isEqualTo("Yes")
        assertThat(choiceState.choices[1].id).isEqualTo("no")
        assertThat(choiceState.choices[1].text).isEqualTo("No")
    }

    @Test
    fun `YesOrNoChoiceNodeHandler - displays custom options`() = runTest {
        val handler = YesOrNoChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "yes-or-no-choice-node",
            "options" to listOf(
                mapOf("id" to "accept", "label" to "I Accept"),
                mapOf("id" to "decline", "label" to "I Decline")
            ),
            "answerVariable" to "terms"
        )

        val result = handler.process(nodeData, "yesno-1")

        val choiceState = (result as NodeResult.DisplayUI).uiState as NodeUIState.SingleChoice
        assertThat(choiceState.choices).hasSize(2)
        assertThat(choiceState.choices[0].id).isEqualTo("accept")
        assertThat(choiceState.choices[0].text).isEqualTo("I Accept")
    }

    @Test
    fun `YesOrNoChoiceNodeHandler - handles response`() = runTest {
        val handler = YesOrNoChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "yes-or-no-choice-node",
            "answerVariable" to "answer"
        )

        val response = mapOf("id" to "yes", "text" to "Yes")
        val result = handler.handleResponse(response, nodeData, "yesno-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-yes")
    }

    // ==================== OpinionScaleChoiceNodeHandler Tests ====================

    @Test
    fun `OpinionScaleChoiceNodeHandler - displays scale with default range`() = runTest {
        val handler = OpinionScaleChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "opinion-scale-choice-node",
            "answerVariable" to "satisfaction"
        )

        val result = handler.process(nodeData, "scale-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val ratingState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Rating

        assertThat(ratingState.ratingType).isEqualTo(NodeUIState.Rating.RatingType.OPINION_SCALE)
        assertThat(ratingState.minValue).isEqualTo(1)
        assertThat(ratingState.maxValue).isEqualTo(10)
    }

    @Test
    fun `OpinionScaleChoiceNodeHandler - displays scale with custom range`() = runTest {
        val handler = OpinionScaleChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "opinion-scale-choice-node",
            "from" to 0,
            "to" to 5,
            "answerVariable" to "score"
        )

        val result = handler.process(nodeData, "scale-1")

        val ratingState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Rating
        assertThat(ratingState.minValue).isEqualTo(0)
        assertThat(ratingState.maxValue).isEqualTo(5)
    }

    @Test
    fun `OpinionScaleChoiceNodeHandler - handles response with correct port`() = runTest {
        val handler = OpinionScaleChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "opinion-scale-choice-node",
            "from" to 1,
            "to" to 10,
            "answerVariable" to "nps"
        )

        val result = handler.handleResponse(7, nodeData, "scale-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        // Port index = value - from = 7 - 1 = 6
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-6")
    }

    @Test
    fun `OpinionScaleChoiceNodeHandler - handles zero-based scale`() = runTest {
        val handler = OpinionScaleChoiceNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "opinion-scale-choice-node",
            "from" to 0,
            "to" to 10,
            "answerVariable" to "score"
        )

        val result = handler.handleResponse(5, nodeData, "scale-1")

        // Port index = value - from = 5 - 0 = 5
        assertThat((result as NodeResult.DelayedProceed).targetPort).isEqualTo("source-5")
    }

    // ==================== UserRatingNodeHandler Tests ====================

    @Test
    fun `UserRatingNodeHandler - displays 5-star rating`() = runTest {
        val handler = UserRatingNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "user-rating-node",
            "answerVariable" to "rating"
        )

        val result = handler.process(nodeData, "user-rating-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val ratingState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Rating

        assertThat(ratingState.ratingType).isEqualTo(NodeUIState.Rating.RatingType.STAR)
        assertThat(ratingState.minValue).isEqualTo(1)
        assertThat(ratingState.maxValue).isEqualTo(5)
    }

    @Test
    fun `UserRatingNodeHandler - handles response and proceeds`() = runTest {
        val handler = UserRatingNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "user-rating-node",
            "answerVariable" to "rating"
        )

        val result = handler.handleResponse(3, nodeData, "user-rating-1")

        // UserRatingNode proceeds without specific port (unlike RatingChoiceNode)
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.setAnswerVariable("user-rating-1", 3) }
    }

    // ==================== SelectOptionNodeHandler Tests ====================

    @Test
    fun `SelectOptionNodeHandler - displays dropdown options`() = runTest {
        val handler = SelectOptionNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "select-option-node",
            "option1" to "Option A",
            "option2" to "Option B",
            "option3" to "Option C",
            "answerVariable" to "selected"
        )

        val result = handler.process(nodeData, "select-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Dropdown::class.java)

        val dropdownState = uiState as NodeUIState.Dropdown
        assertThat(dropdownState.options).hasSize(3)
    }

    @Test
    fun `SelectOptionNodeHandler - skips disabled options`() = runTest {
        val handler = SelectOptionNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "select-option-node",
            "option1" to "Option A",
            "option2" to "Option B",
            "disableOption2" to true,
            "option3" to "Option C",
            "answerVariable" to "selected"
        )

        val result = handler.process(nodeData, "select-1")

        val dropdownState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Dropdown
        assertThat(dropdownState.options).hasSize(2)
        assertThat(dropdownState.options.map { it.text }).containsExactly("Option A", "Option C")
    }

    @Test
    fun `SelectOptionNodeHandler - handles response`() = runTest {
        val handler = SelectOptionNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "select-option-node",
            "option1" to "A",
            "option2" to "B",
            "answerVariable" to "selected"
        )

        val response = mapOf("text" to "A")
        val result = handler.handleResponse(response, nodeData, "select-1")

        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        verify { ChatState.setAnswerVariable("select-1", "A") }
    }

    // ==================== NSelectOptionNodeHandler Tests ====================

    @Test
    fun `NSelectOptionNodeHandler - displays dynamic dropdown`() = runTest {
        val handler = NSelectOptionNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-select-option-node",
            "options" to listOf(
                mapOf("id" to "1", "optionText" to "First"),
                mapOf("id" to "2", "optionText" to "Second"),
                mapOf("id" to "3", "optionText" to "Third")
            ),
            "answerVariable" to "selection"
        )

        val result = handler.process(nodeData, "n-select-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val dropdownState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Dropdown
        assertThat(dropdownState.options).hasSize(3)
    }

    // ==================== NCheckOptionsNodeHandler Tests ====================

    @Test
    fun `NCheckOptionsNodeHandler - displays checkboxes`() = runTest {
        val handler = NCheckOptionsNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-check-options-node",
            "options" to listOf(
                mapOf("id" to "1", "optionText" to "Check 1"),
                mapOf("id" to "2", "optionText" to "Check 2"),
                mapOf("id" to "3", "optionText" to "Check 3")
            ),
            "answerVariable" to "checked"
        )

        val result = handler.process(nodeData, "check-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.MultipleChoice::class.java)

        val multiState = uiState as NodeUIState.MultipleChoice
        assertThat(multiState.options).hasSize(3)
    }

    @Test
    fun `NCheckOptionsNodeHandler - handles list response`() = runTest {
        val handler = NCheckOptionsNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-check-options-node",
            "options" to emptyList<Map<String, Any?>>(),
            "answerVariable" to "checked"
        )

        val response = listOf("Option A", "Option C")
        val result = handler.handleResponse(response, nodeData, "check-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.setAnswerVariable("check-1", "Option A, Option C") }
        verify { ChatState.addToTranscript("user", "Option A, Option C") }
    }

    @Test
    fun `NCheckOptionsNodeHandler - handles comma-separated string response`() = runTest {
        val handler = NCheckOptionsNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-check-options-node",
            "options" to emptyList<Map<String, Any?>>(),
            "answerVariable" to "checked"
        )

        val response = "A, B, C"
        val result = handler.handleResponse(response, nodeData, "check-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.setAnswerVariable("check-1", "A, B, C") }
    }

    @Test
    fun `NCheckOptionsNodeHandler - records selected options`() = runTest {
        val handler = NCheckOptionsNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit
        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "n-check-options-node",
            "options" to emptyList<Map<String, Any?>>(),
            "answerVariable" to "checked"
        )

        val response = listOf("X", "Y")
        handler.handleResponse(response, nodeData, "check-1")

        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "user-check-options-response" &&
            @Suppress("UNCHECKED_CAST")
            (entry.data["selectedOptions"] as? List<String>)?.containsAll(listOf("X", "Y")) == true
        }) }
    }
}
