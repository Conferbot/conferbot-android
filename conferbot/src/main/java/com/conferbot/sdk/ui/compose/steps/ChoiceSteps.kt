package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.MatrixPicks
import com.conferbot.sdk.core.flow.RankingOrder
import com.conferbot.sdk.core.nodes.NodeUIState

/**
 * slider-node's card (web richNodesView sliderCardHtml): the value as it
 * will be saved (snapped to the step, with its unit), the slider between
 * min and max, and Send. Sends {value}.
 */
@Composable
fun SliderStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val cfg = FlowRich.sliderConfig(step.node)
    var value by remember(step.visit) { mutableFloatStateOf(cfg.start.toFloat()) }
    val snapped = FlowRich.snapToStep(value.toDouble(), cfg.min, cfg.max, cfg.step)
    val steps = Math.max(0, Math.round((cfg.max - cfg.min) / cfg.step).toInt() - 1)
    StepCard(style) {
        Text(
            FlowRich.sliderLabel(Js.tidy(snapped), cfg),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = style.text, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = cfg.min.toFloat()..cfg.max.toFloat(),
            // Many steps draw as a smooth track; the value snaps either way.
            steps = if (steps in 1..20) steps else 0,
            enabled = !busy,
            colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent, inactiveTrackColor = style.accent.copy(alpha = 0.18f))
        )
        Row(Modifier.fillMaxWidth()) {
            Text(FlowRich.sliderLabel(Js.tidy(cfg.min), cfg), style = style.hint, modifier = Modifier.weight(1f))
            Text(FlowRich.sliderLabel(Js.tidy(cfg.max), cfg), style = style.hint)
        }
        StepStatus(style, step.error)
        StepActions { StepButton(FlowRich.submitLabel(step.node), style, primary = true, enabled = !busy) { send(FlowStepAnswer.value(snapped)) } }
    }
}

/**
 * ranking-node's card (web richNodesView rankingCardHtml): the items
 * numbered in order, each moved up or down with its arrows, and Send.
 * Sends {order: [ids]} best first.
 */
@Composable
fun RankingStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val items = FlowRich.rankingItems(step.node)
    var order by remember(step.visit) { mutableStateOf(RankingOrder(items.map { it.id })) }
    StepCard(style) {
        Text(t("ranking.help"), style = style.hint)
        order.ids.forEachIndexed { i, id ->
            val item = items.firstOrNull { it.id == id } ?: return@forEachIndexed
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(style.radius)).background(style.option).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = style.optionInk.copy(alpha = 0.7f)), modifier = Modifier.width(20.dp))
                Text(item.text, style = TextStyle(fontSize = style.fontSize, color = style.optionInk), modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                IconButton(onClick = { order = order.move(id, -1) }, enabled = i > 0 && !busy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, t("ranking.moveUp", mapOf("item" to item.text)), tint = style.optionInk.copy(alpha = if (i > 0) 0.9f else 0.25f))
                }
                IconButton(onClick = { order = order.move(id, 1) }, enabled = i < order.ids.size - 1 && !busy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, t("ranking.moveDown", mapOf("item" to item.text)), tint = style.optionInk.copy(alpha = if (i < order.ids.size - 1) 0.9f else 0.25f))
                }
            }
        }
        StepStatus(style, step.error)
        StepActions { StepButton(FlowRich.submitLabel(step.node), style, primary = true, enabled = !busy) { send(FlowStepAnswer.ranking(order.ids)) } }
    }
}

/**
 * matrix-node's card (web richNodesView matrixCardHtml): a grid, a row per
 * question and a column per answer, one pick per row (single: round marks)
 * or several (multiple: square ticks); how many rows are answered; Send.
 * Sends {cells: {row: col | [cols]}}; a refusal ("Answer every row") keeps
 * the picks.
 */
@Composable
fun MatrixStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val rows = FlowRich.matrixRows(step.node)
    val cols = FlowRich.matrixColumns(step.node)
    val multiple = FlowRich.matrixMode(step.node) == "multiple"
    var picks by remember(step.visit) { mutableStateOf(MatrixPicks.of(step.node)) }
    val rowLabel = 96.dp
    StepCard(style) {
        Text(t(if (multiple) "sdk.matrix.pickAny" else "matrix.rateEach"), style = style.hint)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // The columns share the width; past seven narrow ones the grid scrolls.
            val cell = ((maxWidth - rowLabel) / Math.max(1, cols.size)).coerceIn(44.dp, 72.dp)
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(rowLabel)) {
                    Spacer(Modifier.height(36.dp))
                    rows.forEach { r -> Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) { Text(r.text, style = TextStyle(fontSize = 13.sp, color = style.text, lineHeight = 16.sp), maxLines = 2) } }
                }
                Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Row {
                        cols.forEach { c ->
                            Box(Modifier.width(cell).height(36.dp), contentAlignment = Alignment.BottomCenter) {
                                Text(c.text, style = TextStyle(fontSize = 11.5.sp, color = style.muted, textAlign = TextAlign.Center, lineHeight = 14.sp), maxLines = 2)
                            }
                        }
                    }
                    rows.forEach { r ->
                        Row {
                            cols.forEach { c ->
                                val on = picks.isOn(r.id, c.id)
                                Box(
                                    Modifier.width(cell).height(44.dp).clickable(enabled = !busy) { picks = picks.tap(r.id, c.id) }
                                        .semantics { contentDescription = "${r.text}: ${c.text}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val shape = if (multiple) RoundedCornerShape(5.dp) else CircleShape
                                    Box(
                                        Modifier.size(22.dp).clip(shape)
                                            .background(if (on) style.accent else androidx.compose.ui.graphics.Color.Transparent)
                                            .border(1.5.dp, if (on) style.accent else style.text.copy(alpha = 0.3f), shape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (on && multiple) Icon(Icons.Filled.Check, null, tint = style.ink, modifier = Modifier.size(15.dp))
                                        else if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(style.ink))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        StepStatus(style, step.error, t("matrix.progress", mapOf("n" to picks.answered, "total" to rows.size)))
        StepActions { StepButton(FlowRich.submitLabel(step.node), style, primary = true, enabled = !busy) { send(FlowStepAnswer.matrix(picks)) } }
    }
}
