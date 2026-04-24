#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicelog_jni_WhisperJNI_init(JNIEnv *env, jobject, jstring modelPath) {
    LOGI("WhisperJNI::init called (stub)");
    return 0L;
}

JNIEXPORT jstring JNICALL
Java_com_voicelog_jni_WhisperJNI_transcribe(JNIEnv *env, jobject, jlong ctxPtr, jfloatArray audioData) {
    LOGI("WhisperJNI::transcribe called (stub)");
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_voicelog_jni_WhisperJNI_free(JNIEnv *env, jobject, jlong ctxPtr) {
    LOGI("WhisperJNI::free called (stub)");
}

} // extern "C"
