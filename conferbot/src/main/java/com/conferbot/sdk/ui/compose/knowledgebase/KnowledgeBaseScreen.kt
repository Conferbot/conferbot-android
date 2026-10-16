package com.conferbot.sdk.ui.compose.knowledgebase

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.helpcentre.HelpCentre
import com.conferbot.sdk.models.KnowledgeBaseArticle
import com.conferbot.sdk.models.KnowledgeBaseCategory
import com.conferbot.sdk.models.KnowledgeBaseData
import com.conferbot.sdk.services.KnowledgeBaseService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Navigation state for Knowledge Base
 */
sealed class KnowledgeBaseNavState {
    data object Categories : KnowledgeBaseNavState()
    data class ArticleList(val category: KnowledgeBaseCategory) : KnowledgeBaseNavState()
    data class ArticleDetail(
        val article: KnowledgeBaseArticle,
        val category: KnowledgeBaseCategory?,
        /** Opened from the conversation (an AI source, an article card): Back returns to it. */
        val fromChat: Boolean = false
    ) : KnowledgeBaseNavState()
    data class SearchResults(val query: String) : KnowledgeBaseNavState()
}

/**
 * The help centre: categories (and recent articles), a category's articles,
 * one article (its body fetched on open), search.
 *
 * [embedded] draws it inside the chat (the Articles tab): no app bar of its
 * own, a back row while inside. [openRequest] opens one article by id (the
 * web's openArticle); [onBackToChat] is where Back goes from an article
 * opened from the conversation. [showRecent] follows the owner's
 * hideRecentArticles (core/helpcentre/HelpCentre.kt).
 *
 * As the home screen's help centre (core/home): [initialCategoryId] opens
 * on that topic (a home topic chip), and [backAtRoot] puts a back arrow
 * (to [onDismiss], the home) where the list's close would be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    knowledgeBaseService: KnowledgeBaseService,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC),
    title: String = "Help Center",
    embedded: Boolean = false,
    showRecent: Boolean = true,
    openRequest: Conferbot.HelpArticleRequest? = null,
    onOpenRequestHandled: (Conferbot.HelpArticleRequest) -> Unit = {},
    onBackToChat: (() -> Unit)? = null,
    initialCategoryId: String? = null,
    backAtRoot: Boolean = false
) {
    val scope = rememberCoroutineScope()

    // State
    var navState by remember { mutableStateOf<KnowledgeBaseNavState>(KnowledgeBaseNavState.Categories) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KnowledgeBaseArticle>>(emptyList()) }

    // Collect service states
    val knowledgeBaseData by knowledgeBaseService.knowledgeBaseData.collectAsState()
    val isLoading by knowledgeBaseService.isLoading.collectAsState()
    val error by knowledgeBaseService.error.collectAsState()
    val bodies by knowledgeBaseService.articleBodies.collectAsState()
    val failedBodies by knowledgeBaseService.failedBodies.collectAsState()

    fun openArticle(article: KnowledgeBaseArticle, category: KnowledgeBaseCategory?, fromChat: Boolean = false) {
        knowledgeBaseService.sendCurrentEngagement()
        navState = KnowledgeBaseNavState.ArticleDetail(article, category, fromChat)
        knowledgeBaseService.loadArticleBody(article.id)
        knowledgeBaseService.trackArticleView(article)
        knowledgeBaseService.startArticleEngagement(article.id)
    }

    // A topic picked on the home screen: open on it, once.
    var openedTopic by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialCategoryId, knowledgeBaseData) {
        val id = initialCategoryId ?: return@LaunchedEffect
        if (openedTopic == id) return@LaunchedEffect
        val category = knowledgeBaseData?.getCategoryById(id) ?: return@LaunchedEffect
        openedTopic = id
        navState = KnowledgeBaseNavState.ArticleList(category)
    }

    // One article by id: an AI source or an article card in the chat.
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        val (article, category) = HelpCentre.findArticle(knowledgeBaseData, request.articleId, request.title)
        if (article != null) openArticle(article, category, request.fromChat)
        onOpenRequestHandled(request)
    }

    // Update search results when query changes
    LaunchedEffect(searchQuery, knowledgeBaseData) {
        if (searchQuery.length >= 2) {
            knowledgeBaseService.searchArticles(searchQuery).collectLatest { results ->
                searchResults = results
            }
        } else {
            searchResults = emptyList()
        }
    }

    val goBack: () -> Unit = {
        when (val state = navState) {
            is KnowledgeBaseNavState.ArticleList -> navState = KnowledgeBaseNavState.Categories
            is KnowledgeBaseNavState.ArticleDetail -> {
                knowledgeBaseService.sendCurrentEngagement()
                if (state.fromChat && onBackToChat != null) {
                    navState = KnowledgeBaseNavState.Categories
                    onBackToChat()
                } else {
                    navState = state.category?.let { KnowledgeBaseNavState.ArticleList(it) } ?: KnowledgeBaseNavState.Categories
                }
            }
            is KnowledgeBaseNavState.SearchResults -> navState = KnowledgeBaseNavState.Categories
            else -> onDismiss()
        }
    }

    // The system Back steps out of an article or a topic first.
    androidx.activity.compose.BackHandler(enabled = navState !is KnowledgeBaseNavState.Categories) { goBack() }

    Scaffold(
        topBar = {
            if (!embedded) {
                KnowledgeBaseTopBar(
                    navState = navState,
                    title = title,
                    onBackClick = goBack,
                    onDismiss = onDismiss,
                    primaryColor = primaryColor,
                    backAtRoot = backAtRoot
                )
            } else if (navState !is KnowledgeBaseNavState.Categories) {
                EmbeddedBackRow(navState = navState, onBackClick = goBack, primaryColor = primaryColor)
            }
        },
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner
            AnimatedVisibility(visible = error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { knowledgeBaseService.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Main content with navigation
            AnimatedContent(
                targetState = navState,
                transitionSpec = {
                    if (targetState is KnowledgeBaseNavState.Categories) {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                    }
                },
                label = "nav_transition"
            ) { state ->
                when (state) {
                    is KnowledgeBaseNavState.Categories -> {
                        CategoriesScreen(
                            knowledgeBaseData = knowledgeBaseData,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            searchResults = searchResults,
                            onCategoryClick = { category ->
                                navState = KnowledgeBaseNavState.ArticleList(category)
                            },
                            onArticleClick = { article ->
                                val (found, category) = HelpCentre.findArticle(knowledgeBaseData, article.id, article.title)
                                openArticle(found ?: article, category)
                            },
                            onSearch = { query ->
                                if (query.isNotBlank()) {
                                    navState = KnowledgeBaseNavState.SearchResults(query)
                                }
                            },
                            isLoading = isLoading,
                            showRecent = showRecent,
                            primaryColor = primaryColor
                        )
                    }

                    is KnowledgeBaseNavState.ArticleList -> {
                        ArticleListScreen(
                            category = state.category,
                            onArticleClick = { article -> openArticle(article, state.category) },
                            primaryColor = primaryColor
                        )
                    }

                    is KnowledgeBaseNavState.ArticleDetail -> {
                        var relatedArticles by remember { mutableStateOf<List<KnowledgeBaseArticle>>(emptyList()) }
                        var hasRated by remember { mutableStateOf(knowledgeBaseService.hasRatedArticle(state.article.id)) }
                        val article = bodies[state.article.id]?.let { state.article.copy(content = it) } ?: state.article

                        // Fetch related articles
                        LaunchedEffect(state.article) {
                            knowledgeBaseService.getRelatedArticles(state.article).collectLatest { articles ->
                                relatedArticles = articles
                            }
                        }

                        ArticleDetailView(
                            article = article,
                            onBackClick = goBack,
                            onHomeClick = {
                                knowledgeBaseService.sendCurrentEngagement()
                                navState = KnowledgeBaseNavState.Categories
                            },
                            onCategoryClick = {
                                knowledgeBaseService.sendCurrentEngagement()
                                state.category?.let {
                                    navState = KnowledgeBaseNavState.ArticleList(it)
                                }
                            },
                            onRelatedArticleClick = { related ->
                                val category = knowledgeBaseData?.getCategoryById(related.categoryId ?: "")
                                openArticle(related, category)
                            },
                            onRateArticle = { helpful ->
                                scope.launch {
                                    knowledgeBaseService.rateArticle(state.article.id, helpful).collectLatest { success ->
                                        if (success) {
                                            hasRated = true
                                        }
                                    }
                                }
                            },
                            relatedArticles = relatedArticles,
                            hasRated = hasRated,
                            primaryColor = primaryColor,
                            categoryName = state.category?.name,
                            onScrollDepthChange = { depth ->
                                knowledgeBaseService.updateScrollDepth(depth)
                            },
                            showTopBar = false,
                            bodyFailed = state.article.id in failedBodies
                        )
                    }

                    is KnowledgeBaseNavState.SearchResults -> {
                        SearchResultsScreen(
                            query = state.query,
                            results = searchResults,
                            onArticleClick = { article ->
                                val (found, category) = HelpCentre.findArticle(knowledgeBaseData, article.id, article.title)
                                openArticle(found ?: article, category)
                            },
                            primaryColor = primaryColor
                        )
                    }
                }
            }
        }
    }
}

/** Inside the chat: Back and where you are, under the chat header. */
@Composable
private fun EmbeddedBackRow(
    navState: KnowledgeBaseNavState,
    onBackClick: () -> Unit,
    primaryColor: Color
) {
    val label = when (navState) {
        is KnowledgeBaseNavState.ArticleList -> navState.category.name
        is KnowledgeBaseNavState.ArticleDetail -> navState.category?.name ?: navState.article.categoryName ?: "Help Center"
        is KnowledgeBaseNavState.SearchResults -> "Search: ${navState.query}"
        else -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = primaryColor)
        }
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Knowledge Base top bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeBaseTopBar(
    navState: KnowledgeBaseNavState,
    title: String,
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    primaryColor: Color,
    backAtRoot: Boolean = false
) {
    val displayTitle = when (navState) {
        is KnowledgeBaseNavState.Categories -> title
        is KnowledgeBaseNavState.ArticleList -> navState.category.name
        is KnowledgeBaseNavState.ArticleDetail -> navState.article.title
        is KnowledgeBaseNavState.SearchResults -> "Search: ${navState.query}"
    }

    val showBackButton = navState !is KnowledgeBaseNavState.Categories

    TopAppBar(
        title = {
            Text(
                text = displayTitle,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else if (backAtRoot) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = primaryColor,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

/**
 * Categories screen with search
 */
@Composable
private fun CategoriesScreen(
    knowledgeBaseData: KnowledgeBaseData?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<KnowledgeBaseArticle>,
    onCategoryClick: (KnowledgeBaseCategory) -> Unit,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    onSearch: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    showRecent: Boolean = true,
    primaryColor: Color = Color(0xFF0100EC)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search bar
        item {
            ArticleSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                searchResults = searchResults,
                onArticleClick = onArticleClick,
                onSearch = onSearch,
                primaryColor = primaryColor
            )
        }

        when {
            isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primaryColor)
                    }
                }
            }

            knowledgeBaseData == null || knowledgeBaseData.categories.isEmpty() -> {
                item {
                    EmptyCategoriesState()
                }
            }

            else -> {
                // The newest articles, unless the owner hid them (hideRecentArticles).
                val recent = knowledgeBaseData.recentArticles
                if (showRecent && recent.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent articles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(recent, key = { "recent-" + it.id }) { article ->
                        RecentArticleRow(article = article, onClick = { onArticleClick(article) }, primaryColor = primaryColor)
                    }
                }

                // Section header
                item {
                    Text(
                        text = "Browse by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Category list
                items(knowledgeBaseData.categories, key = { it.id }) { category ->
                    CategoryListItem(
                        category = category,
                        onClick = { onCategoryClick(category) },
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

/**
 * Article list screen for a specific category
 */
@Composable
private fun ArticleListScreen(
    category: KnowledgeBaseCategory,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Category header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (category.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = category.getArticleCountText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor
                )
            }
        }

        // Article list
        ArticleListView(
            articles = category.articles,
            onArticleClick = onArticleClick,
            primaryColor = primaryColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Search results screen
 */
@Composable
private fun SearchResultsScreen(
    query: String,
    results: List<KnowledgeBaseArticle>,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF0100EC)
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Results header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${results.size} result${if (results.size != 1) "s" else ""} for \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Results list
        ArticleListView(
            articles = results,
            onArticleClick = onArticleClick,
            primaryColor = primaryColor,
            emptyMessage = "No articles found for \"$query\"",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Standalone Knowledge Base component
 * Use this for embedding KB in other screens
 */
@Composable
fun KnowledgeBaseContent(
    knowledgeBaseData: KnowledgeBaseData?,
    onArticleClick: (KnowledgeBaseArticle) -> Unit,
    onCategoryClick: (KnowledgeBaseCategory) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    primaryColor: Color = Color(0xFF0100EC)
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primaryColor)
            }
        }

        knowledgeBaseData == null || knowledgeBaseData.categories.isEmpty() -> {
            EmptyCategoriesState(modifier = modifier)
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(knowledgeBaseData.categories, key = { it.id }) { category ->
                    CategoryListItem(
                        category = category,
                        onClick = { onCategoryClick(category) },
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

/** One recent article: title, its topic, a chevron. */
@Composable
private fun RecentArticleRow(
    article: KnowledgeBaseArticle,
    onClick: () -> Unit,
    primaryColor: Color
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(article.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                article.categoryName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
