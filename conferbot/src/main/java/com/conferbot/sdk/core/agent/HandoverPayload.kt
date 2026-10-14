package com.conferbot.sdk.core.agent

import com.conferbot.sdk.core.state.ChatState

/**
 * The initiate-handover payload, as the web widget sends it
 * (conferbot-widget buildAllMetaData + _handleHumanHandoverNode): used by
 * the human-handover node and by an AI agent bot's silent start.
 */
object HandoverPayload {

    /** Minutes, as the builder wrote them: half a minute is 0.5, not 2 (the default). */
    fun waitMinutesOf(v: Any?): Number = when (v) {
        is Number -> v
        is String -> v.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toInt() else it } ?: 2
        else -> 2
    }

    fun build(nodeData: Map<String, Any?>, extra: Map<String, Any?> = emptyMap()): Map<String, Any?>? {
        val chatSessionId = ChatState.chatSessionId ?: return null
        val botId = ChatState.botId ?: return null
        val workspaceId = ChatState.workspaceId ?: ""
        val botName = ChatState.getVariable("_botName")?.toString() ?: ""
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())
        val device = "Android/${android.os.Build.MODEL}"
        val location = java.util.TimeZone.getDefault().id

        val chatMetaData = mapOf(
            "version" to "v2",
            "workspaceId" to workspaceId,
            "chatSessionId" to chatSessionId,
            "botId" to botId,
            "botName" to botName,
            "chatDate" to now,
            "deviceInfo" to device,
            "location" to location,
            "record" to ChatState.getRecordForServer(),
            "answerVariables" to ChatState.getAnswerVariablesMap(),
            "transcript" to ChatState.getTranscriptForGPT()
        )
        val metaData = mapOf(
            "visitorId" to (ChatState.visitorId ?: chatSessionId),
            "chatDate" to now,
            "deviceInfo" to device,
            "location" to location
        )
        val maxWaitTime = waitMinutesOf(nodeData["maxWaitTime"])
        return mapOf(
            "workspaceId" to workspaceId,
            "chatbotId" to botId,
            "chatbotName" to botName,
            "chatSessionId" to chatSessionId,
            "chatMetaData" to chatMetaData,
            "metaData" to metaData,
            "priority" to (nodeData["priority"]?.toString() ?: "normal"),
            "maxWaitTime" to maxWaitTime,
            "assignmentType" to (nodeData["assignmentType"]?.toString() ?: "auto"),
            "assignmentStrategy" to (nodeData["agentAssignmentStrategy"]?.toString() ?: ""),
            "assignedAgents" to (nodeData["assignedAgents"] ?: emptyList<String>()),
            "assignedAIAgents" to (nodeData["assignedAIAgents"] ?: emptyList<String>())
        ) + extra
    }
}
