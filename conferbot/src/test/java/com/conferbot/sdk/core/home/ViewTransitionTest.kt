package com.conferbot.sdk.core.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The owner's view transition between home, chat and help centre
 * (core/home/ViewTransition.kt): the React Native SDK's
 * __tests__/core/viewTransition.test.ts (web ts/layout/viewTransition.ts).
 */
class ViewTransitionTest {

    @Test
    fun `unset is the honeycomb, unknown values too`() {
        assertThat(ViewTransition.normalize(null)).isEqualTo("honeycomb")
        assertThat(ViewTransition.normalize("nope")).isEqualTo("honeycomb")
        assertThat(ViewTransition.normalize("fade")).isEqualTo("fade")
        assertThat(ViewTransition.normalizeDirection("x")).isEqualTo("auto")
    }

    @Test
    fun `deeper views are forward, coming back reverses the sweep`() {
        assertThat(ViewTransition.isForward("home", "chat")).isTrue()
        assertThat(ViewTransition.isForward("chat", "home")).isFalse()
        assertThat(ViewTransition.isForward("home", "help")).isTrue()
        assertThat(ViewTransition.isForward("help", "home")).isFalse()
        assertThat(ViewTransition.sweepVector("auto", 1)).isEqualTo(1 to 0)
        assertThat(ViewTransition.sweepVector("auto", -1)).isEqualTo(-1 to 0)
        assertThat(ViewTransition.sweepVector("rtl", 1)).isEqualTo(-1 to 0)
        assertThat(ViewTransition.sweepVector("ttb", -1)).isEqualTo(0 to -1)
    }

    @Test
    fun `classic and none switch at once, the same view does not move`() {
        assertThat(ViewTransition.motion("classic", "auto", "home", "chat")).isEqualTo(ViewTransition.Motion.None)
        assertThat(ViewTransition.motion("none", "auto", "home", "chat")).isEqualTo(ViewTransition.Motion.None)
        assertThat(ViewTransition.motion("fade", "auto", "chat", "chat")).isEqualTo(ViewTransition.Motion.None)
    }

    @Test
    fun `fade and slide are the web's, the rest slide, reduced motion is a short fade`() {
        assertThat(ViewTransition.motion("fade", "auto", "home", "chat")).isEqualTo(ViewTransition.Motion.Fade(340, 40, 1.035f))
        assertThat(ViewTransition.motion("slide", "auto", "home", "chat")).isEqualTo(ViewTransition.Motion.Slide(420, 1, 0))
        assertThat(ViewTransition.motion("slide", "auto", "chat", "home")).isEqualTo(ViewTransition.Motion.Slide(420, -1, 0))
        assertThat(ViewTransition.motion(null, "btt", "home", "chat")).isEqualTo(ViewTransition.Motion.Slide(420, 0, -1))
        assertThat(ViewTransition.motion("iris", "auto", "home", "help", reduced = true)).isEqualTo(ViewTransition.Motion.Reduced(200))
        assertThat(ViewTransition.motion("classic", "auto", "home", "help", reduced = true)).isEqualTo(ViewTransition.Motion.None)
    }
}
