package com.conferbot.sdk.models

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Chat session model matching embed-server Response schema
 * Note: data classes auto-generate copy() function
 */
data class ChatSession(
    @SerializedName("_id")
    val id: String,

    @SerializedName("chatSessionId")
    val chatSessionId: String,

    @SerializedName("botId")
    val botId: String,

    @SerializedName("visitorId")
    val visitorId: String? = null,

    @SerializedName("record")
    val record: List<RecordItem> = emptyList(),

    @SerializedName("chatDate")
    val chatDate: Date? = null,

    @SerializedName("visitorMeta")
    val visitorMeta: Map<String, Any>? = null,

    @SerializedName("isActive")
    val isActive: Boolean = true
)
