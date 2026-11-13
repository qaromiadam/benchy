package com.benchy.X.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.benchy.X.MainActivity
import com.benchy.X.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

data class NotificationAudioDiagnostic(
    val permissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val channelImportanceOk: Boolean,
    val channelHasSound: Boolean,
    val notifStreamVolume: Int,
    val notifStreamMax: Int,
    val ringerNormal: Boolean,
    val dndActive: Boolean
)

object NotificationHelper {

    const val CHANNEL_GOOD_ALERTS = "benchy_good_alerts_v6"
    const val CHANNEL_BAD_ALERTS = "benchy_bad_alerts_v6"
    const val CHANNEL_SCAN_PROGRESS = "benchy_scan_progress"
    const val CHANNEL_BATTERY_TRACKER = "benchy_battery_tracker"

    const val NOTIF_ID_ALERT = 1001
    const val NOTIF_ID_ANTIVIRUS = 1002
    private const val NOTIF_ID_TEST_BASE = 1100
    const val NOTIF_ID_LONG_SCAN = 2001
    const val NOTIF_ID_BATTERY_TRACKER = 2002

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val testAlertSeq = AtomicInteger(0)

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var soundPool: SoundPool? = null

    @Volatile
    private var poolGoodId: Int = 0

    @Volatile
    private var poolBadId: Int = 0

    @Volatile
    private var poolGoodLoaded: Boolean = false

    @Volatile
    private var poolBadLoaded: Boolean = false

    @Volatile
    private var cachedGoodPcm: ShortArray? = null

    @Volatile
    private var cachedBadPcm: ShortArray? = null

    fun getNotificationSoundUri(context: Context, isPositive: Boolean): Uri {
        val rawResId = if (isPositive) R.raw.ding_ding else R.raw.dong_dong
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$rawResId")
    }

    fun initializeChannels(context: Context) {
        val appCtx = context.applicationContext
        val manager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Delete legacy channel versions once if present so v6 48kHz stereo Ogg channels apply cleanly
        runCatching {
            val legacyIds = listOf(
                "benchy_good_alerts", "benchy_bad_alerts",
                "benchy_good_alerts_v2", "benchy_bad_alerts_v2",
                "benchy_good_alerts_v3", "benchy_bad_alerts_v3",
                "benchy_good_alerts_v4", "benchy_bad_alerts_v4",
                "benchy_good_alerts_v5", "benchy_bad_alerts_v5"
            )
            legacyIds.forEach { oldId ->
                if (manager.getNotificationChannel(oldId) != null) {
                    manager.deleteNotificationChannel(oldId)
                }
            }
        }

        val needsGood = manager.getNotificationChannel(CHANNEL_GOOD_ALERTS) == null
        val needsBad = manager.getNotificationChannel(CHANNEL_BAD_ALERTS) == null
        val needsScan = manager.getNotificationChannel(CHANNEL_SCAN_PROGRESS) == null
        val needsBattery = manager.getNotificationChannel(CHANNEL_BATTERY_TRACKER) == null

        if (needsGood || needsBad || needsScan || needsBattery) {
            val goodUri = getNotificationSoundUri(appCtx, isPositive = true)
            val badUri = getNotificationSoundUri(appCtx, isPositive = false)

            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val toCreate = mutableListOf<NotificationChannel>()

            if (needsGood) {
                toCreate += NotificationChannel(
                    CHANNEL_GOOD_ALERTS,
                    "Benchy Positive Alerts (Ding-Ding)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Positive benchmark scores (>50%), healthy diagnostics, and 0-threat antivirus results."
                    setSound(goodUri, audioAttrs)
                    enableVibration(false)
                }
            }

            if (needsBad) {
                toCreate += NotificationChannel(
                    CHANNEL_BAD_ALERTS,
                    "Benchy Warning Alerts (Dong-Dong)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Low performance scores (<=50%), thermal warnings, and suspicious app alerts."
                    setSound(badUri, audioAttrs)
                    enableVibration(true)
                }
            }

            if (needsScan) {
                toCreate += NotificationChannel(
                    CHANNEL_SCAN_PROGRESS,
                    "Benchy 2-Hour Long Scan",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live progress notification for active 2-hour hardware & security scans."
                    setSound(null, null)
                }
            }

            if (needsBattery) {
                toCreate += NotificationChannel(
                    CHANNEL_BATTERY_TRACKER,
                    "Benchy 1-Week Battery Tracker",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Persistent unswippable live telemetry for 7-day battery voltage, current, and temperature logging."
                    setSound(null, null)
                    enableVibration(false)
                }
            }

            if (toCreate.isNotEmpty()) {
                manager.createNotificationChannels(toCreate)
            }
        }

        preloadInAppSoundPool(appCtx)
    }

    private fun preloadInAppSoundPool(context: Context) {
        if (soundPool == null) {
            synchronized(this) {
                if (soundPool == null) {
                    runCatching {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        val pool = SoundPool.Builder()
                            .setMaxStreams(2)
                            .setAudioAttributes(attrs)
                            .build()
                        pool.setOnLoadCompleteListener { _, sampleId, status ->
                            if (status == 0) {
                                if (sampleId == poolGoodId) poolGoodLoaded = true
                                if (sampleId == poolBadId) poolBadLoaded = true
                            }
                        }
                        poolGoodId = pool.load(context, R.raw.ding_ding, 1)
                        poolBadId = pool.load(context, R.raw.dong_dong, 1)
                        soundPool = pool
                    }
                }
            }
        }

        // Pre-warm fallback PCM buffers asynchronously so first tap has zero synthesis delay
        if (cachedGoodPcm == null || cachedBadPcm == null) {
            audioScope.launch {
                if (cachedGoodPcm == null) {
                    cachedGoodPcm = SoundGenerator.buildPcmSamples(isGood = true)
                }
                if (cachedBadPcm == null) {
                    cachedBadPcm = SoundGenerator.buildPcmSamples(isGood = false)
                }
            }
        }
    }

