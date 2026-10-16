package com.conferbot.sdk.ui.compose.chat

import com.conferbot.sdk.core.i18n.I18n
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.waiting.WaitingExtras
import com.conferbot.sdk.models.KnowledgeBaseArticle
import com.conferbot.sdk.ui.compose.knowledgebase.ArticleDetailView
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * An article the server picked for a wait, shown on its own over the chat
 * while the help centre is off or does not list it (#75; RN
 * useWaitingExtras.openArticle over the chat, iOS #83's sheet). Fetched by
 * id (`get-kb-article`, [WaitingExtras.openArticleById]); the help centre's
 * article view draws it. Back, the system back and the breadcrumb close it.
 */
@Composable
fun WaitArticleSheet(modifier: Modifier = Modifier, waiting: WaitingExtras = Conferbot.waiting) {
    // This chat can show an article by id: AI sources and article cards open in the app (#90).
    androidx.compose.runtime.DisposableEffect(Unit) {
        Conferbot.setArticlesShown(true)
        onDispose { Conferbot.setArticlesShown(false) }
    }
    val shown by waiting.article.collectAsState()
    val view = shown ?: return
    val colors = ConferbotThemeAmbient.current.colors
    val scope = rememberCoroutineScope()
    var rated by remember(view.id) { mutableStateOf(false) }
    BackHandler { waiting.closeArticle() }
    val article = view.article ?: KnowledgeBaseArticle(id = view.id, title = view.title.orEmpty())
    Surface(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            ArticleDetailView(
                article = article,
                onBackClick = { waiting.closeArticle() },
                onHomeClick = { waiting.closeArticle() },
                onCategoryClick = { waiting.closeArticle() },
                onRelatedArticleClick = {},
                onRateArticle = { helpful ->
                    scope.launch {
                        Conferbot.rateKnowledgeBaseArticle(view.id, helpful)?.collectLatest { ok -> if (ok) rated = true }
                    }
                },
                hasRated = rated,
                primaryColor = colors.primary,
                bodyFailed = view.failed,
                modifier = Modifier.fillMaxSize()
            )
            if (view.failed) {
                TextButton(
                    onClick = { waiting.retryArticle() },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                ) { Text(I18n.t("common.tryAgain")) }
            }
        }
    }
}
