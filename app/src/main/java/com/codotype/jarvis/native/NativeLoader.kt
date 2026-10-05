package com.codotype.jarvis.native

import android.util.Log

/**
 * Loads the native inference libraries. If the project was built without
 * native support (jarvis.buildNative=false) the libraries are absent and the
 * app still runs — the UI reports that inference is unavailable instead of
 * crashing.
 */
object NativeLoader {

    private const val TAG = "NativeLoader"

    @Volatile
    var llmAvailable: Boolean = false
        private set

    @Volatile
    var sttAvailable: Boolean = false
        private set

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        llmAvailable = tryLoad("jarvis_llm")
        sttAvailable = tryLoad("jarvis_whisper")
    }

    private fun tryLoad(name: String): Boolean = try {
        System.loadLibrary(name)
        Log.i(TAG, "Loaded native library: $name")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native library not available: $name (${e.message})")
        false
    }
}
