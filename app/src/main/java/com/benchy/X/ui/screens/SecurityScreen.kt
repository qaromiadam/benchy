package com.benchy.X.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.benchy.X.R
import com.benchy.X.data.AllowlistedAppEntity
import com.benchy.X.data.UserPreferences
import com.benchy.X.engine.FileThreatCategory
import com.benchy.X.engine.FlaggedFile
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
import java.util.Locale

@Composable
fun SecurityScreen(
    preferences: UserPreferences,
    antivirusState: AntivirusUiState,
    allowlistedApps: List<AllowlistedAppEntity>,
    isIgnoringBatteryOpt: () -> Boolean,
    hasFilePermission: () -> Boolean,
    onRunScan: () -> Unit,
    onScanSafFolder: (Uri) -> Unit,
    onDeleteFlaggedFile: (FlaggedFile) -> Unit,
    onDismissFlaggedFile: (String) -> Unit,
    onToggleAutoScan: (Boolean) -> Unit,
    onAllowlistPackage: (String, String) -> Unit,
    onRemoveAllowlist: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val metrics = LocalBenchyLayoutMetrics.current
    var showBatteryOptForAutoScan by remember { mutableStateOf(false) }
    var showStoragePermDialog by remember { mutableStateOf(false) }
    var pendingScanAfterPerm by remember { mutableStateOf(false) }
    var filePermGranted by remember { mutableStateOf(hasFilePermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = hasFilePermission()
                filePermGranted = nowGranted
                if (pendingScanAfterPerm && nowGranted) {
                    pendingScanAfterPerm = false
                    onRunScan()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val readStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        filePermGranted = granted || hasFilePermission()
        if (pendingScanAfterPerm) {
            pendingScanAfterPerm = false
            onRunScan()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onScanSafFolder(uri)
        }
    }

    fun requestFileAccess(autoRunAfter: Boolean = false) {
        pendingScanAfterPerm = autoRunAfter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { context.startActivity(appIntent) }
                .onFailure { runCatching { context.startActivity(fallback) } }
        } else {
            readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun handleRunFullScanClick() {
        if (hasFilePermission()) {
            onRunScan()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestFileAccess(autoRunAfter = true)
        } else {
            showStoragePermDialog = true
        }
    }

    val isScanning = antivirusState.isScanning
    val pkgProgress = antivirusState.packageProgress
    val fileProgress = antivirusState.fileProgress
    val pkgResult = antivirusState.packageResult
    val fileResult = antivirusState.fileResult

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

    if (showStoragePermDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermDialog = false },
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { AutoText(resId = R.string.security_crawler_perm_dialog_title) },
            text = { AutoText(resId = R.string.security_crawler_perm_dialog_body) },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePermDialog = false
                        requestFileAccess(autoRunAfter = true)
                    },
                    modifier = Modifier.testTag("crawler_dialog_grant_btn")
                ) {
                    AutoText(resId = R.string.security_crawler_grant_all)
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            showStoragePermDialog = false
                            folderPickerLauncher.launch(null)
                        },
                        modifier = Modifier.testTag("crawler_dialog_saf_btn")
                    ) {
                        AutoText(resId = R.string.security_scan_folder)
                    }
                    TextButton(
                        onClick = {
                            showStoragePermDialog = false
                            onRunScan()
                        },
                        modifier = Modifier.testTag("crawler_dialog_continue_btn")
                    ) {
                        AutoText(resId = R.string.security_crawler_scan_anyway)
                    }
                }
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
            // Header & Scan Triggers
            item(key = "sec_header") {
                BenchyCard(modifier = Modifier.animateContentSize()) {
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

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (preferences.skipSystemApps) {
                            "Mode: User-installed apps + file-system crawler (payloads & zip bombs)"
                        } else {
                            "Mode: All installed apps + file-system crawler (payloads & zip bombs)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { handleRunFullScanClick() },
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

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        enabled = !isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("antivirus_scan_folder_btn")
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        AutoText(resId = R.string.security_scan_folder)
                    }

                    AnimatedVisibility(visible = isScanning) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when {
                                pkgProgress != null -> {
                                    val total = pkgProgress.totalCount.coerceAtLeast(1)
                                    val fraction = (pkgProgress.scannedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    AutoText(
                                        resId = R.string.security_inspecting_progress,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        formatArgs = arrayOf(
                                            pkgProgress.scannedCount,
                                            pkgProgress.totalCount,
                                            pkgProgress.currentAppLabel
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
                                        text = "${pkgProgress.currentPackageName} • Flagged: ${pkgProgress.flaggedSoFar}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                fileProgress != null -> {
                                    val total = fileProgress.totalFiles.coerceAtLeast(1)
                                    val fraction = (fileProgress.scannedFiles.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    AutoText(
                                        resId = R.string.security_inspecting_files_progress,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        formatArgs = arrayOf(
                                            fileProgress.scannedFiles,
                                            fileProgress.totalFiles,
                                            fileProgress.currentFileName
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
                                        text = "Dirs crawled: ${fileProgress.directoriesTraversed} • Payload & Zip-Bomb check • Flagged: ${fileProgress.flaggedSoFar}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> {
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
            }

            // Read Files Permission Card
            item(key = "sec_file_perm") {
                val borderColor = if (filePermGranted) {
                    TierPeakGreen.copy(alpha = 0.5f)
                } else {
                    TierDragOrange.copy(alpha = 0.7f)
                }
                BenchyCard(borderColor = borderColor) {
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
                                imageVector = if (filePermGranted) Icons.Outlined.CheckCircle else Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = if (filePermGranted) TierPeakGreen else TierDragOrange
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                AutoText(
                                    resId = if (filePermGranted) {
                                        R.string.security_file_perm_granted
                                    } else {
                                        R.string.security_file_perm_title
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                AutoText(
                                    resId = R.string.security_file_perm_desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!filePermGranted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { requestFileAccess() },
                                modifier = Modifier.testTag("grant_file_perm_btn")
                            ) {
                                AutoText(resId = R.string.action_grant_permission)
                            }
                        }
                    }
                }
            }

            // Automated Daily Antivirus Toggle
            item(key = "sec_auto_scan") {
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

            // Scan Summary Status (Packages + Files)
            if ((pkgResult != null || fileResult != null) && !isScanning) {
                val flaggedPkgs = pkgResult?.flaggedPackages.orEmpty()
                val flaggedFiles = fileResult?.flaggedFiles.orEmpty()
                val totalApps = pkgResult?.totalPackagesInspected ?: 0
                val totalFiles = fileResult?.totalFilesInspected ?: 0

                item(key = "sec_summary_banner") {
                    val zeroThreats = flaggedPkgs.isEmpty() && flaggedFiles.isEmpty()
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
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (zeroThreats) {
                                    AutoText(
                                        resId = R.string.security_zero_threats,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = bannerColor,
                                        formatArgs = arrayOf(totalApps, totalFiles)
                                    )
                                } else {
                                    AutoText(
                                        resId = R.string.security_threats_found,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = bannerColor,
                                        formatArgs = arrayOf(
                                            flaggedPkgs.size,
                                            flaggedFiles.size,
                                            totalApps,
                                            totalFiles
                                        )
                                    )
                                }
                                fileResult?.let { fr ->
                                    Text(
                                        text = "Crawler: ${fr.crawlScopeLabel}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Flagged Files & Zip Bombs
                items(flaggedFiles, key = { "file_${it.id}" }) { flaggedFile ->
                    FlaggedFileCard(
                        flaggedFile = flaggedFile,
                        onDelete = { onDeleteFlaggedFile(flaggedFile) },
                        onDismiss = { onDismissFlaggedFile(flaggedFile.id) }
                    )
                }

                // Flagged App Packages
                items(flaggedPkgs, key = { "pkg_${it.packageName}" }) { pkg ->
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
                item(key = "sec_allowlist_header") {
                    AutoText(
                        resId = R.string.security_allowlisted_section,
                        style = MaterialTheme.typography.titleMedium,
                        formatArgs = arrayOf(allowlistedApps.size),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(allowlistedApps, key = { "allow_${it.packageName}" }) { item ->
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

@Composable
private fun FlaggedFileCard(
    flaggedFile: FlaggedFile,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val tierColor = when (flaggedFile.riskTier) {
        RiskTier.HIGH -> TierCriticalRed
        RiskTier.MEDIUM -> TierDragOrange
        RiskTier.LOW -> TierGoodAmber
    }
    val categoryLabel = when (flaggedFile.category) {
        FileThreatCategory.ZIP_BOMB -> "ZIP BOMB"
        FileThreatCategory.EICAR_TEST -> "EICAR TEST"
        FileThreatCategory.SPOOFED_EXTENSION -> "SPOOFED EXT"
        FileThreatCategory.SUSPICIOUS_APK_OR_SCRIPT -> "EXEC / APK"
        FileThreatCategory.MALICIOUS_PAYLOAD -> "MALICIOUS"
    }
    val sizeText = when {
        flaggedFile.sizeBytes >= 1024 * 1024 ->
            String.format(Locale.US, "%.2f MB", flaggedFile.sizeBytes / (1024.0 * 1024.0))
        flaggedFile.sizeBytes >= 1024 ->
            String.format(Locale.US, "%.1f KB", flaggedFile.sizeBytes / 1024.0)
        else -> "${flaggedFile.sizeBytes} B"
    }

    BenchyCard(borderColor = tierColor.copy(alpha = 0.7f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flaggedFile.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$sizeText • ${flaggedFile.filePath}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tierColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tierColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        flaggedFile.reasons.forEach { reason ->
            AutoText(
                text = "• $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dismiss_file_${flaggedFile.fileName}")
            ) {
                Icon(Icons.Outlined.VisibilityOff, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                AutoText(resId = R.string.security_dismiss_file_btn)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = TierCriticalRed),
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_file_${flaggedFile.fileName}")
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                AutoText(resId = R.string.security_delete_file_btn)
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
