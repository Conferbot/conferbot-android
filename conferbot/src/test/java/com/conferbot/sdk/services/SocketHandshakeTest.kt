package com.conferbot.sdk.services

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The handshake carries the visitor, and a blocked one is told apart (web core/socket.ts). */
class SocketHandshakeTest {

    @Test
    fun `the handshake names the bot and the visitor`() {
        assertThat(SocketClient.handshakeQuery("b1", "v_1 x")).isEqualTo("botId=b1&visitorId=v_1+x")
        assertThat(SocketClient.handshakeQuery("b1", null)).isEqualTo("botId=b1")
        assertThat(SocketClient.handshakeQuery("b1", " ")).isEqualTo("botId=b1")
        assertThat(SocketClient.handshakeAuth("b1", "v1")).isEqualTo(mapOf("botId" to "b1", "visitorId" to "v1"))
        assertThat(SocketClient.handshakeAuth("b1", null)).isEqualTo(mapOf("botId" to "b1"))
    }

    @Test
    fun `a connect error's message, from the client's exception or a string`() {
        assertThat(SocketClient.connectErrorMessage(RuntimeException("visitor_blocked"))).isEqualTo("visitor_blocked")
        assertThat(SocketClient.connectErrorMessage("visitor_blocked")).isEqualTo("visitor_blocked")
        assertThat(SocketClient.connectErrorMessage(null)).isNull()
    }
}
