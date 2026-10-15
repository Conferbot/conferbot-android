package com.conferbot.sdk.ui.compose

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.conferbot.sdk.core.customization.LauncherIcons
import com.conferbot.sdk.core.customization.LauncherMotion
import kotlinx.coroutines.delay

/** When the chat was last opened in this app session: idle moments rest a while after. */
internal object LauncherOpened {
    @Volatile var at = 0L
}

internal class LauncherMotionState {
    var frame by mutableStateOf<List<LauncherMotion.OpFrame?>?>(null)
    var rings by mutableStateOf<List<LauncherMotion.Ring?>>(listOf(null, null))
}

/**
 * The launcher's motion, as the web plays it (core LauncherMotion): an idle
 * moment ~2s after it shows and a few more after that ("subtle" / "lively"),
 * a reaction when a message arrives while the chat is closed, nothing while
 * it is open, after it was opened, for "off", or with animations turned off
 * on the phone. Also the ripple rings: three times after the launcher
 * settles, and once more for a new message.
 */
@Composable
internal fun rememberLauncherMotion(iconId: String?, mode: String, chatOpen: Boolean, unreadCount: Int, enabled: Boolean): LauncherMotionState {
    val state = remember { LauncherMotionState() }
    val context = LocalContext.current
    // The phone's "remove animations" (animator scale 0) is the reduced-motion setting.
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val still = !enabled || reduced
    val open by rememberUpdatedState(chatOpen)
    var moment by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var ripple by remember { mutableStateOf<Triple<Long, Double, Int>?>(null) }
    var played by remember { mutableStateOf(0) }

    // The ripple after the launcher settles, whatever the motion mode.
    LaunchedEffect(still) {
        if (!still) ripple = Triple(System.currentTimeMillis(), LauncherMotion.RIPPLE_DELAY, LauncherMotion.RIPPLE_ITER)
    }

    // Idle moments: the first ~2s in, then the plan's pace, never after the chat was opened.
    LaunchedEffect(still, mode, chatOpen, iconId) {
        if (still || mode == "off" || chatOpen) return@LaunchedEffect
        while (true) {
            val wait = LauncherMotion.nextIdleDelay(mode, played) ?: break
            delay(wait)
            if (LauncherOpened.at != 0L && System.currentTimeMillis() - LauncherOpened.at < LauncherMotion.OPENED_REST_MS) break
            played += 1
            moment = "idle" to System.currentTimeMillis()
        }
    }

    // A message while the chat is closed: the icon reacts and the ripple plays once more.
    var lastUnread by remember { mutableStateOf(unreadCount) }
    LaunchedEffect(unreadCount) {
        if (unreadCount > lastUnread && !chatOpen && !still) {
            if (mode != "off") moment = "msg" to System.currentTimeMillis()
            ripple = Triple(System.currentTimeMillis(), 0.0, 1)
        }
        lastUnread = unreadCount
    }

    // One clock for the moment and the ripple; it stops when both are done.
    LaunchedEffect(moment, ripple) {
        if (moment == null && ripple == null) return@LaunchedEffect
        val icon = LauncherIcons.byId(iconId)
        while (moment != null || ripple != null) {
            withFrameMillis { }
            val now = System.currentTimeMillis()
            val m = moment
            if (m != null) {
                if (now - m.second < LauncherMotion.MOMENT_MS && !open) {
                    state.frame = LauncherMotion.iconFrame(icon, m.first, (now - m.second) / 1000.0)
                } else {
                    moment = null
                    state.frame = null
                }
            }
            val r = ripple
            if (r != null) {
                val t = (now - r.first) / 1000.0
                if (open || t > r.second + LauncherMotion.RIPPLE_GAP + LauncherMotion.RIPPLE_DUR * r.third) {
                    ripple = null
                    state.rings = listOf(null, null)
                } else {
                    state.rings = listOf(LauncherMotion.rippleRing(t, 0, r.third, r.second), LauncherMotion.rippleRing(t, 1, r.third, r.second))
                }
            }
        }
    }
    return state
}
