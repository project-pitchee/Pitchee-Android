package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun RealtimeSpectrumScreen() {
    val context = LocalContext.current
    val factory = remember { SpectrumViewModel.factory(context.applicationContext) }
    val viewModel: SpectrumViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    fun toggleAnalysis() {
        if (state.mode == SpectrumMode.IDLE) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                permissionDenied = false
                viewModel.start()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            viewModel.togglePlaybackOrAnalysis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SpectrumChart(
            frames = state.frames,
            playbackPositionSeconds = state.playbackPositionSeconds,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.spectrum_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.spectrum_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.frames.isEmpty()) {
            Text(
                text = if (state.mode == SpectrumMode.PREPARING) {
                    stringResource(R.string.spectrum_preparing)
                } else {
                    stringResource(R.string.spectrum_no_signal)
                },
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.record_permission_denied),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
            }
            SpectrumControls(
                mode = state.mode,
                onToggle = ::toggleAnalysis,
                onRewind = viewModel::rewindFiveSeconds,
                onForward = viewModel::forwardFiveSeconds,
            )
        }
    }
}

@Composable
private fun SpectrumControls(
    mode: SpectrumMode,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
) {
    val showToolbar = mode != SpectrumMode.IDLE
    AnimatedContent(
        targetState = showToolbar,
        transitionSpec = {
            val animation = spring<Float>(dampingRatio = 0.78f, stiffness = 320f)
            scaleIn(animation, initialScale = 0.72f) togetherWith
                scaleOut(animation, targetScale = 0.72f)
        },
        label = "spectrum-controls",
    ) { expanded ->
        if (expanded) {
            SpectrumToolbar(
                mode = mode,
                onToggle = onToggle,
                onRewind = onRewind,
                onForward = onForward,
            )
        } else {
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.spectrum_start_monitoring),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun SpectrumToolbar(
    mode: SpectrumMode,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
) {
    val playing = mode == SpectrumMode.ANALYZING || mode == SpectrumMode.PLAYING
    val centerIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIconButton(
                iconRes = R.drawable.ic_rewind,
                contentDescription = stringResource(R.string.spectrum_rewind_5),
                onClick = onRewind,
            )
            AnimatedContent(
                targetState = if (mode == SpectrumMode.PREPARING) null else centerIcon,
                transitionSpec = {
                    scaleIn(
                        spring(dampingRatio = 0.72f, stiffness = 360f),
                        initialScale = 0.68f,
                    ) togetherWith scaleOut(
                        spring(dampingRatio = 0.72f, stiffness = 360f),
                        targetScale = 0.68f,
                    )
                },
                label = "spectrum-center-icon",
            ) { icon ->
                FilledIconButton(
                    onClick = onToggle,
                    enabled = mode != SpectrumMode.PREPARING,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (icon == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = stringResource(
                                if (playing) {
                                    R.string.spectrum_pause
                                } else {
                                    R.string.spectrum_resume
                                },
                            ),
                        )
                    }
                }
            }
            ControlIconButton(
                iconRes = R.drawable.ic_forward,
                contentDescription = stringResource(R.string.spectrum_forward_5),
                onClick = onForward,
            )
        }
    }
}

