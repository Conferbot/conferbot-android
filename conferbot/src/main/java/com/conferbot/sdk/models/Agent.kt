package com.conferbot.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Agent model representing a live agent
 */
data class Agent(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("status")
    val status: String? = null
)

/**
 * Agent details matching embed-server agentDetails structure
 */
data class AgentDetails(
    @SerializedName("_id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null
)
