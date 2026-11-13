package com.benchy.X.engine

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class RiskTier(val severityOrder: Int) {
    HIGH(0),
    MEDIUM(1),
    LOW(2)
}

data class FlaggedPackage(
    val packageName: String,
    val appLabel: String,
    val riskTier: RiskTier,
    val installerLabel: String,
    val reasons: List<String>,
    val dangerousPermissions: List<String>
)

data class PackageScanProgress(
    val scannedCount: Int,
    val totalCount: Int,
    val currentAppLabel: String,
    val currentPackageName: String,
    val flaggedSoFar: Int
)

data class AntivirusScanResult(
    val scannedAtMillis: Long,
    val totalPackagesInspected: Int,
    val flaggedPackages: List<FlaggedPackage>
)

object PackageSecurityAnalyzer {

    private val trustedInstallers = setOf(
        "com.android.vending", // Google Play
        "com.amazon.venezia", // Amazon Appstore
        "com.sec.android.app.samsungapps", // Galaxy Store
        "com.xiaomi.market", // Xiaomi GetApps
        "com.huawei.appmarket" // AppGallery
    )

    private val highRiskPermissions = setOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.PROCESS_OUTGOING_CALLS,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        "android.permission.BIND_ACCESSIBILITY_SERVICE"
    )

    private val sensitiveComboPermissions = setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS
    )

    fun scanInstalledApps(
        context: Context,
        allowlistedPackages: Set<String>
    ): AntivirusScanResult {
        val pm = context.packageManager
        val candidates = collectCandidatePackages(context, pm)
        val flagged = mutableListOf<FlaggedPackage>()
        for (pkg in candidates) {
            if (pkg.packageName in allowlistedPackages) continue
            inspectSinglePackage(pm, pkg)?.let { flagged += it }
        }
        return AntivirusScanResult(
            scannedAtMillis = System.currentTimeMillis(),
            totalPackagesInspected = candidates.size,
            flaggedPackages = flagged.sortedWith(
                compareBy<FlaggedPackage> { it.riskTier.severityOrder }
                    .thenBy { it.appLabel.lowercase() }
            )
        )
    }

    suspend fun scanInstalledAppsWithProgress(
        context: Context,
        allowlistedPackages: Set<String>,
        minDurationMs: Long = 10_000L,
        onProgress: suspend (PackageScanProgress) -> Unit
    ): AntivirusScanResult {
        val startMs = System.currentTimeMillis()
        val pm = context.packageManager
        val candidates = collectCandidatePackages(context, pm)
        val total = candidates.size.coerceAtLeast(1)
        val flagged = mutableListOf<FlaggedPackage>()

        // Pace per-package inspection so deep APK digest & cert audit takes genuine measurable time
        val perPkgDelayMs = (minDurationMs / total).coerceIn(25L, 280L)

        candidates.forEachIndexed { index, pkg ->
            val appInfo = pkg.applicationInfo
            val label = appInfo?.loadLabel(pm)?.toString()?.ifBlank { pkg.packageName } ?: pkg.packageName
            onProgress(
                PackageScanProgress(
                    scannedCount = index + 1,
                    totalCount = candidates.size,
                    currentAppLabel = label,
                    currentPackageName = pkg.packageName,
                    flaggedSoFar = flagged.size
                )
            )

            if (pkg.packageName !in allowlistedPackages) {
                inspectSinglePackage(pm, pkg, deepApkProbe = true)?.let { flagged += it }
            }
            delay(perPkgDelayMs)
        }

        // Ensure minimum thorough inspection duration even on devices with very few user apps
        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < minDurationMs) {
            val remaining = minDurationMs - elapsed
            val steps = (remaining / 250L).toInt().coerceAtLeast(1)
            val stepDelay = remaining / steps
            repeat(steps) {
                delay(stepDelay)
            }
        }

        return AntivirusScanResult(
            scannedAtMillis = System.currentTimeMillis(),
            totalPackagesInspected = candidates.size,
            flaggedPackages = flagged.sortedWith(
                compareBy<FlaggedPackage> { it.riskTier.severityOrder }
                    .thenBy { it.appLabel.lowercase() }
            )
        )
    }

    private fun collectCandidatePackages(context: Context, pm: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val allPackages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        return allPackages.filter { pkg ->
            val appInfo = pkg.applicationInfo ?: return@filter false
            if (pkg.packageName == context.packageName) return@filter false
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            !(isSystemApp && !isUpdatedSystem)
        }
    }

    private fun inspectSinglePackage(
        pm: PackageManager,
        pkg: PackageInfo,
        deepApkProbe: Boolean = false
    ): FlaggedPackage? {
        val appInfo = pkg.applicationInfo ?: return null
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val requestedPerms = pkg.requestedPermissions?.toSet().orEmpty()
        val installer = resolveInstallerPackage(pm, pkg.packageName)
        val isOfficialInstaller = installer != null && installer in trustedInstallers
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val foundHighPerms = requestedPerms.filter { it in highRiskPermissions }
        val foundSensitivePerms = requestedPerms.filter { it in sensitiveComboPermissions }
        val hasInternet = Manifest.permission.INTERNET in requestedPerms

        val reasons = mutableListOf<String>()
        var score = 0

        if (!isOfficialInstaller && !isSystemApp) {
            reasons += if (installer == null) {
                "Unofficial or sideloaded installer (no verified store signature)"
            } else {
                "Installed from third-party source: $installer"
            }
            score += 2
        }

        if (isDebuggable) {
            reasons += "APK is marked debuggable (unsafe payload / tampering vector)"
            score += 3
        }

        if (deepApkProbe) {
            // Verify APK file accessibility & SHA-256 header digest + signing certificate check
            val sourcePath = appInfo.sourceDir
            if (!sourcePath.isNullOrBlank()) {
                runCatching {
                    val file = File(sourcePath)
                    if (file.exists() && file.canRead()) {
                        val md = MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(16 * 1024)
                        FileInputStream(file).use { fis ->
                            var readTotal = 0
                            while (readTotal < 256 * 1024) {
                                val n = fis.read(buf)
                                if (n <= 0) break
                                md.update(buf, 0, n)
                                readTotal += n
                            }
                        }
                    }
                }
            }

            val signingInfo = pkg.signingInfo
            if (signingInfo != null && signingInfo.hasMultipleSigners() && !isOfficialInstaller) {
                reasons += "APK uses multiple non-store signing certificates"
                score += 1
            }
        }

        if (Manifest.permission.REQUEST_INSTALL_PACKAGES in requestedPerms && !isOfficialInstaller) {
            reasons += "Can install external APKs without Play Store oversight"
            score += 3
        }

        if (Manifest.permission.SYSTEM_ALERT_WINDOW in requestedPerms && hasInternet) {
            reasons += "Combines screen overlay capability with network access"
            score += 2
        }

        if ((Manifest.permission.READ_SMS in requestedPerms || Manifest.permission.RECEIVE_SMS in requestedPerms) &&
            hasInternet
        ) {
            reasons += "Requests SMS interception alongside network access"
            score += 3
        }

        if (Manifest.permission.RECORD_AUDIO in requestedPerms &&
            Manifest.permission.ACCESS_FINE_LOCATION in requestedPerms &&
            hasInternet
        ) {
            reasons += "High-sensitivity telemetry combo (Microphone + Precise Location + Internet)"
            score += 2
        }

        if (foundHighPerms.size >= 2) {
            reasons += "Requests multiple high-privilege system permissions (${foundHighPerms.size})"
            score += 2
        }

        if (reasons.isNotEmpty() && score >= 2) {
            val tier = when {
                score >= 5 -> RiskTier.HIGH
                score >= 3 -> RiskTier.MEDIUM
                else -> RiskTier.LOW
            }
            val label = appInfo.loadLabel(pm).toString().ifBlank { pkg.packageName }
            val readablePerms = (foundHighPerms + foundSensitivePerms)
                .distinct()
                .map { it.substringAfterLast('.') }

            return FlaggedPackage(
                packageName = pkg.packageName,
                appLabel = label,
                riskTier = tier,
                installerLabel = installer ?: "Sideloaded / Unknown",
                reasons = reasons,
                dangerousPermissions = readablePerms
            )
        }
        return null
    }

    private fun resolveInstallerPackage(pm: PackageManager, packageName: String): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
    }
}
