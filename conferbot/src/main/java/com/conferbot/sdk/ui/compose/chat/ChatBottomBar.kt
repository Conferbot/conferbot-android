package com.conferbot.sdk.ui.compose.chat

import com.conferbot.sdk.core.i18n.I18n
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.conferbot.sdk.core.ServerChatbotCustomization
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

private const val CONFERBOT_URL = "https://www.conferbot.com"
private const val CONFERBOT_LOGO_URL =
    "https://www.conferbot.com/img/logo/conferbot-logo.png"

/**
 * Unified bottom bar: chat input + powered-by footer as one seamless unit.
 *
 * Matches the web widget where the input area and footer share the same
 * white background with a single upward shadow, creating one cohesive block.
 *
 * Layout:
 * ┌─────────────────────────────────────────┐  ← shadow on top edge
 * │  ┌─────────────────────────┐  ┌──────┐  │
 * │  │  Type a message...      │  │  ➤   │  │  ← pill input + send btn
 * │  └─────────────────────────┘  └──────┘  │
 * │          Powered by [LOGO]              │  ← footer, no separator
 * └─────────────────────────────────────────┘
 */
@Composable
fun ChatBottomBar(
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    serverCustomization: ServerChatbotCustomization?,
    modifier: Modifier = Modifier,
    /** Off while the agent turned replies off or locked the conversation. */
    enabled: Boolean = true,
    placeholder: String = I18n.t("chat.composer.placeholder"),
    /** The paperclip: shown when given (a live chat that takes visitor files). */
    onAttach: (() -> Unit)? = null
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank() && enabled

    // "Ask another way" under an AI answer puts the cursor here.
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusAsks by com.conferbot.sdk.core.Conferbot.composerFocus.collectAsState()
    LaunchedEffect(focusAsks) { if (focusAsks > 0 && enabled) runCatching { focusRequester.requestFocus() } }

    // The widget theme's surfaces (white, as before, for classic in light),
    // its field radius, and the palette's send colours.
    val look by com.conferbot.sdk.core.Conferbot.customizations.collectAsState()
    val tokens = look.theme.tokens
    val barColor = com.conferbot.sdk.ui.compose.lookColor(tokens?.bg) ?: Color.White
    val fieldColor = com.conferbot.sdk.ui.compose.lookColor(tokens?.field) ?: Color.White
    val fieldBorder = com.conferbot.sdk.ui.compose.lookColor(tokens?.line2) ?: Color(0xFFE0E0E0)
    val placeholderColor = com.conferbot.sdk.ui.compose.lookColor(tokens?.muted) ?: Color(0xFF4D4D4D)
    val inkColor = com.conferbot.sdk.ui.compose.lookColor(tokens?.ink) ?: Color.Black
    val footerColor = com.conferbot.sdk.ui.compose.lookColor(tokens?.muted) ?: Color(0xFF56595B)
    val fieldShape = RoundedCornerShape(minOf(look.fieldRadius, 25f).dp)
    val sendColor = look.accentInk?.let { com.conferbot.sdk.ui.compose.lookColor(look.accent) } ?: colors.headerBackground
    val sendInk = com.conferbot.sdk.ui.compose.lookColor(look.accentInk)
        ?: if (sendColor.luminance() > 0.5f) Color.Black else Color.White

    val sendBgColor by animateColorAsState(
        targetValue = if (canSend) sendColor else sendColor.copy(alpha = 0.35f),
        animationSpec = tween(200),
        label = "send_bg"
    )

    // One unified white block with a soft upward shadow
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
                clip = false,
                ambientColor = Color(0x33636363),   // rgba(99,99,99,0.2)
                spotColor = Color(0x33636363)
            )
            .background(barColor)
    ) {
        // ── Input row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onAttach != null) {
                IconButton(onClick = onAttach, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = I18n.t("chat.composer.attach"),
                        tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
                    )
                }
            }
            // Pill-shaped input field with subtle border
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 130.dp)
                    .border(
                        width = 1.dp,
                        color = fieldBorder,
                        shape = fieldShape   // border-radius: 25px, or the theme's
                    )
                    .background(fieldColor, fieldShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(
                            fontSize = 16.sp,           // font-size: 16px
                            color = placeholderColor   // color: #4d4d4d
                        )
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        text = newText
                        onTypingChanged(newText.isNotEmpty())
                    },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = inkColor,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(inkColor),
                    maxLines = 4,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }

            // Themed circular send button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sendBgColor),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSendMessage(text.trim())
                            text = ""
                            onTypingChanged(false)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = I18n.t("chat.composer.send"),
                        // Readable on whatever the header colour is.
                        tint = sendInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── Powered-by footer — same white background, no separator ──
        if (serverCustomization?.hideBrand != true) {
            val isCustom = serverCustomization?.enableCustomBrand == true &&
                    !serverCustomization.customBrand.isNullOrBlank()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The owner's own text is not a link; "Powered by" opens conferbot.com.
                    .clickable(enabled = !isCustom) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CONFERBOT_URL))
                        context.startActivity(intent)
                    }
                    .padding(top = 2.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCustom) {
                    Text(
                        text = serverCustomization!!.customBrand!!,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = footerColor
                        )
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = I18n.t("chat.poweredBy") + " ",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = footerColor
                            )
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(CONFERBOT_LOGO_URL)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Conferbot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }
            }
        }
    }
}
