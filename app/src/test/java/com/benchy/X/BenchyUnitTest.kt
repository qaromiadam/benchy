package com.benchy.X

import com.benchy.X.engine.BenchmarkScorer
import com.benchy.X.engine.BatterySnapshot
import com.benchy.X.engine.DeviceTelemetry
import com.benchy.X.engine.MemorySnapshot
import com.benchy.X.engine.NativeBridge
import com.benchy.X.engine.PerformanceTier
import com.benchy.X.engine.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchyUnitTest {

    @Test
    fun performanceTierBoundariesMatchSpecification() {
        assertEquals(PerformanceTier.CRITICAL, PerformanceTier.fromScore(0))
        assertEquals(PerformanceTier.CRITICAL, PerformanceTier.fromScore(20))
        assertEquals(PerformanceTier.DRAG, PerformanceTier.fromScore(21))
        assertEquals(PerformanceTier.DRAG, PerformanceTier.fromScore(50))
        assertEquals(PerformanceTier.GOOD, PerformanceTier.fromScore(51))
        assertEquals(PerformanceTier.GOOD, PerformanceTier.fromScore(80))
        assertEquals(PerformanceTier.PEAK, PerformanceTier.fromScore(81))
        assertEquals(PerformanceTier.PEAK, PerformanceTier.fromScore(100))

        assertFalse(PerformanceTier.fromScore(50).isPositiveAlert)
        assertTrue(PerformanceTier.fromScore(51).isPositiveAlert)
    }

    @Test
    fun scorerProducesValidBoundedMetrics() {
        val telemetry = DeviceTelemetry(
            socName = "Snapdragon",
            deviceModel = "Test Device",
            cpuCores = 8,
            perCoreMhz = List(8) { 1800 to 2800 },
            thermalStatusLabel = "Cool",
            thermalSeverity = 0,
            thermalHeadroom = 0.85f,
            thermalZones = emptyList(),
            battery = BatterySnapshot(85, 4100, -250, 31.5f, false, "Good", 3825_000, 1025f, "Discharging"),
            memory = MemorySnapshot(8L * 1024 * 1024 * 1024, 4L * 1024 * 1024 * 1024, false, 0, 0, true),
            storage = StorageSnapshot(128_000_000_000L, 64_000_000_000L)
        )
        val summary = BenchmarkScorer.computeSummary(
            scanType = "QUICK",
            cpuSingle = NativeBridge.CpuSingleResult(2000.0, 1200.0, 260.0, 1400.0),
            cpuMulti = NativeBridge.CpuMultiResult(8800.0, 6200.0, 1500.0, 1200.0),
            ram = NativeBridge.RamBenchResult(16500.0, 12000.0, 52.0),
            storage = NativeBridge.StorageBenchResult(550.0, 1150.0, 24000.0, 9000.0),
            gpu3dAvgFps = 59.0,
            telemetry = telemetry,
            threatsDetected = 0
        )
        assertTrue(summary.overallScore in 1..100)
        assertTrue(summary.gaming2dRating in 1..10)
        assertTrue(summary.gaming3dRating in 1..10)
        assertTrue(summary.batteryTempRating in 1..10)
        assertTrue(summary.multitaskingRating in 1..10)
    }

    @Test
    fun wallClockCountdownDecrementsSecondBySecond() {
        val totalDurationMs = 30_000L
        val samples = listOf(0L, 1_000L, 2_000L, 10_000L, 29_000L, 30_000L)
        val remaining = samples.map { elapsed ->
            kotlin.math.ceil(((totalDurationMs - elapsed).coerceAtLeast(0L)) / 1000.0).toInt()
        }
        assertEquals(listOf(30, 29, 28, 20, 1, 0), remaining)
    }
}
