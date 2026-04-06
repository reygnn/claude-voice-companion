package com.claudecompanion.data.remote

import com.claudecompanion.domain.model.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicStreamingClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    fun streamMessage(
        apiKey: String,
        messages: List<Message>,
        systemPrompt: String
    ): Flow<StreamEvent> = callbackFlow {
        val body = buildJsonObject {
            put("model", "claude-sonnet-4-20250514")
            put("max_tokens", 4096)
            put("stream", true)
            put("system", systemPrompt)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role.value)
                        put("content", msg.content)
                    }
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = httpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    trySend(StreamEvent.Error("API error: ${response.code}"))
                    close()
                    return
                }
                response.body?.source()?.use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ")
                            if (data == "[DONE]") break
                            parseStreamEvent(data)?.let { trySend(it) }
                        }
                    }
                }
                trySend(StreamEvent.Done)
                close()
            }

            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamEvent.Error(e.message ?: "Network error"))
                close(e)
            }
        })

        awaitClose { call.cancel() }
    }

    private fun parseStreamEvent(json: String): StreamEvent? {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "content_block_delta" -> {
                    val text = obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                    text?.let { StreamEvent.TextDelta(it) }
                }
                "message_stop" -> StreamEvent.Done
                "error" -> {
                    val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    StreamEvent.Error(msg ?: "Unknown error")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data object Done : StreamEvent
    data class Error(val message: String) : StreamEvent
}
