package com.tvseries.Movies

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tvseries.Movies.ui.LocalLang
import com.tvseries.Movies.ui.MoviesRoot
import com.tvseries.Movies.ui.MoviesTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MoviesApp).container
        val startLang = LocaleHelper.savedLanguage(this)
        setContent {
            val lang by container.prefs.language.collectAsState()
            val theme by container.prefs.theme.collectAsState()
            // Language changed in Settings: recreate so the new locale/layout direction is applied everywhere.
            LaunchedEffect(lang) { if (lang != startLang) recreate() }
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(
                LocalLang provides lang,
                LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MoviesTheme(dark = dark) { MoviesRoot(container) }
            }
        }
    }
}
