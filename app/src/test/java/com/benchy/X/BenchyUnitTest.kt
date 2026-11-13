package com.benchy.X

import com.benchy.X.engine.BenchmarkScorer
import com.benchy.X.engine.BatterySnapshot
import com.benchy.X.engine.DeviceTelemetry
import com.benchy.X.engine.FileSecurityAnalyzer
import com.benchy.X.engine.FileThreatCategory
import com.benchy.X.engine.MemorySnapshot
import com.benchy.X.engine.NativeBridge
import com.benchy.X.engine.PerformanceTier
import com.benchy.X.engine.StorageSnapshot
import com.benchy.X.notifications.SoundGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    @Test
    fun fileSecurityAnalyzerDetectsEicarAndSpoofedDoubleExtension() {
        val tempDir = kotlin.io.path.createTempDirectory("benchy_sec_test").toFile()
        try {
            val eicarFile = File(tempDir, "test_payload.txt").apply {
                writeText("X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*")
            }
            val eicarHit = FileSecurityAnalyzer.inspectLocalFile(eicarFile)
            assertNotNull(eicarHit)
            assertEquals(FileThreatCategory.EICAR_TEST, eicarHit?.category)

            val spoofed = File(tempDir, "invoice.pdf.apk").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            }
            val spoofHit = FileSecurityAnalyzer.inspectLocalFile(spoofed)
            assertNotNull(spoofHit)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fileSecurityAnalyzerDetectsHighRatioZipBombWithoutCrashing() {
        val tempDir = kotlin.io.path.createTempDirectory("benchy_zipbomb_test").toFile()
        try {
            val bombZip = File(tempDir, "highly_compressed_test.zip")
            // 60 MB of zeroes compresses down to ~60 KB (~1000:1 ratio) -> triggers Zip Bomb check
            ZipOutputStream(bombZip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("zeros.bin"))
                val chunk = ByteArray(1024 * 1024)
                repeat(60) {
                    zos.write(chunk)
                }
                zos.closeEntry()
            }
            val hit = FileSecurityAnalyzer.inspectLocalFile(bombZip)
            assertNotNull(hit)
            assertEquals(FileThreatCategory.ZIP_BOMB, hit?.category)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun soundGeneratorProducesNonEmptyAudiblePcmBuffers() {
        val good = SoundGenerator.buildPcmSamples(isGood = true)
        val bad = SoundGenerator.buildPcmSamples(isGood = false)
        assertTrue(good.size > 10_000)
        assertTrue(bad.size > 10_000)
        assertTrue(good.maxOf { kotlin.math.abs(it.toInt()) } > 20_000)
        assertTrue(bad.maxOf { kotlin.math.abs(it.toInt()) } > 20_000)
        val goodRms = kotlin.math.sqrt(good.map { it.toDouble() * it.toDouble() }.average())
        val badRms = kotlin.math.sqrt(bad.map { it.toDouble() * it.toDouble() }.average())
        assertTrue("Expected high RMS loudness for Ding-Ding, got $goodRms", goodRms > 7_000.0)
        assertTrue("Expected high RMS loudness for Dong-Dong, got $badRms", badRms > 7_000.0)
    }
}
