package com.conferbot.sdk.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider

/**
 * Position of the floating widget on screen.
 */
enum class WidgetPosition {
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

/**
 * Configuration for the floating chat widget appearance and behavior.
 *
 * @param position Corner placement of the FAB
 * @param size Diameter of the FAB
 * @param offsetX Horizontal distance from the screen edge
 * @param offsetY Vertical distance from the screen edge
 * @param backgroundColor Solid background color for the FAB. When null, the server
 *   theme primary color is used, falling back to a default blue.
 * @param gradientColors Optional gradient pair. When set, [backgroundColor] is ignored.
 * @param showUnreadBadge Whether to display the unread message badge
 * @param ctaText Optional call-to-action label shown next to the FAB (reserved for future use)
 * @param borderRadius Corner radius of the FAB
 */
data class ConferBotWidgetConfig(
    val position: WidgetPosition = WidgetPosition.BOTTOM_RIGHT,
    val size: Dp = 56.dp,
    val offsetX: Dp = 16.dp,
    val offsetY: Dp = 16.dp,
    val backgroundColor: Color? = null,
    val gradientColors: Pair<Color, Color>? = null,
    val showUnreadBadge: Boolean = true,
    val ctaText: String? = null,
    val borderRadius: Dp = 28.dp
)

/** Default brand blue used when no server theme or explicit color is provided. */
private val DefaultBrandBlue = Color(0xFF2563EB)

/** Duration (ms) for the chat overlay enter/exit transitions. */
private const val OVERLAY_TRANSITION_MS = 300

/** Duration (ms) for the FAB icon crossfade. */
private const val ICON_CROSSFADE_MS = 200

/**
 * Floating chat widget launcher.
 *
 * Renders a FAB in the chosen screen corner. Tapping the FAB opens an
 * animated chat overlay that hosts [ConferBotChatScreen]. The overlay
 * includes a semi-transparent scrim that dismisses the chat on tap.
 *
 * Usage:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     // Your app content
 *     ConferBotWidget()
 * }
 * ```
 *
 * @param config Visual and positional configuration for the widget
 * @param modifier Modifier applied to the root container
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConferBotWidget(
    config: ConferBotWidgetConfig = ConferBotWidgetConfig(),
    modifier: Modifier = Modifier
) {
    var isChatOpen by remember { mutableStateOf(false) }

    val unreadCount by Conferbot.unreadCount.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val serverTheme by Conferbot.serverTheme.collectAsState()
    val serverCustomization by Conferbot.serverCustomization.collectAsState()

    // Synchronise visibility state with the singleton so other parts of the
    // SDK can query whether the overlay is showing (e.g. to suppress notifications).
    LaunchedEffect(isChatOpen) {
        Conferbot.setChatVisible(isChatOpen)
        if (isChatOpen) {
            Conferbot.resetUnreadCount()
        }
    }

    // Resolve the FAB background color: explicit config > server theme > default blue
    val resolvedColor = config.backgroundColor
        ?: serverTheme?.colors?.primary
        ?: DefaultBrandBlue

    // Alignment for the FAB based on the configured position
    val fabAlignment = when (config.position) {
        WidgetPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        WidgetPosition.BOTTOM_LEFT -> Alignment.BottomStart
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ---- Scrim (semi-transparent backdrop) ----
        AnimatedVisibility(
            visible = isChatOpen,
            enter = fadeIn(tween(OVERLAY_TRANSITION_MS)),
            exit = fadeOut(tween(OVERLAY_TRANSITION_MS))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isChatOpen = false
                    }
            )
        }

        // ---- Chat overlay ----
        AnimatedVisibility(
            visible = isChatOpen,
            enter = slideInVertically(tween(OVERLAY_TRANSITION_MS)) { it } + fadeIn(tween(OVERLAY_TRANSITION_MS)),
            exit = slideOutVertically(tween(OVERLAY_TRANSITION_MS)) { it } + fadeOut(tween(OVERLAY_TRANSITION_MS)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                shadowElevation = 16.dp,
                color = Color.White
            ) {
                val currentServerTheme = serverTheme
                if (currentServerTheme != null) {
                    ConferbotThemeProvider(theme = currentServerTheme) {
                        ConferBotChatScreen(onDismiss = { isChatOpen = false })
                    }
                } else {
                    ConferBotChatScreen(onDismiss = { isChatOpen = false })
                }
            }
        }

        // ---- Floating Action Button ----
        Box(
            modifier = Modifier
                .align(fabAlignment)
                .padding(
                    start = if (config.position == WidgetPosition.BOTTOM_LEFT) config.offsetX else 0.dp,
                    end = if (config.position == WidgetPosition.BOTTOM_RIGHT) config.offsetX else 0.dp,
                    bottom = config.offsetY
                )
        ) {
            val fabShape = RoundedCornerShape(config.borderRadius)

            BadgedBox(
                badge = {
                    if (config.showUnreadBadge && unreadCount > 0 && !isChatOpen) {
                        Badge(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            ) {
                val gradientModifier = config.gradientColors?.let { (start, end) ->
                    Modifier.background(
                        brush = Brush.linearGradient(listOf(start, end)),
                        shape = fabShape
                    )
                }

                Surface(
                    onClick = { isChatOpen = !isChatOpen },
                    modifier = Modifier
                        .size(config.size)
                        .shadow(8.dp, fabShape)
                        .then(gradientModifier ?: Modifier),
                    shape = fabShape,
                    color = if (config.gradientColors != null) Color.Transparent else resolvedColor,
                    contentColor = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isChatOpen,
                            transitionSpec = {
                                fadeIn(tween(ICON_CROSSFADE_MS)) togetherWith fadeOut(tween(ICON_CROSSFADE_MS))
                            },
                            label = "fab_icon"
                        ) { chatOpen ->
                            if (chatOpen) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close chat",
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "Open chat",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenience wrapper that overlays [ConferBotWidget] on top of arbitrary content.
 *
 * Usage:
 * ```
 * ConferBotWidgetScope {
 *     MyAppContent()
 * }
 * ```
 *
 * @param config Widget configuration
 * @param content The host application content
 */
@Composable
fun ConferBotWidgetScope(
    config: ConferBotWidgetConfig = ConferBotWidgetConfig(),
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        content()
        ConferBotWidget(config)
    }
}
