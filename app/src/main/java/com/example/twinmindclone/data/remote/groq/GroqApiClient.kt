package com.example.twinmindclone.data.remote.groq

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqApiClient @Inject constructor(
    private val groqApiService: GroqApiService,
    private val gson: Gson
) {

    companion object {
        private const val TRANSCRIPTION_MODEL = "whisper-large-v3"
        private const val SUMMARY_MODEL = "llama-3.3-70b-versatile"

        private const val SUMMARY_SYSTEM_PROMPT = """You are a meeting assistant. Analyze the transcript and respond with EXACTLY this format — no extra text before or after:

Title: [concise meeting title, max 8 words]

Summary:
[2-3 sentences describing what was discussed]

Action Items:
• [action item 1]
• [action item 2]

Key Points:
• [key point 1]
• [key point 2]"""
    }

    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String = "audio/wav"): String {
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = "audio.wav",
            body = audioBytes.toRequestBody(mimeType.toMediaType())
        )
        val model = TRANSCRIPTION_MODEL.toRequestBody("text/plain".toMediaType())
        return groqApiService.transcribeAudio(filePart, model).text
    }

    fun streamSummary(transcript: String): Flow<String> = flow {
        val request = ChatCompletionRequest(
            model = SUMMARY_MODEL,
            messages = listOf(
                ChatMessage(role = "system", content = SUMMARY_SYSTEM_PROMPT),
                ChatMessage(role = "user", content = "Meeting transcript:\n\n$transcript")
            ),
            stream = true,
            maxTokens = 1024
        )

        val responseBody = groqApiService.streamChatCompletion(request)
        responseBody.charStream().buffered().use { reader ->
            for (line in reader.lineSequence()) {
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
                    val content = chunk.choices.firstOrNull()?.delta?.content
                    if (!content.isNullOrEmpty()) emit(content)
                } catch (_: Exception) {
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun parseSummary(text: String): StructuredSummary {
        val lines = text.lines()
        var title = ""
        val summaryLines = mutableListOf<String>()
        val actionItems = mutableListOf<String>()
        val keyPoints = mutableListOf<String>()
        var section = ""

        for (line in lines) {
            val trimmed = line.trim()
                .removePrefix("**").removeSuffix("**")
                .removePrefix("*").removeSuffix("*")
                .trim()

            when {
                trimmed.startsWith("Title:", ignoreCase = true) -> {
                    title = trimmed.drop("Title:".length).trim()
                    section = "title"
                }
                trimmed.equals("Summary:", ignoreCase = true) ||
                trimmed.equals("Summary", ignoreCase = true) -> {
                    section = "summary"
                }
                trimmed.contains("action item", ignoreCase = true) &&
                (trimmed.endsWith(":") || trimmed.endsWith("s:")) -> {
                    section = "actions"
                }
                trimmed.contains("key point", ignoreCase = true) &&
                (trimmed.endsWith(":") || trimmed.endsWith("s:")) -> {
                    section = "keypoints"
                }
                isBulletLine(trimmed) -> {
                    val item = stripBullet(trimmed)
                    if (item.isNotBlank()) {
                        when (section) {
                            "actions" -> actionItems.add(item)
                            "keypoints" -> keyPoints.add(item)
                        }
                    }
                }
                section == "summary" && trimmed.isNotBlank() -> summaryLines.add(trimmed)
            }
        }

        return StructuredSummary(
            title = title,
            summary = summaryLines.joinToString(" "),
            actionItems = actionItems,
            keyPoints = keyPoints
        )
    }

    private fun isBulletLine(trimmed: String): Boolean =
        trimmed.startsWith("• ") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.matches(Regex("^\\d+[.):]\\s+.*"))

    private fun stripBullet(trimmed: String): String = when {
        trimmed.startsWith("• ") -> trimmed.removePrefix("• ").trim()
        trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
        trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
        trimmed.matches(Regex("^\\d+[.):]\\s+.*")) ->
            trimmed.replaceFirst(Regex("^\\d+[.):] +"), "").trim()
        else -> trimmed
    }
}
