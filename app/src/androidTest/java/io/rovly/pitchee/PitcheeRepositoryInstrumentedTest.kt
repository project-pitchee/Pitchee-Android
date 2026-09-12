package io.rovly.pitchee

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.rovly.pitchee.data.FeminineTimeline
import io.rovly.pitchee.data.PitcheeRepository
import space.pitchee.core.PitcheePhase
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PitcheeRepositoryInstrumentedTest {
    @Test
    fun handlesPcmWavAndSuccessfulSpeechAnalysis() = runBlocking {
        val repository = PitcheeRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        try {
            val error = runCatching {
                repository.analyzePcm(
                    samples = FloatArray(16_000),
                    sampleRate = 16_000,
                    channels = 1,
                )
            }.exceptionOrNull()

            assertNotNull("Silence should be rejected by VAD", error)
            assertTrue(
                "Unexpected native error: ${error?.message}",
                error?.message.orEmpty().contains("no speech", ignoreCase = true),
            )

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val wav = File(context.cacheDir, "pitchee-silence.wav")
            try {
                writeSilenceWav(wav)
                val fileError = runCatching {
                    repository.analyze(Uri.fromFile(wav))
                }.exceptionOrNull()
                assertNotNull("Silent WAV should be rejected by VAD", fileError)
                assertTrue(
                    "Unexpected WAV error: ${fileError?.message}",
                    fileError?.message.orEmpty().contains("no speech", ignoreCase = true),
                )
            } finally {
                wav.delete()
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val speech = File(context.cacheDir, "pitchee-speech.m4a")
            try {
                instrumentation.context.assets.open("pitchee-speech.m4a").use { input ->
                    speech.outputStream().use(input::copyTo)
                }
                val phases = mutableListOf<PitcheePhase>()
                val pcm = repository.decode(
                    uri = Uri.fromFile(speech),
                    onPhase = phases::add,
                )
                val result = repository.analyzePcm(
                    samples = pcm.samples,
                    sampleRate = pcm.sampleRate,
                    channels = pcm.channels,
                    onPhase = phases::add,
                )
                assertTrue(
                    "Final score should be in [0, 100] but was ${result.composite.finalScore}",
                    result.composite.finalScore in 0.0..100.0,
                )
                assertTrue(
                    "Expected the synthetic speech to produce a VFP window",
                    result.vfp.windowCount > 0,
                )
                assertTrue(
                    "Native phase callback was not invoked: $phases",
                    PitcheePhase.ANALYZING in phases && PitcheePhase.COMPLETED in phases,
                )
                val timeline = FeminineTimeline.from(result, pcm.durationSeconds)
                val speechStart = result.vad.segments.first().startSeconds
                assertNotNull(
                    "Expected a time-level VFP score inside detected speech",
                    timeline.scoreAt(speechStart + 0.1),
                )
            } finally {
                speech.delete()
            }
        } finally {
            repository.close()
        }
    }

    private fun writeSilenceWav(file: File) {
        val sampleRate = 16_000
        val sampleCount = sampleRate
        val dataSize = sampleCount * Short.SIZE_BYTES
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * Short.SIZE_BYTES)
            putShort(Short.SIZE_BYTES.toShort())
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }
        file.outputStream().use { output ->
            output.write(header.array())
            output.write(ByteArray(dataSize))
        }
    }
}
