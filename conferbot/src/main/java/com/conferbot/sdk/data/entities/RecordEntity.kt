package com.conferbot.sdk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing record entries
 * These are full conversation records for server sync
 */
@Entity(
    tableName = "record_entries",
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
        Index(value = ["recordId"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,

    /**
     * Record ID (from server or generated locally)
     */
    val recordId: String,

    val shape: String,

    val type: String? = null,

    val text: String? = null,

    val time: String,

    /**
     * Additional data stored as JSON string
     */
    val data: String? = null
)
