package com.conferbot.sdk.ui.compose.knowledgebase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.KnowledgeBaseArticle

/**
 * Search bar for Knowledge Base articles
 * Features:
 * - Instant search with dropdown results
 * - Clear button
 * - Keyboard navigation support
 * - Shows article category and reading time in results
 */
@Composable
fun ArticleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<KnowledgeBaseArticle>,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search for articles...",
    primaryColor: Color = Color(0xFF0100EC),
    maxResults: Int = 5
) {
    var isFocused by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(-1) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Show dropdown when focused and has query or results
    val showDropdown = isFocused && query.isNotBlank()

    // Reset selected index when results change
    LaunchedEffect(searchResults) {
        selectedIndex = -1
    }

    Column(modifier = modifier) {
        // Search Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(primaryColor),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch(query)
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Clear button
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            selectedIndex = -1
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search Results Dropdown
        AnimatedVisibility(
            visible = showDropdown && searchResults.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    val displayResults = searchResults.take(maxResults)

                    displayResults.forEachIndexed { index, article ->
                        SearchResultItem(
                            article = article,
                            isSelected = index == selectedIndex,
                            onClick = {
                                onArticleClick(article)
                                focusManager.clearFocus()
                            },
                            primaryColor = primaryColor
                        )

                        if (index < displayResults.lastIndex) {
                            Divider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // View all results
                    if (searchResults.size > maxResults) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearch(query)
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "View all ${searchResults.size} results",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryColor
                            )
                        }
                    }
                }
            }
        }

        // No results message
        AnimatedVisibility(
            visible = showDropdown && searchResults.isEmpty() && query.length >= 2,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No articles found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Individual search result item
 */
@Composable
private fun SearchResultItem(
    article: KnowledgeBaseArticle,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) primaryColor.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Document icon
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = primaryColor.copy(alpha = 0.1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
    }
}

/**
 * Compact search bar for header use
 */
@Composable
fun CompactSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search articles..."
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
