package com.example.twinmindclone.data.remote.groq

import com.google.gson.annotations.SerializedName

data class TranscriptionResponse(
    @SerializedName("text") val text: String
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("stream") val stream: Boolean = true,
    @SerializedName("max_tokens") val maxTokens: Int = 1024
)

data class ChatCompletionChunk(
    @SerializedName("choices") val choices: List<StreamChoice> = emptyList()
)

data class StreamChoice(
    @SerializedName("delta") val delta: StreamDelta,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class StreamDelta(
    @SerializedName("content") val content: String? = null
)

data class StructuredSummary(
    val title: String = "",
    val summary: String = "",
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList()
)
