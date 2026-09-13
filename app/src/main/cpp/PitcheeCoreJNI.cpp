#include "pitchee/pitchee.h"

#include <jni.h>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void throw_illegal_state(JNIEnv* env, const char* message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message && *message ? message : "PitcheeCore error");
}

struct PhaseBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

struct ProgressBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

void report_progress(const pitchee_progress_t* progress, void* user_data) {
    auto* bridge = static_cast<ProgressBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || progress == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jint>(progress->stage),
        static_cast<jlong>(progress->completed),
        static_cast<jlong>(progress->total),
        static_cast<jfloat>(progress->fraction)
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

void report_phase(pitchee_analysis_phase_t phase, void* user_data) {
    auto* bridge = static_cast<PhaseBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr) return;
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jint>(phase)
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeCreate(
    JNIEnv* env,
    jclass,
    jstring model_directory,
    jint threads
) {
    if (model_directory == nullptr) {
        throw_illegal_state(env, "model directory is null");
        return 0;
    }

    const char* model_chars = env->GetStringUTFChars(model_directory, nullptr);
    pitchee_analyzer_options_t options{};
    options.intra_op_threads = threads;
    options.use_coreml = 0;

    pitchee_analyzer_t* analyzer = nullptr;
    char error[1024] = {};
    const auto status = pitchee_analyzer_create(
        model_chars,
        &options,
        &analyzer,
        error,
        sizeof(error)
    );
    env->ReleaseStringUTFChars(model_directory, model_chars);

    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(analyzer);
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_analyzer_destroy(reinterpret_cast<pitchee_analyzer_t*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeAnalyze(
    JNIEnv* env,
    jobject,
    jlong handle_value,
    jfloatArray samples,
    jint sample_rate,
    jint channels,
    jobject phase_callback
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(handle_value);
    if (analyzer == nullptr || samples == nullptr) {
        throw_illegal_state(env, "analyzer or PCM buffer is null");
        return nullptr;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jmethodID phase_method = nullptr;
    if (phase_callback != nullptr) {
        jclass callback_class = env->GetObjectClass(phase_callback);
        phase_method = env->GetMethodID(callback_class, "onPhase", "(I)V");
        env->DeleteLocalRef(callback_class);
        if (phase_method == nullptr) return nullptr;
    }
    PhaseBridge phase_bridge{env, phase_callback, phase_method};

    char* json = nullptr;
    char error[1024] = {};
    const auto status = pitchee_analyzer_analyze_pcm(
        analyzer,
        pcm.data(),
        pcm.size(),
        sample_rate,
        channels,
        phase_callback == nullptr ? nullptr : report_phase,
        phase_callback == nullptr ? nullptr : &phase_bridge,
        &json,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    jstring result = env->NewStringUTF(json);
    pitchee_string_free(json);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeAnalyzeWithProgress(
    JNIEnv* env,
    jobject,
    jlong handle_value,
    jfloatArray samples,
    jint sample_rate,
    jint channels,
    jobject progress_callback
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(handle_value);
    if (analyzer == nullptr || samples == nullptr) {
        throw_illegal_state(env, "analyzer or PCM buffer is null");
        return nullptr;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jmethodID progress_method = nullptr;
    if (progress_callback != nullptr) {
        jclass callback_class = env->GetObjectClass(progress_callback);
        progress_method = env->GetMethodID(
            callback_class,
            "onProgress",
            "(IJJF)V"
        );
        env->DeleteLocalRef(callback_class);
        if (progress_method == nullptr) return nullptr;
    }
    ProgressBridge progress_bridge{env, progress_callback, progress_method};

    char* json = nullptr;
    char error[1024] = {};
    const auto status = pitchee_analyzer_analyze_pcm_with_progress(
        analyzer,
        pcm.data(),
        pcm.size(),
        sample_rate,
        channels,
        progress_callback == nullptr ? nullptr : report_progress,
        progress_callback == nullptr ? nullptr : &progress_bridge,
        &json,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    jstring result = env->NewStringUTF(json);
    pitchee_string_free(json);
    return result;
}
