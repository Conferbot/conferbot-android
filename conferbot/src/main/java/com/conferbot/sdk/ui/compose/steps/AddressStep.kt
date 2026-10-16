package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowRich.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.NodeUIState
import kotlinx.coroutines.delay

/** Search after the visitor stops typing for this long (web ADDRESS_DEBOUNCE_MS). */
private const val ADDRESS_DEBOUNCE_MS = 300L
private const val MIN_CHARS = 3

/** [text] with the words typed (two letters or more) in bold. */
internal fun highlightMatch(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val words = query.lowercase().split(Regex("[\\s,]+")).filter { it.length >= 2 }.map { Regex.escape(it) }
    if (words.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var at = 0
    for (m in Regex("(${words.joinToString("|")})", RegexOption.IGNORE_CASE).findAll(text)) {
        append(text.substring(at, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.value) }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/**
 * address-node's card (web richNodesView addressCardHtml): a search with
 * suggestions as the visitor types (their words in bold), a picked address
 * to confirm, or the address typed by hand. Sends {action: search, q},
 * {action: pick, index} (on Confirm) and {action: manual, line1, city,
 * region, postal, country}; the step searches on the server and the card
 * says why one was refused, keeping what was typed.
 */
@Composable
fun AddressStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val results = Js.list(step.extra["results"]).map { Js.str(it) }
    val allowManual = step.extra["allowManual"] != false
    val placeholder = Js.str(step.extra["placeholder"]).ifEmpty { t("address.placeholder") }
    var query by remember(step.visit) { mutableStateOf("") }
    var asked by remember(step.visit) { mutableStateOf("") }
    var picked by remember(step.visit) { mutableStateOf<Int?>(null) }
    var manual by remember(step.visit) { mutableStateOf(false) }
    val fields = remember(step.visit) { mutableStateMapOf("line1" to "", "city" to "", "region" to "", "postal" to "", "country" to "") }

    // Suggestions as the visitor types.
    LaunchedEffect(query, manual) {
        val q = query.trim()
        if (manual || q.length < MIN_CHARS || q == asked) return@LaunchedEffect
        delay(ADDRESS_DEBOUNCE_MS)
        asked = q
        picked = null
        send(FlowStepAnswer.addressSearch(q))
    }

    StepCard(style) {
        when {
            manual -> {
                listOf("line1" to t("address.street"), "city" to t("address.city"), "region" to t("address.region"), "postal" to t("address.postal"), "country" to t("address.country"))
                    .forEach { (k, label) ->
                        StepLabel(if (k == "line1") label else "$label ${t("common.optional")}", style)
                        StepField(
                            value = fields[k] ?: "", onValueChange = { fields[k] = it }, style = style, enabled = !busy,
                            invalid = k == "line1" && !step.error.isNullOrEmpty(), imeAction = if (k == "country") ImeAction.Done else ImeAction.Next
                        )
                    }
                StepStatus(style, step.error)
                StepActions {
                    StepButton(t("common.back"), style, enabled = !busy) { manual = false }
                    StepButton(t("action.confirm"), style, primary = true, enabled = !busy && (fields["line1"] ?: "").isNotBlank()) {
                        send(FlowStepAnswer.addressManual(fields["line1"] ?: "", fields["city"] ?: "", fields["region"] ?: "", fields["postal"] ?: "", fields["country"] ?: ""))
                    }
                }
            }
            picked != null && results.getOrNull(picked!!) != null -> {
                val i = picked!!
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = style.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(results[i], style = style.body)
                }
                StepStatus(style, step.error)
                StepActions {
                    StepButton(t("common.change"), style, enabled = !busy) { picked = null }
                    StepButton(t("action.confirm"), style, primary = true, enabled = !busy) { send(FlowStepAnswer.addressPick(i)) }
                }
            }
            else -> {
                StepLabel(t("address.label"), style)
                StepField(
                    value = query, onValueChange = { query = it }, style = style, placeholder = placeholder, enabled = true,
                    invalid = !step.error.isNullOrEmpty(), imeAction = ImeAction.Search,
                    onDone = { if (query.trim().length >= MIN_CHARS) { asked = query.trim(); send(FlowStepAnswer.addressSearch(query.trim())) } }
                )
                val searching = busy && asked.isNotEmpty()
                when {
                    searching -> Text(t("address.searching"), style = style.hint)
                    results.isNotEmpty() && asked.isNotEmpty() -> Column(Modifier.clip(RoundedCornerShape(10.dp)).background(style.text.copy(alpha = 0.03f))) {
                        results.forEachIndexed { i, r ->
                            if (i > 0) Divider(color = style.line)
                            Row(
                                Modifier.fillMaxWidth().clickable { picked = i }.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = style.muted, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(highlightMatch(r, asked), style = style.body.copy(fontSize = 14.sp))
                            }
                        }
                    }
                    else -> StepStatus(style, step.error)
                }
                if (allowManual) StepLink(t("address.manual"), style, enabled = !busy) {
                    fields["line1"] = fields["line1"].orEmpty().ifEmpty { query.trim() }
                    manual = true
                }
            }
        }
    }
}
