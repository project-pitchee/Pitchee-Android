package io.rovly.pitchee.ui

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.RecordedAudio
import kotlin.math.ceil
import kotlin.math.floor
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
    val player = remember(audio.file) { MediaPlayer() }
    var isPrepared by remember(audio.file) { mutableStateOf(false) }
    var isPlaying by remember(audio.file) { mutableStateOf(false) }
    var positionMs by remember(audio.file) { mutableLongStateOf(0L) }
    var durationMs by remember(audio.file) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(audio.file.absolutePath) { mutableStateOf(false) }
    var viewportStart by rememberSaveable(audio.file.absolutePath) { mutableFloatStateOf(0f) }
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

    LaunchedEffect(isPlaying, positionMs, windowSeconds) {
        if (!isPlaying) return@LaunchedEffect
        val playhead = positionMs / 1000.0
        val viewportEnd = viewportStart + windowSeconds
        if (playhead < viewportStart || playhead > viewportEnd) {
            viewportStart = (playhead - windowSeconds * 0.2)
                .coerceIn(0.0, (audio.durationSeconds - windowSeconds).coerceAtLeast(0.0))
                .toFloat()
        }
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
            val positionSeconds = positionMs / 1000.0
            if (positionSeconds !in viewportStart.toDouble()..(viewportStart + windowSeconds)) {
                seekTo(viewportStart + windowSeconds / 2.0)
            } else if (positionMs >= durationMs - 100) {
                seekTo(viewportStart.toDouble())
            }
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
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            PlayerPink
        },
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
            .clickable(enabled = !expanded) { expanded = true },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = if (expanded) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            PlayerInk
        },
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
                            containerColor = PlayerPink,
                            contentColor = PlayerInk,
                            disabledContainerColor = PlayerPink.copy(alpha = 0.45f),
                            disabledContentColor = PlayerInk.copy(alpha = 0.55f),
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "录音回放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "显示 2 秒 · 左右拖动",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs.toLong())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                BasicWaveform(
                    audio = audio,
                    positionSeconds = positionMs / 1000.0,
                    viewportStart = viewportStart.toDouble(),
                    windowSeconds = windowSeconds,
                    onViewportChange = { viewportStart = it.toFloat() },
                )
            }
        } else {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = "展开播放器",
                )
            }
        }
    }
}

private val PlayerPink = Color(0xFFFFB6C1)
private val PlayerInk = Color(0xFF4A1D2B)

@Composable
private fun BasicWaveform(
    audio: RecordedAudio,
    positionSeconds: Double,
    viewportStart: Double,
    windowSeconds: Double,
    onViewportChange: (Double) -> Unit,
) {
    val waveform = audio.waveform
    if (waveform.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.onSurface
    val centerColor = MaterialTheme.colorScheme.outlineVariant
    val playheadColor = PlayerPink
    val currentViewportStart by rememberUpdatedState(viewportStart)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)
    val maxStart = (audio.durationSeconds - windowSeconds).coerceAtLeast(0.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .pointerInput(audio.file, windowSeconds) {
                var dragStart = 0f
                var accumulatedDelta = 0f
                detectDragGestures(
                    onDragStart = {
                        dragStart = currentViewportStart.toFloat()
                        accumulatedDelta = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDelta -= dragAmount.x / size.width * windowSeconds.toFloat()
                        val next = (dragStart + accumulatedDelta)
                            .coerceIn(0f, maxStart.toFloat())
                        currentOnViewportChange(next.toDouble())
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        drawLine(
            color = centerColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx(),
        )

        val samplesPerSecond = waveform.size / audio.durationSeconds
        val firstPoint = floor(viewportStart * samplesPerSecond).toInt()
            .coerceIn(0, waveform.lastIndex)
        val lastPoint = ceil((viewportStart + windowSeconds) * samplesPerSecond).toInt()
            .coerceIn(firstPoint + 1, waveform.size)
        val barSpacing = 3.dp.toPx()
        val barCount = (size.width / barSpacing).toInt().coerceAtLeast(2)
        repeat(barCount) { index ->
            val fraction = index.toFloat() / (barCount - 1)
            val waveformIndex = (firstPoint + fraction * (lastPoint - firstPoint - 1))
                .roundToInt()
                .coerceIn(0, waveform.lastIndex)
            val halfHeight = waveform[waveformIndex].coerceIn(0f, 1f) * size.height * 0.43f
            val x = fraction * size.width
            drawLine(
                color = lineColor.copy(alpha = 0.82f),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val playheadVisible = positionSeconds in viewportStart..(viewportStart + windowSeconds)
        if (playheadVisible) {
            val x = ((positionSeconds - viewportStart) / windowSeconds * size.width).toFloat()
            drawLine(
                color = playheadColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
