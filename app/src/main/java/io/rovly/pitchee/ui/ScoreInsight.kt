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
                score >= 80 -> "整体表现很强，继续保持"
                score >= 60 -> "已经有明显优势，再打磨细节"
                score >= 40 -> "基础已建立，下一项提升最划算"
                else -> "先解决最拖分的部分"
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
                    ruleDescription = "基频、自然度和基础音色同时达到门槛，触发 60–100 区间的提升。",
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
                    description = "基频已经较高，但自然度低于 50，声音可能显得刻意或不稳定。",
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
                "f0_unavailable" -> "基频信息不足" to
                    "在安静环境中用稳定音量多录几秒，避免气声、耳语或过低的音量。"
                "high_f0_stylized_cap" -> "自然度是主要短板" to
                    "先减少刻意拔高和挤压音调，使用放松的说话状态，把自然度提高到 50 以上。"
                "low_f0_natural_cap" -> "F0 是主要短板" to
                    "保持当前自然度，通过轻松的发声练习逐步提升平均 F0，目标先接近并超过 165 Hz。"
                "low_f0_stylized_cap" -> "F0 和自然度同时不足" to
                    "先降低发声紧张感，再逐步提升音高；不要只追求高音而牺牲自然度。"
                "high_f0_male_cap" -> "模型音色是主要短板" to
                    "基频已经达标，下一步重点练习共鸣和音色，而不是继续抬高音调。"
                "pass_boost" -> "没有明显短板" to
                    "继续保持稳定的音高、自然度和音色表现，避免为了追求高分而过度用力。"
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
                    "基频" to "当前平均 F0 还需要提升，先把目标稳定在 165–200 Hz 区间。"
                standard <= naturalness ->
                    "音色标准分" to "音色模型分相对较弱，重点练习共鸣位置和更稳定的音色。"
                else ->
                    "自然度" to "自然度相对较弱，减少挤压和刻意变化，让说话更放松、稳定。"
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
