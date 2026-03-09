package com.example.twinmindclone.data.remote.groq

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface GroqApiService {

    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): TranscriptionResponse

    @Streaming
    @POST("openai/v1/chat/completions")
    suspend fun streamChatCompletion(
        @Body request: ChatCompletionRequest
    ): ResponseBody
}
