package com.conferbot.sdk.testutils

/**
 * Test helper for ChatState operations
 * Provides utilities to set up and verify ChatState in tests
 *
 * Note: ChatState is a singleton object, so we need to manage state carefully in tests.
 * This helper provides methods to reset and configure ChatState for isolated tests.
 */
object TestChatState {

    /**
     * Initialize ChatState with test data
     */
    fun initialize(
        chatSessionId: String = TestFixtures.TEST_CHAT_SESSION_ID,
        visitorId: String = TestFixtures.TEST_VISITOR_ID,
        botId: String = TestFixtures.TEST_BOT_ID,
        workspaceId: String? = TestFixtures.TEST_WORKSPACE_ID
    ) {
        com.conferbot.sdk.core.state.ChatState.reset()
        com.conferbot.sdk.core.state.ChatState.initialize(
            chatSessionId = chatSessionId,
            visitorId = visitorId,
            botId = botId,
            workspaceId = workspaceId
        )
    }

    /**
     * Reset ChatState to initial state
     */
    fun reset() {
        com.conferbot.sdk.core.state.ChatState.reset()
    }

    /**
     * Add sample answer variables for testing
     */
    fun addSampleAnswerVariables() {
        TestFixtures.AnswerVariableData.sampleVariables.forEach { (key, value) ->
            com.conferbot.sdk.core.state.ChatState.addAnswerVariable("node-$key", key, value)
        }
    }

    /**
     * Add sample transcript entries for testing
     */
    fun addSampleTranscript() {
        TestFixtures.TranscriptData.sampleTranscript.forEach { (by, message, _) ->
            com.conferbot.sdk.core.state.ChatState.addToTranscript(by, message)
        }
    }

    /**
     * Set sample user metadata for testing
     */
    fun setSampleUserMetadata(
        name: String = "John Doe",
        email: String = "john@example.com",
        phone: String = "+1234567890"
    ) {
        com.conferbot.sdk.core.state.ChatState.setUserMetadata("name", name)
        com.conferbot.sdk.core.state.ChatState.setUserMetadata("email", email)
        com.conferbot.sdk.core.state.ChatState.setUserMetadata("phone", phone)
    }

    /**
     * Set up a flow with steps for testing
     */
    fun setUpFlow(steps: List<Map<String, Any>>) {
        com.conferbot.sdk.core.state.ChatState.setSteps(steps)
    }

    /**
     * Get current answer variable value
     */
    fun getAnswerVariable(key: String): Any? {
        return com.conferbot.sdk.core.state.ChatState.getAnswerVariableValue(key)
    }

    /**
     * Get current user metadata
     */
    fun getUserMetadata(type: String): String? {
        return com.conferbot.sdk.core.state.ChatState.getUserMetadata(type)
    }

    /**
     * Get transcript for GPT
     */
    fun getTranscript(): List<Map<String, String>> {
        return com.conferbot.sdk.core.state.ChatState.getTranscriptForGPT()
    }

    /**
     * Get record entries
     */
    fun getRecord(): List<Map<String, Any?>> {
        return com.conferbot.sdk.core.state.ChatState.getRecordForServer()
    }

    /**
     * Get current node index
     */
    fun getCurrentIndex(): Int {
        return com.conferbot.sdk.core.state.ChatState.currentIndex.value
    }

    /**
     * Get current node
     */
    fun getCurrentNode(): Map<String, Any>? {
        return com.conferbot.sdk.core.state.ChatState.getCurrentNode()
    }

    /**
     * Verify answer variable exists with expected value
     */
    fun verifyAnswerVariable(key: String, expectedValue: Any?): Boolean {
        return com.conferbot.sdk.core.state.ChatState.getAnswerVariableValue(key) == expectedValue
    }

    /**
     * Verify user metadata matches expected value
     */
    fun verifyUserMetadata(type: String, expectedValue: String): Boolean {
        return com.conferbot.sdk.core.state.ChatState.getUserMetadata(type) == expectedValue
    }

    /**
     * Verify transcript contains specific message
     */
    fun verifyTranscriptContains(by: String, message: String): Boolean {
        return com.conferbot.sdk.core.state.ChatState.transcript.value.any {
            it.by == by && it.message == message
        }
    }

    /**
     * Get memory usage info
     */
    fun getMemoryUsageInfo(): com.conferbot.sdk.core.state.MemoryUsageInfo {
        return com.conferbot.sdk.core.state.ChatState.getMemoryUsageInfo()
    }
}
