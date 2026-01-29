package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing user metadata collected during conversation
 * One-to-one relationship with chat session
 */
@Entity(
    tableName = "user_metadata",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"], unique = true)
    ]
)
data class UserMetadataEntity(
    @PrimaryKey
    val sessionId: String,

    val name: String? = null,

    val email: String? = null,

    val phone: String? = null,

    /**
     * Additional custom metadata stored as JSON string
     */
    val customData: String? = null,

    val updatedAt: Long = System.currentTimeMillis()
)
