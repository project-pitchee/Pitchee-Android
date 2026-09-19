package io.rovly.pitchee.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R

@Composable
internal fun PitchSessionSummaryScreen(
    summary: PitchSessionSummary,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val masculineColor = Color(0xFF6495ED)
    val feminineColor = Color(0xFFFFB6C1)
    val emptyRatioColor = MaterialTheme.colorScheme.outlineVariant
    SwipeBackContainer(enabled = true, onBack = onBack) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
            Text(
                text = stringResource(R.string.pitch_summary_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.pitch_summary_average),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary.meanF0Hz?.let { "%.0f Hz".format(it) } ?: "--",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            R.string.pitch_summary_samples,
                            summary.sampleCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.pitch_summary_ratio),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pitch_summary_ratio_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (summary.sampleCount == 0) {
                    drawRect(emptyRatioColor)
                } else {
                    val masculineWidth = size.width * summary.maleRatio
                    drawRect(
                        color = masculineColor,
                        size = Size(masculineWidth, size.height),
                    )
                    drawRect(
                        color = feminineColor,
                        topLeft = Offset(masculineWidth, 0f),
                        size = Size(size.width - masculineWidth, size.height),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            RatioRow(
                label = stringResource(R.string.pitch_summary_male),
                ratio = summary.maleRatio,
                color = masculineColor,
            )
            Spacer(Modifier.height(8.dp))
            RatioRow(
                label = stringResource(R.string.pitch_summary_female),
                ratio = summary.femaleRatio,
                color = feminineColor,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pitch_summary_back))
            }
            }
        }
    }
}

@Composable
private fun RatioRow(
    label: String,
    ratio: Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.height(12.dp).fillMaxWidth(0.03f),
                shape = RoundedCornerShape(6.dp),
                color = color,
            ) {}
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = "%.0f%%".format(ratio.coerceIn(0f, 1f) * 100f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
