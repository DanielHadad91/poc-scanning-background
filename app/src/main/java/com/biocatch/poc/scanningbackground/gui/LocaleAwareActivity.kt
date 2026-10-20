package com.biocatch.poc.scanningbackground.gui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.biocatch.poc.scanningbackground.model.AppLanguage
import java.util.Locale

/**
 * Applies the chosen UI language. SYSTEM leaves the device language as is; HEBREW/ENGLISH force that
 * language (and its layout direction) on every Android version. Views that must stay left-to-right
 * (the URL field, the clock) keep an explicit textDirection="ltr" in the layout.
 */
abstract class LocaleAwareActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = LanguageStore(newBase).language.tag
        if (tag == null) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /** True when the effective UI language is Hebrew, used to pick guide content. */
    protected fun isHebrew(): Boolean {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
        return locale.language == "iw" || locale.language == "he"
    }

    protected fun currentLanguage(): AppLanguage = LanguageStore(this).language
}
