package com.conferbot.sdk.core.state

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Centralized memory management for the Conferbot SDK.
 * Handles low memory situations, background trimming, and memory monitoring.
 *
 * Usage:
 * 1. Register in your Application class:
 *    ```kotlin
 *    class MyApp : Application(), ComponentCallbacks2 {
 *        override fun onTrimMemory(level: Int) {
 *            super.onTrimMemory(level)
 *            MemoryManager.handleTrimMemory(level)
 *        }
 *
 *        override fun onLowMemory() {
 *            super.onLowMemory()
 *            MemoryManager.handleLowMemory()
 *        }
 *    }
 *    ```
 *
 * 2. Or use Conferbot.handleTrimMemory(level) directly
 */
object MemoryManager {
    private const val TAG = "ConferBotMemory"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Memory state
    private val _memoryState = MutableStateFlow(MemoryState.NORMAL)
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    // Registered cleanup handlers
    private val cleanupHandlers = mutableListOf<MemoryCleanupHandler>()

    // Configuration
    private var config = MemoryConfig()

    // Context for memory info
    private var appContext: Context? = null

    /**
     * Initialize memory manager with application context
     */
    fun initialize(context: Context, memoryConfig: MemoryConfig = MemoryConfig()) {
        appContext = context.applicationContext
        config = memoryConfig
    }

    /**
     * Register a cleanup handler
     */
    fun registerCleanupHandler(handler: MemoryCleanupHandler) {
        if (!cleanupHandlers.contains(handler)) {
            cleanupHandlers.add(handler)
        }
    }

    /**
     * Unregister a cleanup handler
     */
    fun unregisterCleanupHandler(handler: MemoryCleanupHandler) {
        cleanupHandlers.remove(handler)
    }

    /**
     * Handle ComponentCallbacks2.onTrimMemory
     */
    fun handleTrimMemory(level: Int) {
        Log.d(TAG, "onTrimMemory called with level: $level")

        when (level) {
            // UI is hidden, app is still running
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                _memoryState.value = MemoryState.BACKGROUND
                trimForBackground()
            }

            // Running low on memory while app is running
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                _memoryState.value = MemoryState.LOW
                trimForLowMemory()
            }

