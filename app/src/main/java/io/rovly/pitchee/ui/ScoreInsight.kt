package io.rovly.pitchee.ui

import io.rovly.pitchee.data.PitcheeResult
import kotlin.math.max
import kotlin.math.min

enum class ScoreRuleState {
    CONTINUOUS,
    CAPPED,
    BOOSTED,
    F0_UNAVAILABLE,
}

data class ScoreInsight(
    val headline: String,
    val ruleName: String,
    val ruleDescription: String,
    val ruleImpact: String,
    val ruleState: ScoreRuleState,
    val bottleneckTitle: String,
    val bottleneckDescription: String,
) {
    companion object {
        fun from(result: PitcheeResult): ScoreInsight {
            val score = result.composite.finalScore
            val headline = when {
                score >= 80 -> "主要指标高于参考线"
                score >= 60 -> "主要指标达到参考线"
                score >= 40 -> "存在一项低于参考线的指标"
                else -> "当前分数主要受短板指标限制"
            }

            val (ruleName, ruleDescription, ruleImpact, ruleState) = ruleSummary(result)
            val (bottleneckTitle, bottleneckDescription) = findBottleneck(result)
            return ScoreInsight(
                headline = headline,
                ruleName = ruleName,
                ruleDescription = ruleDescription,
                ruleImpact = ruleImpact,
                ruleState = ruleState,
                bottleneckTitle = bottleneckTitle,
                bottleneckDescription = bottleneckDescription,
            )
        }

        private fun ruleSummary(result: PitcheeResult): RuleSummary {
            val composite = result.composite
            return when (composite.rule) {
                "f0_unavailable" -> RuleSummary(
                    ruleName = "基频不可用",
                    ruleDescription = "未找到可靠的基频，本次直接使用标准音色分，未套用综合分公式。",
                    ruleImpact = "综合分 = 标准音色分",
                    ruleState = ScoreRuleState.F0_UNAVAILABLE,
                )
                "pass_boost" -> RuleSummary(
                    ruleName = "达标提升规则",
                    ruleDescription = "三项条件同时满足时，综合分进入 60–100 区间。",
                    ruleImpact = if (composite.boosted) {
                        "规则提升 +%.1f".format(composite.finalScore - composite.baseScore)
                    } else {
                        "已满足提升条件"
                    },
                    ruleState = ScoreRuleState.BOOSTED,
                )
                "high_f0_stylized_cap" -> cappedRule(
                    name = "高基频、低自然度封顶",
                    cap = composite.cap,
                    description = "基频已经较高，但自然度低于 50。",
                    result = result,
                )
                "low_f0_natural_cap" -> cappedRule(
                    name = "低基频封顶",
                    cap = composite.cap,
                    description = "自然度达标，但 F0 不高于 165 Hz，当前基频限制了综合分。",
                    result = result,
                )
                "low_f0_stylized_cap" -> cappedRule(
                    name = "低基频、低自然度封顶",
                    cap = composite.cap,
                    description = "F0 不高于 165 Hz 且自然度低于 50，基频和自然度同时拖分。",
                    result = result,
                )
                "high_f0_male_cap" -> cappedRule(
                    name = "音色分不足封顶",
                    cap = composite.cap,
                    description = "基频高于 165 Hz，但模型音色标准分低于 50，音色仍在限制最终结果。",
                    result = result,
                )
                else -> RuleSummary(
                    ruleName = "连续评分",
                    ruleDescription = "本次没有触发封顶或提升，直接使用四项连续评分公式。",
                    ruleImpact = "无封顶、无提升",
                    ruleState = ScoreRuleState.CONTINUOUS,
                )
            }
        }

        private fun cappedRule(
            name: String,
            cap: Double?,
            description: String,
            result: PitcheeResult,
        ): RuleSummary {
            val reduction = result.composite.baseScore - result.composite.finalScore
            return RuleSummary(
                ruleName = name,
                ruleDescription = description,
                ruleImpact = if (result.composite.limited && reduction > 0.05) {
                    "封顶 ${cap?.let { "%.0f".format(it) } ?: "--"} · 实际压低 %.1f 分"
                        .format(reduction)
                } else {
                    "规则上限 ${cap?.let { "%.0f".format(it) } ?: "--"}，本次未继续压低"
                },
                ruleState = ScoreRuleState.CAPPED,
            )
        }

        private fun findBottleneck(result: PitcheeResult): Pair<String, String> =
            when (result.composite.rule) {
                "f0_unavailable" -> "基频不可用" to
                    "没有可靠的 F0，本次没有触发其他综合分规则。"
                "high_f0_stylized_cap" -> "自然度低于阈值" to
                    "F0 高于 165 Hz，但自然度低于 50，触发最高 30 分规则。"
                "low_f0_natural_cap" -> "F0 低于阈值" to
                    "自然度达到 50，但 F0 不高于 165 Hz，触发最高 59 分规则。"
                "low_f0_stylized_cap" -> "F0 和自然度低于阈值" to
                    "F0 不高于 165 Hz，且自然度低于 50，触发最高 20 分规则。"
                "high_f0_male_cap" -> "音色标准分低于阈值" to
                    "F0 高于 165 Hz，但音色标准分低于 50，触发最高 59 分规则。"
                "pass_boost" -> "未触发封顶规则" to
                    "基频、自然度和音色标准分均达到提升门槛。"
                else -> continuousBottleneck(result)
            }

        private fun continuousBottleneck(result: PitcheeResult): Pair<String, String> {
            val standard = result.vfp.standardScore
            val naturalness = result.naturalness.score
            val f0 = result.f0.meanHz
            val f0Score = f0?.let {
                min(100.0, max(0.0, (it - 110.0) / 90.0 * 100.0))
            }
            val weakest = when {
                f0Score == null || f0Score <= standard && f0Score <= naturalness ->
                    "基频" to "平均 F0 在三个连续评分项中最低。"
                standard <= naturalness ->
                    "音色标准分" to "音色标准分在三个连续评分项中最低。"
                else ->
                    "自然度" to "自然度在三个连续评分项中最低。"
            }
            return weakest
        }
    }
}

private data class RuleSummary(
    val ruleName: String,
    val ruleDescription: String,
    val ruleImpact: String,
    val ruleState: ScoreRuleState,
)
