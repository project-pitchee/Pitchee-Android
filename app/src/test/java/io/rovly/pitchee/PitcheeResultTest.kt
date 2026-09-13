package io.rovly.pitchee

import io.rovly.pitchee.data.PitcheeResult
import io.rovly.pitchee.data.hasSpeechSecondsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitcheeResultTest {
    @Test
    fun parsesCoreJsonContractV2() {
        val result = PitcheeResult.fromJson(
            """
            {
              "schema_version": 2,
              "model_version": "2026-09",
              "audio": {
                "source_sample_rate": 48000,
                "source_channels": 1,
                "input_seconds": 5.2,
                "analyzed_seconds": 5.2
              },
              "vad": {
                "segment_count": 1,
                "speech_seconds": 1.5,
                "silero_segment_count": 1,
                "discarded_breath_like_count": 0,
                "trimmed_segment_count": 0,
                "segments": [{
                  "start_seconds": 0.2,
                  "end_seconds": 1.7,
                  "speech_start_seconds": 0.0,
                  "speech_end_seconds": 1.5
                }]
              },
              "f0": {
                "window_seconds": 0.1,
                "mean_hz": null,
                "standard_deviation_hz": null,
                "voiced_frame_count": 0,
                "voiced_window_count": 0,
                "windows": []
              },
              "vfp": {
                "vfp_standard_score": 50.0,
                "window_count": 1,
                "window_duration_seconds": 1.5,
                "windows": [{
                  "start_seconds": 0.2,
                  "end_seconds": 1.7,
                  "vfp_standard_score": 50.0
                }]
              },
              "naturalness": {
                "score": 75.0,
                "window_count": 1,
                "window_duration_seconds": 1.5,
                "windows": [{
                  "start_seconds": 0.2,
                  "end_seconds": 1.7,
                  "score": 75.0
                }]
              },
              "composite": {
                "base_score": 60.0,
                "final_score": 60.0,
                "cap": null,
                "rule": "continuous",
                "limited": false,
                "boosted": false
              }
            }
            """.trimIndent()
        )

        assertEquals(2, result.schemaVersion)
        assertEquals("2026-09", result.modelVersion)
        assertEquals(1, result.vad.segments.size)
        assertEquals(50.0, result.vfp.windows.single().standardScore, 0.0)
        assertEquals(75.0, result.naturalness.windows.single().score, 0.0)
        assertNull(result.f0.meanHz)
        assertEquals(60.0, result.composite.finalScore, 0.0)
        assertFalse(result.hasSpeechSecondsAtLeast(5.0))
        assertTrue(
            result.copy(
                vad = result.vad.copy(speechSeconds = 5.0),
            ).hasSpeechSecondsAtLeast(5.0)
        )
    }
}
