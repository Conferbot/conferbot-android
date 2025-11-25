package com.conferbot.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.RecordItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message bubble composable
 */
@Composable
fun MessageBubble(
    message: RecordItem,
    modifier: Modifier = Modifier
) {
    when (message) {
        is RecordItem.UserMessage -> UserMessageBubble(message, modifier)
        is RecordItem.BotMessage -> BotMessageBubble(message, modifier)
        is RecordItem.AgentMessage -> AgentMessageBubble(message, modifier)
        is RecordItem.SystemMessage -> SystemMessageBubble(message, modifier)
        is RecordItem.AgentJoinedMessage -> SystemMessageBubble(
            RecordItem.SystemMessage(
                id = message.id,
                time = message.time,
                text = "${message.agentDetails.name} joined the chat"
            ),
            modifier
        )
        else -> {}
    }
}

/**
 * User message bubble
 */
@Composable
fun UserMessageBubble(
    message: RecordItem.UserMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = Color(0xFF0100EC),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = message.text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.time),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Bot message bubble
 */
@Composable
fun BotMessageBubble(
    message: RecordItem.BotMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text ?: "",
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.time),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Agent message bubble
 */
@Composable
fun AgentMessageBubble(
    message: RecordItem.AgentMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.agentDetails.name,
                color = Color(0xFF0100EC),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.time),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * System message bubble
 */
@Composable
fun SystemMessageBubble(
    message: RecordItem.SystemMessage,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message.text,
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * Format time
 */
private fun formatTime(date: Date): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(date)
}
