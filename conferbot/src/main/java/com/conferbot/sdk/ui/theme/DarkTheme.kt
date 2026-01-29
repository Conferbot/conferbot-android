package com.conferbot.sdk.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default dark theme for Conferbot SDK
 *
 * Provides a sleek dark color scheme optimized for low-light environments
 * with proper contrast ratios and accessibility. Based on Material Design 3 guidelines.
 */

// Dark theme color palette
object DarkThemeColors {
    // Brand colors
    val Primary = Color(0xFF7C7FFF)
    val Secondary = Color(0xFFD0BCFF)
    val Tertiary = Color(0xFFEFB8C8)

    // Surface colors
    val Background = Color(0xFF1C1B1F)
    val Surface = Color(0xFF2B2930)
    val SurfaceVariant = Color(0xFF49454F)
    val Error = Color(0xFFF2B8B5)

    // Content colors
    val OnPrimary = Color(0xFF00006E)
    val OnSecondary = Color(0xFF381E72)
    val OnBackground = Color(0xFFE6E1E5)
    val OnSurface = Color(0xFFE6E1E5)
    val OnSurfaceVariant = Color(0xFFCAC4D0)
    val OnError = Color(0xFF601410)

    // Message bubbles
    val BotBubble = Color(0xFF49454F)
    val BotBubbleText = Color(0xFFE6E1E5)
    val UserBubble = Color(0xFF7C7FFF)
    val UserBubbleText = Color.White
    val AgentBubble = Color(0xFF1E3B23)
    val AgentBubbleText = Color(0xFF81C784)
    val SystemMessageText = Color(0xFF9E9E9E)

    // Input area
    val InputBackground = Color(0xFF2B2930)
    val InputText = Color(0xFFE6E1E5)
    val InputPlaceholder = Color(0xFF9E9E9E)
    val InputBorder = Color(0xFF49454F)
    val InputBorderFocused = Color(0xFF7C7FFF)

    // Header
    val HeaderBackground = Color(0xFF2B2930)
    val HeaderText = Color(0xFFE6E1E5)
    val HeaderIcon = Color(0xFFE6E1E5)

    // Status indicators
    val Online = Color(0xFF81C784)
    val Offline = Color(0xFF757575)
    val Typing = Color(0xFF9E9E9E)
    val Sending = Color(0xFF757575)
    val Sent = Color(0xFF9E9E9E)
    val Delivered = Color(0xFF7C7FFF)
    val Read = Color(0xFF7C7FFF)

    // Interactive elements
    val ButtonBackground = Color(0xFF7C7FFF)
    val ButtonText = Color(0xFF00006E)
    val ButtonDisabled = Color(0xFF49454F)
    val ButtonDisabledText = Color(0xFF9E9E9E)
    val Link = Color(0xFF7C7FFF)

    // Dividers and outlines
    val Divider = Color(0xFF49454F)
    val Outline = Color(0xFF49454F)

    // Timestamps
    val Timestamp = Color(0xFF9E9E9E)
}

/**
 * Pre-configured dark theme colors
 */
val DarkThemeColorsConfig = ConferbotColors(
    primary = DarkThemeColors.Primary,
    secondary = DarkThemeColors.Secondary,
    tertiary = DarkThemeColors.Tertiary,
    background = DarkThemeColors.Background,
    surface = DarkThemeColors.Surface,
    surfaceVariant = DarkThemeColors.SurfaceVariant,
    error = DarkThemeColors.Error,
    onPrimary = DarkThemeColors.OnPrimary,
    onSecondary = DarkThemeColors.OnSecondary,
    onBackground = DarkThemeColors.OnBackground,
    onSurface = DarkThemeColors.OnSurface,
    onSurfaceVariant = DarkThemeColors.OnSurfaceVariant,
    onError = DarkThemeColors.OnError,
    botBubble = DarkThemeColors.BotBubble,
    botBubbleText = DarkThemeColors.BotBubbleText,
    userBubble = DarkThemeColors.UserBubble,
    userBubbleText = DarkThemeColors.UserBubbleText,
    agentBubble = DarkThemeColors.AgentBubble,
    agentBubbleText = DarkThemeColors.AgentBubbleText,
    systemMessageText = DarkThemeColors.SystemMessageText,
    inputBackground = DarkThemeColors.InputBackground,
    inputText = DarkThemeColors.InputText,
    inputPlaceholder = DarkThemeColors.InputPlaceholder,
    inputBorder = DarkThemeColors.InputBorder,
    inputBorderFocused = DarkThemeColors.InputBorderFocused,
    headerBackground = DarkThemeColors.HeaderBackground,
    headerText = DarkThemeColors.HeaderText,
    headerIcon = DarkThemeColors.HeaderIcon,
    online = DarkThemeColors.Online,
    offline = DarkThemeColors.Offline,
    typing = DarkThemeColors.Typing,
    sending = DarkThemeColors.Sending,
    sent = DarkThemeColors.Sent,
    delivered = DarkThemeColors.Delivered,
    read = DarkThemeColors.Read,
    buttonBackground = DarkThemeColors.ButtonBackground,
    buttonText = DarkThemeColors.ButtonText,
    buttonDisabled = DarkThemeColors.ButtonDisabled,
    buttonDisabledText = DarkThemeColors.ButtonDisabledText,
    link = DarkThemeColors.Link,
    divider = DarkThemeColors.Divider,
    outline = DarkThemeColors.Outline,
    timestamp = DarkThemeColors.Timestamp
)

