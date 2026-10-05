package com.codotype.jarvis.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Records mono 16-bit PCM at 16 kHz — exactly the format whisper.cpp expects —
 * and exposes a normalised amplitude for the waveform UI.
 */
class AudioRecorder {

    private val sampleRate = 16_000
    private var record: AudioRecord? = null
    private var recording = false

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate) // >= 1 second of headroom
        val bufferSize = maxOf(minBuf, sampleRate * 2)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        record = rec
        recording = true
        rec.startRecording()
    }

    /** Stops recording and returns the captured audio as float PCM in [-1, 1]. */
    fun stop(): FloatArray {
        val rec = record ?: return FloatArray(0)
        recording = false
        runCatching { rec.stop() }

        val out = ArrayList<Short>(sampleRate * 4)
        val buf = ShortArray(2048)
        while (true) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) break
            for (i in 0 until n) out.add(buf[i])
        }
        rec.release()
        record = null
        _amplitude.value = 0f

        val samples = FloatArray(out.size)
        for (i in out.indices) samples[i] = out[i] / 32768f
        return samples
    }

    /** Call from a polling loop while recording to update [amplitude]. */
    fun updateAmplitude(samples: ShortArray) {
        if (samples.isEmpty()) return
        var sum = 0.0
        for (s in samples) sum += (s / 32768.0) * (s / 32768.0)
        val rms = sqrt(sum / samples.size)
        _amplitude.value = abs(rms).toFloat().coerceIn(0f, 1f)
    }

    fun cancel() {
        recording = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        _amplitude.value = 0f
    }
}
