package com.conferbot.sdk.services

import android.util.Log
import com.conferbot.sdk.models.SocketEvents
import com.conferbot.sdk.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Socket client for real-time communication
 */
class SocketClient(
    private val apiKey: String,
    private val botId: String,
    socketUrl: String = Constants.DEFAULT_SOCKET_URL
) {
    private var socket: Socket? = null
    private var _isConnected = false

    val isConnected: Boolean
        get() = _isConnected

    init {
        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionAttempts = Constants.SOCKET_RECONNECTION_ATTEMPTS
                reconnectionDelay = Constants.SOCKET_RECONNECTION_DELAY.toLong()
                reconnectionDelayMax = Constants.SOCKET_RECONNECTION_DELAY_MAX.toLong()
                timeout = Constants.SOCKET_TIMEOUT
                extraHeaders = mapOf(
                    Constants.HEADER_API_KEY to listOf(apiKey),
                    Constants.HEADER_BOT_ID to listOf(botId),
                    Constants.HEADER_PLATFORM to listOf(Constants.PLATFORM_IDENTIFIER)
                )
            }

            socket = IO.socket(socketUrl, options)
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to create socket", e)
        }
    }

    /**
     * Connect to socket server
     */
    fun connect() {
        if (socket?.connected() == true) {
            return
        }

        setupConnectionHandlers()
        socket?.connect()
    }

    /**
     * Setup connection event handlers
     */
    private fun setupConnectionHandlers() {
        socket?.on(SocketEvents.CONNECT) {
            _isConnected = true
            Log.d(TAG, "Connected")
        }

        socket?.on(SocketEvents.DISCONNECT) {
            _isConnected = false
            Log.d(TAG, "Disconnected")
        }

        socket?.on(SocketEvents.CONNECT_ERROR) { args ->
            _isConnected = false
            Log.e(TAG, "Connection error: ${args.firstOrNull()}")
        }

        socket?.on(SocketEvents.RECONNECT) {
            _isConnected = true
            Log.d(TAG, "Reconnected")
        }
    }

    /**
     * Initialize mobile session
     */
    fun mobileInit(
        chatSessionId: String,
        visitorId: String? = null,
        deviceInfo: Map<String, String>? = null
    ) {
        val data = JSONObject().apply {
            put("botId", botId)
            put("chatSessionId", chatSessionId)
            put("platform", Constants.PLATFORM_IDENTIFIER)
            visitorId?.let { put("visitorId", it) }
            deviceInfo?.let {
                put("deviceInfo", JSONObject(it))
            }
        }
        emit(SocketEvents.MOBILE_INIT, data)
    }

    /**
     * Join chat room as visitor
     */
    fun joinChatRoom(chatSessionId: String) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
        }
        emit(SocketEvents.JOIN_CHAT_ROOM, data)
    }

    /**
     * Leave chat room
     */
    fun leaveChatRoom(chatSessionId: String) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
        }
        emit(SocketEvents.LEAVE_CHAT_ROOM, data)
    }

    /**
     * Send visitor message
     */
    fun sendVisitorMessage(
        chatSessionId: String,
        record: Map<String, Any>,
        answerVariables: List<Any>,
        visitorMeta: Map<String, Any>? = null
    ) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("record", JSONObject(record))
            put("answerVariables", answerVariables)
            put("botId", botId)
            visitorMeta?.let { put("visitorMeta", JSONObject(it)) }
        }
        emit(SocketEvents.SEND_VISITOR_MESSAGE, data)
    }

    /**
     * Send visitor typing status
     */
    fun sendTypingStatus(chatSessionId: String, isTyping: Boolean) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            put("isTyping", isTyping)
        }
        emit(SocketEvents.VISITOR_TYPING, data)
    }

    /**
     * Initiate handover to live agent
     */
    fun initiateHandover(chatSessionId: String, message: String? = null) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
            message?.let { put("message", it) }
        }
        emit(SocketEvents.INITIATE_HANDOVER, data)
    }

    /**
     * End chat
     */
    fun endChat(chatSessionId: String) {
        val data = JSONObject().apply {
            put("chatSessionId", chatSessionId)
        }
        emit(SocketEvents.END_CHAT, data)
    }

    /**
     * Emit event
     */
    fun emit(event: String, data: Any) {
        if (socket?.connected() != true) {
            Log.w(TAG, "Cannot emit - not connected")
            return
        }
        socket?.emit(event, data)
    }

    /**
     * Listen to event
     */
    fun on(event: String, callback: Emitter.Listener) {
        socket?.on(event, callback)
    }

    /**
     * Remove event listener
     */
    fun off(event: String, callback: Emitter.Listener? = null) {
        if (callback != null) {
            socket?.off(event, callback)
        } else {
            socket?.off(event)
        }
    }

    /**
     * Disconnect from socket
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        _isConnected = false
    }

    /**
     * Dispose of resources
     */
    fun dispose() {
        disconnect()
    }

    companion object {
        private const val TAG = "ConferBot-Socket"
    }
}
