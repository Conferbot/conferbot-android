package com.conferbot.sdk.services

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Represents a file pending upload
 */
data class PendingFileUpload(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
)

/**
 * Individual file upload state
 */
sealed class FileUploadItemState {
    data object Pending : FileUploadItemState()
    data class Uploading(val progress: Float) : FileUploadItemState()
    data class Completed(val url: String) : FileUploadItemState()
    data class Failed(val error: String) : FileUploadItemState()
    data object Cancelled : FileUploadItemState()
}

/**
 * Upload batch result
 */
data class UploadBatchResult(
    val success: Boolean,
    val uploadedFiles: List<Map<String, Any?>>,
    val failedFiles: List<Map<String, Any?>>,
    val totalFiles: Int,
    val successCount: Int,
    val failCount: Int
)

/**
 * Manager class to handle file uploads for the chat SDK
 * Coordinates between UI and FileUploadService
 */
class FileUploadManager(
    private val context: Context,
    private val uploadService: FileUploadService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current upload states for batch uploads
    private val _fileStates = MutableStateFlow<Map<Uri, FileUploadItemState>>(emptyMap())
    val fileStates: StateFlow<Map<Uri, FileUploadItemState>> = _fileStates.asStateFlow()

    // Overall batch progress
    private val _batchProgress = MutableStateFlow(0f)
    val batchProgress: StateFlow<Float> = _batchProgress.asStateFlow()

    // Current upload job for cancellation
    private var currentUploadJob: Job? = null

    /**
     * Upload a single file
     */
    suspend fun uploadFile(
        uri: Uri,
        chatSessionId: String,
        nodeId: String? = null,
        useSignedUrl: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        uploadService.uploadFile(
            uri = uri,
            chatSessionId = chatSessionId,
            nodeId = nodeId,
            useSignedUrl = useSignedUrl
        ).fold(
            onFirst = { state ->
                when (state) {
                    is UploadState.Uploading -> onProgress(state.progress)
                    is UploadState.Success -> return@withContext state.result
                    is UploadState.Error -> return@withContext UploadResult(
                        success = false,
                        url = null,
                        fileName = "",
                        fileSize = 0,
                        mimeType = "",
                        error = state.message
                    )
                    is UploadState.Cancelled -> return@withContext UploadResult(
                        success = false,
                        url = null,
                        fileName = "",
                        fileSize = 0,
                        mimeType = "",
                        error = "Upload cancelled"
                    )
                    else -> {}
                }
            }
        )

        // Should not reach here, but provide fallback
        UploadResult(
            success = false,
            url = null,
            fileName = "",
            fileSize = 0,
            mimeType = "",
            error = "Unknown upload state"
        )
    }

    /**
     * Upload multiple files sequentially
     * Returns batch result with all successes and failures
     */
    fun uploadFiles(
        files: List<PendingFileUpload>,
        chatSessionId: String,
        nodeId: String? = null,
        useSignedUrl: Boolean = true
    ): Flow<UploadBatchProgress> = flow {
        if (files.isEmpty()) {
            emit(UploadBatchProgress.Completed(
                UploadBatchResult(
                    success = true,
                    uploadedFiles = emptyList(),
                    failedFiles = emptyList(),
                    totalFiles = 0,
                    successCount = 0,
                    failCount = 0
                )
            ))
            return@flow
        }

        val uploadedFiles = mutableListOf<Map<String, Any?>>()
        val failedFiles = mutableListOf<Map<String, Any?>>()

        // Initialize states
        val initialStates = files.associate { it.uri to FileUploadItemState.Pending }
        _fileStates.value = initialStates

        emit(UploadBatchProgress.Started(files.size))

        files.forEachIndexed { index, file ->
            // Update state to uploading
            _fileStates.update { states ->
                states + (file.uri to FileUploadItemState.Uploading(0f))
            }

            emit(UploadBatchProgress.FileStarted(index, file.name))

            // Collect upload progress
            uploadService.uploadFile(
                uri = file.uri,
                chatSessionId = chatSessionId,
                nodeId = nodeId,
                useSignedUrl = useSignedUrl
            ).collect { state ->
                when (state) {
                    is UploadState.Uploading -> {
                        _fileStates.update { states ->
                            states + (file.uri to FileUploadItemState.Uploading(state.progress))
                        }
                        // Calculate overall progress
                        val baseProgress = index.toFloat() / files.size
                        val fileProgress = state.progress / files.size
                        _batchProgress.value = baseProgress + fileProgress

                        emit(UploadBatchProgress.FileProgress(index, file.name, state.progress))
                    }

                    is UploadState.Success -> {
                        _fileStates.update { states ->
                            states + (file.uri to FileUploadItemState.Completed(state.result.url ?: ""))
                        }
                        uploadedFiles.add(mapOf(
                            "uri" to file.uri.toString(),
                            "url" to state.result.url,
                            "fileName" to state.result.fileName,
                            "fileSize" to state.result.fileSize,
                            "mimeType" to state.result.mimeType
                        ))
                        emit(UploadBatchProgress.FileCompleted(index, file.name, state.result))
                    }

                    is UploadState.Error -> {
                        _fileStates.update { states ->
                            states + (file.uri to FileUploadItemState.Failed(state.message))
                        }
                        failedFiles.add(mapOf(
                            "uri" to file.uri.toString(),
                            "fileName" to file.name,
                            "error" to state.message
                        ))
                        emit(UploadBatchProgress.FileFailed(index, file.name, state.message))
                    }

                    is UploadState.Cancelled -> {
                        _fileStates.update { states ->
                            states + (file.uri to FileUploadItemState.Cancelled)
                        }
                        failedFiles.add(mapOf(
                            "uri" to file.uri.toString(),
                            "fileName" to file.name,
                            "error" to "Cancelled"
                        ))
                        emit(UploadBatchProgress.FileCancelled(index, file.name))
                    }

                    else -> {}
                }
            }
        }

        // Batch complete
        _batchProgress.value = 1f

        val result = UploadBatchResult(
            success = failedFiles.isEmpty(),
            uploadedFiles = uploadedFiles,
            failedFiles = failedFiles,
            totalFiles = files.size,
            successCount = uploadedFiles.size,
            failCount = failedFiles.size
        )

        emit(UploadBatchProgress.Completed(result))
    }.flowOn(Dispatchers.IO)

    /**
     * Cancel all current uploads
     */
    fun cancelAll() {
        currentUploadJob?.cancel()
        uploadService.cancelUpload()
        _fileStates.update { states ->
            states.mapValues { (_, state) ->
                if (state is FileUploadItemState.Uploading || state is FileUploadItemState.Pending) {
                    FileUploadItemState.Cancelled
                } else {
                    state
                }
            }
        }
    }

    /**
     * Reset all states
     */
    fun reset() {
        _fileStates.value = emptyMap()
        _batchProgress.value = 0f
    }

    /**
     * Cleanup resources
     */
    fun dispose() {
        scope.cancel()
        reset()
    }
}

/**
 * Progress events for batch upload
 */
sealed class UploadBatchProgress {
    data class Started(val totalFiles: Int) : UploadBatchProgress()
    data class FileStarted(val index: Int, val fileName: String) : UploadBatchProgress()
    data class FileProgress(val index: Int, val fileName: String, val progress: Float) : UploadBatchProgress()
    data class FileCompleted(val index: Int, val fileName: String, val result: UploadResult) : UploadBatchProgress()
    data class FileFailed(val index: Int, val fileName: String, val error: String) : UploadBatchProgress()
    data class FileCancelled(val index: Int, val fileName: String) : UploadBatchProgress()
    data class Completed(val result: UploadBatchResult) : UploadBatchProgress()
}

/**
 * Extension to fold over flow and collect final result
 */
private suspend fun <T> Flow<T>.fold(onFirst: suspend (T) -> Unit): T? {
    var result: T? = null
    collect { value ->
        result = value
        onFirst(value)
    }
    return result
}
