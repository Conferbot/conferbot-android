package com.conferbot.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

// ==================== ANIMATION CONSTANTS ====================

private const val STAGGER_DELAY_MS = 50
private const val APPEAR_DURATION_MS = 300
private const val FADE_DURATION_MS = 250
private const val SCALE_DURATION_MS = 200
private val PremiumEasing = FastOutSlowInEasing

/**
 * Main composable that renders any NodeUIState
 */
@Composable
fun NodeRenderer(
    uiState: NodeUIState,
    onResponse: (Any) -> Unit,
    primaryColor: Color = ConferbotThemeAmbient.current.colors.primary,
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
    val theme = ConferbotThemeAmbient.current
    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
        SubcomposeAsyncImage(
            model = state.url,
            contentDescription = state.caption,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
            loading = {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        )
        if (!state.caption.isNullOrEmpty()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun VideoNode(
    state: NodeUIState.Video,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        setOnErrorListener { _, _, _ -> false }
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
                color = theme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun AudioNode(
    state: NodeUIState.Audio,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableStateOf("0:00") }
    var currentTime by remember { mutableStateOf("0:00") }
    var isPrepared by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250, easing = LinearEasing),
        label = "audio_progress"
    )

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
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/pause button with subtle background
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = theme.colors.primary.copy(alpha = 0.1f)
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
                            tint = theme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = theme.colors.primary,
                        trackColor = theme.colors.primary.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPlaying || progress > 0f) "$currentTime / $duration" else duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.colors.onSurfaceVariant
                    )
                }
            }
        }

        if (!state.caption.isNullOrEmpty()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun FileNode(
    state: NodeUIState.File,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = theme.colors.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "File",
                        tint = theme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = theme.colors.primary.copy(alpha = 0.08f)
            ) {
                IconButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(state.url)
                            )
                        )
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = theme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Inline text field with embedded send button
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                hasError = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    state.placeholder ?: "Type here...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
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
            trailingIcon = {
                AnimatedVisibility(
                    visible = text.isNotBlank(),
                    enter = fadeIn(tween(FADE_DURATION_MS)) + scaleIn(tween(SCALE_DURATION_MS)),
                    exit = fadeOut(tween(FADE_DURATION_MS)) + scaleOut(tween(SCALE_DURATION_MS))
                ) {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onResponse(text)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Submit",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            isError = hasError,
            supportingText = if (hasError && state.errorMessage != null) {
                { Text(state.errorMessage) }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = primaryColor.copy(alpha = 0.3f),
                cursorColor = primaryColor
            )
        )
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

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Show error message if any
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(tween(FADE_DURATION_MS)) + expandVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing)),
            exit = fadeOut(tween(FADE_DURATION_MS)) + shrinkVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(12.dp)
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
                        modifier = Modifier.size(18.dp)
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
                            modifier = Modifier.size(14.dp)
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
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(FADE_DURATION_MS, delayMillis = index * STAGGER_DELAY_MS)) +
                                    expandVertically(tween(APPEAR_DURATION_MS, delayMillis = index * STAGGER_DELAY_MS, easing = PremiumEasing))
                        ) {
                            SelectedFileCard(
                                fileName = file.name,
                                fileSize = file.formattedSize,
                                onRemove = {
                                    selectedFiles = selectedFiles.toMutableList().apply {
                                        removeAt(index)
                                    }
                                }
                            )
                        }
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
                AnimatedVisibility(
                    visible = selectedFiles.isNotEmpty(),
                    enter = fadeIn(tween(FADE_DURATION_MS)) + expandVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing)),
                    exit = fadeOut(tween(FADE_DURATION_MS)) + shrinkVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing))
                ) {
                    Column {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFiles.size == 1) "Upload File" else "Upload ${selectedFiles.size} Files",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
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
    val theme = ConferbotThemeAmbient.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.colors.botBubble
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = theme.colors.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = theme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove file",
                    tint = theme.colors.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Upload progress card with smooth animated progress
 */
@Composable
private fun FileUploadProgressCard(
    progress: Float,
    fileName: String,
    onCancel: () -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing),
        label = "upload_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    modifier = Modifier.size(44.dp)
                ) {
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier.size(44.dp),
                        color = primaryColor,
                        trackColor = primaryColor.copy(alpha = 0.15f),
                        strokeWidth = 3.dp
                    )
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Uploading... ${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.colors.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel upload",
                        tint = theme.colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.15f),
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
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "success_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
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
                    style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(24.dp)
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
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD32F2F),
                    maxLines = 2
                )
            }

            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ==================== CHOICE NODES ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleChoiceNode(
    state: NodeUIState.SingleChoice,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Web widget: border-radius 0.75rem, shadow 0 1px 3px rgba(0,0,0,0.1)
        // No start padding needed — parent (MessageList) already wraps in Row with BotAvatar
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.choices.forEachIndexed { index, choice ->
                val isSelected = selectedId == choice.id

                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index.toLong() * STAGGER_DELAY_MS)
                    visible = true
                }

                val alpha by animateFloatAsState(
                    targetValue = when {
                        selectedId == null -> 1f
                        isSelected -> 1f
                        else -> 0.35f
                    },
                    animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                    label = "choice_alpha_$index"
                )

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(APPEAR_DURATION_MS, easing = PremiumEasing)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
                            )
                ) {
                    Surface(
                        onClick = {
                            if (selectedId == null) {
                                selectedId = choice.id
                                onResponse(mapOf("id" to choice.id, "text" to choice.text))
                            }
                        },
                        modifier = Modifier.graphicsLayer { this.alpha = alpha },
                        shape = RoundedCornerShape(12.dp), // 0.75rem
                        color = if (isSelected) theme.colors.botBubble
                               else theme.colors.botBubble.copy(alpha = 0.85f),
                        contentColor = theme.colors.botBubbleText,
                        shadowElevation = 1.dp, // subtle shadow matching web widget
                        enabled = selectedId == null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(FADE_DURATION_MS)) +
                                        expandHorizontally(tween(SCALE_DURATION_MS, easing = PremiumEasing)),
                                exit = fadeOut(tween(FADE_DURATION_MS)) +
                                        shrinkHorizontally(tween(SCALE_DURATION_MS))
                            ) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                            Text(
                                text = choice.text,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
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
    val theme = ConferbotThemeAmbient.current
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var submitted by remember { mutableStateOf(false) }

    // Web widget: flex-direction column, gap 8px, max-width 85%
    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(4.dp))
        }

        state.options.forEachIndexed { index, option ->
            val isSelected = option.id in selectedIds

            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index.toLong() * STAGGER_DELAY_MS)
                visible = true
            }

            val borderColor by animateColorAsState(
                targetValue = if (isSelected) primaryColor
                    else theme.colors.outline.copy(alpha = 0.25f),
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "multi_border_$index"
            )

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) primaryColor.copy(alpha = 0.08f)
                    else Color(0xFFFAFAFA),
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "multi_bg_$index"
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(APPEAR_DURATION_MS, easing = PremiumEasing)) +
                        expandVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing))
            ) {
                // Web widget: padding 8px 12px, border-radius 10px, border 1.5px, font 14px
                Card(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !submitted) {
                                selectedIds = if (isSelected)
                                    selectedIds - option.id
                                else
                                    selectedIds + option.id
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (!submitted) {
                                    selectedIds = if (isSelected)
                                        selectedIds - option.id
                                    else
                                        selectedIds + option.id
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = primaryColor),
                            enabled = !submitted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.text,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Compact submit pill
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty() && !submitted,
            enter = fadeIn(tween(FADE_DURATION_MS)) +
                    expandVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing)),
            exit = fadeOut(tween(FADE_DURATION_MS)) +
                    shrinkVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing))
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        submitted = true
                        onResponse(state.options.filter { it.id in selectedIds }.map { it.text })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Submit (${selectedIds.size})", fontSize = 13.sp)
                }
            }
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

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
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
                val isDisabled = selectedId != null && !isSelected

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "img_scale_${image.id}"
                )

                val alpha by animateFloatAsState(
                    targetValue = if (isDisabled) 0.5f else 1f,
                    animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                    label = "img_alpha_${image.id}"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
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
                    border = if (isSelected) BorderStroke(2.5.dp, primaryColor) else null,
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSelected) 4.dp else 1.dp
                    )
                ) {
                    Box {
                        SubcomposeAsyncImage(
                            model = image.imageUrl,
                            contentDescription = image.label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                            }
                        )

                        // Selection checkmark overlay
                        if (isSelected) {
                            val checkScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "check_scale"
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .scale(checkScale)
                                    .clip(CircleShape)
                                    .background(primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Label overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Color.Black.copy(alpha = if (isSelected) 0.7f else 0.5f)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = image.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1
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
            val isActive = index <= currentRating

            // Smooth scale animation on selection
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "star_scale_$index"
            )

            val starColor by animateColorAsState(
                targetValue = if (isActive) tint else Color.Gray.copy(alpha = 0.35f),
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "star_color_$index"
            )

            IconButton(
                onClick = { onRatingChange(index) },
                enabled = currentRating == 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = "Rating $index",
                    tint = starColor,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(scale)
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
    val theme = ConferbotThemeAmbient.current
    val smileys = listOf("😢", "😕", "😐", "🙂", "😄")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        smileys.forEachIndexed { index, smiley ->
            val rating = index + 1
            val isSelected = selectedRating == rating
            val isDisabled = selectedRating != null && !isSelected

            // Bounce animation on selection
            val scale by animateFloatAsState(
                targetValue = when {
                    isSelected -> 1.3f
                    isDisabled -> 0.85f
                    else -> 1f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "smiley_scale_$index"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isDisabled) 0.35f else 1f,
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "smiley_alpha_$index"
            )

            val bgAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.15f else 0f,
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "smiley_bg_$index"
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(theme.colors.primary.copy(alpha = bgAlpha))
                    .clickable(enabled = selectedRating == null) { onRatingChange(rating) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = smiley,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 28.sp
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
    val theme = ConferbotThemeAmbient.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(maxValue - minValue + 1) { index ->
            val value = minValue + index
            val isSelected = selectedValue == value
            val isDisabled = selectedValue != null && !isSelected

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) tint else theme.colors.surfaceVariant,
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "num_bg_$value"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else theme.colors.onSurfaceVariant,
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "num_text_$value"
            )

            val scale by animateFloatAsState(
                targetValue = when {
                    isSelected -> 1.15f
                    isDisabled -> 0.9f
                    else -> 1f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "num_scale_$value"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isDisabled) 0.4f else 1f,
                animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                label = "num_alpha_$value"
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .shadow(if (isSelected) 2.dp else 0.dp, CircleShape)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(enabled = selectedValue == null) { onValueSelected(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.toString(),
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ==================== OTHER NODES ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownNode(
    state: NodeUIState.Dropdown,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<NodeUIState.Dropdown.Option?>(null) }

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
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
                placeholder = {
                    Text(
                        "Select an option",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor.copy(alpha = 0.3f),
                    cursorColor = primaryColor
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                state.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            selectedOption = option
                            expanded = false
                            onResponse(mapOf("id" to option.id, "text" to option.text))
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
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

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Value display chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = primaryColor.copy(alpha = 0.1f),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "${value.toInt()}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.minValue.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ConferbotThemeAmbient.current.colors.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = { if (!submitted) value = it },
                valueRange = state.minValue.toFloat()..state.maxValue.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = primaryColor,
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = primaryColor.copy(alpha = 0.15f)
                )
            )
            Text(
                text = state.maxValue.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ConferbotThemeAmbient.current.colors.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    submitted = true
                    onResponse(value.toInt())
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                enabled = !submitted
            ) {
                Text("Submit", style = MaterialTheme.typography.labelLarge)
            }
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
    val theme = ConferbotThemeAmbient.current
    var selectedDate by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing)
        )
    ) {
        if (!state.questionText.isNullOrEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Date selection
                Text(
                    text = "Select Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { if (!submitted) showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedDate.isNotEmpty()) primaryColor else theme.colors.outline.copy(alpha = 0.4f)
                    ),
                    enabled = !submitted
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedDate.isNotEmpty()) primaryColor else theme.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedDate.ifEmpty { "Choose a date" },
                        color = if (selectedDate.isEmpty())
                            theme.colors.onSurfaceVariant
                        else
                            theme.colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Time selection
                if (state.showTimeSelection) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { if (!submitted) showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedTime.isNotEmpty()) primaryColor else theme.colors.outline.copy(alpha = 0.4f)
                        ),
                        enabled = !submitted
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selectedTime.isNotEmpty()) primaryColor else theme.colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedTime.ifEmpty { "Choose a time" },
                            color = if (selectedTime.isEmpty())
                                theme.colors.onSurfaceVariant
                            else
                                theme.colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    submitted = true
                    onResponse(mapOf("date" to selectedDate, "time" to selectedTime))
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                enabled = selectedDate.isNotEmpty() && !submitted
            ) {
                Text("Confirm", style = MaterialTheme.typography.labelLarge)
            }
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
    val theme = ConferbotThemeAmbient.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        if (state.questionText.isNotEmpty()) {
            BotMessageBubble(text = state.questionText)
            Spacer(modifier = Modifier.height(12.dp))
        }

        state.options.forEachIndexed { index, option ->
            val isSelected = selectedIndex == index
            val isCorrect = index == state.correctAnswerIndex
            val hasAnswered = selectedIndex != null

            // Staggered entrance
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index.toLong() * STAGGER_DELAY_MS)
                visible = true
            }

            // Smooth color transitions for quiz feedback
            val bgColor by animateColorAsState(
                targetValue = when {
                    hasAnswered && isSelected && isCorrect -> Color(0xFF4CAF50)
                    hasAnswered && isSelected && !isCorrect -> Color(0xFFF44336)
                    hasAnswered && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else -> theme.colors.surface
                },
                animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing),
                label = "quiz_bg_$index"
            )

            val contentColor by animateColorAsState(
                targetValue = when {
                    hasAnswered && isSelected -> Color.White
                    hasAnswered && isCorrect -> Color(0xFF2E7D32)
                    else -> theme.colors.onSurface
                },
                animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing),
                label = "quiz_text_$index"
            )

            val borderColor by animateColorAsState(
                targetValue = when {
                    hasAnswered && isCorrect -> Color(0xFF4CAF50)
                    hasAnswered && isSelected && !isCorrect -> Color(0xFFF44336)
                    !hasAnswered -> primaryColor.copy(alpha = 0.4f)
                    else -> theme.colors.outline.copy(alpha = 0.2f)
                },
                animationSpec = tween(APPEAR_DURATION_MS, easing = PremiumEasing),
                label = "quiz_border_$index"
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(APPEAR_DURATION_MS, easing = PremiumEasing)) +
                        expandVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(enabled = selectedIndex == null) {
                            selectedIndex = index
                            onResponse(mapOf("index" to index, "text" to option))
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, borderColor),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSelected) 2.dp else 0.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        // Show check/cross icon after answering
                        AnimatedVisibility(
                            visible = hasAnswered && (isSelected || isCorrect),
                            enter = fadeIn(tween(FADE_DURATION_MS)) + scaleIn(tween(SCALE_DURATION_MS))
                        ) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isSelected) Color.White
                                else if (isCorrect) Color(0xFF4CAF50)
                                else Color(0xFFF44336),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
    val theme = ConferbotThemeAmbient.current
    val currentQuestion = state.questions.getOrNull(state.currentIndex)

    if (currentQuestion != null) {
        Column(modifier = modifier) {
            // Compact progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${state.currentIndex + 1}/${state.questions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                // Progress dots
                state.questions.forEachIndexed { index, _ ->
                    val dotColor by animateColorAsState(
                        targetValue = when {
                            index < state.currentIndex -> primaryColor
                            index == state.currentIndex -> primaryColor
                            else -> theme.colors.outline.copy(alpha = 0.3f)
                        },
                        animationSpec = tween(FADE_DURATION_MS, easing = PremiumEasing),
                        label = "progress_dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(dotColor)
                    )
                }
            }

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
}

