package com.benchy.X

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.benchy.X.data.AppMode
import com.benchy.X.locale.AutoText
import com.benchy.X.locale.TranslationStatusBanner
import com.benchy.X.ui.BenchyViewModel
import com.benchy.X.ui.screens.DashboardScreen
import com.benchy.X.ui.screens.OnboardingScreen
import com.benchy.X.ui.screens.SecurityScreen
import com.benchy.X.ui.screens.SettingsScreen
import com.benchy.X.ui.screens.TechySuiteScreen
import com.benchy.X.ui.theme.BenchyTheme
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object DashboardRoute

@Serializable
data object SecurityRoute

@Serializable
data object TechySuiteRoute

@Serializable
data object SettingsRoute

private data class TopLevelTab(
    val route: Any,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BenchyViewModel = viewModel()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()

            BenchyTheme(
                themeMode = preferences.themeMode,
                accentPreset = preferences.accentPreset,
                cardDensity = preferences.cardDensity
            ) {
                BenchyRootApp(viewModel = viewModel)
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun BenchyRootApp(viewModel: BenchyViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val healthReport by viewModel.healthReport.collectAsStateWithLifecycle()
    val latestBenchmark by viewModel.latestBenchmark.collectAsStateWithLifecycle()
    val quickScan by viewModel.quickScan.collectAsStateWithLifecycle()
    val longScan by viewModel.longScanProgress.collectAsStateWithLifecycle()
    val antivirusState by viewModel.antivirusState.collectAsStateWithLifecycle()
    val allowlistedApps by viewModel.allowlistedApps.collectAsStateWithLifecycle()
    val techyState by viewModel.techyState.collectAsStateWithLifecycle()
    val throttleState by viewModel.throttleState.collectAsStateWithLifecycle()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsStateWithLifecycle()
    val deviceSensors by viewModel.deviceSensors.collectAsStateWithLifecycle()
    val liveSensors by viewModel.liveSensors.collectAsStateWithLifecycle()
    val batterySamples by viewModel.recentBatterySamples.collectAsStateWithLifecycle()
    val batteryAnalytics by viewModel.batteryAnalytics.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    // Keep navigation synchronized if onboarding is completed or reopened from Settings
    LaunchedEffect(preferences.isOnboardingComplete) {
        val onOnboarding = currentDest?.hasRoute<OnboardingRoute>() == true
        if (preferences.isOnboardingComplete && onOnboarding) {
            navController.navigate(DashboardRoute) {
                popUpTo(OnboardingRoute) { inclusive = true }
            }
        } else if (!preferences.isOnboardingComplete && currentDest != null && !onOnboarding) {
            navController.navigate(OnboardingRoute) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val tabs = buildList {
        add(
            TopLevelTab(
                route = DashboardRoute,
                labelResId = R.string.nav_dashboard,
                selectedIcon = Icons.Filled.Speed,
                unselectedIcon = Icons.Outlined.Speed,
                testTag = "tab_dashboard"
            )
        )
        add(
            TopLevelTab(
                route = SecurityRoute,
                labelResId = R.string.nav_security,
                selectedIcon = Icons.Filled.Security,
                unselectedIcon = Icons.Outlined.Security,
                testTag = "tab_security"
            )
        )
        if (preferences.appMode == AppMode.TECHY) {
            add(
                TopLevelTab(
                    route = TechySuiteRoute,
                    labelResId = R.string.nav_techy_suite,
                    selectedIcon = Icons.Filled.Terminal,
                    unselectedIcon = Icons.Outlined.Terminal,
                    testTag = "tab_techy_suite"
                )
            )
        }
        add(
            TopLevelTab(
                route = SettingsRoute,
                labelResId = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "tab_settings"
            )
        )
    }

    val showChrome = preferences.isOnboardingComplete && currentDest?.hasRoute<OnboardingRoute>() != true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null
                                    )
                                },
                                label = { AutoText(resId = tab.labelResId, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null
                                    )
                                },
                                label = { AutoText(resId = tab.labelResId, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (showChrome) {
                        TranslationStatusBanner()
                    }
                    NavHost(
                        navController = navController,
                        startDestination = if (preferences.isOnboardingComplete) DashboardRoute else OnboardingRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                preferences = preferences,
                                onSelectLanguage = viewModel::setLanguageMode,
                                onSelectMode = viewModel::setAppMode,
                                onSelectTheme = viewModel::setThemeMode,
                                onSelectAccent = viewModel::setAccentPreset,
                                onSelectTempUnit = viewModel::setTempUnit,
                                onSelectStorageUnit = viewModel::setStorageUnit,
                                onFinishOnboarding = {
                                    viewModel.completeOnboarding()
                                    navController.navigate(DashboardRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<DashboardRoute> {
                            DashboardScreen(
                                preferences = preferences,
                                telemetry = telemetry,
                                healthReport = healthReport,
                                latestBenchmark = latestBenchmark,
                                quickScan = quickScan,
                                longScan = longScan,
                                isIgnoringBatteryOpt = viewModel::isIgnoringBatteryOptimizations,
                                onMarkIntroSeen = viewModel::markTestMyPhoneIntroSeen,
                                onStartQuickScan = viewModel::startQuickScan,
                                onCancelQuickScan = viewModel::cancelQuickScan,
                                onStartLongScan = viewModel::startLongScan,
                                onCancelLongScan = viewModel::cancelLongScan
                            )
                        }

                        composable<SecurityRoute> {
                            SecurityScreen(
                                preferences = preferences,
                                antivirusState = antivirusState,
                                allowlistedApps = allowlistedApps,
                                isIgnoringBatteryOpt = viewModel::isIgnoringBatteryOptimizations,
                                onRunScan = viewModel::runAntivirusScan,
                                onToggleAutoScan = { enabled -> viewModel.setAutoScan(enabled) },
                                onAllowlistPackage = viewModel::allowlistApp,
                                onRemoveAllowlist = viewModel::removeFromAllowlist
                            )
                        }

                        composable<TechySuiteRoute> {
                            TechySuiteScreen(
                                preferences = preferences,
                                telemetry = telemetry,
                                hardwareSpecs = hardwareSpecs,
                                deviceSensors = deviceSensors,
                                liveSensors = liveSensors,
                                techyState = techyState,
                                throttleState = throttleState,
                                batterySamples = batterySamples,
                                batteryAnalytics = batteryAnalytics,
                                onRunCpu = viewModel::runTechyCpuBenchmark,
                                onRunRam = viewModel::runTechyRamBenchmark,
                                onRunStorage = viewModel::runTechyStorageBenchmark,
                                onSetGpuRunning = viewModel::setGpuStressRunning,
                                onUpdateGpuStats = viewModel::updateGpuStats,
                                onToggleThrottleTest = viewModel::toggleThermalThrottleTest,
                                onToggleLiveSensors = viewModel::toggleLiveSensorStream,
                                onStartBatteryTracker = viewModel::startBatteryTracker,
                                onStopBatteryTracker = viewModel::stopBatteryTracker,
                                onGenerateCsv = viewModel::generateBatteryCsv,
                                onGenerateJson = viewModel::generateBatteryJson
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                preferences = preferences,
                                isIgnoringBatteryOpt = viewModel::isIgnoringBatteryOptimizations,
                                onSelectLanguage = viewModel::setLanguageMode,
                                onSelectMode = viewModel::setAppMode,
                                onSelectTheme = viewModel::setThemeMode,
                                onSelectAccent = viewModel::setAccentPreset,
                                onSelectDensity = viewModel::setCardDensity,
                                onSelectTempUnit = viewModel::setTempUnit,
                                onSelectStorageUnit = viewModel::setStorageUnit,
                                onSetAlertsMuted = viewModel::setAlertsMuted,
                                onPreviewTone = viewModel::previewAlertSound,
                                onSetAutoScan = viewModel::setAutoScan,
                                onReopenOnboarding = viewModel::reopenOnboarding
                            )
                        }
                    }
                }
            }
        }
    }
}
