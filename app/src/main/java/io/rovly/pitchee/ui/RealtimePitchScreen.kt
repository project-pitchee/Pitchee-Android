package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import kotlin.math.max

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.pitch_title),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pitch_subtitle),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val currentHz = state.currentHz
            Text(
                text = currentHz?.let { "%.0f".format(it) } ?: "--",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (currentHz == null) {
                    stringResource(R.string.pitch_no_voice)
                } else {
                    "Hz"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PitchChart(
                points = state.points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp),
            )
            if (state.preparing) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pitch_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!state.running) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.pitch_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (permissionDenied) {
            Text(
                text = stringResource(R.string.record_permission_denied),
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
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

@Composable
private fun PitchChart(
    points: List<F0Point>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val threshold = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(42.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("400", style = MaterialTheme.typography.labelSmall, color = onSurface)
            Text("230", style = MaterialTheme.typography.labelSmall, color = onSurface)
            Text("165", style = MaterialTheme.typography.labelSmall, color = threshold)
            Text("100", style = MaterialTheme.typography.labelSmall, color = onSurface)
            Text("60", style = MaterialTheme.typography.labelSmall, color = onSurface)
        }
        Spacer(Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            val chartPoints = points
            val latestTimestamp = chartPoints.lastOrNull()?.timestampSeconds ?: 0.0
            val firstTimestamp = max(0.0, latestTimestamp - WINDOW_SECONDS)
            val duration = (latestTimestamp - firstTimestamp).coerceAtLeast(0.01)

            fun yFor(hz: Float): Float {
                val normalized = ((hz - MIN_HZ) / (MAX_HZ - MIN_HZ)).coerceIn(0f, 1f)
                return size.height * (1f - normalized)
            }

            listOf(60f, 100f, 165f, 230f, 400f).forEach { hz ->
                val y = yFor(hz)
                drawLine(
                    color = if (hz == 165f) threshold.copy(alpha = 0.38f) else grid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hz == 165f) 2.dp.toPx() else 1.dp.toPx(),
                )
            }

            var previous: Offset? = null
            var previousTimestamp = 0.0
            chartPoints.forEach { point ->
                val hz = point.f0Hz
                if (hz == null || point.timestampSeconds < firstTimestamp) {
                    previous = null
                } else {
                    val x = ((point.timestampSeconds - firstTimestamp) / duration)
                        .toFloat()
                        .coerceIn(0f, 1f) * size.width
                    val current = Offset(x, yFor(hz))
                    val continuous = point.timestampSeconds - previousTimestamp < MAX_GAP_SECONDS
                    previous?.let { start ->
                        if (continuous) {
                            drawLine(
                                color = accent,
                                start = start,
                                end = current,
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    drawCircle(color = accent, radius = 2.5.dp.toPx(), center = current)
                    previous = current
                    previousTimestamp = point.timestampSeconds
                }
            }
        }
    }
}

private const val MIN_HZ = 60f
private const val MAX_HZ = 400f
private const val WINDOW_SECONDS = 6.0
private const val MAX_GAP_SECONDS = 0.12
