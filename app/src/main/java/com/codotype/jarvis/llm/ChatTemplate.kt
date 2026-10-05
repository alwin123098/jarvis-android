package com.codotype.jarvis.llm

import com.codotype.jarvis.data.ChatMessage

/**
 * Turns a conversation into a model-ready prompt. Prefers the model's own
 * built-in chat template (via llama.cpp); falls back to ChatML, which Qwen and
 * most modern instruct models accept.
 */
object ChatTemplate {

    private const val SYSTEM_PREAMBLE =
        "You are Jarvis, a concise, friendly on-device AI assistant. " +
        "Answer in the user's language. If you are unsure, say so plainly."

    fun buildPrompt(
        handle: Long,
        history: List<ChatMessage>,
        systemPrompt: String = SYSTEM_PREAMBLE,
        retrievedContext: String? = null
    ): String {
        val messages = ArrayList<ChatMessage>(history.size + 1)
        val system = buildString {
            append(systemPrompt)
            if (!retrievedContext.isNullOrBlank()) {
                append("\n\nUse the following reference notes when relevant:\n")
                append(retrievedContext)
            }
        }
        messages.add(ChatMessage(role = ChatMessage.ROLE_SYSTEM, content = system))
        messages.addAll(history)

        if (LlamaBridge.available && handle != 0L) {
            val roles = messages.map { it.role }.toTypedArray()
            val contents = messages.map { it.content }.toTypedArray()
            val formatted = runCatching {
                LlamaBridge.applyChatTemplate(handle, roles, contents, true)
            }.getOrDefault("")
            if (formatted.isNotBlank()) return formatted
        }
        return chatMl(messages)
    }

    /** Fallback ChatML renderer. */
    private fun chatMl(messages: List<ChatMessage>): String = buildString {
        for (m in messages) {
            append("<|im_start|>").append(m.role).append('\n')
            append(m.content).append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }
}
