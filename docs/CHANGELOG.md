# Changelog

All notable changes to the Conferbot Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-11-25

### Added
- Initial release of Conferbot Android SDK
- Native Kotlin implementation with Coroutines and Flow
- XML Views support with RecyclerView and Material Design
- Jetpack Compose support with Material Design 3
- Socket.IO integration for real-time messaging
- Retrofit integration for REST API calls
- Full chat functionality (send/receive messages)
- Live agent handover support
- Typing indicators (send and receive)
- Connection status monitoring
- Message history and session management
- Push notification support (FCM ready)
- User identification and metadata
- Customization options (colors, fonts, UI)
- Event listener interface for custom integrations
- Offline message queuing
- StateFlow-based reactive state management
- ProGuard/R8 consumer rules
- Comprehensive documentation
- Example app with XML and Compose implementations

### Features
- **Data Models**: Agent, Message (sealed class), ChatSession, SocketEvents
- **Services**: ApiClient (Retrofit), SocketClient (Socket.IO)
- **Core**: Conferbot singleton with StateFlow
- **UI**: ChatActivity, MessageAdapter, Compose screens
- **Configuration**: ConferBotConfig, ConferBotUser, ConferBotCustomization

### Supported Platforms
- Android 5.0 (API 21) and higher
- Kotlin 1.9+
- Jetpack Compose

### Dependencies
- Retrofit 2.9.0
- OkHttp 4.11.0
- Gson 2.10.1
- Socket.IO-Client 2.1.0
- Jetpack Compose BOM 2023.10.01
- Kotlin Coroutines 1.7.3
- AndroidX Lifecycle 2.6.2

## [Unreleased]

### Planned for 1.1.0
- File upload support
- Audio message support
- Rich media message types
- Canned responses
- Chat transcript export
- Multi-language support
- Accessibility improvements
- Analytics integration
- Message encryption
- Offline mode improvements

### Planned for 1.2.0
- Video call integration
- Screen sharing
- Co-browsing
- Voice messages
- Emoji reactions
- Message search
- Chat tags and categories
- Advanced customization options

---

[1.0.0]: https://github.com/conferbot/android-sdk/releases/tag/v1.0.0
