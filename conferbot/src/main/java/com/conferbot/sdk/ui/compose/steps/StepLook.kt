package com.conferbot.sdk.ui.compose.steps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.ui.compose.lookColor
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * The look every step card shares (web css/nodes/08-rich-cards.css): a card
 * in the theme's card colour with a thin line; the accent for the primary
 * button, focus and picks; the bot's option colours, option corner radius
 * and font size for what the visitor picks. Nothing moves on its own.
 */
data class StepLook(
    /** Options: the bot's option bubble colour and its text colour. */
    val option: Color,
    val optionInk: Color,
    /** Primary buttons, ticks, focus: the accent and the ink on it. */
    val accent: Color,
    val ink: Color,
    /** The card, its text, quiet text and its line. */
    val card: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    /** Options' corner radius and the message text size. */
    val radius: Dp,
    val fontSize: TextUnit,
    /** The chat's font (the owner's, else the theme's; core/customization/OwnerFont.kt). */
    val font: androidx.compose.ui.text.font.FontFamily? = null
) {
    val body get() = TextStyle(fontFamily = font, fontSize = fontSize, color = text, lineHeight = fontSize * 1.4f)
    val bold get() = TextStyle(fontFamily = font, fontSize = fontSize, color = text, fontWeight = FontWeight.SemiBold, lineHeight = fontSize * 1.35f)
    val hint get() = TextStyle(fontFamily = font, fontSize = 12.sp, color = muted, lineHeight = 16.sp)
    val label get() = TextStyle(fontFamily = font, fontSize = 12.sp, color = muted, fontWeight = FontWeight.SemiBold)

    companion object {
        val ERROR = Color(0xFFB42318)
        val ERROR_LINE = Color(0xFFD92D20)

        fun inkOn(c: Color) = if (c.luminance() > 0.55f) Color(0xFF0F172A) else Color.White
    }
}

/** The bot's look when its settings have arrived, else the SDK theme's. */
@Composable
fun rememberStepLook(): StepLook {
    val theme = ConferbotThemeAmbient.current
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    val loaded = map.isNotEmpty()
    val tokens = if (loaded) look.theme.tokens else null
    val accent = (if (loaded) lookColor(look.accent) else null) ?: theme.colors.primary
    return remember(theme, look, loaded) {
        StepLook(
            option = theme.colors.optionBubble,
            optionInk = theme.colors.optionBubbleText,
            accent = accent,
            ink = (if (loaded) lookColor(look.accentInk) else null) ?: StepLook.inkOn(accent),
            card = lookColor(tokens?.card) ?: Color.White,
            text = lookColor(tokens?.ink) ?: Color(0xFF111827),
            muted = lookColor(tokens?.muted) ?: Color(0xFF6B7280),
            line = lookColor(tokens?.line) ?: Color(0x1F0F172A),
            radius = if (loaded) look.optionRadius.dp else 10.dp,
            fontSize = if (loaded) look.fontSize.sp else 14.sp,
            font = theme.typography.fontFamily
        )
    }
}

/** The card a step sits on: at most 320 wide, the card colour, a thin line. */
@Composable
fun StepCard(style: StepLook, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .shadow(2.dp, shape, clip = false, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(style.card)
            .border(1.dp, style.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** A button on a card: primary in the accent, else quiet with a line. */
@Composable
fun StepButton(
    label: String,
    style: StepLook,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(style.radius)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (primary) style.accent else Color.Transparent,
        contentColor = if (primary) style.ink else style.text,
        border = if (primary) null else BorderStroke(1.dp, style.text.copy(alpha = 0.2f)),
        modifier = modifier.alpha(if (enabled) 1f else 0.45f)
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = style.font, fontSize = style.fontSize, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

/** An option the visitor picks, in the bot's option colours; a picked one ringed in the accent. */
@Composable
fun StepOption(
    label: String,
    style: StepLook,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(style.radius)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = style.option,
        contentColor = style.optionInk,
        border = if (picked) BorderStroke(2.dp, style.accent) else null,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = style.font, fontSize = style.fontSize, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** A text link on a card (Resend, Change email, Enter it manually). */
@Composable
fun StepLink(label: String, style: StepLook, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(fontFamily = style.font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) style.accent else style.muted),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp)
    )
}

/** A small label over a field. */
@Composable
fun StepLabel(text: String, style: StepLook) {
    Text(text, style = style.label)
}

/** The line under a field: the refusal in red, else the hint. */
@Composable
fun StepStatus(style: StepLook, error: String?, hint: String? = null) {
    when {
        !error.isNullOrEmpty() -> Text(error, style = TextStyle(fontFamily = style.font, fontSize = 12.5.sp, color = StepLook.ERROR, lineHeight = 17.sp))
        !hint.isNullOrEmpty() -> Text(hint, style = style.hint)
    }
}

/** A field on a card: a soft fill, a thin border, the accent when focused, red when refused. */
@Composable
fun StepField(
    value: String,
    onValueChange: (String) -> Unit,
    style: StepLook,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    invalid: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val shape = RoundedCornerShape(10.dp)
    val line = when {
        invalid -> StepLook.ERROR_LINE
        focused -> style.accent
        else -> style.text.copy(alpha = 0.22f)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontFamily = style.font, fontSize = 15.sp, color = style.text),
        cursorBrush = SolidColor(style.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        // Next goes to the card's next field; the rest close the keyboard and act.
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { focus.clearFocus(); onDone() },
            onSearch = { focus.clearFocus(); onDone() },
            onSend = { focus.clearFocus(); onDone() },
            onGo = { focus.clearFocus(); onDone() }
        ),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(shape)
                    .background(style.text.copy(alpha = 0.04f))
                    .border(if (focused || invalid) 1.5.dp else 1.dp, line, shape)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(placeholder, style = TextStyle(fontFamily = style.font, fontSize = 15.sp, color = style.muted.copy(alpha = 0.8f)))
                inner()
            }
        }
    )
}

/** Buttons at the foot of a card, at its end (wrapping when they do not fit). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepActions(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

/** Opens a link from a card (Checkout, a card's link button, a consent document) in the browser. */
fun openStepLink(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
