package com.conferbot.sdk.notifications

import java.util.Date

/**
 * Sealed class representing different types of Conferbot notifications.
 * Used to type-safely handle various notification scenarios in the SDK.
 */
sealed class ConferbotNotification {

    /**
     * Unique identifier for this notification instance
     */
    abstract val id: String

    /**
     * Timestamp when the notification was created
     */
    abstract val timestamp: Date

    /**
     * New message notification from bot or agent
     */
    data class NewMessage(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val from: String,
        val content: String,
        val senderType: SenderType = SenderType.BOT,
        val chatSessionId: String? = null,
        val avatarUrl: String? = null
    ) : ConferbotNotification()

    /**
     * Notification when a live agent joins the chat
     */
    data class AgentJoined(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val agentName: String,
        val agentId: String? = null,
        val agentAvatarUrl: String? = null,
        val chatSessionId: String? = null
    ) : ConferbotNotification()

    /**
     * Notification when a live agent leaves the chat
     */
    data class AgentLeft(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val agentName: String,
        val agentId: String? = null,
        val chatSessionId: String? = null
    ) : ConferbotNotification()

    /**
     * Notification when chat session ends
     */
    data class ChatEnded(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val reason: String,
        val chatSessionId: String? = null
    ) : ConferbotNotification()

    /**
     * Notification when user is queued for handover to live agent
     */
    data class HandoverQueued(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val position: Int,
        val estimatedWaitMinutes: Int? = null,
        val chatSessionId: String? = null
    ) : ConferbotNotification()

    /**
     * Notification when handover queue position updates
     */
    data class QueuePositionUpdate(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val newPosition: Int,
        val estimatedWaitMinutes: Int? = null,
        val chatSessionId: String? = null
    ) : ConferbotNotification()

    /**
     * Generic system notification
     */
    data class SystemNotification(
        override val id: String = System.currentTimeMillis().toString(),
        override val timestamp: Date = Date(),
        val title: String,
        val message: String,
        val data: Map<String, String> = emptyMap()
    ) : ConferbotNotification()

    /**
     * Type of message sender
     */
    enum class SenderType {
        BOT,
        AGENT,
        SYSTEM
    }

    companion object {
        /**
         * Parse a notification from push notification data
         */
        fun fromPushData(data: Map<String, String>): ConferbotNotification? {
            val type = data["type"] ?: return null
            val chatSessionId = data["chatSessionId"]

            return when (type) {
                "new_message", "conferbot_message" -> {
                    val senderType = when (data["senderType"]) {
                        "agent" -> SenderType.AGENT
                        "system" -> SenderType.SYSTEM
                        else -> SenderType.BOT
                    }
                    NewMessage(
                        from = data["from"] ?: data["senderName"] ?: "Conferbot",
                        content = data["content"] ?: data["message"] ?: data["body"] ?: "",
                        senderType = senderType,
                        chatSessionId = chatSessionId,
                        avatarUrl = data["avatarUrl"]
                    )
                }
                "agent_joined" -> {
                    AgentJoined(
                        agentName = data["agentName"] ?: "Agent",
                        agentId = data["agentId"],
                        agentAvatarUrl = data["agentAvatarUrl"],
                        chatSessionId = chatSessionId
                    )
                }
                "agent_left" -> {
                    AgentLeft(
                        agentName = data["agentName"] ?: "Agent",
                        agentId = data["agentId"],
                        chatSessionId = chatSessionId
                    )
                }
                "chat_ended" -> {
                    ChatEnded(
                        reason = data["reason"] ?: "Chat session ended",
                        chatSessionId = chatSessionId
                    )
                }
                "handover_queued" -> {
                    HandoverQueued(
                        position = data["position"]?.toIntOrNull() ?: 0,
                        estimatedWaitMinutes = data["estimatedWait"]?.toIntOrNull(),
                        chatSessionId = chatSessionId
                    )
                }
                "queue_position_update" -> {
                    QueuePositionUpdate(
                        newPosition = data["position"]?.toIntOrNull() ?: 0,
                        estimatedWaitMinutes = data["estimatedWait"]?.toIntOrNull(),
                        chatSessionId = chatSessionId
                    )
                }
                else -> {
                    SystemNotification(
                        title = data["title"] ?: "Conferbot",
                        message = data["message"] ?: data["body"] ?: "",
                        data = data
                    )
                }
            }
        }
    }
}
