package com.conferbot.sdk.ui.compose.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    /** The bot's settings as the web applies them; null keeps the host app's look. */
    look: com.conferbot.sdk.core.customization.ResolvedCustomizations? = null,
    /**
     * Kept for source compatibility and not shown: the header keeps the bot's
     * name and logo when a person joins, as the web widget does.
     */
    agentName: String? = null,
    agentAvatar: String? = null,
    modifier: Modifier = Modifier,
    /**
     * The words on the Online line: "Online", or the team's reply time
     * ("Typically replies in a few minutes", "Online · team back at 9 AM";
     * core/livechat/ReplyTime.kt headerStatus). Default "Online".
     */
    statusText: String = "Online",
    /** "Email me this chat" in the menu: shown when given (a session, a visitor not blocked). */
    onEmailChat: (() -> Unit)? = null,
    /**
     * "Your requests" in the menu ("Your requests · New reply" when a team
     * reply is unread): shown when given, once the visitor has a request.
     */
    requestsLabel: String? = null,
    onRequests: (() -> Unit)? = null,
    /** Back to the home screen, when the bot has one (web templates/shared.ts #chat_head_back-btn): a ‹ first. */
    onHome: (() -> Unit)? = null
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
            if (onHome != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(width = 24.dp, height = 40.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Back", onClick = onHome)
                        .semantics { contentDescription = "Back" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        style = TextStyle(fontFamily = typography.fontFamily, fontSize = 30.sp, lineHeight = 32.sp),
                        color = colors.headerText,
                        modifier = Modifier.offset(y = (-3).dp)
                    )
                }
            }

            // The bot's logo (the web shows the logo here, not the avatar),
            // none when the owner hid it. A person joining does not change it.
            val avatarUrl = when {
                look != null -> look.logo
                else -> serverCustomization?.avatarUrl ?: serverCustomization?.logoUrl
            }
            val showAvatar = look?.hideLogo != true
            if (showAvatar && avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Bot avatar",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (showAvatar) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.headerText.copy(alpha = 0.2f), CircleShape),
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
                    look?.tagline != null -> look.tagline
                    look == null && serverCustomization?.enableTagline == true &&
                            !serverCustomization.tagline.isNullOrBlank() ->
                        serverCustomization.tagline
                    else -> null
                }
                // The web's "Online" line: a 7dp green dot with a soft ring, and
                // the reply time on it; still there while a person is in the chat.
                if (subtitleText == null && look?.showOnline == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f), CircleShape)
                                .padding(1.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF22C55E), CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            style = TextStyle(fontFamily = typography.fontFamily, fontSize = 11.sp),
                            color = colors.headerText.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    if (onEmailChat != null) DropdownMenuItem(
                        text = { Text(com.conferbot.sdk.core.livechat.EmailTranscript.MENU, fontSize = 14.sp) },
                        onClick = { showMenu = false; onEmailChat() },
                        leadingIcon = {
                            Icon(Icons.Default.Email, null, Modifier.size(18.dp))
                        }
                    )
                    if (requestsLabel != null && onRequests != null) DropdownMenuItem(
                        text = { Text(requestsLabel, fontSize = 14.sp) },
                        onClick = { showMenu = false; onRequests() },
                        leadingIcon = {
                            Icon(Icons.Default.Inbox, null, Modifier.size(18.dp))
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
