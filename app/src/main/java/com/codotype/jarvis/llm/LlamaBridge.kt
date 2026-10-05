package com.codotype.jarvis.llm

import com.codotype.jarvis.native.NativeLoader

/** Invoked for every generated token. Return false to stop generation early. */
fun interface TokenCallback {
    fun onToken(token: String): Boolean
}

/**
 * Thin JNI surface over llama.cpp (see cpp/jarvis_llm.cpp).
 * All handles are opaque pointers returned by [loadModel].
 */
object LlamaBridge {

    init { NativeLoader.load() }

    val available: Boolean get() = NativeLoader.llmAvailable

    external fun loadModel(
        path: String,
        nThreads: Int,
        nCtx: Int,
        nBatch: Int,
        useMmap: Boolean
    ): Long

    external fun freeModel(handle: Long)

    external fun stop(handle: Long)

    external fun contextSize(handle: Long): Int

    external fun applyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistant: Boolean
    ): String

    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): String

    external fun systemInfo(): String
}
