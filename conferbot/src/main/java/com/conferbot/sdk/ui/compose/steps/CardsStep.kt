package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.nodes.handlers.CAROUSEL_CONTINUE
import com.conferbot.sdk.core.nodes.handlers.CarouselCard
import com.conferbot.sdk.core.nodes.handlers.FlowKit
import com.conferbot.sdk.core.nodes.handlers.carouselCards

/** A button on a card: a path button in the option colours, a link button marked as one. */
@Composable
private fun CardButton(label: String, link: Boolean, style: StepLook, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(style.radius),
        color = if (link) androidx.compose.ui.graphics.Color.Transparent else style.option,
        contentColor = if (link) style.accent else style.optionInk,
        border = if (link) androidx.compose.foundation.BorderStroke(1.dp, style.accent.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(fontSize = style.fontSize, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (link) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun OneCard(card: CarouselCard, style: StepLook, busy: Boolean, width: androidx.compose.ui.unit.Dp?, onPath: (String) -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        (if (width != null) Modifier.width(width) else Modifier.widthIn(max = 320.dp).fillMaxWidth())
            .clip(shape).background(style.card).border(1.dp, style.line, shape)
    ) {
        if (card.image.isNotEmpty()) {
            AsyncImage(
                model = card.image, contentDescription = FlowKit.shownLabel(card.title), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(130.dp).background(style.text.copy(alpha = 0.05f))
            )
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (card.title.isNotEmpty() || card.price.isNotEmpty()) Row(verticalAlignment = Alignment.Top) {
                Text(FlowKit.shownLabel(card.title), style = style.bold, modifier = Modifier.weight(1f))
                if (card.price.isNotEmpty()) Text(card.price, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = style.accent), modifier = Modifier.padding(start = 6.dp))
            }
            if (card.description.isNotEmpty()) Text(FlowRich.plainText(card.description), style = TextStyle(fontSize = 13.sp, color = style.muted, lineHeight = 18.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            card.buttons.forEach { b ->
                if (b.action == "link") CardButton(b.label, true, style, true) { openStepLink(context, b.href) }
                else CardButton(b.label, false, style, !busy) { onPath(b.id) }
            }
        }
    }
}

/**
 * carousel-node and product-card-node (web displayStep carousel /
 * richNodesView productCardHtml): cards with their image, title, price,
 * description and up to three buttons. A path button answers {buttonId,
 * cardId}; a link button opens its page and the step keeps waiting; cards
 * with only link buttons get Continue ({action: continue}).
 */
@Composable
fun CardsStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val product = step.type == "product-card-node"
    val cards = carouselCards(if (product) FlowRich.productCarouselData(step.node) else step.node)
    val onlyLinks = cards.none { c -> c.buttons.any { it.action == "path" } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cards.size == 1) OneCard(cards[0], style, busy, null) { send(FlowStepAnswer.cardButton(it, if (product) null else cards[0].id)) }
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 16.dp)) {
            items(cards, key = { it.id }) { c -> OneCard(c, style, busy, 230.dp) { send(FlowStepAnswer.cardButton(it, c.id)) } }
        }
        if (onlyLinks) StepOption(CAROUSEL_CONTINUE, style, enabled = !busy) { send(FlowStepAnswer.cardsContinue) }
        StepStatus(style, step.error)
    }
}

/**
 * navigate-node (web displayStep navigate): the links as options that open
 * their page (the click is recorded, the step keeps waiting) and Done.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigateStep(ui: NodeUIState.SingleChoice, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ui.choices.forEach { c ->
            if (c.id.startsWith("link-")) {
                Surface(
                    onClick = {
                        openStepLink(context, c.targetPort)
                        c.id.removePrefix("link-").toIntOrNull()?.let { send(FlowStepAnswer.navigateLink(it)) }
                    },
                    shape = RoundedCornerShape(style.radius),
                    color = style.option,
                    contentColor = style.optionInk,
                    shadowElevation = 1.dp
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.text, style = TextStyle(fontSize = style.fontSize, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            } else StepOption(c.text, style, enabled = !busy) { send(FlowStepAnswer.navigateDone) }
        }
    }
}
