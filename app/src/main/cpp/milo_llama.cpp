// milo_llama — the on-device model engine (v0.13.0).
//
// A deliberately thin JNI bridge over llama.cpp's C API
// (pinned: llama.cpp b11541; the API surface used here was
// read from that tag's include/llama.h):
//
//   nativeLoad(path)        -> load a GGUF once, keep it
//   nativeGenerate(prompt)  -> fresh context per call,
//                              top-k/top-p/temp/dist sampler
//                              chain, EOG-aware stop
//   nativeUnload()          -> free the model
//
// One mutex serializes everything: the phone runs one
// generation at a time, by design.

#include <jni.h>

#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

llama_model * g_model = nullptr;
std::mutex g_mutex;

constexpr int32_t kCtxSize = 2048;
constexpr int32_t kBatchSize = 512;

std::vector<llama_token> tokenize(
    const llama_vocab * vocab,
    const std::string & text) {
    const int32_t need = -llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        nullptr, 0, true, true);
    if (need <= 0) return {};
    std::vector<llama_token> out(static_cast<size_t>(need));
    const int32_t got = llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        out.data(), need, true, true);
    if (got < 0) return {};
    out.resize(static_cast<size_t>(got));
    return out;
}

// Keep the head (BOS) and the tail (the newest turns) when
// the prompt is longer than the space reserved for it.
void trim_to_budget(std::vector<llama_token> & tokens, int32_t budget) {
    if (budget < 8) budget = 8;
    if (static_cast<int32_t>(tokens.size()) <= budget) return;
    const size_t excess = tokens.size() - static_cast<size_t>(budget);
    tokens.erase(tokens.begin() + 1,
                 tokens.begin() + 1 + static_cast<long>(excess));
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_aimlds_mymilo_local_LocalEngine_nativeLoad(
    JNIEnv * env, jobject /* self */, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    const char * p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return JNI_FALSE;
    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(p, mparams);
    env->ReleaseStringUTFChars(path, p);
    return g_model != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_aimlds_mymilo_local_LocalEngine_nativeGenerate(
    JNIEnv * env, jobject /* self */, jstring prompt,
    jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) return env->NewStringUTF("");
    const char * raw = env->GetStringUTFChars(prompt, nullptr);
    if (raw == nullptr) return env->NewStringUTF("");
    const std::string text(raw);
    env->ReleaseStringUTFChars(prompt, raw);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = kCtxSize;
    cparams.n_batch = kBatchSize;
    llama_context * ctx = llama_init_from_model(g_model, cparams);
    if (ctx == nullptr) return env->NewStringUTF("");

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens = tokenize(vocab, text);
    trim_to_budget(tokens, kCtxSize - max_tokens);
    if (tokens.empty()) {
        llama_free(ctx);
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params sparams =
        llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    std::string out;
    llama_batch batch = llama_batch_get_one(
        tokens.data(), static_cast<int32_t>(tokens.size()));
    llama_token cur = 0;
    for (int32_t i = 0; i < max_tokens; ++i) {
        if (llama_decode(ctx, batch) != 0) break;
        cur = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, cur)) break;
        char buf[256];
        const int32_t n = llama_token_to_piece(
            vocab, cur, buf, static_cast<int32_t>(sizeof(buf)),
            0, false);
        if (n > 0) out.append(buf, static_cast<size_t>(n));
        batch = llama_batch_get_one(&cur, 1);
    }

    llama_sampler_free(smpl);
    llama_free(ctx);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_org_aimlds_mymilo_local_LocalEngine_nativeUnload(
    JNIEnv * /* env */, jobject /* self */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

} // extern "C"
