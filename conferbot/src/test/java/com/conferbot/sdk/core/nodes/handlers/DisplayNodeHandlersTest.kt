package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeTypes
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Display Node Handlers
 * Tests all display-related node handlers including:
 * - MessageNodeHandler
 * - ImageNodeHandler
 * - VideoNodeHandler
 * - AudioNodeHandler
 * - FileNodeHandler
 * - HtmlNodeHandler
 * - UserRedirectNodeHandler
 * - NavigateNodeHandler
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayNodeHandlersTest {

    @Before
    fun setUp() {
        ChatState.reset()
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
    }

    // ==================== MESSAGE NODE HANDLER TESTS ====================

    @Test
    fun `MessageNodeHandler returns correct node type`() {
        val handler = MessageNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.MESSAGE)
    }

    @Test
    fun `MessageNodeHandler processes message text`() = runTest {
        // Given
        val handler = MessageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createMessageNodeData(
            text = "Hello, welcome!"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Message::class.java)
        assertThat((uiState as NodeUIState.Message).text).isEqualTo("Hello, welcome!")
        assertThat(uiState.nodeId).isEqualTo(nodeId)
    }

    @Test
    fun `MessageNodeHandler uses message field when text is empty`() = runTest {
        // Given
        val handler = MessageNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.MESSAGE,
            "text" to "",
            "message" to "Fallback message"
        )

        // When
        val result = handler.process(nodeData, "msg-1")

        // Then
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Message
        assertThat(uiState.text).isEqualTo("Fallback message")
    }

    @Test
    fun `MessageNodeHandler adds message to transcript`() = runTest {
        // Given
        val handler = MessageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createMessageNodeData(
            text = "Bot greeting"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript).hasSize(1)
        assertThat(transcript[0].by).isEqualTo("bot")
        assertThat(transcript[0].message).isEqualTo("Bot greeting")
    }

    @Test
    fun `MessageNodeHandler records response`() = runTest {
        // Given
        val handler = MessageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createMessageNodeData(
            text = "Recorded message"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record).hasSize(1)
        assertThat(record[0].id).isEqualTo(nodeId)
        assertThat(record[0].shape).isEqualTo("bot-message")
        assertThat(record[0].text).isEqualTo("Recorded message")
    }

    // ==================== IMAGE NODE HANDLER TESTS ====================

    @Test
    fun `ImageNodeHandler returns correct node type`() {
        val handler = ImageNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.IMAGE)
    }

    @Test
    fun `ImageNodeHandler processes image with caption`() = runTest {
        // Given
        val handler = ImageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createImageNodeData(
            imageUrl = "https://example.com/photo.jpg",
            caption = "Beautiful sunset"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Image::class.java)
        val imageState = uiState as NodeUIState.Image
        assertThat(imageState.url).isEqualTo("https://example.com/photo.jpg")
        assertThat(imageState.caption).isEqualTo("Beautiful sunset")
        assertThat(imageState.nodeId).isEqualTo(nodeId)
    }

    @Test
    fun `ImageNodeHandler handles null caption`() = runTest {
        // Given
        val handler = ImageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createImageNodeData(
            imageUrl = "https://example.com/photo.jpg",
            caption = null
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Image
        assertThat(uiState.caption).isNull()
    }

    @Test
    fun `ImageNodeHandler adds to transcript with caption or fallback`() = runTest {
        // Given
        val handler = ImageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createImageNodeData(
            imageUrl = "https://example.com/photo.jpg",
            caption = null
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript).hasSize(1)
        assertThat(transcript[0].message).isEqualTo("[Image]")
    }

    @Test
    fun `ImageNodeHandler records response with imageUrl`() = runTest {
        // Given
        val handler = ImageNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createImageNodeData(
            imageUrl = "https://example.com/photo.jpg"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record).hasSize(1)
        assertThat(record[0].shape).isEqualTo("bot-image")
        assertThat(record[0].data["imageUrl"]).isEqualTo("https://example.com/photo.jpg")
    }

    // ==================== VIDEO NODE HANDLER TESTS ====================

    @Test
    fun `VideoNodeHandler returns correct node type`() {
        val handler = VideoNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.VIDEO)
    }

    @Test
    fun `VideoNodeHandler processes video with caption`() = runTest {
        // Given
        val handler = VideoNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createVideoNodeData(
            videoUrl = "https://example.com/video.mp4",
            caption = "Product demo"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Video::class.java)
        val videoState = uiState as NodeUIState.Video
        assertThat(videoState.url).isEqualTo("https://example.com/video.mp4")
        assertThat(videoState.caption).isEqualTo("Product demo")
    }

    @Test
    fun `VideoNodeHandler adds to transcript`() = runTest {
        // Given
        val handler = VideoNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createVideoNodeData(
            caption = "Watch this"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("Watch this")
    }

    @Test
    fun `VideoNodeHandler uses fallback transcript for null caption`() = runTest {
        // Given
        val handler = VideoNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createVideoNodeData(
            caption = null
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("[Video]")
    }

    @Test
    fun `VideoNodeHandler records response with videoUrl`() = runTest {
        // Given
        val handler = VideoNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createVideoNodeData(
            videoUrl = "https://example.com/video.mp4"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-video")
        assertThat(record[0].data["videoUrl"]).isEqualTo("https://example.com/video.mp4")
    }

    // ==================== AUDIO NODE HANDLER TESTS ====================

    @Test
    fun `AudioNodeHandler returns correct node type`() {
        val handler = AudioNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.AUDIO)
    }

    @Test
    fun `AudioNodeHandler processes audio with caption`() = runTest {
        // Given
        val handler = AudioNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAudioNodeData(
            audioUrl = "https://example.com/podcast.mp3",
            caption = "Episode 1"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Audio::class.java)
        val audioState = uiState as NodeUIState.Audio
        assertThat(audioState.url).isEqualTo("https://example.com/podcast.mp3")
        assertThat(audioState.caption).isEqualTo("Episode 1")
    }

    @Test
    fun `AudioNodeHandler uses fallback transcript for null caption`() = runTest {
        // Given
        val handler = AudioNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAudioNodeData(
            caption = null
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("[Audio]")
    }

    @Test
    fun `AudioNodeHandler records response with audioUrl`() = runTest {
        // Given
        val handler = AudioNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAudioNodeData(
            audioUrl = "https://example.com/audio.mp3"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-audio")
        assertThat(record[0].data["audioUrl"]).isEqualTo("https://example.com/audio.mp3")
    }

    // ==================== FILE NODE HANDLER TESTS ====================

    @Test
    fun `FileNodeHandler returns correct node type`() {
        val handler = FileNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.FILE)
    }

    @Test
    fun `FileNodeHandler processes file with name`() = runTest {
        // Given
        val handler = FileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createFileNodeData(
            fileUrl = "https://example.com/document.pdf",
            fileName = "Report.pdf"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.File::class.java)
        val fileState = uiState as NodeUIState.File
        assertThat(fileState.url).isEqualTo("https://example.com/document.pdf")
        assertThat(fileState.fileName).isEqualTo("Report.pdf")
    }

    @Test
    fun `FileNodeHandler uses default fileName when not provided`() = runTest {
        // Given
        val handler = FileNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.FILE,
            "file" to "https://example.com/file.zip"
            // fileName not provided
        )

        // When
        val result = handler.process(nodeData, "file-1")

        // Then
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.File
        assertThat(uiState.fileName).isEqualTo("Download File")
    }

    @Test
    fun `FileNodeHandler adds to transcript with file name`() = runTest {
        // Given
        val handler = FileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createFileNodeData(
            fileName = "MyDocument.pdf"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("[File: MyDocument.pdf]")
    }

    @Test
    fun `FileNodeHandler records response with file data`() = runTest {
        // Given
        val handler = FileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createFileNodeData(
            fileUrl = "https://example.com/doc.pdf",
            fileName = "Doc.pdf"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-file")
        assertThat(record[0].data["fileUrl"]).isEqualTo("https://example.com/doc.pdf")
        assertThat(record[0].data["fileName"]).isEqualTo("Doc.pdf")
    }

    // ==================== HTML NODE HANDLER TESTS ====================

    @Test
    fun `HtmlNodeHandler returns correct node type`() {
        val handler = HtmlNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.HTML)
    }

    @Test
    fun `HtmlNodeHandler processes HTML content`() = runTest {
        // Given
        val handler = HtmlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createHtmlNodeData(
            htmlContent = "<div><b>Bold</b> text</div>"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Html::class.java)
        val htmlState = uiState as NodeUIState.Html
        assertThat(htmlState.htmlContent).isEqualTo("<div><b>Bold</b> text</div>")
    }

    @Test
    fun `HtmlNodeHandler adds generic transcript entry`() = runTest {
        // Given
        val handler = HtmlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createHtmlNodeData()

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("[HTML Content]")
    }

    @Test
    fun `HtmlNodeHandler records response with html data`() = runTest {
        // Given
        val handler = HtmlNodeHandler()
        val htmlContent = "<p>Sample HTML</p>"
        val (nodeId, nodeData) = TestFixtures.createHtmlNodeData(
            htmlContent = htmlContent
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-html")
        assertThat(record[0].data["html"]).isEqualTo(htmlContent)
    }

    // ==================== USER REDIRECT NODE HANDLER TESTS ====================

    @Test
    fun `UserRedirectNodeHandler returns correct node type`() {
        val handler = UserRedirectNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.USER_REDIRECT)
    }

    @Test
    fun `UserRedirectNodeHandler processes redirect URL`() = runTest {
        // Given
        val handler = UserRedirectNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createRedirectNodeData(
            url = "https://example.com/page",
            openInNewTab = true
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Redirect::class.java)
        val redirectState = uiState as NodeUIState.Redirect
        assertThat(redirectState.url).isEqualTo("https://example.com/page")
        assertThat(redirectState.openInNewTab).isTrue()
    }

    @Test
    fun `UserRedirectNodeHandler handles openInNewTab false`() = runTest {
        // Given
        val handler = UserRedirectNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createRedirectNodeData(
            url = "https://example.com",
            openInNewTab = false
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val redirectState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Redirect
        assertThat(redirectState.openInNewTab).isFalse()
    }

    @Test
    fun `UserRedirectNodeHandler adds to transcript with URL`() = runTest {
        // Given
        val handler = UserRedirectNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createRedirectNodeData(
            url = "https://target.com"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript[0].message).isEqualTo("[Redirect to: https://target.com]")
    }

    @Test
    fun `UserRedirectNodeHandler records response`() = runTest {
        // Given
        val handler = UserRedirectNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createRedirectNodeData(
            url = "https://example.com"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-redirect")
        assertThat(record[0].text).isEqualTo("https://example.com")
    }

    // ==================== NAVIGATE NODE HANDLER TESTS ====================

    @Test
    fun `NavigateNodeHandler returns correct node type`() {
        val handler = NavigateNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.NAVIGATE)
    }

    @Test
    fun `NavigateNodeHandler processes navigation URL`() = runTest {
        // Given
        val handler = NavigateNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.NAVIGATE,
            "url" to "https://internal.app/page"
        )

        // When
        val result = handler.process(nodeData, "nav-1")

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Redirect::class.java)
        val redirectState = uiState as NodeUIState.Redirect
        assertThat(redirectState.url).isEqualTo("https://internal.app/page")
        assertThat(redirectState.openInNewTab).isFalse() // Navigate always in same tab
    }

    @Test
    fun `NavigateNodeHandler records response`() = runTest {
        // Given
        val handler = NavigateNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.NAVIGATE,
            "url" to "https://internal.app/page"
        )

        // When
        handler.process(nodeData, "nav-1")

        // Then
        val record = ChatState.record.value
        assertThat(record[0].shape).isEqualTo("bot-navigate")
        assertThat(record[0].text).isEqualTo("https://internal.app/page")
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `MessageNodeHandler handles empty text`() = runTest {
        // Given
        val handler = MessageNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.MESSAGE,
            "text" to "",
            "message" to ""
        )

        // When
        val result = handler.process(nodeData, "msg-empty")

        // Then
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Message
        assertThat(uiState.text).isEmpty()
    }

    @Test
    fun `ImageNodeHandler handles empty URL`() = runTest {
        // Given
        val handler = ImageNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.IMAGE,
            "image" to ""
        )

        // When
        val result = handler.process(nodeData, "img-empty")

        // Then
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Image
        assertThat(uiState.url).isEmpty()
    }

    @Test
    fun `handlers return correct nodeId in UI state`() = runTest {
        // Given
        val messageHandler = MessageNodeHandler()
        val imageHandler = ImageNodeHandler()
        val videoHandler = VideoNodeHandler()

        val expectedNodeId = "unique-node-123"

        // When
        val msgResult = messageHandler.process(
            mapOf("type" to NodeTypes.MESSAGE, "text" to "test"),
            expectedNodeId
        )
        val imgResult = imageHandler.process(
            mapOf("type" to NodeTypes.IMAGE, "image" to "url"),
            expectedNodeId
        )
        val vidResult = videoHandler.process(
            mapOf("type" to NodeTypes.VIDEO, "video" to "url"),
            expectedNodeId
        )

        // Then
        assertThat((msgResult as NodeResult.DisplayUI).uiState)
            .isInstanceOf(NodeUIState.Message::class.java)
        assertThat(((msgResult.uiState) as NodeUIState.Message).nodeId)
            .isEqualTo(expectedNodeId)

        assertThat((imgResult as NodeResult.DisplayUI).uiState)
            .isInstanceOf(NodeUIState.Image::class.java)
        assertThat(((imgResult.uiState) as NodeUIState.Image).nodeId)
            .isEqualTo(expectedNodeId)

        assertThat((vidResult as NodeResult.DisplayUI).uiState)
            .isInstanceOf(NodeUIState.Video::class.java)
        assertThat(((vidResult.uiState) as NodeUIState.Video).nodeId)
            .isEqualTo(expectedNodeId)
    }

    // ==================== MULTIPLE HANDLER SEQUENCE TESTS ====================

    @Test
    fun `multiple display handlers can process sequentially`() = runTest {
        // Given
        val messageHandler = MessageNodeHandler()
        val imageHandler = ImageNodeHandler()
        val videoHandler = VideoNodeHandler()

        // When - Process multiple nodes in sequence
        messageHandler.process(
            mapOf("type" to NodeTypes.MESSAGE, "text" to "Message 1"),
            "msg-1"
        )
        imageHandler.process(
            mapOf("type" to NodeTypes.IMAGE, "image" to "img1.jpg", "caption" to "Image 1"),
            "img-1"
        )
        videoHandler.process(
            mapOf("type" to NodeTypes.VIDEO, "video" to "vid1.mp4", "caption" to "Video 1"),
            "vid-1"
        )

        // Then - All should be in transcript and record
        val transcript = ChatState.transcript.value
        assertThat(transcript).hasSize(3)
        assertThat(transcript[0].message).isEqualTo("Message 1")
        assertThat(transcript[1].message).isEqualTo("Image 1")
        assertThat(transcript[2].message).isEqualTo("Video 1")

        val record = ChatState.record.value
        assertThat(record).hasSize(3)
        assertThat(record.map { it.shape }).containsExactly("bot-message", "bot-image", "bot-video")
    }
}
