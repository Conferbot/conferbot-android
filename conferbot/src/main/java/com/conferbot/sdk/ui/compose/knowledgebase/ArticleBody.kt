package com.conferbot.sdk.ui.compose.knowledgebase

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.helpcentre.ArticleHtml

private const val URL_TAG = "url"

/** A link from an article opens outside the app (the browser, mail, the dialler). */
private fun openOutside(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun annotated(runs: List<ArticleHtml.Run>, linkColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    runs.forEach { r ->
        val decoration = listOfNotNull(
            TextDecoration.Underline.takeIf { r.underline || r.href != null },
            TextDecoration.LineThrough.takeIf { r.strike }
        )
        val span = SpanStyle(
            fontWeight = if (r.bold) FontWeight.Bold else null,
            fontStyle = if (r.italic) FontStyle.Italic else null,
            fontFamily = if (r.code) FontFamily.Monospace else null,
            background = if (r.code) codeBackground else Color.Unspecified,
            color = if (r.href != null) linkColor else Color.Unspecified,
            textDecoration = if (decoration.isEmpty()) null else TextDecoration.combine(decoration)
        )
        if (r.href != null) pushStringAnnotation(URL_TAG, r.href)
        withStyle(span) { append(r.text) }
        if (r.href != null) pop()
    }
}

/**
 * A help centre article's body drawn from its HTML, no WebView (#57): the
 * blocks of [ArticleHtml] in the chat's type, links in [linkColor] opening
 * outside the app, pictures loaded with Coil, simple tables as a grid.
 */
@Composable
fun ArticleBody(
    html: String,
    linkColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = remember(html) { ArticleHtml.parse(html) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val body = MaterialTheme.typography.bodyLarge.copy(color = textColor)

    @Composable
    fun Runs(runs: List<ArticleHtml.Run>, style: TextStyle = body, modifier: Modifier = Modifier) {
        val text = remember(runs, linkColor, codeBackground) { annotated(runs, linkColor, codeBackground) }
        if (runs.none { it.href != null }) {
            Text(text, style = style, modifier = modifier)
        } else {
            ClickableText(text, style = style, modifier = modifier, onClick = { offset ->
                text.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.let { openOutside(context, it.item) }
            })
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ArticleHtml.Block.Heading -> Runs(
                    block.runs,
                    when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(color = textColor, fontWeight = FontWeight.Bold),
                    Modifier.padding(top = 4.dp)
                )
                is ArticleHtml.Block.Paragraph -> Runs(block.runs)
                is ArticleHtml.Block.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(
                        text = when {
                            block.continued -> ""
                            block.ordered -> "${block.number}."
                            block.depth % 2 == 0 -> "•"
                            else -> "◦"
                        },
                        style = body,
                        modifier = Modifier.widthIn(min = 20.dp).padding(end = 6.dp)
                    )
                    Runs(block.runs, modifier = Modifier.weight(1f))
                }
                is ArticleHtml.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(linkColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                    Runs(
                        block.runs,
                        body.copy(color = mutedColor, fontStyle = FontStyle.Italic),
                        Modifier.padding(start = 10.dp).weight(1f)
                    )
                }
                is ArticleHtml.Block.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBackground)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor, fontFamily = FontFamily.Monospace),
                        softWrap = false
                    )
                }
                is ArticleHtml.Block.Image -> AsyncImage(
                    model = block.src,
                    contentDescription = block.alt,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
                is ArticleHtml.Block.Table -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, lineColor, RoundedCornerShape(6.dp))
                ) {
                    val columns = block.rows.maxOf { it.size }
                    block.rows.forEachIndexed { r, row ->
                        val head = block.header && r == 0
                        if (r > 0) Divider(color = lineColor, thickness = 1.dp)
                        Row(
                            Modifier
                                .height(IntrinsicSize.Min)
                                .then(if (head) Modifier.background(codeBackground) else Modifier)
                        ) {
                            for (c in 0 until columns) {
                                if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(lineColor))
                                Runs(
                                    row.getOrNull(c).orEmpty(),
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = textColor,
                                        fontWeight = if (head) FontWeight.Bold else null
                                    ),
                                    Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                ArticleHtml.Block.Rule -> Divider(color = lineColor, thickness = 1.dp)
            }
        }
    }
}