/**
 * Default dark theme typography
 */
val DarkThemeTypography = ConferbotTypography(
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
 * Default dark theme shapes
 */
val DarkThemeShapes = ConferbotShapes(
    bubbleRadius = 16.dp,
    buttonRadius = 12.dp,
    cardRadius = 12.dp,
    inputRadius = 24.dp,
    imageRadius = 12.dp
)

/**
 * Default dark theme spacing
 */
val DarkThemeSpacing = ConferbotSpacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp
)

/**
 * Default dark theme animations
 */
val DarkThemeAnimations = ConferbotAnimations(
    typingDuration = 1000,
    messageFadeDuration = 200,
    buttonPressDuration = 100
)

/**
 * Complete default dark theme
 */
val DarkTheme = ConferbotTheme(
    colors = DarkThemeColorsConfig,
    typography = DarkThemeTypography,
    shapes = DarkThemeShapes,
    spacing = DarkThemeSpacing,
    animations = DarkThemeAnimations,
    background = ConferbotBackground.SolidColor(DarkThemeColors.Background),
    isDarkTheme = true,
    name = "Dark"
)

/**
 * Alternative dark themes for different brand colors
 */
object DarkThemeVariants {
    /**
     * Blue dark theme (default)
     */
    val Blue = DarkTheme

    /**
     * Teal dark theme
     */
    val Teal = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            primary = Color(0xFF4DB6AC),
            userBubble = Color(0xFF4DB6AC),
            inputBorderFocused = Color(0xFF4DB6AC),
            buttonBackground = Color(0xFF4DB6AC),
            buttonText = Color(0xFF003C36),
            link = Color(0xFF4DB6AC),
            delivered = Color(0xFF4DB6AC),
            read = Color(0xFF4DB6AC)
        ),
        name = "Dark Teal"
    )

    /**
     * Purple dark theme
     */
    val Purple = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            primary = Color(0xFFD0BCFF),
            userBubble = Color(0xFFD0BCFF),
            inputBorderFocused = Color(0xFFD0BCFF),
            buttonBackground = Color(0xFFD0BCFF),
            buttonText = Color(0xFF381E72),
            link = Color(0xFFD0BCFF),
            delivered = Color(0xFFD0BCFF),
            read = Color(0xFFD0BCFF)
        ),
        name = "Dark Purple"
    )

    /**
     * Green dark theme
     */
    val Green = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            primary = Color(0xFF81C784),
            userBubble = Color(0xFF81C784),
            inputBorderFocused = Color(0xFF81C784),
            buttonBackground = Color(0xFF81C784),
            buttonText = Color(0xFF1B5E20),
            link = Color(0xFF81C784),
            delivered = Color(0xFF81C784),
            read = Color(0xFF81C784)
        ),
        name = "Dark Green"
    )

    /**
     * Orange dark theme
     */
    val Orange = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            primary = Color(0xFFFFB74D),
            userBubble = Color(0xFFFFB74D),
            inputBorderFocused = Color(0xFFFFB74D),
            buttonBackground = Color(0xFFFFB74D),
            buttonText = Color(0xFFE65100),
            link = Color(0xFFFFB74D),
            delivered = Color(0xFFFFB74D),
            read = Color(0xFFFFB74D)
        ),
        name = "Dark Orange"
    )

    /**
     * Red dark theme
     */
    val Red = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            primary = Color(0xFFEF9A9A),
            userBubble = Color(0xFFEF9A9A),
            inputBorderFocused = Color(0xFFEF9A9A),
            buttonBackground = Color(0xFFEF9A9A),
            buttonText = Color(0xFFC62828),
            link = Color(0xFFEF9A9A),
            delivered = Color(0xFFEF9A9A),
            read = Color(0xFFEF9A9A)
        ),
        name = "Dark Red"
    )

    /**
     * AMOLED dark theme (true black background)
     */
    val AMOLED = DarkTheme.copy(
        colors = DarkThemeColorsConfig.copy(
            background = Color.Black,
            surface = Color(0xFF121212),
            surfaceVariant = Color(0xFF1E1E1E),
            inputBackground = Color(0xFF121212)
        ),
        background = ConferbotBackground.SolidColor(Color.Black),
        name = "AMOLED Dark"
    )
}
