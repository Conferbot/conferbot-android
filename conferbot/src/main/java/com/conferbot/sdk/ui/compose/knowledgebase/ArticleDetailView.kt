package com.conferbot.sdk.ui.compose.knowledgebase

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.conferbot.sdk.models.KnowledgeBaseArticle
import java.text.SimpleDateFormat
import java.util.*

/**
 * Article detail view
 * Features:
 * - Full article content with HTML rendering
 * - Breadcrumb navigation
 * - Table of contents (for articles with multiple headings)
 * - Rating section (helpful/not helpful)
 * - Related articles
 * - Scroll depth tracking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailView(
    article: KnowledgeBaseArticle,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onRelatedArticleClick: (KnowledgeBaseArticle) -> Unit,
    onRateArticle: (Boolean) -> Unit,
    relatedArticles: List<KnowledgeBaseArticle> = emptyList(),
    hasRated: Boolean = false,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC),
    categoryName: String? = null,
    onScrollDepthChange: (Int) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var contentHeight by remember { mutableStateOf(0) }
    var viewportHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // Track scroll depth
    LaunchedEffect(scrollState.value, contentHeight, viewportHeight) {
        if (contentHeight > viewportHeight && contentHeight > 0) {
            val maxScroll = contentHeight - viewportHeight
            val scrollPercent = ((scrollState.value.toFloat() / maxScroll) * 100).toInt().coerceIn(0, 100)
            onScrollDepthChange(scrollPercent)
        }
    }

    Scaffold(
        topBar = {
            ArticleDetailTopBar(
                title = article.title,
                onBackClick = onBackClick,
                primaryColor = primaryColor
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coordinates ->
                    viewportHeight = coordinates.size.height
                }
                .verticalScroll(scrollState)
                .onGloballyPositioned { coordinates ->
                    contentHeight = coordinates.size.height
                }
        ) {
            // Breadcrumb navigation
            BreadcrumbNavigation(
                categoryName = categoryName ?: article.categoryName,
                articleTitle = article.title,
                onHomeClick = onHomeClick,
                onCategoryClick = onCategoryClick,
                primaryColor = primaryColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Article title
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Meta row (reading time, last updated)
            ArticleDetailMeta(
                article = article,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cover image
            article.coverImage?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Author info
            article.author?.let { author ->
                AuthorSection(
                    author = author,
                    publishedDate = article.publishedDate,
                    primaryColor = primaryColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Table of contents (if article has multiple sections)
            val headings = extractHeadings(article.content)
            if (headings.size >= 2) {
                TableOfContents(
                    headings = headings,
                    primaryColor = primaryColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Article content
            HtmlContent(
                html = article.content,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Rating section
            RatingSection(
                hasRated = hasRated,
                onRate = onRateArticle,
                primaryColor = primaryColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Related articles
            if (relatedArticles.isNotEmpty()) {
                RelatedArticlesSection(
                    articles = relatedArticles,
                    onArticleClick = onRelatedArticleClick,
                    primaryColor = primaryColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Article detail top bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleDetailTopBar(
    title: String,
    onBackClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = primaryColor,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        ),
        modifier = modifier
    )
}

/**
 * Breadcrumb navigation
 */
@Composable
fun BreadcrumbNavigation(
    categoryName: String?,
    articleTitle: String,
    onHomeClick: () -> Unit,
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onHomeClick)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = "Home",
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Help",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )
        }

        // Separator
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )

        // Category
        categoryName?.let { category ->
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCategoryClick)
                    .padding(4.dp)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // Current article
        Text(
            text = articleTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(4.dp)
        )
    }
}

/**
 * Article detail meta information
 */
@Composable
private fun ArticleDetailMeta(
    article: KnowledgeBaseArticle,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reading time
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = article.calculateReadingTime(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Last updated
        article.updatedAt?.let { date ->
            Text(
                text = "\u2022",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Updated ${formatDate(date)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Author section
 */
@Composable
private fun AuthorSection(
    author: com.conferbot.sdk.models.ArticleAuthor,
    publishedDate: Date?,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        if (author.avatar != null) {
            AsyncImage(
                model = author.avatar,
                contentDescription = author.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = author.name.firstOrNull()?.uppercase() ?: "A",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = author.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            publishedDate?.let { date ->
                Text(
                    text = "Published on ${formatDate(date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Table of contents for article navigation
 */
@Composable
private fun TableOfContents(
    headings: List<Heading>,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "In this article",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    headings.forEach { heading ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ((heading.level - 1) * 16).dp,
                                    top = 4.dp,
                                    bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(primaryColor, CircleShape)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = heading.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * HTML content renderer
 */
@Composable
private fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier
) {
    // Convert HTML to styled text
    val spannedText = remember(html) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }

    Text(
        text = spannedText.toString(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * Rating section (Was this article helpful?)
 */
@Composable
private fun RatingSection(
    hasRated: Boolean,
    onRate: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasRated) {
                // Thank you message
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Thank you for your feedback!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Was this article helpful?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Yes button
                    OutlinedButton(
                        onClick = { onRate(true) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = primaryColor
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(primaryColor)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yes")
                    }

                    // No button
                    OutlinedButton(
                        onClick = { onRate(false) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No")
                    }
                }
            }
        }
    }
}

/**
 * Related articles section
 */
@Composable
private fun RelatedArticlesSection(
    articles: List<KnowledgeBaseArticle>,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Related Articles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        articles.forEach { article ->
            RelatedArticleItem(
                article = article,
                onClick = { onArticleClick(article) },
                primaryColor = primaryColor
            )

            if (article != articles.last()) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Related article item
 */
@Composable
private fun RelatedArticleItem(
    article: KnowledgeBaseArticle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = primaryColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                article.categoryName?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " \u2022 ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = article.calculateReadingTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Arrow
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Heading data class for table of contents
 */
data class Heading(
    val id: String,
    val text: String,
    val level: Int
)

/**
 * Extract headings from HTML content
 */
private fun extractHeadings(html: String): List<Heading> {
    val headings = mutableListOf<Heading>()
    val pattern = Regex("<h([1-3])[^>]*>(.*?)</h\\1>", RegexOption.IGNORE_CASE)

    pattern.findAll(html).forEachIndexed { index, match ->
        val level = match.groupValues[1].toIntOrNull() ?: 1
        val text = match.groupValues[2]
            .replace(Regex("<[^>]*>"), "") // Remove nested HTML tags
            .trim()

        if (text.isNotBlank()) {
            val id = "heading-$index-${text.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
            headings.add(Heading(id = id, text = text, level = level))
        }
    }

    return headings
}

/**
 * Format date for display
 */
private fun formatDate(date: Date): String {
    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(date)
}
