package com.codotype.jarvis.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codotype.jarvis.audio.AudioRecorder
import com.codotype.jarvis.data.ChatMessage
import com.codotype.jarvis.data.ConversationStore
import com.codotype.jarvis.llm.LlmEngine
import com.codotype.jarvis.llm.LlmState
import com.codotype.jarvis.model.DownloadProgress
import com.codotype.jarvis.model.ModelCatalog
import com.codotype.jarvis.model.ModelRepository
import com.codotype.jarvis.model.ModelSpec
import com.codotype.jarvis.native.NativeLoader
import com.codotype.jarvis.rag.KnowledgeBase
import com.codotype.jarvis.stt.WhisperEngine
import com.codotype.jarvis.tts.TtsEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: String = "",
    val isGenerating: Boolean = false,
    val isListening: Boolean = false,
    val input: String = "",
    val llmState: LlmState = LlmState.Idle,
    val downloadedIds: Set<String> = emptySet(),
    val activeLlmId: String? = null,
    val downloading: DownloadProgress? = null,
    val voiceOutput: Boolean = true,
    val useKnowledge: Boolean = true,
    val knowledgeChunks: Int = 0,
    val showSetup: Boolean = true,
    val nativeAvailable: Boolean = NativeLoader.llmAvailable,
    val error: String? = null
) {
    val hasLlm: Boolean get() = ModelCatalog.defaultLlm.id in downloadedIds
    val hasStt: Boolean get() = ModelCatalog.defaultStt.id in downloadedIds
    val ready: Boolean get() = hasLlm && hasStt && llmState is LlmState.Ready
}

class JarvisViewModel(private val app: Application) : ViewModel() {

    private val repo = ModelRepository(app)
    private val llm = LlmEngine()
    private val whisper = WhisperEngine()
    private val tts = TtsEngine(app)
    private val knowledge = KnowledgeBase(File(app.filesDir, "knowledge"))
    private val recorder = AudioRecorder()
    private val store = ConversationStore(File(app.filesDir, "conversation.json"))

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val amplitude: StateFlow<Float> = recorder.amplitude

    private var generationJob: Job? = null

    init {
        refreshDownloads()
        viewModelScope.launch {
            val saved = store.load()
            _ui.update { it.copy(messages = saved) }
            val chunks = knowledge.load()
            _ui.update { it.copy(knowledgeChunks = chunks) }
            autoLoadIfReady()
        }
    }

    private fun refreshDownloads() {
        val ids = ModelCatalog.all.filter { repo.isDownloaded(it) }.map { it.id }.toSet()
        _ui.update { it.copy(downloadedIds = ids) }
    }

    private suspend fun autoLoadIfReady() {
        val spec = ModelCatalog.defaultLlm
        if (repo.isDownloaded(spec) && llm.state.value !is LlmState.Ready) {
            llm.load(spec, repo.fileFor(spec).absolutePath)
            _ui.update { it.copy(activeLlmId = spec.id, showSetup = !(it.hasStt)) }
        }
        val stt = ModelCatalog.defaultStt
        if (repo.isDownloaded(stt) && !whisper.isReady) {
            whisper.load(repo.fileFor(stt).absolutePath)
        }
        observeLlm()
    }

    private fun observeLlm() {
        viewModelScope.launch {
            llm.state.collect { s -> _ui.update { it.copy(llmState = s) } }
        }
    }

    // ---- Model management --------------------------------------------------

    fun download(spec: ModelSpec) {
        if (_ui.value.downloading != null) return
        viewModelScope.launch {
            repo.download(spec) { p -> _ui.update { it.copy(downloading = p) } }
                .onSuccess {
                    refreshDownloads()
                    _ui.update { it.copy(downloading = null) }
                    if (spec.kind == com.codotype.jarvis.model.ModelKind.LLM) {
                        llm.load(spec, repo.fileFor(spec).absolutePath)
                        _ui.update { it.copy(activeLlmId = spec.id) }
                    } else if (spec.kind == com.codotype.jarvis.model.ModelKind.STT) {
                        whisper.load(repo.fileFor(spec).absolutePath)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(downloading = null, error = e.message ?: "Download failed") }
                }
        }
    }

    fun delete(spec: ModelSpec) {
        repo.delete(spec)
        refreshDownloads()
    }

    fun selectLlm(spec: ModelSpec) {
        if (!repo.isDownloaded(spec)) return
        viewModelScope.launch { llm.load(spec, repo.fileFor(spec).absolutePath) }
        _ui.update { it.copy(activeLlmId = spec.id) }
    }

    fun continueToChat() = _ui.update { it.copy(showSetup = false) }
    fun openSetup() = _ui.update { it.copy(showSetup = true) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    // ---- Chat --------------------------------------------------------------

    fun onInputChange(value: String) = _ui.update { it.copy(input = value) }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.isGenerating) return
        _ui.update { it.copy(input = "") }
        submit(text)
    }

    private fun submit(text: String) {
        val userMsg = ChatMessage.user(text)
        val history = _ui.value.messages + userMsg
        _ui.update { it.copy(messages = history, streaming = "", isGenerating = true) }

        generationJob = viewModelScope.launch {
            val context = if (_ui.value.useKnowledge) {
                knowledge.retrieve(text, k = 3)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n\n") { "- ${it.text}" }
            } else null

            val sb = StringBuilder()
            llm.generate(history, retrievedContext = context) { token ->
                sb.append(token)
                _ui.update { it.copy(streaming = sb.toString()) }
            }

            val reply = sb.toString().ifBlank { "(no response)" }
            val withReply = _ui.value.messages + ChatMessage.assistant(reply)
            _ui.update { it.copy(messages = withReply, streaming = "", isGenerating = false) }
            store.save(withReply)
            if (_ui.value.voiceOutput) tts.speak(reply)
        }
    }

    fun stopGeneration() {
        llm.requestStop()
        generationJob?.cancel()
        val partial = _ui.value.streaming
        val msgs = if (partial.isNotBlank())
            _ui.value.messages + ChatMessage.assistant(partial)
        else _ui.value.messages
        _ui.update { it.copy(messages = msgs, streaming = "", isGenerating = false) }
        viewModelScope.launch { store.save(msgs) }
    }

    fun clearConversation() {
        _ui.update { it.copy(messages = emptyList(), streaming = "") }
        viewModelScope.launch { store.clear() }
    }

    // ---- Voice -------------------------------------------------------------

    fun startListening() {
        recorder.start()
        _ui.update { it.copy(isListening = true) }
    }

    fun stopListeningAndTranscribe() {
        val samples = recorder.stop()
        _ui.update { it.copy(isListening = false) }
        if (samples.size < 16_000 / 2) return // < 0.5s of audio
        viewModelScope.launch {
            val text = whisper.transcribe(samples)
            if (text.isNotBlank()) {
                _ui.update { it.copy(input = text) }
                submit(text)
            }
        }
    }

    fun cancelListening() {
        recorder.cancel()
        _ui.update { it.copy(isListening = false) }
    }

    fun toggleVoiceOutput() {
        val next = !_ui.value.voiceOutput
        tts.setEnabled(next)
        _ui.update { it.copy(voiceOutput = next) }
    }

    fun toggleKnowledge() = _ui.update { it.copy(useKnowledge = !it.useKnowledge) }

    fun reloadKnowledge() {
        viewModelScope.launch {
            val n = knowledge.load()
            _ui.update { it.copy(knowledgeChunks = n) }
        }
    }

    override fun onCleared() {
        llm.unload()
        whisper.unload()
        tts.shutdown()
        recorder.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JarvisViewModel(app) as T
        }
    }
}
