package com.conferbot.sdk.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Placeholder citation markers in AI answers (#102), the cases of
 * embed-server utils/citationMarkers.js stripPlaceholderMarkers.
 */
class CitationMarkersTest {
    private fun strip(s: String) = CitationMarkers.stripPlaceholderMarkers(s)

    @Test
    fun `placeholders go, with the space before them`() {
        assertThat(strip("Wie Sie zit. [n] Als Kunde")).isEqualTo("Wie Sie zit. Als Kunde")
        assertThat(strip("zit. [n] Als")).isEqualTo("zit. Als")
        assertThat(strip("Plans start at \$9 [1, n].")).isEqualTo("Plans start at \$9.")
        assertThat(strip("Yes [N].")).isEqualTo("Yes.")
        assertThat(strip("See the guide [source].")).isEqualTo("See the guide.")
        assertThat(strip("Open daily [web:2], weekends too")).isEqualTo("Open daily, weekends too")
    }

    @Test
    fun `numbers, words, links and checkboxes stay`() {
        assertThat(strip("[Klik hier]")).isEqualTo("[Klik hier]")
        assertThat(strip("See step [2].")).isEqualTo("See step [2].")
        assertThat(strip("[n](https://x)")).isEqualTo("[n](https://x)")
        assertThat(strip("- [x] done")).isEqualTo("- [x] done")
        assertThat(strip("Nothing here . really")).isEqualTo("Nothing here . really")
    }
}
