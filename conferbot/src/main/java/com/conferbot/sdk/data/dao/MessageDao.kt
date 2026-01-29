package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conferbot.sdk.data.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for message operations
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND sender = :sender ORDER BY timestamp ASC")
    suspend fun getMessagesBySender(sessionId: String, sender: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND nodeId = :nodeId")
    suspend fun getMessagesByNodeId(sessionId: String, nodeId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("DELETE FROM messages WHERE timestamp < :olderThan")
    suspend fun deleteOldMessages(olderThan: Long)
}
