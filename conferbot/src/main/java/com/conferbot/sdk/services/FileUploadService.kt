package com.conferbot.sdk.services

import android.content.Context
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Response from signed URL request
 */
data class SignedUrlResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("signedUrl")
    val signedUrl: String?,
    @SerializedName("uploadUrl")
    val uploadUrl: String?,
    @SerializedName("fileUrl")
    val fileUrl: String?,
    @SerializedName("key")
    val key: String?,
    @SerializedName("error")
    val error: String? = null
)

/**
 * Response from direct upload
 */
data class DirectUploadResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("url")
    val url: String?,
    @SerializedName("fileUrl")
    val fileUrl: String?,
    @SerializedName("fileName")
    val fileName: String?,
    @SerializedName("fileSize")
    val fileSize: Long?,
    @SerializedName("mimeType")
    val mimeType: String?,
    @SerializedName("error")
    val error: String? = null
)

/**
 * Result of file upload operation
 */
data class UploadResult(
    val success: Boolean,
    val url: String?,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val error: String? = null
)

/**
 * Upload progress state
 */
sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Float, val fileName: String) : UploadState()
    data class Success(val result: UploadResult) : UploadState()
    data class Error(val message: String) : UploadState()
    object Cancelled : UploadState()
}

/**
 * Retrofit API interface for file upload endpoints
 */
interface FileUploadApi {
    @POST("file/signed-url")
    suspend fun getSignedUploadUrl(
        @Body body: Map<String, Any?>
    ): Response<ApiResponse<SignedUrlResponse>>

    @Multipart
    @POST("file/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("chatSessionId") chatSessionId: RequestBody,
        @Part("nodeId") nodeId: RequestBody?
    ): Response<ApiResponse<DirectUploadResponse>>
}

/**
 * Progress tracking request body
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: okio.BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val fileInputStream = file.inputStream()
        var uploaded: Long = 0
        val fileLength = file.length()

        fileInputStream.use { inputStream ->
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                val progress = (uploaded.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 4096
    }
}

/**
 * Service for handling file uploads
 * Supports both direct upload and S3 signed URL upload
 */
