package com.benchy.X.engine

import com.benchy.X.data.StorageUnit
import com.benchy.X.data.TempUnit

data class HealthMetricRating(
    val ratingOutOfTen: Int,
    val primaryValue: String,
    val secondaryValue: String,
    val advice: String
)

data class DeviceHealthReport(
    val batteryHeat: HealthMetricRating,
    val storageHealth: HealthMetricRating,
    val ramHealth: HealthMetricRating
) {
    val averageRating: Int
        get() = ((batteryHeat.ratingOutOfTen + storageHealth.ratingOutOfTen + ramHealth.ratingOutOfTen) / 3f)
            .toInt()
            .coerceIn(1, 10)
}

object DeviceHealthEvaluator {

    fun evaluate(
        telemetry: DeviceTelemetry,
        tempUnit: TempUnit,
        storageUnit: StorageUnit
    ): DeviceHealthReport {
        val tempC = telemetry.battery.tempCelsius
        val batteryRating = when {
            tempC <= 33f -> 10
            tempC <= 36f -> 9
            tempC <= 39f -> 7
            tempC <= 42f -> 5
            tempC <= 45f -> 3
            else -> 1
        }
        val batteryAdvice = when {
            batteryRating >= 8 -> "Battery temperature is cool and optimal for sustained performance and longevity."
            batteryRating >= 5 -> "Battery is moderately warm. Avoid fast-charging while gaming to reduce thermal wear."
            else -> "Battery is running hot! Unplug charger, pause heavy tasks, and let your device cool down."
        }

        val totalStorage = telemetry.storage.totalBytes.coerceAtLeast(1L)
        val freeStorage = telemetry.storage.freeBytes.coerceAtLeast(0L)
        val freeRatio = freeStorage.toDouble() / totalStorage.toDouble()
        val storageRating = when {
            freeRatio >= 0.45 -> 10
            freeRatio >= 0.30 -> 8
            freeRatio >= 0.20 -> 7
            freeRatio >= 0.12 -> 5
            freeRatio >= 0.06 -> 3
            else -> 1
        }
        val storageAdvice = when {
            storageRating >= 8 -> "Plenty of free flash storage keeps I/O wear-leveling and app updates fast."
            storageRating >= 5 -> "Storage is filling up. Consider clearing old downloads or cached media soon."
            else -> "Critically low free space can severely throttle write speeds and block OS updates."
        }

        val totalRam = telemetry.memory.totalRamBytes.coerceAtLeast(1L)
        val availRam = telemetry.memory.availableRamBytes.coerceAtLeast(0L)
        val availRatio = availRam.toDouble() / totalRam.toDouble()
        val totalRamGb = totalRam / (1024.0 * 1024.0 * 1024.0)
        val baseRamTier = when {
            totalRamGb >= 7.5 && availRatio >= 0.35 -> 10
            totalRamGb >= 5.5 && availRatio >= 0.28 -> 8
            availRatio >= 0.22 -> 7
            availRatio >= 0.14 -> 5
            availRatio >= 0.08 -> 3
            else -> 2
        }
        val ramRating = if (telemetry.memory.lowMemory) (baseRamTier - 2).coerceAtLeast(1) else baseRamTier
        val ramAdvice = when {
            ramRating >= 8 -> "Generous free RAM headroom allows smooth app switching without background reloads."
            ramRating >= 5 -> "Moderate memory pressure. Closing unused heavy apps will keep multitasking snappy."
            else -> "Low free RAM detected—Android may aggressively terminate background apps."
        }

        return DeviceHealthReport(
            batteryHeat = HealthMetricRating(
                ratingOutOfTen = batteryRating,
                primaryValue = TelemetryReader.formatTemp(tempC, tempUnit),
                secondaryValue = "${telemetry.battery.levelPercent}% • ${telemetry.battery.healthLabel}",
                advice = batteryAdvice
            ),
            storageHealth = HealthMetricRating(
                ratingOutOfTen = storageRating,
                primaryValue = "${TelemetryReader.formatStorage(freeStorage, storageUnit)} free",
                secondaryValue = "Total: ${TelemetryReader.formatStorage(totalStorage, storageUnit)}",
                advice = storageAdvice
            ),
            ramHealth = HealthMetricRating(
                ratingOutOfTen = ramRating,
                primaryValue = "${TelemetryReader.formatRamGb(availRam, storageUnit)} free",
                secondaryValue = "Total: ${TelemetryReader.formatRamGb(totalRam, storageUnit)}",
                advice = ramAdvice
            )
        )
    }
}
