package com.conferbot.sdk.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conferbot.sdk.data.entities.UserMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user metadata operations
 */
@Dao
interface UserMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: UserMetadataEntity)

    @Update
    suspend fun update(metadata: UserMetadataEntity)

    @Query("SELECT * FROM user_metadata WHERE sessionId = :sessionId")
    suspend fun getMetadata(sessionId: String): UserMetadataEntity?

    @Query("SELECT * FROM user_metadata WHERE sessionId = :sessionId")
    fun getMetadataFlow(sessionId: String): Flow<UserMetadataEntity?>

    @Query("UPDATE user_metadata SET name = :name, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateName(sessionId: String, name: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_metadata SET email = :email, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateEmail(sessionId: String, email: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_metadata SET phone = :phone, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updatePhone(sessionId: String, phone: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_metadata SET customData = :customData, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateCustomData(sessionId: String, customData: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_metadata WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)
}
