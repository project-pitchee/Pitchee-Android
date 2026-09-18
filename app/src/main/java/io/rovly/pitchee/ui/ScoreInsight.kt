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
    val ruleName: String,
    val ruleDescription: String,
    val ruleImpact: String,
    val ruleState: ScoreRuleState,
    val hasBottleneck: Boolean,
    val bottleneckTitle: String,
    val bottleneckDescription: String,
) {
    companion object {
        fun from(result: PitcheeResult, languageCode: String = "zh"): ScoreInsight {
            val english = languageCode.startsWith("en")
            val (ruleName, ruleDescription, ruleImpact, ruleState) =
                ruleSummary(result, english)
            val bottleneck = findBottleneck(result, english)
            return ScoreInsight(
                ruleName = ruleName,
                ruleDescription = ruleDescription,
                ruleImpact = ruleImpact,
                ruleState = ruleState,
                hasBottleneck = bottleneck != null,
                bottleneckTitle = bottleneck?.title.orEmpty(),
                bottleneckDescription = bottleneck?.description.orEmpty(),
            )
        }

        private fun tr(english: Boolean, zh: String, en: String): String =
            if (english) en else zh

        private fun ruleSummary(result: PitcheeResult, english: Boolean): RuleSummary {
            val composite = result.composite
            return when (composite.rule) {
                "f0_unavailable" -> RuleSummary(
                    ruleName = tr(english, "基频不可用", "F0 unavailable"),
                    ruleDescription = tr(
                        english,
                        "未找到可靠的基频，本次直接使用标准音色分，未套用综合分公式。",
                        "No reliable F0 was found, so the final score uses the timbre score.",
                    ),
                    ruleImpact = tr(
                        english,
                        "综合分 = 标准音色分",
                        "Final = timbre score",
                    ),
                    ruleState = ScoreRuleState.F0_UNAVAILABLE,
                )
                "pass_boost" -> RuleSummary(
                    ruleName = tr(english, "达标提升规则", "Qualified boost"),
                    ruleDescription = tr(
                        english,
                        "三项条件同时满足时，综合分进入 60–100 区间。",
                        "When all three conditions are met, the final score moves into the 60–100 range.",
                    ),
                    ruleImpact = if (composite.boosted) {
                        tr(
                            english,
                            "规则提升 +%.1f".format(composite.finalScore - composite.baseScore),
                            "Boost +%.1f".format(composite.finalScore - composite.baseScore),
                        )
                    } else {
                        tr(english, "已满足提升条件", "Boost conditions met")
                    },
                    ruleState = ScoreRuleState.BOOSTED,
                )
                "high_f0_stylized_cap" -> cappedRule(
                    name = tr(english, "高基频、低自然度封顶", "High F0, low naturalness cap"),
                    cap = composite.cap,
                    description = tr(
                        english,
                        "基频已经较高，但自然度低于 50。",
                        "F0 is high, but naturalness is below 50.",
                    ),
                    result = result,
                    english = english,
                )
                "low_f0_natural_cap" -> cappedRule(
                    name = tr(english, "低基频封顶", "Low F0 cap"),
                    cap = composite.cap,
                    description = tr(
                        english,
                        "自然度达标，但 F0 不高于 165 Hz，当前基频限制了综合分。",
                        "Naturalness passes, but F0 is not above 165 Hz.",
                    ),
                    result = result,
                    english = english,
                )
                "low_f0_stylized_cap" -> cappedRule(
                    name = tr(
                        english,
                        "低基频、低自然度封顶",
                        "Low F0, low naturalness cap",
                    ),
                    cap = composite.cap,
                    description = tr(
                        english,
                        "F0 不高于 165 Hz 且自然度低于 50，基频和自然度同时拖分。",
                        "F0 is not above 165 Hz and naturalness is below 50.",
                    ),
                    result = result,
                    english = english,
                )
                "high_f0_male_cap" -> cappedRule(
                    name = tr(english, "音色分不足封顶", "Timbre score cap"),
                    cap = composite.cap,
                    description = tr(
                        english,
                        "基频高于 165 Hz，但模型音色标准分低于 50，音色仍在限制最终结果。",
                        "F0 is above 165 Hz, but timbre remains below 50.",
                    ),
                    result = result,
                    english = english,
                )
                else -> RuleSummary(
                    ruleName = tr(english, "连续评分", "Continuous scoring"),
                    ruleDescription = tr(
                        english,
                        "本次没有触发封顶或提升，直接使用四项连续评分公式。",
                        "No cap or boost was triggered, so the continuous formula was used.",
                    ),
                    ruleImpact = tr(english, "无封顶、无提升", "No cap or boost"),
                    ruleState = ScoreRuleState.CONTINUOUS,
                )
            }
        }

        private fun cappedRule(
            name: String,
            cap: Double?,
            description: String,
            result: PitcheeResult,
            english: Boolean,
        ): RuleSummary {
            val reduction = result.composite.baseScore - result.composite.finalScore
            return RuleSummary(
                ruleName = name,
                ruleDescription = description,
                ruleImpact = if (result.composite.limited && reduction > 0.05) {
                    tr(
                        english,
                        "封顶 ${cap?.let { "%.0f".format(it) } ?: "--"}，实际压低 %.1f 分"
                            .format(reduction),
                        "Cap ${cap?.let { "%.0f".format(it) } ?: "--"}, reduced by %.1f"
                            .format(reduction),
                    )
                } else {
                    tr(
                        english,
                        "规则上限 ${cap?.let { "%.0f".format(it) } ?: "--"}，本次未继续压低",
                        "Rule cap ${cap?.let { "%.0f".format(it) } ?: "--"}, no further reduction",
                    )
                },
                ruleState = ScoreRuleState.CAPPED,
            )
        }

        private fun findBottleneck(result: PitcheeResult, english: Boolean): Bottleneck? =
            when (result.composite.rule) {
                "f0_unavailable" -> Bottleneck(
                    tr(english, "基频不可用", "F0 unavailable"),
                    tr(
                        english,
                        "没有可靠的 F0，本次没有触发其他综合分规则。",
                        "No reliable F0 was available for the other rules.",
                    ),
                )
                "high_f0_stylized_cap" -> Bottleneck(
                    tr(english, "自然度低于阈值", "Naturalness below threshold"),
                    tr(
                        english,
                        "F0 高于 165 Hz，但自然度低于 50，触发最高 30 分规则。",
                        "F0 is above 165 Hz, but naturalness below 50 caps the score at 30.",
                    ),
                )
                "low_f0_natural_cap" -> Bottleneck(
                    tr(english, "F0 低于阈值", "F0 below threshold"),
                    tr(
                        english,
                        "自然度达到 50，但 F0 不高于 165 Hz，触发最高 59 分规则。",
                        "Naturalness passes, but F0 at or below 165 Hz caps the score at 59.",
                    ),
                )
                "low_f0_stylized_cap" -> Bottleneck(
                    tr(
                        english,
                        "F0 和自然度低于阈值",
                        "F0 and naturalness below thresholds",
                    ),
                    tr(
                        english,
                        "F0 不高于 165 Hz，且自然度低于 50，触发最高 20 分规则。",
                        "F0 at or below 165 Hz and naturalness below 50 cap the score at 20.",
                    ),
                )
                "high_f0_male_cap" -> Bottleneck(
                    tr(english, "音色标准分低于阈值", "Timbre below threshold"),
                    tr(
                        english,
                        "F0 高于 165 Hz，但音色标准分低于 50，触发最高 59 分规则。",
                        "F0 is above 165 Hz, but timbre below 50 caps the score at 59.",
                    ),
                )
                "pass_boost" -> null
                else -> continuousBottleneck(result, english)
            }

        private fun continuousBottleneck(
            result: PitcheeResult,
            english: Boolean,
        ): Bottleneck? {
            val standard = result.vfp.standardScore
            val naturalness = result.naturalness.score
            val f0 = result.f0.meanHz
            val f0Score = f0?.let {
                min(100.0, max(0.0, (it - 110.0) / 90.0 * 100.0))
            }
            val issues = buildList {
                if (f0Score == null) {
                    add(
                        Triple(
                            tr(english, "基频不可用", "F0 unavailable"),
                            tr(
                                english,
                                "没有可用于连续评分的可靠 F0。",
                                "No reliable F0 was available for continuous scoring.",
                            ),
                            0.0,
                        )
                    )
                } else if (f0 <= 165.0) {
                    add(
                        Triple(
                            tr(english, "F0 低于阈值", "F0 below threshold"),
                            tr(english, "平均 F0 不高于 165 Hz。", "Average F0 is at or below 165 Hz."),
                            f0Score,
                        )
                    )
                }
                if (standard < 50.0) {
                    add(
                        Triple(
                            tr(english, "音色标准分低于阈值", "Timbre below threshold"),
                            tr(english, "音色标准分低于 50。", "Timbre score is below 50."),
                            standard,
                        )
                    )
                }
                if (naturalness < 50.0) {
                    add(
                        Triple(
                            tr(english, "自然度低于阈值", "Naturalness below threshold"),
                            tr(english, "自然度低于 50。", "Naturalness is below 50."),
                            naturalness,
                        )
                    )
                }
            }
            val weakest = issues.minByOrNull { it.third } ?: return null
            return Bottleneck(weakest.first, weakest.second)
        }
    }
}

private data class Bottleneck(
    val title: String,
    val description: String,
)

private data class RuleSummary(
    val ruleName: String,
    val ruleDescription: String,
    val ruleImpact: String,
    val ruleState: ScoreRuleState,
)
