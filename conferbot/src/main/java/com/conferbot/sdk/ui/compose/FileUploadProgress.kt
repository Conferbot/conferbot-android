package com.conferbot.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.services.UploadState

/**
 * Displays file upload progress with cancel option
 *
 * @param progress Upload progress from 0f to 1f
 * @param fileName Name of the file being uploaded
 * @param onCancel Callback when cancel button is clicked
 * @param primaryColor Primary color for the progress indicator
 * @param modifier Modifier for the composable
 */
@Composable
fun FileUploadProgress(
    progress: Float,
    fileName: String,
    onCancel: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "upload_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
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
                // File icon with circular progress behind it
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    CircularProgressIndicator(
                        progress = animatedProgress,
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

                // File name and progress percentage
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uploading... ${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Cancel button
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

            // Linear progress bar
            LinearProgressIndicator(
                progress = animatedProgress,
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
 * Upload state indicator that shows different UI based on upload state
 */
@Composable
fun FileUploadStateIndicator(
    state: UploadState,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
        label = "upload_state_transition",
        modifier = modifier
    ) { currentState ->
        when (currentState) {
            is UploadState.Idle -> {
                // Nothing to show
            }

            is UploadState.Uploading -> {
                FileUploadProgress(
                    progress = currentState.progress,
                    fileName = currentState.fileName,
                    onCancel = onCancel,
                    primaryColor = primaryColor
                )
            }

            is UploadState.Success -> {
                UploadSuccessCard(
                    fileName = currentState.result.fileName,
                    fileUrl = currentState.result.url ?: ""
                )
            }

            is UploadState.Error -> {
                UploadErrorCard(
                    message = currentState.message,
                    onRetry = onRetry
                )
            }

            is UploadState.Cancelled -> {
                UploadCancelledCard(onRetry = onRetry)
            }
        }
    }
}

/**
 * Success state card after upload completes
 */
@Composable
fun UploadSuccessCard(
    fileName: String,
    fileUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Error state card when upload fails
 */
@Composable
fun UploadErrorCard(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF44336)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onRetry != null) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

/**
 * Cancelled state card
 */
@Composable
fun UploadCancelledCard(
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
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
                    .background(Color(0xFFFFA000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelled",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Upload cancelled",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF8F00),
                modifier = Modifier.weight(1f)
            )

            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = "Try again",
                        color = Color(0xFFFF8F00)
                    )
                }
            }
        }
    }
}

/**
 * Compact inline progress indicator for list items
 */
@Composable
fun InlineUploadProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Multiple file upload progress list
 */
@Composable
fun MultipleFileUploadProgress(
    files: List<Pair<String, UploadState>>,
    onCancelFile: (Int) -> Unit,
    onRetryFile: ((Int) -> Unit)? = null,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        files.forEachIndexed { index, (fileName, state) ->
            FileUploadStateIndicator(
                state = state,
                onCancel = { onCancelFile(index) },
                onRetry = onRetryFile?.let { { it(index) } },
                primaryColor = primaryColor
            )
        }
    }
}
