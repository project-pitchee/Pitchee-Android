package io.rovly.pitchee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.data.PitcheeResult
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun ScoreResultContent(
    result: PitcheeResult,
    timeline: @Composable () -> Unit = {},
) {
    val insight = remember(result) { ScoreInsight.from(result) }
    ScoreHero(result, insight)
    Spacer(Modifier.height(16.dp))
    timeline()
    Spacer(Modifier.height(12.dp))
    RuleCard(result, insight)
    Spacer(Modifier.height(12.dp))
    BottleneckCard(insight)
    Spacer(Modifier.height(12.dp))
    MetricsCard(result, insight)
}

@Composable
private fun ScoreHero(result: PitcheeResult, insight: ScoreInsight) {
    val score = result.composite.finalScore
    val scoreColor = feminineScoreColor(score)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = scoreColor.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "女性化综合分",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 14.dp.toPx()
                    drawArc(
                        color = scoreColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = scoreColor,
                        startAngle = -90f,
                        sweepAngle = (score / 100.0 * 360.0).toFloat(),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(score),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = scoreColor,
                    )
                    Text(
                        text = "/ 100",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = insight.headline,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RuleCard(result: PitcheeResult, insight: ScoreInsight) {
    val containerColor = when (insight.ruleState) {
        ScoreRuleState.CAPPED -> MaterialTheme.colorScheme.errorContainer
        ScoreRuleState.BOOSTED -> MaterialTheme.colorScheme.primaryContainer
        ScoreRuleState.F0_UNAVAILABLE -> MaterialTheme.colorScheme.tertiaryContainer
        ScoreRuleState.CONTINUOUS -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (insight.ruleState) {
        ScoreRuleState.CAPPED -> MaterialTheme.colorScheme.onErrorContainer
        ScoreRuleState.BOOSTED -> MaterialTheme.colorScheme.onPrimaryContainer
        ScoreRuleState.F0_UNAVAILABLE -> MaterialTheme.colorScheme.onTertiaryContainer
        ScoreRuleState.CONTINUOUS -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "本次命中的规则",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = insight.ruleName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = insight.ruleDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = contentColor.copy(alpha = 0.10f),
                contentColor = contentColor,
            ) {
                Text(
                    text = insight.ruleImpact,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = contentColor.copy(alpha = 0.16f),
            )
            Text(
                text = "综合分公式",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "50% 标准音色 + 20% 自然度 + 15% 基频 + 15% 协同",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Spacer(Modifier.height(14.dp))
            ScorePath(
                baseScore = result.composite.baseScore,
                cap = result.composite.cap,
                finalScore = result.composite.finalScore,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun ScorePath(
    baseScore: Double,
    cap: Double?,
    finalScore: Double,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreStage("基础连续分", "%.1f".format(baseScore), contentColor)
        Text("→", color = contentColor.copy(alpha = 0.55f))
        ScoreStage(
            label = "规则限制",
            value = cap?.let { "≤ %.0f".format(it) } ?: "无",
            color = contentColor,
        )
        Text("→", color = contentColor.copy(alpha = 0.55f))
        ScoreStage("最终得分", "%.1f".format(finalScore), contentColor, emphasized = true)
    }
}

@Composable
private fun ScoreStage(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    emphasized: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.68f),
        )
        Text(
            text = value,
            style = if (emphasized) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun BottleneckCard(insight: ScoreInsight) {
    val containerColor = if (insight.ruleState == ScoreRuleState.CAPPED) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (insight.ruleState == ScoreRuleState.CAPPED) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "最需要改善",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = insight.bottleneckTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = insight.bottleneckDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun MetricsCard(result: PitcheeResult, insight: ScoreInsight) {
    val standard = result.vfp.standardScore
    val naturalness = result.naturalness.score
    val f0 = result.f0.meanHz
    val f0Score = f0?.let { min(100.0, max(0.0, (it - 110.0) / 90.0 * 100.0)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "组成指标",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            MetricRow(
                label = "标准音色",
                value = "%.1f".format(standard),
                progress = (standard / 100.0).toFloat(),
                highlighted = insight.bottleneckTitle.contains("音色"),
                score = standard,
            )
            Spacer(Modifier.height(14.dp))
            MetricRow(
                label = "自然度",
                value = "%.1f".format(naturalness),
                progress = (naturalness / 100.0).toFloat(),
                highlighted = insight.bottleneckTitle.contains("自然度"),
                score = naturalness,
            )
            Spacer(Modifier.height(14.dp))
            MetricRow(
                label = "平均 F0",
                value = f0?.let { "%.0f Hz".format(it) } ?: "未检测到",
                progress = f0Score?.div(100.0)?.toFloat() ?: 0f,
                highlighted = insight.bottleneckTitle.contains("F0") ||
                    insight.bottleneckTitle.contains("基频"),
                score = f0Score ?: 0.0,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "F0 进度条按 110–200 Hz 映射；最终规则以 165 Hz 为关键分界。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    progress: Float,
    highlighted: Boolean,
    score: Double,
) {
    val color = feminineScoreColor(score)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(9.dp)) { drawCircle(color) }
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (highlighted) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.18f),
                        contentColor = color,
                    ) {
                        Text(
                            text = "重点",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
        )
    }
}
