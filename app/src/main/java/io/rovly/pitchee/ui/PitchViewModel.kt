package io.rovly.pitchee.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.PitcheeRepository
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
import space.pitchee.core.PitcheeRealtimeF0

data class F0Point(
    val timestampSeconds: Double,
    val f0Hz: Float?,
    val confidence: Float,
)

data class PitchUiState(
    val running: Boolean = false,
    val preparing: Boolean = false,
    val points: List<F0Point> = emptyList(),
    val error: String? = null,
) {
    val current: F0Point? get() = points.lastOrNull()
    val currentHz: Float? get() = current?.f0Hz
}

class PitchViewModel(
    private val repository: PitcheeRepository,
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var analysisJob: Job? = null
    private val mutableState = MutableStateFlow(PitchUiState())
    val state: StateFlow<PitchUiState> = mutableState.asStateFlow()

    fun start() {
        if (analysisJob?.isActive == true) return
        mutableState.value = PitchUiState(preparing = true)
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            var stream: PitcheeRealtimeF0? = null
            try {
                stream = repository.createRealtimeF0()
                recorder.start()
                mutableState.update { it.copy(running = true, preparing = false) }
                val buffer = FloatArray(READ_SAMPLES)
                while (isActive) {
                    val count = recorder.read(buffer)
                    if (count <= 0) continue
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    stream.process(chunk) { timestamp, f0Hz, confidence, voiced ->
                        val point = F0Point(
                            timestampSeconds = timestamp,
                            f0Hz = f0Hz.takeIf { voiced && it.isFinite() },
                            confidence = confidence,
                        )
                        mutableState.update { current ->
                            current.copy(
                                points = (current.points + point).takeLast(MAX_POINTS),
                                error = null,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isActive) {
                    mutableState.update {
                        it.copy(
                            running = false,
                            preparing = false,
                            error = error.message ?: "实时基频监测失败",
                        )
                    }
                }
            } finally {
                recorder.stop()
                stream?.close()
            }
        }
    }

    fun stop() {
        analysisJob?.cancel()
        analysisJob = null
        mutableState.update { it.copy(running = false, preparing = false) }
    }

    override fun onCleared() {
        val job = analysisJob
        analysisJob = null
        job?.cancel()
        recorder.stop()
        if (job == null) repository.close() else job.invokeOnCompletion { repository.close() }
    }

    companion object {
        private const val READ_SAMPLES = 512
        private const val MAX_POINTS = 480

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                PitchViewModel(
                    repository = PitcheeRepository(appContext),
                    recorder = RealtimeF0AudioRecorder(appContext),
                )
            }
        }
    }
}
