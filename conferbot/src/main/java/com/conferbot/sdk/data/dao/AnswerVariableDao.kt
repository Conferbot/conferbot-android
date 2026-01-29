package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conferbot.sdk.data.entities.AnswerVariableEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for answer variable operations
 */
@Dao
interface AnswerVariableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variable: AnswerVariableEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variables: List<AnswerVariableEntity>)

    @Query("SELECT * FROM answer_variables WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getVariables(sessionId: String): List<AnswerVariableEntity>

    @Query("SELECT * FROM answer_variables WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getVariablesFlow(sessionId: String): Flow<List<AnswerVariableEntity>>

    @Query("SELECT * FROM answer_variables WHERE sessionId = :sessionId AND nodeId = :nodeId LIMIT 1")
    suspend fun getVariableByNodeId(sessionId: String, nodeId: String): AnswerVariableEntity?

    @Query("SELECT * FROM answer_variables WHERE sessionId = :sessionId AND `key` = :key LIMIT 1")
    suspend fun getVariableByKey(sessionId: String, key: String): AnswerVariableEntity?

    @Query("UPDATE answer_variables SET value = :value WHERE sessionId = :sessionId AND nodeId = :nodeId")
    suspend fun updateValueByNodeId(sessionId: String, nodeId: String, value: String?)

    @Query("UPDATE answer_variables SET value = :value WHERE sessionId = :sessionId AND `key` = :key")
    suspend fun updateValueByKey(sessionId: String, key: String, value: String?)

    @Query("DELETE FROM answer_variables WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM answer_variables WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("DELETE FROM answer_variables WHERE sessionId = :sessionId AND nodeId = :nodeId")
    suspend fun deleteByNodeId(sessionId: String, nodeId: String)

    @Query("SELECT COUNT(*) FROM answer_variables WHERE sessionId = :sessionId")
    suspend fun getVariableCount(sessionId: String): Int
}
