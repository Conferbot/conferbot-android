package com.conferbot.sdk.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** initiate-handover carries the step's settings (#59), as React Native's handoverTimeout.test.ts. */
class HandoverPayloadTest {

    @Test
    fun `an email turned off on the step stays off, sound and skills go too`() {
        val p = HandoverPayload.stepSettings(
            mapOf(
                "enableHandoverEmail" to false,
                "enableHandoverSound" to true,
                "checkedSoundTab" to "default",
                "selectedSound" to "ding",
                "assignmentMethod" to "skills",
                "requiredSkills" to listOf("billing"),
                "maxWaitTime" to 2
            )
        )
        assertThat(p["enableHandoverEmail"]).isEqualTo(false)
        assertThat(p["enableHandoverSound"]).isEqualTo(true)
        assertThat(p["checkedSoundTab"]).isEqualTo("default")
        assertThat(p["selectedSound"]).isEqualTo("ding")
        assertThat(p["assignmentMethod"]).isEqualTo("skills")
        assertThat(p["requiredSkills"]).isEqualTo(listOf("billing"))
        assertThat(p).doesNotContainKey("maxWaitTime")
    }

    @Test
    fun `a step that follows the workspace sends no assignment overrides`() {
        val p = HandoverPayload.stepSettings(mapOf("assignmentMethod" to "workspace", "requiredSkills" to emptyList<String>()))
        assertThat(p).doesNotContainKey("assignmentMethod")
        assertThat(p).doesNotContainKey("requiredSkills")
        // A setting the step never had is not sent at all (as JSON drops undefined).
        assertThat(p).doesNotContainKey("enableHandoverEmail")
    }

    @Test
    fun `max wait reads minutes as the builder wrote them`() {
        assertThat(HandoverPayload.waitMinutesOf("0.5")).isEqualTo(0.5)
        assertThat(HandoverPayload.waitMinutesOf("3")).isEqualTo(3)
        assertThat(HandoverPayload.waitMinutesOf(null)).isEqualTo(2)
    }
}
