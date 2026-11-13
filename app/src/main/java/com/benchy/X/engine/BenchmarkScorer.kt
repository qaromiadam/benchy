package com.benchy.X.engine

import androidx.compose.ui.graphics.Color
import com.benchy.X.R
import kotlin.math.roundToInt

enum class PerformanceTier(
    val colorHex: Color,
    val isPositiveAlert: Boolean,
    val reviewResId: Int,
    val reviewEnglishFallback: String
) {
    CRITICAL(
        colorHex = Color(0xFFD32F2F),
        isPositiveAlert = false,
        reviewResId = R.string.review_tier_critical,
        reviewEnglishFallback = "Critical Performance Warning: Severe thermal throttling or hardware bottleneck detected. Apps may lag and the battery is running hot. Let your phone cool down and close background apps."
    ),
    DRAG(
        colorHex = Color(0xFFEF6C00),
        isPositiveAlert = false,
        reviewResId = R.string.review_tier_drag,
        reviewEnglishFallback = "Moderate Performance Drag: Your phone is starting to slow under load. Consider freeing up storage space or clearing active background tasks to regain speed."
    ),
    GOOD(
        colorHex = Color(0xFFC68A00),
        isPositiveAlert = true,
        reviewResId = R.string.review_tier_good,
        reviewEnglishFallback = "Good & Stable: Your phone is running smoothly for daily tasks, casual 2D games, and multitasking. Thermal levels are normal."
    ),
    PEAK(
        colorHex = Color(0xFF2E7D32),
        isPositiveAlert = true,
        reviewResId = R.string.review_tier_peak,
        reviewEnglishFallback = "Peak Performance: Excellent condition! Your hardware is operating at top speed with great thermal efficiency. Perfect for heavy 3D gaming and multitasking."
    );

    companion object {
        fun fromScore(score: Int): PerformanceTier = when {
            score <= 20 -> CRITICAL
            score <= 50 -> DRAG
            score <= 80 -> GOOD
            else -> PEAK
        }
    }
}

data class BenchmarkSummary(
    val scanType: String,
    val overallScore: Int,
    val gaming2dRating: Int,
    val gaming3dRating: Int,
    val batteryTempRating: Int,
    val multitaskingRating: Int,
    val cpuSingleScore: Double,
    val cpuMultiScore: Double,
    val ramBandwidthMbPerSec: Double,
    val storageSeqReadMbPerSec: Double,
    val storageSeqWriteMbPerSec: Double,
    val threatsDetected: Int,
    val tier: PerformanceTier
)

object BenchmarkScorer {

    fun computeSummary(
        scanType: String,
        cpuSingle: NativeBridge.CpuSingleResult,
        cpuMulti: NativeBridge.CpuMultiResult,
        ram: NativeBridge.RamBenchResult,
        storage: NativeBridge.StorageBenchResult,
        gpu3dAvgFps: Double,
        telemetry: DeviceTelemetry,
        threatsDetected: Int
    ): BenchmarkSummary {
        // Normalize hardware metrics into 0..100 curves
        val cpuNorm = ((cpuSingle.score / 2200.0) * 45.0 + (cpuMulti.score / 9500.0) * 55.0)
            .coerceIn(10.0, 100.0)
        val ramNorm = ((ram.readBandwidthMbPerSec / 18000.0) * 100.0).coerceIn(15.0, 100.0)
        val storageNorm = (((storage.seqReadMbPerSec / 1200.0) * 55.0) + ((storage.seqWriteMbPerSec / 600.0) * 45.0))
            .coerceIn(12.0, 100.0)
        val gpuNorm = ((gpu3dAvgFps / 60.0) * 100.0).coerceIn(15.0, 100.0)

        val tempC = telemetry.battery.tempCelsius
        val thermalPenalty = when {
            tempC >= 46f -> 35.0
            tempC >= 42f -> 18.0
            tempC >= 39f -> 7.0
            else -> 0.0
        }
        val securityPenalty = (threatsDetected * 6.0).coerceAtMost(24.0)

        val rawScore = (cpuNorm * 0.34 + gpuNorm * 0.26 + ramNorm * 0.20 + storageNorm * 0.20) -
            thermalPenalty - securityPenalty
        val finalScore = rawScore.roundToInt().coerceIn(5, 100)

        val gaming2d = ((cpuNorm * 0.4 + gpuNorm * 0.6) / 10.0).roundToInt().coerceIn(1, 10)
        val gaming3d = ((gpuNorm * 0.72 + cpuNorm * 0.28 - thermalPenalty * 0.4) / 10.0)
            .roundToInt()
            .coerceIn(1, 10)
        val batteryTempRating = when {
            tempC <= 33f -> 10
            tempC <= 36f -> 9
            tempC <= 39f -> 7
            tempC <= 42f -> 5
            tempC <= 45f -> 3
            else -> 1
        }
        val availRamRatio = telemetry.memory.availableRamBytes.toDouble() /
            telemetry.memory.totalRamBytes.coerceAtLeast(1L).toDouble()
        val multitasking = ((ramNorm * 0.55 + (availRamRatio * 100.0) * 0.45) / 10.0)
            .roundToInt()
            .coerceIn(1, 10)

        return BenchmarkSummary(
            scanType = scanType,
            overallScore = finalScore,
            gaming2dRating = gaming2d,
            gaming3dRating = gaming3d,
            batteryTempRating = batteryTempRating,
            multitaskingRating = multitasking,
            cpuSingleScore = cpuSingle.score,
            cpuMultiScore = cpuMulti.score,
            ramBandwidthMbPerSec = ram.readBandwidthMbPerSec,
            storageSeqReadMbPerSec = storage.seqReadMbPerSec,
            storageSeqWriteMbPerSec = storage.seqWriteMbPerSec,
            threatsDetected = threatsDetected,
            tier = PerformanceTier.fromScore(finalScore)
        )
    }
}
