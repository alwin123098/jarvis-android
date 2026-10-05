package com.codotype.jarvis.stt

import com.codotype.jarvis.native.NativeLoader

/**
 * Thin JNI surface over whisper.cpp (see cpp/jarvis_whisper.cpp).
 */
object WhisperBridge {

    init { NativeLoader.load() }

    val available: Boolean get() = NativeLoader.sttAvailable

    external fun loadModel(path: String): Long

    external fun freeModel(handle: Long)

    /** [samples] must be mono float PCM in [-1, 1] at 16 kHz. */
    external fun transcribe(
        handle: Long,
        samples: FloatArray,
        nThreads: Int,
        language: String
    ): String
}
