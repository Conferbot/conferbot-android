@file:Suppress("unused")
package com.conferbot.sdk.ui.compose

/**
 * Deprecated — use the modular equivalents in the `messages` subpackage.
 *
 * This file re-exports the message bubble composables so existing code
 * that imports from `com.conferbot.sdk.ui.compose` continues to compile.
 */

// Re-export via typealias is not available for functions in Kotlin,
// so this file is intentionally left minimal.
// All bubble composables are now in:
//   com.conferbot.sdk.ui.compose.messages.MessageBubble
//   com.conferbot.sdk.ui.compose.messages.BotAvatar