@Composable
fun HumanHandoverNode(
    state: NodeUIState.HumanHandover,
    onResponse: (Any) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
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
                // Pulsing animation for waiting state
                val infiniteTransition = rememberInfiniteTransition(label = "waiting_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pulsing indicator ring
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer { alpha = pulseAlpha },
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = primaryColor,
                                strokeWidth = 3.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.handoverMessage ?: "Connecting you to an agent...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        if (state.maxWaitTime != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Estimated wait: ${state.maxWaitTime} minutes",
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.AGENT_CONNECTED -> {
                // Smooth slide-in green banner
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(APPEAR_DURATION_MS)) +
                            slideInVertically(tween(APPEAR_DURATION_MS, easing = PremiumEasing)) { -it }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${state.agentName ?: "Agent"} has joined the chat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.NO_AGENTS_AVAILABLE -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFFF9800).copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "No agents",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.handoverMessage ?: "No agents available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            NodeUIState.HumanHandover.HandoverState.POST_CHAT_SURVEY -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading survey...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = theme.colors.onSurfaceVariant
                        )
                    }
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
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
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

                    val styledHtml = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    margin: 0;
                                    padding: 12px;
                                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                    font-size: 14px;
                                    line-height: 1.6;
                                    color: #1a1a1a;
                                    word-wrap: break-word;
                                }
                                img { max-width: 100%; height: auto; border-radius: 8px; }
                                a { color: #1976D2; text-decoration: none; }
                                p { margin: 0 0 8px 0; }
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
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Payment icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color(0xFF635BFF).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        tint = Color(0xFF635BFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Payment",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.amount != null && state.currency != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.currency} ${state.amount}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (!state.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (state.paymentUrl.isNotEmpty()) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(state.paymentUrl)
                        )
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF635BFF)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                enabled = state.paymentUrl.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Pay Now",
                    style = MaterialTheme.typography.labelLarge
                )
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
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = ConferbotThemeAmbient.current.colors.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Redirect",
                        tint = ConferbotThemeAmbient.current.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Redirecting...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ==================== HELPER COMPONENTS ====================

/**
 * Inline bot message bubble used by node renderers for question prompts.
 * Matches web widget: border-radius 1.15rem 0.8rem 0.8rem 0
 * (all corners rounded except bottom-left — the bot's corner).
 */
@Composable
fun BotMessageBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    // Web widget style: rounded except bottom-left
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 16.dp
    )
    Surface(
        modifier = modifier
            .shadow(elevation = 1.dp, shape = bubbleShape, clip = false),
        shape = bubbleShape,
        color = theme.colors.botBubble,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = theme.colors.botBubbleText,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

/**
 * Shimmer placeholder effect for loading images
 */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = modifier.background(
            theme.colors.surfaceVariant.copy(alpha = alpha)
        )
    )
}
