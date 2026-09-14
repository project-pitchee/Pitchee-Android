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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.rovly.pitchee.data.PitcheeResult
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.delay

@Composable
internal fun ScoreResultContent(
    result: PitcheeResult,
    previousScore: Double? = null,
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
internal fun ScoreRulesContent(result: PitcheeResult) {
    val insight = remember(result) { ScoreInsight.from(result) }
    val currentRule = result.composite.rule
    var otherRulesExpanded by rememberSaveable(currentRule) { mutableStateOf(false) }
    val currentRuleDoc = scoringRuleDocs.firstOrNull { it.key == currentRule }
    val otherRuleDocs = scoringRuleDocs.filterNot { it.key == currentRule }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "本次结果",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = insight.ruleName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = insight.ruleDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = buildString {
                append("基础分 %.1f".format(result.composite.baseScore))
                result.composite.cap?.let { append(" · 上限 %.0f".format(it)) }
                append(" · 最终 %.1f".format(result.composite.finalScore))
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = insight.ruleImpact,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "本次指标",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        scoreIndicators(result).forEach { indicator ->
            IndicatorRow(indicator)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "本次命中规则",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))

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
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Text(
                text = if (indicator.passed) "✓" else "×",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = indicator.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = indicator.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = if (indicator.passed) "未限制" else "限制分数",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

private data class ScoreIndicator(
    val label: String,
    val value: String,
    val passed: Boolean,
)

private fun scoreIndicators(result: PitcheeResult): List<ScoreIndicator> {
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
    return listOf(
        ScoreIndicator("标准音色", "%.1f".format(standard), !standardLimited),
        ScoreIndicator("自然度", "%.1f".format(naturalness), !naturalnessLimited),
        ScoreIndicator(
            label = "平均 F0",
            value = f0?.let { "%.0f Hz".format(it) } ?: "未检测到",
            passed = !f0Limited,
        ),
    )
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
        Spacer(Modifier.height(5.dp))
        Text(
            text = rule.condition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "公式",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        FormulaView(rule.formulas.map(::latexBlock))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "本次处理",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = rule.handling,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FormulaView(formulas: List<String>) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary
    val markwon = remember(context, textColor) {
        Markwon.builder(context)
            .usePlugin(
                JLatexMathPlugin.create(
                    18f,
                    JLatexMathPlugin.BuilderConfigure { builder ->
                        builder.theme()
                            .textColor(textColor)
                            .blockTextColor(textColor)
                            .blockPadding(JLatexMathTheme.Padding.all(0))
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
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
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
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
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

private fun latexBlock(body: String): String = "\$\$\n$body\n\$\$"

private data class ScoringRuleDoc(
    val key: String,
    val title: String,
    val condition: String,
    val formulas: List<String>,
    val handling: String,
)

private val scoringRuleDocs = listOf(
    ScoringRuleDoc(
        key = "continuous",
        title = "连续评分",
        condition = "未命中下列任何封顶或提升规则",
        formulas = listOf(
            """S_r = \frac{S}{100}""",
            """N_r = \max\left(0, \min\left(1, \frac{N - 40}{50}\right)\right)""",
            """F_r = \max\left(0, \min\left(1, \frac{F_0 - 110}{90}\right)\right)""",
            """base = 100 \times \left(0.50S_r + 0.20N_r + 0.15F_r + 0.15S_rN_rF_r\right)""",
        ),
        handling = "final_score = base_score。",
    ),
    ScoringRuleDoc(
        key = "pass_boost",
        title = "达标提升",
        condition = "F0 > 165 Hz，自然度 N > 80，标准音色 S > 50",
        formulas = listOf(
            """strength = \min\left(\frac{F_0 - 165}{25}, \frac{N - 80}{20}, \frac{S - 50}{30}, 1\right)""",
            """promoted = 60 + 40 \times strength""",
        ),
        handling = "若 promoted > base_score，则 final_score = promoted。",
    ),
    ScoringRuleDoc(
        key = "high_f0_stylized_cap",
        title = "高基频、低自然度封顶",
        condition = "F0 > 165 Hz 且 N < 50",
        formulas = listOf("""final = \min(base, 30)"""),
        handling = "最终分最高为 30。",
    ),
    ScoringRuleDoc(
        key = "low_f0_natural_cap",
        title = "低基频封顶",
        condition = "F0 ≤ 165 Hz 且 N ≥ 50",
        formulas = listOf("""final = \min(base, 59)"""),
        handling = "最终分最高为 59。",
    ),
    ScoringRuleDoc(
        key = "low_f0_stylized_cap",
        title = "低基频、低自然度封顶",
        condition = "F0 ≤ 165 Hz 且 N < 50",
        formulas = listOf("""final = \min(base, 20)"""),
        handling = "最终分最高为 20。",
    ),
    ScoringRuleDoc(
        key = "high_f0_male_cap",
        title = "音色分不足封顶",
        condition = "F0 > 165 Hz，N ≥ 50 且 S < 50",
        formulas = listOf("""final = \min(base, 59)"""),
        handling = "最终分最高为 59。",
    ),
    ScoringRuleDoc(
        key = "f0_unavailable",
        title = "基频不可用",
        condition = "未检测到可靠 F0",
        formulas = listOf("final = S"),
        handling = "直接使用标准音色分，不使用连续综合公式。",
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
private fun MetricsCard(result: PitcheeResult, insight: ScoreInsight) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.tertiaryContainer
    val toggleColor = MaterialTheme.colorScheme.tertiary
    val toggleContentColor = MaterialTheme.colorScheme.onTertiary
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
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                ),
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
