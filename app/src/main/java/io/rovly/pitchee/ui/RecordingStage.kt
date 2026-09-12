package io.rovly.pitchee.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import space.pitchee.core.PitcheePhase

private enum class RecordVisualMode {
    IDLE,
    RECORDING,
    ANALYZING,
}

@Composable
internal fun RecordingStage(
    state: RecordUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = when (state) {
        is RecordUiState.Recording -> RecordVisualMode.RECORDING
        is RecordUiState.Analyzing -> RecordVisualMode.ANALYZING
        else -> RecordVisualMode.IDLE
    }
    val recording = state as? RecordUiState.Recording
    val startLabel = stringResource(R.string.start_recording)
    val stopLabel = stringResource(R.string.stop_and_analyze)
    val statusText = when (state) {
        is RecordUiState.Recording -> stringResource(
            R.string.recording_elapsed,
            state.elapsedSeconds,
        )
        is RecordUiState.Analyzing -> stringResource(
            when (state.phase) {
                PitcheePhase.LOADING_AUDIO -> R.string.analysis_phase_audio
                PitcheePhase.PREPARING_MODELS -> R.string.analysis_phase_models
                PitcheePhase.ANALYZING -> R.string.analysis_phase_inference
                PitcheePhase.COMPLETED -> R.string.analysis_phase_finishing
            }
        )
        else -> stringResource(R.string.click_to_record)
    }

    val targetButtonSize = when (mode) {
        RecordVisualMode.IDLE -> 96.dp
        RecordVisualMode.RECORDING -> 108.dp
        RecordVisualMode.ANALYZING -> 92.dp
    }
    val buttonSize by animateDpAsState(
        targetValue = targetButtonSize,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "record-button-size",
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val buttonColor = when (mode) {
        RecordVisualMode.IDLE -> primaryColor
        RecordVisualMode.RECORDING -> errorColor
        RecordVisualMode.ANALYZING -> MaterialTheme.colorScheme.primaryContainer
    }
    val pulseTransition = rememberInfiniteTransition(label = "recording-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recording-pulse-scale",
    )
    val waveformAlpha by animateFloatAsState(
        targetValue = if (recording != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "recording-waveform-alpha",
    )
    val progress by animateFloatAsState(
        targetValue = recording?.let {
            (it.elapsedSeconds / RecordViewModel.MAX_RECORDING_SECONDS).coerceIn(0f, 1f)
        } ?: 0f,
        animationSpec = tween(durationMillis = 180),
        label = "recording-progress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.analysis_inference_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LiveWaveform(
                amplitudes = recording?.amplitudes.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .graphicsLayer {
                        alpha = waveformAlpha
                        translationX = (1f - waveformAlpha) * size.width * 0.3f
                    },
                color = errorColor,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    if (recording != null) {
                        Canvas(
                            modifier = Modifier
                                .size(132.dp)
                                .scale(pulse),
                        ) {
                            drawCircle(errorColor.copy(alpha = 0.10f))
                        }
                        Canvas(Modifier.size(128.dp)) {
                            val stroke = 4.dp.toPx()
                            drawArc(
                                color = errorColor.copy(alpha = 0.18f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = errorColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            when (mode) {
                                RecordVisualMode.IDLE -> onStart()
                                RecordVisualMode.RECORDING -> onStop()
                                RecordVisualMode.ANALYZING -> Unit
                            }
                        },
                        enabled = mode != RecordVisualMode.ANALYZING,
                        modifier = Modifier
                            .size(buttonSize)
                            .semantics {
                                contentDescription = if (mode == RecordVisualMode.RECORDING) {
                                    stopLabel
                                } else {
                                    startLabel
                                }
                            },
                        shape = CircleShape,
                        color = buttonColor,
                        contentColor = if (mode == RecordVisualMode.IDLE) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.White
                        },
                        shadowElevation = if (mode == RecordVisualMode.RECORDING) 10.dp else 4.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.55f) + fadeIn()) togetherWith
                                        (scaleOut(targetScale = 0.55f) + fadeOut())
                                },
                                label = "record-button-content",
                            ) { currentMode ->
                                when (currentMode) {
                                    RecordVisualMode.IDLE -> Canvas(Modifier.size(34.dp)) {
                                        drawCircle(Color.White)
                                    }
                                    RecordVisualMode.RECORDING -> Canvas(Modifier.size(30.dp)) {
                                        drawRoundRect(
                                            color = Color.White,
                                            cornerRadius = CornerRadius(5.dp.toPx()),
                                        )
                                    }
                                    RecordVisualMode.ANALYZING -> CircularProgressIndicator(
                                        modifier = Modifier.size(34.dp),
                                        strokeWidth = 3.dp,
                                        color = primaryColor,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = stringResource(R.string.analysis_powered_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f),
            textAlign = TextAlign.Center,
        )
    }
}
