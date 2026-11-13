package com.benchy.X.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.benchyDataStore: DataStore<Preferences> by preferencesDataStore(name = "benchy_prefs")

enum class LanguageMode(val tag: String) {
    FOLLOW_SYSTEM("system"),
    ENGLISH("en"),
    FRENCH("fr")
}

enum class AppMode {
    USER_FRIENDLY,
    TECHY
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

enum class AccentPreset(val hex: Long, val label: String) {
    ELECTRIC_CYAN(0xFF00E5FF, "Electric Cyan"),
    COBALT_PULSE(0xFF2979FF, "Cobalt Pulse"),
    EMERALD_TELEMETRY(0xFF00E676, "Emerald Telemetry"),
    SOLAR_AMBER(0xFFFFB300, "Solar Amber")
}

enum class CardDensity {
    COMFORTABLE,
    COMPACT
}

enum class TempUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class StorageUnit {
    GB,
    GIB
}

data class UserPreferences(
    val isOnboardingComplete: Boolean = false,
    val languageMode: LanguageMode = LanguageMode.FOLLOW_SYSTEM,
    val appMode: AppMode = AppMode.USER_FRIENDLY,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.ELECTRIC_CYAN,
    val cardDensity: CardDensity = CardDensity.COMFORTABLE,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val storageUnit: StorageUnit = StorageUnit.GB,
    val alertsMuted: Boolean = false,
    val autoScanEnabled: Boolean = false,
    val autoScanIntervalHours: Int = 24,
    val skipSystemApps: Boolean = false,
    val hasSeenTestMyPhoneIntro: Boolean = false,
    val batteryTrackerActive: Boolean = false,
    val batteryTrackerStartMillis: Long = 0L
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val APP_MODE = stringPreferencesKey("app_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val CARD_DENSITY = stringPreferencesKey("card_density")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val STORAGE_UNIT = stringPreferencesKey("storage_unit")
        val ALERTS_MUTED = booleanPreferencesKey("alerts_muted")
        val AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
        val AUTO_SCAN_INTERVAL_HOURS = intPreferencesKey("auto_scan_interval_hours")
        val SKIP_SYSTEM_APPS = booleanPreferencesKey("skip_system_apps")
        val SEEN_TEST_MY_PHONE_INTRO = booleanPreferencesKey("has_seen_test_my_phone_intro")
        val BATTERY_TRACKER_ACTIVE = booleanPreferencesKey("battery_tracker_active")
        val BATTERY_TRACKER_START = stringPreferencesKey("battery_tracker_start_millis")
    }

    val preferences: Flow<UserPreferences> = context.benchyDataStore.data.map { prefs ->
        UserPreferences(
            isOnboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            languageMode = enumValueOrDefault(prefs[Keys.LANGUAGE_MODE], LanguageMode.FOLLOW_SYSTEM),
            appMode = enumValueOrDefault(prefs[Keys.APP_MODE], AppMode.USER_FRIENDLY),
            themeMode = enumValueOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            accentPreset = enumValueOrDefault(prefs[Keys.ACCENT_PRESET], AccentPreset.ELECTRIC_CYAN),
            cardDensity = enumValueOrDefault(prefs[Keys.CARD_DENSITY], CardDensity.COMFORTABLE),
            tempUnit = enumValueOrDefault(prefs[Keys.TEMP_UNIT], TempUnit.CELSIUS),
            storageUnit = enumValueOrDefault(prefs[Keys.STORAGE_UNIT], StorageUnit.GB),
            alertsMuted = prefs[Keys.ALERTS_MUTED] ?: false,
            autoScanEnabled = prefs[Keys.AUTO_SCAN_ENABLED] ?: false,
            autoScanIntervalHours = prefs[Keys.AUTO_SCAN_INTERVAL_HOURS] ?: 24,
            skipSystemApps = prefs[Keys.SKIP_SYSTEM_APPS] ?: false,
            hasSeenTestMyPhoneIntro = prefs[Keys.SEEN_TEST_MY_PHONE_INTRO] ?: false,
            batteryTrackerActive = prefs[Keys.BATTERY_TRACKER_ACTIVE] ?: false,
            batteryTrackerStartMillis = prefs[Keys.BATTERY_TRACKER_START]?.toLongOrNull() ?: 0L
        )
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.benchyDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.benchyDataStore.edit { it[Keys.LANGUAGE_MODE] = mode.name }
    }

    suspend fun setAppMode(mode: AppMode) {
        context.benchyDataStore.edit { it[Keys.APP_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.benchyDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.benchyDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setCardDensity(density: CardDensity) {
        context.benchyDataStore.edit { it[Keys.CARD_DENSITY] = density.name }
    }

    suspend fun setTempUnit(unit: TempUnit) {
        context.benchyDataStore.edit { it[Keys.TEMP_UNIT] = unit.name }
    }

    suspend fun setStorageUnit(unit: StorageUnit) {
        context.benchyDataStore.edit { it[Keys.STORAGE_UNIT] = unit.name }
    }

    suspend fun setAlertsMuted(muted: Boolean) {
        context.benchyDataStore.edit { it[Keys.ALERTS_MUTED] = muted }
    }

    suspend fun setAutoScan(enabled: Boolean, intervalHours: Int? = null) {
        context.benchyDataStore.edit {
            it[Keys.AUTO_SCAN_ENABLED] = enabled
            if (intervalHours != null) {
                it[Keys.AUTO_SCAN_INTERVAL_HOURS] = intervalHours
            }
        }
    }

    suspend fun setSkipSystemApps(skip: Boolean) {
        context.benchyDataStore.edit { it[Keys.SKIP_SYSTEM_APPS] = skip }
    }

    suspend fun markTestMyPhoneIntroSeen() {
        context.benchyDataStore.edit { it[Keys.SEEN_TEST_MY_PHONE_INTRO] = true }
    }

    suspend fun setBatteryTrackerState(active: Boolean, startMillis: Long) {
        context.benchyDataStore.edit {
            it[Keys.BATTERY_TRACKER_ACTIVE] = active
            it[Keys.BATTERY_TRACKER_START] = startMillis.toString()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
        if (raw == null) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
