package com.conferbot.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.conferbot.sdk.core.nodes.NodeUIState

/**
 * Main composable that renders any NodeUIState
 */
@Composable
fun NodeRenderer(
    uiState: NodeUIState,
    onResponse: (Any) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is NodeUIState.Message -> MessageNode(uiState, modifier)
        is NodeUIState.Image -> ImageNode(uiState, modifier)
        is NodeUIState.Video -> VideoNode(uiState, modifier)
        is NodeUIState.Audio -> AudioNode(uiState, modifier)
        is NodeUIState.File -> FileNode(uiState, modifier)
        is NodeUIState.TextInput -> TextInputNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.FileUpload -> FileUploadNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.SingleChoice -> SingleChoiceNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.MultipleChoice -> MultipleChoiceNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Rating -> RatingNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Dropdown -> DropdownNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Range -> RangeNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Calendar -> CalendarNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.ImageChoice -> ImageChoiceNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Quiz -> QuizNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.MultipleQuestions -> MultipleQuestionsNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.HumanHandover -> HumanHandoverNode(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.PostChatSurvey -> PostChatSurveyView(uiState, onResponse, primaryColor, modifier)
        is NodeUIState.Html -> HtmlNode(uiState, modifier)
        is NodeUIState.Payment -> PaymentNode(uiState, primaryColor, modifier)
        is NodeUIState.Redirect -> RedirectNode(uiState, modifier)
    }
}

// ==================== MESSAGE NODES ====================

@Composable
fun MessageNode(
    state: NodeUIState.Message,
    modifier: Modifier = Modifier
) {
    BotMessageBubble(text = state.text, modifier = modifier)
}

@Composable
fun ImageNode(
    state: NodeUIState.Image,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AsyncImage(
            model = state.url,
            contentDescription = state.caption,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        if (!state.caption.isNullOrEmpty()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun VideoNode(
    state: NodeUIState.Video,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(android.net.Uri.parse(state.url))
                        val mediaController = android.widget.MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            // Scale video to fit width
                            val videoWidth = mp.videoWidth
                            val videoHeight = mp.videoHeight
                            if (videoWidth > 0 && videoHeight > 0) {
                                val viewWidth = width
                                val scaledHeight = (viewWidth.toFloat() / videoWidth * videoHeight).toInt()
                                layoutParams = layoutParams?.apply {
                                    height = scaledHeight
                                } ?: android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    scaledHeight
                                )
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            // Open in external player on error
                            false
                        }
                        start()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        if (!state.caption.isNullOrEmpty()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AudioNode(
    state: NodeUIState.Audio,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableStateOf("0:00") }
    var currentTime by remember { mutableStateOf("0:00") }
    var isPrepared by remember { mutableStateOf(false) }

    val mediaPlayer = remember {
        android.media.MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(state.url)
                prepareAsync()
            } catch (e: Exception) {
                // Handle invalid URL gracefully
            }
        }
    }

    DisposableEffect(state.url) {
        mediaPlayer.setOnPreparedListener { mp ->
            isPrepared = true
            val totalSec = mp.duration / 1000
            duration = String.format("%d:%02d", totalSec / 60, totalSec % 60)
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            progress = 0f
            currentTime = "0:00"
        }

        onDispose {
            mediaPlayer.release()
        }
    }

    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && isPrepared) {
            try {
                val pos = mediaPlayer.currentPosition
                val dur = mediaPlayer.duration
                if (dur > 0) {
                    progress = pos.toFloat() / dur.toFloat()
                    val sec = pos / 1000
                    currentTime = String.format("%d:%02d", sec / 60, sec % 60)
                }
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(250)
        }
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPrepared) {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isPlaying || progress > 0f) "$currentTime / $duration" else duration,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!state.caption.isNullOrEmpty()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun FileNode(
    state: NodeUIState.File,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "File",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download"
            )
        }
    }
}

// ==================== INPUT NODES ====================

