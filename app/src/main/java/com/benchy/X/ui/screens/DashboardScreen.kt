package com.benchy.X.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benchy.X.R
import com.benchy.X.data.BenchmarkRecordEntity
import com.benchy.X.data.UserPreferences
import com.benchy.X.engine.DeviceHealthReport
import com.benchy.X.engine.DeviceTelemetry
import com.benchy.X.engine.HealthMetricRating
import com.benchy.X.engine.PerformanceTier
import com.benchy.X.locale.AutoText
import com.benchy.X.ui.QuickScanUiState
import com.benchy.X.ui.components.AnimatedScoreRing
import com.benchy.X.ui.components.BatteryOptExemptionDialog
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.theme.JetBrainsMonoFontFamily
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics
import com.benchy.X.ui.theme.TierCriticalRed
import com.benchy.X.ui.theme.TierDragOrange
import com.benchy.X.ui.theme.TierGoodAmber
import com.benchy.X.ui.theme.TierPeakGreen
import com.benchy.X.workers.LongScanProgress
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    preferences: UserPreferences,
    telemetry: DeviceTelemetry,
    healthReport: DeviceHealthReport,
    latestBenchmark: BenchmarkRecordEntity?,
    quickScan: QuickScanUiState,
    longScan: LongScanProgress,
    isIgnoringBatteryOpt: () -> Boolean,
    onMarkIntroSeen: () -> Unit,
    onStartQuickScan: () -> Unit,
    onCancelQuickScan: () -> Unit,
    onStartLongScan: () -> Unit,
    onCancelLongScan: () -> Unit
) {
    val metrics = LocalBenchyLayoutMetrics.current
    var showFirstTimeIntro by remember { mutableStateOf(false) }
    var showScanModeSheet by remember { mutableStateOf(false) }
    var showBatteryOptForLongScan by remember { mutableStateOf(false) }

    if (showFirstTimeIntro) {
        AlertDialog(
            onDismissRequest = {
                showFirstTimeIntro = false
                onMarkIntroSeen()
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                AutoText(
                    resId = R.string.test_my_phone_intro_title,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                AutoText(
                    resId = R.string.test_my_phone_intro_message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFirstTimeIntro = false
                        onMarkIntroSeen()
                        showScanModeSheet = true
                    },
                    modifier = Modifier.testTag("intro_got_it_btn")
                ) {
                    AutoText(resId = R.string.action_got_it)
                }
            }
        )
    }

    if (showScanModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScanModeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AutoText(
                    resId = R.string.scan_mode_sheet_title,
                    style = MaterialTheme.typography.headlineMedium
                )

                // Quick Scan (30 Seconds)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showScanModeSheet = false
                            onStartQuickScan()
                        }
                        .testTag("scan_option_quick")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AutoText(
                                resId = R.string.scan_quick_title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            AutoText(
                                resId = R.string.scan_quick_desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Long Scan (2 Hours - Recommended)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showScanModeSheet = false
                            if (isIgnoringBatteryOpt()) {
                                onStartLongScan()
                            } else {
                                showBatteryOptForLongScan = true
                            }
                        }
                        .testTag("scan_option_long")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AutoText(
                                resId = R.string.scan_long_title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AutoText(
                                resId = R.string.scan_long_desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBatteryOptForLongScan) {
        BatteryOptExemptionDialog(
            bodyResId = R.string.battery_opt_long_scan_body,
            onDismiss = { showBatteryOptForLongScan = false },
            onContinue = {
                showBatteryOptForLongScan = false
                onStartLongScan()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            // Device identity pill header
            item(key = "dash_identity") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${telemetry.deviceModel} • ${telemetry.socName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = telemetry.thermalStatusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Hero "Test My Phone" Card
            item(key = "dash_hero") {
                val primary = MaterialTheme.colorScheme.primary
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.45f)),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        primary.copy(alpha = 0.14f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AutoText(
                                resId = R.string.dashboard_hero_title,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            AutoText(
                                resId = R.string.dashboard_hero_subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    if (!preferences.hasSeenTestMyPhoneIntro) {
                                        showFirstTimeIntro = true
                                    } else {
                                        showScanModeSheet = true
                                    }
                                },
                                enabled = !quickScan.isRunning && !longScan.isRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("test_my_phone_cta")
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                AutoText(
                                    resId = R.string.dashboard_run_test_cta,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Active 30s Quick Scan Progress (1-second real-time countdown)
            item(key = "dash_quick_scan") {
                AnimatedVisibility(visible = quickScan.isRunning) {
                    val elapsedSec = (30 - quickScan.remainingSeconds).coerceIn(0, 30)
                    BenchyCard(borderColor = MaterialTheme.colorScheme.primary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoText(
                                resId = R.string.scan_in_progress_title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${elapsedSec}s / 30s • ${quickScan.remainingSeconds}s left",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AutoText(
                            text = quickScan.stageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { quickScan.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCancelQuickScan,
                            modifier = Modifier.testTag("cancel_quick_scan_btn")
                        ) {
                            Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.action_cancel)
                        }
                    }
                }
            }

            // Active 2-Hour Long Scan Live Interim Progress
            item(key = "dash_long_scan") {
                AnimatedVisibility(visible = longScan.isRunning) {
                    val remainingMs = (longScan.totalDurationMillis - longScan.elapsedMillis).coerceAtLeast(0L)
                    val remHours = remainingMs / 3_600_000L
                    val remMins = (remainingMs % 3_600_000L) / 60_000L
                    val remSecs = (remainingMs % 60_000L) / 1000L
                    val remFormatted = String.format(Locale.US, "%02d:%02d:%02d", remHours, remMins, remSecs)

                    BenchyCard(borderColor = MaterialTheme.colorScheme.primary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                AutoText(
                                    resId = R.string.scan_long_progress_title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${longScan.progressPercent}%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AutoText(
                            text = longScan.phaseLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (longScan.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AutoText(
                                resId = R.string.scan_remaining_label,
                                style = MaterialTheme.typography.labelMedium,
                                formatArgs = arrayOf(remFormatted)
                            )
                            AutoText(
                                resId = R.string.scan_interim_score_label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                formatArgs = arrayOf(longScan.interimScore)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InterimChip("CPU", "${longScan.interimCpuMulti.toInt()}", Modifier.weight(1f))
                            InterimChip("RAM", "${longScan.interimRamBw.toInt()} MB/s", Modifier.weight(1f))
                            InterimChip("Risks", "${longScan.interimThreats}", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onCancelLongScan,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TierCriticalRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_long_scan_btn")
                        ) {
                            Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.action_cancel)
                        }
                    }
                }
            }

            // Latest Benchmark Result + Color-Coded Score Ring + Sub-Ratings + Review
            item(key = "dash_latest_result") {
                if (latestBenchmark == null) {
                    BenchyCard {
                        AutoText(
                            resId = R.string.dashboard_no_runs_yet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val tier = PerformanceTier.fromScore(latestBenchmark.overallScore)
                    BenchyCard(borderColor = tier.colorHex.copy(alpha = 0.6f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoText(
                                resId = R.string.dashboard_last_score_label,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tier.colorHex.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = latestBenchmark.scanType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tier.colorHex,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedScoreRing(
                                score = latestBenchmark.overallScore,
                                tierColor = tier.colorHex
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tier.colorHex.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AutoText(
                                resId = tier.reviewResId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SubRatingTile(
                                title = stringResource(R.string.subrating_2d_gaming),
                                rating = latestBenchmark.gaming2dRating,
                                icon = Icons.Outlined.Gamepad,
                                modifier = Modifier.weight(1f)
                            )
                            SubRatingTile(
                                title = stringResource(R.string.subrating_3d_gaming),
                                rating = latestBenchmark.gaming3dRating,
                                icon = Icons.Outlined.Speed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SubRatingTile(
                                title = stringResource(R.string.subrating_battery_temp),
                                rating = latestBenchmark.batteryTempRating,
                                icon = Icons.Outlined.Thermostat,
                                modifier = Modifier.weight(1f)
                            )
                            SubRatingTile(
                                title = stringResource(R.string.subrating_multitasking),
                                rating = latestBenchmark.multitaskingRating,
                                icon = Icons.Outlined.Memory,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Device Health Check (1-10 Ratings) — empty on first launch until user runs a scan
            item(key = "dash_health_title") {
                AutoText(
                    resId = R.string.health_section_title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (latestBenchmark == null) {
                item(key = "dash_health_empty") {
                    BenchyCard {
                        AutoText(
                            resId = R.string.health_empty_state,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "dash_health_battery") {
                    HealthRatingCard(
                        title = stringResource(R.string.health_battery_heat),
                        icon = Icons.Outlined.Thermostat,
                        metric = healthReport.batteryHeat
                    )
                }

                item(key = "dash_health_storage") {
                    HealthRatingCard(
                        title = stringResource(R.string.health_storage),
                        icon = Icons.Outlined.SdStorage,
                        metric = healthReport.storageHealth
                    )
                }

                item(key = "dash_health_ram") {
                    HealthRatingCard(
                        title = stringResource(R.string.health_ram),
                        icon = Icons.Outlined.Memory,
                        metric = healthReport.ramHealth
                    )
                }
            }
        }
    }
}

@Composable
private fun InterimChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubRatingTile(
    title: String,
    rating: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val badgeColor = ratingColor(rating)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "$rating/10",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            AutoText(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun HealthRatingCard(
    title: String,
    icon: ImageVector,
    metric: HealthMetricRating
) {
    val color = ratingColor(metric.ratingOutOfTen)
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
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Column {
                    AutoText(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${metric.primaryValue} • ${metric.secondaryValue}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "${metric.ratingOutOfTen} / 10",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (metric.ratingOutOfTen / 10f).coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        AutoText(
            text = metric.advice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun ratingColor(rating: Int): Color = when {
    rating <= 3 -> TierCriticalRed
    rating <= 5 -> TierDragOrange
    rating <= 8 -> TierGoodAmber
    else -> TierPeakGreen
}
