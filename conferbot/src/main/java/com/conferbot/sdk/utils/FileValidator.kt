package com.conferbot.sdk.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Result of file validation
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()

    data class Invalid(
        val errorCode: ErrorCode,
        val message: String
    ) : ValidationResult()

    enum class ErrorCode {
        FILE_TOO_LARGE,
        INVALID_TYPE,
        FILE_NOT_FOUND,
        FILE_EMPTY,
        UNKNOWN_ERROR
    }
}

/**
 * File information extracted from Uri
 */
data class FileInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val extension: String
)

/**
 * Utility object for validating files before upload
 */
object FileValidator {

    /**
     * Validate a file against allowed types and size constraints
     *
     * @param context Android context for content resolver
     * @param uri The file Uri to validate
     * @param allowedTypes List of allowed MIME types (e.g., ["image/*", "application/pdf"])
     *                     Supports wildcards like "image/*" and "*/*"
     * @param maxSizeBytes Maximum allowed file size in bytes
     * @return ValidationResult indicating if file is valid or the error
     */
    fun validateFile(
        context: Context,
        uri: Uri,
        allowedTypes: List<String>? = null,
        maxSizeBytes: Long
    ): ValidationResult {
        try {
            // Get file info
            val fileInfo = getFileInfo(context, uri)
                ?: return ValidationResult.Invalid(
                    ValidationResult.ErrorCode.FILE_NOT_FOUND,
                    "Could not read file information"
                )

            // Check if file is empty
            if (fileInfo.size == 0L) {
                return ValidationResult.Invalid(
                    ValidationResult.ErrorCode.FILE_EMPTY,
                    "File is empty"
                )
            }

            // Check file size
            if (fileInfo.size > maxSizeBytes) {
                val maxSizeMb = maxSizeBytes / (1024 * 1024)
                val fileSizeMb = fileInfo.size / (1024 * 1024)
                return ValidationResult.Invalid(
                    ValidationResult.ErrorCode.FILE_TOO_LARGE,
                    "File is too large (${fileSizeMb}MB). Maximum allowed size is ${maxSizeMb}MB"
                )
            }

            // Check file type if restrictions are specified
            if (!allowedTypes.isNullOrEmpty()) {
                if (!isTypeAllowed(fileInfo.mimeType, allowedTypes)) {
                    return ValidationResult.Invalid(
                        ValidationResult.ErrorCode.INVALID_TYPE,
                        "File type '${fileInfo.mimeType}' is not allowed. Allowed types: ${formatAllowedTypes(allowedTypes)}"
                    )
                }
            }

            return ValidationResult.Valid

        } catch (e: Exception) {
            return ValidationResult.Invalid(
                ValidationResult.ErrorCode.UNKNOWN_ERROR,
                e.message ?: "Unknown error validating file"
            )
        }
    }

    /**
     * Get file information from a Uri
     *
     * @param context Android context
     * @param uri The file Uri
     * @return FileInfo or null if unable to read
     */
    fun getFileInfo(context: Context, uri: Uri): FileInfo? {
        try {
            var name = "unknown"
            var size: Long = 0

            // Get mime type
            val mimeType = getMimeType(context, uri)

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

            // Get file extension
            val extension = name.substringAfterLast('.', "")

            return FileInfo(
                uri = uri,
                name = name,
                size = size,
                mimeType = mimeType,
                extension = extension
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Get the MIME type of a file from Uri
     *
     * @param context Android context
     * @param uri The file Uri
     * @return The MIME type string
     */
    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: run {
            // Fallback: try to get from file extension
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase())
                ?: "application/octet-stream"
        }
    }

    /**
     * Get file size from Uri
     *
     * @param context Android context
     * @param uri The file Uri
     * @return File size in bytes, or -1 if unable to determine
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }

            // Fallback for file:// URIs
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        return file.length()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return -1
    }

    /**
     * Get file name from Uri
     *
     * @param context Android context
     * @param uri The file Uri
     * @return The file name
     */
    fun getFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex) ?: "unknown"
                    }
                }
            }

            // Fallback for file:// URIs
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    return File(path).name
                }
            }

            // Last resort: use last path segment
            return uri.lastPathSegment ?: "unknown"
        } catch (e: Exception) {
            return "unknown"
        }
    }

    /**
     * Format file size to human readable string
     *
     * @param bytes File size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Check if a MIME type matches any of the allowed patterns
     */
    private fun isTypeAllowed(mimeType: String, allowedTypes: List<String>): Boolean {
        val normalizedMimeType = mimeType.lowercase()

        return allowedTypes.any { allowedType ->
            val normalizedAllowed = allowedType.lowercase().trim()
            when {
                // Accept all types
                normalizedAllowed == "*/*" -> true

                // Wildcard match (e.g., "image/*")
                normalizedAllowed.endsWith("/*") -> {
                    val prefix = normalizedAllowed.removeSuffix("/*")
                    normalizedMimeType.startsWith("$prefix/")
                }

                // Exact match
                else -> normalizedMimeType == normalizedAllowed
            }
        }
    }

    /**
     * Format allowed types list for display
     */
    private fun formatAllowedTypes(types: List<String>): String {
        return types.joinToString(", ") { type ->
            when {
                type == "*/*" -> "All files"
                type.endsWith("/*") -> {
                    val category = type.removeSuffix("/*")
                    "${category.replaceFirstChar { it.uppercase() }} files"
                }
                else -> type.substringAfterLast('/')
            }
        }
    }

    /**
     * Get common MIME type patterns
     */
    object MimeTypes {
        val IMAGES = listOf("image/*")
        val DOCUMENTS = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
        )
        val VIDEOS = listOf("video/*")
        val AUDIO = listOf("audio/*")
        val ALL = listOf("*/*")

        /**
         * Get MIME types for common file extensions
         */
        fun fromExtensions(extensions: List<String>): List<String> {
            return extensions.mapNotNull { ext ->
                val cleanExt = ext.removePrefix(".").lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt)
            }
        }
    }
}
