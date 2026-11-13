package com.benchy.X.notifications

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

object SoundGenerator {

    const val SAMPLE_RATE = 44100
    private const val SOUNDS_DIR = "sounds"
    const val DING_DING_FILE = "ding_ding_v3.wav"
    const val DONG_DONG_FILE = "dong_dong_v3.wav"

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val amplitude: Double = 0.98,
        val decayRate: Double = 1.0
    )

    private val goodSegments = listOf(
        // High-loudness bright Ding-Ding chime (D6 -> G6) tuned for phone speaker presence
        ToneSegment(freqHz = 1174.66, durationMs = 190, amplitude = 0.98, decayRate = 1.2),
        ToneSegment(freqHz = 0.0, durationMs = 50, amplitude = 0.0, decayRate = 0.0),
        ToneSegment(freqHz = 1567.98, durationMs = 380, amplitude = 0.98, decayRate = 0.9)
    )

    private val badSegments = listOf(
        // High-loudness warning Dong-Dong tone (A4 -> E4 with strong upper harmonics for small speakers)
        ToneSegment(freqHz = 440.00, durationMs = 280, amplitude = 0.98, decayRate = 0.8),
        ToneSegment(freqHz = 0.0, durationMs = 80, amplitude = 0.0, decayRate = 0.0),
        ToneSegment(freqHz = 329.63, durationMs = 440, amplitude = 0.98, decayRate = 0.7)
    )

    fun buildPcmSamples(isGood: Boolean): ShortArray {
        val segments = if (isGood) goodSegments else badSegments
        val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        val samples = ShortArray(totalSamples)
        var offset = 0
        val fadeInSamples = (SAMPLE_RATE * 4) / 1000
        val fadeOutSamples = (SAMPLE_RATE * 16) / 1000
        val normFactor = 1.0 / tanh(2.4)

        for (seg in segments) {
            val segSamples = (SAMPLE_RATE * seg.durationMs) / 1000
            for (i in 0 until segSamples) {
                val sampleValue: Short = if (seg.freqHz <= 0.0) {
                    0
                } else {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envIn = if (i < fadeInSamples) i.toDouble() / fadeInSamples else 1.0
                    val envOut = if (i > segSamples - fadeOutSamples) {
                        (segSamples - i).toDouble() / fadeOutSamples
                    } else {
                        1.0
                    }
                    val decay = exp(-seg.decayRate * t)
                    val env = min(envIn, envOut).coerceIn(0.0, 1.0) * decay

                    // Rich harmonic stack so small mobile speakers project strongly
                    val h1 = sin(2.0 * PI * seg.freqHz * t)
                    val h2 = 0.55 * sin(2.0 * PI * (seg.freqHz * 2.0) * t)
                    val h3 = 0.35 * sin(2.0 * PI * (seg.freqHz * 3.0) * t)
                    val h4 = 0.18 * sin(2.0 * PI * (seg.freqHz * 4.0) * t)
                    val composite = h1 + h2 + h3 + h4

                    // Soft-clipping waveshaper maximizes RMS loudness cleanly without digital harshness
                    val loudWave = tanh(composite * 1.45) * normFactor
                    val raw = loudWave * seg.amplitude * env * Short.MAX_VALUE
                    raw.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                if (offset < samples.size) {
                    samples[offset++] = sampleValue
                }
            }
        }
        return samples
    }

    fun ensureSoundFiles(context: Context): Pair<File, File> {
        val dir = File(context.filesDir, SOUNDS_DIR).apply { if (!exists()) mkdirs() }
        // Remove stale quieter v1/v2 audio files
        listOf("ding_ding.wav", "dong_dong.wav", "ding_ding_v2.wav", "dong_dong_v2.wav").forEach { oldName ->
            runCatching { File(dir, oldName).takeIf { it.exists() }?.delete() }
        }

        val goodFile = File(dir, DING_DING_FILE)
        val badFile = File(dir, DONG_DONG_FILE)

        if (!goodFile.exists() || goodFile.length() <= 44) {
            writeWavFromShorts(goodFile, buildPcmSamples(isGood = true))
        }
        if (!badFile.exists() || badFile.length() <= 44) {
            writeWavFromShorts(badFile, buildPcmSamples(isGood = false))
        }

        return goodFile to badFile
    }

    private fun writeWavFromShorts(file: File, samples: ShortArray) {
        val pcmBytes = ByteArray(samples.size * 2)
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            pcmBuffer.putShort(s)
        }
        val dataSize = pcmBytes.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmBytes)
            out.flush()
        }
    }
}
