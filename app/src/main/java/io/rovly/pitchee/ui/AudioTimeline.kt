package io.rovly.pitchee.ui

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.FeminineTimeline
import io.rovly.pitchee.data.RecordedAudio
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
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
internal fun ExpandableAudioTimeline(
    audio: RecordedAudio,
    timeline: FeminineTimeline?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(audio.file.absolutePath) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        FilledIconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = if (expanded) "收起播放器" else "展开播放器",
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                RecordedAudioTimeline(
                    audio = audio,
                    timeline = timeline,
                )
            }
        }
    }
}

@Composable
internal fun RecordedAudioTimeline(
    audio: RecordedAudio,
    timeline: FeminineTimeline?,
    modifier: Modifier = Modifier,
) {
    val player = remember(audio.file) { MediaPlayer() }
    var isPrepared by remember(audio.file) { mutableStateOf(false) }
    var isPlaying by remember(audio.file) { mutableStateOf(false) }
    var positionMs by remember(audio.file) { mutableLongStateOf(0L) }
    var durationMs by remember(audio.file) { mutableIntStateOf(0) }

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

    fun seekTo(seconds: Double) {
        if (!isPrepared) return
        val target = (seconds.coerceIn(0.0, audio.durationSeconds) * 1000.0).toInt()
        player.seekTo(target)
        positionMs = target.toLong()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "录音时间轴",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "拖动定位 · 双指缩放 · 点击跳转",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    enabled = isPrepared,
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            if (positionMs >= durationMs - 100) seekTo(0.0)
                            player.start()
                            isPlaying = true
                        }
                    },
                ) {
                    Text(if (isPlaying) "暂停" else "播放")
                }
            }
            Spacer(Modifier.height(14.dp))
            WaveformView(
                audio = audio,
                timeline = timeline,
                positionSeconds = positionMs / 1000.0,
                onSeek = ::seekTo,
            )
            Spacer(Modifier.height(8.dp))
            FeminineScoreLegend()
        }
    }
}

