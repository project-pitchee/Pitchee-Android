package io.rovly.pitchee.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.AudioRecorder
import io.rovly.pitchee.data.PitcheeRepository
import io.rovly.pitchee.data.PitcheeResult
import io.rovly.pitchee.data.RecordedAudio
import io.rovly.pitchee.data.hasSpeechSecondsAtLeast
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeProgressStage
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RecordUiState {
    data object Ready : RecordUiState

    data class Recording(
        val elapsedSeconds: Float,
        val amplitudes: List<Float>,
    ) : RecordUiState

    data class Analyzing(
        val phase: PitcheePhase,
        val progress: PitcheeProgress? = null,
        val audio: RecordedAudio? = null,
    ) : RecordUiState

    data class Success(
        val result: PitcheeResult,
        val audio: RecordedAudio,
    ) : RecordUiState

    data class Error(
        val message: String,
        val audio: RecordedAudio? = null,
    ) : RecordUiState
}

class RecordViewModel(
    private val repository: PitcheeRepository,
    private val recorder: AudioRecorder,
) : ViewModel() {
    private var timerJob: Job? = null
    private var retainedAudio: RecordedAudio? = null

    private val mutableState = MutableStateFlow<RecordUiState>(RecordUiState.Ready)
    val state: StateFlow<RecordUiState> = mutableState.asStateFlow()

    fun startRecording() {
        if (mutableState.value is RecordUiState.Recording ||
            mutableState.value is RecordUiState.Analyzing
        ) {
            return
        }

        discardRetainedAudio()
        try {
            recorder.start()
        } catch (error: Throwable) {
            mutableState.value = RecordUiState.Error(
                error.message ?: "无法开始录音，请检查麦克风权限"
            )
            return
        }

        val amplitudes = mutableListOf(0f)
        mutableState.value = RecordUiState.Recording(0f, amplitudes)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive && mutableState.value is RecordUiState.Recording) {
                delay(TIMER_INTERVAL_MS)
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
                val previousAmplitude = amplitudes.lastOrNull() ?: 0f
                val nextAmplitude = recorder.currentAmplitude()
                amplitudes += previousAmplitude * 0.45f + nextAmplitude * 0.55f
                mutableState.value = RecordUiState.Recording(
                    elapsedSeconds = elapsed.coerceAtMost(MAX_RECORDING_SECONDS),
                    amplitudes = amplitudes.toList(),
                )
                if (elapsed >= MAX_RECORDING_SECONDS) {
                    stopAndAnalyze()
                    return@launch
                }
            }
        }
    }

    fun stopAndAnalyze() {
        if (mutableState.value !is RecordUiState.Recording) return

        timerJob?.cancel()
        timerJob = null
        val file = try {
            recorder.stop()
        } catch (error: Throwable) {
            mutableState.value = RecordUiState.Error(error.message ?: "录音失败")
            return
        }

        mutableState.value = RecordUiState.Analyzing(PitcheePhase.LOADING_AUDIO)
        viewModelScope.launch {
            var recordedAudio: RecordedAudio? = null
            try {
                val pcm = repository.decode(Uri.fromFile(file), maxSeconds = null) { phase ->
                    mutableState.value = RecordUiState.Analyzing(phase)
                }
                recordedAudio = RecordedAudio.from(file, pcm).also {
                    retainedAudio = it
                }
                mutableState.value = RecordUiState.Analyzing(
                    phase = PitcheePhase.PREPARING_MODELS,
                    audio = recordedAudio,
                )
                val result = repository.analyzePcm(
                    samples = pcm.samples,
                    sampleRate = pcm.sampleRate,
                    channels = pcm.channels,
                    onProgress = { progress ->
                        mutableState.value = RecordUiState.Analyzing(
                            phase = progress.stage.toPhase(),
                            progress = progress,
                            audio = recordedAudio,
                        )
                    },
                )
                if (!result.hasSpeechSecondsAtLeast(MINIMUM_SPEECH_SECONDS)) {
                    recordedAudio.file.delete()
                    retainedAudio = null
                    mutableState.value = RecordUiState.Error(
                        message = "有效说话时间不足 5 秒，请重新录制"
                    )
                } else {
                    mutableState.value = RecordUiState.Analyzing(
                        phase = PitcheePhase.COMPLETED,
                        progress = PitcheeProgress(
                            stage = PitcheeProgressStage.COMPLETED,
                            completed = 1,
                            total = 1,
                            stageFraction = 1f,
                        ),
                        audio = recordedAudio,
                    )
                    delay(COMPLETION_HOLD_MS)
                    mutableState.value = RecordUiState.Success(result, recordedAudio)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val audio = recordedAudio
                if (audio == null) file.delete()
                mutableState.value = RecordUiState.Error(
                    message = error.message ?: "音频分析失败",
                    audio = audio,
                )
            }
        }
    }

    fun reset() {
        if (mutableState.value is RecordUiState.Analyzing) return
        discardRetainedAudio()
        mutableState.value = RecordUiState.Ready
    }

    override fun onCleared() {
        timerJob?.cancel()
        recorder.cancel()
        discardRetainedAudio()
        repository.close()
    }

    private fun discardRetainedAudio() {
        retainedAudio?.file?.delete()
        retainedAudio = null
    }

    private fun PitcheeProgressStage.toPhase(): PitcheePhase = when (this) {
        PitcheeProgressStage.LOADING_AUDIO,
        PitcheeProgressStage.RESAMPLING_AUDIO,
        -> PitcheePhase.LOADING_AUDIO
        PitcheeProgressStage.COMPLETED -> PitcheePhase.COMPLETED
        else -> PitcheePhase.ANALYZING
    }

    companion object {
        const val MAX_RECORDING_SECONDS = 20f
        const val MINIMUM_SPEECH_SECONDS = 5.0
        private const val TIMER_INTERVAL_MS = 16L
        private const val COMPLETION_HOLD_MS = 900L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                RecordViewModel(
                    repository = PitcheeRepository(appContext),
                    recorder = AudioRecorder(appContext),
                )
            }
        }
    }
}
