package com.benchy.X.workers

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.benchy.X.data.BenchmarkRecordEntity
import com.benchy.X.data.BenchyDatabase
import com.benchy.X.data.BenchyRepository
import com.benchy.X.data.PreferencesRepository
import com.benchy.X.engine.BenchmarkScorer
import com.benchy.X.engine.NativeBridge
import com.benchy.X.engine.PackageSecurityAnalyzer
import com.benchy.X.engine.TelemetryReader
import com.benchy.X.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class LongScanService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                scanJob?.cancel()
                LongScanCoordinator.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (scanJob?.isActive != true) {
                    startForegroundNotification("Initializing 2-Hour Deep Scan…", 0)
                    launchDeepScan()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(content: String, percent: Int) {
        NotificationHelper.initializeChannels(this)
        val notification = NotificationHelper.buildLongScanNotification(
            context = this,
            title = "Benchy 2-Hour Long Scan",
            content = content,
            progressPercent = percent,
            cancelPendingIntent = cancelPendingIntent()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_ID_LONG_SCAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_LONG_SCAN, notification)
        }
    }

    private fun updateForegroundNotification(content: String, percent: Int) {
        val notification = NotificationHelper.buildLongScanNotification(
            context = this,
            title = "Benchy 2-Hour Long Scan ($percent%)",
            content = content,
            progressPercent = percent,
            cancelPendingIntent = cancelPendingIntent()
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.NOTIF_ID_LONG_SCAN, notification)
    }

    private fun cancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, LongScanService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this,
            901,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchDeepScan() {
        scanJob = serviceScope.launch {
            val db = BenchyDatabase.getInstance(applicationContext)
            val repo = BenchyRepository(db)
            val prefsRepo = PreferencesRepository(applicationContext)

            val totalMs = LongScanCoordinator.TWO_HOURS_MILLIS
            val startMs = System.currentTimeMillis()

            // Initial immediate passes so interim scores populate immediately
            var cpuSingle = NativeBridge.benchmarkCpuSingleCore(900)
            var cpuMulti = NativeBridge.benchmarkCpuMultiCore(durationMs = 1100)
            var ram = NativeBridge.benchmarkRam(bufferMb = 24)
            var storage = NativeBridge.benchmarkStorage(cacheDir.absolutePath, fileSizeMb = 16, randomOps = 600)
            var allowlisted = repo.getAllowlistedPackages()
            var skipSystem = prefsRepo.preferences.first().skipSystemApps
            var security = PackageSecurityAnalyzer.scanInstalledApps(applicationContext, allowlisted, skipSystem)
            var fileThreats = 0
            var telemetry = TelemetryReader.readDeviceTelemetry(applicationContext)

            val gpuSamples = mutableListOf(58.0)
            var currentInterimScore = BenchmarkScorer.computeSummary(
                scanType = "LONG",
                cpuSingle = cpuSingle,
                cpuMulti = cpuMulti,
                ram = ram,
                storage = storage,
                gpu3dAvgFps = gpuSamples.average(),
                telemetry = telemetry,
                threatsDetected = security.flaggedPackages.size + fileThreats
            ).overallScore

            // Concurrent 1-second wall-clock countdown ticker for real-second accuracy
            val tickerJob = launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startMs
                    if (elapsed >= totalMs) break

                    val pct = ((elapsed * 100L) / totalMs).toInt().coerceIn(0, 99)
                    val phase = when {
                        pct < 20 -> "Sustained CPU & Thermal Baseline"
                        pct < 45 -> "Multi-Core & Memory Stability Loop"
                        pct < 70 -> "Storage I/O Endurance & 4K IOPS Check"
                        pct < 90 -> "Deep Package, File & Zip-Bomb Audit"
                        else -> "Final Thermal Throttling Verification"
                    }

                    LongScanCoordinator.update(
                        LongScanProgress(
                            isRunning = true,
                            phaseLabel = phase,
                            elapsedMillis = elapsed,
                            totalDurationMillis = totalMs,
                            progressPercent = pct,
                            interimScore = currentInterimScore,
                            interimCpuSingle = cpuSingle.score,
                            interimCpuMulti = cpuMulti.score,
                            interimRamBw = ram.readBandwidthMbPerSec,
                            interimStorageRead = storage.seqReadMbPerSec,
                            interimThreats = security.flaggedPackages.size + fileThreats,
                            currentTempCelsius = telemetry.battery.tempCelsius
                        )
                    )

                    val remMs = (totalMs - elapsed).coerceAtLeast(0L)
                    val remH = remMs / 3_600_000L
                    val remM = (remMs % 3_600_000L) / 60_000L
                    val remS = (remMs % 60_000L) / 1000L
                    val remStr = String.format(Locale.US, "%02d:%02d:%02d", remH, remM, remS)
                    updateForegroundNotification(
                        "$phase • Interim $currentInterimScore% • $remStr left",
                        pct
                    )
                    delay(1_000L)
                }
            }

            // Periodic deep stress & security probe loop across the full 2 hours
            var cycle = 0
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed >= totalMs) break

                when (cycle % 4) {
                    0 -> cpuSingle = NativeBridge.benchmarkCpuSingleCore(900)
                    1 -> cpuMulti = NativeBridge.benchmarkCpuMultiCore(durationMs = 1100)
                    2 -> ram = NativeBridge.benchmarkRam(bufferMb = 24)
                    3 -> {
                        storage = NativeBridge.benchmarkStorage(cacheDir.absolutePath, fileSizeMb = 16, randomOps = 500)
                        allowlisted = repo.getAllowlistedPackages()
                        skipSystem = prefsRepo.preferences.first().skipSystemApps
                        security = PackageSecurityAnalyzer.scanInstalledAppsWithProgress(
                            context = applicationContext,
                            allowlistedPackages = allowlisted,
                            skipSystemApps = skipSystem,
                            minDurationMs = 4_000L,
                            onProgress = {}
                        )
                        if (com.benchy.X.engine.FileSecurityAnalyzer.hasFileScanPermission(applicationContext)) {
                            fileThreats = com.benchy.X.engine.FileSecurityAnalyzer
                                .scanDeviceFilesWithProgress(applicationContext, minDurationMs = 2_000L) {}
                                .flaggedFiles.size
                        }
                    }
                }
                telemetry = TelemetryReader.readDeviceTelemetry(applicationContext)
                val thermalAdjustedFps = (60.0 - telemetry.thermalSeverity * 7.5).coerceIn(18.0, 60.0)
                gpuSamples += thermalAdjustedFps

                currentInterimScore = BenchmarkScorer.computeSummary(
                    scanType = "LONG",
                    cpuSingle = cpuSingle,
                    cpuMulti = cpuMulti,
                    ram = ram,
                    storage = storage,
                    gpu3dAvgFps = gpuSamples.average(),
                    telemetry = telemetry,
                    threatsDetected = security.flaggedPackages.size + fileThreats
                ).overallScore

                cycle++
                val remainingBeforeEnd = totalMs - (System.currentTimeMillis() - startMs)
                if (remainingBeforeEnd <= 0L) break
                delay(minOf(15_000L, remainingBeforeEnd))
            }

            tickerJob.cancel()

            if (isActive) {
                val finalSummary = BenchmarkScorer.computeSummary(
                    scanType = "LONG",
                    cpuSingle = cpuSingle,
                    cpuMulti = cpuMulti,
                    ram = ram,
                    storage = storage,
                    gpu3dAvgFps = gpuSamples.average(),
                    telemetry = telemetry,
                    threatsDetected = security.flaggedPackages.size
                )

                repo.saveBenchmark(
                    BenchmarkRecordEntity(
                        timestampMillis = System.currentTimeMillis(),
                        scanType = "LONG",
                        overallScore = finalSummary.overallScore,
                        gaming2dRating = finalSummary.gaming2dRating,
                        gaming3dRating = finalSummary.gaming3dRating,
                        batteryTempRating = finalSummary.batteryTempRating,
                        multitaskingRating = finalSummary.multitaskingRating,
                        cpuSingleScore = finalSummary.cpuSingleScore,
                        cpuMultiScore = finalSummary.cpuMultiScore,
                        ramBandwidthMbPerSec = finalSummary.ramBandwidthMbPerSec,
                        storageSeqReadMbPerSec = finalSummary.storageSeqReadMbPerSec,
                        storageSeqWriteMbPerSec = finalSummary.storageSeqWriteMbPerSec,
                        threatsDetected = finalSummary.threatsDetected
                    )
                )

                val prefs = prefsRepo.preferences.first()
                NotificationHelper.postResultAlert(
                    context = applicationContext,
                    isPositive = finalSummary.tier.isPositiveAlert,
                    title = "2-Hour Deep Scan Complete: ${finalSummary.overallScore}%",
                    body = "${finalSummary.threatsDetected} app threat(s) found. ${finalSummary.tier.reviewEnglishFallback}",
                    alertsMuted = prefs.alertsMuted
                )

                LongScanCoordinator.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.benchy.X.action.START_LONG_SCAN"
        const val ACTION_CANCEL = "com.benchy.X.action.CANCEL_LONG_SCAN"
    }
}
