package com.conferbot.sdk.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default light theme for Conferbot SDK
 *
 * Provides a clean, professional light color scheme with good contrast
 * and accessibility. Based on Material Design 3 guidelines.
 */

// Light theme color palette
object LightThemeColors {
    // Brand colors
    val Primary = Color(0xFF0100EC)
    val Secondary = Color(0xFF6750A4)
    val Tertiary = Color(0xFF7D5260)

    // Surface colors
    val Background = Color(0xFFFFFBFF)
    val Surface = Color(0xFFFFFBFF)
    val SurfaceVariant = Color(0xFFF5F5F5)
    val Error = Color(0xFFB3261E)

    // Content colors
    val OnPrimary = Color.White
    val OnSecondary = Color.White
    val OnBackground = Color(0xFF1C1B1F)
    val OnSurface = Color(0xFF1C1B1F)
    val OnSurfaceVariant = Color(0xFF49454F)
    val OnError = Color.White

    // Message bubbles
    val BotBubble = Color(0xFFF5F5F5)
    val BotBubbleText = Color(0xFF1C1B1F)
    val UserBubble = Color(0xFF0100EC)
    val UserBubbleText = Color.White
    val AgentBubble = Color(0xFFE8F5E9)
    val AgentBubbleText = Color(0xFF1B5E20)
    val SystemMessageText = Color(0xFF6B6B6B)

    // Input area
    val InputBackground = Color.White
    val InputText = Color(0xFF1C1B1F)
    val InputPlaceholder = Color(0xFF9E9E9E)
    val InputBorder = Color(0xFFE0E0E0)
    val InputBorderFocused = Color(0xFF0100EC)

    // Header
    val HeaderBackground = Color(0xFF0100EC)
    val HeaderText = Color.White
    val HeaderIcon = Color.White

    // Status indicators
    val Online = Color(0xFF4CAF50)
    val Offline = Color(0xFF9E9E9E)
    val Typing = Color(0xFF9E9E9E)
    val Sending = Color(0xFF9E9E9E)
    val Sent = Color(0xFF9E9E9E)
    val Delivered = Color(0xFF0100EC)
    val Read = Color(0xFF0100EC)

    // Interactive elements
    val ButtonBackground = Color(0xFF0100EC)
    val ButtonText = Color.White
    val ButtonDisabled = Color(0xFFE0E0E0)
    val ButtonDisabledText = Color(0xFF9E9E9E)
    val Link = Color(0xFF0100EC)

    // Dividers and outlines
    val Divider = Color(0xFFE0E0E0)
    val Outline = Color(0xFFE0E0E0)

    // Timestamps
    val Timestamp = Color(0xFF9E9E9E)
}

/**
 * Pre-configured light theme colors
 */
val LightThemeColorsConfig = ConferbotColors(
    primary = LightThemeColors.Primary,
    secondary = LightThemeColors.Secondary,
    tertiary = LightThemeColors.Tertiary,
    background = LightThemeColors.Background,
    surface = LightThemeColors.Surface,
    surfaceVariant = LightThemeColors.SurfaceVariant,
    error = LightThemeColors.Error,
    onPrimary = LightThemeColors.OnPrimary,
    onSecondary = LightThemeColors.OnSecondary,
    onBackground = LightThemeColors.OnBackground,
    onSurface = LightThemeColors.OnSurface,
    onSurfaceVariant = LightThemeColors.OnSurfaceVariant,
    onError = LightThemeColors.OnError,
    botBubble = LightThemeColors.BotBubble,
    botBubbleText = LightThemeColors.BotBubbleText,
    userBubble = LightThemeColors.UserBubble,
    userBubbleText = LightThemeColors.UserBubbleText,
    agentBubble = LightThemeColors.AgentBubble,
    agentBubbleText = LightThemeColors.AgentBubbleText,
    systemMessageText = LightThemeColors.SystemMessageText,
    inputBackground = LightThemeColors.InputBackground,
    inputText = LightThemeColors.InputText,
    inputPlaceholder = LightThemeColors.InputPlaceholder,
    inputBorder = LightThemeColors.InputBorder,
    inputBorderFocused = LightThemeColors.InputBorderFocused,
    headerBackground = LightThemeColors.HeaderBackground,
    headerText = LightThemeColors.HeaderText,
    headerIcon = LightThemeColors.HeaderIcon,
    online = LightThemeColors.Online,
    offline = LightThemeColors.Offline,
    typing = LightThemeColors.Typing,
    sending = LightThemeColors.Sending,
    sent = LightThemeColors.Sent,
    delivered = LightThemeColors.Delivered,
    read = LightThemeColors.Read,
    buttonBackground = LightThemeColors.ButtonBackground,
    buttonText = LightThemeColors.ButtonText,
    buttonDisabled = LightThemeColors.ButtonDisabled,
    buttonDisabledText = LightThemeColors.ButtonDisabledText,
    link = LightThemeColors.Link,
    divider = LightThemeColors.Divider,
    outline = LightThemeColors.Outline,
    timestamp = LightThemeColors.Timestamp
)

