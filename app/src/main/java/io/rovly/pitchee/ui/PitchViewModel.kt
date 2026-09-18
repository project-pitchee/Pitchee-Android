package io.rovly.pitchee.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.PitcheeRepository
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.RealtimeF0AudioRecorder
import io.rovly.pitchee.data.RealtimeF0HistoryEntry
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
    val currentHz: Float?
        get() {
            if (!running) return null
            val latestTimestamp = points.lastOrNull()?.timestampSeconds ?: return null
            val lastVoiced = points.asReversed().firstOrNull { it.f0Hz != null } ?: return null
            return lastVoiced.f0Hz.takeIf {
                latestTimestamp - lastVoiced.timestampSeconds <= CURRENT_F0_HOLD_SECONDS
            }
        }

    private companion object {
        const val CURRENT_F0_HOLD_SECONDS = 2.0
    }
}

class PitchViewModel(
    private val repository: PitcheeRepository,
    private val recorder: RealtimeF0AudioRecorder,
    private val historyStore: HistoryStore,
) : ViewModel() {
    private var analysisJob: Job? = null
    private var sessionRecorded = false
    private var sessionF0Sum = 0.0
    private var sessionF0Count = 0
    private val mutableState = MutableStateFlow(PitchUiState())
    val state: StateFlow<PitchUiState> = mutableState.asStateFlow()

    fun start() {
        if (analysisJob?.isActive == true) return
        sessionRecorded = false
        sessionF0Sum = 0.0
        sessionF0Count = 0
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
                        val measuredF0 = f0Hz.takeIf { voiced && it.isFinite() }
                        if (measuredF0 != null) {
                            sessionF0Sum += measuredF0
                            sessionF0Count++
                        }
                        val point = F0Point(
                            timestampSeconds = timestamp,
                            f0Hz = measuredF0,
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
                recordSession()
                recorder.stop()
                stream?.close()
            }
        }
    }

    fun stop() {
        recordSession()
        analysisJob?.cancel()
        analysisJob = null
        mutableState.update { it.copy(running = false, preparing = false) }
    }

    private fun recordSession() {
        if (sessionRecorded) return
        if (sessionF0Count > 0) {
            historyStore.addRealtimeF0(
                RealtimeF0HistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    meanF0Hz = sessionF0Sum / sessionF0Count,
                ),
            )
        }
        sessionRecorded = true
    }

    override fun onCleared() {
        recordSession()
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
                    historyStore = HistoryStore(appContext),
                )
            }
        }
    }
}
