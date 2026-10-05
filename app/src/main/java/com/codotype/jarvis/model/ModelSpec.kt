package com.codotype.jarvis.model

/** What a downloadable file is used for. */
enum class ModelKind { LLM, STT, TTS }

/**
 * Describes one file on the Hugging Face Hub that Jarvis can fetch on demand.
 * The app itself ships with no model weights — this is how the "tiny APK,
 * big brain on first run" split is achieved.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val repo: String,
    val file: String,
    val kind: ModelKind,
    val approxSizeMb: Int,
    val description: String,
    val license: String,
    val required: Boolean = false
) {
    /** Direct, resumable download URL on the Hub. */
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/main/$file"

    /** Filesystem-safe local name. */
    val localName: String get() = file.substringAfterLast('/')
}
