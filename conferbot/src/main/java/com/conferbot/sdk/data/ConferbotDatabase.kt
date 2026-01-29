package com.conferbot.sdk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.conferbot.sdk.data.dao.AnswerVariableDao
import com.conferbot.sdk.data.dao.ChatSessionDao
import com.conferbot.sdk.data.dao.MessageDao
import com.conferbot.sdk.data.dao.RecordDao
import com.conferbot.sdk.data.dao.TranscriptDao
import com.conferbot.sdk.data.dao.UserMetadataDao
import com.conferbot.sdk.data.entities.AnswerVariableEntity
import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.entities.MessageEntity
import com.conferbot.sdk.data.entities.RecordEntity
import com.conferbot.sdk.data.entities.TranscriptEntity
import com.conferbot.sdk.data.entities.UserMetadataEntity

/**
 * Room database for Conferbot SDK
 * Stores all session data for persistence across app restarts
 */
@Database(
    entities = [
        ChatSessionEntity::class,
        MessageEntity::class,
        AnswerVariableEntity::class,
        UserMetadataEntity::class,
        TranscriptEntity::class,
        RecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ConferbotDatabase : RoomDatabase() {

    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun messageDao(): MessageDao
    abstract fun answerVariableDao(): AnswerVariableDao
    abstract fun userMetadataDao(): UserMetadataDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun recordDao(): RecordDao

    companion object {
        private const val DATABASE_NAME = "conferbot_database"

        @Volatile
        private var INSTANCE: ConferbotDatabase? = null

        /**
         * Get the singleton database instance
         */
        fun getInstance(context: Context): ConferbotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConferbotDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clear the database instance (useful for testing)
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
