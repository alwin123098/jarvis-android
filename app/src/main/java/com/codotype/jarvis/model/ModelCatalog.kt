package com.codotype.jarvis.model

/**
 * The models Jarvis knows how to fetch. Every entry points at a real,
 * official Hugging Face repository. Sizes are approximate and shown to the
 * user before download so the storage cost is never a surprise.
 */
object ModelCatalog {

    // ---- Language models (the "brain") ------------------------------------
    val qwen05b = ModelSpec(
        id = "qwen2.5-0.5b",
        displayName = "Qwen2.5 0.5B Instruct (Nano)",
        repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        kind = ModelKind.LLM,
        approxSizeMb = 400,
        description = "Smallest usable chat model. Fast on any phone, best for " +
            "short Q&A and commands.",
        license = "Apache-2.0",
        required = true
    )

    val qwen15b = ModelSpec(
        id = "qwen2.5-1.5b",
        displayName = "Qwen2.5 1.5B Instruct (Balanced)",
        repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        file = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        kind = ModelKind.LLM,
        approxSizeMb = 1000,
        description = "Noticeably smarter answers. Needs ~2 GB free RAM; best " +
            "on mid-range and flagship phones.",
        license = "Apache-2.0"
    )

    // ---- Speech to text ---------------------------------------------------
    val whisperTinyEn = ModelSpec(
        id = "whisper-tiny-en",
        displayName = "Whisper Tiny (English)",
        repo = "ggerganov/whisper.cpp",
        file = "ggml-tiny.en.bin",
        kind = ModelKind.STT,
        approxSizeMb = 75,
        description = "Fast English speech recognition. Runs in real time on " +
            "most devices.",
        license = "MIT",
        required = true
    )

    val whisperBaseEn = ModelSpec(
        id = "whisper-base-en",
        displayName = "Whisper Base (English, more accurate)",
        repo = "ggerganov/whisper.cpp",
        file = "ggml-base.en.bin",
        kind = ModelKind.STT,
        approxSizeMb = 142,
        description = "Higher accuracy English transcription, slightly slower.",
        license = "MIT"
    )

    val whisperTinyMulti = ModelSpec(
        id = "whisper-tiny-multi",
        displayName = "Whisper Tiny (Multilingual)",
        repo = "ggerganov/whisper.cpp",
        file = "ggml-tiny.bin",
        kind = ModelKind.STT,
        approxSizeMb = 75,
        description = "Speech recognition for 90+ languages, including Hindi.",
        license = "MIT"
    )

    // ---- Text to speech (optional, higher quality than system voices) -----
    val piperLessac = ModelSpec(
        id = "piper-lessac-medium",
        displayName = "Piper voice — Lessac (US English)",
        repo = "rhasspy/piper-voices",
        file = "en/en_US/lessac/medium/en_US-lessac-medium.onnx",
        kind = ModelKind.TTS,
        approxSizeMb = 63,
        description = "Natural offline voice. Used by the optional Piper TTS " +
            "backend; the default uses Android's built-in engine.",
        license = "MIT"
    )

    val all: List<ModelSpec> = listOf(
        qwen05b, qwen15b, whisperTinyEn, whisperBaseEn, whisperTinyMulti, piperLessac
    )

    val defaultLlm: ModelSpec = qwen05b
    val defaultStt: ModelSpec = whisperTinyEn

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
