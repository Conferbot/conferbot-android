package com.conferbot.sdk.services

import com.conferbot.sdk.models.ChatSession
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.models.RecordItemDeserializer
import com.conferbot.sdk.utils.ConferBotNetworkConfig
import com.conferbot.sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
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

    @POST("session/{chatSessionId}/message")
    suspend fun sendMessage(
        @Path("chatSessionId") chatSessionId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<Any>>

    @POST("push/register")
    suspend fun registerPushToken(
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>
}

/**
 * API client for REST endpoints
 */
class ApiClient(
    private val apiKey: String,
    private val botId: String,
    baseUrl: String = Constants.DEFAULT_API_BASE_URL,
    enableLogging: Boolean = false
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
     * Send a message
     */
    suspend fun sendMessage(
        chatSessionId: String,
        message: String,
        metadata: Map<String, Any>? = null
    ): ApiResponse<Any> {
        return try {
            val body = mutableMapOf<String, Any?>(
                "message" to message
            )
            if (metadata != null) {
                body["metadata"] = metadata
            }

            val response = api.sendMessage(chatSessionId, body)
            if (response.isSuccessful) {
                response.body() ?: ApiResponse(
                    success = false,
                    error = "Empty response body"
                )
            } else {
                ApiResponse(
                    success = false,
                    error = "Failed to send message: ${response.code()}"
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
     * Register push notification token
     */
    suspend fun registerPushToken(
        token: String,
        chatSessionId: String
    ): ApiResponse<Unit> {
        return try {
            val body = mapOf(
                "token" to token,
                "chatSessionId" to chatSessionId,
                "platform" to Constants.PLATFORM_IDENTIFIER
            )

            val response = api.registerPushToken(body)
            if (response.isSuccessful) {
                ApiResponse(success = true)
            } else {
                ApiResponse(
                    success = false,
                    error = "Failed to register push token: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
}
