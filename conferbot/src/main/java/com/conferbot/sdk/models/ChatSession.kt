package com.conferbot.sdk.models

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Chat session model matching embed-server Response schema
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
) {
    /**
     * Create a copy of the ChatSession with updated fields
     */
    fun copy(
        id: String = this.id,
        chatSessionId: String = this.chatSessionId,
        botId: String = this.botId,
        visitorId: String? = this.visitorId,
        record: List<RecordItem> = this.record,
        chatDate: Date? = this.chatDate,
        visitorMeta: Map<String, Any>? = this.visitorMeta,
        isActive: Boolean = this.isActive
    ): ChatSession {
        return ChatSession(
            id = id,
            chatSessionId = chatSessionId,
            botId = botId,
            visitorId = visitorId,
            record = record,
            chatDate = chatDate,
            visitorMeta = visitorMeta,
            isActive = isActive
        )
    }
}
