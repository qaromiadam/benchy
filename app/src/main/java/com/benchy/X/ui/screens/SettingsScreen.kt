package com.benchy.X.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.benchy.X.ui.components.BatteryOptExemptionDialog
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics

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
    onSetAutoScan: (Boolean, Int) -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val metrics = LocalBenchyLayoutMetrics.current
    val translationState by remember(context) {
        TranslationEngine.getInstance(context)
    }.state.collectAsStateWithLifecycle()
    val detectedSystemLang = remember { LocaleManagerHelper.getDetectedSystemLanguageName() }
    var showBatteryOptForAutoScan by remember { mutableStateOf(false) }

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
                            onClick = { onPreviewTone(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_ding_btn")
                        ) {
                            Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_preview_good)
                        }
                        OutlinedButton(
                            onClick = { onPreviewTone(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_dong_btn")
                        ) {
                            Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            AutoText(resId = R.string.settings_preview_bad)
                        }
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12, 24, 48, 168).forEach { hours ->
                            val label = if (hours == 168) "7 Days" else "${hours}h"
                            FilterChip(
                                selected = preferences.autoScanIntervalHours == hours,
                                onClick = { onSetAutoScan(preferences.autoScanEnabled, hours) },
                                label = { Text(label) }
                            )
                        }
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
