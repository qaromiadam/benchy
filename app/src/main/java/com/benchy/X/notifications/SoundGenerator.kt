package com.benchy.X.notifications

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

object SoundGenerator {

    private const val SAMPLE_RATE = 44100
    private const val SOUNDS_DIR = "sounds"
    const val DING_DING_FILE = "ding_ding.wav"
    const val DONG_DONG_FILE = "dong_dong.wav"

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val amplitude: Double = 0.65
    )

    fun ensureSoundFiles(context: Context): Pair<File, File> {
        val dir = File(context.filesDir, SOUNDS_DIR).apply { if (!exists()) mkdirs() }
        val goodFile = File(dir, DING_DING_FILE)
        val badFile = File(dir, DONG_DONG_FILE)

        if (!goodFile.exists() || goodFile.length() < 44) {
            // Good / Positive Alert: 1046.5 Hz for 90ms -> 40ms gap -> 1318.5 Hz for 140ms
            writeWav(
                file = goodFile,
                segments = listOf(
                    ToneSegment(freqHz = 1046.5, durationMs = 90, amplitude = 0.65),
                    ToneSegment(freqHz = 0.0, durationMs = 40, amplitude = 0.0),
                    ToneSegment(freqHz = 1318.5, durationMs = 140, amplitude = 0.70)
                )
            )
        }

        if (!badFile.exists() || badFile.length() < 44) {
            // Bad / Warning Alert: 220.0 Hz for 280ms -> 140ms gap -> 174.6 Hz for 380ms
            writeWav(
                file = badFile,
                segments = listOf(
                    ToneSegment(freqHz = 220.0, durationMs = 280, amplitude = 0.72),
                    ToneSegment(freqHz = 0.0, durationMs = 140, amplitude = 0.0),
                    ToneSegment(freqHz = 174.6, durationMs = 380, amplitude = 0.75)
                )
            )
        }

        return goodFile to badFile
    }

    private fun writeWav(file: File, segments: List<ToneSegment>) {
        val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        val pcmBytes = ByteArray(totalSamples * 2)
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        val fadeSamples = (SAMPLE_RATE * 5) / 1000 // 5ms smooth envelope prevents pops
        for (seg in segments) {
            val segSamples = (SAMPLE_RATE * seg.durationMs) / 1000
            for (i in 0 until segSamples) {
                val sampleValue: Short = if (seg.freqHz <= 0.0) {
                    0
                } else {
                    val envIn = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
                    val envOut = if (i > segSamples - fadeSamples) {
                        (segSamples - i).toDouble() / fadeSamples
                    } else {
                        1.0
                    }
                    val env = min(envIn, envOut).coerceIn(0.0, 1.0)
                    val angle = 2.0 * PI * seg.freqHz * i / SAMPLE_RATE
                    val raw = sin(angle) * seg.amplitude * env * Short.MAX_VALUE
                    raw.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                pcmBuffer.putShort(sampleValue)
            }
        }

        val dataSize = pcmBytes.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = 1 (PCM)
            putShort(1) // NumChannels = 1 (Mono)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2) // ByteRate
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
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
