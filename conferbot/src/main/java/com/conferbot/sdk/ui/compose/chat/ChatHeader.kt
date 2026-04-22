package com.conferbot.sdk.ui.compose.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.ServerChatbotCustomization
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.launch

/**
 * Compact chat header matching the web widget style.
 *
 * Height ~56dp. Shows: back-less avatar + title + tagline on left,
 * close + overflow on right.
 */
@Composable
fun ChatHeader(
    title: String,
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    isConnected: Boolean,
    isOnline: Boolean = true,
    pendingMessageCount: Int = 0,
    isSyncing: Boolean = false,
    serverCustomization: ServerChatbotCustomization? = null,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val typography = theme.typography
    val spacing = theme.spacing

    var showMenu by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.headerBackground,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (28dp — compact)
            val avatarUrl = serverCustomization?.avatarUrl
                ?: serverCustomization?.logoUrl
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Bot avatar",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.onPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "B",
                        style = TextStyle(
                            fontFamily = typography.fontFamily,
                            fontSize = 13.sp,
                            fontWeight = typography.headerWeight
                        ),
                        color = colors.headerText
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Title + subtitle (takes remaining space)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = 16.sp,
                        fontWeight = typography.headerWeight
                    ),
                    color = colors.headerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Subtitle: status or tagline
                val subtitleText = when {
                    !isOnline -> if (pendingMessageCount > 0)
                        "Offline \u00b7 $pendingMessageCount pending" else "Offline"
                    isSyncing -> "Syncing..."
                    !isConnected -> "Reconnecting..."
                    serverCustomization?.enableTagline == true &&
                            !serverCustomization.tagline.isNullOrBlank() ->
                        serverCustomization.tagline
                    else -> null
                }
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        style = TextStyle(
                            fontFamily = typography.fontFamily,
                            fontSize = 11.sp
                        ),
                        color = colors.headerText.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = colors.headerIcon,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Overflow menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = colors.headerIcon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Restart Chat", fontSize = 14.sp) },
                        onClick = {
                            showMenu = false
                            Conferbot.clearHistory()
                            coroutineScope.launch { Conferbot.initializeSession() }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("End Chat", fontSize = 14.sp) },
                        onClick = { showMenu = false; onDismiss() },
                        leadingIcon = {
                            Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (soundEnabled) "Sound Off" else "Sound On", fontSize = 14.sp) },
                        onClick = { soundEnabled = !soundEnabled; showMenu = false },
                        leadingIcon = {
                            Icon(
                                if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                null, Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
