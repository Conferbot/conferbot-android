package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing answer variables collected during conversation
 * These represent user responses mapped to specific node IDs and keys
 */
@Entity(
    tableName = "answer_variables",
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
        Index(value = ["nodeId"]),
        Index(value = ["key"])
    ]
)
data class AnswerVariableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,

    val nodeId: String,

    val key: String,

    /**
     * Value stored as JSON string to support different types
     */
    val value: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