class FileUploadService(
    private val apiKey: String,
    private val botId: String,
    private val context: Context,
    baseUrl: String = Constants.DEFAULT_API_BASE_URL,
    enableLogging: Boolean = false
) {
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.API_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(120_000, TimeUnit.MILLISECONDS)  // Longer timeout for uploads
        .writeTimeout(120_000, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header(Constants.HEADER_API_KEY, apiKey)
                .header(Constants.HEADER_BOT_ID, botId)
                .header(Constants.HEADER_PLATFORM, Constants.PLATFORM_IDENTIFIER)
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .apply {
            if (enableLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }
        }
        .build()

    // Separate client for S3 uploads (no auth headers)
    private val s3HttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.API_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(120_000, TimeUnit.MILLISECONDS)
        .writeTimeout(120_000, TimeUnit.MILLISECONDS)
        .apply {
            if (enableLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    }
                )
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val api: FileUploadApi = retrofit.create(FileUploadApi::class.java)

    @Volatile
    private var currentUploadCall: Call? = null

    /**
     * Upload a file and return a Flow of upload states
     * Automatically chooses between direct upload and signed URL upload
     */
    fun uploadFile(
        uri: Uri,
        chatSessionId: String,
        nodeId: String? = null,
        useSignedUrl: Boolean = true
    ): Flow<UploadState> = flow {
        emit(UploadState.Idle)

        try {
            // Get file info
            val fileInfo = getFileInfo(uri)
            val mimeType = fileInfo.mimeType
            val fileSize = fileInfo.size

            // FIX 7: Validate MIME type against allowlist
            if (!isAllowedMimeType(mimeType)) {
                Log.w(TAG, "Blocked upload of disallowed MIME type: $mimeType")
                emit(UploadState.Error("File type not allowed: $mimeType"))
                return@flow
            }

            // FIX 7: Sanitize filename
            val fileName = sanitizeFilename(fileInfo.name)

            emit(UploadState.Uploading(0f, fileName))

            // Create temp file from Uri
            val tempFile = createTempFileFromUri(uri, fileName)

            try {
                val result = if (useSignedUrl) {
                    uploadWithSignedUrl(
                        file = tempFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        chatSessionId = chatSessionId,
                        nodeId = nodeId
                    ) { progress ->
                        // Note: Flow collection happens on a different coroutine
                        // Progress updates would need to be handled differently
                    }
                } else {
                    uploadDirect(
                        file = tempFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        chatSessionId = chatSessionId,
                        nodeId = nodeId
                    ) { progress ->
                        // Progress callback
                    }
                }

                if (result.success) {
                    emit(UploadState.Uploading(1f, fileName))
                    emit(UploadState.Success(result))
                } else {
                    emit(UploadState.Error(result.error ?: "Upload failed"))
                }
            } finally {
                // Clean up temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("canceled", ignoreCase = true) == true) {
                emit(UploadState.Cancelled)
            } else {
                emit(UploadState.Error(e.message ?: "Unknown upload error"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Upload file using direct multipart upload
     */
    suspend fun uploadDirect(
        file: File,
        fileName: String,
        mimeType: String,
        chatSessionId: String,
        nodeId: String? = null,
        onProgress: (Float) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = mimeType.toMediaType()
            val progressRequestBody = ProgressRequestBody(file, mediaType, onProgress)

            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                progressRequestBody
            )

            val chatSessionIdPart = chatSessionId.toRequestBody("text/plain".toMediaType())
            val nodeIdPart = nodeId?.toRequestBody("text/plain".toMediaType())

            val response = api.uploadFile(filePart, chatSessionIdPart, nodeIdPart)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val data = body.data
                    UploadResult(
                        success = true,
                        url = data.url ?: data.fileUrl,
                        fileName = data.fileName ?: fileName,
                        fileSize = data.fileSize ?: file.length(),
                        mimeType = data.mimeType ?: mimeType
                    )
                } else {
                    UploadResult(
                        success = false,
                        url = null,
                        fileName = fileName,
                        fileSize = file.length(),
                        mimeType = mimeType,
                        error = body?.error ?: "Upload failed"
                    )
                }
            } else {
                UploadResult(
                    success = false,
                    url = null,
                    fileName = fileName,
                    fileSize = file.length(),
                    mimeType = mimeType,
                    error = "Upload failed: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            UploadResult(
                success = false,
                url = null,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = mimeType,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Get a signed URL for upload
     */
    suspend fun getSignedUploadUrl(
        fileName: String,
        mimeType: String,
        chatSessionId: String,
        nodeId: String? = null
    ): SignedUrlResponse = withContext(Dispatchers.IO) {
        try {
            val body = mutableMapOf<String, Any?>(
                "fileName" to fileName,
                "mimeType" to mimeType,
                "contentType" to mimeType,
                "chatSessionId" to chatSessionId,
                "botId" to botId
            )
            nodeId?.let { body["nodeId"] = it }

            val response = api.getSignedUploadUrl(body)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody?.success == true && responseBody.data != null) {
                    responseBody.data
                } else {
                    SignedUrlResponse(
                        success = false,
                        signedUrl = null,
                        uploadUrl = null,
                        fileUrl = null,
                        key = null,
                        error = responseBody?.error ?: "Failed to get signed URL"
                    )
                }
            } else {
                SignedUrlResponse(
                    success = false,
                    signedUrl = null,
                    uploadUrl = null,
                    fileUrl = null,
                    key = null,
                    error = "Failed to get signed URL: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            SignedUrlResponse(
                success = false,
                signedUrl = null,
                uploadUrl = null,
                fileUrl = null,
                key = null,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Upload file to a signed URL (S3)
     */
    suspend fun uploadToSignedUrl(
        url: String,
        file: File,
        mimeType: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaType = mimeType.toMediaType()
            val progressRequestBody = ProgressRequestBody(file, mediaType, onProgress)

            val request = Request.Builder()
                .url(url)
                .put(progressRequestBody)
                .header("Content-Type", mimeType)
                .build()

            currentUploadCall = s3HttpClient.newCall(request)
            val call = currentUploadCall ?: return@withContext false
            val response = call.execute()
            currentUploadCall = null

            response.isSuccessful
        } catch (e: Exception) {
            currentUploadCall = null
            false
        }
    }

    /**
     * Upload using signed URL (2-step process)
     */
    suspend fun uploadWithSignedUrl(
        file: File,
        fileName: String,
        mimeType: String,
        chatSessionId: String,
        nodeId: String? = null,
        onProgress: (Float) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get signed URL
            val signedUrlResponse = getSignedUploadUrl(fileName, mimeType, chatSessionId, nodeId)

            if (!signedUrlResponse.success) {
                return@withContext UploadResult(
                    success = false,
                    url = null,
                    fileName = fileName,
                    fileSize = file.length(),
                    mimeType = mimeType,
                    error = signedUrlResponse.error ?: "Failed to get upload URL"
                )
            }

            val uploadUrl = signedUrlResponse.signedUrl ?: signedUrlResponse.uploadUrl
            val fileUrl = signedUrlResponse.fileUrl

            if (uploadUrl == null) {
                return@withContext UploadResult(
                    success = false,
                    url = null,
                    fileName = fileName,
                    fileSize = file.length(),
                    mimeType = mimeType,
                    error = "No upload URL received"
                )
            }

            // Step 2: Upload to signed URL
            val uploadSuccess = uploadToSignedUrl(uploadUrl, file, mimeType, onProgress)

            if (uploadSuccess) {
                UploadResult(
                    success = true,
                    url = fileUrl ?: uploadUrl.split("?").first(),
                    fileName = fileName,
                    fileSize = file.length(),
                    mimeType = mimeType
                )
            } else {
                UploadResult(
                    success = false,
                    url = null,
                    fileName = fileName,
                    fileSize = file.length(),
                    mimeType = mimeType,
                    error = "Failed to upload file"
                )
            }
        } catch (e: Exception) {
            UploadResult(
                success = false,
                url = null,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = mimeType,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Cancel ongoing upload
     */
    fun cancelUpload() {
        currentUploadCall?.cancel()
        currentUploadCall = null
    }

    /**
     * Get file information from Uri
     */
    private fun getFileInfo(uri: Uri): FileInfo {
        var name = "unknown"
        var size: Long = 0
        var mimeType = "application/octet-stream"

        // Get mime type
        mimeType = context.contentResolver.getType(uri) ?: run {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

        // Query for file details
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "unknown"
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // Fallback for file:// URIs
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                }
            }
        }

        return FileInfo(name, size, mimeType)
    }

    /**
     * Create a temporary file from a content Uri
     */
    private fun createTempFileFromUri(uri: Uri, fileName: String): File {
        val extension = fileName.substringAfterLast('.', "tmp")
        val tempFile = File.createTempFile("upload_", ".$extension", context.cacheDir)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return tempFile
    }

    /**
     * Internal file info holder
     */
    private data class FileInfo(
        val name: String,
        val size: Long,
        val mimeType: String
    )

    companion object {
        private const val TAG = "FileUploadService"

        /** Allowed MIME type prefixes for upload. */
        private val ALLOWED_MIME_PREFIXES = listOf(
            "image/",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "text/plain",
            "text/csv",
            "audio/",
            "video/"
        )

        /**
         * Validate that the MIME type is in the allowlist.
         * @return true if the MIME type is allowed
         */
        fun isAllowedMimeType(mimeType: String): Boolean {
            return ALLOWED_MIME_PREFIXES.any { prefix ->
                mimeType.startsWith(prefix)
            }
        }

        /**
         * Sanitize a filename by replacing non-safe characters.
         * Keeps only alphanumeric, dot, hyphen, and underscore.
         */
        fun sanitizeFilename(name: String): String {
            // Separate name and extension
            val lastDot = name.lastIndexOf('.')
            val baseName = if (lastDot > 0) name.substring(0, lastDot) else name
            val extension = if (lastDot > 0) name.substring(lastDot) else ""
            // Replace unsafe characters in baseName
            val sanitized = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            // Ensure non-empty
            val safeName = sanitized.ifBlank { "upload" }
            return safeName + extension
        }
    }
}
