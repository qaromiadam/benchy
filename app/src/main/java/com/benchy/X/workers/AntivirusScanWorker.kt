package com.benchy.X.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.benchy.X.data.BenchyDatabase
import com.benchy.X.data.BenchyRepository
import com.benchy.X.data.PreferencesRepository
import com.benchy.X.engine.PackageSecurityAnalyzer
import com.benchy.X.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AntivirusScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = BenchyDatabase.getInstance(applicationContext)
        val repository = BenchyRepository(db)
        val prefs = PreferencesRepository(applicationContext).preferences.first()

        val allowlisted = repository.getAllowlistedPackages()
        val scanResult = PackageSecurityAnalyzer.scanInstalledAppsWithProgress(
            context = applicationContext,
            allowlistedPackages = allowlisted,
            skipSystemApps = prefs.skipSystemApps,
            minDurationMs = 10_000L,
            onProgress = { /* background sweep */ }
        )
        val fileResult = if (com.benchy.X.engine.FileSecurityAnalyzer.hasFileScanPermission(applicationContext)) {
            com.benchy.X.engine.FileSecurityAnalyzer.scanDeviceFilesWithProgress(
                context = applicationContext,
                minDurationMs = 4_000L,
                onProgress = {}
            )
        } else {
            null
        }
        val pkgThreats = scanResult.flaggedPackages.size
        val fileThreats = fileResult?.flaggedFiles?.size ?: 0
        val totalThreats = pkgThreats + fileThreats

        if (totalThreats == 0) {
            val fileNote = if (fileResult != null) " and ${fileResult.totalFilesInspected} files" else ""
            NotificationHelper.postResultAlert(
                context = applicationContext,
                isPositive = true,
                title = "Benchy Auto-Scan: All Clear",
                body = "0 threats detected across ${scanResult.totalPackagesInspected} inspected apps$fileNote.",
                alertsMuted = prefs.alertsMuted,
                notificationId = NotificationHelper.NOTIF_ID_ANTIVIRUS
            )
        } else {
            NotificationHelper.postResultAlert(
                context = applicationContext,
                isPositive = false,
                title = "Benchy Security Alert: $totalThreats Risk(s)",
                body = "$pkgThreats app risk(s) and $fileThreats file/zip-bomb risk(s) found. Open Benchy Security to review.",
                alertsMuted = prefs.alertsMuted,
                notificationId = NotificationHelper.NOTIF_ID_ANTIVIRUS
            )
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "benchy_periodic_antivirus"

        fun schedule(context: Context, intervalHours: Int) {
            val hours = intervalHours.coerceIn(1, 720).toLong()
            val request = PeriodicWorkRequestBuilder<AntivirusScanWorker>(hours, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
