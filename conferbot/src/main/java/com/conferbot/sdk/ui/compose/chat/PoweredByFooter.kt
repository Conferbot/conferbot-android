package com.conferbot.sdk.ui.compose.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.conferbot.sdk.core.ServerChatbotCustomization

private const val CONFERBOT_URL = "https://www.conferbot.com"
private const val CONFERBOT_LOGO_URL =
    "https://prd.media.cdn.conferbot.com/62829a1c49f355163dfdbfb2/conferbot-logo-1710782074234.png"

/**
 * "Powered by [Conferbot Logo]" footer matching the web widget.
 *
 * Shows the actual Conferbot logo image loaded from CDN, just like the web widget.
 * Tapping opens the Conferbot website. Hidden when `hideBrand` is true.
 *
 * Web widget reference:
 * - Logo: 22px height, margin-bottom: -5px
 * - Text: "Powered by", color rgb(86 89 91), font-weight 600, font-size 14px
 */
@Composable
fun PoweredByFooter(
    serverCustomization: ServerChatbotCustomization?,
    modifier: Modifier = Modifier
) {
    if (serverCustomization?.hideBrand == true) return

    val context = LocalContext.current
    val isCustom = serverCustomization?.enableCustomBrand == true &&
            !serverCustomization.customBrand.isNullOrBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CONFERBOT_URL))
                context.startActivity(intent)
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCustom) {
            Text(
                text = serverCustomization!!.customBrand!!,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF687882)
                )
            )
        } else {
            // "Powered by [LOGO]" — matching the web widget exactly
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Powered by ",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF56595B) // rgb(86 89 91) from web widget
                    )
                )
                // Actual Conferbot logo from CDN — same image the web widget uses
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(CONFERBOT_LOGO_URL)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Conferbot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(18.dp) // ~22px web, scaled for mobile density
                )
            }
        }
    }
}
