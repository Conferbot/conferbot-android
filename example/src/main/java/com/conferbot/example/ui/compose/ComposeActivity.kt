package com.conferbot.example.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.models.ConferBotUser
import com.conferbot.sdk.ui.compose.ConferBotChatScreen
import com.conferbot.example.ui.compose.theme.ConferBotExampleTheme
import kotlinx.coroutines.launch

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConferBotExampleTheme {
                ComposeExampleScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeExampleScreen() {
    var showChat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Observe SDK state
    val unreadCount by Conferbot.unreadCount.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val currentAgent by Conferbot.currentAgent.collectAsState()
    val messages by Conferbot.record.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Example") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Connection: ${if (isConnected) "Connected" else "Disconnected"}",
                        color = if (isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Text(text = "Agent: ${currentAgent?.name ?: "No agent"}")
                    Text(text = "Messages: ${messages.size}")
                    Text(text = "Unread: $unreadCount")
                }
            }

            // Actions
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium
            )

            // Open Chat Button
            Button(
                onClick = { showChat = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Chat")
                if (unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("$unreadCount") }
                }
            }

            // Identify User
            OutlinedButton(
                onClick = {
                    Conferbot.identify(
                        ConferBotUser(
                            id = "compose-user-123",
                            name = "Compose User",
                            email = "compose@example.com",
                            metadata = mapOf(
                                "ui" to "compose",
                                "example" to true
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Identify User")
            }

            // Send Message
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (Conferbot.chatSessionId.value == null) {
                            Conferbot.initializeSession()
                        }
                        Conferbot.sendMessage("Hello from Compose!")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Test Message")
            }

            // Request Agent
            OutlinedButton(
                onClick = {
                    Conferbot.initiateHandover("User requested agent from Compose")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Request Agent")
            }

            // Clear History
            TextButton(
                onClick = { Conferbot.clearHistory() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear History")
            }
        }
    }

    // Show chat in fullscreen
    if (showChat) {
        ConferBotChatScreen(
            onDismiss = { showChat = false }
        )
    }
}
