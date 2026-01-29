package com.conferbot.sdk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * CompositionLocal for the current Conferbot theme
 *
 * This provides access to the current theme throughout the composable tree.
 */
val LocalConferbotTheme = staticCompositionLocalOf { LightTheme }

/**
 * CompositionLocal for controlling whether to use dark theme
 */
val LocalUseDarkTheme = compositionLocalOf { false }

/**
 * CompositionLocal for theme change callback
 */
val LocalOnThemeChange = staticCompositionLocalOf<((ConferbotTheme) -> Unit)?> { null }

/**
 * Ambient object for accessing the current Conferbot theme
 *
 * Usage:
 * ```
 * val theme = ConferbotThemeAmbient.current
 * val colors = theme.colors
 * val typography = theme.typography
 * ```
 */
object ConferbotThemeAmbient {
    /**
     * Access the current Conferbot theme
     */
    val current: ConferbotTheme
        @Composable
        get() = LocalConferbotTheme.current

    /**
     * Access current colors
     */
    val colors: ConferbotColors
        @Composable
        get() = current.colors

    /**
     * Access current typography
     */
    val typography: ConferbotTypography
        @Composable
        get() = current.typography

    /**
     * Access current shapes
     */
    val shapes: ConferbotShapes
        @Composable
        get() = current.shapes

    /**
     * Access current spacing
     */
    val spacing: ConferbotSpacing
        @Composable
        get() = current.spacing

    /**
     * Access current animations
     */
    val animations: ConferbotAnimations
        @Composable
        get() = current.animations

    /**
     * Access current background
     */
    val background: ConferbotBackground
        @Composable
        get() = current.background

    /**
     * Check if current theme is dark
     */
    val isDarkTheme: Boolean
        @Composable
        get() = current.isDarkTheme
}

/**
 * Theme provider for Conferbot SDK
 *
 * Wraps content with the appropriate Conferbot theme based on the provided
 * light/dark themes and system settings.
 *
 * @param lightTheme The theme to use in light mode
 * @param darkTheme The theme to use in dark mode
 * @param useDarkTheme Whether to use dark theme. If null, follows system settings
 * @param onThemeChange Callback when theme changes (for persistence)
 * @param content The composable content to wrap
 */
@Composable
fun ConferbotThemeProvider(
    lightTheme: ConferbotTheme = LightTheme,
    darkTheme: ConferbotTheme = DarkTheme,
    useDarkTheme: Boolean? = null,
    onThemeChange: ((ConferbotTheme) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDarkTheme = useDarkTheme ?: isSystemInDarkTheme()
    val currentTheme = if (isDarkTheme) darkTheme else lightTheme

    CompositionLocalProvider(
        LocalConferbotTheme provides currentTheme,
        LocalUseDarkTheme provides isDarkTheme,
        LocalOnThemeChange provides onThemeChange
    ) {
        content()
    }
}

/**
 * Alternative theme provider that accepts a single theme
 *
 * @param theme The theme to use
 * @param content The composable content to wrap
 */
@Composable
fun ConferbotThemeProvider(
    theme: ConferbotTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalConferbotTheme provides theme,
        LocalUseDarkTheme provides theme.isDarkTheme
    ) {
        content()
    }
}

/**
 * State holder for dynamic theme switching
 */
@Stable
class ConferbotThemeState(
    initialLightTheme: ConferbotTheme = LightTheme,
    initialDarkTheme: ConferbotTheme = DarkTheme,
    initialUseDarkTheme: Boolean? = null
) {
    /**
     * The current light theme
     */
    var lightTheme by mutableStateOf(initialLightTheme)

    /**
     * The current dark theme
     */
    var darkTheme by mutableStateOf(initialDarkTheme)

    /**
     * Whether to use dark theme (null = follow system)
     */
    var useDarkTheme by mutableStateOf(initialUseDarkTheme)

    /**
     * Update the light theme
     */
    fun setLightTheme(theme: ConferbotTheme) {
        lightTheme = theme.copy(isDarkTheme = false)
    }

    /**
     * Update the dark theme
     */
    fun setDarkTheme(theme: ConferbotTheme) {
        darkTheme = theme.copy(isDarkTheme = true)
    }

    /**
     * Update primary color for both themes
     */
    fun setPrimaryColor(color: Color) {
        lightTheme = lightTheme.copy(
            colors = lightTheme.colors.copy(
                primary = color,
                userBubble = color,
                headerBackground = color,
                inputBorderFocused = color,
                buttonBackground = color,
                link = color
            )
        )
        // For dark theme, we might want a lighter variant
        val lightColor = color.copy(alpha = 0.8f)
        darkTheme = darkTheme.copy(
            colors = darkTheme.colors.copy(
                primary = lightColor,
                userBubble = lightColor,
                inputBorderFocused = lightColor,
                buttonBackground = lightColor,
                link = lightColor
            )
        )
    }

    /**
     * Toggle dark mode
     */
    fun toggleDarkMode() {
        useDarkTheme = useDarkTheme?.not() ?: true
    }

    /**
     * Set specific dark mode preference
     */
    fun setDarkMode(enabled: Boolean?) {
        useDarkTheme = enabled
    }
}

/**
 * Remember a theme state
 */
@Composable
fun rememberConferbotThemeState(
    initialLightTheme: ConferbotTheme = LightTheme,
    initialDarkTheme: ConferbotTheme = DarkTheme,
    initialUseDarkTheme: Boolean? = null
): ConferbotThemeState {
    return remember {
        ConferbotThemeState(
            initialLightTheme = initialLightTheme,
            initialDarkTheme = initialDarkTheme,
            initialUseDarkTheme = initialUseDarkTheme
        )
    }
}

/**
 * Theme provider that uses a ConferbotThemeState for dynamic updates
 */
@Composable
fun ConferbotThemeProvider(
    state: ConferbotThemeState,
    content: @Composable () -> Unit
) {
    ConferbotThemeProvider(
        lightTheme = state.lightTheme,
        darkTheme = state.darkTheme,
        useDarkTheme = state.useDarkTheme,
        content = content
    )
}

/**
 * Theme mode options
 */
enum class ThemeMode {
    /**
     * Always use light theme
     */
    LIGHT,

    /**
     * Always use dark theme
     */
    DARK,

    /**
     * Follow system setting
     */
    SYSTEM
}

/**
 * Convert ThemeMode to nullable Boolean for useDarkTheme parameter
 */
fun ThemeMode.toUseDarkTheme(): Boolean? = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> null
}
