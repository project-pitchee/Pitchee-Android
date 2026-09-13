package io.rovly.pitchee.ui

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.RecordedAudio
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val ScoreColors = listOf(
    Color(0xFFD55362),
    Color(0xFFE47A36),
    Color(0xFFD9B53D),
    Color(0xFF57A773),
    Color(0xFF168C80),
)

internal fun feminineScoreColor(score: Double): Color = when {
    score < 20.0 -> ScoreColors[0]
    score < 40.0 -> ScoreColors[1]
    score < 60.0 -> ScoreColors[2]
    score < 80.0 -> ScoreColors[3]
    else -> ScoreColors[4]
}

@Composable
internal fun LiveWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val barColor = color ?: MaterialTheme.colorScheme.primary
    val centerLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val spacing = 5.dp.toPx()
        val maxBars = (size.width / spacing).toInt().coerceAtLeast(1)
        val visible = amplitudes.takeLast(maxBars)
        val centerY = size.height / 2f
        drawLine(
            color = centerLineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        visible.forEachIndexed { index, amplitude ->
            val height = (amplitude.coerceIn(0f, 1f) * size.height * 0.82f)
                .coerceAtLeast(2.dp.toPx())
            val recency = index.toFloat() / max(visible.lastIndex, 1)
            val x = size.width - (visible.size - index - 0.5f) * spacing
            drawLine(
                color = barColor.copy(alpha = 0.22f + 0.72f * recency),
                start = Offset(x, centerY - height / 2f),
                end = Offset(x, centerY + height / 2f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun RecordedAudioTimeline(
    audio: RecordedAudio,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val playerAccent = if (darkTheme) Color(0xFF7DB3FF) else Color(0xFF3B82F6)
    val playerAccentContent = if (darkTheme) Color(0xFF102443) else Color.White
    val playerBackground = if (darkTheme) Color(0xFF172A46) else Color(0xFFE4F0FF)
    val playerContent = if (darkTheme) Color(0xFFEAF3FF) else Color(0xFF16345C)

    val player = remember(audio.file) { MediaPlayer() }
    var isPrepared by remember(audio.file) { mutableStateOf(false) }
    var isPlaying by remember(audio.file) { mutableStateOf(false) }
    var positionMs by remember(audio.file) { mutableLongStateOf(0L) }
    var durationMs by remember(audio.file) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(audio.file.absolutePath) { mutableStateOf(false) }
    var playWhenReady by remember(audio.file) { mutableStateOf(false) }
    var scrubbing by remember(audio.file) { mutableStateOf(false) }
    var resumeAfterScrub by remember(audio.file) { mutableStateOf(false) }
    val windowSeconds = min(2.0, audio.durationSeconds).coerceAtLeast(0.05)

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, audio.file) {
        runCatching {
            player.reset()
            player.setDataSource(audio.file.absolutePath)
            player.setOnPreparedListener {
                durationMs = it.duration
                isPrepared = true
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = it.duration.toLong()
            }
            player.prepareAsync()
        }.onFailure {
            isPrepared = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            positionMs = player.currentPosition.toLong()
            delay(50)
        }
    }

    LaunchedEffect(isPrepared, playWhenReady) {
        if (!isPrepared || !playWhenReady) return@LaunchedEffect
        if (positionMs >= durationMs - 100) {
            player.seekTo(0)
            positionMs = 0L
        }
        player.start()
        isPlaying = true
        playWhenReady = false
    }

    fun seekTo(seconds: Double) {
        if (!isPrepared) return
        val target = (seconds.coerceIn(0.0, audio.durationSeconds) * 1000.0).toInt()
        player.seekTo(target)
        positionMs = target.toLong()
    }

    fun togglePlayback() {
        if (!isPrepared) return
        if (isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            if (positionMs >= durationMs - 100) seekTo(0.0)
            player.start()
            isPlaying = true
        }
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 20.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "audio-player-corner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) playerBackground else playerAccent,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "audio-player-color",
    )
    val sizeModifier = if (expanded) Modifier.fillMaxWidth() else Modifier.size(52.dp)

    Surface(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.76f, stiffness = 220f),
            )
            .then(sizeModifier)
            .clickable(enabled = !expanded) {
                expanded = true
                playWhenReady = true
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = if (expanded) playerContent else playerAccentContent,
        shadowElevation = if (expanded) 0.dp else 3.dp,
    ) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        enabled = isPrepared,
                        onClick = ::togglePlayback,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playerAccent,
                            contentColor = playerAccentContent,
                            disabledContainerColor = playerAccent.copy(alpha = 0.42f),
                            disabledContentColor = playerAccentContent.copy(alpha = 0.58f),
                        ),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            ),
                            contentDescription = if (isPlaying) "暂停" else "播放",
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = (positionMs / 1000f).coerceIn(0f, audio.durationSeconds.toFloat()),
                        onValueChange = { seconds ->
                            if (!scrubbing) {
                                scrubbing = true
                                resumeAfterScrub = isPlaying
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                }
                            }
                            seekTo(seconds.toDouble())
                        },
                        onValueChangeFinished = {
                            scrubbing = false
                            if (resumeAfterScrub) {
                                player.start()
                                isPlaying = true
                            }
                            resumeAfterScrub = false
                        },
                        enabled = isPrepared,
                        valueRange = 0f..audio.durationSeconds.toFloat().coerceAtLeast(0.01f),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = playerAccent,
                            activeTrackColor = playerAccent,
                            inactiveTrackColor = playerContent.copy(alpha = 0.18f),
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                BasicWaveform(
                    audio = audio,
                    positionSeconds = positionMs / 1000.0,
                    windowSeconds = windowSeconds,
                    accent = playerAccent,
                    contentColor = playerContent,
                )
            }
        } else {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = "展开并播放",
                )
            }
        }
    }
}

@Composable
private fun BasicWaveform(
    audio: RecordedAudio,
    positionSeconds: Double,
    windowSeconds: Double,
    accent: Color,
    contentColor: Color,
) {
    val waveform = audio.waveform
    if (waveform.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
    ) {
        val centerY = size.height / 2f
        val viewportStart = positionSeconds - windowSeconds / 2.0
        val barSpacing = 3.dp.toPx()
        val barCount = (size.width / barSpacing).toInt().coerceAtLeast(2)
        val duration = audio.durationSeconds.coerceAtLeast(0.001)

        repeat(barCount) { index ->
            val fraction = index.toFloat() / (barCount - 1)
            val time = viewportStart + fraction * windowSeconds
            val amplitude = if (time in 0.0..duration) {
                val waveformIndex = (time / duration * waveform.lastIndex)
                    .roundToInt()
                    .coerceIn(0, waveform.lastIndex)
                waveform[waveformIndex].coerceIn(0f, 1f)
            } else {
                0f
            }
            val halfHeight = amplitude * size.height * 0.43f
            val x = fraction * size.width
            drawLine(
                color = contentColor.copy(alpha = 0.76f),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val centerX = size.width / 2f
        drawLine(
            color = accent,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
