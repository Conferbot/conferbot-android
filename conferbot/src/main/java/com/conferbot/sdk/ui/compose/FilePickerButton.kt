package com.conferbot.sdk.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.utils.FileValidator
import com.conferbot.sdk.utils.ValidationResult

/**
 * Selected file information to display in UI
 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val formattedSize: String
)

/**
 * A composable button that launches the system file picker
 *
 * @param allowedTypes List of allowed MIME types (e.g., listOf("image/star", "application/pdf"))
 * @param maxSizeBytes Maximum file size in bytes
 * @param onFileSelected Callback when a valid file is selected
 * @param onError Callback when an error occurs (invalid file)
 * @param primaryColor The primary color for styling
 * @param enabled Whether the button is enabled
 * @param allowMultiple Whether to allow multiple file selection
 * @param modifier Modifier for the composable
 */
@Composable
fun FilePickerButton(
    allowedTypes: List<String>?,
    maxSizeBytes: Long,
    onFileSelected: (Uri) -> Unit,
    onError: (String) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    allowMultiple: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Single file picker
    val singleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might not be persistable, continue anyway
            }

            // Validate the file
            val validationResult = FileValidator.validateFile(
                context = context,
                uri = it,
                allowedTypes = allowedTypes,
                maxSizeBytes = maxSizeBytes
            )

            when (validationResult) {
                is ValidationResult.Valid -> onFileSelected(it)
                is ValidationResult.Invalid -> onError(validationResult.message)
            }
        }
    }

    // Multiple file picker
    val multipleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might not be persistable, continue anyway
            }

            val validationResult = FileValidator.validateFile(
                context = context,
                uri = uri,
                allowedTypes = allowedTypes,
                maxSizeBytes = maxSizeBytes
            )

            when (validationResult) {
                is ValidationResult.Valid -> onFileSelected(uri)
                is ValidationResult.Invalid -> onError(validationResult.message)
            }
        }
    }

    // Convert allowed types to MIME type array for the picker
    val mimeTypes = allowedTypes?.toTypedArray() ?: arrayOf("*" + "/" + "*")

    OutlinedButton(
        onClick = {
            if (allowMultiple) {
                multipleFilePicker.launch(mimeTypes)
            } else {
                singleFilePicker.launch(mimeTypes)
            }
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) primaryColor else Color.Gray),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor
        )
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = "Select file",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (allowMultiple) "Select Files" else "Select File"
        )
    }
}

/**
 * A larger, more prominent file picker area
 *
 * @param allowedTypes List of allowed MIME types
 * @param maxSizeBytes Maximum file size in bytes
 * @param maxSizeMb Maximum file size in megabytes (for display)
 * @param onFileSelected Callback when a valid file is selected
 * @param onError Callback when an error occurs
 * @param primaryColor Primary color for styling
 * @param enabled Whether the picker is enabled
 * @param modifier Modifier for the composable
 */
@Composable
fun FilePickerArea(
    allowedTypes: List<String>?,
    maxSizeBytes: Long,
    maxSizeMb: Int,
    onFileSelected: (Uri) -> Unit,
    onError: (String) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Continue anyway
            }

            val validationResult = FileValidator.validateFile(
                context = context,
                uri = it,
                allowedTypes = allowedTypes,
                maxSizeBytes = maxSizeBytes
            )

            when (validationResult) {
                is ValidationResult.Valid -> onFileSelected(it)
                is ValidationResult.Invalid -> onError(validationResult.message)
            }
        }
    }

    val mimeTypes = allowedTypes?.toTypedArray() ?: arrayOf("*" + "/" + "*")

    Card(
        onClick = { if (enabled) filePicker.launch(mimeTypes) },
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            2.dp,
            if (enabled) primaryColor.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                primaryColor.copy(alpha = 0.05f)
            } else {
                Color.Gray.copy(alpha = 0.05f)
            }
        ),
        enabled = enabled
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Upload",
                    modifier = Modifier.size(40.dp),
                    tint = if (enabled) primaryColor else Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to upload file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Text(
                    text = "Max ${maxSizeMb}MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!allowedTypes.isNullOrEmpty() && allowedTypes.first() != "*" + "/" + "*") {
                    Text(
                        text = formatAllowedTypesForDisplay(allowedTypes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Display a selected file with option to remove
 */
@Composable
fun SelectedFileChip(
    file: SelectedFile,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = file.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Folder, // Using folder as close icon placeholder
                    contentDescription = "Remove file",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Format allowed types for display to user
 */
private fun formatAllowedTypesForDisplay(types: List<String>): String {
    val formatted = types.take(3).mapNotNull { type ->
        when {
            type == "*" + "/" + "*" -> null
            type.endsWith("/" + "*") -> type.removeSuffix("/" + "*").replaceFirstChar { it.uppercase() }
            else -> type.substringAfterLast('/').uppercase()
        }
    }

    return when {
        formatted.isEmpty() -> ""
        formatted.size == 1 -> formatted.first()
        types.size > 3 -> "${formatted.joinToString(", ")} +"
        else -> formatted.joinToString(", ")
    }
}
