package com.tvseries.Movies

import android.app.Application
import android.content.Context
import com.tvseries.Movies.ads.AdsModule
import com.tvseries.Movies.ads.AdsProvider
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.WorkCard
import kotlinx.coroutines.flow.MutableStateFlow
import com.tvseries.Movies.data.DiscoverSession
import com.tvseries.Movies.data.Prefs
import com.tvseries.Movies.data.SharedPrefsStore
import com.tvseries.Movies.data.TmdbClient
import com.tvseries.Movies.data.UpdateClient
import com.tvseries.Movies.data.UpdateException
import com.tvseries.Movies.data.UpdateInfo
import com.tvseries.Movies.data.Updates
import com.tvseries.Movies.data.TmdbRepository
import com.tvseries.Movies.data.WikidataClient

class AppContainer(app: Application) {
    val prefs = Prefs(SharedPrefsStore(app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)))
    private val http = TmdbClient.buildHttp(app.cacheDir)
    val client = TmdbClient(http, { prefs.apiKey.value.ifBlank { BuildConfig.TMDB_API_KEY } })
    val repo = TmdbRepository(client, prefs)
    val wikidata = WikidataClient(http)
    val session = DiscoverSession(repo, prefs)

    /** Plain client (no cache): update checks must always hit the network. */
    val updates = UpdateClient(
        okhttp3.OkHttpClient.Builder().connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).callTimeout(10, java.util.concurrent.TimeUnit.MINUTES).build(),
        BuildConfig.UPDATE_REPO,
        { prefs.updateToken.value },
    )
    val ads: AdsProvider = AdsModule.provide(BuildConfig.START_IO_APP_ID)

    /** A newer build published on GitHub (null = none / unknown). Drives the badge on the Settings tab. */
    val updateAvailable = MutableStateFlow<UpdateInfo?>(null)

    suspend fun checkUpdateQuietly() {
        try {
            val info = updates.latest()
            updateAvailable.value = info?.takeIf { Updates.isNewer(it.build, BuildConfig.BUILD_NUMBER) }
        } catch (e: UpdateException) {
            // silent: the Settings page shows the reason when the user checks manually
        }
    }

    /** One-shot selection handed from Home to the filter screen. */
    var presetSelection: Selection? = null

    /** Titles handed to the final filtering stage (set by the filter screen). */
    var finalCandidates: List<WorkCard> = emptyList()

    /** Titles chosen for side-by-side comparison (max 3). */
    val compare = MutableStateFlow<List<WorkCard>>(emptyList())

    fun toggleCompare(card: WorkCard) {
        val cur = compare.value
        compare.value = if (cur.any { it.key == card.key }) cur.filterNot { it.key == card.key } else (cur + card).takeLast(3)
    }

    val hasBuiltInKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    companion object {
        const val PREFS_NAME = "movies_settings"
    }
}

const val CRASH_FILE = "last_crash.txt"

class MoviesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Save any crash to a file so the next launch can show it (phone-only debugging).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { java.io.File(filesDir, CRASH_FILE).writeText(android.util.Log.getStackTraceString(e)) }
            previous?.uncaughtException(t, e)
        }
        container = AppContainer(this)
        runCatching { container.ads.initialize(this) }
    }
}
