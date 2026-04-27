package com.voicelog.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.voicelog.R
import java.util.Locale

data class AppLanguageOption(
    val tag: String,
    @StringRes val displayNameRes: Int,
)

object AppLanguagePreferences {
    private const val PREFS_NAME = "voicelog_prefs"
    private const val KEY_APP_LANGUAGE_TAG = "app_language_tag"
    const val SYSTEM_LANGUAGE_TAG = ""
    const val ENGLISH_LANGUAGE_TAG = "en"
    const val KOREAN_LANGUAGE_TAG = "ko"

    val supportedOptions = listOf(
        AppLanguageOption(SYSTEM_LANGUAGE_TAG, R.string.language_system),
        AppLanguageOption(ENGLISH_LANGUAGE_TAG, R.string.language_english),
        AppLanguageOption(KOREAN_LANGUAGE_TAG, R.string.language_korean),
    )

    fun getLanguageTag(context: Context): String =
        getPrefs(context).getString(KEY_APP_LANGUAGE_TAG, SYSTEM_LANGUAGE_TAG)
            ?: SYSTEM_LANGUAGE_TAG

    fun setLanguageTag(context: Context, languageTag: String) {
        getPrefs(context).edit()
            .putString(KEY_APP_LANGUAGE_TAG, normalizedTag(languageTag))
            .apply()
    }

    fun applyStoredLanguage(context: Context) {
        applyLanguageTag(getLanguageTag(context))
    }

    fun applyAndStoreLanguage(context: Context, languageTag: String) {
        val normalized = normalizedTag(languageTag)
        setLanguageTag(context, normalized)
        applyLanguageTag(normalized)
    }

    fun getSummaryLanguageName(context: Context): String {
        return if (resolvedLanguageTag(context).startsWith(KOREAN_LANGUAGE_TAG)) {
            context.getString(R.string.language_name_korean)
        } else {
            context.getString(R.string.language_name_english)
        }
    }

    fun localizedContext(context: Context): Context {
        val languageTag = getLanguageTag(context)
        if (languageTag.isBlank()) {
            return context
        }

        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    private fun applyLanguageTag(languageTag: String) {
        val locales = if (languageTag.isBlank()) {
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
            LocaleListCompat.getEmptyLocaleList()
        } else {
            val locale = Locale.forLanguageTag(languageTag)
            Locale.setDefault(locale)
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun resolvedLanguageTag(context: Context): String {
        val stored = getLanguageTag(context)
        return if (stored.isNotBlank()) {
            stored
        } else {
            Locale.getDefault().language
        }
    }

    private fun normalizedTag(languageTag: String): String =
        supportedOptions.firstOrNull { it.tag == languageTag }?.tag ?: SYSTEM_LANGUAGE_TAG

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
