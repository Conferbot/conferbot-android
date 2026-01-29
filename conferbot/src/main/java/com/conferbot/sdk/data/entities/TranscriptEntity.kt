package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing transcript entries
 * These are conversation history entries for GPT context
 */
@Entity(
    tableName = "transcript_entries",
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
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,

    /**
     * Who sent the message: "bot", "user", or "agent"
     */
    val by: String,

    val message: String,

    val timestamp: Long = System.currentTimeMillis()
)