    /**
     * Zero-latency sound preview for Settings tester using preloaded SoundPool (USAGE_MEDIA),
     * with pre-cached low-latency AudioTrack fallback if SoundPool is still finishing initial load.
     */
    fun playInAppTone(context: Context, isGood: Boolean) {
        val appCtx = context.applicationContext
        preloadInAppSoundPool(appCtx)

        val pool = soundPool
        val sampleId = if (isGood) poolGoodId else poolBadId
        val loaded = if (isGood) poolGoodLoaded else poolBadLoaded
        if (pool != null && loaded && sampleId != 0) {
            val streamId = runCatching { pool.play(sampleId, 1f, 1f, 1, 0, 1f) }.getOrDefault(0)
            if (streamId != 0) return
        }

        // Instant cached PCM AudioTrack fallback
        audioScope.launch {
            runCatching {
                activeTrack?.let { old ->
                    runCatching {
                        old.stop()
                        old.release()
                    }
                }
                val pcm = if (isGood) {
                    cachedGoodPcm ?: SoundGenerator.buildPcmSamples(true).also { cachedGoodPcm = it }
                } else {
                    cachedBadPcm ?: SoundGenerator.buildPcmSamples(false).also { cachedBadPcm = it }
                }
                val minBytes = pcm.size * 2
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SoundGenerator.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                val track = builder.build()

                activeTrack = track
                track.write(pcm, 0, pcm.size)
                track.setVolume(AudioTrack.getMaxVolume())
                track.play()

                val durationMs = ((pcm.size * 1000L) / SoundGenerator.SAMPLE_RATE) + 180L
                delay(durationMs)
                if (activeTrack === track) {
                    activeTrack = null
                }
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun raiseNotificationStreamVolume(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
            val target = (max * 0.8f).toInt().coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, target, AudioManager.FLAG_SHOW_UI)
        }.onFailure {
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }
    }

    fun inspectNotificationAudioState(context: Context): NotificationAudioDiagnostic {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val vol = am?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 1
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) ?: 7
        val ringerNormal = (am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL) == AudioManager.RINGER_MODE_NORMAL
        val enabled = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)

        val goodCh = runCatching { nm?.getNotificationChannel(CHANNEL_GOOD_ALERTS) }.getOrNull()
        val chImpOk = goodCh == null || goodCh.importance >= NotificationManager.IMPORTANCE_DEFAULT
        val chHasSound = goodCh == null || goodCh.sound != null
        val dndActive = runCatching {
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)

        return NotificationAudioDiagnostic(
            permissionGranted = canPostNotifications(context),
            notificationsEnabled = enabled,
            channelImportanceOk = chImpOk,
            channelHasSound = chHasSound,
            notifStreamVolume = vol,
            notifStreamMax = maxVol,
            ringerNormal = ringerNormal,
            dndActive = dndActive
        )
    }

    /**
     * Posts a real OS notification whose v6 channel is bound to bundled 48kHz stereo Ogg Vorbis
     * raw resources (R.raw.ding_ding / R.raw.dong_dong). Played natively by Android SystemUI
     * even when Benchy is closed.
     */
    fun postResultAlert(
        context: Context,
        isPositive: Boolean,
        title: String,
        body: String,
        alertsMuted: Boolean,
        notificationId: Int = NOTIF_ID_ALERT,
        useUniqueTestId: Boolean = false
    ) {
        initializeChannels(context)
        if (!canPostNotifications(context)) return

        val channelId = if (isPositive) CHANNEL_GOOD_ALERTS else CHANNEL_BAD_ALERTS
        val effectiveId = if (useUniqueTestId) {
            NOTIF_ID_TEST_BASE + (testAlertSeq.getAndIncrement() % 10)
        } else {
            notificationId
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            effectiveId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingTap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setSilent(alertsMuted)

        runCatching {
            val nm = NotificationManagerCompat.from(context)
            if (!useUniqueTestId) {
                nm.cancel(effectiveId)
            }
            nm.notify(effectiveId, builder.build())
        }
    }

    fun buildLongScanNotification(
        context: Context,
        title: String,
        content: String,
        progressPercent: Int,
        cancelPendingIntent: PendingIntent
    ): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            NOTIF_ID_LONG_SCAN,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_SCAN_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setContentIntent(pendingTap)
            .addAction(0, context.getString(R.string.action_cancel), cancelPendingIntent)
            .build()
        notif.flags = notif.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return notif
    }

    fun buildBatteryTrackerNotification(
        context: Context,
        title: String,
        content: String,
        stopPendingIntent: PendingIntent,
        repostPendingIntent: PendingIntent? = null
    ): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            NOTIF_ID_BATTERY_TRACKER,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_BATTERY_TRACKER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingTap)
            .addAction(0, context.getString(R.string.action_stop), stopPendingIntent)

        if (repostPendingIntent != null) {
            builder.setDeleteIntent(repostPendingIntent)
        }

        val notif = builder.build()
        notif.flags = notif.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return notif
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
