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

struct F0FrameBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

struct SpectrumBridge {
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

void report_f0_frame(const pitchee_f0_frame_t* frame, void* user_data) {
    auto* bridge = static_cast<F0FrameBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || frame == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jdouble>(frame->timestamp_seconds),
        static_cast<jfloat>(frame->f0_hz),
        static_cast<jfloat>(frame->confidence),
        frame->voiced != 0
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

void report_spectrum_frame(
    const pitchee_spectrum_frame_t* frame,
    void* user_data
) {
    auto* bridge = static_cast<SpectrumBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || frame == nullptr) {
        return;
    }

    jfloatArray magnitudes = bridge->env->NewFloatArray(
        static_cast<jsize>(frame->bin_count)
    );
    if (magnitudes == nullptr) return;
    bridge->env->SetFloatArrayRegion(
        magnitudes,
        0,
        static_cast<jsize>(frame->bin_count),
        frame->magnitudes
    );
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jdouble>(frame->timestamp_seconds),
        magnitudes,
        static_cast<jlong>(frame->first_bin_index),
        static_cast<jfloat>(frame->bin_hz),
        static_cast<jfloat>(frame->peak_hz),
        static_cast<jfloat>(frame->centroid_hz),
        static_cast<jfloat>(frame->rolloff_hz),
        static_cast<jfloat>(frame->flatness)
    );
    bridge->env->DeleteLocalRef(magnitudes);
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

extern "C" JNIEXPORT jdouble JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeCompositeScoreValue(
    JNIEnv*,
    jclass,
    jdouble vfp_standard_score,
    jdouble naturalness_score,
    jdouble f0_hz
) {
    return pitchee_composite_score_value(
        vfp_standard_score,
        naturalness_score,
        f0_hz
    );
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeRealtimeF0Create(
    JNIEnv* env,
    jobject,
    jlong analyzer_handle,
    jint context_samples,
    jint hop_samples
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(analyzer_handle);
    if (analyzer == nullptr) {
        throw_illegal_state(env, "analyzer is null");
        return 0;
    }

    pitchee_realtime_f0_options_t options{};
    options.context_samples = context_samples;
    options.hop_samples = hop_samples;

    pitchee_realtime_f0_t* stream = nullptr;
    char error[1024] = {};
    const auto status = pitchee_realtime_f0_create(
        analyzer,
        &options,
        &stream,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(stream);
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeProcess(
    JNIEnv* env,
    jobject,
    jlong stream_handle,
    jfloatArray samples,
    jobject frame_callback
) {
    auto* stream = reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle);
    if (stream == nullptr || samples == nullptr || frame_callback == nullptr) {
        throw_illegal_state(env, "realtime F0 stream or PCM buffer is null");
        return 0;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jclass callback_class = env->GetObjectClass(frame_callback);
    jmethodID frame_method = env->GetMethodID(
        callback_class,
        "onFrame",
        "(DFFZ)V"
    );
    env->DeleteLocalRef(callback_class);
    if (frame_method == nullptr) return 0;

    F0FrameBridge frame_bridge{env, frame_callback, frame_method};
    size_t frame_count = 0;
    char error[1024] = {};
    const auto status = pitchee_realtime_f0_process(
        stream,
        pcm.data(),
        pcm.size(),
        report_f0_frame,
        &frame_bridge,
        &frame_count,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return static_cast<jlong>(frame_count);
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeReset(
    JNIEnv*,
    jobject,
    jlong stream_handle
) {
    pitchee_realtime_f0_reset(reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong stream_handle
) {
    pitchee_realtime_f0_destroy(reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeCreate(
    JNIEnv* env,
    jclass,
    jint fft_size,
    jint hop_samples,
    jint min_hz,
    jint max_hz,
    jint value_type,
    jfloat smoothing
) {
    pitchee_spectrum_options_t options{};
    options.fft_size = fft_size;
    options.hop_samples = hop_samples;
    options.min_hz = min_hz;
    options.max_hz = max_hz;
    options.value_type = static_cast<pitchee_spectrum_value_t>(value_type);
    options.smoothing = smoothing;

    pitchee_spectrum_t* spectrum = nullptr;
    char error[1024] = {};
    const auto status = pitchee_spectrum_create(
        &options,
        &spectrum,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(spectrum);
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeProcess(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloatArray samples,
    jobject frame_callback
) {
    auto* spectrum = reinterpret_cast<pitchee_spectrum_t*>(handle);
    if (spectrum == nullptr || samples == nullptr || frame_callback == nullptr) {
        throw_illegal_state(env, "spectrum or PCM buffer is null");
        return 0;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jclass callback_class = env->GetObjectClass(frame_callback);
    jmethodID frame_method = env->GetMethodID(
        callback_class,
        "onFrame",
        "(D[FJFFFFF)V"
    );
    env->DeleteLocalRef(callback_class);
    if (frame_method == nullptr) return 0;

    SpectrumBridge bridge{env, frame_callback, frame_method};
    size_t frame_count = 0;
    char error[1024] = {};
    const auto status = pitchee_spectrum_process(
        spectrum,
        pcm.data(),
        pcm.size(),
        report_spectrum_frame,
        &bridge,
        &frame_count,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return static_cast<jlong>(frame_count);
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeReset(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_spectrum_reset(reinterpret_cast<pitchee_spectrum_t*>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_spectrum_destroy(reinterpret_cast<pitchee_spectrum_t*>(handle));
}
