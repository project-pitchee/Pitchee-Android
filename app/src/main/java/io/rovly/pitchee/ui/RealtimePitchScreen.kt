package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R

@Composable
internal fun RealtimePitchScreen() {
    val context = LocalContext.current
    val factory = remember { PitchViewModel.factory(context.applicationContext) }
    val viewModel: PitchViewModel = viewModel(factory = factory)
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

    Box(modifier = Modifier.fillMaxSize()) {
        PitchChart(
            points = state.points,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.pitch_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pitch_subtitle),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            val currentHz = state.currentHz
            Text(
                text = currentHz?.let { "%.0f Hz".format(it) } ?: "--",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = if (currentHz == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (currentHz == null) {
                    stringResource(R.string.pitch_no_voice)
                } else {
                    stringResource(R.string.pitch_current_f0)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.record_permission_denied),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.preparing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pitch_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            } else if (!state.running) {
                Text(
                    text = stringResource(R.string.pitch_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = ::toggleMonitoring) {
                Text(
                    text = stringResource(
                        if (state.running || state.preparing) {
                            R.string.pitch_stop_monitoring
                        } else {
                            R.string.pitch_start_monitoring
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PitchChart(
    points: List<F0Point>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val feminineColor = Color(0xFFFFB6C1)
    val masculineColor = Color(0xFF6495ED)

    Canvas(modifier = modifier.fillMaxSize()) {
        val latestTimestamp = points.lastOrNull()?.timestampSeconds ?: 0.0
        val firstTimestamp = latestTimestamp - WINDOW_SECONDS
        val plotTop = 164.dp.toPx()
        val plotBottom = size.height - 132.dp.toPx()
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        fun yFor(hz: Float): Float {
            val normalized = ((hz - MIN_HZ) / (MAX_HZ - MIN_HZ)).coerceIn(0f, 1f)
            return plotBottom - normalized * plotHeight
        }

        val thresholdY = yFor(165f)
        drawRect(
            color = feminineColor.copy(alpha = 0.20f),
            topLeft = Offset.Zero,
            size = Size(size.width, thresholdY),
        )
        drawRect(
            color = masculineColor.copy(alpha = 0.20f),
            topLeft = Offset(0f, thresholdY),
            size = Size(size.width, size.height - thresholdY),
        )

        listOf(100f, 165f, 230f, 350f).forEach { hz ->
            val y = yFor(hz)
            drawLine(
                color = gridColor.copy(alpha = if (hz == 165f) 0.72f else 0.34f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (hz == 165f) 2.5.dp.toPx() else 1.dp.toPx(),
            )
        }

        val voiced = points.filter {
            it.f0Hz != null && it.timestampSeconds >= firstTimestamp
        }
        val segments = mutableListOf<List<Offset>>()
        var currentSegment = mutableListOf<Offset>()
        var previousTime = Double.NEGATIVE_INFINITY
        voiced.forEach { point ->
            val hz = point.f0Hz ?: return@forEach
            val x = ((point.timestampSeconds - firstTimestamp) / WINDOW_SECONDS)
                .toFloat()
                .coerceIn(0f, 1f) * size.width
            if (currentSegment.isNotEmpty() &&
                point.timestampSeconds - previousTime > MAX_GAP_SECONDS
            ) {
                segments += currentSegment.toList()
                currentSegment = mutableListOf()
            }
            currentSegment += Offset(x, yFor(hz))
            previousTime = point.timestampSeconds
        }
        if (currentSegment.isNotEmpty()) segments += currentSegment.toList()

        segments.forEach { segment ->
            if (segment.size == 1) {
                drawCircle(lineColor, radius = 2.2.dp.toPx(), center = segment.first())
                return@forEach
            }
            val path = Path().apply {
                moveTo(segment.first().x, segment.first().y)
                for (index in 0 until segment.lastIndex) {
                    val current = segment[index]
                    val next = segment[index + 1]
                    quadraticTo(
                        current.x,
                        current.y,
                        (current.x + next.x) / 2f,
                        (current.y + next.y) / 2f,
                    )
                }
                lineTo(segment.last().x, segment.last().y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            segment.forEach { point ->
                drawCircle(lineColor, radius = 1.6.dp.toPx(), center = point)
            }
        }
    }
}

private const val MIN_HZ = 60f
private const val MAX_HZ = 400f
private const val WINDOW_SECONDS = 3.0
private const val MAX_GAP_SECONDS = 0.25
