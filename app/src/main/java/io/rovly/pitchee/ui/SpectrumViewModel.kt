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
    PLAYING,
    PAUSED,
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
    val playbackEndSeconds: Double? = null,
    val error: String? = null,
)

class SpectrumViewModel(
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var liveJob: Job? = null
    private var playbackJob: Job? = null
    private var spectrum: PitcheeSpectrum? = null
    private var audioTrack: AudioTrack? = null
    private val frameBuffer = ArrayDeque<SpectrumFramePoint>()
    private val audioRing = FloatArray((SAMPLE_RATE * HISTORY_SECONDS).toInt())
    private var ringWriteIndex = 0
    @Volatile
    private var totalSamples = 0L
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
        stopLiveCapture()
        stopPlaybackInternal()
        val currentSample = mutableState.value.playbackPositionSeconds
            ?.times(SAMPLE_RATE)
            ?.toLong()
            ?: totalSamples
        val targetSample = (currentSample - REWIND_SECONDS * SAMPLE_RATE)
            .coerceAtLeast(oldestSample())
        updatePlaybackState(
            mode = SpectrumMode.PAUSED,
            positionSample = targetSample,
        )
    }

    fun forwardFiveSeconds() {
        if (mutableState.value.mode == SpectrumMode.IDLE) return
        stopPlaybackInternal()
        val currentSample = mutableState.value.playbackPositionSeconds
            ?.times(SAMPLE_RATE)
            ?.toLong()
            ?: totalSamples
        val targetSample = (currentSample + FORWARD_SECONDS * SAMPLE_RATE)
            .coerceAtMost(totalSamples)
        if (targetSample >= totalSamples - END_EPSILON_SAMPLES) {
            updatePlaybackState(
                mode = SpectrumMode.PAUSED,
                positionSample = totalSamples,
            )
            resumeAnalysis()
        } else {
            updatePlaybackState(
                mode = SpectrumMode.PAUSED,
                positionSample = targetSample,
            )
        }
    }

    fun togglePlaybackOrAnalysis() {
        when (mutableState.value.mode) {
            SpectrumMode.IDLE -> start()
            SpectrumMode.PREPARING, SpectrumMode.ANALYZING -> pauseAnalysis()
            SpectrumMode.PLAYING -> pausePlayback()
            SpectrumMode.PAUSED -> {
                val positionSample = mutableState.value.playbackPositionSeconds
                    ?.times(SAMPLE_RATE)
                    ?.toLong()
                    ?: totalSamples
                if (positionSample >= totalSamples - END_EPSILON_SAMPLES) {
                    resumeAnalysis()
                } else {
                    startPlayback(positionSample)
                }
            }
        }
    }

    fun stop() {
        resetSession()
        mutableState.value = SpectrumUiState()
    }

    private fun resetSession() {
        liveJob?.cancel()
        liveJob = null
        stopPlaybackInternal()
        recorder.stop()
        spectrum?.close()
        spectrum = null
        frameBuffer.clear()
        audioRing.fill(0f)
        ringWriteIndex = 0
        totalSamples = 0L
        lastPublishedTimestamp = Double.NEGATIVE_INFINITY
        mutableState.value = SpectrumUiState()
    }

    private fun startAnalysisInternal() {
        if (mutableState.value.mode == SpectrumMode.ANALYZING ||
            mutableState.value.mode == SpectrumMode.PREPARING
        ) {
            return
        }
        stopPlaybackInternal()
        mutableState.update {
            it.copy(
                mode = SpectrumMode.PREPARING,
                playbackPositionSeconds = null,
                playbackEndSeconds = null,
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        mode = SpectrumMode.IDLE,
                        error = error.message,
                    )
                }
            }
        }
    }

    private fun pauseAnalysis() {
        stopLiveCapture()
        updatePlaybackState(
            mode = SpectrumMode.PAUSED,
            positionSample = totalSamples,
        )
    }

    private fun resumeAnalysis() {
        stopPlaybackInternal()
        startAnalysisInternal()
    }

    private fun stopLiveCapture() {
        liveJob?.cancel()
        liveJob = null
        recorder.stop()
    }

    private fun startPlayback(startSample: Long) {
        val safeStart = startSample
            .coerceAtLeast(oldestSample())
            .coerceAtMost(totalSamples)
        if (safeStart >= totalSamples - END_EPSILON_SAMPLES) {
            resumeAnalysis()
            return
        }
        updatePlaybackState(mode = SpectrumMode.PLAYING, positionSample = safeStart)
        playbackJob = viewModelScope.launch(Dispatchers.Default) {
            var track: AudioTrack? = null
            var reachedEnd = false
            try {
                track = createAudioTrack()
                audioTrack = track
                track.play()
                var positionSample = safeStart
                var lastPublished = Double.NEGATIVE_INFINITY
                while (isActive && positionSample < totalSamples) {
                    val count = min(PLAYBACK_CHUNK_SAMPLES, (totalSamples - positionSample).toInt())
                    val samples = copyAudio(positionSample, count)
                    val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    check(written >= 0) { "音频回放失败：$written" }
                    positionSample += written
                    val positionSeconds = positionSample.toDouble() / SAMPLE_RATE
                    if (positionSeconds - lastPublished >= PLAYBACK_PUBLISH_INTERVAL_SECONDS) {
                        lastPublished = positionSeconds
                        mutableState.update {
                            it.copy(playbackPositionSeconds = positionSeconds)
                        }
                    }
                }
                reachedEnd = positionSample >= totalSamples
                if (reachedEnd) {
                    updatePlaybackState(
                        mode = SpectrumMode.PAUSED,
                        positionSample = totalSamples,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        mode = SpectrumMode.PAUSED,
                        error = error.message,
                    )
                }
            } finally {
                runCatching { track?.pause() }
                track?.flush()
                track?.release()
                if (audioTrack === track) audioTrack = null
                playbackJob = null
            }
            if (reachedEnd) resumeAnalysis()
        }
    }

    private fun pausePlayback() {
        val positionSample = mutableState.value.playbackPositionSeconds
            ?.times(SAMPLE_RATE)
            ?.toLong()
            ?: totalSamples
        stopPlaybackInternal()
        updatePlaybackState(
            mode = SpectrumMode.PAUSED,
            positionSample = positionSample,
        )
    }

    private fun stopPlaybackInternal() {
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching { track.pause() }
            track.flush()
        }
    }

    private fun updatePlaybackState(
        mode: SpectrumMode,
        positionSample: Long,
    ) {
        val safeSample = positionSample.coerceIn(0L, totalSamples)
        mutableState.update {
            it.copy(
                mode = mode,
                playbackPositionSeconds = safeSample.toDouble() / SAMPLE_RATE,
                playbackEndSeconds = totalSamples.toDouble() / SAMPLE_RATE,
            )
        }
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
            .setBufferSizeInBytes(max(minimumBuffer, PLAYBACK_CHUNK_SAMPLES * Float.SIZE_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                check(it.state == AudioTrack.STATE_INITIALIZED) {
                    it.release()
                    "无法初始化音频回放"
                }
            }
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
        private const val SAMPLE_RATE = 16_000
        private const val HISTORY_SECONDS = 10.0
        private const val PUBLISH_INTERVAL_SECONDS = 1.0 / 30.0
        private const val PLAYBACK_PUBLISH_INTERVAL_SECONDS = 1.0 / 30.0
        private const val REWIND_SECONDS = 5L
        private const val FORWARD_SECONDS = 5L
        private const val PLAYBACK_CHUNK_SAMPLES = 512
        private const val END_EPSILON_SAMPLES = 160L
        // 10 seconds at 16 kHz / 256 samples is about 625 frames.
        private const val MAX_FRAMES = 640

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpectrumViewModel(RealtimeF0AudioRecorder(context.applicationContext))
            }
        }
    }
}
