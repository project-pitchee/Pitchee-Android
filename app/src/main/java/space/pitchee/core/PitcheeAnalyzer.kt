package space.pitchee.core

import java.io.Closeable

enum class PitcheePhase(val nativeValue: Int) {
    PREPARING_MODELS(0),
    LOADING_AUDIO(1),
    ANALYZING(2),
    COMPLETED(3),
    ;

    internal companion object {
        fun fromNative(value: Int): PitcheePhase =
            entries.firstOrNull { it.nativeValue == value } ?: ANALYZING
    }
}

fun interface PitcheePhaseCallback {
    fun onPhase(nativeValue: Int)
}

/**
 * Thin JNI binding to the PitcheeCore C ABI.
 *
 * Calls on one instance are serialized because one native analyzer is not safe
 * for concurrent inference.
 */
class PitcheeAnalyzer private constructor(private var nativeHandle: Long) : Closeable {
    @Synchronized
    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onPhase: ((PitcheePhase) -> Unit)? = null,
    ): String {
        check(nativeHandle != 0L) { "PitcheeAnalyzer is closed" }
        require(samples.isNotEmpty()) { "PCM buffer must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0 && samples.size % channels == 0) {
            "PCM buffer must contain complete interleaved frames"
        }
        val callback = onPhase?.let { listener ->
            PitcheePhaseCallback { value ->
                listener(PitcheePhase.fromNative(value))
            }
        }
        return checkNotNull(
            nativeAnalyze(nativeHandle, samples, sampleRate, channels, callback)
        ) {
            "PitcheeCore returned an empty result"
        }
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeDestroy(handle: Long)

    private external fun nativeAnalyze(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheePhaseCallback?,
    ): String?

    companion object {
        init {
            System.loadLibrary("pitchee_core_jni")
        }

        fun create(modelDirectory: String, threads: Int = 2): PitcheeAnalyzer {
            require(modelDirectory.isNotBlank()) { "modelDirectory must not be blank" }
            require(threads > 0) { "threads must be positive" }
            return PitcheeAnalyzer(nativeCreate(modelDirectory, threads))
        }

        @JvmStatic
        private external fun nativeCreate(modelDirectory: String, threads: Int): Long
    }
}
