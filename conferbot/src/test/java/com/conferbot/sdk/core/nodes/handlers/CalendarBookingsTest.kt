package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFlowContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * calendar-node's booked slots (RN __tests__/core/AdvancedInputHandlers.test.ts
 * "calendar-node booked slots"; web displayStep.ts daySlotsFor, nodeLogic.ts
 * isDayFullyBooked): taken slots shown and refused, a fully booked day not
 * offered, bookings read on the bot's clock for this node only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarBookingsTest {
    private val ctx = TestFlowContext(nodeId = "cal")
    private val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val data = mapOf(
        "timeZone" to "Asia/Dubai",
        "showTimeSelection" to true,
        "slotDuration" to 30,
        "weeklyHours" to days.map { mapOf("dayName" to it, "available" to true, "slots" to listOf(mapOf("start" to "09:00", "end" to "10:00"))) },
        "answerVariable" to "meeting"
    )

    /** A day n days ahead, on the bot's clock. */
    private fun ahead(n: Int) = FlowCalendar.dayKeyInZone(ctx.clock + n * 86_400_000L, "Asia/Dubai")
    private fun bookingOn(day: String, slot: String, nodeId: String? = null): Map<String, Any?> =
        mapOf("date" to "${day}T08:00:00.000Z", "timeSlotSelected" to slot) + (if (nodeId != null) mapOf("nodeId" to nodeId) else emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ChatState.reset()
        ChatState.initialize("s", "v", "b", null)
        ChatState.registerAnswerVariable("cal", "meeting")
    }

    @After
    fun tearDown() {
        ChatState.reset()
        Dispatchers.resetMain()
    }

    private fun ui(d: Map<String, Any?> = data) = (ctx.process(CalendarNodeHandler(), d) as NodeResult.DisplayUI).uiState as NodeUIState.Calendar

    @Test
    fun `a taken slot shown as booked and refused - another node's booking does not count`() {
        val day = ahead(3)
        ctx.bookings = listOf(bookingOn(day, "09:00 - 09:30"), bookingOn(day, "09:30 - 10:00", nodeId = "other"))
        val slots = ui().availableSlots!!.filter { it.date == day }
        assertThat(slots.map { it.time to it.available }).containsExactly("09:00" to false, "09:30" to true).inOrder()
        assertThat(ctx.respond(CalendarNodeHandler(), data, mapOf("date" to day, "slot" to "09:00"))).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat(ChatState.getAnswer("meeting")).isNull()
        assertThat(ctx.respond(CalendarNodeHandler(), data, mapOf("date" to day, "slot" to "09:30"))).isEqualTo(NodeResult.DelayedProceed(400))
    }

    @Test
    fun `a day with every slot taken is left out - the node's own bookings count too`() {
        val day = ahead(4)
        ctx.bookings = listOf(bookingOn(day, "09:00 - 09:30", nodeId = "cal"))
        val slots = ui(data + ("bookings" to listOf(bookingOn(day, "09:30 - 10:00")))).availableSlots!!
        assertThat(slots.any { it.date == day }).isFalse()
        assertThat(slots.any { it.date == ahead(5) }).isTrue()
    }

    @Test
    fun `bookings read on the bot's clock, for this node only`() {
        val bookings = listOf(
            mapOf("date" to "2026-11-02T21:30:00.000Z", "timeSlotSelected" to "09:00 - 09:30"), // 01:30 on the 3rd in Dubai
            mapOf("date" to "2026-11-03T08:00:00.000Z", "timeSlotSelected" to "10:00 - 10:30", "nodeId" to "cal"),
            mapOf("date" to "2026-11-03T08:00:00.000Z", "timeSlotSelected" to "11:00 - 11:30", "nodeId" to "x"),
            null
        )
        assertThat(FlowCalendar.bookedLabels(bookings, "cal", null, "2026-11-03", "Asia/Dubai")).containsExactly("09:00 - 09:30", "10:00 - 10:30").inOrder()
        assertThat(FlowCalendar.bookedLabels(bookings, "cal", null, "2026-11-02", "UTC")).containsExactly("09:00 - 09:30")
        assertThat(FlowCalendar.isDayFullyBooked(listOf(FlowCalendar.Slot("09:00", "09:30")), listOf("09:00 - 09:30"))).isTrue()
        assertThat(FlowCalendar.isDayFullyBooked(emptyList(), emptyList())).isFalse()
    }
}
