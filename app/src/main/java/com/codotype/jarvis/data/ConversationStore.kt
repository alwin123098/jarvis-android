package com.codotype.jarvis.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the conversation as a single JSON file in the app's private storage.
 * No network, no database dependency — the whole history is small and local.
 */
class ConversationStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun load(): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<ChatMessage>>(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun save(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        runCatching { file.writeText(json.encodeToString(messages)) }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        Unit
    }
}
