package com.conferbot.sdk.ui.compose.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.conferbot.sdk.core.home.HomeView
import com.conferbot.sdk.core.home.ViewTransition

/**
 * The open chat's views - home, conversation, help centre - and how the new
 * one comes in: [ViewTransition.motion] from the owner's viewTransition /
 * viewTransitionDirection (web ts/layout/viewTransition.ts; RN
 * components/Home/ViewSwitch.tsx). Played once per switch, in
 * `graphicsLayer` (no recomposition per frame); only the new view moves, as
 * a native view has no ghost of the old one. With the animator duration
 * scale at 0 it is the web's reduced motion: a 200ms fade.
 */
@Composable
fun ViewSwitch(
    view: HomeView,
    kind: Any?,
    sweep: Any?,
    modifier: Modifier = Modifier,
    content: @Composable (HomeView) -> Unit
) {
    val context = LocalContext.current
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    // Decided while drawing, so the new view's first frame is already where it starts.
    val last = remember { Switch(view) }
    if (last.shown != view) {
        last.motion = ViewTransition.motion(kind, sweep, last.shown.key, view.key, reduced)
        last.shown = view
    }

    Box(modifier.fillMaxSize().clipToBounds()) {
        key(view) {
            val mo = last.motion
            val v = remember { Animatable(if (mo == ViewTransition.Motion.None) 1f else 0f) }
            LaunchedEffect(Unit) {
                when (mo) {
                    is ViewTransition.Motion.Reduced -> v.animateTo(1f, tween(mo.durationMs, easing = LinearEasing))
                    is ViewTransition.Motion.Fade -> v.animateTo(
                        1f, tween(mo.durationMs, delayMillis = mo.delayMs, easing = CubicBezierEasing(ViewTransition.EASE[0], ViewTransition.EASE[1], ViewTransition.EASE[2], ViewTransition.EASE[3]))
                    )
                    is ViewTransition.Motion.Slide -> v.animateTo(
                        1f, tween(mo.durationMs, easing = CubicBezierEasing(ViewTransition.EASE_IO[0], ViewTransition.EASE_IO[1], ViewTransition.EASE_IO[2], ViewTransition.EASE_IO[3]))
                    )
                    ViewTransition.Motion.None -> {}
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = v.value
                        when (mo) {
                            is ViewTransition.Motion.Reduced -> alpha = p
                            is ViewTransition.Motion.Fade -> {
                                alpha = p
                                val s = mo.scaleFrom + (1f - mo.scaleFrom) * p
                                scaleX = s
                                scaleY = s
                            }
                            is ViewTransition.Motion.Slide -> {
                                translationX = mo.dx * size.width * (1f - p)
                                translationY = mo.dy * size.height * (1f - p)
                            }
                            ViewTransition.Motion.None -> {}
                        }
                    }
            ) { content(view) }
        }
    }
}

/** The view last drawn and how the current one came in (not state: read only while drawing). */
private class Switch(var shown: HomeView, var motion: ViewTransition.Motion = ViewTransition.Motion.None)
