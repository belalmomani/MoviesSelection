package com.tvseries.Movies

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tvseries.Movies.ui.ConsentScreen
import com.tvseries.Movies.ui.CrashScreen
import com.tvseries.Movies.ui.LocalLang
import com.tvseries.Movies.ui.MoviesRoot
import com.tvseries.Movies.ui.MoviesTheme
import com.tvseries.Movies.ui.TvPageButtons
import com.tvseries.Movies.ui.TvRoot

class MainActivity : ComponentActivity() {
    private var isTvDevice = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun sendKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    /** Moves focus about a screenful up or down by replaying D-pad presses: works on every screen, no per-screen code. */
    private fun page(up: Boolean) {
        val code = if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        for (i in 0 until PAGE_STEPS) mainHandler.postDelayed({ sendKey(code) }, i * PAGE_STEP_DELAY_MS)
    }

    /** On a TV the remote's page / channel keys scroll a screenful (invisible, never in the way). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_PAGE_UP || event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
        val down = event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN
        if (isTvDevice && (up || down)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) page(up)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

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
        isTvDevice = autoTv
        setContent {
            val lang by container.prefs.language.collectAsState()
            val theme by container.prefs.theme.collectAsState()
            val tvPref by container.prefs.tvMode.collectAsState()
            val tv = tvPref == "on" || (tvPref == "auto" && autoTv)
            val tvNav by container.prefs.tvNav.collectAsState()
            val adsConsent by container.prefs.adsConsent.collectAsState()
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
                  // Targeting Android 15+ draws edge-to-edge: keep content inside the system bars / cutouts / keyboard.
                  androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                  ) {
                    var crash by remember { mutableStateOf(runCatching { File(filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()) }
                    val c = crash
                    if (c != null) {
                        CrashScreen(c) {
                            runCatching { File(filesDir, CRASH_FILE).delete() }
                            crash = null
                        }
                    } else if (BuildConfig.ADS_ENABLED && !tv && adsConsent == "unset") {
                        ConsentScreen { personalized ->
                            container.prefs.setAdsConsent(if (personalized) "yes" else "no")
                            runCatching { container.ads.initialize(applicationContext, personalized) }
                        }
                    } else {
                        if (tv) TvRoot(container) else MoviesRoot(container, pickFromWatchlist)
                    }
                    if (c == null && isTvDevice && tvNav && !tv) {
                        TvPageButtons(onUp = { page(true) }, onDown = { page(false) }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd))
                    }
                  }
                }
            }
        }
    }

    private companion object {
        const val PAGE_STEPS = 6
        const val PAGE_STEP_DELAY_MS = 45L
    }
}