@Composable
private fun ControlIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun SpectrumChart(
    frames: List<SpectrumFramePoint>,
    playbackPositionSeconds: Double?,
    modifier: Modifier = Modifier,
) {
    val latestTimestamp = frames.lastOrNull()?.timestampSeconds ?: 0.0
    val windowEnd = playbackPositionSeconds?.plus(SPECTRUM_WINDOW_SECONDS / 2.0)
        ?: latestTimestamp
    val windowStart = windowEnd - SPECTRUM_WINDOW_SECONDS
    val targetPosition = playbackPositionSeconds ?: latestTimestamp
    val animatedPosition by animateFloatAsState(
        targetValue = targetPosition.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 220f),
        label = "spectrum-playhead",
    )
    val image = remember(frames, windowStart, windowEnd) {
        createSpectrumBitmap(frames, windowStart, windowEnd)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    val surfaceColor = MaterialTheme.colorScheme.background
    val playheadColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val plotLeft = 44.dp
        val plotHeight = maxHeight

        fun yFraction(hz: Float): Float =
            (log10(hz / SPECTRUM_MIN_HZ) / log10(SPECTRUM_MAX_HZ / SPECTRUM_MIN_HZ))
                .toFloat()
                .coerceIn(0f, 1f)

        fun yDp(hz: Float) = plotHeight - plotHeight * yFraction(hz)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val plotLeftPx = plotLeft.toPx()
            val plotWidthPx = (size.width - plotLeftPx).coerceAtLeast(1f)
            fun yFor(hz: Float): Float = size.height - yFraction(hz) * size.height

            if (image != null) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(plotLeftPx.roundToInt(), 0),
                    dstSize = IntSize(plotWidthPx.roundToInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
            FREQUENCY_GRID_HZ.forEach { hz ->
                val y = yFor(hz)
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeftPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hz == 1000f) 1.6.dp.toPx() else 1.dp.toPx(),
                )
            }
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor,
                        surfaceColor.copy(alpha = 0.82f),
                        surfaceColor.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = 188.dp.toPx(),
                ),
                topLeft = Offset(plotLeftPx, 0f),
                size = Size(plotWidthPx, 188.dp.toPx()),
            )
            if (playbackPositionSeconds != null) {
                val x = plotLeftPx + (
                    (animatedPosition - windowStart) / SPECTRUM_WINDOW_SECONDS
                    ).toFloat().coerceIn(0f, 1f) * plotWidthPx
                drawLine(
                    color = playheadColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        FREQUENCY_GRID_HZ.forEach { hz ->
            Text(
                text = formatFrequencyAxis(hz),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = yDp(hz) - 8.dp)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun createSpectrumBitmap(
    frames: List<SpectrumFramePoint>,
    windowStart: Double,
    windowEnd: Double,
): ImageBitmap? {
    val visibleFrames = frames.filter {
        it.timestampSeconds in windowStart..windowEnd
    }
    if (visibleFrames.isEmpty()) return null
    val width = SPECTRUM_BITMAP_WIDTH
    val height = SPECTRUM_BITMAP_HEIGHT
    val pixels = IntArray(width * height)
    val minHz = (visibleFrames.first().firstBinIndex * visibleFrames.first().binHz)
        .coerceAtLeast(1f)
    val maxHz = (
        (visibleFrames.first().firstBinIndex + visibleFrames.first().magnitudes.lastIndex) *
            visibleFrames.first().binHz
        ).coerceAtLeast(minHz + 1f)
    val logRange = log10(maxHz / minHz)
    val frequencyBins = FloatArray(height) { y ->
        val frequencyRatio = 1f - y.toFloat() / (height - 1).coerceAtLeast(1)
        minHz * 10.0.pow(frequencyRatio.toDouble() * logRange).toFloat()
    }
    val firstTimestamp = visibleFrames.first().timestampSeconds
    val lastTimestamp = visibleFrames.last().timestampSeconds
    val frameSpan = (lastTimestamp - firstTimestamp).coerceAtLeast(0.001)
    var frameIndex = 0

    for (x in 0 until width) {
        val timestamp = windowStart + x.toDouble() / (width - 1) * SPECTRUM_WINDOW_SECONDS
        while (frameIndex < visibleFrames.lastIndex &&
            visibleFrames[frameIndex + 1].timestampSeconds <= timestamp
        ) {
            frameIndex++
        }
        val frame = visibleFrames[frameIndex]
        val hasFrame = timestamp >= firstTimestamp && timestamp <= lastTimestamp
        val magnitudeLastIndex = frame.magnitudes.lastIndex
        for (y in 0 until height) {
            val magnitudeIndex = (frequencyBins[y] / frame.binHz - frame.firstBinIndex)
                .roundToInt()
                .coerceIn(0, magnitudeLastIndex)
            val decibels = frame.magnitudes[magnitudeIndex]
            val normalized = ((decibels - SPECTRUM_MIN_DB) / SPECTRUM_DB_RANGE)
                .coerceIn(0f, 1f)
            pixels[y * width + x] = if (hasFrame) {
                spectrumColor(normalized).toArgb()
            } else {
                SpectrumLow.toArgb()
            }
        }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

private fun spectrumColor(value: Float): Color = when {
    value < 0.35f -> lerp(SpectrumLow, SpectrumBlue, value / 0.35f)
    value < 0.65f -> lerp(SpectrumBlue, SpectrumCyan, (value - 0.35f) / 0.3f)
    value < 0.85f -> lerp(SpectrumCyan, SpectrumPink, (value - 0.65f) / 0.2f)
    else -> lerp(SpectrumPink, SpectrumHigh, (value - 0.85f) / 0.15f)
}

private fun formatFrequencyAxis(hz: Float): String =
    if (hz >= 1000f) "%.0fk".format(hz / 1000f) else "%.0f".format(hz)

private val FREQUENCY_GRID_HZ = listOf(8000f, 4000f, 2000f, 1000f, 500f, 250f, 125f)
private val SpectrumLow = Color(0xFF111827)
private val SpectrumBlue = Color(0xFF2357D8)
private val SpectrumCyan = Color(0xFF19B9C8)
private val SpectrumPink = Color(0xFFE75480)
private val SpectrumHigh = Color(0xFFFFD9A0)
private const val SPECTRUM_WINDOW_SECONDS = 3.0
private const val SPECTRUM_BITMAP_WIDTH = 192
private const val SPECTRUM_BITMAP_HEIGHT = 72
private const val SPECTRUM_MIN_DB = -90f
private const val SPECTRUM_DB_RANGE = 75f
private const val SPECTRUM_MIN_HZ = 40f
private const val SPECTRUM_MAX_HZ = 8000f
