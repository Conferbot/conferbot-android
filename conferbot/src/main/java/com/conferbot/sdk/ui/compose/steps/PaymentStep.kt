package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowRich.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.NodeUIState

/**
 * payment-node's card (web richNodesView paymentCardHtml): what is paid
 * for and how much, "Secure checkout by Stripe", then Cancel, "I've paid"
 * once the Checkout is open, and Pay now, which opens Stripe Checkout in
 * the browser. Sends {action: open | check | cancel}; the step watches for
 * the payment itself and the card says why a check or a start failed.
 */
@Composable
fun PaymentStep(ui: NodeUIState.SingleChoice, step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val context = LocalContext.current
    val amount = Js.str(step.extra["amountText"]).trim()
    val name = Js.str(step.extra["productName"]).trim()
    val desc = Js.str(step.extra["description"]).trim()
    val waiting = step.extra["waiting"] == true
    val url = Js.str(step.extra["checkoutUrl"]).trim()
    val ids = ui.choices.map { it.id }
    StepCard(style) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(style.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = style.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifEmpty { t("pay.payment") }, style = style.bold)
                if (desc.isNotEmpty()) Text(desc, style = TextStyle(fontSize = 12.5.sp, color = style.muted, lineHeight = 17.sp))
            }
            if (amount.isNotEmpty()) Text(amount, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = style.text), modifier = Modifier.padding(start = 8.dp))
        }
        if (waiting) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(style.accent.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(style.accent))
                Spacer(Modifier.width(8.dp))
                Text(t("pay.waiting"), style = TextStyle(fontSize = 13.sp, color = style.text))
            }
        }
        if (!step.error.isNullOrEmpty()) StepStatus(style, step.error)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = style.muted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(t("pay.secure"), style = style.hint)
        }
        StepActions {
            if ("cancel" in ids) StepButton(t("common.cancel"), style, enabled = !busy) { send(FlowStepAnswer.payment("cancel")) }
            if ("check" in ids) StepButton(t("pay.ivePaid"), style, enabled = !busy) { send(FlowStepAnswer.payment("check")) }
            StepButton(Js.str(step.extra["payLabel"]).ifEmpty { t("pay.payNow") }, style, primary = true, enabled = url.isNotEmpty()) {
                openStepLink(context, url)
                send(FlowStepAnswer.payment("open"))
            }
        }
    }
}