/**
 * Default light theme typography
 */
val LightThemeTypography = ConferbotTypography(
    fontFamily = FontFamily.Default,
    headerSize = 20.sp,
    subtitleSize = 14.sp,
    bodySize = 16.sp,
    captionSize = 12.sp,
    messageSize = 15.sp,
    inputSize = 16.sp,
    buttonSize = 14.sp,
    timestampSize = 11.sp
)

/**
 * Default light theme shapes
 */
val LightThemeShapes = ConferbotShapes(
    bubbleRadius = 16.dp,
    buttonRadius = 12.dp,
    cardRadius = 12.dp,
    inputRadius = 24.dp,
    imageRadius = 12.dp
)

/**
 * Default light theme spacing
 */
val LightThemeSpacing = ConferbotSpacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp
)

/**
 * Default light theme animations
 */
val LightThemeAnimations = ConferbotAnimations(
    typingDuration = 1000,
    messageFadeDuration = 200,
    buttonPressDuration = 100
)

/**
 * Complete default light theme
 */
val LightTheme = ConferbotTheme(
    colors = LightThemeColorsConfig,
    typography = LightThemeTypography,
    shapes = LightThemeShapes,
    spacing = LightThemeSpacing,
    animations = LightThemeAnimations,
    background = ConferbotBackground.SolidColor(LightThemeColors.Background),
    isDarkTheme = false,
    name = "Light"
)

/**
 * Alternative light themes for different brand colors
 */
object LightThemeVariants {
    /**
     * Blue light theme (default)
     */
    val Blue = LightTheme

    /**
     * Teal light theme
     */
    val Teal = LightTheme.copy(
        colors = LightThemeColorsConfig.copy(
            primary = Color(0xFF009688),
            userBubble = Color(0xFF009688),
            headerBackground = Color(0xFF009688),
            inputBorderFocused = Color(0xFF009688),
            buttonBackground = Color(0xFF009688),
            link = Color(0xFF009688),
            delivered = Color(0xFF009688),
            read = Color(0xFF009688)
        ),
        name = "Light Teal"
    )

    /**
     * Purple light theme
     */
    val Purple = LightTheme.copy(
        colors = LightThemeColorsConfig.copy(
            primary = Color(0xFF6750A4),
            userBubble = Color(0xFF6750A4),
            headerBackground = Color(0xFF6750A4),
            inputBorderFocused = Color(0xFF6750A4),
            buttonBackground = Color(0xFF6750A4),
            link = Color(0xFF6750A4),
            delivered = Color(0xFF6750A4),
            read = Color(0xFF6750A4)
        ),
        name = "Light Purple"
    )

    /**
     * Green light theme
     */
    val Green = LightTheme.copy(
        colors = LightThemeColorsConfig.copy(
            primary = Color(0xFF2E7D32),
            userBubble = Color(0xFF2E7D32),
            headerBackground = Color(0xFF2E7D32),
            inputBorderFocused = Color(0xFF2E7D32),
            buttonBackground = Color(0xFF2E7D32),
            link = Color(0xFF2E7D32),
            delivered = Color(0xFF2E7D32),
            read = Color(0xFF2E7D32)
        ),
        name = "Light Green"
    )

    /**
     * Orange light theme
     */
    val Orange = LightTheme.copy(
        colors = LightThemeColorsConfig.copy(
            primary = Color(0xFFE65100),
            userBubble = Color(0xFFE65100),
            headerBackground = Color(0xFFE65100),
            inputBorderFocused = Color(0xFFE65100),
            buttonBackground = Color(0xFFE65100),
            link = Color(0xFFE65100),
            delivered = Color(0xFFE65100),
            read = Color(0xFFE65100)
        ),
        name = "Light Orange"
    )

    /**
     * Red light theme
     */
    val Red = LightTheme.copy(
        colors = LightThemeColorsConfig.copy(
            primary = Color(0xFFC62828),
            userBubble = Color(0xFFC62828),
            headerBackground = Color(0xFFC62828),
            inputBorderFocused = Color(0xFFC62828),
            buttonBackground = Color(0xFFC62828),
            link = Color(0xFFC62828),
            delivered = Color(0xFFC62828),
            read = Color(0xFFC62828)
        ),
        name = "Light Red"
    )
}
