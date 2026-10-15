package com.tvseries.Movies

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the saved app language (Arabic = RTL, English = LTR) to the activity context. */
object LocaleHelper {
    fun savedLanguage(base: Context): String =
        base.getSharedPreferences(AppContainer.PREFS_NAME, Context.MODE_PRIVATE).getString("language", "ar") ?: "ar"

    @Suppress("DEPRECATION")
    fun wrap(base: Context): Context {
        val locale = Locale(savedLanguage(base))
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }
}
