package io.rovly.pitchee.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rovly.pitchee.data.PitcheeResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.delay

@Composable
internal fun ScoreResultContent(
    result: PitcheeResult,
    previousScore: Double? = null,
    onOpenRules: () -> Unit = {},
    audioPlayer: @Composable () -> Unit = {},
) {
    val insight = remember(result) { ScoreInsight.from(result) }
    val score = result.composite.finalScore.coerceIn(0.0, 100.0)
    AnimatedScoreIndexChart(
        targetScore = score,
        previousScore = previousScore,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    audioPlayer()
    Spacer(Modifier.height(12.dp))
    if (insight.hasBottleneck) {
        BottleneckCard(insight, onOpenRules)
        Spacer(Modifier.height(12.dp))
    } else if (score > 60.0) {
        PassCard(onOpenRules)
        Spacer(Modifier.height(12.dp))
    }
    MetricsCard(result, insight)
}

@Composable
internal fun ScoreIndexChart(
    score: Double,
    modifier: Modifier = Modifier,
) {
    val feminineVisual = score.coerceIn(0.0, 112.0)
    val masculineVisual = (100.0 - score).coerceIn(0.0, 112.0)
    val feminineScore = feminineVisual.coerceAtMost(100.0)
    val masculineScore = masculineVisual.coerceAtMost(100.0)
    val feminineSize = (76f + feminineVisual.toFloat() * 1.76f).dp
    val masculineSize = (76f + masculineVisual.toFloat() * 1.76f).dp
    val feminineTravel = cornerTravel(feminineScore).value
    val masculineTravel = cornerTravel(masculineScore).value
    val feminineCenterX = feminineTravel
    val feminineCenterY = -feminineTravel * 0.82f
    val masculineCenterX = -masculineTravel
    val masculineCenterY = masculineTravel * 0.82f
    val groupMinX = minOf(
        feminineCenterX - feminineSize.value / 2f,
        masculineCenterX - masculineSize.value / 2f,
    )
    val groupMaxX = maxOf(
        feminineCenterX + feminineSize.value / 2f,
        masculineCenterX + masculineSize.value / 2f,
    )
    val groupMinY = minOf(
        feminineCenterY - feminineSize.value / 2f,
        masculineCenterY - masculineSize.value / 2f,
    )
    val groupMaxY = maxOf(
        feminineCenterY + feminineSize.value / 2f,
        masculineCenterY + masculineSize.value / 2f,
    )
    val groupOffsetX = -(groupMinX + groupMaxX) / 2f
    val groupOffsetY = -(groupMinY + groupMaxY) / 2f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(304.dp),
    ) {
        ScoreIndexCircle(
            score = masculineScore,
            color = Color(0xFF6495ED),
            contentColor = Color.White,
            size = masculineSize,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = (masculineCenterX + groupOffsetX).dp,
                    y = (masculineCenterY + groupOffsetY).dp,
                ),
        )
        ScoreIndexCircle(
            score = feminineScore,
            color = Color(0xFFFFB6C1),
            contentColor = Color(0xFF3A1F2A),
            size = feminineSize,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = (feminineCenterX + groupOffsetX).dp,
                    y = (feminineCenterY + groupOffsetY).dp,
                ),
        )
    }
}

@Composable
private fun PassCard(onOpenRules: () -> Unit) {
    InsightCard(
        label = "评估结果",
        title = "你的声音很pass",
        description = "本次没有触发主要短板规则。",
        containerColor = Color(0xFFDDF6E4),
        contentColor = Color(0xFF116B3A),
        onOpenRules = onOpenRules,
    )
}

@Composable
private fun InsightCard(
    label: String,
    title: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    onOpenRules: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            ResultActionButton(
                text = "查看评分细则",
                onClick = onOpenRules,
                containerColor = if (isSystemInDarkTheme()) {
                    Color(0xFF2E7D52)
                } else {
                    Color(0xFF1F6F43)
                },
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun ResultActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun AnimatedScoreIndexChart(
    targetScore: Double,
    previousScore: Double?,
    modifier: Modifier = Modifier,
) {
    val target = targetScore.coerceIn(0.0, 100.0).toFloat()
    val start = (previousScore ?: 0.0).coerceIn(0.0, 100.0).toFloat()
    val animatedScore = remember { Animatable(start) }

    LaunchedEffect(target, start) {
        animatedScore.stop()
        animatedScore.snapTo(start)
        if (abs(target - start) > 0.01f) {
            delay(500)
            animatedScore.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.45f,
                    stiffness = 170f,
                    visibilityThreshold = 0.05f,
                ),
            )
        }
    }

    ScoreIndexChart(
        score = animatedScore.value.toDouble(),
        modifier = modifier,
    )
}

private fun cornerTravel(score: Double): Dp {
    val lowScoreFactor = (1.0 - score.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return (22f + lowScoreFactor * 42f).dp
}

@Composable
private fun ScoreIndexCircle(
    score: Double,
    color: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "%.1f%%".format(score),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = TextUnit.Unspecified,
                    lineHeight = TextUnit.Unspecified,
                ),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = scoreFontCap(score),
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

private fun scoreFontCap(score: Double): TextUnit {
    val progress = (score.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return (34f + 18f * sqrt(progress)).sp
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
                text = "评分规则",
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
internal fun ScoreRuleCard(result: PitcheeResult) {
    val insight = remember(result) { ScoreInsight.from(result) }
    RuleCard(result, insight)
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
private fun BottleneckCard(
    insight: ScoreInsight,
    onOpenRules: () -> Unit,
) {
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

    InsightCard(
        label = "主要短板",
        title = insight.bottleneckTitle,
        description = insight.bottleneckDescription,
        containerColor = containerColor,
        contentColor = contentColor,
        onOpenRules = onOpenRules,
    )
}

@Composable
private fun MetricsCard(result: PitcheeResult, insight: ScoreInsight) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val darkTheme = isSystemInDarkTheme()
    val cardColor = if (darkTheme) Color(0xFF3A3114) else Color(0xFFFFF4CC)
    val toggleColor = if (darkTheme) Color(0xFFFFB74D) else Color(0xFFF59E0B)
    val toggleContentColor = Color(0xFF3A2100)
    val standard = result.vfp.standardScore
    val naturalness = result.naturalness.score
    val f0 = result.f0.meanHz
    val f0Score = f0?.let { min(100.0, max(0.0, (it - 110.0) / 90.0 * 100.0)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "显示音色/F0/VFP指标",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ResultActionButton(
                    text = if (expanded) "收起" else "展开",
                    onClick = { expanded = !expanded },
                    containerColor = toggleColor,
                    contentColor = toggleContentColor,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            text = "最低",
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
