package com.benchy.X.workers

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.benchy.X.data.BatterySampleEntity
import com.benchy.X.data.BenchyDatabase
import com.benchy.X.data.BenchyRepository
import com.benchy.X.data.PreferencesRepository
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

class BatteryTrackerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackerJob: Job? = null
    private var lastRecordedLevel = -1
    private var activeStartMillis = 0L

    private val batteryChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val snap = TelemetryReader.readBattery(this@BatteryTrackerService, intent)
            if (snap.levelPercent != lastRecordedLevel && activeStartMillis > 0L) {
                lastRecordedLevel = snap.levelPercent
                serviceScope.launch {
                    recordBatterySample(snap, activeStartMillis)
                }
            }
        }
    }
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                unregisterBatteryReceiver()
                serviceScope.launch {
                    PreferencesRepository(applicationContext).setBatteryTrackerState(false, 0L)
                }
                trackerJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REPOST_NOTIFICATION -> {
                // Immediately re-pin the persistent notification if Android 14+ SystemUI ever dismissed it
                postForegroundNotification()
                serviceScope.launch {
                    val prefs = PreferencesRepository(applicationContext).preferences.first()
                    if (prefs.batteryTrackerStartMillis > 0L) {
                        activeStartMillis = prefs.batteryTrackerStartMillis
                        val elapsedDays = ((System.currentTimeMillis() - activeStartMillis)
                            .coerceAtLeast(0L).toFloat() / (24f * 60f * 60f * 1000f)).coerceIn(0f, 7f)
                        postForegroundNotification(elapsedDays = elapsedDays)
                    }
                }
                return START_STICKY
            }
            else -> {
                registerBatteryReceiver()
                startForegroundTelemetry()
                return START_STICKY
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (!receiverRegistered) {
            runCatching {
                registerReceiver(batteryChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                receiverRegistered = true
            }
        }
    }

    private fun unregisterBatteryReceiver() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(batteryChangeReceiver) }
            receiverRegistered = false
        }
    }

    private fun postForegroundNotification(elapsedDays: Float? = null, levelPercent: Int? = null) {
        NotificationHelper.initializeChannels(this)
        val snap = TelemetryReader.readBattery(this)
        val computedDays = elapsedDays ?: if (activeStartMillis > 0L) {
            ((System.currentTimeMillis() - activeStartMillis).coerceAtLeast(0L).toFloat() / (24f * 60f * 60f * 1000f))
                .coerceIn(0f, 7f)
        } else {
            0f
        }
        val dayFormatted = String.format(Locale.US, "%.2f", computedDays)
        val pct = levelPercent ?: snap.levelPercent
        val chargeTag = if (snap.isCharging) "Charging" else "Discharging"
        val notif = NotificationHelper.buildBatteryTrackerNotification(
            context = this,
            title = "1-Week Battery Tracker • Day $dayFormatted / 7 ($pct%)",
            content = "${snap.voltageMv} mV • ${snap.currentMa} mA • ${String.format(Locale.US, "%.1f°C", snap.tempCelsius)} • $chargeTag",
            stopPendingIntent = stopPendingIntent(),
            repostPendingIntent = repostPendingIntent()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIF_ID_BATTERY_TRACKER,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_BATTERY_TRACKER, notif)
        }
    }

    private fun startForegroundTelemetry() {
        postForegroundNotification()

        if (trackerJob?.isActive == true) return
        trackerJob = serviceScope.launch {
            val prefsRepo = PreferencesRepository(applicationContext)
            val existingPrefs = prefsRepo.preferences.first()
            val startMillis = if (existingPrefs.batteryTrackerStartMillis > 0L) {
                existingPrefs.batteryTrackerStartMillis
            } else {
                System.currentTimeMillis().also {
                    prefsRepo.setBatteryTrackerState(true, it)
                }
            }
            activeStartMillis = startMillis
            if (!existingPrefs.batteryTrackerActive) {
                prefsRepo.setBatteryTrackerState(true, startMillis)
            }

            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsedMs = (now - startMillis).coerceAtLeast(0L)
                val elapsedDays = (elapsedMs.toFloat() / (24f * 60f * 60f * 1000f)).coerceIn(0f, 7f)
                val battery = TelemetryReader.readBattery(applicationContext)
                lastRecordedLevel = battery.levelPercent

                recordBatterySample(battery, startMillis)

                val dayFormatted = String.format(Locale.US, "%.2f", elapsedDays)
                val chargeTag = if (battery.isCharging) "Charging" else "Discharging"
                val updatedNotif = NotificationHelper.buildBatteryTrackerNotification(
                    context = applicationContext,
                    title = "1-Week Battery Tracker • Day $dayFormatted / 7 (${battery.levelPercent}%)",
                    content = "${battery.voltageMv} mV • ${battery.currentMa} mA • ${String.format(Locale.US, "%.1f°C", battery.tempCelsius)} • $chargeTag",
                    stopPendingIntent = stopPendingIntent(),
                    repostPendingIntent = repostPendingIntent()
                )
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.NOTIF_ID_BATTERY_TRACKER, updatedNotif)

                if (elapsedMs >= sevenDaysMs) {
                    val prefs = prefsRepo.preferences.first()
                    NotificationHelper.postResultAlert(
                        context = applicationContext,
                        isPositive = true,
                        title = "7-Day Battery Health Test Complete",
                        body = "1-week voltage, current, and thermal logs are ready to export in Techy Suite.",
                        alertsMuted = prefs.alertsMuted
                    )
                    prefsRepo.setBatteryTrackerState(false, startMillis)
                    unregisterBatteryReceiver()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                // Sample every 15 seconds for accurate multi-day drain curves & capacity estimation
                delay(15_000L)
            }
        }
    }

    private suspend fun recordBatterySample(
        battery: com.benchy.X.engine.BatterySnapshot,
        startMillis: Long
    ) {
        val now = System.currentTimeMillis()
        val elapsedMs = (now - startMillis).coerceAtLeast(0L)
        val elapsedDays = (elapsedMs.toFloat() / (24f * 60f * 60f * 1000f)).coerceIn(0f, 7f)
        val repo = BenchyRepository(BenchyDatabase.getInstance(applicationContext))
        repo.insertBatterySample(
            BatterySampleEntity(
                timestampMillis = now,
                voltageMv = battery.voltageMv,
                currentMa = battery.currentMa,
                tempCelsius = battery.tempCelsius,
                levelPercent = battery.levelPercent,
                elapsedDays = elapsedDays,
                isCharging = battery.isCharging,
                chargeCounterUah = battery.chargeCounterUah,
                powerMw = battery.powerMw
            )
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, BatteryTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            902,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun repostPendingIntent(): PendingIntent {
        val repostIntent = Intent(this, BatteryTrackerService::class.java).apply {
            action = ACTION_REPOST_NOTIFICATION
        }
        return PendingIntent.getService(
            this,
            903,
            repostIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        unregisterBatteryReceiver()
        trackerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.benchy.X.action.START_BATTERY_TRACKER"
        const val ACTION_STOP = "com.benchy.X.action.STOP_BATTERY_TRACKER"
        const val ACTION_REPOST_NOTIFICATION = "com.benchy.X.action.REPOST_BATTERY_TRACKER"

        fun start(context: Context) {
            val intent = Intent(context, BatteryTrackerService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryTrackerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
