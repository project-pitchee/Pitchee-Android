package io.rovly.pitchee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.AnalysisHistoryEntry
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.RealtimeF0HistoryEntry
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

private data class DashboardData(
    val realtimeF0: List<RealtimeF0HistoryEntry> = emptyList(),
    val analyses: List<AnalysisHistoryEntry> = emptyList(),
)

@Composable
internal fun DashboardScreen(refreshKey: Any? = Unit) {
    val context = LocalContext.current
    val store = remember(context) { HistoryStore(context) }
    var data by remember { mutableStateOf(DashboardData()) }

    LaunchedEffect(refreshKey) {
        data = DashboardData(
            realtimeF0 = store.realtimeF0(),
            analyses = store.analyses(),
        )
    }

    val averageF0 = data.realtimeF0
        .takeIf { it.isNotEmpty() }
        ?.map { it.meanF0Hz }
        ?.average()
    val latestF0 = data.realtimeF0.firstOrNull()?.meanF0Hz

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_average_f0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = {
                            store.clearRealtimeF0()
                            data = data.copy(realtimeF0 = emptyList())
                        },
                        enabled = data.realtimeF0.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.dashboard_reset))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = averageF0?.let { "%.0f Hz".format(it) } ?: "--",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        data.realtimeF0.isEmpty() -> stringResource(R.string.dashboard_no_realtime)
                        latestF0 == null -> stringResource(R.string.dashboard_no_valid_f0)
                        else -> stringResource(R.string.dashboard_latest_f0, latestF0)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        AnalysisMetricSection(entries = data.analyses)
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.dashboard_analysis_history),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        if (data.analyses.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_no_analysis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            data.analyses.take(20).forEachIndexed { index, entry ->
                AnalysisHistoryRow(
                    entry = entry,
                    onDelete = {
                        store.removeAnalysis(entry.timestampMillis)
                        data = data.copy(
                            analyses = data.analyses.filterNot {
                                it.timestampMillis == entry.timestampMillis
                            },
                        )
                    },
                )
                if (index != data.analyses.take(20).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private enum class AnalysisMetric(
    val labelRes: Int,
) {
    F0(R.string.dashboard_metric_f0),
    NATURALNESS(R.string.dashboard_metric_naturalness),
    STANDARD(R.string.dashboard_metric_standard),
}

@Composable
private fun AnalysisMetricSection(entries: List<AnalysisHistoryEntry>) {
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(AnalysisMetric.F0.ordinal) }
    val selected = AnalysisMetric.entries[selectedOrdinal]
    val chronological = entries.take(30).asReversed()
    val points = chronological.mapNotNull { entry ->
        val value = when (selected) {
            AnalysisMetric.F0 -> entry.meanF0Hz?.toFloat()
            AnalysisMetric.NATURALNESS -> entry.naturalnessScore.toFloat()
            AnalysisMetric.STANDARD -> entry.standardScore.toFloat()
        } ?: return@mapNotNull null
        AnalysisChartPoint(
            timestampMillis = entry.timestampMillis,
            value = value,
        )
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_metric_chart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisMetric.entries.forEach { metric ->
                FilterChip(
                    selected = selected == metric,
                    onClick = { selectedOrdinal = metric.ordinal },
                    label = { Text(stringResource(metric.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_metric_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AnalysisMetricChart(
                points = points,
                metric = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            )
        }
    }
}

@Composable
private fun AnalysisMetricChart(
    points: List<AnalysisChartPoint>,
    metric: AnalysisMetric,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val selectedPointColor = MaterialTheme.colorScheme.primary
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val xFor: (Int) -> androidx.compose.ui.unit.Dp = { index ->
            if (points.size <= 1) {
                maxWidth / 2
            } else {
                maxWidth * index / (points.size - 1)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        selectedIndex = nearestPointIndex(
                            x = down.position.x,
                            width = size.width,
                            count = points.size,
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            selectedIndex = nearestPointIndex(
                                x = change.position.x,
                                width = size.width,
                                count = points.size,
                            )
                            if (!change.pressed) break
                        }
                        selectedIndex = null
                    }
                },
        ) {
            val values = points.map { it.value }
            val minimum = when (metric) {
                AnalysisMetric.F0 -> (values.minOrNull() ?: 0f) - 10f
                else -> 0f
            }
            val maximum = when (metric) {
                AnalysisMetric.F0 -> (values.maxOrNull() ?: 1f) + 10f
                else -> 100f
            }
            val range = max(maximum - minimum, 1f)
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)

            for (line in 0..2) {
                val y = size.height * line / 2f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun point(index: Int): Offset = Offset(
                x = stepX * index,
                y = size.height - ((points[index].value - minimum) / range) * size.height,
            )

            if (points.size == 1) {
                drawCircle(pointColor, radius = 4.dp.toPx(), center = point(0))
            } else {
                for (index in 0 until points.lastIndex) {
                    drawLine(
                        color = lineColor,
                        start = point(index),
                        end = point(index + 1),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                points.indices.forEach { index ->
                    drawCircle(pointColor, radius = 3.dp.toPx(), center = point(index))
                }
            }

            selectedIndex?.let { index ->
                val selectedPoint = point(index)
                drawLine(
                    color = crosshairColor,
                    start = Offset(selectedPoint.x, 0f),
                    end = Offset(selectedPoint.x, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                    ),
                )
                drawCircle(
                    color = selectedPointColor,
                    radius = 4.5.dp.toPx(),
                    center = selectedPoint,
                )
            }
        }

        selectedIndex?.let { index ->
            val point = points[index]
            val date = remember(point.timestampMillis, locale) {
                SimpleDateFormat("M/d", locale).format(Date(point.timestampMillis))
            }
            val value = when (metric) {
                AnalysisMetric.F0 -> "%.0f Hz".format(point.value)
                else -> "%.1f".format(point.value)
            }
            val tooltipWidth = 62.dp
            val tooltipX = (xFor(index) - tooltipWidth / 2)
                .coerceIn(0.dp, maxWidth - tooltipWidth)
            Surface(
                modifier = Modifier
                    .offset(x = tooltipX, y = 0.dp)
                    .width(tooltipWidth),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private data class AnalysisChartPoint(
    val timestampMillis: Long,
    val value: Float,
)

private fun nearestPointIndex(
    x: Float,
    width: Int,
    count: Int,
): Int {
    if (count <= 1 || width <= 0) return 0
    return ((x / width) * (count - 1))
        .roundToInt()
        .coerceIn(0, count - 1)
}


@Composable
private fun AnalysisHistoryRow(
    entry: AnalysisHistoryEntry,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val timestamp = remember(entry.timestampMillis, locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            locale,
        ).format(Date(entry.timestampMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "%s %.1f   %s %.1f   %s".format(
                    "S",
                    entry.standardScore,
                    "N",
                    entry.naturalnessScore,
                    entry.meanF0Hz?.let { "F0 %.0f".format(it) } ?: "F0 --",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "%.1f".format(entry.finalScore),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = feminineScoreColor(entry.finalScore),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.dashboard_delete_record),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
