package com.benchy.X.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.benchy.X.MainActivity
import com.benchy.X.R

object NotificationHelper {

    const val FILE_PROVIDER_AUTHORITY = "com.benchy.X.fileprovider"
    const val CHANNEL_GOOD_ALERTS = "benchy_good_alerts"
    const val CHANNEL_BAD_ALERTS = "benchy_bad_alerts"
    const val CHANNEL_SCAN_PROGRESS = "benchy_scan_progress"
    const val CHANNEL_BATTERY_TRACKER = "benchy_battery_tracker"

    const val NOTIF_ID_ALERT = 1001
    const val NOTIF_ID_ANTIVIRUS = 1002
    const val NOTIF_ID_LONG_SCAN = 2001
    const val NOTIF_ID_BATTERY_TRACKER = 2002

    fun initializeChannels(context: Context) {
        val (goodFile, badFile) = SoundGenerator.ensureSoundFiles(context)
        val goodUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, goodFile)
        val badUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, badFile)

        grantSystemUiUriRead(context, goodUri)
        grantSystemUiUriRead(context, badUri)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val goodChannel = NotificationChannel(
            CHANNEL_GOOD_ALERTS,
            "Benchy Positive Alerts (Ding-Ding)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Positive benchmark scores (>50%), healthy diagnostics, and 0-threat antivirus results."
            setSound(goodUri, audioAttrs)
            enableVibration(false)
        }

        val badChannel = NotificationChannel(
            CHANNEL_BAD_ALERTS,
            "Benchy Warning Alerts (Dong-Dong)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Low performance scores (<=50%), thermal warnings, and suspicious app alerts."
            setSound(badUri, audioAttrs)
            enableVibration(true)
        }

        val scanChannel = NotificationChannel(
            CHANNEL_SCAN_PROGRESS,
            "Benchy 2-Hour Long Scan",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live progress notification for active 2-hour hardware & security scans."
            setSound(null, null)
        }

        val batteryChannel = NotificationChannel(
            CHANNEL_BATTERY_TRACKER,
            "Benchy 1-Week Battery Tracker",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Persistent unswippable live telemetry for 7-day battery voltage, current, and temperature logging."
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(
            listOf(goodChannel, badChannel, scanChannel, batteryChannel)
        )
    }

    private fun grantSystemUiUriRead(context: Context, uri: Uri) {
        runCatching {
            context.grantUriPermission(
                "com.android.systemui",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun playInAppTone(context: Context, isGood: Boolean) {
        val (goodFile, badFile) = SoundGenerator.ensureSoundFiles(context)
        val target = if (isGood) goodFile else badFile
        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(target.absolutePath)
                setOnCompletionListener { mp -> mp.release() }
                prepare()
                start()
            }
            // Release safely if anything throws later
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
        }
    }

    fun postResultAlert(
        context: Context,
        isPositive: Boolean,
        title: String,
        body: String,
        alertsMuted: Boolean,
        notificationId: Int = NOTIF_ID_ALERT
    ) {
        initializeChannels(context)
        if (!alertsMuted) {
            playInAppTone(context, isPositive)
        }

        if (!canPostNotifications(context)) return

        val channelId = if (isPositive) CHANNEL_GOOD_ALERTS else CHANNEL_BAD_ALERTS
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingTap)
            .setAutoCancel(true)

        if (alertsMuted) {
            builder.setSilent(true)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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
