package com.conferbot.sdk.ui.compose.mascot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.customization.LauncherMascotLook
import com.conferbot.sdk.core.customization.MascotMoods
import com.conferbot.sdk.ui.compose.lookColor
import kotlinx.coroutines.delay

/**
 * The soft light button behind the launcher's mascot (launcherStyle.ts): white
 * at 35% 30%, to the launcher colour at 14% on white at its edge.
 */
internal fun mascotButtonBrush(light: String, w: Float, h: Float): Brush =
    Brush.radialGradient(
        listOf(Color.White, lookColor(light) ?: Color(0xFFDFE7FD)),
        center = Offset(w * 0.35f, h * 0.3f),
        // The farthest corner from 35% 30% (objectBoundingBox r 0.955 of a square).
        radius = maxOf(w, h) * 0.955f,
    )

/**
 * The "mascot" launcher style (launcherStyle.ts, RN LauncherMascot.tsx): the
 * owner's mascot, its bust at `scale`% of the button's short side, centred
 * and lifted (translate -50% -56%) so its ears and hat peek over the rim,
 * blinking now and then. A reply landing while the chat is closed (the
 * unread count rising) makes it hop for joy and smile for 2.4s, then it
 * blinks again; a reply mid-hop starts it again. Reduced motion keeps only
 * the smile. Drawn over the button, not clipped by it, and never taking a tap.
 */
@Composable
internal fun LauncherMascot(
    m: LauncherMascotLook,
    boxW: Float,
    boxH: Float,
    unread: Int,
    chatOpen: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val n = minOf(boxW, boxH) * m.scale / 100f
    val layers = rememberMascotLayers(m.accent, "down", m.look, bust = true)
    var cheer by remember { mutableIntStateOf(0) }
    var last by remember { mutableIntStateOf(unread) }
    LaunchedEffect(unread, chatOpen) {
        val rose = unread > last
        last = unread
        if (rose && !chatOpen) cheer += 1
    }
    var happy by remember { mutableStateOf(false) }
    LaunchedEffect(cheer) {
        if (cheer == 0) return@LaunchedEffect
        // A fresh mood (the hop starts again), then back to blinking.
        happy = false
        delay(16)
        happy = true
        delay(MascotMoods.CHEER_MS)
        happy = false
    }
    Box(modifier.size(boxW.dp, boxH.dp)) {
        MascotMoodFigure(
            layers = layers,
            look = m.look,
            size = n,
            mood = if (happy) "happy" else "idle",
            bust = true,
            reduced = reduced,
            enabled = !chatOpen,
            modifier = Modifier.offset((boxW / 2 - n / 2).dp, (boxH / 2 - n * 0.56f).dp),
        )
    }
}
