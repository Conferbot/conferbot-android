package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conferbot.sdk.data.entities.TranscriptEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transcript entry operations
 */
@Dao
interface TranscriptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TranscriptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TranscriptEntity>)

    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTranscript(sessionId: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTranscriptFlow(sessionId: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTranscript(sessionId: String, limit: Int): List<TranscriptEntity>

    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId AND `by` = :by ORDER BY timestamp ASC")
    suspend fun getTranscriptBySender(sessionId: String, by: String): List<TranscriptEntity>

    @Query("SELECT COUNT(*) FROM transcript_entries WHERE sessionId = :sessionId")
    suspend fun getTranscriptCount(sessionId: String): Int

    @Query("DELETE FROM transcript_entries WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("DELETE FROM transcript_entries WHERE timestamp < :olderThan")
    suspend fun deleteOldEntries(olderThan: Long)
}
