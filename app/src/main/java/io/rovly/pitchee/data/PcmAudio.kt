package io.rovly.pitchee.data

class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int,
) {
    val frameCount: Int
        get() = samples.size / channels

    val durationSeconds: Double
        get() = frameCount.toDouble() / sampleRate
}
