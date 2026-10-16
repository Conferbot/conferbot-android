package com.conferbot.sdk.ui.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.flow.FlowStepCards
import com.conferbot.sdk.core.nodes.NodeUIState

/** Whether [ui] is drawn by a step card here (else the generic node views draw it). */
fun hasStepCard(ui: NodeUIState?): Boolean = FlowStepCards.cardStep(ui) != null

/**
 * The card for the step waiting for the visitor (the web widget's cards:
 * ts/chat/displayStep.ts and richNodesView.ts), chosen by the step's type.
 * Each sends the flow-node spec's answer (core/flow/FlowStepAnswer) through
 * [send] - by default Conferbot.answerStep, so the visitor's bubble is the
 * step's own reply. The bot's question is the line above the card. A card
 * keeps what was entered through a refusal and starts fresh on a new visit.
 */
@Composable
fun FlowStepView(ui: NodeUIState, send: (Any) -> Unit = { Conferbot.answerStep(it) }) {
    val step = FlowStepCards.cardStep(ui) ?: return
    val style = rememberStepLook()
    val processing by (Conferbot.flowEngine?.isProcessing?.collectAsState() ?: remember { mutableStateOf(false) })
    key(step.visit) {
        when (step.type) {
            "consent-node" -> ConsentStep(step, style, processing, send)
            "otp-node" -> OtpStep(step, style, processing, send)
            "address-node" -> AddressStep(step, style, processing, send)
            "signature-node" -> SignatureStep(step, style, processing, send)
            "matrix-node" -> MatrixStep(step, style, processing, send)
            "ranking-node" -> RankingStep(step, style, processing, send)
            "slider-node" -> SliderStep(step, style, processing, send)
            "datetime-node" -> DateTimeStep(step, style, processing, send)
            "calendar-node" -> (ui as? NodeUIState.Calendar)?.let { CalendarStep(it, step, style, processing, send) }
            "payment-node" -> (ui as? NodeUIState.SingleChoice)?.let { PaymentStep(it, step, style, processing, send) }
            "carousel-node", "product-card-node" -> CardsStep(step, style, processing, send)
            "navigate-node" -> (ui as? NodeUIState.SingleChoice)?.let { NavigateStep(it, style, processing, send) }
            else -> Unit
        }
    }
}
