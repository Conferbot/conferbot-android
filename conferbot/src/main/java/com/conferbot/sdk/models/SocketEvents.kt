package com.conferbot.sdk.models

/**
 * Socket events matching embed-server socket.js
 */
object SocketEvents {
    // Client to server events
    const val GET_CHATBOT_DATA = "get-chatbot-data"
    const val RESPONSE_RECORD = "response-record" // Send visitor message/response
    const val JOIN_CHAT_ROOM = "join-chat-room-visitor"
    const val LEAVE_CHAT_ROOM = "leave-chat-room"
    const val VISITOR_TYPING = "visitor-typing"
    const val INITIATE_HANDOVER = "initiate-handover"
    const val ACCEPT_HANDOVER = "accept-handover"
    const val END_CHAT = "end-chat"
    const val EMAIL_NODE_TRIGGER = "email-node-trigger"
    const val ZAPIER_NODE_TRIGGER = "zapier-node-trigger"
    const val CALENDAR_SLOT_SELECTION_RECORD = "calendar-slot-selection-record"
    const val TOGGLE_VISITOR_INPUT = "toggle-visitor-input"
    const val EXECUTE_INTEGRATION = "execute-integration" // Execute native integrations (Stripe, etc.)
    const val POST_CHAT_SURVEY_RESPONSE = "post-chat-survey-response" // Submit post-chat survey responses

    // Push notification events
    const val REGISTER_PUSH_TOKEN = "register-push-token"
    const val UNREGISTER_PUSH_TOKEN = "unregister-push-token"

    // Server to client events
    const val FETCHED_CHATBOT_DATA = "fetched-chatbot-data"
    const val BOT_RESPONSE = "bot-response"
    const val AGENT_MESSAGE = "agent-message"
    const val AGENT_ACCEPTED = "agent-accepted"
    const val AGENT_LEFT = "agent-left"
    const val AGENT_TYPING_STATUS = "agent-typing-status"
    const val VISITOR_TYPING_STATUS = "visitor-typing-status"
    const val CHAT_ENDED = "chat-ended"
    const val NO_AGENTS_AVAILABLE = "no-agents-available"
    const val VISITOR_DISCONNECTED = "visitor-disconnected"
    const val VISITOR_INPUT_TOGGLED = "visitor-input-toggled"
    const val DESTROY_NOTIFICATION = "destroy-notification"
    const val INTEGRATION_RESULT = "integration-result" // Result from native integration execution

    // Connection events
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val CONNECT_ERROR = "connect_error"
    const val RECONNECT = "reconnect"
    const val RECONNECT_ATTEMPT = "reconnect_attempt"
    const val RECONNECT_ERROR = "reconnect_error"
    const val RECONNECT_FAILED = "reconnect_failed"

    // Analytics events
    const val TRACK_CHAT_START = "track-chat-start"
    const val TRACK_NODE_VISIT = "track-node-visit"
    const val TRACK_NODE_EXIT = "track-node-exit"
    const val TRACK_SENTIMENT = "track-sentiment"
    const val TRACK_INTERACTION = "track-interaction"
    const val TRACK_GOAL_COMPLETION = "track-goal-completion"
    const val TRACK_CHAT_ENGAGEMENT = "track-chat-engagement"
    const val TRACK_DROP_OFF = "track-drop-off"
    const val SUBMIT_CHAT_RATING = "submit-chat-rating"
    const val FINALIZE_ANALYTICS = "finalize-analytics"

    // Knowledge Base events
    const val GET_KNOWLEDGE_BASE_DATA = "get-knowledge-base-data"
    const val KNOWLEDGE_BASE_DATA = "knowledge-base-data"
    const val TRACK_ARTICLE_VIEW = "track-article-view"
    const val TRACK_ARTICLE_ENGAGEMENT = "track-article-engagement"
    const val RATE_ARTICLE = "rate-article"
    const val ARTICLE_RATED = "article-rated"
    const val SEARCH_ARTICLES = "search-articles"
    const val SEARCH_RESULTS = "search-results"
}
