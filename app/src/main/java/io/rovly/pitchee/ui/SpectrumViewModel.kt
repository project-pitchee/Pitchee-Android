package io.rovly.pitchee.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.RealtimeF0AudioRecorder
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.pitchee.core.PitcheeSpectrum

enum class SpectrumMode {
    IDLE,
    PREPARING,
    ANALYZING,
    ANALYSIS_PAUSED,
    REPLAYING,
}

data class SpectrumFramePoint(
    val timestampSeconds: Double,
    val magnitudes: FloatArray,
    val firstBinIndex: Int,
    val binHz: Float,
    val centroidHz: Float,
    val rolloffHz: Float,
    val flatness: Float,
)

data class SpectrumUiState(
    val mode: SpectrumMode = SpectrumMode.IDLE,
    val frames: List<SpectrumFramePoint> = emptyList(),
    val playbackPositionSeconds: Double? = null,
    val replayWindowStartSeconds: Double? = null,
    val replayWindowEndSeconds: Double? = null,
    val replayTailEndSeconds: Double? = null,
    val error: String? = null,
)

class SpectrumViewModel(
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var liveJob: Job? = null
    private var playbackJob: Job? = null
    private var spectrum: PitcheeSpectrum? = null
    private var audioTrack: AudioTrack? = null
    private val sessionLock = Any()
    private var playbackGeneration = 0
    private var replayPageDepth = 0
    private var lastControlAtMillis = 0L
    private val frameBuffer = ArrayDeque<SpectrumFramePoint>()
    private val audioRing = FloatArray((SAMPLE_RATE * HISTORY_SECONDS).toInt())
    private var ringWriteIndex = 0

    @Volatile
    private var totalSamples = 0L
    private var replayTailSample = 0L
    private var lastPublishedTimestamp = Double.NEGATIVE_INFINITY
    private val mutableState = MutableStateFlow(SpectrumUiState())
    val state: StateFlow<SpectrumUiState> = mutableState.asStateFlow()

    fun start() {
        if (mutableState.value.mode != SpectrumMode.IDLE) return
        resetSession()
        startAnalysisInternal()
    }

    fun rewindFiveSeconds() {
        if (mutableState.value.mode == SpectrumMode.IDLE) return
        if (mutableState.value.mode == SpectrumMode.REPLAYING &&
            replayPageDepth >= MAX_REPLAY_WINDOWS
        ) {
            return
        }
        if (!acceptControlEvent()) return
        stopLiveCapture()
        stopPlaybackInternal()

        val currentWindowStart = mutableState.value.replayWindowStartSeconds
        val currentWindowEnd = mutableState.value.replayWindowEndSeconds
            ?: totalSamples.toDouble() / SAMPLE_RATE
        val liveTail = totalSamples.toDouble() / SAMPLE_RATE

        if (replayTailSample <= 0L) {
            replayTailSample = totalSamples
        }
        replayPageDepth = if (mutableState.value.mode == SpectrumMode.REPLAYING) {
            replayPageDepth + 1
        } else {
            1
        }

        val targetEnd = if (currentWindowStart != null) {
            currentWindowStart
        } else {
            min(currentWindowEnd, replayTailSample.toDouble() / SAMPLE_RATE)
        }
        val targetStart = max(
            oldestSample().toDouble() / SAMPLE_RATE,
            targetEnd - WINDOW_SECONDS,
        )
        if (targetEnd - targetStart <= END_EPSILON_SECONDS) {
            startAnalysisInternal()
            return
        }

        startReplayWindow(
            startSeconds = targetStart,
            endSeconds = min(targetEnd, liveTail),
        )
    }

    fun toggleAnalysis() {
        if (!acceptControlEvent()) return
        when (mutableState.value.mode) {
            SpectrumMode.IDLE -> start()
            SpectrumMode.PREPARING, SpectrumMode.ANALYZING -> pauseAnalysis()
            SpectrumMode.ANALYSIS_PAUSED, SpectrumMode.REPLAYING -> resumeAnalysis()
        }
    }

    fun stop() {
        resetSession()
        mutableState.value = SpectrumUiState()
    }

    private fun resetSession() {
        stopLiveCapture()
        stopPlaybackInternal()
        spectrum?.close()
        spectrum = null
        synchronized(sessionLock) {
            frameBuffer.clear()
            audioRing.fill(0f)
            ringWriteIndex = 0
            totalSamples = 0L
            replayTailSample = 0L
            lastPublishedTimestamp = Double.NEGATIVE_INFINITY
        }
        mutableState.value = SpectrumUiState()
    }

    private fun startAnalysisInternal() {
        if (mutableState.value.mode == SpectrumMode.ANALYZING ||
            mutableState.value.mode == SpectrumMode.PREPARING
        ) {
            return
        }
        stopPlaybackInternal()
        replayTailSample = 0L
        replayPageDepth = 0
        mutableState.update {
            it.copy(
                mode = SpectrumMode.PREPARING,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
                error = null,
            )
        }
        liveJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val stream = spectrum ?: PitcheeSpectrum.create(
                    fftSize = FFT_SIZE,
                    hopSamples = HOP_SAMPLES,
                    minHz = MIN_HZ,
                    maxHz = MAX_HZ,
                ).also { spectrum = it }
                recorder.start()
                mutableState.update { it.copy(mode = SpectrumMode.ANALYZING) }
                val buffer = FloatArray(READ_SAMPLES)
                while (isActive) {
                    val count = recorder.read(buffer)
                    if (count <= 0) continue
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    synchronized(sessionLock) {
                        if (isActive) {
                            appendAudio(chunk)
                            stream.process(chunk) {
                                    timestampSeconds,
                                    magnitudes,
                                    firstBinIndex,
                                    binHz,
                                    _,
                                    centroidHz,
                                    rolloffHz,
                                    flatness,
                                ->
                                val point = SpectrumFramePoint(
                                    timestampSeconds = timestampSeconds,
                                    magnitudes = magnitudes,
                                    firstBinIndex = firstBinIndex.toInt(),
                                    binHz = binHz,
                                    centroidHz = centroidHz,
                                    rolloffHz = rolloffHz,
                                    flatness = flatness,
                                )
                                frameBuffer.addLast(point)
                                while (frameBuffer.isNotEmpty() &&
                                    frameBuffer.first().timestampSeconds <
                                    point.timestampSeconds - HISTORY_SECONDS
                                ) {
                                    frameBuffer.removeFirst()
                                }
                                while (frameBuffer.size > MAX_FRAMES) {
                                    frameBuffer.removeFirst()
                                }
                                if (point.timestampSeconds - lastPublishedTimestamp >=
                                    PUBLISH_INTERVAL_SECONDS
                                ) {
                                    lastPublishedTimestamp = point.timestampSeconds
                                    val frames = frameBuffer.toList()
                                    mutableState.update { current ->
                                        current.copy(frames = frames, error = null)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        mode = SpectrumMode.ANALYSIS_PAUSED,
                        error = error.message,
                    )
                }
            }
        }
    }

    private fun pauseAnalysis() {
        stopLiveCapture()
        mutableState.update {
            it.copy(
                mode = SpectrumMode.ANALYSIS_PAUSED,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
            )
        }
    }

    private fun resumeAnalysis() {
        stopPlaybackInternal()
        startAnalysisInternal()
    }

    private fun stopLiveCapture() {
        synchronized(sessionLock) {
            liveJob?.cancel()
            liveJob = null
            recorder.stop()
        }
    }

    private fun startReplayWindow(
        startSeconds: Double,
        endSeconds: Double,
    ) {
        val startSample = (startSeconds * SAMPLE_RATE).toLong()
            .coerceAtLeast(oldestSample())
            .coerceAtMost(totalSamples)
        val endSample = (endSeconds * SAMPLE_RATE).toLong()
            .coerceIn(startSample, totalSamples)
        if (endSample <= startSample) {
            resumeAnalysis()
            return
        }

        updateReplayState(
            positionSample = startSample,
            windowStartSample = startSample,
            windowEndSample = endSample,
            tailSample = replayTailSample,
        )
        val generation = ++playbackGeneration
        playbackJob = viewModelScope.launch(Dispatchers.Default) {
            var track: AudioTrack? = null
            var reachedTail = false
            try {
                if (!isActive || generation != playbackGeneration) return@launch
                track = createAudioTrack()
                if (!isActive || generation != playbackGeneration) {
                    return@launch
                }
                audioTrack = track
                track.play()
                var windowStart = startSample
                var windowEnd = endSample
                while (isActive && windowStart < replayTailSample) {
                    updateReplayState(
                        positionSample = windowStart,
                        windowStartSample = windowStart,
                        windowEndSample = windowEnd,
                        tailSample = replayTailSample,
                    )
                    var position = windowStart
                    while (isActive && position < windowEnd) {
                        val count = min(
                            PLAYBACK_CHUNK_SAMPLES,
                            (windowEnd - position).toInt(),
                        )
                        val samples = copyAudio(position, count)
                        val written = track.write(
                            samples,
                            0,
                            samples.size,
                            AudioTrack.WRITE_BLOCKING,
                        )
                        check(written >= 0) { "音频回放失败：$written" }
                        position += written
                        updateReplayPosition(position)
                    }

                    if (windowEnd >= replayTailSample - END_EPSILON_SAMPLES) {
                        reachedTail = true
                        break
                    }
                    windowStart = windowEnd
                    windowEnd = min(
                        windowStart + (WINDOW_SECONDS * SAMPLE_RATE).toLong(),
                        replayTailSample,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message) }
            } finally {
                runCatching { track?.pause() }
                track?.flush()
                track?.release()
                if (audioTrack === track) audioTrack = null
                if (generation == playbackGeneration) playbackJob = null
            }
            if (reachedTail && generation == playbackGeneration) resumeAnalysis()
        }
    }

    private fun updateReplayPosition(positionSample: Long) {
        mutableState.update {
            it.copy(
                mode = SpectrumMode.REPLAYING,
                playbackPositionSeconds = positionSample.toDouble() / SAMPLE_RATE,
            )
        }
    }

    private fun updateReplayState(
        positionSample: Long,
        windowStartSample: Long,
        windowEndSample: Long,
        tailSample: Long,
    ) {
        mutableState.update {
            it.copy(
                mode = SpectrumMode.REPLAYING,
                playbackPositionSeconds = positionSample.toDouble() / SAMPLE_RATE,
                replayWindowStartSeconds = windowStartSample.toDouble() / SAMPLE_RATE,
                replayWindowEndSeconds = windowEndSample.toDouble() / SAMPLE_RATE,
                replayTailEndSeconds = tailSample.toDouble() / SAMPLE_RATE,
            )
        }
    }

    private fun stopPlaybackInternal() {
        playbackGeneration++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        audioTrack = null
    }

    private fun appendAudio(samples: FloatArray) {
        samples.forEach { sample ->
            audioRing[ringWriteIndex] = sample
            ringWriteIndex = (ringWriteIndex + 1) % audioRing.size
        }
        totalSamples += samples.size
    }

    private fun oldestSample(): Long =
        max(0L, totalSamples - audioRing.size.toLong())

    private fun copyAudio(startSample: Long, count: Int): FloatArray {
        val oldest = oldestSample()
        val safeStart = startSample.coerceAtLeast(oldest)
        val safeCount = min(count, (totalSamples - safeStart).toInt())
        val output = FloatArray(safeCount)
        var ringIndex = (safeStart % audioRing.size).toInt()
        for (index in output.indices) {
            output[index] = audioRing[ringIndex]
            ringIndex = (ringIndex + 1) % audioRing.size
        }
        return output
    }

    private fun createAudioTrack(): AudioTrack {
        val minimumBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimumBuffer > 0) { "当前设备不支持实时音频回放" }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(
                max(minimumBuffer, PLAYBACK_CHUNK_SAMPLES * Float.SIZE_BYTES * 4),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                check(it.state == AudioTrack.STATE_INITIALIZED) {
                    it.release()
                    "无法初始化音频回放"
                }
            }
    }

    private fun acceptControlEvent(): Boolean {
        val now = System.nanoTime() / 1_000_000L
        if (now - lastControlAtMillis < CONTROL_DEBOUNCE_MILLIS) return false
        lastControlAtMillis = now
        return true
    }

    override fun onCleared() {
        resetSession()
    }

    companion object {
        private const val FFT_SIZE = 1024
        private const val HOP_SAMPLES = 256
        private const val MIN_HZ = 40
        private const val MAX_HZ = 8000
        private const val READ_SAMPLES = 512
        const val WINDOW_SECONDS = 5.0
        private const val SAMPLE_RATE = 16_000
        private const val HISTORY_SECONDS = 30.0
        private const val PUBLISH_INTERVAL_SECONDS = 1.0 / 30.0
        private const val PLAYBACK_CHUNK_SAMPLES = 512
        private const val END_EPSILON_SECONDS = 0.01
        private const val END_EPSILON_SAMPLES = 160L
        private const val CONTROL_DEBOUNCE_MILLIS = 300L
        private const val MAX_REPLAY_WINDOWS = 3
        // 30 seconds at 16 kHz / 256 samples is about 1875 frames.
        private const val MAX_FRAMES = 1880

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpectrumViewModel(RealtimeF0AudioRecorder(context.applicationContext))
            }
        }
    }
}
