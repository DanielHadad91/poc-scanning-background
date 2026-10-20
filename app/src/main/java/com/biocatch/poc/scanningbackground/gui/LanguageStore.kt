package com.biocatch.poc.scanningbackground.gui

import android.content.Context
import com.biocatch.poc.scanningbackground.model.AppLanguage

/**
 * The UI language choice. This is a presentation concern that belongs to the app, not the SDK, so it
 * lives here in its own preferences file rather than in the SDK's SettingsStore.
 */
class LanguageStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = prefs.getString(KEY_LANGUAGE, null)
            ?.let { name -> runCatching { AppLanguage.valueOf(name) }.getOrNull() }
            ?: AppLanguage.SYSTEM
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    private companion object {
        const val PREFS_NAME = "ui_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}
