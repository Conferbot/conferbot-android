package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing chat messages
 * Linked to chat session via foreign key
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,

    val sessionId: String,

    val content: String,

    /**
     * Message sender: "bot", "user", "agent", or "system"
     */
    val sender: String,

    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Node ID from chatbot flow (if applicable)
     */
    val nodeId: String? = null,

    /**
     * Node type from chatbot flow (if applicable)
     */
    val nodeType: String? = null,

    /**
     * Additional message metadata as JSON string
     */
    val metadata: String? = null
)
