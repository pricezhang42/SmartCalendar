package com.example.smartcalendar.ui.ai

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false
)
