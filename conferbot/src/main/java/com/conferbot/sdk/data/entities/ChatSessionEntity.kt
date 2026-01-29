package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing chat session information
 * Allows session persistence across app restarts
 */
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["botId"]),
        Index(value = ["createdAt"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: String,

    val visitorId: String,

    val botId: String,

    val workspaceId: String? = null,

    val currentIndex: Int = 0,

    val isActive: Boolean = true,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)
