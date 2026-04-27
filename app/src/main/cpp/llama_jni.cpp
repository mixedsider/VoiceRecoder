#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaState {
    llama_model *model;
    llama_context *ctx;
    llama_sampler *sampler;
    double last_prompt_eval_ms = 0.0;
    double last_decode_ms = 0.0;
    double last_sampling_ms = 0.0;
    double last_ttft_ms = 0.0;
    int last_prompt_token_count = 0;
    int last_generated_token_count = 0;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicelog_jni_LlamaJNI_init(JNIEnv *env, jobject, jstring modelPath, jint contextSize, jint threadCount) {
    const auto load_started_at = std::chrono::steady_clock::now();
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading llama model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load llama model");
        return 0L;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize > 0 ? contextSize : 2048;
    ctx_params.n_threads = threadCount > 0 ? threadCount : 4;
    ctx_params.n_threads_batch = threadCount > 0 ? threadCount : 4;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0L;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));

    auto *state = new LlamaState{model, ctx, sampler};
    const auto load_finished_at = std::chrono::steady_clock::now();
    const auto total_load_ms =
        std::chrono::duration<double, std::milli>(load_finished_at - load_started_at).count();
    LOGI("Llama model loaded successfully in %.2f ms", total_load_ms);
    return reinterpret_cast<jlong>(state);
}

JNIEXPORT jstring JNICALL
Java_com_voicelog_jni_LlamaJNI_generate(JNIEnv *env, jobject, jlong statePtr, jstring prompt, jint maxNewTokens) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    if (!state) {
        LOGE("Invalid llama state");
        return env->NewStringUTF("");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptCpp(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    const llama_vocab *vocab = llama_model_get_vocab(state->model);
    llama_perf_context_reset(state->ctx);
    llama_perf_sampler_reset(state->sampler);
    state->last_prompt_eval_ms = 0.0;
    state->last_decode_ms = 0.0;
    state->last_sampling_ms = 0.0;
    state->last_ttft_ms = 0.0;
    state->last_prompt_token_count = 0;
    state->last_generated_token_count = 0;

    std::vector<llama_token> tokens(promptCpp.size() + 32);
    int n_tokens = llama_tokenize(
        vocab,
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
    state->last_prompt_token_count = n_tokens;

    llama_memory_clear(llama_get_memory(state->ctx), true);

    const auto generate_started_at = std::chrono::steady_clock::now();
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        return env->NewStringUTF("");
    }

    std::string result;
    const int max_new_tokens = maxNewTokens > 0 ? maxNewTokens : 512;
    int n_generated = 0;
    bool first_token_recorded = false;

    while (n_generated < max_new_tokens) {
        llama_token token = llama_sampler_sample(state->sampler, state->ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) break;

        if (!first_token_recorded) {
            const auto first_token_at = std::chrono::steady_clock::now();
            state->last_ttft_ms =
                std::chrono::duration<double, std::milli>(first_token_at - generate_started_at).count();
            first_token_recorded = true;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
        if (n > 0) result.append(buf, n);

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(state->ctx, next) != 0) break;

        n_generated++;
    }

    const auto perf_ctx = llama_perf_context(state->ctx);
    const auto perf_sampler = llama_perf_sampler(state->sampler);
    state->last_prompt_eval_ms = perf_ctx.t_p_eval_ms;
    state->last_decode_ms = perf_ctx.t_eval_ms;
    state->last_sampling_ms = perf_sampler.t_sample_ms;
    state->last_prompt_token_count = perf_ctx.n_p_eval > 0 ? perf_ctx.n_p_eval : n_tokens;
    state->last_generated_token_count = n_generated;

    LOGI("Generated %d tokens", n_generated);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jdouble JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastPromptEvalMs(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_prompt_eval_ms : 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastDecodeMs(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_decode_ms : 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastSamplingMs(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_sampling_ms : 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastTimeToFirstTokenMs(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_ttft_ms : 0.0;
}

JNIEXPORT jint JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastPromptTokenCount(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_prompt_token_count : 0;
}

JNIEXPORT jint JNICALL
Java_com_voicelog_jni_LlamaJNI_getLastGeneratedTokenCount(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    return state ? state->last_generated_token_count : 0;
}

JNIEXPORT void JNICALL
Java_com_voicelog_jni_LlamaJNI_free(JNIEnv *, jobject, jlong statePtr) {
    auto *state = reinterpret_cast<LlamaState *>(statePtr);
    if (state) {
        llama_sampler_free(state->sampler);
        llama_free(state->ctx);
        llama_model_free(state->model);
        delete state;
        LOGI("Llama state freed");
    }
}

} // extern "C"