@Composable
fun TextInputNode(
    state: NodeUIState.TextInput,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                hasError = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(state.placeholder ?: "Type here...") },
            keyboardOptions = KeyboardOptions(
                keyboardType = when (state.inputType) {
                    NodeUIState.TextInput.InputType.EMAIL -> KeyboardType.Email
                    NodeUIState.TextInput.InputType.PHONE -> KeyboardType.Phone
                    NodeUIState.TextInput.InputType.NUMBER -> KeyboardType.Number
                    NodeUIState.TextInput.InputType.URL -> KeyboardType.Uri
                    else -> KeyboardType.Text
                },
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        onResponse(text)
                    }
                }
            ),
            isError = hasError,
            supportingText = if (hasError && state.errorMessage != null) {
                { Text(state.errorMessage) }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onResponse(text)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Submit")
        }
    }
}

@Composable
fun FileUploadNode(
    state: NodeUIState.FileUpload,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Upload state management
    var uploadState by remember { mutableStateOf<FileUploadState>(FileUploadState.Idle) }
    var selectedFiles by remember { mutableStateOf<List<SelectedFileData>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Calculate max size in bytes
    val maxSizeBytes = state.maxSizeMb.toLong() * 1024 * 1024

    Column(modifier = modifier) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Show error message if any
        AnimatedVisibility(visible = errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { errorMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Show upload progress or file picker based on state
        when (val currentState = uploadState) {
            is FileUploadState.Idle -> {
                // Show selected files if any
                if (selectedFiles.isNotEmpty()) {
                    selectedFiles.forEachIndexed { index, file ->
                        SelectedFileCard(
                            fileName = file.name,
                            fileSize = file.formattedSize,
                            onRemove = {
                                selectedFiles = selectedFiles.toMutableList().apply {
                                    removeAt(index)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // File picker area
                val canSelectMore = state.allowMultiple &&
                        selectedFiles.size < state.maxFiles ||
                        selectedFiles.isEmpty()

                if (canSelectMore) {
                    FilePickerArea(
                        allowedTypes = state.allowedTypes,
                        maxSizeBytes = maxSizeBytes,
                        maxSizeMb = state.maxSizeMb,
                        onFileSelected = { uri ->
                            errorMessage = null
                            val fileInfo = getFileInfoFromUri(context, uri)
                            if (fileInfo != null) {
                                if (state.allowMultiple) {
                                    if (selectedFiles.size < state.maxFiles) {
                                        selectedFiles = selectedFiles + fileInfo
                                    } else {
                                        errorMessage = "Maximum ${state.maxFiles} files allowed"
                                    }
                                } else {
                                    selectedFiles = listOf(fileInfo)
                                }
                            }
                        },
                        onError = { error ->
                            errorMessage = error
                        },
                        primaryColor = primaryColor
                    )
                }

                // Upload button when files are selected
                if (selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // Start upload - the actual upload will be handled by parent
                            uploadState = FileUploadState.Uploading(0f, selectedFiles.first().name)
                            onResponse(
                                mapOf(
                                    "action" to "upload",
                                    "files" to selectedFiles.map { file ->
                                        mapOf(
                                            "uri" to file.uri.toString(),
                                            "name" to file.name,
                                            "size" to file.size,
                                            "mimeType" to file.mimeType
                                        )
                                    },
                                    "nodeId" to state.nodeId,
                                    "answerKey" to state.answerKey
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFiles.size == 1) "Upload File" else "Upload ${selectedFiles.size} Files"
                        )
                    }
                }
            }

            is FileUploadState.Uploading -> {
                FileUploadProgressCard(
                    progress = currentState.progress,
                    fileName = currentState.fileName,
                    onCancel = {
                        uploadState = FileUploadState.Idle
                        onResponse(mapOf("action" to "cancel"))
                    },
                    primaryColor = primaryColor
                )
            }

            is FileUploadState.Success -> {
                UploadSuccessIndicator(
                    fileName = currentState.fileName,
                    fileUrl = currentState.url
                )
            }

            is FileUploadState.Error -> {
                UploadErrorIndicator(
                    message = currentState.message,
                    onRetry = {
                        uploadState = FileUploadState.Idle
                    }
                )
            }
        }
    }
}

/**
 * File upload state for the node
 */
private sealed class FileUploadState {
    data object Idle : FileUploadState()
    data class Uploading(val progress: Float, val fileName: String) : FileUploadState()
    data class Success(val fileName: String, val url: String) : FileUploadState()
    data class Error(val message: String) : FileUploadState()
}

/**
 * Selected file data holder
 */
private data class SelectedFileData(
    val uri: android.net.Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val formattedSize: String
)

/**
 * Get file info from Uri
 */
private fun getFileInfoFromUri(context: android.content.Context, uri: android.net.Uri): SelectedFileData? {
    return try {
        var name = "unknown"
        var size: Long = 0
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)

                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "unknown"
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        val formattedSize = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }

        SelectedFileData(uri, name, size, mimeType, formattedSize)
    } catch (e: Exception) {
        null
    }
}

/**
 * Card showing selected file with remove option
 */
@Composable
private fun SelectedFileCard(
    fileName: String,
    fileSize: String,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove file",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Upload progress card
 */
@Composable
private fun FileUploadProgressCard(
    progress: Float,
    fileName: String,
    onCancel: () -> Unit,
    primaryColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(48.dp),
                        color = primaryColor,
                        trackColor = primaryColor.copy(alpha = 0.2f),
                        strokeWidth = 3.dp
                    )
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = primaryColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uploading... ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel upload",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.2f),
            )
        }
    }
}

/**
 * Success indicator after upload
 */
@Composable
private fun UploadSuccessIndicator(
    fileName: String,
    fileUrl: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upload complete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF388E3C),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Error indicator when upload fails
 */
@Composable
private fun UploadErrorIndicator(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upload failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD32F2F),
                    maxLines = 2
                )
            }

            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}

