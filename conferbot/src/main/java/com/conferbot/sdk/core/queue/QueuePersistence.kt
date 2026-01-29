package com.conferbot.sdk.core.queue

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the offline message queue to SharedPreferences.
 * Ensures messages survive app restarts.
 */
class QueuePersistence(context: Context) {

    companion object {
        private const val TAG = "QueuePersistence"
        private const val PREFS_NAME = "conferbot_offline_queue"
        private const val KEY_QUEUED_MESSAGES = "queued_messages"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_QUEUE_VERSION = "queue_version"
        private const val CURRENT_VERSION = 1

        // Max age for persisted messages (24 hours)
        private const val MAX_MESSAGE_AGE_MS = 24 * 60 * 60 * 1000L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Save messages to persistent storage
     */
    fun saveMessages(messages: List<QueuedMessage>) {
        try {
            val json = gson.toJson(messages.map { it.toSerializable() })
            prefs.edit()
                .putString(KEY_QUEUED_MESSAGES, json)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .putInt(KEY_QUEUE_VERSION, CURRENT_VERSION)
                .apply()
            Log.d(TAG, "Saved ${messages.size} messages to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages", e)
        }
    }

    /**
     * Load messages from persistent storage
     */
    fun loadMessages(): List<QueuedMessage> {
        return try {
            val json = prefs.getString(KEY_QUEUED_MESSAGES, null)
            if (json.isNullOrEmpty()) {
                Log.d(TAG, "No saved messages found")
                return emptyList()
            }

            val version = prefs.getInt(KEY_QUEUE_VERSION, 0)
            if (version != CURRENT_VERSION) {
                Log.w(TAG, "Queue version mismatch, clearing old data")
                clear()
                return emptyList()
            }

            val type = object : TypeToken<List<SerializableMessage>>() {}.type
            val serializableList: List<SerializableMessage> = gson.fromJson(json, type)

            val messages = serializableList
                .mapNotNull { it.toQueuedMessage() }
                .filter { !isMessageExpired(it) }

            Log.d(TAG, "Loaded ${messages.size} messages from storage")
            messages
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
            emptyList()
        }
    }

    /**
     * Clear all persisted messages
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_QUEUED_MESSAGES)
            .remove(KEY_LAST_SYNC_TIME)
            .apply()
        Log.d(TAG, "Cleared all persisted messages")
    }

    /**
     * Get the last sync time
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }

    /**
     * Check if a message is too old (expired)
     */
    private fun isMessageExpired(message: QueuedMessage): Boolean {
        val age = System.currentTimeMillis() - message.timestamp
        return age > MAX_MESSAGE_AGE_MS
    }

    /**
     * Remove messages for a specific session
     */
    fun removeMessagesForSession(chatSessionId: String) {
        val messages = loadMessages()
        val filtered = messages.filter { it.chatSessionId != chatSessionId }
        saveMessages(filtered)
        Log.d(TAG, "Removed messages for session: $chatSessionId")
    }

    /**
     * Get count of persisted messages
     */
    fun getMessageCount(): Int {
        return loadMessages().size
    }
}

/**
 * Serializable version of QueuedMessage for JSON storage.
 * Maps are stored as JSON strings to avoid Gson type issues.
 */
private data class SerializableMessage(
    val id: String,
    val type: String,
    val payloadJson: String,
    val timestamp: Long,
    val retryCount: Int,
    val chatSessionId: String
) {
    fun toQueuedMessage(): QueuedMessage? {
        return try {
            val gson = Gson()
            val payloadType = object : TypeToken<Map<String, Any>>() {}.type
            val payload: Map<String, Any> = gson.fromJson(payloadJson, payloadType)

            QueuedMessage(
                id = id,
                type = MessageType.valueOf(type),
                payload = payload,
                timestamp = timestamp,
                retryCount = retryCount,
                chatSessionId = chatSessionId
            )
        } catch (e: Exception) {
            Log.e("QueuePersistence", "Failed to deserialize message", e)
            null
        }
    }
}

/**
 * Extension function to convert QueuedMessage to serializable format
 */
private fun QueuedMessage.toSerializable(): SerializableMessage {
    val gson = Gson()
    return SerializableMessage(
        id = id,
        type = type.name,
        payloadJson = gson.toJson(payload),
        timestamp = timestamp,
        retryCount = retryCount,
        chatSessionId = chatSessionId
    )
}