            // Running critically low while app is running
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                _memoryState.value = MemoryState.CRITICAL
                clearForCriticalMemory()
            }

            // App is in background and system is running low
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                _memoryState.value = MemoryState.BACKGROUND_LOW
                trimForBackground()
            }

            // App is in background, system is moderately low
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                _memoryState.value = MemoryState.LOW
                trimForLowMemory()
            }

            // App is in background and may be killed soon
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                _memoryState.value = MemoryState.CRITICAL
                clearForCriticalMemory()
            }

            // Running at moderate memory level
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                _memoryState.value = MemoryState.MODERATE
                // Light cleanup
                notifyHandlers(MemoryPressureLevel.LIGHT)
            }
        }
    }

    /**
     * Handle low memory callback
     */
    fun handleLowMemory() {
        Log.w(TAG, "onLowMemory called")
        _memoryState.value = MemoryState.CRITICAL
        clearForCriticalMemory()
    }

    /**
     * Trim memory for background state
     */
    private fun trimForBackground() {
        scope.launch {
            Log.d(TAG, "Trimming memory for background")
            notifyHandlers(MemoryPressureLevel.MODERATE)

            // Trim ChatState
            ChatState.trimMemory()
        }
    }

    /**
     * Trim memory for low memory state
     */
    private fun trimForLowMemory() {
        scope.launch {
            Log.w(TAG, "Trimming memory for low memory")
            notifyHandlers(MemoryPressureLevel.HIGH)

            // More aggressive cleanup
            ChatState.clearOldMessages(config.lowMemoryMessageLimit)
        }
    }

    /**
     * Clear memory for critical state
     */
    private fun clearForCriticalMemory() {
        scope.launch {
            Log.w(TAG, "Clearing memory for critical state")
            notifyHandlers(MemoryPressureLevel.CRITICAL)

            // Most aggressive cleanup
            ChatState.onLowMemory()

            // Clear all repositories from memory
            PaginatedMessageManager.clearAll()
            MessageRepository.clearAll()
        }
    }

    /**
     * Notify all registered handlers
     */
    private fun notifyHandlers(level: MemoryPressureLevel) {
        cleanupHandlers.forEach { handler ->
            try {
                handler.onMemoryPressure(level)
            } catch (e: Exception) {
                Log.e(TAG, "Error in cleanup handler", e)
            }
        }
    }

    /**
     * Get current memory info
     */
    fun getMemoryInfo(): MemoryInfo {
        val context = appContext ?: return MemoryInfo()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        return MemoryInfo(
            availableSystemMemory = memoryInfo.availMem,
            totalSystemMemory = memoryInfo.totalMem,
            isLowMemory = memoryInfo.lowMemory,
            lowMemoryThreshold = memoryInfo.threshold,
            appUsedMemory = usedMemory,
            appMaxMemory = maxMemory,
            memoryUsagePercent = ((usedMemory.toDouble() / maxMemory) * 100).toInt()
        )
    }

    /**
     * Check if should preemptively free memory
     */
    fun shouldFreeMemory(): Boolean {
        val info = getMemoryInfo()
        return info.isLowMemory || info.memoryUsagePercent > config.memoryWarningThreshold
    }

    /**
     * Get current ChatState memory usage
     */
    fun getChatMemoryUsage(): MemoryUsageInfo {
        return ChatState.getMemoryUsageInfo()
    }

    /**
     * Force garbage collection (use sparingly)
     */
    fun suggestGarbageCollection() {
        System.gc()
    }

    /**
     * Reset to normal state (call when returning to foreground)
     */
    fun resetToNormal() {
        if (_memoryState.value != MemoryState.CRITICAL) {
            _memoryState.value = MemoryState.NORMAL
        }
    }
}

/**
 * Memory states
 */
enum class MemoryState {
    NORMAL,
    MODERATE,
    BACKGROUND,
    BACKGROUND_LOW,
    LOW,
    CRITICAL
}

/**
 * Memory pressure levels for handlers
 */
enum class MemoryPressureLevel {
    LIGHT,      // Light cleanup - trim caches
    MODERATE,   // Moderate cleanup - reduce memory footprint
    HIGH,       // High pressure - aggressive cleanup
    CRITICAL    // Critical - clear as much as possible
}

/**
 * Interface for memory cleanup handlers
 */
interface MemoryCleanupHandler {
    fun onMemoryPressure(level: MemoryPressureLevel)
}

/**
 * Memory configuration
 */
data class MemoryConfig(
    val memoryWarningThreshold: Int = 75,  // Percentage
    val lowMemoryMessageLimit: Int = 30,
    val criticalMemoryMessageLimit: Int = 10
)

/**
 * System memory information
 */
data class MemoryInfo(
    val availableSystemMemory: Long = 0,
    val totalSystemMemory: Long = 0,
    val isLowMemory: Boolean = false,
    val lowMemoryThreshold: Long = 0,
    val appUsedMemory: Long = 0,
    val appMaxMemory: Long = 0,
    val memoryUsagePercent: Int = 0
) {
    /**
     * Get human readable memory size
     */
    fun formatMemory(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun toString(): String {
        return """
            MemoryInfo:
            - Available System: ${formatMemory(availableSystemMemory)}
            - Total System: ${formatMemory(totalSystemMemory)}
            - Is Low Memory: $isLowMemory
            - App Used: ${formatMemory(appUsedMemory)}
            - App Max: ${formatMemory(appMaxMemory)}
            - Usage: $memoryUsagePercent%
        """.trimIndent()
    }
}

/**
 * Extension function to make ComponentCallbacks2 integration easier
 */
fun ComponentCallbacks2.handleConferbotMemory(level: Int) {
    MemoryManager.handleTrimMemory(level)
}
