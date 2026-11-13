package com.benchy.X.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benchy.X.R
import com.benchy.X.data.AllowlistedAppEntity
import com.benchy.X.data.UserPreferences
import com.benchy.X.engine.FlaggedPackage
import com.benchy.X.engine.RiskTier
import com.benchy.X.locale.AutoText
import com.benchy.X.ui.AntivirusUiState
import com.benchy.X.ui.components.BatteryOptExemptionDialog
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.theme.JetBrainsMonoFontFamily
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics
import com.benchy.X.ui.theme.TierCriticalRed
import com.benchy.X.ui.theme.TierDragOrange
import com.benchy.X.ui.theme.TierGoodAmber
import com.benchy.X.ui.theme.TierPeakGreen

@Composable
fun SecurityScreen(
    preferences: UserPreferences,
    antivirusState: AntivirusUiState,
    allowlistedApps: List<AllowlistedAppEntity>,
    isIgnoringBatteryOpt: () -> Boolean,
    onRunScan: () -> Unit,
    onToggleAutoScan: (Boolean) -> Unit,
    onAllowlistPackage: (String, String) -> Unit,
    onRemoveAllowlist: (String) -> Unit
) {
    val context = LocalContext.current
    val metrics = LocalBenchyLayoutMetrics.current
    var showBatteryOptForAutoScan by remember { mutableStateOf(false) }

    val isScanning = antivirusState.isScanning
    val scanProgress = antivirusState.progress
    val scanResult = antivirusState.result

    if (showBatteryOptForAutoScan) {
        BatteryOptExemptionDialog(
            bodyResId = R.string.battery_opt_autoscan_body,
            onDismiss = { showBatteryOptForAutoScan = false },
            onContinue = {
                showBatteryOptForAutoScan = false
                onToggleAutoScan(true)
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
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AutoText(
                                resId = R.string.security_title,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            AutoText(
                                resId = R.string.security_subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRunScan,
                        enabled = !isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("antivirus_scan_now_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AutoText(resId = R.string.action_running)
                        } else {
                            Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            AutoText(resId = R.string.security_scan_now)
                        }
                    }

                    AnimatedVisibility(visible = isScanning) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val total = (scanProgress?.totalCount ?: 1).coerceAtLeast(1)
                            val scanned = scanProgress?.scannedCount ?: 0
                            val fraction = (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            if (scanProgress != null) {
                                AutoText(
                                    resId = R.string.security_inspecting_progress,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    formatArgs = arrayOf(
                                        scanProgress.scannedCount,
                                        scanProgress.totalCount,
                                        scanProgress.currentAppLabel
                                    )
                                )
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "${scanProgress.currentPackageName} • Flagged so far: ${scanProgress.flaggedSoFar}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Automated Daily Antivirus Toggle
            item {
                BenchyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AutoText(
                                resId = R.string.security_auto_scan_title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            AutoText(
                                resId = R.string.security_auto_scan_desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                formatArgs = arrayOf(preferences.autoScanIntervalHours)
                            )
                        }
                        Switch(
                            checked = preferences.autoScanEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !isIgnoringBatteryOpt()) {
                                    showBatteryOptForAutoScan = true
                                } else {
                                    onToggleAutoScan(enable)
                                }
                            },
                            modifier = Modifier.testTag("auto_scan_switch")
                        )
                    }
                }
            }

            // Scan Summary Status
            if (scanResult != null && !isScanning) {
                item {
                    val zeroThreats = scanResult.flaggedPackages.isEmpty()
                    val bannerColor = if (zeroThreats) TierPeakGreen else TierCriticalRed
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bannerColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (zeroThreats) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = bannerColor
                            )
                            if (zeroThreats) {
                                AutoText(
                                    resId = R.string.security_zero_threats,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = bannerColor,
                                    formatArgs = arrayOf(scanResult.totalPackagesInspected)
                                )
                            } else {
                                AutoText(
                                    resId = R.string.security_threats_found,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = bannerColor,
                                    formatArgs = arrayOf(
                                        scanResult.flaggedPackages.size,
                                        scanResult.totalPackagesInspected
                                    )
                                )
                            }
                        }
                    }
                }

                // Risk-Tiered Cards with Allowlist and App Info Actions
                items(scanResult.flaggedPackages, key = { it.packageName }) { pkg ->
                    FlaggedPackageCard(
                        pkg = pkg,
                        onAllowlist = { onAllowlistPackage(pkg.packageName, pkg.appLabel) },
                        onOpenAppInfo = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", pkg.packageName, null)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }

            // Allowlisted Apps Section
            if (allowlistedApps.isNotEmpty()) {
                item {
                    AutoText(
                        resId = R.string.security_allowlisted_section,
                        style = MaterialTheme.typography.titleMedium,
                        formatArgs = arrayOf(allowlistedApps.size),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(allowlistedApps, key = { it.packageName }) { item ->
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
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = TierPeakGreen
                                )
                                Column {
                                    Text(
                                        text = item.appLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = item.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onRemoveAllowlist(item.packageName) },
                                modifier = Modifier.testTag("remove_allowlist_${item.packageName}")
                            ) {
                                AutoText(resId = R.string.security_remove_allowlist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlaggedPackageCard(
    pkg: FlaggedPackage,
    onAllowlist: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val tierColor = when (pkg.riskTier) {
        RiskTier.HIGH -> TierCriticalRed
        RiskTier.MEDIUM -> TierDragOrange
        RiskTier.LOW -> TierGoodAmber
    }
    val tierResId = when (pkg.riskTier) {
        RiskTier.HIGH -> R.string.security_risk_high
        RiskTier.MEDIUM -> R.string.security_risk_medium
        RiskTier.LOW -> R.string.security_risk_low
    }

    BenchyCard(borderColor = tierColor.copy(alpha = 0.65f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pkg.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pkg.packageName} • ${pkg.installerLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tierColor.copy(alpha = 0.16f)
            ) {
                AutoText(
                    resId = tierResId,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tierColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        pkg.reasons.forEach { reason ->
            AutoText(
                text = "• $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (pkg.dangerousPermissions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pkg.dangerousPermissions.take(5).forEach { perm ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = perm,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onAllowlist,
                modifier = Modifier
                    .weight(1f)
                    .testTag("allowlist_btn_${pkg.packageName}")
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                AutoText(resId = R.string.security_allowlist_btn)
            }
            Button(
                onClick = onOpenAppInfo,
                modifier = Modifier
                    .weight(1f)
                    .testTag("app_info_btn_${pkg.packageName}")
            ) {
                Icon(Icons.Outlined.Info, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                AutoText(resId = R.string.security_app_info_btn)
            }
        }
    }
}
