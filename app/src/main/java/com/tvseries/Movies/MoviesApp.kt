package com.tvseries.Movies

import android.app.Application
import android.content.Context
import com.tvseries.Movies.ads.AdsModule
import com.tvseries.Movies.ads.AdsProvider
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.Prefs
import com.tvseries.Movies.data.SharedPrefsStore
import com.tvseries.Movies.data.TmdbClient
import com.tvseries.Movies.data.TmdbRepository

class AppContainer(app: Application) {
    val prefs = Prefs(SharedPrefsStore(app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)))
    private val http = TmdbClient.buildHttp(app.cacheDir)
    val client = TmdbClient(http, { prefs.apiKey.value.ifBlank { BuildConfig.TMDB_API_KEY } })
    val repo = TmdbRepository(client, prefs)
    val ads: AdsProvider = AdsModule.provide(BuildConfig.START_IO_APP_ID)

    /** One-shot selection handed from Home to the filter screen. */
    var presetSelection: Selection? = null

    val hasBuiltInKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    companion object {
        const val PREFS_NAME = "movies_settings"
    }
}

class MoviesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.ads.initialize(this)
    }
}
