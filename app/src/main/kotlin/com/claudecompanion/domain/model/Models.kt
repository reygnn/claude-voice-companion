package com.claudecompanion.domain.model

import java.time.Instant

data class Message(
    val id: Long = 0,
    val role: Role,
    val content: String,
    val timestamp: Instant = Instant.now()
)

enum class Role(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system")
}

data class Conversation(
    val id: Long = 0,
    val title: String,
    val createdAt: Instant = Instant.now(),
    val lastMessageAt: Instant = Instant.now(),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val messages: List<Message> = emptyList()
)

const val DEFAULT_SYSTEM_PROMPT =
    "You are a thoughtful conversational partner. Keep your answers " +
            "conversational – not too long, not too short."
