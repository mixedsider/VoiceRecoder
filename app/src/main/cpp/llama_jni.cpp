#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaState {
    llama_model *model;
    llama_context *ctx;
    llama_sampler *sampler;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicelog_jni_LlamaJNI_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading llama model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model *model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load llama model");
        return 0L;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    llama_context *ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(model);
        return 0L;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));

    auto *state = new LlamaState{model, ctx, sampler};
    LOGI("Llama model loaded successfully");
    return reinterpret_cast<jlong>(state);
}

JNIEXPORT jstring JNICALL
Java_com_voicelog_jni_LlamaJNI_generate(JNIEnv *env, jobject, jlong statePtr, jstring prompt) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    if (!state) {
        LOGE("Invalid llama state");
        return env->NewStringUTF("");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptCpp(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    std::vector<llama_token> tokens(promptCpp.size() + 32);
    int n_tokens = llama_tokenize(
        llama_get_model(state->ctx),
        promptCpp.c_str(),
        static_cast<int>(promptCpp.size()),
        tokens.data(),
        static_cast<int>(tokens.size()),
        true,
        false
    );

    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    llama_kv_cache_clear(state->ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        return env->NewStringUTF("");
    }

    std::string result;
    const int max_new_tokens = 512;
    int n_generated = 0;

    while (n_generated < max_new_tokens) {
        llama_token token = llama_sampler_sample(state->sampler, state->ctx, -1);

        if (llama_token_is_eog(llama_get_model(state->ctx), token)) break;

        char buf[256];
        int n = llama_token_to_piece(llama_get_model(state->ctx), token, buf, sizeof(buf), 0, false);
        if (n > 0) result.append(buf, n);

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(state->ctx, next) != 0) break;

        n_generated++;
    }

    LOGI("Generated %d tokens", n_generated);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_voicelog_jni_LlamaJNI_free(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    if (state) {
        llama_sampler_free(state->sampler);
        llama_free(state->ctx);
        llama_free_model(state->model);
        delete state;
        LOGI("Llama state freed");
    }
}

} // extern "C"
