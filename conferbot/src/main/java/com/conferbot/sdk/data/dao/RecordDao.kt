package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conferbot.sdk.data.entities.RecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for record entry operations
 */
@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<RecordEntity>)

    @Query("SELECT * FROM record_entries WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getRecords(sessionId: String): List<RecordEntity>

    @Query("SELECT * FROM record_entries WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getRecordsFlow(sessionId: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM record_entries WHERE sessionId = :sessionId AND recordId = :recordId LIMIT 1")
    suspend fun getRecordById(sessionId: String, recordId: String): RecordEntity?

    @Query("UPDATE record_entries SET data = :data WHERE sessionId = :sessionId AND recordId = :recordId")
    suspend fun updateData(sessionId: String, recordId: String, data: String?)

    @Query("DELETE FROM record_entries WHERE sessionId = :sessionId AND recordId = :recordId")
    suspend fun delete(sessionId: String, recordId: String)

    @Query("DELETE FROM record_entries WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM record_entries WHERE sessionId = :sessionId")
    suspend fun getRecordCount(sessionId: String): Int
}
