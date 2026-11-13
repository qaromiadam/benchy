package com.benchy.X.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BatteryExportSample(
    val timestampMillis: Long,
    val voltageMv: Int,
    val currentMa: Int,
    val tempCelsius: Float,
    val levelPercent: Int,
    val elapsedDays: Float,
    val isCharging: Boolean,
    val chargeCounterUah: Int,
    val powerMw: Float
)

class BenchyRepository(private val db: BenchyDatabase) {

    val latestBenchmark: Flow<BenchmarkRecordEntity?> = db.benchmarkDao().observeLatest()
    val benchmarkHistory: Flow<List<BenchmarkRecordEntity>> = db.benchmarkDao().observeHistory()
    val recentBatterySamples: Flow<List<BatterySampleEntity>> = db.batterySampleDao().observeRecentSamples()
    val allowlistedApps: Flow<List<AllowlistedAppEntity>> = db.allowlistedAppDao().observeAllowlisted()

    suspend fun saveBenchmark(record: BenchmarkRecordEntity) {
        db.benchmarkDao().insert(record)
    }

    suspend fun insertBatterySample(sample: BatterySampleEntity) {
        db.batterySampleDao().insertSample(sample)
    }

    suspend fun clearBatterySamples() {
        db.batterySampleDao().clearAll()
    }

    suspend fun getAllowlistedPackages(): Set<String> {
        return db.allowlistedAppDao().getAllowlistedPackages().toSet()
    }

    suspend fun allowlistPackage(packageName: String, appLabel: String) {
        db.allowlistedAppDao().addAllowlisted(
            AllowlistedAppEntity(
                packageName = packageName,
                appLabel = appLabel,
                allowlistedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeAllowlistedPackage(packageName: String) {
        db.allowlistedAppDao().removeAllowlisted(packageName)
    }

    suspend fun exportBatterySamplesCsv(): String {
        val samples = db.batterySampleDao().getAllSamplesOrdered()
        val sb = StringBuilder()
        sb.appendLine("timestamp_ms,voltage_mv,current_ma,temp_c,level_percent,elapsed_days,is_charging,charge_counter_uah,power_mw")
        samples.forEach { s ->
            sb.appendLine("${s.timestampMillis},${s.voltageMv},${s.currentMa},${s.tempCelsius},${s.levelPercent},${s.elapsedDays},${s.isCharging},${s.chargeCounterUah},${s.powerMw}")
        }
        return sb.toString()
    }

    suspend fun exportBatterySamplesJson(): String {
        val samples = db.batterySampleDao().getAllSamplesOrdered().map {
            BatteryExportSample(
                timestampMillis = it.timestampMillis,
                voltageMv = it.voltageMv,
                currentMa = it.currentMa,
                tempCelsius = it.tempCelsius,
                levelPercent = it.levelPercent,
                elapsedDays = it.elapsedDays,
                isCharging = it.isCharging,
                chargeCounterUah = it.chargeCounterUah,
                powerMw = it.powerMw
            )
        }
        val json = Json { prettyPrint = true }
        return json.encodeToString(samples)
    }

    suspend fun exportBatterySamplesTxt(): String {
        val samples = db.batterySampleDao().getAllSamplesOrdered()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val sb = StringBuilder()
        sb.appendLine("============================================================")
        sb.appendLine("BENCHY — 1-WEEK BATTERY HEALTH & DRAIN LOG")
        sb.appendLine("Generated: ${sdf.format(java.util.Date())}")
        sb.appendLine("Total Samples Logged: ${samples.size}")
        if (samples.isNotEmpty()) {
            val minV = samples.minOf { it.voltageMv }
            val maxV = samples.maxOf { it.voltageMv }
            val avgV = samples.map { it.voltageMv }.average().toInt()
            val maxT = samples.maxOf { it.tempCelsius }
            val avgT = samples.map { it.tempCelsius }.average()
            val avgP = samples.map { it.powerMw }.average()
            val spanDays = samples.last().elapsedDays
            sb.appendLine(String.format(java.util.Locale.US, "Elapsed Span: %.3f days", spanDays))
            sb.appendLine("Voltage (mV): min=$minV | avg=$avgV | max=$maxV")
            sb.appendLine(String.format(java.util.Locale.US, "Temperature (°C): avg=%.1f | peak=%.1f", avgT, maxT))
            sb.appendLine(String.format(java.util.Locale.US, "Average Power Draw: %.1f mW", avgP))
        }
        sb.appendLine("============================================================")
        if (samples.isEmpty()) {
            sb.appendLine("No battery samples recorded yet. Start the 1-Week Battery Tracker to log telemetry.")
        } else {
            samples.forEach { s ->
                val ts = sdf.format(java.util.Date(s.timestampMillis))
                val state = if (s.isCharging) "Charging" else "Discharging"
                val capMah = s.chargeCounterUah / 1000
                sb.appendLine(
                    String.format(
                        java.util.Locale.US,
                        "[%s] Day %.3f | Level: %3d%% | %4d mV | %5d mA | %7.1f mW | %4.1f°C | %-11s | Counter: %d mAh",
                        ts, s.elapsedDays, s.levelPercent, s.voltageMv, s.currentMa,
                        s.powerMw, s.tempCelsius, state, capMah
                    )
                )
            }
        }
        return sb.toString()
    }
}
