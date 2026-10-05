package com.codotype.jarvis.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wraps a whisper.cpp context and runs transcription off the UI thread. */
class WhisperEngine {

    private var handle: Long = 0L

    val isReady: Boolean get() = handle != 0L

    suspend fun load(modelPath: String): Boolean = withContext(Dispatchers.Default) {
        if (!WhisperBridge.available) return@withContext false
        if (handle != 0L) unload()
        handle = runCatching { WhisperBridge.loadModel(modelPath) }.getOrDefault(0L)
        handle != 0L
    }

    fun unload() {
        if (handle != 0L) runCatching { WhisperBridge.freeModel(handle) }
        handle = 0L
    }

    suspend fun transcribe(samples: FloatArray, language: String = "en"): String =
        withContext(Dispatchers.Default) {
            val h = handle
            if (h == 0L || samples.isEmpty()) return@withContext ""
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 6)
            runCatching { WhisperBridge.transcribe(h, samples, threads, language) }
                .getOrDefault("")
                .trim()
        }
}
