package io.rovly.pitchee

import org.junit.Assert.assertEquals
import org.junit.Test
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeProgressStage

class PitcheeProgressTest {
    @Test
    fun naturalnessStagesContributeAtMostTwentyPercent() {
        val start = PitcheeProgress(
            stage = PitcheeProgressStage.PREPARING_NATURALNESS_WINDOWS,
            completed = 0,
            total = 1,
            stageFraction = 0f,
        ).overallFraction
        val end = PitcheeProgress(
            stage = PitcheeProgressStage.SCORING_NATURALNESS_WINDOWS,
            completed = 1,
            total = 1,
            stageFraction = 1f,
        ).overallFraction

        assertEquals(0.20f, end - start, 0.0001f)
    }

    @Test
    fun completedStageReachesFullProgress() {
        val progress = PitcheeProgress(
            stage = PitcheeProgressStage.COMPLETED,
            completed = 1,
            total = 1,
            stageFraction = 1f,
        )

        assertEquals(1f, progress.overallFraction, 0.0001f)
    }
}
