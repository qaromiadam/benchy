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
}
