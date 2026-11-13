package com.benchy.X.locale

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.benchy.X.data.LanguageMode
import java.util.Locale

object LocaleManagerHelper {

    fun applyLanguageMode(mode: LanguageMode) {
        val locales = when (mode) {
            LanguageMode.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            LanguageMode.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            LanguageMode.FRENCH -> LocaleListCompat.forLanguageTags("fr")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getSystemLocale(): Locale {
        val systemLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        return systemLocales[0] ?: Locale.getDefault()
    }

    fun getDetectedSystemLanguageName(): String {
        val sys = getSystemLocale()
        val display = sys.getDisplayLanguage(sys).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(sys) else it.toString()
        }
        return display.ifBlank { "English" }
    }

    fun resolveEffectiveLanguageTag(mode: LanguageMode): String {
        return when (mode) {
            LanguageMode.ENGLISH -> "en"
            LanguageMode.FRENCH -> "fr"
            LanguageMode.FOLLOW_SYSTEM -> getSystemLocale().language.lowercase(Locale.ROOT)
        }
    }

    fun resolveEffectiveDisplayName(mode: LanguageMode): String {
        return when (mode) {
            LanguageMode.ENGLISH -> "English"
            LanguageMode.FRENCH -> "Français"
            LanguageMode.FOLLOW_SYSTEM -> getDetectedSystemLanguageName()
        }
    }

    fun requiresMlKitTranslation(mode: LanguageMode): Boolean {
        val tag = resolveEffectiveLanguageTag(mode)
        return tag != "en" && tag != "fr"
    }

    @Suppress("UNUSED_PARAMETER")
    fun currentAppLocaleTag(context: Context): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales[0]?.language?.lowercase(Locale.ROOT) ?: "en"
        }
        return getSystemLocale().language.lowercase(Locale.ROOT)
    }
}
