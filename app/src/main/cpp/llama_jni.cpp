#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicelog_jni_LlamaJNI_init(JNIEnv *env, jobject, jstring modelPath) {
    LOGI("LlamaJNI::init called (stub)");
    return 0L;
}

JNIEXPORT jstring JNICALL
Java_com_voicelog_jni_LlamaJNI_generate(JNIEnv *env, jobject, jlong statePtr, jstring prompt) {
    LOGI("LlamaJNI::generate called (stub)");
    return env->NewStringUTF("stub summary");
}

JNIEXPORT void JNICALL
Java_com_voicelog_jni_LlamaJNI_free(JNIEnv *env, jobject, jlong statePtr) {
    LOGI("LlamaJNI::free called (stub)");
}

} // extern "C"
