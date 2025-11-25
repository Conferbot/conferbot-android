#!/usr/bin/env kotlin

/**
 * Connection Test for Conferbot Android SDK
 * Tests connection to embed server on localhost:8001
 *
 * Run with:
 * kotlinc test_connection.kt -include-runtime -d test_connection.jar && java -jar test_connection.jar
 *
 * Or with gradle:
 * ./gradlew runConnectionTest
 */

import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

// Configuration
const val SOCKET_URL = "http://localhost:8001"
const val TEST_API_KEY = "test_api_key"
const val TEST_BOT_ID = "test_bot_id"

fun main() {
    println("🚀 Starting Conferbot Android SDK Connection Test\n")
    println("Configuration:")
    println("  Socket URL: $SOCKET_URL")
    println("  API Key: $TEST_API_KEY")
    println("  Bot ID: $TEST_BOT_ID\n")

    println("📡 Testing REST API endpoint...")

    try {
        val url = URL("$SOCKET_URL/api/v1/mobile/session/init")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("X-API-Key", TEST_API_KEY)
            setRequestProperty("X-Bot-ID", TEST_BOT_ID)
            setRequestProperty("X-Platform", "android")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }

        // Send request body
        val requestBody = """
            {
                "botId": "$TEST_BOT_ID",
                "userId": "test_user_android"
            }
        """.trimIndent()

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody)
            writer.flush()
        }

        // Get response
        val responseCode = connection.responseCode

        println("✅ REST API connection successful!")
        println("   Status Code: $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK ||
            responseCode == HttpURLConnection.HTTP_CREATED) {

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            println("   Response: $response")

            println("\n✅ Test completed successfully!")
            println("   REST API endpoint is working correctly.\n")

            connection.disconnect()
            exitProcess(0)
        } else {
            println("\n⚠️  Received non-200 status code")
            println("   This might be expected without valid API keys\n")

            connection.disconnect()
            exitProcess(0)
        }

    } catch (e: java.net.ConnectException) {
        println("❌ Connection error: ${e.message}")
        println("   Make sure embed server is running on port 8001\n")
        exitProcess(1)

    } catch (e: java.net.SocketTimeoutException) {
        println("\n❌ Test timed out - no response from server")
        println("   Possible issues:")
        println("   - Embed server not running on port 8001")
        println("   - Firewall blocking connection")
        println("   - Invalid API key or bot ID\n")
        exitProcess(1)

    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
