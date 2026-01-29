package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conferbot.sdk.data.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat session operations
 */
@Dao
interface ChatSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity)

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId")
    fun getSessionFlow(sessionId: String): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE botId = :botId AND isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveSession(botId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE botId = :botId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSession(botId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE botId = :botId ORDER BY createdAt DESC")
    suspend fun getAllSessions(botId: String): List<ChatSessionEntity>

    @Query("UPDATE chat_sessions SET currentIndex = :index, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateCurrentIndex(sessionId: String, index: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET isActive = :isActive, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateActiveStatus(sessionId: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun touch(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE updatedAt < :olderThan")
    suspend fun deleteOldSessions(olderThan: Long)

    @Query("DELETE FROM chat_sessions WHERE botId = :botId")
    suspend fun deleteAllForBot(botId: String)

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE botId = :botId")
    suspend fun getSessionCount(botId: String): Int
}
