package com.conferbot.sdk.services

import com.conferbot.sdk.core.livechat.EmailTranscript
import com.conferbot.sdk.models.ChatSession
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.models.RecordItemDeserializer
import com.conferbot.sdk.utils.ConferBotNetworkConfig
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.conferbot.sdk.notifications.PushContract
import com.conferbot.sdk.notifications.PushRegisterError
import com.conferbot.sdk.notifications.PushRegisterResult
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * API response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Retrofit API service interface
 */
interface ConferBotApiService {
    @POST("session/init")
    suspend fun initSession(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<ChatSession>>

    @GET("session/{chatSessionId}")
    suspend fun getSessionHistory(
        @Path("chatSessionId") chatSessionId: String
    ): Response<ApiResponse<Map<String, Any>>>

    /**
     * Store-only (embed-server #194): body `{ record, answerVariables, visitorId? }`,
     * answer `{ success, data: { success, responseId, storeOnly: true } }`, 423
     * CONVERSATION_LOCKED when an agent locked the chat. It never runs the bot.
     */
    @POST("session/{chatSessionId}/message")
    suspend fun sendMessage(
        @Path("chatSessionId") chatSessionId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<Any>>

    /** "Email me this chat" (embed-server #193; the bot is the X-Bot-ID header's). */
    @POST(EmailTranscript.PATH)
    suspend fun transcriptEmail(
        @Body body: Map<String, String>
    ): Response<Map<String, @JvmSuppressWildcards Any?>>

    /** embed-server #189 (docs/mobile-push.md); the bot is the X-Bot-ID header's. */
    @POST(PushContract.REGISTER_PATH)
    suspend fun pushRegister(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST(PushContract.UNREGISTER_PATH)
    suspend fun pushUnregister(
        @Body body: Map<String, String>
    ): Response<Map<String, @JvmSuppressWildcards Any?>>
}

/**
 * API client for REST endpoints
 */
class ApiClient(
    private val apiKey: String,
    private val botId: String,
    baseUrl: String = Constants.DEFAULT_API_BASE_URL,
    enableLogging: Boolean = false,
    /** Tests only: answers in place of the network (the headers are already set). */
    testInterceptor: Interceptor? = null
) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(RecordItem::class.java, RecordItemDeserializer())
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ConferBotNetworkConfig.apiTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(ConferBotNetworkConfig.apiTimeout, TimeUnit.MILLISECONDS)
        .writeTimeout(ConferBotNetworkConfig.apiTimeout, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header(Constants.HEADER_API_KEY, apiKey)
                .header(Constants.HEADER_BOT_ID, botId)
                .header(Constants.HEADER_PLATFORM, Constants.PLATFORM_IDENTIFIER)
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .apply {
            testInterceptor?.let { addInterceptor(it) }
            if (enableLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val api: ConferBotApiService = retrofit.create(ConferBotApiService::class.java)

    /**
     * Initialize a new chat session
     */
    suspend fun initSession(userId: String? = null): ApiResponse<ChatSession> {
        return try {
            val body = mutableMapOf<String, Any?>(
                "botId" to botId,
                "platform" to Constants.PLATFORM_IDENTIFIER
            )
            if (userId != null) {
                body["userId"] = userId
            }

            val response = api.initSession(body)
            if (response.isSuccessful) {
                response.body() ?: ApiResponse(
                    success = false,
                    error = "Empty response body"
                )
            } else {
                ApiResponse(
                    success = false,
                    error = "Failed to initialize session: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Get session history
     */
    suspend fun getSessionHistory(chatSessionId: String): ApiResponse<List<RecordItem>> {
        return try {
            val response = api.getSessionHistory(chatSessionId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val recordData = body.data["record"] as? List<*>
                    val recordItems = recordData?.mapNotNull { item ->
                        if (item is Map<*, *>) {
                            val json = gson.toJson(item)
                            gson.fromJson(json, RecordItem::class.java)
                        } else null
                    } ?: emptyList()

                    ApiResponse(
                        success = true,
                        data = recordItems
                    )
                } else {
                    ApiResponse(
                        success = false,
                        error = body?.error ?: "Failed to get session history"
                    )
                }
            } else {
                ApiResponse(
                    success = false,
                    error = "Failed to get session history: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Store the conversation record over REST - STORE ONLY.
     *
     * POST {mobile base}/session/{chatSessionId}/message with
     * `{ record, answerVariables, visitorId? }`: the whole record, as the
     * socket's response-record sends it. The server creates or updates the
     * conversation and merges the answer variables, but never runs the bot:
     * no AI agent or bot reply, no live-chat relay (embed-server #194). Its
     * answer's data carries `storeOnly: true`. A locked conversation (an agent
     * froze it) stores nothing: success false, error CONVERSATION_LOCKED.
     * Never throws.
     *
     * The SDK does not call this: messages, and the offline queue, go over the
     * socket (Conferbot.sendMessage), which is where replies arrive.
     */
    suspend fun sendMessage(
        chatSessionId: String,
        record: List<Any?>,
        answerVariables: List<Any?> = emptyList(),
        visitorId: String? = null
    ): ApiResponse<Map<String, Any?>> {
        return try {
            val body = mutableMapOf<String, Any?>(
                "record" to record,
                "answerVariables" to answerVariables
            )
            if (visitorId != null) body["visitorId"] = visitorId
            val response = api.sendMessage(chatSessionId, body)
            @Suppress("UNCHECKED_CAST")
            val data = (response.body()?.data ?: errorBodyOf(response)?.get("data")) as? Map<String, Any?>
            when {
                response.isSuccessful -> ApiResponse(success = response.body()?.success ?: false, data = data, error = if (response.body() == null) "Empty response body" else null)
                response.code() == 423 -> ApiResponse(success = false, data = data, error = "CONVERSATION_LOCKED")
                else -> ApiResponse(success = false, data = data, error = "Failed to store the record: ${response.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * The old single-message form. The route never took `{ message, metadata }`
     * (it stores a whole record and never answers), so this sends nothing and
     * returns success false; send messages with Conferbot.sendMessage (the
     * socket), or store a record with the `record` overload.
     */
    @Deprecated(
        "The REST route is store-only and takes the whole record; send messages with Conferbot.sendMessage (the socket)",
        ReplaceWith("com.conferbot.sdk.core.Conferbot.sendMessage(message)")
    )
    @Suppress("UNUSED_PARAMETER")
    suspend fun sendMessage(
        chatSessionId: String,
        message: String,
        metadata: Map<String, Any>? = null
    ): ApiResponse<Any> = ApiResponse(
        success = false,
        error = "STORE_ONLY_ROUTE: POST session/{id}/message stores { record, answerVariables } and never sends a message; use Conferbot.sendMessage"
    )

    private fun errorBodyOf(response: Response<*>): Map<String, Any?>? = try {
        response.errorBody()?.string()?.let {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(it, Map::class.java) as? Map<String, Any?>
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Register a push token with a conversation (POST {mobile base}/push/register
     * `{ token, chatSessionId, tokenType, deviceInfo? }`, no visitorId: the
     * server takes the conversation's). Never throws: a failure is the
     * server's error code, NETWORK when it could not be reached.
     */
    suspend fun registerPush(body: Map<String, Any>): PushRegisterResult {
        return try {
            val response = api.pushRegister(body)
            PushContract.registerResult(response.code(), bodyOf(response))
        } catch (e: Exception) {
            PushRegisterResult(false, PushRegisterError.NETWORK)
        }
    }

    /** Stop pushes to this token (POST {mobile base}/push/unregister `{ token }`). Never throws. */
    suspend fun unregisterPush(token: String): Boolean {
        return try {
            val response = api.pushUnregister(PushContract.unregisterBody(token))
            response.isSuccessful && bodyOf(response)?.get("success") != false
        } catch (e: Exception) {
            false
        }
    }

    private fun bodyOf(response: Response<Map<String, Any?>>): Map<String, Any?>? =
        if (response.isSuccessful) response.body() else response.errorBody()?.string()?.let { raw ->
            runCatching { gson.fromJson<Map<String, Any?>>(raw, object : TypeToken<Map<String, Any?>>() {}.type) }.getOrNull()
        }

    /**
     * "Email me this chat": the server sends the conversation to this address
     * only (POST {mobile base}/chat/transcript-email). Never throws: a
     * failure reads as not sent, with the server's reason when it gave one
     * (a 400 invalid address, a 429 rate limit).
     */
    suspend fun sendTranscriptEmail(chatSessionId: String, email: String): EmailTranscript.Result {
        return try {
            val response = api.transcriptEmail(EmailTranscript.body(chatSessionId, email))
            val data: Map<String, Any?>? = if (response.isSuccessful) response.body() else response.errorBody()?.string()?.let { raw ->
                runCatching { gson.fromJson<Map<String, Any?>>(raw, object : TypeToken<Map<String, Any?>>() {}.type) }.getOrNull()
            }
            EmailTranscript.result(response.code(), data)
        } catch (e: Exception) {
            EmailTranscript.Result(false)
        }
    }
}
