package io.rovly.pitchee.ui

import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.rovly.pitchee.R
import io.rovly.pitchee.data.PitcheeResult
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ScoreResultContent(
    result: PitcheeResult,
    previousScore: Double? = null,
    previousMetrics: PreviousMetrics? = null,
    animateScore: Boolean = true,
    onOpenRules: () -> Unit = {},
    audioPlayer: @Composable () -> Unit = {},
) {
    val insight = remember(result) { ScoreInsight.from(result) }
    val score = result.composite.finalScore.coerceIn(0.0, 100.0)
    AnimatedScoreIndexChart(
        targetScore = score,
        previousScore = previousScore,
        animate = animateScore,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    audioPlayer()
    Spacer(Modifier.height(12.dp))
    if (insight.hasBottleneck) {
        BottleneckCard(insight, onOpenRules)
        Spacer(Modifier.height(12.dp))
    } else if (score > 60.0) {
        PassCard(onOpenRules)
        Spacer(Modifier.height(12.dp))
    }
    MetricsCard(
        result = result,
        insight = insight,
        previousMetrics = previousMetrics,
        animate = animateScore,
    )
}

@Composable
internal fun ScoreIndexChart(
    score: Double,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(304.dp),
    ) {
        val feminineVisual = score.coerceIn(0.0, 112.0)
        val masculineVisual = (100.0 - score).coerceIn(0.0, 112.0)
        val feminineScore = feminineVisual.coerceAtMost(100.0)
        val masculineScore = masculineVisual.coerceAtMost(100.0)
        val rawFeminineSize = 76f + feminineVisual.toFloat() * 1.76f
        val rawMasculineSize = 76f + masculineVisual.toFloat() * 1.76f
        val rawFeminineTravel = cornerTravel(feminineScore).value
        val rawMasculineTravel = cornerTravel(masculineScore).value
        val rawFeminineCenterY = -rawFeminineTravel * 0.82f
        val rawMasculineCenterY = rawMasculineTravel * 0.82f
        val rawGroupWidth = maxOf(
            rawFeminineTravel + rawFeminineSize / 2f,
            -rawMasculineTravel + rawMasculineSize / 2f,
        ) - minOf(
            rawFeminineTravel - rawFeminineSize / 2f,
            -rawMasculineTravel - rawMasculineSize / 2f,
        )
        val rawGroupHeight = maxOf(
            rawFeminineCenterY + rawFeminineSize / 2f,
            rawMasculineCenterY + rawMasculineSize / 2f,
        ) - minOf(
            rawFeminineCenterY - rawFeminineSize / 2f,
            rawMasculineCenterY - rawMasculineSize / 2f,
        )
        val targetGroupWidth = (maxWidth.value - 40f).coerceAtLeast(0f)
        val horizontalScale = (targetGroupWidth / rawGroupWidth).coerceAtLeast(1f)
        val verticalScale = (288f / rawGroupHeight).coerceAtLeast(1f)
        val groupScale = minOf(horizontalScale, verticalScale)
        val feminineSize = (rawFeminineSize * groupScale).dp
        val masculineSize = (rawMasculineSize * groupScale).dp
        val feminineTravel = rawFeminineTravel * groupScale
        val masculineTravel = rawMasculineTravel * groupScale
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

        Box(Modifier.fillMaxSize()) {
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
}

@Composable
private fun PassCard(onOpenRules: () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    InsightCard(
        label = "评估结果",
        title = "你的声音很pass",
        description = "本次没有触发主要短板规则。",
        containerColor = if (darkTheme) Color(0xFF123D29) else Color(0xFFDDF6E4),
        contentColor = if (darkTheme) Color(0xFFA8E6C0) else Color(0xFF116B3A),
        actionContainerColor = if (darkTheme) Color(0xFF3F9D6B) else Color(0xFF1F6F43),
        actionContentColor = Color.White,
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
    actionContainerColor: Color,
    actionContentColor: Color,
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
                containerColor = actionContainerColor,
                contentColor = actionContentColor,
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
    animate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val target = targetScore.coerceIn(0.0, 100.0).toFloat()
    val start = if (animate) {
        (previousScore ?: 0.0).coerceIn(0.0, 100.0).toFloat()
    } else {
        target
    }
    val animatedScore = remember { Animatable(start) }

    LaunchedEffect(target, start, animate) {
        animatedScore.stop()
        animatedScore.snapTo(start)
        if (animate && abs(target - start) > 0.01f) {
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
internal fun ScoreRulesContent(
    result: PitcheeResult,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
) {
    val insight = remember(result) { ScoreInsight.from(result) }
    val currentRule = result.composite.rule
    var otherRulesExpanded by rememberSaveable(currentRule) { mutableStateOf(false) }
    val currentRuleDoc = scoringRuleDocs.firstOrNull { it.key == currentRule }
    val otherRuleDocs = scoringRuleDocs.filterNot { it.key == currentRule }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "本次综合分",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f".format(result.composite.finalScore),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            ScoreDelta(
                previousScore = previousScore,
                currentScore = result.composite.finalScore,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "本次指标",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        scoreIndicators(result, previousMetrics).forEach { indicator ->
            IndicatorRow(indicator)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(10.dp))
        CommonFormulaSection()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "本次命中规则",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = insight.ruleDescription,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        currentRuleDoc?.let { rule ->
            RuleDetails(
                rule = rule,
                isCurrent = true,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { otherRulesExpanded = !otherRulesExpanded }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "未触发的其他规则",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${otherRuleDocs.size} 条规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (otherRulesExpanded) "−" else "+",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(
            visible = otherRulesExpanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
            ),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
            ),
        ) {
            Column {
                otherRuleDocs.forEachIndexed { index, rule ->
                    RuleDetails(rule = rule, isCurrent = false)
                    if (index != otherRuleDocs.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreDelta(
    previousScore: Double?,
    currentScore: Double,
) {
    val delta = previousScore?.let { currentScore - it }
    val (arrow, color) = when {
        delta == null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0.05 -> "↑" to Color(0xFF257A45)
        delta < -0.05 -> "↓" to MaterialTheme.colorScheme.error
        else -> "→" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "较上次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = delta?.let { "$arrow ${"%.1f".format(abs(it))}" } ?: "首次测评",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun CommonFormulaSection() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "基础公式",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "所有规则以此基础计算",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FormulaView(commonScoreFormulas.map(::latexBlock))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Standard 代表标准音色分，范围 0 到 100。\n" +
                "Naturalness 代表自然度分，范围 0 到 100。\n" +
                "F0 代表平均基频，单位是 Hz。\n" +
                "三个 _r 变量是归一化分数，范围 0 到 1。\n" +
                "Base 是封顶或提升之前的综合基础分。\n" +
                "Final 是最终显示的综合分。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
        )
    }
}

@Composable
private fun IndicatorRow(indicator: ScoreIndicator) {
    val passContainer = if (isSystemInDarkTheme()) {
        Color(0xFF23563A)
    } else {
        Color(0xFFDCEFDF)
    }
    val passContent = if (isSystemInDarkTheme()) {
        Color(0xFF9FE0B5)
    } else {
        Color(0xFF155F35)
    }
    val containerColor = if (indicator.passed) {
        passContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (indicator.passed) {
        passContent
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (indicator.passed) R.drawable.ic_check else R.drawable.ic_close,
                    ),
                    contentDescription = indicator.tier,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = indicator.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = indicator.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        val delta = indicator.comparison?.let { it.current - it.previous }
        val comparisonText = when {
            delta == null -> "较上次 —"
            delta > 0.05 -> "较上次 ↑ ${formatComparison(abs(delta), indicator.comparison.unit)}"
            delta < -0.05 -> "较上次 ↓ ${formatComparison(abs(delta), indicator.comparison.unit)}"
            else -> "较上次 → ${formatComparison(0.0, indicator.comparison.unit)}"
        }
        val comparisonColor = when {
            delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
            delta > 0.05 -> Color(0xFF257A45)
            delta < -0.05 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = indicator.tier,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = comparisonText,
                style = MaterialTheme.typography.labelSmall,
                color = comparisonColor,
            )
        }
    }
}

private data class ScoreIndicator(
    val label: String,
    val value: String,
    val passed: Boolean,
    val tier: String,
    val comparison: ScoreComparison?,
)

private data class ScoreComparison(
    val current: Double,
    val previous: Double,
    val unit: String = "",
)

private fun formatComparison(value: Double, unit: String): String =
    if (unit == " Hz") "%.0f%s".format(value, unit) else "%.1f%s".format(value, unit)

private fun scoreIndicators(
    result: PitcheeResult,
    previousMetrics: PreviousMetrics?,
): List<ScoreIndicator> {
    val standard = result.vfp.standardScore
    val naturalness = result.naturalness.score
    val f0 = result.f0.meanHz
    val rule = result.composite.rule
    val standardLimited = when (rule) {
        "high_f0_male_cap" -> true
        "continuous" -> standard < 50.0
        else -> false
    }
    val naturalnessLimited = when (rule) {
        "high_f0_stylized_cap", "low_f0_stylized_cap" -> true
        "continuous" -> naturalness < 50.0
        else -> false
    }
    val f0Limited = when (rule) {
        "f0_unavailable", "low_f0_natural_cap", "low_f0_stylized_cap" -> true
        "continuous" -> f0 == null || f0 <= 165.0
        else -> false
    }
    val standardTier = scoreTier(standard, !standardLimited)
    val naturalnessTier = scoreTier(naturalness, !naturalnessLimited)
    val f0Score = f0?.let { ((it - 110.0) / 90.0 * 100.0).coerceIn(0.0, 100.0) } ?: 0.0
    val f0Tier = scoreTier(f0Score, !f0Limited)
    return listOf(
        ScoreIndicator(
            label = "标准音色",
            value = "%.1f".format(standard),
            passed = !standardLimited,
            tier = standardTier,
            comparison = previousMetrics?.let {
                ScoreComparison(standard, it.standardScore)
            },
        ),
        ScoreIndicator(
            label = "自然度",
            value = "%.1f".format(naturalness),
            passed = !naturalnessLimited,
            tier = naturalnessTier,
            comparison = previousMetrics?.let {
                ScoreComparison(naturalness, it.naturalnessScore)
            },
        ),
        ScoreIndicator(
            label = "平均 F0",
            value = f0?.let { "%.0f Hz".format(it) } ?: "未检测到",
            passed = !f0Limited,
            tier = f0Tier,
            comparison = if (f0 != null && previousMetrics?.meanF0Hz != null) {
                ScoreComparison(f0, previousMetrics.meanF0Hz, " Hz")
            } else {
                null
            },
        ),
    ).sortedBy { it.passed }
}

private fun scoreTier(score: Double, passed: Boolean): String {
    if (!passed) return "综分限制项"
    return when (score.coerceAtLeast(50.0)) {
        in 50.0..<60.0 -> "好"
        in 60.0..<70.0 -> "良好"
        in 70.0..<80.0 -> "较好"
        else -> "非常好"
    }
}

@Composable
private fun RuleDetails(
    rule: ScoringRuleDoc,
    isCurrent: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rule.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "当前命中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = rule.guidance,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "触发条件",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = rule.condition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "计算公式",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FormulaView(rule.formulas.map(::latexBlock))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "处理结果",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = rule.handling,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FormulaView(formulas: List<String>) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary
    val formulaTextSize = with(density) { 17.sp.toPx() }
    val markwon = remember(context, textColor, formulaTextSize) {
        Markwon.builder(context)
            .usePlugin(
                JLatexMathPlugin.create(
                    formulaTextSize,
                    JLatexMathPlugin.BuilderConfigure { builder ->
                        builder.theme()
                            .textColor(textColor)
                            .blockTextColor(textColor)
                            .blockFitCanvas(true)
                            .blockPadding(JLatexMathTheme.Padding.symmetric(6, 12))
                    },
                ),
            )
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        formulas.forEachIndexed { index, formula ->
            AndroidView(
                factory = { viewContext ->
                    TextView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        setTextColor(textColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setLineSpacing(0f, 1.15f)
                        includeFontPadding = false
                        setPadding(0, 0, 0, 0)
                    }
                },
                update = { textView -> markwon.setMarkdown(textView, formula) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (index != formulas.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun latexBlock(body: String): String {
    val lines = body.lines().filter { it.isNotBlank() }
    val content = when {
        lines.size == 1 -> lines.single()
        body.contains("\\begin{") -> body
        else -> "\\begin{gathered}\n${lines.joinToString(" \\\\\n")}\n\\end{gathered}"
    }
    return "\$\$\n$content\n\$\$"
}

private data class ScoringRuleDoc(
    val key: String,
    val title: String,
    val guidance: String,
    val condition: String,
    val formulas: List<String>,
    val handling: String,
)

private val commonScoreFormulas = listOf(
    """Standard = vfp\_standard\_score""",
    """Naturalness = naturalness\_score""",
    """F0 = mean\_f0\_hz""",
    """Standard_r = \frac{Standard}{100}""",
    """N_1 = \frac{Naturalness - 40}{50}""",
    """Naturalness_r = \max(0, \min(1, N_1))""",
    """F_1 = \frac{F0 - 110}{90}""",
    """F0_r = \max(0, \min(1, F_1))""",
    """Base = 100 \times \left(\begin{gathered}
0.50Standard_r + 0.20Naturalness_r\\
+ 0.15F0_r + 0.15Standard_rNaturalness_rF0_r
\end{gathered}\right)""",
    """Final = rule(Base, Standard, Naturalness, F0)""",
)

private val scoringRuleDocs = listOf(
    ScoringRuleDoc(
        key = "continuous",
        title = "连续评分",
        guidance = "这次没有触发特殊限制。保持现在的节奏再录一次，就能观察趋势变化。",
        condition = "未命中下列任何封顶或提升规则",
        formulas = listOf("""Final = Base"""),
        handling = "综合分采用 Base。",
    ),
    ScoringRuleDoc(
        key = "pass_boost",
        title = "加分",
        guidance = "三项指标都已经过线。继续用现在舒服的音高和语速朗读，稳定度会更容易保持。",
        condition = "F0 > 165，Naturalness > 80，Standard > 50",
        formulas = listOf(
            """s_F0 = \frac{F0 - 165}{25}""",
            """s_N = \frac{Naturalness - 80}{20}""",
            """s_S = \frac{Standard - 50}{30}""",
            """strength = \min(s_F0, s_N, s_S, 1)""",
            """promoted = 60 + 40strength""",
            """Final = \max(Base, promoted)""",
        ),
        handling = "综合分最高为 100。\n如果 promoted > Base，综合分采用 promoted。",
    ),
    ScoringRuleDoc(
        key = "high_f0_stylized_cap",
        title = "高基频、低自然度封顶",
        guidance = "音高已经上去了，但自然度还没跟上。下一次先放松语气，不必刻意抬高音调。",
        condition = "F0 > 165，Naturalness < 50",
        formulas = listOf("""Final = \min(Base, 30)"""),
        handling = "综合分最高为 30。\n请尝试自然说话，再提高音高。",
    ),
    ScoringRuleDoc(
        key = "low_f0_natural_cap",
        title = "低基频封顶",
        guidance = "自然度已经不错，接下来把注意力放在音高上。声音不用抬高，保持舒服就好。",
        condition = "F0 ≤ 165，Naturalness ≥ 50",
        formulas = listOf("""Final = \min(Base, 59)"""),
        handling = "综合分最高为 59。\n下一次试着用稍高但仍舒服的音调朗读。",
    ),
    ScoringRuleDoc(
        key = "low_f0_stylized_cap",
        title = "低基频、低自然度",
        guidance = "这次音高和自然度都需要照顾。先放慢一点，完整自然地读完整个句子。",
        condition = "F0 ≤ 165，Naturalness < 50",
        formulas = listOf("""Final = \min(Base, 20)"""),
        handling = "综合分最高为 20。\n先把朗读放松，再逐步提高音高。",
    ),
    ScoringRuleDoc(
        key = "high_f0_male_cap",
        title = "音色分不足",
        guidance = "音高和自然度已经达标，接下来重点练音色。试着让声音更明亮、更轻松。",
        condition = "F0 > 165，Naturalness ≥ 50，Standard < 50",
        formulas = listOf("""Final = \min(Base, 59)"""),
        handling = "综合分最高为 59。\n下一次重点观察 Standard 的变化。",
    ),
    ScoringRuleDoc(
        key = "f0_unavailable",
        title = "基频不可用",
        guidance = "没有识别到稳定基频，下次可以离麦克风近一点。",
        condition = "F0 不可用",
        formulas = listOf("Final = Standard"),
        handling = "综合分直接采用标准音色分 Standard。",
    ),
)

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
    val darkTheme = isSystemInDarkTheme()

    InsightCard(
        label = "主要短板",
        title = insight.bottleneckTitle,
        description = insight.bottleneckDescription,
        containerColor = containerColor,
        contentColor = contentColor,
        actionContainerColor = if (darkTheme) Color(0xFFB64B5D) else Color(0xFF8E1B2E),
        actionContentColor = Color.White,
        onOpenRules = onOpenRules,
    )
}

@Composable
private fun MetricsCard(
    result: PitcheeResult,
    insight: ScoreInsight,
    previousMetrics: PreviousMetrics?,
    animate: Boolean,
) {
    val cardColor = MaterialTheme.colorScheme.tertiaryContainer
    val toggleColor = MaterialTheme.colorScheme.tertiary
    val toggleContentColor = MaterialTheme.colorScheme.onTertiary
    val currentStandard = result.vfp.standardScore
    val currentNaturalness = result.naturalness.score
    val currentF0 = result.f0.meanHz
    val previousStandard = previousMetrics?.standardScore ?: 0.0
    val previousNaturalness = previousMetrics?.naturalnessScore ?: 0.0
    val previousF0 = previousMetrics?.meanF0Hz ?: 0.0
    val standardAnimation = remember { Animatable(previousStandard.toFloat()) }
    val naturalnessAnimation = remember { Animatable(previousNaturalness.toFloat()) }
    val f0Animation = remember { Animatable(previousF0.toFloat()) }
    val compareInteraction = remember { MutableInteractionSource() }
    val comparing by compareInteraction.collectIsPressedAsState()

    LaunchedEffect(result, previousMetrics, animate) {
        standardAnimation.stop()
        naturalnessAnimation.stop()
        f0Animation.stop()
        standardAnimation.snapTo(previousStandard.toFloat())
        naturalnessAnimation.snapTo(previousNaturalness.toFloat())
        f0Animation.snapTo(previousF0.toFloat())
        if (!animate) {
            standardAnimation.snapTo(currentStandard.toFloat())
            naturalnessAnimation.snapTo(currentNaturalness.toFloat())
            f0Animation.snapTo((currentF0 ?: 0.0).toFloat())
            return@LaunchedEffect
        }

        delay(500)
        val metricSpring = spring(
            dampingRatio = 0.62f,
            stiffness = 180f,
            visibilityThreshold = 0.01f,
        )
        launch {
            standardAnimation.animateTo(
                targetValue = currentStandard.toFloat(),
                animationSpec = metricSpring,
            )
        }
        launch {
            naturalnessAnimation.animateTo(
                targetValue = currentNaturalness.toFloat(),
                animationSpec = metricSpring,
            )
        }
        launch {
            f0Animation.animateTo(
                targetValue = (currentF0 ?: 0.0).toFloat(),
                animationSpec = metricSpring,
            )
        }
    }

    val standardProgress = if (comparing) {
        (previousStandard / 100.0).toFloat()
    } else {
        (standardAnimation.value / 100f).coerceIn(0f, 1f)
    }
    val naturalnessProgress = if (comparing) {
        (previousNaturalness / 100.0).toFloat()
    } else {
        (naturalnessAnimation.value / 100f).coerceIn(0f, 1f)
    }
    val f0ProgressValue = if (comparing) previousF0 else f0Animation.value.toDouble()
    val f0Progress = (f0ProgressScore(f0ProgressValue) / 100.0).toFloat()
    val standardForColor = if (comparing) previousStandard else standardAnimation.value.toDouble()
    val naturalnessForColor =
        if (comparing) previousNaturalness else naturalnessAnimation.value.toDouble()
    val f0ForColor = f0ProgressScore(f0ProgressValue)

    Column {
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
                    Button(
                        onClick = {},
                        interactionSource = compareInteraction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = toggleColor,
                            contentColor = toggleContentColor,
                        ),
                    ) {
                        Text(
                            text = "对比上次",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = "标准音色",
                        value = if (comparing) {
                            "%.1f".format(previousStandard)
                        } else {
                            "%.1f".format(currentStandard)
                        },
                        progress = standardProgress,
                        highlighted = insight.bottleneckTitle.contains("音色"),
                        score = standardForColor,
                    )
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = "自然度",
                        value = if (comparing) {
                            "%.1f".format(previousNaturalness)
                        } else {
                            "%.1f".format(currentNaturalness)
                        },
                        progress = naturalnessProgress,
                        highlighted = insight.bottleneckTitle.contains("自然度"),
                        score = naturalnessForColor,
                    )
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = "平均 F0",
                        value = if (comparing) {
                            previousMetrics?.meanF0Hz?.let { "%.0f Hz".format(it) } ?: "未检测到"
                        } else {
                            currentF0?.let { "%.0f Hz".format(it) } ?: "未检测到"
                        },
                        progress = f0Progress,
                        highlighted = insight.bottleneckTitle.contains("F0") ||
                            insight.bottleneckTitle.contains("基频"),
                        score = f0ForColor,
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
        if (previousMetrics == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "没有找到上次的指标。",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun f0ProgressScore(f0Hz: Double): Double =
    min(100.0, max(0.0, (f0Hz - 110.0) / 90.0 * 100.0))

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
