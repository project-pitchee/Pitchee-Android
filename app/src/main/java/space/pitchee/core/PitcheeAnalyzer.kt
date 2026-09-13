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

enum class PitcheeProgressStage(val nativeValue: Int) {
    LOADING_AUDIO(0),
    RESAMPLING_AUDIO(1),
    ANALYZING_F0(2),
    DETECTING_SPEECH(3),
    PREPARING_VFP_WINDOWS(4),
    EXTRACTING_VFP_EMBEDDINGS(5),
    CLASSIFYING_VFP_WINDOWS(6),
    PREPARING_NATURALNESS_WINDOWS(7),
    EXTRACTING_NATURALNESS_EMBEDDINGS(8),
    SCORING_NATURALNESS_WINDOWS(9),
    CALCULATING_SCORES(10),
    SERIALIZING_RESULT(11),
    COMPLETED(12),
    ;

    internal companion object {
        fun fromNative(value: Int): PitcheeProgressStage =
            entries.firstOrNull { it.nativeValue == value } ?: LOADING_AUDIO
    }
}

data class PitcheeProgress(
    val stage: PitcheeProgressStage,
    val completed: Long,
    val total: Long,
    val stageFraction: Float,
) {
    val overallFraction: Float
        get() = (
            stageStarts[stage.ordinal] +
                stageWeights[stage.ordinal] * stageFraction.coerceIn(0f, 1f)
            ).coerceIn(0f, 1f)
}

// F0 and VFP dominate inference time. The three naturalness stages share no
// more than 20% of the overall bar so the result cannot appear almost done.
private val stageWeights = floatArrayOf(
    0.03f, // loading audio
    0.03f, // resampling
    0.18f, // analyzing F0
    0.06f, // detecting speech
    0.04f, // preparing VFP windows
    0.18f, // extracting VFP embeddings
    0.08f, // classifying VFP windows
    0.04f, // preparing naturalness windows
    0.09f, // extracting naturalness embeddings
    0.07f, // scoring naturalness windows
    0.11f, // calculating scores
    0.07f, // serializing result
    0.02f, // completed
)

private val stageStarts = FloatArray(stageWeights.size).also { starts ->
    var accumulated = 0f
    stageWeights.forEachIndexed { index, weight ->
        starts[index] = accumulated
        accumulated += weight
    }
}

fun interface PitcheeProgressCallback {
    fun onProgress(
        stage: Int,
        completed: Long,
        total: Long,
        fraction: Float,
    )
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
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): String {
        check(nativeHandle != 0L) { "PitcheeAnalyzer is closed" }
        require(samples.isNotEmpty()) { "PCM buffer must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0 && samples.size % channels == 0) {
            "PCM buffer must contain complete interleaved frames"
        }
        val phaseCallback = onPhase?.let { listener ->
            PitcheePhaseCallback { value ->
                listener(PitcheePhase.fromNative(value))
            }
        }
        val progressCallback = onProgress?.let { listener ->
            PitcheeProgressCallback { stage, completed, total, fraction ->
                listener(
                    PitcheeProgress(
                        stage = PitcheeProgressStage.fromNative(stage),
                        completed = completed,
                        total = total,
                        stageFraction = fraction,
                    )
                )
            }
        }
        val result = if (progressCallback != null) {
            nativeAnalyzeWithProgress(
                nativeHandle,
                samples,
                sampleRate,
                channels,
                progressCallback,
            )
        } else {
            nativeAnalyze(nativeHandle, samples, sampleRate, channels, phaseCallback)
        }
        return checkNotNull(result) {
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

    private external fun nativeAnalyzeWithProgress(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheeProgressCallback?,
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
