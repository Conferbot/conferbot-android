package com.conferbot.sdk.ui.compose

import androidx.compose.ui.graphics.Color
import com.conferbot.sdk.core.customization.WidgetTheme

/** A colour from the bot's settings or theme ("#rgb", "#rrggbb", rgb(a)), or null. */
internal fun lookColor(value: String?): Color? = WidgetTheme.parseColor(value)?.let {
    Color(red = (it.r / 255).toFloat(), green = (it.g / 255).toFloat(), blue = (it.b / 255).toFloat(), alpha = it.a.toFloat())
}
