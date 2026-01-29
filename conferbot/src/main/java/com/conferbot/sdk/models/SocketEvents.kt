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

    // Connection events
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val CONNECT_ERROR = "connect_error"
    const val RECONNECT = "reconnect"
    const val RECONNECT_ATTEMPT = "reconnect_attempt"
    const val RECONNECT_ERROR = "reconnect_error"
    const val RECONNECT_FAILED = "reconnect_failed"
}
