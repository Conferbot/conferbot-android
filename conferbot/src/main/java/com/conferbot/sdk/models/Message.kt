package com.conferbot.sdk.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import java.util.Date

/**
 * Message types matching embed-server schema
 */
enum class MessageType(val value: String) {
    USER_MESSAGE("user-message"),
    USER_INPUT_RESPONSE("user-input-response"), // User input response in chatbot flow
    BOT_MESSAGE("bot-message"),
    AGENT_MESSAGE("agent-message"),
    AGENT_MESSAGE_FILE("agent-message-file"),
    AGENT_MESSAGE_AUDIO("agent-message-audio"),
    AGENT_JOINED_MESSAGE("agent-joined-message"),
    AGENT_LEFT_CHAT("agent-left-chat"),
    USER_LIVE_MESSAGE("user-live-message"),
    VISITOR_DISCONNECTED_MESSAGE("visitor-disconnected-message"),
    VISITOR_RECONNECTED_MESSAGE("visitor-reconnected-message"),
    SYSTEM_MESSAGE("system-message");

    companion object {
        fun fromString(value: String): MessageType {
            return values().find { it.value == value } ?: SYSTEM_MESSAGE
        }
    }
}

/**
 * Base record item matching embed-server Response.record structure
 */
sealed class RecordItem {
    abstract val id: String
    abstract val type: MessageType
    abstract val time: Date

    /**
     * User message record
     */
    data class UserMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String,

        @SerializedName("metadata")
        val metadata: Map<String, Any>? = null
    ) : RecordItem() {
        override val type: MessageType = MessageType.USER_MESSAGE
    }

    /**
     * User input response record (for chatbot flow responses)
     */
    data class UserInputResponse(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String,

        @SerializedName("metadata")
        val metadata: Map<String, Any>? = null
    ) : RecordItem() {
        override val type: MessageType = MessageType.USER_INPUT_RESPONSE
    }

    /**
     * Bot message record
     */
    data class BotMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String? = null,

        val nodeData: Map<String, Any>? = null
    ) : RecordItem() {
        override val type: MessageType = MessageType.BOT_MESSAGE
    }

    /**
     * Agent message record
     */
    data class AgentMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String,

        @SerializedName("agentDetails")
        val agentDetails: AgentDetails
    ) : RecordItem() {
        override val type: MessageType = MessageType.AGENT_MESSAGE
    }

    /**
     * Agent file message record
     */
    data class AgentMessageFile(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("file")
        val file: String,

        @SerializedName("agentDetails")
        val agentDetails: AgentDetails? = null
    ) : RecordItem() {
        override val type: MessageType = MessageType.AGENT_MESSAGE_FILE
    }

    /**
     * Agent audio message record
     */
    data class AgentMessageAudio(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("url")
        val url: String,

        @SerializedName("agentDetails")
        val agentDetails: AgentDetails
    ) : RecordItem() {
        override val type: MessageType = MessageType.AGENT_MESSAGE_AUDIO
    }

    /**
     * Agent joined message record
     */
    data class AgentJoinedMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("agentDetails")
        val agentDetails: AgentDetails
    ) : RecordItem() {
        override val type: MessageType = MessageType.AGENT_JOINED_MESSAGE
    }

    /**
     * Agent left chat message record
     */
    data class AgentLeftMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String,

        @SerializedName("agentDetails")
        val agentDetails: AgentDetails? = null
    ) : RecordItem() {
        override val type: MessageType = MessageType.AGENT_LEFT_CHAT
    }

    /**
     * User live chat message record (sent during agent handover)
     */
    data class UserLiveMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String
    ) : RecordItem() {
        override val type: MessageType = MessageType.USER_LIVE_MESSAGE
    }

    /**
     * System message record
     */
    data class SystemMessage(
        @SerializedName("_id")
        override val id: String,

        @SerializedName("time")
        override val time: Date,

        @SerializedName("text")
        val text: String
    ) : RecordItem() {
        override val type: MessageType = MessageType.SYSTEM_MESSAGE
    }
}

/**
 * Custom deserializer for RecordItem to handle polymorphic deserialization
 */
class RecordItemDeserializer : JsonDeserializer<RecordItem> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): RecordItem {
        val jsonObject = json.asJsonObject
        val typeString = jsonObject.get("type").asString
        val type = MessageType.fromString(typeString)

        return when (type) {
            MessageType.USER_MESSAGE -> context.deserialize(jsonObject, RecordItem.UserMessage::class.java)
            MessageType.USER_INPUT_RESPONSE -> context.deserialize(jsonObject, RecordItem.UserInputResponse::class.java)
            MessageType.BOT_MESSAGE -> context.deserialize(jsonObject, RecordItem.BotMessage::class.java)
            MessageType.AGENT_MESSAGE -> context.deserialize(jsonObject, RecordItem.AgentMessage::class.java)
            MessageType.AGENT_MESSAGE_FILE -> context.deserialize(jsonObject, RecordItem.AgentMessageFile::class.java)
            MessageType.AGENT_MESSAGE_AUDIO -> context.deserialize(jsonObject, RecordItem.AgentMessageAudio::class.java)
            MessageType.AGENT_JOINED_MESSAGE -> context.deserialize(jsonObject, RecordItem.AgentJoinedMessage::class.java)
            MessageType.AGENT_LEFT_CHAT -> context.deserialize(jsonObject, RecordItem.AgentLeftMessage::class.java)
            MessageType.USER_LIVE_MESSAGE -> context.deserialize(jsonObject, RecordItem.UserLiveMessage::class.java)
            else -> context.deserialize(jsonObject, RecordItem.SystemMessage::class.java)
        }
    }
}
