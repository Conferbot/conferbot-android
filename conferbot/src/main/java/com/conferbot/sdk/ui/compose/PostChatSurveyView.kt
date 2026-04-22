package com.conferbot.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * Post-chat survey view for human handover flow
 * Displays survey questions after agent chat ends
 */
@Composable
fun PostChatSurveyView(
    state: NodeUIState.PostChatSurvey,
    onResponse: (Any) -> Unit,
    primaryColor: Color = ConferbotThemeAmbient.current.colors.primary,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    // Track all answers
    var answers by remember { mutableStateOf(mutableMapOf<String, Any>()) }
    var currentQuestionIndex by remember { mutableStateOf(state.currentQuestionIndex) }

    val currentQuestion = state.questions.getOrNull(currentQuestionIndex)
    val isLastQuestion = currentQuestionIndex >= state.questions.size - 1
    val canProceed = currentQuestion?.let { question ->
        if (question.required) {
            answers.containsKey(question.id) && answers[question.id] != null
        } else {
            true
        }
    } ?: false

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Survey header
            SurveyHeader(
                title = state.surveyTitle ?: "How was your experience?",
                description = state.surveyDescription,
                primaryColor = primaryColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress indicator
            SurveyProgressIndicator(
                currentQuestion = currentQuestionIndex + 1,
                totalQuestions = state.questions.size,
                primaryColor = primaryColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Current question
            AnimatedContent(
                targetState = currentQuestionIndex,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "question_animation"
            ) { questionIndex ->
                val question = state.questions.getOrNull(questionIndex)
                if (question != null) {
                    SurveyQuestionContent(
                        question = question,
                        currentAnswer = answers[question.id],
                        onAnswerChanged = { answer ->
                            answers = answers.toMutableMap().apply {
                                put(question.id, answer)
                            }
                        },
                        primaryColor = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button (only show if not first question)
                if (currentQuestionIndex > 0) {
                    OutlinedButton(
                        onClick = { currentQuestionIndex-- },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Next/Submit button
                Button(
                    onClick = {
                        if (isLastQuestion) {
                            // Submit all answers
                            val surveyResponses = answers.map { (questionId, value) ->
                                NodeUIState.SurveyResponse(
                                    questionId = questionId,
                                    value = value
                                )
                            }
                            onResponse(surveyResponses)
                        } else {
                            // Go to next question
                            currentQuestionIndex++
                        }
                    },
                    enabled = canProceed && !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isLastQuestion) "Submit" else "Next")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isLastQuestion) Icons.Default.Check else Icons.Default.ArrowForward,
                            contentDescription = if (isLastQuestion) "Submit" else "Next"
                        )
                    }
                }
            }

            // Skip survey option
            if (!state.isSubmitting) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { onResponse(emptyList<NodeUIState.SurveyResponse>()) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Skip survey",
                        color = theme.colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Survey header with title and optional description
 */
@Composable
private fun SurveyHeader(
    title: String,
    description: String?,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.RateReview,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Survey progress indicator
 */
@Composable
private fun SurveyProgressIndicator(
    currentQuestion: Int,
    totalQuestions: Int,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Question $currentQuestion of $totalQuestions",
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.onSurfaceVariant
            )
            Text(
                text = "${(currentQuestion.toFloat() / totalQuestions * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = currentQuestion.toFloat() / totalQuestions,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = primaryColor,
            trackColor = primaryColor.copy(alpha = 0.2f),
        )
    }
}

/**
 * Survey question content - renders appropriate component based on question type
 */
@Composable
private fun SurveyQuestionContent(
    question: NodeUIState.PostChatSurvey.SurveyQuestion,
    currentAnswer: Any?,
    onAnswerChanged: (Any) -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Column {
        // Question text
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Required indicator
        if (question.required) {
            Text(
                text = "* Required",
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Question-specific component
        when (question.type) {
            NodeUIState.PostChatSurvey.SurveyQuestionType.RATING -> {
                SurveyRatingQuestion(
                    currentRating = currentAnswer as? Int,
                    minRating = question.minRating,
                    maxRating = question.maxRating,
                    onRatingChanged = onAnswerChanged,
                    primaryColor = primaryColor
                )
            }

            NodeUIState.PostChatSurvey.SurveyQuestionType.TEXT -> {
                SurveyTextQuestion(
                    currentText = currentAnswer as? String ?: "",
                    onTextChanged = onAnswerChanged,
                    primaryColor = primaryColor
                )
            }

            NodeUIState.PostChatSurvey.SurveyQuestionType.CHOICE -> {
                SurveyChoiceQuestion(
                    options = question.options ?: emptyList(),
                    selectedOption = currentAnswer as? String,
                    onOptionSelected = onAnswerChanged,
                    primaryColor = primaryColor
                )
            }

            NodeUIState.PostChatSurvey.SurveyQuestionType.MULTI_CHOICE -> {
                @Suppress("UNCHECKED_CAST")
                SurveyMultiChoiceQuestion(
                    options = question.options ?: emptyList(),
                    selectedOptions = (currentAnswer as? List<String>) ?: emptyList(),
                    onOptionsChanged = onAnswerChanged,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

/**
 * Star rating component for survey
 */
@Composable
private fun SurveyRatingQuestion(
    currentRating: Int?,
    minRating: Int,
    maxRating: Int,
    onRatingChanged: (Int) -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Star rating row
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            (minRating..maxRating).forEach { rating ->
                IconButton(
                    onClick = { onRatingChanged(rating) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (currentRating != null && rating <= currentRating) {
                            Icons.Filled.Star
                        } else {
                            Icons.Filled.StarOutline
                        },
                        contentDescription = "Rating $rating",
                        tint = if (currentRating != null && rating <= currentRating) {
                            Color(0xFFFFB300) // Gold color for filled stars
                        } else {
                            Color.Gray
                        },
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rating labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Poor",
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.onSurfaceVariant
            )
            Text(
                text = "Excellent",
                style = MaterialTheme.typography.bodySmall,
                color = theme.colors.onSurfaceVariant
            )
        }

        // Selected rating text
        if (currentRating != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = primaryColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (currentRating) {
                        1 -> "Very Dissatisfied"
                        2 -> "Dissatisfied"
                        3 -> "Neutral"
                        4 -> "Satisfied"
                        5 -> "Very Satisfied"
                        else -> "Rating: $currentRating"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryColor
                )
            }
        }
    }
}

/**
 * Text input component for survey feedback
 */
@Composable
private fun SurveyTextQuestion(
    currentText: String,
    onTextChanged: (String) -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    OutlinedTextField(
        value = currentText,
        onValueChange = { onTextChanged(it) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        placeholder = { Text("Share your feedback...") },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            cursorColor = primaryColor
        ),
        maxLines = 5
    )

    // Character count
    Text(
        text = "${currentText.length}/500",
        style = MaterialTheme.typography.bodySmall,
        color = theme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.End
    )
}

/**
 * Single choice component for survey
 */
@Composable
private fun SurveyChoiceQuestion(
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOptionSelected(option) },
                color = if (isSelected) primaryColor.copy(alpha = 0.1f) else theme.colors.surface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) primaryColor else theme.colors.outline
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onOptionSelected(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Multiple choice component for survey
 */
@Composable
private fun SurveyMultiChoiceQuestion(
    options: List<String>,
    selectedOptions: List<String>,
    onOptionsChanged: (List<String>) -> Unit,
    primaryColor: Color
) {
    val theme = ConferbotThemeAmbient.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option in selectedOptions

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val newSelection = if (isSelected) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                        onOptionsChanged(newSelection)
                    },
                color = if (isSelected) primaryColor.copy(alpha = 0.1f) else theme.colors.surface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) primaryColor else theme.colors.outline
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            val newSelection = if (isSelected) {
                                selectedOptions - option
                            } else {
                                selectedOptions + option
                            }
                            onOptionsChanged(newSelection)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Compact inline survey for simpler post-chat feedback
 * Shows a single rating question inline in the chat
 */
@Composable
fun InlinePostChatSurvey(
    question: String = "How was your experience?",
    onRatingSubmitted: (Int) -> Unit,
    primaryColor: Color = ConferbotThemeAmbient.current.colors.primary,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    var selectedRating by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.colors.botBubble.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!submitted) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..5).forEach { rating ->
                        IconButton(
                            onClick = {
                                selectedRating = rating
                                submitted = true
                                onRatingSubmitted(rating)
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedRating != null && rating <= (selectedRating ?: 0)) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Filled.StarOutline
                                },
                                contentDescription = "Rating $rating",
                                tint = if (selectedRating != null && rating <= (selectedRating ?: 0)) {
                                    Color(0xFFFFB300)
                                } else {
                                    Color.Gray
                                },
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Submitted",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thank you for your feedback!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
