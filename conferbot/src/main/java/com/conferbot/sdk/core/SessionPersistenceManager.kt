package com.conferbot.sdk.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.conferbot.sdk.core.analytics.ChatAnalytics
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.PaginatedMessageManager
import com.conferbot.sdk.core.state.PaginationConfig
import com.conferbot.sdk.core.state.PaginationState
import com.conferbot.sdk.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for session persistence and restoration
 * Handles saving and restoring chat sessions across app restarts
 */
object SessionPersistenceManager {
    private const val TAG = "SessionPersistence"

    /**
     * Check if there's a valid (non-expired) session for the given bot
     *
     * @param context Application context
     * @param botId The bot ID to check for
     * @return true if a valid session exists
     */
    suspend fun hasValidSession(context: Context, botId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                val session = repository.getLatestValidSession(botId)
                session != null
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for valid session", e)
                false
            }
        }
    }

    /**
     * Restore the latest valid session for a bot
     * This should be called before initializing a new session to check for existing data
     *
     * @param context Application context
     * @param botId The bot ID to restore session for
     * @param paginationConfig Pagination configuration
     * @return SessionRestoreResult indicating success and session details
     */
    suspend fun restoreSession(
        context: Context,
        botId: String,
        paginationConfig: PaginationConfig = PaginationConfig()
    ): SessionRestoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                val restoredSession = repository.restoreLatestSession(botId)

                if (restoredSession == null) {
                    Log.d(TAG, "No valid session found for bot: $botId")
                    return@withContext SessionRestoreResult(
                        success = false,
                        reason = "No valid session found"
                    )
                }

                Log.d(TAG, "Found session to restore: ${restoredSession.session.sessionId}")

                // Restore ChatState with the persisted data
                val restored = ChatState.restoreSession(context, botId)

                if (restored) {
                    return@withContext SessionRestoreResult(
                        success = true,
                        sessionId = restoredSession.session.sessionId,
                        visitorId = restoredSession.session.visitorId,
                        messageCount = restoredSession.messages.size,
                        answerVariableCount = restoredSession.answerVariables.size
                    )
                } else {
                    return@withContext SessionRestoreResult(
                        success = false,
                        reason = "Failed to restore session to ChatState"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring session", e)
                SessionRestoreResult(
                    success = false,
                    reason = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Clear expired sessions for a bot
     * Call this periodically to clean up old data
     *
     * @param context Application context
     * @param expiryMs Session expiry time in milliseconds (default 30 minutes)
     */
    suspend fun clearExpiredSessions(
        context: Context,
        expiryMs: Long = SessionRepository.SESSION_EXPIRY_MS
    ) {
        withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                repository.clearOldSessions(System.currentTimeMillis() - expiryMs)
                Log.d(TAG, "Cleared expired sessions")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing expired sessions", e)
            }
        }
    }

    /**
     * Get session info without restoring
     *
     * @param context Application context
     * @param botId The bot ID to check
     * @return SessionInfo or null if no valid session exists
     */
    suspend fun getSessionInfo(context: Context, botId: String): SessionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                val session = repository.getLatestValidSession(botId)

                session?.let {
                    SessionInfo(
                        sessionId = it.sessionId,
                        visitorId = it.visitorId,
                        botId = it.botId,
                        currentIndex = it.currentIndex,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        isValid = repository.isSessionValid(it)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting session info", e)
                null
            }
        }
    }

    /**
     * Force invalidate a session
     * Use this when the user explicitly ends a chat
     *
     * @param context Application context
     * @param sessionId The session ID to invalidate
     */
    suspend fun invalidateSession(context: Context, sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                repository.deactivateSession(sessionId)
                Log.d(TAG, "Session invalidated: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error invalidating session", e)
            }
        }
    }

    /**
     * Delete a session and all its data
     *
     * @param context Application context
     * @param sessionId The session ID to delete
     */
    suspend fun deleteSession(context: Context, sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val repository = SessionRepository.getInstance(context)
                repository.deleteSession(sessionId)
                Log.d(TAG, "Session deleted: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting session", e)
            }
        }
    }
}

/**
 * Result of session restoration attempt
 */
data class SessionRestoreResult(
    val success: Boolean,
    val sessionId: String? = null,
    val visitorId: String? = null,
    val messageCount: Int = 0,
    val answerVariableCount: Int = 0,
    val reason: String? = null
)

/**
 * Information about a stored session
 */
data class SessionInfo(
    val sessionId: String,
    val visitorId: String,
    val botId: String,
    val currentIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isValid: Boolean
)
