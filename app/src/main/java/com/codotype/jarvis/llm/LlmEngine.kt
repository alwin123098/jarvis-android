package com.codotype.jarvis.llm

import com.codotype.jarvis.data.ChatMessage
import com.codotype.jarvis.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lifecycle + inference state of the local model. */
sealed interface LlmState {
    data object Idle : LlmState
    data object Loading : LlmState
    data class Ready(val contextSize: Int) : LlmState
    data class Error(val message: String) : LlmState
}

/**
 * Owns a single llama.cpp session. Inference is serialised on a dedicated
 * dispatcher so the UI thread is never blocked and two generations can never
 * race on the same context.
 */
class LlmEngine {

    private var handle: Long = 0L
    private val lock = Mutex()

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    val state: StateFlow<LlmState> = _state.asStateFlow()

    val isReady: Boolean get() = handle != 0L

    suspend fun load(spec: ModelSpec, modelPath: String, contextSize: Int = 2048) =
        withContext(Dispatchers.Default) {
            lock.withLock {
                if (handle != 0L) unloadInternal()
                _state.value = LlmState.Loading
                if (!LlamaBridge.available) {
                    _state.value = LlmState.Error(
                        "Native engine missing. Build with -Pjarvis.buildNative=true."
                    )
                    return@withLock
                }
                val threads = (Runtime.getRuntime().availableProcessors() - 1)
                    .coerceIn(2, 6)
                val h = runCatching {
                    LlamaBridge.loadModel(
                        path = modelPath,
                        nThreads = threads,
                        nCtx = contextSize,
                        nBatch = 512,
                        useMmap = true
                    )
                }.getOrDefault(0L)

                if (h == 0L) {
                    _state.value = LlmState.Error("Could not load ${spec.displayName}.")
                } else {
                    handle = h
                    _state.value = LlmState.Ready(LlamaBridge.contextSize(h))
                }
            }
        }

    fun unload() {
        if (handle != 0L) unloadInternal()
        _state.value = LlmState.Idle
    }

    private fun unloadInternal() {
        runCatching { LlamaBridge.freeModel(handle) }
        handle = 0L
    }

    fun requestStop() {
        if (handle != 0L) runCatching { LlamaBridge.stop(handle) }
    }

    /**
     * Streams a completion for [history]. [onToken] is called per token on the
     * inference thread; return false from it to abort.
     */
    suspend fun generate(
        history: List<ChatMessage>,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f,
        retrievedContext: String? = null,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.Default) {
        lock.withLock {
            val h = handle
            if (h == 0L) return@withLock ""
            val prompt = ChatTemplate.buildPrompt(h, history, retrievedContext = retrievedContext)
            runCatching {
                LlamaBridge.generate(h, prompt, maxTokens, temperature, topK, topP) { token ->
                    onToken(token)
                    true
                }
            }.getOrDefault("")
        }
    }
}
