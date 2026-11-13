package com.benchy.X.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benchy.X.R
import com.benchy.X.data.AccentPreset
import com.benchy.X.data.AppMode
import com.benchy.X.data.CardDensity
import com.benchy.X.data.LanguageMode
import com.benchy.X.data.StorageUnit
import com.benchy.X.data.TempUnit
import com.benchy.X.data.ThemeMode
import com.benchy.X.data.UserPreferences
import com.benchy.X.locale.AutoText
import com.benchy.X.locale.LocaleManagerHelper
import com.benchy.X.locale.TranslationEngine
import com.benchy.X.locale.TranslationModelState
import com.benchy.X.notifications.NotificationHelper
import com.benchy.X.ui.components.BatteryOptExemptionDialog
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    isIgnoringBatteryOpt: () -> Boolean,
    onSelectLanguage: (LanguageMode) -> Unit,
    onSelectMode: (AppMode) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectAccent: (AccentPreset) -> Unit,
    onSelectDensity: (CardDensity) -> Unit,
    onSelectTempUnit: (TempUnit) -> Unit,
    onSelectStorageUnit: (StorageUnit) -> Unit,
    onSetAlertsMuted: (Boolean) -> Unit,
    onPreviewTone: (Boolean) -> Unit,
    onTestNotification: (Boolean) -> Unit,
    onSetAutoScan: (Boolean, Int) -> Unit,
    onSetSkipSystemApps: (Boolean) -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val metrics = LocalBenchyLayoutMetrics.current
    val translationState by remember(context) {
        TranslationEngine.getInstance(context)
    }.state.collectAsStateWithLifecycle()
    val detectedSystemLang = remember { LocaleManagerHelper.getDetectedSystemLanguageName() }
    var showBatteryOptForAutoScan by remember { mutableStateOf(false) }
    var showCustomIntervalDialog by remember { mutableStateOf(false) }
    var customHoursInput by remember(preferences.autoScanIntervalHours) {
        mutableStateOf(preferences.autoScanIntervalHours.toString())
    }
    var activeTonePreview by remember { mutableStateOf<String?>(null) }
    var pendingTestAlertPositive by remember { mutableStateOf<Boolean?>(null) }
    var diagRefreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                diagRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        diagRefreshTick++
        val pendingIsPositive = pendingTestAlertPositive
        pendingTestAlertPositive = null
        if (granted && pendingIsPositive != null) {
            onTestNotification(pendingIsPositive)
            scope.launch {
                activeTonePreview = if (pendingIsPositive) {
                    "Dispatched system notification (Ding-Ding channel)"
                } else {
                    "Dispatched system notification (Dong-Dong channel)"
                }
                delay(1800L)
                activeTonePreview = null
            }
        }
    }

    fun triggerLiveTestNotification(isPositive: Boolean) {
        if (!NotificationHelper.canPostNotifications(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingTestAlertPositive = isPositive
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        onTestNotification(isPositive)
        diagRefreshTick++
        scope.launch {
            activeTonePreview = if (isPositive) {
                "Dispatched system notification (Ding-Ding channel)"
            } else {
                "Dispatched system notification (Dong-Dong channel)"
            }
            delay(1800L)
            activeTonePreview = null
        }
    }

    if (showBatteryOptForAutoScan) {
        BatteryOptExemptionDialog(
            bodyResId = R.string.battery_opt_autoscan_body,
            onDismiss = { showBatteryOptForAutoScan = false },
            onContinue = {
                showBatteryOptForAutoScan = false
                onSetAutoScan(true, preferences.autoScanIntervalHours)
            }
        )
    }

    if (showCustomIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showCustomIntervalDialog = false },
            title = { AutoText(resId = R.string.settings_autoscan_custom_title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHoursInput,
                        onValueChange = { raw ->
                            customHoursInput = raw.filter { it.isDigit() }.take(3)
                        },
                        label = { AutoText(resId = R.string.settings_autoscan_custom_hint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_autoscan_hours_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 4, 6, 8, 72).forEach { quickH ->
                            FilterChip(
                                selected = customHoursInput == quickH.toString(),
                                onClick = { customHoursInput = quickH.toString() },
                                label = { Text("${quickH}h") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customHoursInput.toIntOrNull()?.coerceIn(1, 720) ?: 24
                        onSetAutoScan(preferences.autoScanEnabled, parsed)
                        showCustomIntervalDialog = false
                    },
                    modifier = Modifier.testTag("custom_autoscan_save_btn")
                ) {
                    AutoText(resId = R.string.action_done)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomIntervalDialog = false }) {
                    AutoText(resId = R.string.action_cancel)
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
            item {
                AutoText(
                    resId = R.string.settings_title,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // 1. Language Picker + ML Kit Status
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.settings_language_section,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.languageMode == LanguageMode.FOLLOW_SYSTEM,
                            onClick = { onSelectLanguage(LanguageMode.FOLLOW_SYSTEM) },
                            label = {
                                AutoText(text = "${stringResource(R.string.onboarding_lang_follow_system)} ($detectedSystemLang)")
                            },
                            modifier = Modifier.testTag("settings_lang_system")
                        )
                        FilterChip(
                            selected = preferences.languageMode == LanguageMode.ENGLISH,
                            onClick = { onSelectLanguage(LanguageMode.ENGLISH) },
                            label = { Text("English") },
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                        FilterChip(
                            selected = preferences.languageMode == LanguageMode.FRENCH,
                            onClick = { onSelectLanguage(LanguageMode.FRENCH) },
                            label = { Text("Français") },
                            modifier = Modifier.testTag("settings_lang_fr")
                        )
                    }

                    when (val ts = translationState) {
                        is TranslationModelState.Ready -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            AutoText(
                                resId = R.string.translation_ready_status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                formatArgs = arrayOf(ts.languageName)
                            )
                        }
                        is TranslationModelState.Downloading -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            AutoText(
                                resId = R.string.translation_downloading_banner,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                formatArgs = arrayOf(ts.languageName)
                            )
                        }
                        is TranslationModelState.ErrorFallback -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            AutoText(
                                resId = R.string.translation_error_status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TranslationModelState.Native -> Unit
                    }
                }
            }

            // 2. Mode Switcher (User-Friendly vs Techy)
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.settings_mode_section,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.appMode == AppMode.USER_FRIENDLY,
                            onClick = { onSelectMode(AppMode.USER_FRIENDLY) },
                            label = { AutoText(resId = R.string.mode_user_friendly_title) },
                            modifier = Modifier.testTag("settings_mode_friendly")
                        )
                        FilterChip(
                            selected = preferences.appMode == AppMode.TECHY,
                            onClick = { onSelectMode(AppMode.TECHY) },
                            label = { AutoText(resId = R.string.mode_techy_title) },
                            modifier = Modifier.testTag("settings_mode_techy")
                        )
                    }
                }
            }

            // 3. Theme, Accent Color, and Card Density
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.settings_appearance_section,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.AMOLED -> "AMOLED Black"
                            }
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSelectTheme(mode) },
                                label = { AutoText(text = label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    AutoText(resId = R.string.pref_accent_color, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preferences.accentPreset == preset,
                                onClick = { onSelectAccent(preset) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.hex))
                                    )
                                },
                                label = { AutoText(text = preset.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    AutoText(resId = R.string.settings_card_density, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.cardDensity == CardDensity.COMFORTABLE,
                            onClick = { onSelectDensity(CardDensity.COMFORTABLE) },
                            label = { AutoText(text = "Comfortable") }
                        )
                        FilterChip(
                            selected = preferences.cardDensity == CardDensity.COMPACT,
                            onClick = { onSelectDensity(CardDensity.COMPACT) },
                            label = { AutoText(text = "Compact") }
                        )
                    }
                }
            }

            // 4. Units
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Straighten, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.settings_units_section,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AutoText(resId = R.string.pref_temp_unit, style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = preferences.tempUnit == TempUnit.CELSIUS,
                                    onClick = { onSelectTempUnit(TempUnit.CELSIUS) },
                                    label = { Text("°C") }
                                )
                                FilterChip(
                                    selected = preferences.tempUnit == TempUnit.FAHRENHEIT,
                                    onClick = { onSelectTempUnit(TempUnit.FAHRENHEIT) },
                                    label = { Text("°F") }
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            AutoText(resId = R.string.pref_storage_unit, style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = preferences.storageUnit == StorageUnit.GB,
                                    onClick = { onSelectStorageUnit(StorageUnit.GB) },
                                    label = { Text("GB") }
                                )
                                FilterChip(
                                    selected = preferences.storageUnit == StorageUnit.GIB,
                                    onClick = { onSelectStorageUnit(StorageUnit.GIB) },
                                    label = { Text("GiB") }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Alert Sound Preview/Mute & Antivirus Auto-Scan Schedule
            item {
                BenchyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        AutoText(
                            resId = R.string.settings_alerts_section,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onPreviewTone(true)
                                scope.launch {
                                    activeTonePreview = "Playing Ding-Ding (1175Hz -> 1568Hz)…"
                                    delay(900L)
                                    activeTonePreview = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_ding_btn")
                        ) {
                            Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_preview_good)
                        }
                        OutlinedButton(
                            onClick = {
                                onPreviewTone(false)
                                scope.launch {
                                    activeTonePreview = "Playing Dong-Dong (440Hz -> 330Hz)…"
                                    delay(950L)
                                    activeTonePreview = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_dong_btn")
                        ) {
                            Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_preview_bad)
                        }
                    }

                    activeTonePreview?.let { status ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { triggerLiveTestNotification(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_live_notif_btn")
                        ) {
                            Icon(Icons.Outlined.Notifications, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_test_notif)
                        }
                        OutlinedButton(
                            onClick = { triggerLiveTestNotification(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_live_bad_notif_btn")
                        ) {
                            Icon(Icons.Outlined.Notifications, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_test_notif_bad)
                        }
                    }

                    val notifDiag = remember(activeTonePreview, preferences.alertsMuted, diagRefreshTick) {
                        NotificationHelper.inspectNotificationAudioState(context)
                    }
                    if (!notifDiag.permissionGranted || !notifDiag.notificationsEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OS Notification permission is off — grant notifications so Benchy can post audible alerts.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                if (!notifDiag.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        ) {
                            Text("Enable Notification Permission")
                        }
                    } else if (!notifDiag.channelImportanceOk || !notifDiag.channelHasSound) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Benchy alert channel is silenced in Android Notification Channel Settings.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_GOOD_ALERTS)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        ) {
                            Text("Open Channel Sound Settings")
                        }
                    } else if (notifDiag.notifStreamVolume == 0 || !notifDiag.ringerNormal || notifDiag.dndActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Notification Volume is ${notifDiag.notifStreamVolume}/${notifDiag.notifStreamMax} (Ringer Normal=${notifDiag.ringerNormal}, DND=${notifDiag.dndActive}) — system notifications are currently muted by Android.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    NotificationHelper.raiseNotificationStreamVolume(context)
                                    diagRefreshTick++
                                }
                            ) {
                                Text("Raise Notification Vol")
                            }
                            TextButton(
                                onClick = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                                }
                            ) {
                                Text("Sound Settings")
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Channel v6 Ready • Notification Vol ${notifDiag.notifStreamVolume}/${notifDiag.notifStreamMax}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoText(
                            resId = R.string.settings_mute_alerts,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = preferences.alertsMuted,
                            onCheckedChange = onSetAlertsMuted,
                            modifier = Modifier.testTag("mute_alerts_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AutoText(
                                resId = R.string.pref_skip_system_apps,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            AutoText(
                                resId = R.string.pref_skip_system_apps_desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.skipSystemApps,
                            onCheckedChange = onSetSkipSystemApps,
                            modifier = Modifier.testTag("settings_skip_system_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoText(
                            resId = R.string.security_auto_scan_title,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = preferences.autoScanEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !isIgnoringBatteryOpt()) {
                                    showBatteryOptForAutoScan = true
                                } else {
                                    onSetAutoScan(enable, preferences.autoScanIntervalHours)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    AutoText(resId = R.string.settings_autoscan_interval, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    val presetHours = listOf(12, 24, 48, 168)
                    val isCustomInterval = preferences.autoScanIntervalHours !in presetHours
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.animateContentSize()
                    ) {
                        presetHours.forEach { hours ->
                            val label = if (hours == 168) "7 Days" else "${hours}h"
                            FilterChip(
                                selected = preferences.autoScanIntervalHours == hours,
                                onClick = { onSetAutoScan(preferences.autoScanEnabled, hours) },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = isCustomInterval,
                            onClick = { showCustomIntervalDialog = true },
                            label = {
                                if (isCustomInterval) {
                                    Text("${preferences.autoScanIntervalHours}h (Custom)")
                                } else {
                                    AutoText(resId = R.string.settings_autoscan_custom)
                                }
                            },
                            modifier = Modifier.testTag("autoscan_custom_chip")
                        )
                    }
                }
            }

            // 6. Re-open Onboarding
            item {
                OutlinedButton(
                    onClick = onReopenOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("reopen_onboarding_btn")
                ) {
                    Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    AutoText(resId = R.string.settings_reopen_onboarding)
                }
            }
        }
    }
}