// ==================== CHOICE NODES ====================

@Composable
fun SingleChoiceNode(
    state: NodeUIState.SingleChoice,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        state.choices.forEach { choice ->
            val isSelected = selectedId == choice.id

            Button(
                onClick = {
                    selectedId = choice.id
                    onResponse(mapOf("id" to choice.id, "text" to choice.text))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                border = if (!isSelected) BorderStroke(1.dp, primaryColor) else null,
                shape = RoundedCornerShape(12.dp),
                enabled = selectedId == null
            ) {
                Text(
                    text = choice.text,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MultipleChoiceNode(
    state: NodeUIState.MultipleChoice,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        state.options.forEach { option ->
            val isSelected = option.id in selectedIds

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isSelected) primaryColor else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        selectedIds = if (isSelected) {
                            selectedIds - option.id
                        } else {
                            selectedIds + option.id
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        selectedIds = if (isSelected) {
                            selectedIds - option.id
                        } else {
                            selectedIds + option.id
                        }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = option.text)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val selectedTexts = state.options
                    .filter { it.id in selectedIds }
                    .map { it.text }
                onResponse(selectedTexts)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(12.dp),
            enabled = selectedIds.isNotEmpty()
        ) {
            Text("Submit")
        }
    }
}

@Composable
fun ImageChoiceNode(
    state: NodeUIState.ImageChoice,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            items(state.images) { image ->
                val isSelected = selectedId == image.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(enabled = selectedId == null) {
                            selectedId = image.id
                            onResponse(
                                mapOf(
                                    "id" to image.id,
                                    "label" to image.label,
                                    "imageUrl" to image.imageUrl
                                )
                            )
                        },
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) BorderStroke(3.dp, primaryColor) else null
                ) {
                    Box {
                        AsyncImage(
                            model = image.imageUrl,
                            contentDescription = image.label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = image.label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== RATING NODES ====================

@Composable
fun RatingNode(
    state: NodeUIState.Rating,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedRating by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (state.ratingType) {
            NodeUIState.Rating.RatingType.STAR -> {
                StarRating(
                    maxRating = state.maxValue,
                    currentRating = selectedRating ?: 0,
                    onRatingChange = { rating ->
                        selectedRating = rating
                        onResponse(rating)
                    },
                    tint = primaryColor
                )
            }

            NodeUIState.Rating.RatingType.SMILEY -> {
                SmileyRating(
                    onRatingChange = { rating ->
                        selectedRating = rating
                        onResponse(rating)
                    },
                    selectedRating = selectedRating
                )
            }

            NodeUIState.Rating.RatingType.NUMBER,
            NodeUIState.Rating.RatingType.OPINION_SCALE -> {
                NumberRating(
                    minValue = state.minValue,
                    maxValue = state.maxValue,
                    selectedValue = selectedRating,
                    onValueSelected = { value ->
                        selectedRating = value
                        onResponse(value)
                    },
                    tint = primaryColor
                )
            }
        }
    }
}

@Composable
private fun StarRating(
    maxRating: Int,
    currentRating: Int,
    onRatingChange: (Int) -> Unit,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        (1..maxRating).forEach { index ->
            IconButton(
                onClick = { onRatingChange(index) },
                enabled = currentRating == 0
            ) {
                Icon(
                    imageVector = if (index <= currentRating) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = "Rating $index",
                    tint = if (index <= currentRating) tint else Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun SmileyRating(
    onRatingChange: (Int) -> Unit,
    selectedRating: Int?
) {
    val smileys = listOf("😢", "😕", "😐", "🙂", "😄")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        smileys.forEachIndexed { index, smiley ->
            val rating = index + 1
            val isSelected = selectedRating == rating

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable(enabled = selectedRating == null) { onRatingChange(rating) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = smiley,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

@Composable
private fun NumberRating(
    minValue: Int,
    maxValue: Int,
    selectedValue: Int?,
    onValueSelected: (Int) -> Unit,
    tint: Color
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(maxValue - minValue + 1) { index ->
            val value = minValue + index
            val isSelected = selectedValue == value

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) tint else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = selectedValue == null) { onValueSelected(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.toString(),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// ==================== OTHER NODES ====================

@Composable
fun DropdownNode(
    state: NodeUIState.Dropdown,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<NodeUIState.Dropdown.Option?>(null) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it && selectedOption == null }
        ) {
            OutlinedTextField(
                value = selectedOption?.text ?: "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                placeholder = { Text("Select an option") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                state.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.text) },
                        onClick = {
                            selectedOption = option
                            expanded = false
                            onResponse(mapOf("id" to option.id, "text" to option.text))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RangeNode(
    state: NodeUIState.Range,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var value by remember {
        mutableFloatStateOf(
            (state.defaultValue ?: ((state.minValue + state.maxValue) / 2)).toFloat()
        )
    }
    var submitted by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = state.minValue.toString())
            Slider(
                value = value,
                onValueChange = { if (!submitted) value = it },
                valueRange = state.minValue.toFloat()..state.maxValue.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
            )
            Text(text = state.maxValue.toString())
        }

        Text(
            text = "Selected: ${value.toInt()}",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                submitted = true
                onResponse(value.toInt())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(12.dp),
            enabled = !submitted
        ) {
            Text("Submit")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarNode(
    state: NodeUIState.Calendar,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedDate by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Date selection
                Text(
                    text = "Select Date",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { if (!submitted) showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !submitted
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedDate.ifEmpty { "Choose a date" },
                        color = if (selectedDate.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }

                // Time selection
                if (state.showTimeSelection) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { if (!submitted) showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !submitted
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedTime.ifEmpty { "Choose a time" },
                            color = if (selectedTime.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                submitted = true
                onResponse(mapOf("date" to selectedDate, "time" to selectedTime))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(12.dp),
            enabled = selectedDate.isNotEmpty() && !submitted
        ) {
            Text("Confirm")
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val calendar = java.util.Calendar.getInstance().apply {
                                timeInMillis = millis
                            }
                            selectedDate = String.format(
                                "%04d-%02d-%02d",
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH) + 1,
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = String.format(
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuizNode(
    state: NodeUIState.Quiz,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        state.options.forEachIndexed { index, option ->
            val isSelected = selectedIndex == index

            Button(
                onClick = {
                    selectedIndex = index
                    onResponse(mapOf("index" to index, "text" to option))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isSelected && index == state.correctAnswerIndex -> Color(0xFF4CAF50)
                        isSelected -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                border = if (!isSelected) BorderStroke(1.dp, primaryColor) else null,
                shape = RoundedCornerShape(12.dp),
                enabled = selectedIndex == null
            ) {
                Text(
                    text = option,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MultipleQuestionsNode(
    state: NodeUIState.MultipleQuestions,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val currentQuestion = state.questions.getOrNull(state.currentIndex)

    if (currentQuestion != null) {
        TextInputNode(
            state = NodeUIState.TextInput(
                questionText = currentQuestion.questionText,
                inputType = when (currentQuestion.answerType.lowercase()) {
                    "email" -> NodeUIState.TextInput.InputType.EMAIL
                    "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
                    "name" -> NodeUIState.TextInput.InputType.NAME
                    else -> NodeUIState.TextInput.InputType.TEXT
                },
                nodeId = state.nodeId,
                answerKey = currentQuestion.answerKey
            ),
            onResponse = onResponse,
            primaryColor = primaryColor,
            modifier = modifier
        )
    }
}

@Composable
fun HumanHandoverNode(
    state: NodeUIState.HumanHandover,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        when (state.state) {
            NodeUIState.HumanHandover.HandoverState.PRE_CHAT_QUESTIONS -> {
                val currentQuestion = state.preChatQuestions?.getOrNull(state.currentQuestionIndex)
                if (currentQuestion != null) {
                    TextInputNode(
                        state = NodeUIState.TextInput(
                            questionText = currentQuestion.questionText,
                            inputType = when (currentQuestion.answerType.lowercase()) {
                                "email" -> NodeUIState.TextInput.InputType.EMAIL
                                "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
                                "name" -> NodeUIState.TextInput.InputType.NAME
                                else -> NodeUIState.TextInput.InputType.TEXT
                            },
                            nodeId = state.nodeId,
                            answerKey = currentQuestion.answerKey
                        ),
                        onResponse = onResponse,
                        primaryColor = primaryColor
                    )
                }
            }

            NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.handoverMessage ?: "Connecting you to an agent...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        if (state.maxWaitTime != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Estimated wait: ${state.maxWaitTime} minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${state.agentName ?: "Agent"} has joined the chat",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "No agents",
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.handoverMessage ?: "No agents available",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY -> {
                val currentQuestion = state.preChatQuestions?.getOrNull(state.currentQuestionIndex)
                if (currentQuestion != null) {
                    TextInputNode(
                        state = NodeUIState.TextInput(
                            questionText = currentQuestion.questionText,
                            inputType = NodeUIState.TextInput.InputType.TEXT,
                            nodeId = state.nodeId,
                            answerKey = currentQuestion.answerKey
                        ),
                        onResponse = onResponse,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun HtmlNode(
    state: NodeUIState.Html,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(false)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false

                    // Wrap HTML content with basic styling
                    val styledHtml = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    margin: 0;
                                    padding: 12px;
                                    font-family: sans-serif;
                                    font-size: 14px;
                                    line-height: 1.5;
                                    color: #1a1a1a;
                                    word-wrap: break-word;
                                }
                                img { max-width: 100%; height: auto; }
                                a { color: #1976D2; }
                            </style>
                        </head>
                        <body>${state.htmlContent}</body>
                        </html>
                    """.trimIndent()

                    loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 400.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun PaymentNode(
    state: NodeUIState.Payment,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Payment",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.amount != null && state.currency != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.currency} ${state.amount}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (!state.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Open payment URL
                    if (state.paymentUrl.isNotEmpty()) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(state.paymentUrl)
                        )
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF635BFF)),
                shape = RoundedCornerShape(8.dp),
                enabled = state.paymentUrl.isNotEmpty()
            ) {
                Text("Pay Now")
            }
        }
    }
}

@Composable
fun RedirectNode(
    state: NodeUIState.Redirect,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(state.url) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(state.url)
        )
        context.startActivity(intent)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Redirect"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Redirecting...")
        }
    }
}

// ==================== HELPER COMPONENTS ====================

@Composable
fun BotMessageBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
