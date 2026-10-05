package com.codotype.jarvis.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** A single turn in the conversation. */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun user(text: String) = ChatMessage(role = ROLE_USER, content = text)
        fun assistant(text: String) = ChatMessage(role = ROLE_ASSISTANT, content = text)
    }
}
