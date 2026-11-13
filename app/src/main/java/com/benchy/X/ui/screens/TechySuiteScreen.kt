package com.benchy.X.ui.screens

import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.benchy.X.R
import com.benchy.X.data.BatterySampleEntity
import com.benchy.X.data.UserPreferences
import com.benchy.X.engine.BenchyGlStressRenderer
import com.benchy.X.engine.DeviceTelemetry
import com.benchy.X.engine.HardwareSpecs
import com.benchy.X.engine.SensorSummaryItem
import com.benchy.X.engine.TelemetryReader
import com.benchy.X.locale.AutoText
import com.benchy.X.ui.BatteryAnalytics
import com.benchy.X.ui.LiveSensorReadings
import com.benchy.X.ui.TechyBenchmarkUiState
import com.benchy.X.ui.ThrottleTestUiState
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.theme.JetBrainsMonoFontFamily
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics
import com.benchy.X.ui.theme.TierCriticalRed
import com.benchy.X.ui.theme.TierDragOrange
import com.benchy.X.ui.theme.TierPeakGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechySuiteScreen(
    preferences: UserPreferences,
    telemetry: DeviceTelemetry,
    hardwareSpecs: HardwareSpecs,
    deviceSensors: List<SensorSummaryItem>,
    liveSensors: LiveSensorReadings,
    techyState: TechyBenchmarkUiState,
    throttleState: ThrottleTestUiState,
    batterySamples: List<BatterySampleEntity>,
    batteryAnalytics: BatteryAnalytics,
    onRunCpu: () -> Unit,
    onRunRam: () -> Unit,
    onRunStorage: () -> Unit,
    onSetGpuRunning: (Boolean) -> Unit,
    onUpdateGpuStats: (avgFps: Double, onePctLow: Double, dropPct: Double, renderer: String, score2d: Double) -> Unit,
    onToggleThrottleTest: () -> Unit,
    onToggleLiveSensors: (Boolean) -> Unit,
    onStartBatteryTracker: () -> Unit,
    onStopBatteryTracker: () -> Unit,
    onGenerateCsv: suspend () -> String,
    onGenerateJson: suspend () -> String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metrics = LocalBenchyLayoutMetrics.current

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = onGenerateCsv()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                }
                Toast.makeText(context, "Exported CSV battery log", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = onGenerateJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Exported JSON battery log", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2D Canvas draw-call frame counter while GPU test is active
    var canvas2dFps by remember { mutableDoubleStateOf(60.0) }
    var canvasTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(techyState.gpuRunning) {
        if (techyState.gpuRunning) {
            var lastNs = System.nanoTime()
            while (techyState.gpuRunning) {
                delay(16L)
                val now = System.nanoTime()
                val dt = ((now - lastNs) / 1_000_000.0).coerceAtLeast(1.0)
                lastNs = now
                canvas2dFps = (1000.0 / dt).coerceIn(15.0, 120.0)
                canvasTick++
            }
        }
    }

    // Live 1-second clock for accurate 1-Week Battery Tracker elapsed countdown
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(preferences.batteryTrackerActive) {
        while (preferences.batteryTrackerActive) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AutoText(
                        resId = R.string.techy_title,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    AutoText(
                        resId = R.string.techy_subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. CPU Benchmark (Native C++ JNI + /sys/devices/system/cpu)
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_cpu_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = onRunCpu,
                            enabled = !techyState.cpuRunning,
                            modifier = Modifier.testTag("techy_run_cpu_btn")
                        ) {
                            if (techyState.cpuRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                AutoText(resId = R.string.action_run)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${telemetry.cpuCores} Logical Cores • Live Clocks (/sys/devices/system/cpu)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        telemetry.perCoreMhz.forEachIndexed { idx, (curMhz, maxMhz) ->
                            val ratio = if (maxMhz > 0) (curMhz.toFloat() / maxMhz.toFloat()).coerceIn(0.05f, 1f) else 0f
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CPU$idx",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(42.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = if (curMhz > 0) "$curMhz MHz" else "Protected",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(74.dp)
                                )
                            }
                        }
                    }

                    if (techyState.cpuSingle != null && techyState.cpuMulti != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricBox(
                                label = "Single-Core",
                                value = "${techyState.cpuSingle.score.toInt()} pts",
                                sub = "${String.format(Locale.US, "%.0f", techyState.cpuSingle.fpMflops)} MFLOPS • ${String.format(Locale.US, "%.0f", techyState.cpuSingle.intMops)} MOPS",
                                modifier = Modifier.weight(1f)
                            )
                            TechMetricBox(
                                label = "Multi-Core",
                                value = "${techyState.cpuMulti.score.toInt()} pts",
                                sub = "SHA-256 ${String.format(Locale.US, "%.0f", techyState.cpuMulti.totalSha256MbPerSec)} MB/s",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Thermal Throttling Stress Loop (Live Dual-Line Performance vs Temp Chart)
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.TrendingDown, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_throttle_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = onToggleThrottleTest,
                            colors = if (throttleState.isRunning) {
                                ButtonDefaults.buttonColors(containerColor = TierCriticalRed)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.testTag("techy_throttle_btn")
                        ) {
                            AutoText(resId = if (throttleState.isRunning) R.string.action_stop else R.string.action_start)
                        }
                    }

                    if (throttleState.samples.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val linePrimary = MaterialTheme.colorScheme.primary
                        val tempLineColor = TierDragOrange
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(8.dp)
                        ) {
                            val samples = throttleState.samples
                            if (samples.size >= 2) {
                                val maxScore = samples.maxOf { it.multiScore }.coerceAtLeast(1.0)
                                val minScore = (samples.minOf { it.multiScore } * 0.8).coerceAtLeast(0.0)
                                val scoreSpan = (maxScore - minScore).coerceAtLeast(1.0)

                                val perfPath = Path()
                                val tempPath = Path()
                                samples.forEachIndexed { idx, s ->
                                    val x = (idx.toFloat() / (samples.size - 1).coerceAtLeast(1)) * size.width
                                    val scoreNorm = ((s.multiScore - minScore) / scoreSpan).toFloat().coerceIn(0f, 1f)
                                    val yPerf = size.height - (scoreNorm * size.height)
                                    val tempNorm = ((s.tempCelsius - 20f) / 35f).coerceIn(0f, 1f)
                                    val yTemp = size.height - (tempNorm * size.height)
                                    if (idx == 0) {
                                        perfPath.moveTo(x, yPerf)
                                        tempPath.moveTo(x, yTemp)
                                    } else {
                                        perfPath.lineTo(x, yPerf)
                                        tempPath.lineTo(x, yTemp)
                                    }
                                }
                                drawPath(perfPath, color = linePrimary, style = Stroke(width = 3.5f))
                                drawPath(tempPath, color = tempLineColor, style = Stroke(width = 2.5f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val latest = throttleState.samples.last()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricBox(
                                label = "Peak / Current",
                                value = "${throttleState.peakScore.toInt()} / ${latest.multiScore.toInt()}",
                                sub = "${throttleState.samples.size} loops (${latest.elapsedSec}s)",
                                modifier = Modifier.weight(1f)
                            )
                            TechMetricBox(
                                label = "Thermal Throttle",
                                value = String.format(Locale.US, "%.1f%%", throttleState.throttleDropPercent),
                                sub = TelemetryReader.formatTemp(latest.tempCelsius, preferences.tempUnit),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. GPU Benchmark (2D Canvas Draw Calls + OpenGL ES 3.0 3D Shader Stress)
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_gpu_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = { onSetGpuRunning(!techyState.gpuRunning) },
                            colors = if (techyState.gpuRunning) {
                                ButtonDefaults.buttonColors(containerColor = TierCriticalRed)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.testTag("techy_run_gpu_btn")
                        ) {
                            Icon(
                                imageVector = if (techyState.gpuRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoText(resId = if (techyState.gpuRunning) R.string.action_stop else R.string.action_start)
                        }
                    }

                    AnimatedVisibility(visible = techyState.gpuRunning) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val accent = MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val t = canvasTick * 0.12f
                                    for (i in 0 until 180) {
                                        val x = ((i * 37 + canvasTick * 5) % size.width.toInt().coerceAtLeast(1)).toFloat()
                                        val y = size.height * 0.5f + sin(t + i * 0.25f) * (size.height * 0.36f)
                                        val x2 = x + cos(t + i) * 18f
                                        drawLine(
                                            color = accent.copy(alpha = 0.55f),
                                            start = Offset(x, y),
                                            end = Offset(x2, size.height - y),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                                Text(
                                    text = "2D Canvas Stress • ${String.format(Locale.US, "%.1f", canvas2dFps)} FPS",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                )
                            }

                            AndroidView(
                                factory = { ctx ->
                                    GLSurfaceView(ctx).apply {
                                        setEGLContextClientVersion(3)
                                        setRenderer(
                                            BenchyGlStressRenderer { avg, low1, drop, rendererName ->
                                                onUpdateGpuStats(avg, low1, drop, rendererName, canvas2dFps)
                                            }
                                        )
                                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    techyState.gpuResult?.let { gpu ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = gpu.rendererName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricBox(
                                label = "3D Avg FPS",
                                value = String.format(Locale.US, "%.1f", gpu.avg3dFps),
                                sub = "2D: ${String.format(Locale.US, "%.0f", gpu.score2dFps)} FPS",
                                modifier = Modifier.weight(1f)
                            )
                            TechMetricBox(
                                label = "1% Low FPS",
                                value = String.format(Locale.US, "%.1f", gpu.onePercentLowFps),
                                sub = "Drop: ${String.format(Locale.US, "%.1f%%", gpu.thermalDropPercent)}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. RAM Diagnostic Check (C++ Bandwidth, Latency, Buffers/Cached & zRAM)
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_ram_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = onRunRam,
                            enabled = !techyState.ramRunning,
                            modifier = Modifier.testTag("techy_run_ram_btn")
                        ) {
                            if (techyState.ramRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                AutoText(resId = R.string.action_run)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Free: ${TelemetryReader.formatRamGb(telemetry.memory.availableRamBytes, preferences.storageUnit)} / ${TelemetryReader.formatRamGb(telemetry.memory.totalRamBytes, preferences.storageUnit)} • zRAM: ${if (telemetry.memory.zramDetected) "Active" else "None"} (${telemetry.memory.swapTotalKb / 1024} MB swap) • Page Cache: ${telemetry.memory.cachedKb / 1024} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    techyState.ramResult?.let { ram ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricBox(
                                label = "Read BW",
                                value = "${ram.readBandwidthMbPerSec.toInt()} MB/s",
                                sub = "Copy: ${ram.copyBandwidthMbPerSec.toInt()} MB/s",
                                modifier = Modifier.weight(1f)
                            )
                            TechMetricBox(
                                label = "Pointer Latency",
                                value = String.format(Locale.US, "%.1f ns", ram.latencyNs),
                                sub = "8 MB stride ring",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Storage Read & Write Speed Test (Sequential + Random 4K IOPS)
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.SdStorage, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_storage_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = onRunStorage,
                            enabled = !techyState.storageRunning,
                            modifier = Modifier.testTag("techy_run_storage_btn")
                        ) {
                            if (techyState.storageRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                AutoText(resId = R.string.action_run)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "FS Block Size: ${telemetry.storage.blockSizeBytes} B • Free: ${TelemetryReader.formatStorage(telemetry.storage.freeBytes, preferences.storageUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    techyState.storageResult?.let { st ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricBox(
                                label = "Sequential R / W",
                                value = "${st.seqReadMbPerSec.toInt()} / ${st.seqWriteMbPerSec.toInt()} MB/s",
                                sub = "Unbuffered cache probe",
                                modifier = Modifier.weight(1f)
                            )
                            TechMetricBox(
                                label = "Random 4K IOPS",
                                value = "${st.rand4kReadIops.toInt()} R",
                                sub = "${st.rand4kWriteIops.toInt()} W IOPS",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 6. Thermal Zones & Headroom
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Thermostat, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.techy_thermal_zones_title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val headroomPct = telemetry.thermalHeadroom?.let { (it * 100f).toInt().coerceIn(0, 150) }
                    Text(
                        text = buildString {
                            append("Thermal Status: ${telemetry.thermalStatusLabel}")
                            if (headroomPct != null) append(" • 10s Headroom: $headroomPct%")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (telemetry.thermalZones.isEmpty()) {
                        Text(
                            text = "Battery Sensor: ${TelemetryReader.formatTemp(telemetry.battery.tempCelsius, preferences.tempUnit)} (OEM restricts /sys/class/thermal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            telemetry.thermalZones.forEach { z ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${z.name}: ${TelemetryReader.formatTemp(z.tempCelsius, preferences.tempUnit)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. SoC, Kernel & Display Architecture Specs
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.DeveloperBoard, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.techy_specs_title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TechMetricBox(
                            label = "Display & GPU",
                            value = "${hardwareSpecs.refreshRateHz.toInt()} Hz",
                            sub = "${hardwareSpecs.screenResolution} • GLES ${hardwareSpecs.glesVersion}",
                            modifier = Modifier.weight(1f)
                        )
                        TechMetricBox(
                            label = "OS & Security Patch",
                            value = hardwareSpecs.androidVersion,
                            sub = "Patch ${hardwareSpecs.securityPatch}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ABIs: ${hardwareSpecs.abis}\nBoard: ${hardwareSpecs.board}\nKernel: ${hardwareSpecs.kernelVersion}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 8. Sensor Array & Live Telemetry
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Sensors, null, tint = MaterialTheme.colorScheme.primary)
                            AutoText(
                                resId = R.string.techy_sensors_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = liveSensors.streaming,
                            onCheckedChange = onToggleLiveSensors,
                            modifier = Modifier.testTag("techy_sensors_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${deviceSensors.size} Hardware Sensors Detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = liveSensors.streaming) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TechMetricBox(
                                    label = "Accelerometer (m/s²)",
                                    value = String.format(Locale.US, "%.2f, %.2f", liveSensors.accelX, liveSensors.accelY),
                                    sub = String.format(Locale.US, "Z: %.2f", liveSensors.accelZ),
                                    modifier = Modifier.weight(1f)
                                )
                                TechMetricBox(
                                    label = "Gyro & Light",
                                    value = String.format(Locale.US, "%.2f rad/s", liveSensors.gyroZ),
                                    sub = String.format(Locale.US, "%.1f lx", liveSensors.lightLux),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        deviceSensors.take(8).forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${s.name.take(24)} (${String.format(Locale.US, "%.2f", s.powerMa)}mA)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 9. Accurate 1-Week Battery Health & Drain Test (Unswippable Service + Live 1s Clock + CSV/JSON Export)
            item {
                val active = preferences.batteryTrackerActive
                val elapsedMs = if (active && preferences.batteryTrackerStartMillis > 0L) {
                    (nowMillis - preferences.batteryTrackerStartMillis).coerceAtLeast(0L)
                } else {
                    ((batterySamples.firstOrNull()?.elapsedDays ?: 0f) * 86_400_000f).toLong()
                }
                val days = elapsedMs / 86_400_000L
                val hrs = (elapsedMs % 86_400_000L) / 3_600_000L
                val mins = (elapsedMs % 3_600_000L) / 60_000L
                val secs = (elapsedMs % 60_000L) / 1000L
                val elapsedClock = String.format(Locale.US, "%dd %02d:%02d:%02d", days, hrs, mins, secs)

                BenchyCard(borderColor = if (active) TierPeakGreen else MaterialTheme.colorScheme.outline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.BatteryChargingFull,
                                null,
                                tint = if (active) TierPeakGreen else MaterialTheme.colorScheme.primary
                            )
                            AutoText(
                                resId = R.string.techy_battery_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = { if (active) onStopBatteryTracker() else onStartBatteryTracker() },
                            colors = if (active) {
                                ButtonDefaults.buttonColors(containerColor = TierCriticalRed)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.testTag("techy_battery_toggle_btn")
                        ) {
                            AutoText(resId = if (active) R.string.action_stop else R.string.action_start)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TechMetricBox(
                            label = "Live Power Draw",
                            value = "${telemetry.battery.voltageMv} mV • ${telemetry.battery.currentMa} mA",
                            sub = "${String.format(Locale.US, "%.0f", telemetry.battery.powerMw)} mW (${telemetry.battery.pluggedSource})",
                            modifier = Modifier.weight(1f)
                        )
                        TechMetricBox(
                            label = "7-Day Elapsed",
                            value = "$elapsedClock / 7d",
                            sub = "${TelemetryReader.formatTemp(telemetry.battery.tempCelsius, preferences.tempUnit)} • ${batterySamples.size} pts",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val estMah = if (batteryAnalytics.estimatedCapacityMah > 0) {
                            "${batteryAnalytics.estimatedCapacityMah} mAh"
                        } else if (telemetry.battery.chargeCounterUah > 0 && telemetry.battery.levelPercent > 5) {
                            "${((telemetry.battery.chargeCounterUah / 1000.0) / (telemetry.battery.levelPercent / 100.0)).toInt()} mAh"
                        } else {
                            "Sampling…"
                        }
                        TechMetricBox(
                            label = "Est. Full Capacity",
                            value = estMah,
                            sub = "Avg ${batteryAnalytics.avgVoltageMv.takeIf { it > 0 } ?: telemetry.battery.voltageMv} mV",
                            modifier = Modifier.weight(1f)
                        )
                        TechMetricBox(
                            label = "Measured Drain Rate",
                            value = String.format(Locale.US, "%.2f %%/hr", batteryAnalytics.drainRatePctPerHour),
                            sub = "Peak ${TelemetryReader.formatTemp(batteryAnalytics.peakTempCelsius.takeIf { it > 0f } ?: telemetry.battery.tempCelsius, preferences.tempUnit)}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (batterySamples.size >= 2) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val lineColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(6.dp)
                        ) {
                            val ordered = batterySamples.reversed()
                            val path = Path()
                            ordered.forEachIndexed { idx, s ->
                                val x = (idx.toFloat() / (ordered.size - 1).coerceAtLeast(1)) * size.width
                                val y = size.height - ((s.levelPercent / 100f).coerceIn(0f, 1f) * size.height)
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { csvLauncher.launch("benchy_battery_7d.csv") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_battery_csv_btn")
                        ) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.techy_export_csv)
                        }
                        OutlinedButton(
                            onClick = { jsonLauncher.launch("benchy_battery_7d.json") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_battery_json_btn")
                        ) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.techy_export_json)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechMetricBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sub,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
