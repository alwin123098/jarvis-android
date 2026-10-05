package com.codotype.jarvis.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Offline voice output.
 *
 * Default backend: Android's built-in TextToSpeech engine, which speaks using
 * an on-device voice pack — no network, no extra download. It is available on
 * essentially every Android device.
 *
 * Higher-quality neural voices (Piper / ONNX) are a documented extension point;
 * see docs/ARCHITECTURE.md.
 */
class TtsEngine(context: Context) : TextToSpeech.OnInitListener {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var enabled = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) { _speaking.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
            })
            _ready.value = true
        } else {
            _ready.value = false
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stop()
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _ready.value = false
    }
}
