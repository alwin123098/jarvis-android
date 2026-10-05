// JNI bridge to whisper.cpp for offline speech-to-text.
// Pinned against whisper.cpp tag v1.7.4.
#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "JarvisSTT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_mutex;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_codotype_jarvis_stt_WhisperBridge_loadModel(
        JNIEnv * env, jobject, jstring jpath) {

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("Loading whisper model: %s", path);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // CPU only for universal device support

    whisper_context * ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (ctx == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }
    LOGI("Whisper model ready");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_codotype_jarvis_stt_WhisperBridge_freeModel(JNIEnv *, jobject, jlong handle) {
    if (handle) whisper_free(reinterpret_cast<whisper_context *>(handle));
}

// samples: mono float PCM in [-1, 1] at 16 kHz.
JNIEXPORT jstring JNICALL
Java_com_codotype_jarvis_stt_WhisperBridge_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray jsamples,
        jint n_threads, jstring jlanguage) {

    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(g_mutex);

    const jsize n = env->GetArrayLength(jsamples);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(jsamples, 0, n, samples.data());

    const char * lang = env->GetStringUTFChars(jlanguage, nullptr);
    std::string language(lang);
    env->ReleaseStringUTFChars(jlanguage, lang);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.no_context       = true;
    params.single_segment   = false;
    params.n_threads        = n_threads;
    params.language         = language.empty() ? "en" : language.c_str();

    if (whisper_full(ctx, params, samples.data(), (int) samples.size()) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    std::string text;
    const int n_seg = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_seg; ++i) {
        text += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(text.c_str());
}

} // extern "C"
