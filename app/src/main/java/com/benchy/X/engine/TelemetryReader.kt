package com.benchy.X.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import com.benchy.X.data.StorageUnit
import com.benchy.X.data.TempUnit
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

data class BatterySnapshot(
    val levelPercent: Int,
    val voltageMv: Int,
    val currentMa: Int,
    val tempCelsius: Float,
    val isCharging: Boolean,
    val healthLabel: String,
    val chargeCounterUah: Int = 0,
    val powerMw: Float = 0f,
    val pluggedSource: String = "Battery"
)

data class MemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val zramDetected: Boolean,
    val buffersKb: Long = 0L,
    val cachedKb: Long = 0L
)

data class StorageSnapshot(
    val totalBytes: Long,
    val freeBytes: Long,
    val blockSizeBytes: Long = 4096L
)

data class ThermalZoneReading(
    val name: String,
    val tempCelsius: Float
)

data class HardwareSpecs(
    val abis: String,
    val kernelVersion: String,
    val securityPatch: String,
    val androidVersion: String,
    val board: String,
    val refreshRateHz: Float,
    val screenResolution: String,
    val glesVersion: String
)

data class SensorSummaryItem(
    val name: String,
    val vendor: String,
    val type: Int,
    val powerMa: Float
)

data class DeviceTelemetry(
    val socName: String,
    val deviceModel: String,
    val cpuCores: Int,
    val perCoreMhz: List<Pair<Int, Int>>,
    val thermalStatusLabel: String,
    val thermalSeverity: Int,
    val thermalHeadroom: Float?,
    val thermalZones: List<ThermalZoneReading>,
    val battery: BatterySnapshot,
    val memory: MemorySnapshot,
    val storage: StorageSnapshot
)

object TelemetryReader {

    fun readBattery(context: Context, broadcastIntent: Intent? = null): BatterySnapshot {
        val intent = broadcastIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else 50
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3900) ?: 3900
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempC = tempTenths / 10f
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Discharging"
        }

        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            ?: BatteryManager.BATTERY_HEALTH_GOOD
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawCurrentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMa = if (kotlin.math.abs(rawCurrentUa) > 10_000) rawCurrentUa / 1000 else rawCurrentUa
        val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 } ?: 0
        val powerMw = (voltageMv / 1000f) * kotlin.math.abs(currentMa)

        return BatterySnapshot(
            levelPercent = pct,
            voltageMv = voltageMv,
            currentMa = currentMa,
            tempCelsius = tempC,
            isCharging = isCharging,
            healthLabel = healthLabel,
            chargeCounterUah = chargeCounterUah,
            powerMw = powerMw,
            pluggedSource = pluggedSource
        )
    }

    fun readMemory(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        var swapTotalKb = 0L
        var swapFreeKb = 0L
        var buffersKb = 0L
        var cachedKb = 0L
        runCatching {
            File("/proc/meminfo").useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("SwapTotal:") -> swapTotalKb = parseMeminfoKb(line)
                        line.startsWith("SwapFree:") -> swapFreeKb = parseMeminfoKb(line)
                        line.startsWith("Buffers:") -> buffersKb = parseMeminfoKb(line)
                        line.startsWith("Cached:") -> cachedKb = parseMeminfoKb(line)
                    }
                }
            }
        }
        val zramDetected = runCatching {
            File("/proc/swaps").readText().contains("zram", ignoreCase = true)
        }.getOrDefault(false)

        return MemorySnapshot(
            totalRamBytes = info.totalMem,
            availableRamBytes = info.availMem,
            lowMemory = info.lowMemory,
            swapTotalKb = swapTotalKb,
            swapFreeKb = swapFreeKb,
            zramDetected = zramDetected,
            buffersKb = buffersKb,
            cachedKb = cachedKb
        )
    }

    fun readStorage(): StorageSnapshot {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSnapshot(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            blockSizeBytes = stat.blockSizeLong
        )
    }

    fun readThermalZones(): List<ThermalZoneReading> {
        val zones = mutableListOf<ThermalZoneReading>()
        for (i in 0..31) {
            val base = File("/sys/class/thermal/thermal_zone$i")
            if (!base.exists()) continue
            val typeStr = runCatching { File(base, "type").readText().trim() }.getOrNull() ?: continue
            val rawTemp = runCatching { File(base, "temp").readText().trim().toFloatOrNull() }.getOrNull() ?: continue
            val celsius = if (rawTemp > 200f) rawTemp / 1000f else rawTemp
            if (celsius in -20f..135f) {
                zones += ThermalZoneReading(name = typeStr, tempCelsius = celsius)
            }
            if (zones.size >= 10) break
        }
        return zones
    }

    fun readHardwareSpecs(context: Context): HardwareSpecs {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val refreshHz = mode?.refreshRate ?: 60f
        val resolution = if (mode != null) "${mode.physicalWidth} × ${mode.physicalHeight}" else "Unknown"

        val kernel = runCatching {
            File("/proc/version").readText().trim().substringBefore(" (").take(64)
        }.getOrElse { System.getProperty("os.version") ?: "Linux" }

        return HardwareSpecs(
            abis = Build.SUPPORTED_ABIS.joinToString(", "),
            kernelVersion = kernel,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            board = "${Build.BOARD} / ${Build.HARDWARE}",
            refreshRateHz = refreshHz,
            screenResolution = resolution,
            glesVersion = am.deviceConfigurationInfo.glEsVersion
        )
    }

    fun listDeviceSensors(context: Context): List<SensorSummaryItem> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        return sm.getSensorList(Sensor.TYPE_ALL)
            .distinctBy { it.name }
            .take(18)
            .map {
                SensorSummaryItem(
                    name = it.name,
                    vendor = it.vendor,
                    type = it.type,
                    powerMa = it.power
                )
            }
    }

    fun readDeviceTelemetry(context: Context): DeviceTelemetry {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalSeverity = pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        val thermalLabel = when (thermalSeverity) {
            PowerManager.THERMAL_STATUS_NONE -> "Cool"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Load"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe Heat"
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical Throttle"
            else -> "Nominal"
        }
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm?.getThermalHeadroom(10)?.takeIf { !it.isNaN() } }.getOrNull()
        } else {
            null
        }

        val socName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOfNotNull(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() && it != "unknown" }
                .joinToString(" ")
                .ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

        return DeviceTelemetry(
            socName = socName,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            perCoreMhz = NativeBridge.getPerCoreFrequenciesMhz(),
            thermalStatusLabel = thermalLabel,
            thermalSeverity = thermalSeverity,
            thermalHeadroom = headroom,
            thermalZones = readThermalZones(),
            battery = readBattery(context),
            memory = readMemory(context),
            storage = readStorage()
        )
    }

    fun formatTemp(celsius: Float, unit: TempUnit): String {
        return when (unit) {
            TempUnit.CELSIUS -> String.format(Locale.US, "%.1f °C", celsius)
            TempUnit.FAHRENHEIT -> String.format(Locale.US, "%.1f °F", celsius * 9f / 5f + 32f)
        }
    }

    fun formatStorage(bytes: Long, unit: StorageUnit): String {
        return when (unit) {
            StorageUnit.GB -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
            StorageUnit.GIB -> String.format(Locale.US, "%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatRamGb(bytes: Long, unit: StorageUnit): String = formatStorage(bytes, unit)

    private fun parseMeminfoKb(line: String): Long {
        return line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
    }
}
