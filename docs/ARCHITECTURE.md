# Architecture

## The core constraint

"Small app, real assistant" only works if the model weights live outside the
APK. Jarvis therefore splits cleanly in two:

1. **The app** — Kotlin/Compose UI, a native inference engine (llama.cpp +
   whisper.cpp compiled to a shared library), a downloader, and a retriever.
   This is what ships in the APK and is small.
2. **The models** — GGUF / ggml files fetched on first run from the Hugging
   Face Hub into the app's private storage.

## Layers

### Presentation (`ui/`)
Jetpack Compose. `ChatScreen` renders the message list and composer;
`SetupScreen` handles model selection and download; `JarvisViewModel` owns all
state as a single `UiState` `StateFlow`. The UI never blocks: generation runs on
`Dispatchers.Default`, downloads on `Dispatchers.IO`.

### Inference (`llm/`, `stt/`, `cpp/`)
- `LlamaBridge` / `WhisperBridge` are `external` Kotlin objects bound to the JNI
  functions in `cpp/jarvis_llm.cpp` and `cpp/jarvis_whisper.cpp`.
- `LlmEngine` owns exactly one llama.cpp session, guarded by a `Mutex`, so two
  generations can never race on one context. It re-processes the full prompt
  each turn (simple and correct) after clearing the KV cache.
- Sampling uses a llama.cpp sampler chain: top-k → top-p → temperature → dist,
  with a greedy chain when temperature is 0.
- Token streaming crosses the JNI boundary via a `TokenCallback` interface; the
  native side calls `onToken` per token and stops early if it returns false.
- The chat prompt is produced by the model's own template through
  `llama_chat_apply_template`, with a ChatML fallback.

The engine runs **CPU-only** (`n_gpu_layers = 0`), which is why it works on
every device. GPU/Vulkan offload is a roadmap item.

### Voice (`audio/`, `stt/`, `tts/`)
- `AudioRecorder` captures mono 16-bit PCM at 16 kHz — exactly whisper.cpp's
  input format — and exposes a normalised amplitude for the UI.
- `WhisperEngine` transcribes the captured float PCM on a background thread.
- `TtsEngine` wraps Android's `TextToSpeech`. This is offline once a voice pack
  is installed and needs no extra download. A Piper/ONNX backend is the
  documented upgrade path: `ModelCatalog.piperLessac` already describes the
  voice file, and a future `PiperTtsBackend` would load it via ONNX Runtime.

### Models (`model/`)
- `ModelSpec` describes one Hub file (repo, path, size, license).
- `ModelCatalog` is the curated, hand-checked list.
- `ModelRepository` streams downloads with HTTP range resume and reports
  progress through a callback.

### Knowledge (`rag/`)
`KnowledgeBase` is a dependency-free BM25 index over text/JSONL packs in the
knowledge folder. It avoids an embeddings model entirely — keeping the install
small — while still grounding answers in your documents. Retrieval results are
injected into the system prompt as "reference notes".

## Why these choices

| Decision | Reason |
|---|---|
| llama.cpp / whisper.cpp via JNI | Mature, CPU-only, runs on all Androids |
| Qwen2.5 GGUF | Official quants, strong for its size, permissive license |
| Download-on-first-run | The only way to be small *and* capable |
| BM25 instead of embeddings | No second model, no GPU, tiny footprint |
| System TTS by default | Offline out of the box, zero download |
