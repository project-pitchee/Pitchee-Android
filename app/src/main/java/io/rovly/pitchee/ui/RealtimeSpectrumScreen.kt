package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

    fun toggleMonitoring() {
        if (state.running || state.preparing) {
            viewModel.stop()
            return
        }
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
    }

    val currentPeakHz = state.current
        ?.takeIf { it.peakDb >= PEAK_DISPLAY_THRESHOLD_DB }
        ?.peakHz

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SpectrumChart(
            frames = state.frames,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            Spacer(Modifier.height(16.dp))
            Text(
                text = currentPeakHz?.let(::formatHz) ?: "--",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = if (currentPeakHz == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = stringResource(R.string.spectrum_peak),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.frames.isEmpty()) {
            Text(
                text = if (state.preparing) {
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
                    text = error.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.spectrum_start_failed),
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
            FilledIconButton(
                onClick = ::toggleMonitoring,
                modifier = Modifier.size(72.dp),
            ) {
                if (state.preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            if (state.running) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = stringResource(
                            if (state.running) {
                                R.string.spectrum_stop_monitoring
                            } else {
                                R.string.spectrum_start_monitoring
                            },
                        ),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumChart(
    frames: List<SpectrumFramePoint>,
    modifier: Modifier = Modifier,
) {
    val image = remember(frames) { createSpectrumBitmap(frames) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val peakColor = Color(0xFFFF7FA8)
    val surfaceColor = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val plotTop = 196.dp
        val plotBottom = maxHeight - 116.dp
        val plotLeft = 44.dp
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1.dp)

        fun yFraction(hz: Float): Float =
            (log10(hz / SPECTRUM_MIN_HZ) / log10(SPECTRUM_MAX_HZ / SPECTRUM_MIN_HZ))
                .toFloat()
                .coerceIn(0f, 1f)

        fun yDp(hz: Float) = plotBottom - plotHeight * yFraction(hz)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val plotTopPx = plotTop.toPx()
            val plotBottomPx = plotBottom.toPx()
            val plotLeftPx = plotLeft.toPx()
            val plotHeightPx = (plotBottomPx - plotTopPx).coerceAtLeast(1f)
            fun yFor(hz: Float): Float = plotBottomPx - yFraction(hz) * plotHeightPx

            drawRect(
                color = surfaceColor,
                topLeft = Offset(plotLeftPx, plotTopPx),
                size = Size(size.width - plotLeftPx, plotHeightPx),
            )

            if (image != null && frames.isNotEmpty()) {
                val latestTimestamp = frames.last().timestampSeconds
                val firstTimestamp = latestTimestamp - SPECTRUM_WINDOW_SECONDS
                val firstFrameFraction = (
                    (frames.first().timestampSeconds - firstTimestamp) /
                        SPECTRUM_WINDOW_SECONDS
                    ).toFloat().coerceIn(0f, 1f)
                val lastFrameFraction = (
                    (frames.last().timestampSeconds - firstTimestamp) /
                        SPECTRUM_WINDOW_SECONDS
                    ).toFloat().coerceIn(0f, 1f)
                val destinationLeft = plotLeftPx + firstFrameFraction * (size.width - plotLeftPx)
                val destinationRight = plotLeftPx + lastFrameFraction * (size.width - plotLeftPx)
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(
                        destinationLeft.roundToInt(),
                        plotTopPx.roundToInt(),
                    ),
                    dstSize = IntSize(
                        (destinationRight - destinationLeft).roundToInt().coerceAtLeast(1),
                        plotHeightPx.roundToInt(),
                    ),
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

            frames.lastOrNull()
                ?.takeIf { it.peakDb >= PEAK_DISPLAY_THRESHOLD_DB }
                ?.let { frame ->
                val destinationRight = plotLeftPx + (size.width - plotLeftPx)
                val peakY = yFor(frame.peakHz.coerceIn(SPECTRUM_MIN_HZ, SPECTRUM_MAX_HZ))
                drawLine(
                    color = peakColor,
                    start = Offset(plotLeftPx, peakY),
                    end = Offset(destinationRight, peakY),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(
                    color = peakColor,
                    radius = 5.dp.toPx(),
                    center = Offset(destinationRight, peakY),
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

private fun createSpectrumBitmap(frames: List<SpectrumFramePoint>): ImageBitmap? {
    if (frames.isEmpty()) return null
    val width = SPECTRUM_BITMAP_WIDTH
    val height = SPECTRUM_BITMAP_HEIGHT
    val pixels = IntArray(width * height)
    val minHz = (frames.first().firstBinIndex * frames.first().binHz).coerceAtLeast(1f)
    val maxHz = (
        (frames.first().firstBinIndex + frames.first().magnitudes.lastIndex) *
            frames.first().binHz
        ).coerceAtLeast(minHz + 1f)
    val logRange = log10(maxHz / minHz)
    val firstTimestamp = frames.first().timestampSeconds
    val latestTimestamp = frames.last().timestampSeconds
    val visibleStart = latestTimestamp - SPECTRUM_WINDOW_SECONDS
    val frameSpan = (latestTimestamp - firstTimestamp).coerceAtLeast(0.001)
    val frequencyBins = FloatArray(height) { y ->
        val frequencyRatio = 1f - y.toFloat() / (height - 1).coerceAtLeast(1)
        minHz * 10.0.pow(frequencyRatio.toDouble() * logRange).toFloat()
    }

    for (x in 0 until width) {
        val timestamp = visibleStart + x.toDouble() / (width - 1) * SPECTRUM_WINDOW_SECONDS
        val frameIndex = ((timestamp - firstTimestamp) / frameSpan * frames.lastIndex)
            .roundToInt()
            .coerceIn(0, frames.lastIndex)
        val frame = frames[frameIndex]
        val hasFrame = timestamp >= firstTimestamp && timestamp <= latestTimestamp
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

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "%.0f Hz".format(hz)

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
private const val PEAK_DISPLAY_THRESHOLD_DB = -65f
