package com.benchy.X.workers

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LongScanProgress(
    val isRunning: Boolean = false,
    val phaseLabel: String = "",
    val elapsedMillis: Long = 0L,
    val totalDurationMillis: Long = LongScanCoordinator.TWO_HOURS_MILLIS,
    val progressPercent: Int = 0,
    val interimScore: Int = 0,
    val interimCpuSingle: Double = 0.0,
    val interimCpuMulti: Double = 0.0,
    val interimRamBw: Double = 0.0,
    val interimStorageRead: Double = 0.0,
    val interimThreats: Int = 0,
    val currentTempCelsius: Float = 0f
)

object LongScanCoordinator {
    const val TWO_HOURS_MILLIS = 2L * 60L * 60L * 1000L

    private val _progress = MutableStateFlow(LongScanProgress())
    val progress: StateFlow<LongScanProgress> = _progress.asStateFlow()

    fun update(state: LongScanProgress) {
        _progress.value = state
    }

    fun reset() {
        _progress.value = LongScanProgress(isRunning = false)
    }

    fun startLongScan(context: Context) {
        val intent = Intent(context, LongScanService::class.java).apply {
            action = LongScanService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelLongScan(context: Context) {
        val intent = Intent(context, LongScanService::class.java).apply {
            action = LongScanService.ACTION_CANCEL
        }
        context.startService(intent)
        WorkManager.getInstance(context).cancelUniqueWork("benchy_long_scan_watchdog")
    }
}

class LongScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        LongScanCoordinator.startLongScan(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<LongScanWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "benchy_long_scan_watchdog",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
