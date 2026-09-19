package io.rovly.pitchee.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.RealtimeF0AudioRecorder
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
import java.util.ArrayDeque

data class SpectrumFramePoint(
    val timestampSeconds: Double,
    val magnitudes: FloatArray,
    val firstBinIndex: Int,
    val binHz: Float,
    val peakHz: Float,
    val peakDb: Float,
    val centroidHz: Float,
    val rolloffHz: Float,
    val flatness: Float,
)

data class SpectrumUiState(
    val running: Boolean = false,
    val preparing: Boolean = false,
    val frames: List<SpectrumFramePoint> = emptyList(),
    val error: String? = null,
) {
    val current: SpectrumFramePoint?
        get() = frames.lastOrNull()
}

class SpectrumViewModel(
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var analysisJob: Job? = null
    private val frameBuffer = ArrayDeque<SpectrumFramePoint>()
    private var lastPublishedTimestamp = Double.NEGATIVE_INFINITY
    private val mutableState = MutableStateFlow(SpectrumUiState())
    val state: StateFlow<SpectrumUiState> = mutableState.asStateFlow()

    fun start() {
        if (analysisJob?.isActive == true) return
        frameBuffer.clear()
        lastPublishedTimestamp = Double.NEGATIVE_INFINITY
        mutableState.value = SpectrumUiState(preparing = true)
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            var spectrum: PitcheeSpectrum? = null
            try {
                val stream = PitcheeSpectrum.create(
                    fftSize = FFT_SIZE,
                    hopSamples = HOP_SAMPLES,
                    minHz = MIN_HZ,
                    maxHz = MAX_HZ,
                )
                spectrum = stream
                recorder.start()
                mutableState.update { it.copy(running = true, preparing = false) }
                val buffer = FloatArray(READ_SAMPLES)
                while (isActive) {
                    val count = recorder.read(buffer)
                    if (count <= 0) continue
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    stream.process(chunk) {
                            timestampSeconds,
                            magnitudes,
                            firstBinIndex,
                            binHz,
                            peakHz,
                            centroidHz,
                            rolloffHz,
                            flatness,
                        ->
                        val point = SpectrumFramePoint(
                            timestampSeconds = timestampSeconds,
                            magnitudes = magnitudes,
                            firstBinIndex = firstBinIndex.toInt(),
                            binHz = binHz,
                            peakHz = peakHz,
                            peakDb = magnitudes.maxOrNull() ?: -120f,
                            centroidHz = centroidHz,
                            rolloffHz = rolloffHz,
                            flatness = flatness,
                        )
                        frameBuffer.addLast(point)
                        while (frameBuffer.isNotEmpty() &&
                            frameBuffer.first().timestampSeconds <
                            point.timestampSeconds - WINDOW_SECONDS
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
                        running = false,
                        preparing = false,
                        error = error.message,
                    )
                }
            } finally {
                spectrum?.close()
                recorder.stop()
            }
        }
    }

    fun stop() {
        analysisJob?.cancel()
        analysisJob = null
        recorder.stop()
        mutableState.update { it.copy(running = false, preparing = false) }
    }

    fun clearDisplay() {
        if (!mutableState.value.running && !mutableState.value.preparing) {
            mutableState.value = SpectrumUiState()
        }
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val FFT_SIZE = 1024
        private const val HOP_SAMPLES = 256
        private const val MIN_HZ = 40
        private const val MAX_HZ = 8000
        private const val READ_SAMPLES = 512
        private const val WINDOW_SECONDS = 3.0
        private const val PUBLISH_INTERVAL_SECONDS = 1.0 / 30.0
        // 3 seconds at 16 kHz / 256 samples is about 188 frames.
        private const val MAX_FRAMES = 192

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpectrumViewModel(RealtimeF0AudioRecorder(context.applicationContext))
            }
        }
    }
}