@Composable
private fun WaveformView(
    audio: RecordedAudio,
    timeline: FeminineTimeline?,
    positionSeconds: Double,
    onSeek: (Double) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outlineVariant
    val unknown = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    var zoom by rememberSaveable(audio.file.absolutePath) { mutableFloatStateOf(1f) }
    var viewportStart by rememberSaveable(audio.file.absolutePath) { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val visibleSpan = 1f / zoom
    val visibleStartSeconds = viewportStart * audio.durationSeconds
    val visibleDurationSeconds = audio.durationSeconds / zoom
    val playheadSeconds = positionSeconds.coerceIn(0.0, audio.durationSeconds)
    val playheadVisible = playheadSeconds in visibleStartSeconds..(visibleStartSeconds + visibleDurationSeconds)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .onSizeChanged { canvasSize = it }
                .pointerInput(audio.file, visibleSpan, viewportStart) {
                    detectTapGestures { point ->
                        if (size.width == 0) return@detectTapGestures
                        val fraction = (point.x / size.width).coerceIn(0f, 1f)
                        onSeek(visibleStartSeconds + fraction * visibleDurationSeconds)
                    }
                }
                .pointerInput(audio.file, zoom, viewportStart) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        if (size.width == 0) return@detectTransformGestures
                        val oldSpan = 1f / zoom
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 12f)
                        val nextSpan = 1f / nextZoom
                        val centerFraction = centroid.x / size.width
                        val anchored = viewportStart + centerFraction * oldSpan -
                            centerFraction * nextSpan
                        val panned = anchored - pan.x / size.width * nextSpan
                        zoom = nextZoom
                        viewportStart = panned.coerceIn(0f, 1f - nextSpan)
                    }
                },
        ) {
            drawRoundRect(
                color = track.copy(alpha = 0.28f),
                cornerRadius = CornerRadius(18.dp.toPx()),
            )

            if (timeline != null) {
                val firstBin = floor(viewportStart * timeline.size)
                    .toInt()
                    .coerceIn(0, timeline.size - 1)
                val lastBin = ceil((viewportStart + visibleSpan) * timeline.size)
                    .toInt()
                    .coerceIn(firstBin + 1, timeline.size)
                for (bin in firstBin until lastBin) {
                    val binStartFraction = bin.toFloat() / timeline.size
                    val binEndFraction = (bin + 1f) / timeline.size
                    val x = ((binStartFraction - viewportStart) / visibleSpan) * size.width
                    val width = ((binEndFraction - binStartFraction) / visibleSpan) * size.width
                    val score = timeline.pointAtBin(bin)?.score
                    drawRect(
                        color = score?.let { feminineScoreColor(it.toDouble()) } ?: unknown,
                        topLeft = Offset(x, 0f),
                        size = Size(max(width + 0.6f, 1f), size.height),
                    )
                }
            }

            val waveform = audio.waveform
            val firstPoint = floor(viewportStart * waveform.size).toInt()
                .coerceIn(0, waveform.lastIndex)
            val lastPoint = ceil((viewportStart + visibleSpan) * waveform.size).toInt()
                .coerceIn(firstPoint + 1, waveform.size)
            val drawSteps = (size.width / 2.5f).toInt().coerceAtLeast(1)
            repeat(drawSteps) { index ->
                val fraction = index.toFloat() / max(drawSteps - 1, 1)
                val point = (firstPoint + fraction * (lastPoint - firstPoint - 1))
                    .roundToInt()
                    .coerceIn(0, waveform.lastIndex)
                val amplitude = waveform[point].coerceIn(0f, 1f)
                val halfHeight = amplitude * size.height * 0.42f
                val x = size.width * fraction
                drawLine(
                    color = onSurface.copy(alpha = 0.86f),
                    start = Offset(x, size.height / 2f - halfHeight),
                    end = Offset(x, size.height / 2f + halfHeight),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            if (playheadVisible) {
                val playheadX = ((playheadSeconds - visibleStartSeconds) /
                    visibleDurationSeconds * size.width).toFloat()
                drawLine(
                    color = accent,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
        ) {
            if (playheadVisible) {
                val badgeWidth = 112.dp
                val playheadX = (
                    (playheadSeconds - visibleStartSeconds) / visibleDurationSeconds *
                        maxWidth.value
                    ).toFloat()
                val badgeOffset = (playheadX.dp - badgeWidth / 2)
                    .coerceIn(0.dp, maxWidth - badgeWidth)
                val score = timeline?.pointAt(playheadSeconds)?.score
                Surface(
                    modifier = Modifier.offset(x = badgeOffset),
                    shape = RoundedCornerShape(8.dp),
                    color = score?.let { feminineScoreColor(it.toDouble()) }
                        ?: MaterialTheme.colorScheme.outlineVariant,
                    contentColor = Color.White,
                ) {
                    Text(
                        text = score?.let { "当前 %.0f".format(it) } ?: "未检测到语音",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${(zoom * 10).roundToInt() / 10f}×",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val oldSpan = 1f / zoom
                    val nextZoom = (zoom / 1.5f).coerceAtLeast(1f)
                    val nextSpan = 1f / nextZoom
                    viewportStart = (viewportStart + oldSpan / 2f - nextSpan / 2f)
                        .coerceIn(0f, 1f - nextSpan)
                    zoom = nextZoom
                },
                enabled = zoom > 1f,
            ) {
                Text("缩小")
            }
            TextButton(onClick = {
                zoom = 1f
                viewportStart = 0f
            }) {
                Text("重置")
            }
            TextButton(
                onClick = {
                    val oldSpan = 1f / zoom
                    val nextZoom = (zoom * 1.5f).coerceAtMost(12f)
                    val nextSpan = 1f / nextZoom
                    viewportStart = (viewportStart + oldSpan / 2f - nextSpan / 2f)
                        .coerceIn(0f, 1f - nextSpan)
                    zoom = nextZoom
                },
                enabled = zoom < 12f,
            ) {
                Text("放大")
            }
        }
    }
}

@Composable
private fun FeminineScoreLegend() {
    Column {
        Text(
            text = "时间级女性化指数（VFP + 局部自然度 + F0）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val labels = listOf("0–19", "20–39", "40–59", "60–79", "80+")
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(8.dp)) {
                        drawCircle(ScoreColors[index])
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "灰色表示 VAD 未识别到有效语音；时间级综合分使用源音频上的 VFP、自然度和 F0 窗口计算，最终总分仍可能因整段规则而不同。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
