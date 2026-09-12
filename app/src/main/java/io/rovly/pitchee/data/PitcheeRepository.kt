package io.rovly.pitchee.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.pitchee.core.PitcheeAnalyzer
import space.pitchee.core.PitcheePhase

/**
 * App-facing PitcheeCore API. Call [analyze] for an audio Uri or [analyzePcm]
 * when the caller already has interleaved Float32 PCM in the range [-1, 1].
 */
class PitcheeRepository(
    context: Context,
    private val threads: Int = DEFAULT_THREADS,
) : Closeable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var analyzer: PitcheeAnalyzer? = null

    suspend fun analyze(
        uri: Uri,
        maxSeconds: Int? = null,
        onPhase: ((PitcheePhase) -> Unit)? = null,
    ): PitcheeResult {
        val audio = decode(uri, maxSeconds, onPhase)
        return analyzePcm(audio.samples, audio.sampleRate, audio.channels, onPhase)
    }

    suspend fun decode(
        uri: Uri,
        maxSeconds: Int? = null,
        onPhase: ((PitcheePhase) -> Unit)? = null,
    ): PcmAudio {
        onPhase?.invoke(PitcheePhase.LOADING_AUDIO)
        return AudioFileDecoder.decode(appContext, uri, maxSeconds)
    }

    suspend fun analyzePcm(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onPhase: ((PitcheePhase) -> Unit)? = null,
    ): PitcheeResult = withContext(Dispatchers.Default) {
        require(samples.isNotEmpty()) { "PCM 数据不能为空" }
        require(sampleRate > 0) { "采样率必须大于 0" }
        require(channels > 0 && samples.size % channels == 0) {
            "PCM 数据必须包含完整的交错声道帧"
        }

        synchronized(lock) {
            val engine = analyzer
            if (engine == null) {
                onPhase?.invoke(PitcheePhase.PREPARING_MODELS)
            }
            val readyEngine = engine ?: PitcheeAnalyzer.create(
                modelDirectory = prepareModels().absolutePath,
                threads = threads,
            ).also { analyzer = it }

            val json = readyEngine.analyze(samples, sampleRate, channels, onPhase)
            val result = PitcheeResult.fromJson(json)
            Log.i(
                TAG,
                "Analysis completed: score=${result.composite.finalScore}, " +
                    "speech=${result.vad.speechSeconds}s, " +
                    "windows=${result.vfp.windowCount}",
            )
            result
        }
    }

    override fun close() {
        synchronized(lock) {
            analyzer?.close()
            analyzer = null
        }
    }

    private fun prepareModels(): File {
        val target = File(appContext.noBackupFilesDir, MODELS_DIRECTORY)
        if (File(target, READY_MARKER).readTextOrNull() == MODEL_VERSION) return target

        val staging = File(appContext.cacheDir, "$MODELS_DIRECTORY-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "无法创建模型缓存目录" }
        try {
            MODEL_FILES.forEach { name ->
                appContext.assets.open(name).use { input ->
                    File(staging, name).outputStream().use(input::copyTo)
                }
            }
            File(staging, READY_MARKER).writeText(MODEL_VERSION)
            target.deleteRecursively()
            check(staging.renameTo(target)) { "无法提交模型缓存" }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
        return target
    }

    private fun File.readTextOrNull(): String? =
        runCatching { if (isFile) readText() else null }.getOrNull()

    private companion object {
        const val DEFAULT_THREADS = 2
        const val TAG = "PitcheeRepository"
        const val MODELS_DIRECTORY = "pitchee-models"
        const val READY_MARKER = ".ready"
        const val MODEL_VERSION = "2026-09"

        val MODEL_FILES = listOf(
            "SileroVAD.onnx",
            "ECAPAFrontend.onnx",
            "ECAPA.onnx",
            "VFPHead.onnx",
            "SwiftF0.onnx",
            "Naturalness.onnx",
            "manifest.json",
        )
    }
}
