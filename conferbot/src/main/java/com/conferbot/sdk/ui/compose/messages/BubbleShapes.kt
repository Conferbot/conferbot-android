package com.conferbot.sdk.ui.compose.messages

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.customization.Corners
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Bubble corners as the web widget draws them: 18/18/18 with a 6 tail on
 * the speaker's side, or the owner's own radius on every corner
 * (core/customization). Before the bot's settings arrive, the host app's
 * theme radius with a square tail, as before.
 */
private fun Corners.shape() = RoundedCornerShape(
    topStart = topLeft.dp, topEnd = topRight.dp, bottomEnd = bottomRight.dp, bottomStart = bottomLeft.dp
)

@Composable
fun botBubbleShape(): Shape {
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    val r = ConferbotThemeAmbient.current.shapes.bubbleRadius
    return if (map.isNotEmpty()) look.botBubble.shape() else RoundedCornerShape(topStart = r, topEnd = r, bottomStart = 0.dp, bottomEnd = r)
}

@Composable
fun userBubbleShape(): Shape {
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    val r = ConferbotThemeAmbient.current.shapes.bubbleRadius
    return if (map.isNotEmpty()) look.userBubble.shape() else RoundedCornerShape(topStart = r, topEnd = r, bottomStart = r, bottomEnd = 0.dp)
}

/** Choice buttons: the owner's radius (10 when unset, as the web). Before the bot's settings arrive, 12. */
@Composable
fun optionShape(): Shape {
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    return RoundedCornerShape(if (map.isNotEmpty()) look.optionRadius.dp else 12.dp)
}
