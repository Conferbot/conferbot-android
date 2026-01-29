package com.conferbot.sdk.testutils

import com.conferbot.sdk.core.nodes.NodeTypes

/**
 * Test fixtures providing sample node data for unit tests
 */
object TestFixtures {

    // ==================== SESSION DATA ====================

    const val TEST_CHAT_SESSION_ID = "test-session-123"
    const val TEST_VISITOR_ID = "visitor-456"
    const val TEST_BOT_ID = "bot-789"
    const val TEST_WORKSPACE_ID = "workspace-abc"
    const val TEST_API_KEY = "test-api-key"

    // ==================== MESSAGE NODES ====================

    fun createMessageNodeData(
        text: String = "Hello, welcome to our chat!",
        nodeId: String = "message-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.MESSAGE,
            "text" to text,
            "message" to text,
            "label" to "Welcome Message"
        )
    }

    fun createImageNodeData(
        imageUrl: String = "https://example.com/image.png",
        caption: String? = "Sample image",
        nodeId: String = "image-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.IMAGE,
            "image" to imageUrl,
            "caption" to caption,
            "label" to "Image Display"
        )
    }

    fun createVideoNodeData(
        videoUrl: String = "https://example.com/video.mp4",
        caption: String? = "Sample video",
        nodeId: String = "video-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.VIDEO,
            "video" to videoUrl,
            "caption" to caption,
            "label" to "Video Display"
        )
    }

    fun createAudioNodeData(
        audioUrl: String = "https://example.com/audio.mp3",
        caption: String? = "Sample audio",
        nodeId: String = "audio-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.AUDIO,
            "audio" to audioUrl,
            "caption" to caption,
            "label" to "Audio Display"
        )
    }

    fun createFileNodeData(
        fileUrl: String = "https://example.com/document.pdf",
        fileName: String = "document.pdf",
        nodeId: String = "file-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.FILE,
            "file" to fileUrl,
            "fileName" to fileName,
            "label" to "File Download"
        )
    }

    fun createHtmlNodeData(
        htmlContent: String = "<div><b>Bold text</b></div>",
        nodeId: String = "html-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.HTML,
            "html" to htmlContent,
            "label" to "HTML Content"
        )
    }

    fun createRedirectNodeData(
        url: String = "https://example.com",
        openInNewTab: Boolean = true,
        nodeId: String = "redirect-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.USER_REDIRECT,
            "url" to url,
            "openInNewTab" to openInNewTab,
            "label" to "Redirect"
        )
    }

    // ==================== ASK QUESTION NODES ====================

    fun createAskNameNodeData(
        questionText: String = "What is your name?",
        answerVariable: String = "name",
        nameGreetResponse: String? = "Nice to meet you, {name}!",
        nodeId: String = "ask-name-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_NAME,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "nameGreetResponse" to nameGreetResponse,
            "label" to "Ask Name"
        )
    }

    fun createAskEmailNodeData(
        questionText: String = "What is your email?",
        answerVariable: String = "email",
        incorrectEmailResponse: String = "Please enter a valid email address",
        nodeId: String = "ask-email-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_EMAIL,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "incorrectEmailResponse" to incorrectEmailResponse,
            "label" to "Ask Email"
        )
    }

    fun createAskPhoneNodeData(
        questionText: String = "What is your phone number?",
        answerVariable: String = "phone",
        incorrectPhoneNumberResponse: String = "Please enter a valid phone number",
        nodeId: String = "ask-phone-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_PHONE,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "incorrectPhoneNumberResponse" to incorrectPhoneNumberResponse,
            "label" to "Ask Phone"
        )
    }

    fun createAskNumberNodeData(
        questionText: String = "Enter a number",
        answerVariable: String = "number",
        nodeId: String = "ask-number-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_NUMBER,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "label" to "Ask Number"
        )
    }

    fun createAskUrlNodeData(
        questionText: String = "Enter a URL",
        answerVariable: String = "url",
        nodeId: String = "ask-url-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_URL,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "label" to "Ask URL"
        )
    }

    fun createAskLocationNodeData(
        questionText: String = "What is your location?",
        answerVariable: String = "location",
        nodeId: String = "ask-location-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_LOCATION,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "label" to "Ask Location"
        )
    }

    fun createAskCustomNodeData(
        questionText: String = "Custom question",
        answerVariable: String = "customAnswer",
        nodeId: String = "ask-custom-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_CUSTOM,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "label" to "Ask Custom"
        )
    }

    fun createAskFileNodeData(
        questionText: String = "Please upload a file",
        answerVariable: String = "file",
        maxSize: Int = 5,
        nodeId: String = "ask-file-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.ASK_FILE,
            "questionText" to questionText,
            "answerVariable" to answerVariable,
            "maxSize" to maxSize,
            "label" to "Ask File"
        )
    }

    fun createCalendarNodeData(
        questionText: String? = "Select a date",
        showTimeSelection: Boolean = false,
        timezone: String? = "America/New_York",
        answerVariable: String = "calendar_selection",
        nodeId: String = "calendar-node-1"
    ): Pair<String, Map<String, Any?>> {
        return nodeId to mapOf(
            "type" to NodeTypes.CALENDAR,
            "questionText" to questionText,
            "showTimeSelection" to showTimeSelection,
            "botTimeZone" to timezone,
            "answerVariable" to answerVariable,
            "label" to "Calendar"
        )
    }

    // ==================== FLOW STRUCTURE ====================

    /**
     * Create a simple linear flow with message and ask name nodes
     */
    fun createSimpleFlow(): Pair<List<Map<String, Any>>, List<Map<String, Any>>> {
        val steps = listOf(
            mapOf(
                "id" to "node-1",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Welcome!",
                    "label" to "Welcome"
                )
            ),
            mapOf(
                "id" to "node-2",
                "data" to mapOf(
                    "type" to NodeTypes.ASK_NAME,
                    "questionText" to "What's your name?",
                    "answerVariable" to "name",
                    "label" to "Ask Name"
                )
            ),
            mapOf(
                "id" to "node-3",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Thanks for chatting!",
                    "label" to "Goodbye"
                )
            )
        )

        val edges = listOf(
            mapOf(
                "id" to "edge-1-2",
                "source" to "node-1",
                "target" to "node-2"
            ),
            mapOf(
                "id" to "edge-2-3",
                "source" to "node-2",
                "target" to "node-3"
            )
        )

        return steps to edges
    }

    /**
     * Create a branching flow with multiple paths
     */
    fun createBranchingFlow(): Pair<List<Map<String, Any>>, List<Map<String, Any>>> {
        val steps = listOf(
            mapOf(
                "id" to "start",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Start",
                    "label" to "Start"
                )
            ),
            mapOf(
                "id" to "branch-1",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Branch 1",
                    "label" to "Branch 1"
                )
            ),
            mapOf(
                "id" to "branch-2",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "Branch 2",
                    "label" to "Branch 2"
                )
            ),
            mapOf(
                "id" to "end",
                "data" to mapOf(
                    "type" to NodeTypes.MESSAGE,
                    "text" to "End",
                    "label" to "End"
                )
            )
        )

        val edges = listOf(
            mapOf(
                "id" to "edge-start-branch1",
                "source" to "start",
                "target" to "branch-1",
                "sourceHandle" to "source-0"
            ),
            mapOf(
                "id" to "edge-start-branch2",
                "source" to "start",
                "target" to "branch-2",
                "sourceHandle" to "source-1"
            ),
            mapOf(
                "id" to "edge-branch1-end",
                "source" to "branch-1",
                "target" to "end"
            ),
            mapOf(
                "id" to "edge-branch2-end",
                "source" to "branch-2",
                "target" to "end"
            )
        )

        return steps to edges
    }

    // ==================== VALIDATION TEST DATA ====================

    object ValidationData {
        // Valid emails
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.org",
            "user+tag@example.co.uk",
            "simple@test.io"
        )

        // Invalid emails
        val invalidEmails = listOf(
            "invalid",
            "@nodomain.com",
            "no@domain",
            "spaces in@email.com",
            "",
            "missing@.com"
        )

        // Valid phone numbers
        val validPhones = listOf(
            "+1234567890",
            "1234567890",
            "+44 7911 123456",
            "(123) 456-7890"
        )

        // Invalid phone numbers
        val invalidPhones = listOf(
            "123",
            "abc123",
            "",
            "not-a-phone"
        )

        // Valid URLs
        val validUrls = listOf(
            "https://example.com",
            "http://test.org/path",
            "https://sub.domain.com/page?query=1"
        )

        // Invalid URLs
        val invalidUrls = listOf(
            "not-a-url",
            "",
            "//missing-scheme.com",
            "ftp:/incomplete"
        )

        // Valid numbers
        val validNumbers = listOf(
            "123",
            "45.67",
            "-89",
            "0",
            "3.14159"
        )

        // Invalid numbers
        val invalidNumbers = listOf(
            "abc",
            "",
            "12.34.56",
            "not-a-number"
        )
    }

    // ==================== ANSWER VARIABLE DATA ====================

    object AnswerVariableData {
        val sampleVariables = mapOf(
            "name" to "John Doe",
            "email" to "john@example.com",
            "phone" to "+1234567890",
            "age" to 30,
            "isSubscribed" to true
        )

        val variableReferences = mapOf(
            "{{name}}" to "John Doe",
            "\${email}" to "john@example.com",
            "{{age}}" to 30
        )
    }

    // ==================== TRANSCRIPT DATA ====================

    object TranscriptData {
        val sampleTranscript = listOf(
            Triple("bot", "Hello! How can I help you?", 1000L),
            Triple("user", "I need help with my order", 2000L),
            Triple("bot", "Sure, what's your order number?", 3000L),
            Triple("user", "Order #12345", 4000L),
            Triple("agent", "I'll look into that for you", 5000L)
        )
    }

    // ==================== RECORD DATA ====================

    object RecordData {
        fun createSampleRecordEntry(
            id: String = "record-1",
            shape: String = "bot-message",
            type: String? = NodeTypes.MESSAGE,
            text: String? = "Hello!"
        ): Map<String, Any?> {
            return mapOf(
                "id" to id,
                "shape" to shape,
                "type" to type,
                "text" to text
            )
        }
    }
}
