# Jarvis — an offline AI assistant for Android

Jarvis is a chat **and** voice assistant that runs entirely on your phone. No
account, no cloud, no API keys. The app is small; the "brain" is a model you
download once from Hugging Face and then run locally with
[llama.cpp](https://github.com/ggerganov/llama.cpp). Speech-to-text runs locally
with [whisper.cpp](https://github.com/ggerganov/whisper.cpp), and replies are
spoken with the device's built-in offline text-to-speech engine.

> **About the "10 MB" goal — the honest version.** A language model's weights
> cannot fit in 10 MB; the smallest usable chat model is ~400 MB. What *is*
> tiny is the **app**: the APK (UI + voice + inference engine) is in the
> ~10–15 MB range. On first launch Jarvis downloads the model files you choose
> from the Hugging Face Hub, and from then on everything runs offline. This is
> the only way to have both a small install and a real assistant.

---

## Features

- **Chat**, streaming token-by-token, in a clean Compose UI.
- **Voice in** — tap the mic, speak, and your words are transcribed on-device
  with Whisper (English, or multilingual with the multilingual model).
- **Voice out** — replies are read aloud using Android's offline TTS engine.
- **Fully offline inference** — after the one-time download, no network is used.
- **Local knowledge packs** — drop text/JSONL files into the app's knowledge
  folder and Jarvis will ground its answers in them using a built-in BM25
  retriever (no embeddings model needed).
- **Model picker** — choose a small fast model (Qwen2.5 0.5B) or a smarter one
  (Qwen2.5 1.5B), and swap speech models, from inside the app.
- **Resumable downloads** — a dropped connection does not restart a 1 GB file.

## How it fits together

```
        ┌──────────────────────── Android app (APK ≈ 10–15 MB) ───────────────────────┐
        │                                                                             │
  mic ──┤  AudioRecorder ──► WhisperEngine ──► (JNI) whisper.cpp ──► text             │
        │        ▲                                                    │               │
        │        │                                                    ▼               │
        │   amplitude UI                                    ┌──────────────────┐      │
        │                                                   │  ChatTemplate    │      │
        │   TtsEngine ◄──── reply text ◄── LlmEngine ◄──────│  + KnowledgeBase │      │
        │  (Android TTS)                  (JNI) llama.cpp  └──────────────────┘      │
        │                                       ▲                     ▲              │
        │                                       │                     │              │
        │                          ModelRepository ──► models/ (GGUF, downloaded once)│
        └─────────────────────────────────────────────────────────────────────────────┘
                                                   ▲
                                     one-time download from huggingface.co
```

The native engine (`llama.cpp`, `whisper.cpp`) is compiled into the APK as a
shared library. The models are **not** bundled — they are fetched on first run.

## Models

| Purpose | Model | Repo (Hugging Face) | Size |
|---|---|---|---|
| Chat (default) | Qwen2.5 0.5B Instruct Q4_K_M | `Qwen/Qwen2.5-0.5B-Instruct-GGUF` | ≈400 MB |
| Chat (optional) | Qwen2.5 1.5B Instruct Q4_K_M | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` | ≈1 GB |
| Speech→text (default) | Whisper Tiny (en) | `ggerganov/whisper.cpp` | ≈75 MB |
| Speech→text (optional) | Whisper Base (en) | `ggerganov/whisper.cpp` | ≈142 MB |
| Speech→text (optional) | Whisper Tiny (multilingual) | `ggerganov/whisper.cpp` | ≈75 MB |
| Voice (optional) | Piper Lessac medium | `rhasspy/piper-voices` | ≈63 MB |

All are permissively licensed (Apache-2.0 / MIT). Jarvis ships **no** weights.

## Requirements

- Android 8.0 (API 26) or newer, arm64-v8a or armeabi-v7a.
- ~2 GB free storage and ~2 GB RAM for the 1.5B model (the 0.5B model needs
  far less — it runs comfortably on a 3 GB phone).
- To build: Android Studio (or the Android SDK + NDK) and JDK 17.

## Build

### 1. Quick UI-only build (no native engine)

This compiles in seconds and lets you see the interface. Chat/voice will report
that the native engine is missing.

```bash
git clone https://github.com/alwin123098/jarvis-android.git
cd jarvis-android

# If the Gradle wrapper JAR is not present (it is a binary and may be absent
# from a source checkout), generate it once with a local Gradle install:
#   gradle wrapper --gradle-version 8.9
# Opening the project in Android Studio also creates it automatically.

./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Full build with the native engine (real inference)

```bash
# Vendors llama.cpp (pinned tag b3743) and whisper.cpp (v1.7.4)
./scripts/fetch_native.sh

# Build with native enabled
./gradlew assembleDebug -Pjarvis.buildNative=true
```

`fetch_native.sh` clones the two engines into
`app/src/main/cpp/third_party/` (git-ignored). The JNI layer in this repo is
written against those pinned tags, so the native code matches the headers it
compiles against.

> Building the native libraries needs the Android NDK (the build pins
> `26.1.10909125`). Android Studio installs it automatically when you open the
> project; on the command line install it via the SDK Manager.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it (enable "install from unknown
sources" first).

On first launch, pick your models and tap Download. After that, switch the
phone to aeroplane mode — Jarvis keeps working.

## Knowledge packs

Jarvis can ground its answers in your own text. Put plain `.txt`, `.md`, or
`.jsonl` files in the app's knowledge folder and it will retrieve the most
relevant passages and add them to the prompt. See
[docs/KNOWLEDGE.md](docs/KNOWLEDGE.md) for how to export a Hugging Face dataset
into a pack (there is a helper script: `scripts/export_dataset.py`).

## Privacy

Everything runs on the device. The only network activity is the initial model
download from `huggingface.co`. Your conversations are stored in the app's
private storage and are never uploaded.

## Project layout

```
app/src/main/
├── cpp/                     # JNI bridge + CMake (llama.cpp / whisper.cpp)
├── java/com/codotype/jarvis/
│   ├── llm/                 # LlamaBridge, LlmEngine, ChatTemplate
│   ├── stt/                 # WhisperBridge, WhisperEngine
│   ├── tts/                 # TtsEngine (Android offline TTS)
│   ├── audio/               # AudioRecorder (16 kHz PCM)
│   ├── model/               # ModelSpec, ModelCatalog, ModelRepository
│   ├── rag/                 # KnowledgeBase (BM25 retrieval)
│   ├── data/                # ChatMessage, ConversationStore
│   └── ui/                  # Compose screens + ViewModel + theme
└── res/                     # icons, themes, strings
```

More detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Roadmap

- [ ] Piper / ONNX neural voice backend (scaffolded in the catalog).
- [ ] Function calling for on-device actions (alarms, reminders).
- [ ] Conversation export/import.
- [ ] Optional GPU (Vulkan) offload for newer devices.

## License

MIT — see [LICENSE](LICENSE). Jarvis bundles no model weights; the models you
download keep their own licenses (listed in the app and in the table above).

## Credits

Built on the shoulders of [llama.cpp](https://github.com/ggerganov/llama.cpp),
[whisper.cpp](https://github.com/ggerganov/whisper.cpp), the
[Qwen](https://huggingface.co/Qwen) team, and the
[Hugging Face Hub](https://huggingface.co).
