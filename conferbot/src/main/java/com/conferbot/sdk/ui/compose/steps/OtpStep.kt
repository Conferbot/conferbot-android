package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.flow.FlowRich.t
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.NodeUIState
import kotlinx.coroutines.delay

/**
 * otp-node's card (web richNodesView otpCardHtml): the e-mail with Send
 * code, then six digit boxes with Verify, Resend after its countdown,
 * Change email and Skip when the step allows it. Sends {action: send,
 * email}, {action: verify, code} (by itself once six digits are in),
 * {action: resend} and {action: skip}; a refusal shows on the card and what
 * was typed stays (a wrong code empties the boxes, as the web).
 */
@Composable
fun OtpStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val stage = Js.str(step.extra["stage"]).ifEmpty { "email" }
    val allowSkip = step.extra["allowSkip"] == true
    val skipLabel = Js.str(step.extra["skipLabel"]).ifEmpty { t("common.skip") }
    var email by remember(step.visit) { mutableStateOf("") }
    var code by remember(step.visit) { mutableStateOf("") }
    var editEmail by remember(step.visit) { mutableStateOf(false) }
    var last by remember(step.visit) { mutableStateOf("") }
    var wrong by remember(step.visit) { mutableStateOf(false) }
    // A new code went out (the step came back at the code stage): fresh boxes, a new countdown.
    val resendIn = (step.extra["resendIn"] as? Number)?.toInt() ?: FlowRich.OTP_RESEND_SECONDS
    var left by remember(step.visit) { mutableIntStateOf(0) }
    val sentAt = if (stage == "code") step else null
    LaunchedEffect(sentAt?.extra, sentAt?.error) {
        if (stage != "code") return@LaunchedEffect
        if (last == "send" || last == "resend" || last == "") {
            editEmail = false
            code = ""
            wrong = false
            left = resendIn
        } else if (last == "verify" && !step.error.isNullOrEmpty()) {
            code = ""
            wrong = true
        }
    }
    LaunchedEffect(left > 0) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    fun go(answer: Map<String, Any?>) {
        if (busy) return
        last = Js.str(answer["action"])
        send(answer)
    }

    StepCard(style) {
        if (stage != "code" || editEmail) {
            StepLabel(t("otp.emailLabel"), style)
            StepField(
                value = email, onValueChange = { email = it }, style = style, placeholder = t("otp.emailPlaceholder"),
                invalid = !step.error.isNullOrEmpty() && (stage == "email" || editEmail), enabled = !busy,
                keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, onDone = { go(FlowStepAnswer.otpSend(email)) }
            )
            StepStatus(style, if (stage == "email") step.error else null, t("otp.willEmail", mapOf("n" to FlowRich.OTP_LENGTH)))
            StepActions {
                if (editEmail) StepButton(t("common.back"), style, enabled = !busy) { editEmail = false }
                else if (allowSkip) StepButton(skipLabel, style, enabled = !busy) { go(FlowStepAnswer.otpSkip) }
                StepButton(t("otp.send"), style, primary = true, enabled = !busy) { go(FlowStepAnswer.otpSend(email)) }
            }
        } else {
            Text(
                t("otp.sentTo", mapOf("n" to FlowRich.OTP_LENGTH, "email" to Js.str(step.extra["sentTo"]).ifEmpty { "***" })),
                style = style.body.copy(fontSize = 14.sp)
            )
            DigitBoxes(code, wrong, style, enabled = !busy) { typed ->
                code = typed
                wrong = false
                if (typed.length == FlowRich.OTP_LENGTH) go(FlowStepAnswer.otpVerify(typed))
            }
            StepStatus(style, step.error, t("otp.checkInbox", mapOf("min" to 10)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepLink(FlowStepAnswer.resendLabel(left), style, enabled = left <= 0 && !busy) { go(FlowStepAnswer.otpResend) }
                Box(Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(style.text.copy(alpha = 0.35f)))
                StepLink(t("otp.changeEmail"), style, enabled = !busy) { editEmail = true }
            }
            StepActions {
                if (allowSkip) StepButton(skipLabel, style, enabled = !busy) { go(FlowStepAnswer.otpSkip) }
                StepButton(t("otp.verify"), style, primary = true, enabled = !busy) { go(FlowStepAnswer.otpVerify(FlowStepAnswer.digitsFromPaste(code))) }
            }
        }
    }
}

/**
 * Six boxes drawing one hidden field: typing, paste, the keyboard's code
 * suggestion and backspace all go to it; the next empty box is ringed.
 */
@Composable
private fun DigitBoxes(code: String, wrong: Boolean, style: StepLook, enabled: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = { onChange(FlowStepAnswer.digitsFromPaste(it)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (code.length == FlowRich.OTP_LENGTH) onChange(code) }),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until FlowRich.OTP_LENGTH) {
                        val current = focused && i == code.length.coerceAtMost(FlowRich.OTP_LENGTH - 1)
                        val line = when {
                            wrong -> StepLook.ERROR_LINE
                            current -> style.accent
                            else -> style.text.copy(alpha = 0.24f)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(style.text.copy(alpha = 0.04f))
                                .border(if (current && !wrong) 1.5.dp else 1.dp, line, RoundedCornerShape(10.dp))
                                .semantics { contentDescription = t("otp.digit", mapOf("n" to i + 1, "total" to FlowRich.OTP_LENGTH)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code.getOrNull(i)?.toString() ?: "", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = style.text))
                        }
                    }
                }
            }
        }
    )
}
