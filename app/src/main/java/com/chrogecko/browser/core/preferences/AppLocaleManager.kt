package com.chrogecko.browser.core.preferences

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    CZECH("cs"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Coordinates the selected UI/engine language across synchronous startup configuration (API < 33),
 * Android 13+ per-app [LocaleManager] integration, live Compose [Resources] overrides, and
 * GeckoView's `Accept-Language` header.
 */
object AppLocaleManager {
    private const val PREFS_NAME = "chrogecko_locale"
    private const val KEY_LANGUAGE = "language_tag"

    fun readSavedTag(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.tag)
            .let { AppLanguage.fromTag(it).tag }

    fun writeSavedTag(context: Context, tag: String) {
        val normalized = AppLanguage.fromTag(tag).tag
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LANGUAGE, normalized)
        }
    }

    /**
     * Reads any per-app language override configured via Android 13+ system settings so the in-app
     * preference stays aligned with the OS chooser. Returns `null` below API 33.
     */
    fun syncFromSystemIfApplicable(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            ?: return null
        if (locales.isEmpty) return AppLanguage.SYSTEM.tag
        return when (locales[0]?.language?.lowercase(Locale.ROOT)) {
            AppLanguage.CZECH.tag -> AppLanguage.CZECH.tag
            AppLanguage.ENGLISH.tag -> AppLanguage.ENGLISH.tag
            else -> AppLanguage.SYSTEM.tag
        }
    }

    fun applyPlatformLocale(context: Context, language: AppLanguage) {
        writeSavedTag(context, language.tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            localeManager.applicationLocales = if (language == AppLanguage.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
        }
    }

    fun resolveLocale(tag: String): Locale {
        val language = AppLanguage.fromTag(tag)
        if (language == AppLanguage.SYSTEM) {
            val sysLocales = Resources.getSystem().configuration.locales
            return if (!sysLocales.isEmpty) sysLocales[0] else Locale.getDefault()
        }
        return Locale.forLanguageTag(language.tag)
    }

    fun localizedConfiguration(base: Configuration, tag: String): Configuration {
        val locale = resolveLocale(tag)
        return Configuration(base).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }

    fun localizedResources(context: Context, tag: String): Resources =
        context.createConfigurationContext(
            localizedConfiguration(context.resources.configuration, tag),
        ).resources

    fun wrapContext(base: Context, tag: String): Context {
        if (AppLanguage.fromTag(tag) == AppLanguage.SYSTEM) return base
        return base.createConfigurationContext(
            localizedConfiguration(base.resources.configuration, tag),
        )
    }

    fun geckoLocalesFor(language: AppLanguage): Array<String>? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.ENGLISH -> arrayOf("en-US", "en")
        AppLanguage.CZECH -> arrayOf("cs-CZ", "cs")
    }
}
