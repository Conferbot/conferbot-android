package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.nodes.NodeUIState
import java.util.TimeZone

private val WEEK = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * A month of days to pick from: the days [allowed] says no to are faded and
 * cannot be picked. Pages month by month between [first] and [last] ("YYYY-MM").
 */
@Composable
internal fun MonthPicker(
    style: StepLook,
    month: String,
    onMonth: (String) -> Unit,
    first: String?,
    last: String?,
    picked: String?,
    allowed: (String) -> Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(monthTitle(month), style = style.bold, modifier = Modifier.weight(1f))
            val canBack = first == null || month > first
            val canOn = last == null || month < last
            IconButton(onClick = { onMonth(FlowStepAnswer.addMonths(month, -1)) }, enabled = canBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, t("calendar.prevMonth"), tint = style.text.copy(alpha = if (canBack) 0.8f else 0.25f))
            }
            IconButton(onClick = { onMonth(FlowStepAnswer.addMonths(month, 1)) }, enabled = canOn, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowRight, t("calendar.nextMonth"), tint = style.text.copy(alpha = if (canOn) 0.8f else 0.25f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            I18n.weekdayNames("narrow", WEEK).forEach { Text(it, style = style.hint.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f)) }
        }
        val days = FlowStepAnswer.monthGrid(month.take(4).toInt(), month.substring(5, 7).toInt())
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val ok = allowed(day)
                            val on = day == picked
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(if (on) style.accent else if (ok) style.accent.copy(alpha = 0.07f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(enabled = ok && enabled) { onPick(day) }
                                    .alpha(if (ok) 1f else 0.3f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day.takeLast(2).trimStart('0'),
                                    style = TextStyle(fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) style.ink else style.text)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Times as options to pick, in a box that scrolls when there are many. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TimeOptions(style: StepLook, times: List<String>, label: (String) -> String, picked: String?, enabled: Boolean, taken: Set<String> = emptySet(), onPick: (String) -> Unit) {
    Box(Modifier.heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            times.forEach { tm ->
                // Already booked: shown greyed out, not pickable (web displayStep.ts calendar-node).
                val booked = tm in taken
                val words = label(tm)
                StepOption(
                    words, style,
                    modifier = if (booked) Modifier.alpha(0.45f).semantics { contentDescription = t("sdk.calendar.slotBooked", "time" to words) } else Modifier,
                    picked = tm == picked, enabled = enabled && !booked
                ) { onPick(tm) }
            }
        }
    }
}

// English writes 2:30 PM; the other widget languages the 24-hour 14:30.
private fun clock12(hhmm: String): String = if (I18n.language != I18n.EN) hhmm else runCatching {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "AM" else "PM"}"
}.getOrDefault(hhmm)

/** "October 2026" in the widget's language (English keeps the SDK's own spelling). */
private fun monthTitle(ym: String): String =
    if (I18n.language == I18n.EN) FlowStepAnswer.monthTitle(ym)
    else runCatching { "${I18n.monthNames("long")[ym.substring(5, 7).toInt() - 1]} ${ym.take(4)}" }.getOrDefault(ym)

/** A closed weekday for "Closed on ...": "Sundays" in English, the day's name in the others (web rich.ts weekdaysName). */
private fun closedDayName(wd: Int): String =
    if (I18n.language == I18n.EN) "${FlowCalendar.DAY_NAMES[wd]}s" else I18n.weekdayNames("long")[wd]

/**
 * datetime-node's card (web richNodesView dateTimeCardHtml): a month to
 * pick the day from (minDate / maxDate, closed weekdays faded), the times
 * between minTime and maxTime every timeStep minutes, and Send. Sends
 * {value: "2026-10-19" | "14:30" | "2026-10-19T14:30"}; a refused pick says why.
 */
@Composable
fun DateTimeStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val data = step.node
    val mode = FlowRich.dateMode(data)
    val today = remember { FlowCalendar.dayKeyInZone(System.currentTimeMillis(), TimeZone.getDefault().id) }
    val min = FlowRich.resolveDateBound(data["minDate"], today)
    val max = FlowRich.resolveDateBound(data["maxDate"], today)
    var day by remember(step.visit) { mutableStateOf<String?>(null) }
    var time by remember(step.visit) { mutableStateOf<String?>(null) }
    var month by remember(step.visit) { mutableStateOf((min?.takeIf { it > today } ?: today).take(7)) }
    val closed = FlowRich.disabledWeekdaysOf(data).sorted()
    StepCard(style) {
        StepLabel(t(if (mode == "time") "dates.time" else if (mode == "datetime") "dates.dateTime" else "dates.date"), style)
        if (mode != "time") {
            // Days out of bounds cannot be picked (as the web's date input); a
            // closed weekday can, and the step says why it is refused.
            MonthPicker(style, month, { month = it }, min?.take(7), max?.take(7), day, { FlowStepAnswer.dayInBounds(data, it, today) }, !busy) { day = it }
            if (closed.isNotEmpty()) Text(t("dates.closedOn", mapOf("days" to FlowRich.joinAnd(closed.map { closedDayName(it) }))), style = style.hint)
        }
        if (mode != "date") TimeOptions(style, FlowStepAnswer.timeChoices(data), ::clock12, time, !busy) { time = it }
        StepStatus(style, step.error)
        val value = when (mode) {
            "time" -> time
            "datetime" -> if (day != null && time != null) "${day}T$time" else null
            else -> day
        }
        StepActions {
            StepButton(FlowRich.submitLabel(data), style, primary = true, enabled = !busy && value != null) { value?.let { send(FlowStepAnswer.dateTime(it)) } }
        }
    }
}

/**
 * calendar-node's card (web displayStep calendar): the month with the days
 * that are open, then that day's free times (on the bot's clock) when the
 * step books a time. Sends {date} or {date, slot}.
 */
@Composable
fun CalendarStep(ui: NodeUIState.Calendar, step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val slots = ui.availableSlots.orEmpty()
    val openDays: List<String> = if (ui.showTimeSelection) slots.map { it.date }.distinct() else Js.list(step.extra["days"]).map { Js.str(it) }
    var day by remember(step.visit) { mutableStateOf<String?>(null) }
    var month by remember(step.visit) { mutableStateOf((openDays.firstOrNull() ?: FlowCalendar.dayKeyInZone(System.currentTimeMillis(), ui.timezone ?: "UTC")).take(7)) }
    StepCard(style) {
        MonthPicker(style, month, { month = it }, openDays.firstOrNull()?.take(7), openDays.lastOrNull()?.take(7), day, { it in openDays }, !busy) {
            day = it
            if (!ui.showTimeSelection) send(FlowStepAnswer.calendar(it))
        }
        if (ui.showTimeSelection && day != null) {
            Text(t("calendar.chooseSlot"), style = style.hint)
            val daySlots = slots.filter { it.date == day }
            val taken = daySlots.filter { !it.available }.mapNotNull { it.time }.toSet()
            TimeOptions(style, daySlots.mapNotNull { it.time }, { it }, null, !busy, taken) { send(FlowStepAnswer.calendar(day!!, it)) }
        }
        if (!ui.timezone.isNullOrEmpty() && ui.showTimeSelection) Text(ui.timezone, style = style.hint)
        StepStatus(style, step.error)
    }
}
