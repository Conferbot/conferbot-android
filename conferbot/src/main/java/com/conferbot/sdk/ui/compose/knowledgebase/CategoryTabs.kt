package com.conferbot.sdk.ui.compose.knowledgebase

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.CategoryIcon
import com.conferbot.sdk.models.KnowledgeBaseCategory

/**
 * Horizontal scrollable category tabs
 * Features:
 * - "All" tab showing all articles
 * - Category icons based on category type
 * - Article count badge
 * - Smooth animations
 */
@Composable
fun CategoryTabs(
    categories: List<KnowledgeBaseCategory>,
    selectedCategoryId: String?, // null means "All" is selected
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC),
    showAllTab: Boolean = true,
    totalArticleCount: Int = 0
) {
    val listState = rememberLazyListState()

    // Auto-scroll to selected category
    LaunchedEffect(selectedCategoryId) {
        val index = if (selectedCategoryId == null) {
            0
        } else {
            categories.indexOfFirst { it.id == selectedCategoryId }.let {
                if (showAllTab) it + 1 else it
            }
        }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // "All" tab
        if (showAllTab) {
            item {
                CategoryTab(
                    name = "All",
                    icon = Icons.Outlined.GridView,
                    articleCount = totalArticleCount,
                    isSelected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                    primaryColor = primaryColor
                )
            }
        }

        // Category tabs
        items(categories, key = { it.id }) { category ->
            CategoryTab(
                name = category.name,
                icon = getCategoryIcon(category.getDefaultIcon()),
                articleCount = category.articleCount,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) },
                primaryColor = primaryColor
            )
        }
    }
}

/**
 * Individual category tab
 */
@Composable
private fun CategoryTab(
    name: String,
    icon: ImageVector,
    articleCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "backgroundColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "contentColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Article count badge
            if (articleCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color.White.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = articleCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Vertical category list for category selection screen
 */
@Composable
fun CategoryList(
    categories: List<KnowledgeBaseCategory>,
    onCategoryClick: (KnowledgeBaseCategory) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEachIndexed { index, category ->
            CategoryListItem(
                category = category,
                onClick = { onCategoryClick(category) },
                primaryColor = primaryColor,
                animationDelay = index * 50
            )
        }
    }
}

/**
 * Individual category list item (card style)
 */
@Composable
fun CategoryListItem(
    category: KnowledgeBaseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC),
    animationDelay: Int = 0
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.getDefaultIcon()),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Category info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (category.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = category.getArticleCountText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View category",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Get icon for category type
 */
fun getCategoryIcon(categoryIcon: CategoryIcon): ImageVector {
    return when (categoryIcon) {
        CategoryIcon.GETTING_STARTED -> Icons.Outlined.PlayCircle
        CategoryIcon.SETTINGS -> Icons.Outlined.Settings
        CategoryIcon.FAQ -> Icons.Outlined.Help
        CategoryIcon.GUIDE -> Icons.Outlined.MenuBook
        CategoryIcon.ACCOUNT -> Icons.Outlined.Person
        CategoryIcon.BILLING -> Icons.Outlined.CreditCard
        CategoryIcon.SECURITY -> Icons.Outlined.Security
        CategoryIcon.INTEGRATION -> Icons.Outlined.Code
        CategoryIcon.TROUBLESHOOTING -> Icons.Outlined.Build
        CategoryIcon.DOCUMENT -> Icons.Outlined.Description
    }
}

/**
 * Empty categories state
 */
@Composable
fun EmptyCategoriesState(
    modifier: Modifier = Modifier,
    message: String = "No categories available"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
