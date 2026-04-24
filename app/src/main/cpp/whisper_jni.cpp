#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicelog_jni_WhisperJNI_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading whisper model from: %s", path);

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;

    whisper_context *ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("Failed to load whisper model");
        return 0L;
    }

    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_voicelog_jni_WhisperJNI_transcribe(JNIEnv *env, jobject, jlong ctxPtr, jfloatArray audioData) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx) {
        LOGE("Invalid whisper context");
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(audioData);
    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "ko";
    params.translate = false;
    params.n_threads = 4;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    int result = whisper_full(ctx, params, audio, static_cast<int>(len));
    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed with code: %d", result);
        return env->NewStringUTF("");
    }

    std::string text;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        const char *segment = whisper_full_get_segment_text(ctx, i);
        if (segment) text += segment;
    }

    LOGI("Transcription complete: %zu chars", text.size());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_voicelog_jni_WhisperJNI_free(JNIEnv *, jobject, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx) {
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

} // extern "C"
