package io.rovly.pitchee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.AnalysisHistoryEntry
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.RealtimeF0HistoryEntry
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

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
                Text(
                    text = stringResource(R.string.dashboard_average_f0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
                if (data.realtimeF0.size > 1) {
                    Spacer(Modifier.height(18.dp))
                    RealtimeF0Sparkline(
                        values = data.realtimeF0
                            .take(24)
                            .map { it.meanF0Hz.toFloat() }
                            .asReversed(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp),
                    )
                }
            }
        }
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
                AnalysisHistoryRow(entry)
                if (index != data.analyses.take(20).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RealtimeF0Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val minimum = values.minOrNull() ?: return@Canvas
        val maximum = values.maxOrNull() ?: return@Canvas
        val range = max(maximum - minimum, 1f)
        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        fun point(index: Int): Offset {
            val fraction = (values[index] - minimum) / range
            return Offset(
                x = stepX * index,
                y = size.height - fraction * size.height,
            )
        }
        if (values.size == 1) {
            drawCircle(lineColor, radius = 3.dp.toPx(), center = point(0))
            return@Canvas
        }
        for (index in 0 until values.lastIndex) {
            drawLine(
                color = lineColor,
                start = point(index),
                end = point(index + 1),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AnalysisHistoryRow(entry: AnalysisHistoryEntry) {
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
    }
}
