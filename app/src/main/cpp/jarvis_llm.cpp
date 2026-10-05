// JNI bridge to llama.cpp for the Jarvis offline LLM.
// Pinned against llama.cpp tag b3743 (classic llama.h API).
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "JarvisLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct JarvisSession {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;
    int32_t         n_ctx   = 2048;
    int32_t         n_batch = 512;
    std::atomic<bool> stop{false};
    std::mutex      mutex;
};

bool g_backend_ready = false;

inline JarvisSession * as_session(jlong handle) {
    return reinterpret_cast<JarvisSession *>(handle);
}

// Decode a run of tokens, chunked to respect n_batch.
int decode_tokens(JarvisSession * s, std::vector<llama_token> & tokens, int32_t start_pos) {
    int32_t n_past = start_pos;
    const int32_t n = static_cast<int32_t>(tokens.size());
    for (int32_t i = 0; i < n; i += s->n_batch) {
        const int32_t chunk = std::min(s->n_batch, n - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk, n_past, 0);
        const int rc = llama_decode(s->ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode failed at chunk %d (rc=%d)", i, rc);
            return rc;
        }
        n_past += chunk;
    }
    return n_past;
}

std::string token_to_text(const llama_model * model, llama_token tok) {
    char buf[256];
    const int n = llama_token_to_piece(model, tok, buf, sizeof(buf), 0, false);
    if (n < 0) return {};
    return std::string(buf, n);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_loadModel(
        JNIEnv * env, jobject /*thiz*/, jstring jpath, jint n_threads,
        jint n_ctx, jint n_batch, jboolean use_mmap) {

    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap   = use_mmap == JNI_TRUE;
    mparams.use_mlock  = false;
    mparams.n_gpu_layers = 0; // CPU only -> works on every Android device

    llama_model * model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (model == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = static_cast<uint32_t>(n_ctx);
    cparams.n_batch         = static_cast<uint32_t>(n_batch);
    cparams.n_threads       = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context * ctx = llama_new_context_with_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }
    llama_set_n_threads(ctx, n_threads, n_threads);

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);

    auto * session = new JarvisSession();
    session->model   = model;
    session->ctx     = ctx;
    session->sampler = sampler;
    session->n_ctx   = n_ctx;
    session->n_batch = n_batch;

    LOGI("Model ready. n_ctx=%d n_batch=%d threads=%d", n_ctx, n_batch, n_threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_freeModel(JNIEnv *, jobject, jlong handle) {
    JarvisSession * s = as_session(handle);
    if (s == nullptr) return;
    std::lock_guard<std::mutex> lock(s->mutex);
    if (s->sampler) llama_sampler_free(s->sampler);
    if (s->ctx)     llama_free(s->ctx);
    if (s->model)   llama_free_model(s->model);
    delete s;
}

JNIEXPORT void JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_stop(JNIEnv *, jobject, jlong handle) {
    JarvisSession * s = as_session(handle);
    if (s) s->stop.store(true);
}

JNIEXPORT jint JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_contextSize(JNIEnv *, jobject, jlong handle) {
    JarvisSession * s = as_session(handle);
    return s ? s->n_ctx : 0;
}

// Apply the model's built-in chat template to a role/content list.
JNIEXPORT jstring JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_applyChatTemplate(
        JNIEnv * env, jobject, jlong handle,
        jobjectArray roles, jobjectArray contents, jboolean add_assistant) {

    JarvisSession * s = as_session(handle);
    if (s == nullptr) return env->NewStringUTF("");

    const jsize n = env->GetArrayLength(roles);
    std::vector<llama_chat_message> chat;
    std::vector<std::string> store;
    chat.reserve(n);
    store.reserve(n);

    for (jsize i = 0; i < n; ++i) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto jcont = (jstring) env->GetObjectArrayElement(contents, i);
        const char * role = env->GetStringUTFChars(jrole, nullptr);
        const char * cont = env->GetStringUTFChars(jcont, nullptr);
        store.emplace_back(role);
        store.emplace_back(cont);
        env->ReleaseStringUTFChars(jrole, role);
        env->ReleaseStringUTFChars(jcont, cont);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcont);
    }
    for (jsize i = 0; i < n; ++i) {
        chat.push_back({ store[2 * i].c_str(), store[2 * i + 1].c_str() });
    }

    int32_t need = llama_chat_apply_template(
            s->model, nullptr, chat.data(), chat.size(),
            add_assistant == JNI_TRUE, nullptr, 0);
    if (need < 0) need = 4096;
    std::string buf(need + 1, '\0');
    llama_chat_apply_template(
            s->model, nullptr, chat.data(), chat.size(),
            add_assistant == JNI_TRUE, buf.data(), static_cast<int32_t>(buf.size()));
    return env->NewStringUTF(buf.c_str());
}

// Streaming generation. `callback` is a Kotlin TokenCallback with
// boolean onToken(String). Return false from onToken to abort.
JNIEXPORT jstring JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_generate(
        JNIEnv * env, jobject, jlong handle, jstring jprompt,
        jint max_tokens, jfloat temperature, jint top_k, jfloat top_p,
        jobject callback) {

    JarvisSession * s = as_session(handle);
    if (s == nullptr) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(s->mutex);
    s->stop.store(false);

    // Fresh sampler chain with the requested sampling parameters.
    if (s->sampler) llama_sampler_free(s->sampler);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    s->sampler = llama_sampler_chain_init(sparams);
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    const char * cprompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(cprompt);
    env->ReleaseStringUTFChars(jprompt, cprompt);

    // Tokenize (reserve room for special tokens).
    std::vector<llama_token> tokens(prompt.size() + 32);
    int n_tok = llama_tokenize(s->model, prompt.c_str(), (int) prompt.size(),
                               tokens.data(), (int) tokens.size(), true, true);
    if (n_tok < 0) {
        tokens.resize(-n_tok);
        n_tok = llama_tokenize(s->model, prompt.c_str(), (int) prompt.size(),
                               tokens.data(), (int) tokens.size(), true, true);
    }
    tokens.resize(std::max(0, n_tok));

    llama_kv_cache_clear(s->ctx);
    int32_t n_past = decode_tokens(s, tokens, 0);
    if (n_past < 0) {
        return env->NewStringUTF("[error] prompt decode failed");
    }

    // Resolve the callback method once.
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)Z");

    std::string output;
    for (int i = 0; i < max_tokens && !s->stop.load(); ++i) {
        llama_token id = llama_sampler_sample(s->sampler, s->ctx, -1);
        if (llama_token_is_eog(s->model, id)) break;
        llama_sampler_accept(s->sampler, id);

        std::string piece = token_to_text(s->model, id);
        output += piece;

        jstring jpiece = env->NewStringUTF(piece.c_str());
        jboolean keep_going = env->CallBooleanMethod(callback, on_token, jpiece);
        env->DeleteLocalRef(jpiece);
        if (keep_going == JNI_FALSE) break;

        llama_batch batch = llama_batch_get_one(&id, 1, n_past, 0);
        if (llama_decode(s->ctx, batch) != 0) {
            LOGE("decode failed during generation");
            break;
        }
        n_past++;
    }

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_codotype_jarvis_llm_LlamaBridge_systemInfo(JNIEnv * env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

} // extern "C"
