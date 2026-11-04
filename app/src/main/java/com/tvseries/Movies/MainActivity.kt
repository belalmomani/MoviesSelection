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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tvseries.Movies.ui.CrashScreen
import com.tvseries.Movies.ui.LocalLang
import com.tvseries.Movies.ui.MoviesRoot
import com.tvseries.Movies.ui.MoviesTheme
import com.tvseries.Movies.ui.TvRoot

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MoviesApp).container
        val startLang = LocaleHelper.savedLanguage(this)
        val pickFromWatchlist = intent?.getBooleanExtra("pick", false) == true
        // A real TV (or leanback device) gets the TV edition automatically.
        val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val autoTv = uiMode?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        setContent {
            val lang by container.prefs.language.collectAsState()
            val theme by container.prefs.theme.collectAsState()
            val tvPref by container.prefs.tvMode.collectAsState()
            val tv = tvPref == "on" || (tvPref == "auto" && autoTv)
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
                MoviesTheme(dark = dark) {
                    var crash by remember { mutableStateOf(runCatching { File(filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()) }
                    val c = crash
                    if (c != null) {
                        CrashScreen(c) {
                            runCatching { File(filesDir, CRASH_FILE).delete() }
                            crash = null
                        }
                    } else {
                        if (tv) TvRoot(container) else MoviesRoot(container, pickFromWatchlist)
                    }
                }
            }
        }
    }
}
