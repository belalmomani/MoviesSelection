@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.tvseries.Movies.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import androidx.compose.foundation.layout.fillMaxSize
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.GemFinder
import com.tvseries.Movies.data.ContentType
import com.tvseries.Movies.data.ActorEntry
import com.tvseries.Movies.data.ActorPool
import com.tvseries.Movies.data.CardPool
import com.tvseries.Movies.data.ListItemDto
import com.tvseries.Movies.data.PersonScore
import com.tvseries.Movies.data.TopLists
import com.tvseries.Movies.data.WatchedProfile
import com.tvseries.Movies.data.TaxonomySeed
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val error: TmdbError) : Load<Nothing>
}

/** Suggestions of one genre the user watches a lot ([count] of their watched titles are of it). */
class ForYouSection(val genreId: Int, val count: Int, val cards: List<WorkCard>)

/** [known] = watched titles whose genres are known; [sections] stays empty until there are enough of them. */
class ForYou(val known: Int, val sections: List<ForYouSection>)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container

    class Section(val id: String, val path: String, val titleAr: String, val titleEn: String)

    val sections = listOf(
        Section("trending", "trending/all/week", "الرائج هذا الأسبوع", "Trending this week"),
        Section("popular_movie", "movie/popular", "أفلام شائعة", "Popular movies"),
        Section("popular_tv", "tv/popular", "مسلسلات شائعة", "Popular series"),
        Section("now_playing", "movie/now_playing", "أحدث الأفلام في العرض", "Latest in cinemas"),
        Section("on_air", "tv/on_the_air", "مسلسلات تُعرض حاليًا", "Series on air now"),
        Section("top_movie", "movie/top_rated", "الأعلى تقييمًا (أفلام)", "Top rated movies"),
    )

    private val flows = sections.associate { it.id to MutableStateFlow<Load<List<WorkCard>>>(Load.Loading) }
    fun flow(id: String): StateFlow<Load<List<WorkCard>>> = flows.getValue(id).asStateFlow()

    fun loadAll() {
        for (s in sections) load(s)
    }

    private fun load(s: Section) {
        val f = flows.getValue(s.id)
        viewModelScope.launch {
            f.value = Load.Loading
            f.value = try {
                val default = if (s.path.startsWith("tv")) "tv" else "movie"
                Load.Ok(c.repo.list(s.path).results.mapNotNull { it.toCard(default) })
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    val savedFilters = c.prefs.savedFilters
    val contentType = c.prefs.contentType
    private var gemsType: String? = null

    private val _gems = MutableStateFlow<Load<List<WorkCard>>>(Load.Loading)
    val gems: StateFlow<Load<List<WorkCard>>> = _gems.asStateFlow()

    fun loadGems(force: Boolean = false) {
        val t = c.prefs.contentType.value
        if (!force && gemsType == t && _gems.value !is Load.Fail) return
        gemsType = t
        viewModelScope.launch {
            _gems.value = Load.Loading
            _gems.value = try {
                Load.Ok(fetchGems())
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    /** Well rated, moderately voted, not the most popular. Ranked by a vote-weighted score, not rating alone. */
    private suspend fun fetchGems(): List<WorkCard> {
        val q = c.repo.baseQuery().copy(minVotes = 1500, maxVotes = 20000, minRating = 7, sort = "vote_average.desc")
        val items = ArrayList<Pair<String, ListItemDto>>()
        val medias = if (c.prefs.contentType.value == "all") listOf("movie", "tv") else listOf(c.prefs.contentType.value)
        for (media in medias) for (page in 1..2) c.repo.discover(media, page, q).results.forEach { items += media to it }
        val watched = c.prefs.watched.value
        return items
            .filter { (_, d) -> GemFinder.isGem(d.voteAverage ?: 0.0, d.voteCount ?: 0) }
            .sortedByDescending { (_, d) -> GemFinder.score(d.voteAverage ?: 0.0, d.voteCount ?: 0) }
            .mapNotNull { (m, d) -> d.toCard(m) }
            .distinctBy { it.key }
            .filter { it.key !in watched }
            .take(24)
    }

    // ---- Titles rated 8 and above, and well-known actors rated 7 and above ----

    private val _top8Movies = MutableStateFlow<Load<List<WorkCard>>>(Load.Loading)
    val top8Movies: StateFlow<Load<List<WorkCard>>> = _top8Movies.asStateFlow()
    private val _top8Series = MutableStateFlow<Load<List<WorkCard>>>(Load.Loading)
    val top8Series: StateFlow<Load<List<WorkCard>>> = _top8Series.asStateFlow()
    private var top8Started = false

    fun loadTop8(force: Boolean = false) {
        if (top8Started && !force && _top8Movies.value !is Load.Fail && _top8Series.value !is Load.Fail) return
        top8Started = true
        loadTop8("movie", _top8Movies)
        loadTop8("tv", _top8Series)
    }

    private fun loadTop8(media: String, target: MutableStateFlow<Load<List<WorkCard>>>) {
        viewModelScope.launch {
            target.value = Load.Loading
            target.value = try {
                Load.Ok(fetchTop8(media))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    private suspend fun fetchTop8(media: String): List<WorkCard> {
        val q = TopLists.titleQuery(c.repo.baseQuery(), media)
        val out = ArrayList<WorkCard>()
        for (page in 1..TOP8_PAGES) {
            out += c.repo.discover(media, page, q).results.mapNotNull { it.toCard(media) }
        }
        return out.filter { (it.rating ?: 0.0) >= TopLists.MIN_TITLE_RATING }.distinctBy { it.key }
    }

    // ---- "What you watched": suggestions of the genres the user watches most ----

    val watchedKeys: StateFlow<Set<String>> get() = c.prefs.watched

    private val _forYou = MutableStateFlow<Load<ForYou>>(Load.Loading)
    val forYou: StateFlow<Load<ForYou>> = _forYou.asStateFlow()
    private var forYouJob: Job? = null
    private var forYouFor: Set<String>? = null

    fun loadForYou(force: Boolean = false) {
        val keys = c.prefs.watched.value
        if (!force && keys == forYouFor && _forYou.value is Load.Ok) return
        forYouJob?.cancel()
        forYouFor = keys
        forYouJob = viewModelScope.launch {
            if (_forYou.value !is Load.Ok) _forYou.value = Load.Loading
            _forYou.value = try {
                Load.Ok(buildForYou(keys))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    private suspend fun buildForYou(keys: Set<String>): ForYou {
        val genres = genresOfWatched(keys.toList().take(WATCHED_LOOKUP_MAX))
        val top = WatchedProfile.top(genres.values.toList())
        if (top.isEmpty()) return ForYou(genres.size, emptyList())
        val sections = top.map { t -> ForYouSection(t.genreId, t.count, suggestionsFor(t.genreId, keys)) }.filter { it.cards.isNotEmpty() }
        return ForYou(genres.size, sections)
    }

    /** Genre ids of each watched title: from the copy kept on the device, asking TMDB only for the ones not seen before. */
    private suspend fun genresOfWatched(keys: List<String>): Map<String, List<Int>> {
        val cache = HashMap(c.prefs.watchedGenres())
        val missing = keys.filter { it !in cache }
        if (missing.isNotEmpty()) {
            val gate = Semaphore(ACTOR_PARALLEL)
            val fetched = coroutineScope {
                missing.map { key ->
                    async {
                        gate.withPermit {
                            val id = key.substringAfter(':').toIntOrNull()
                            if (id == null) {
                                null
                            } else {
                                try {
                                    key to c.repo.details(key.substringBefore(':'), id).genres.orEmpty().map { it.id }
                                } catch (e: TmdbError) {
                                    null
                                }
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            for ((key, ids) in fetched) cache[key] = ids
            if (fetched.isNotEmpty()) c.prefs.setWatchedGenres(cache)
        }
        return keys.mapNotNull { key -> cache[key]?.let { key to it } }.toMap()
    }

    private suspend fun suggestionsFor(filmGenreId: Int, watched: Set<String>): List<WorkCard> {
        val base = c.repo.baseQuery()
        val perKind = listOf("movie", "tv").map { media ->
            val q = WatchedProfile.suggestionQuery(base, media, filmGenreId)
            if (q == null) emptyList() else c.repo.discover(media, 1, q).results.mapNotNull { it.toCard(media) }.filter { it.key !in watched }
        }
        return WatchedProfile.mix(perKind[0], perKind[1], FOR_YOU_SIZE)
    }

    // ---- 10 movies and 10 series a day, rated 8 and above, picked from a pool kept on the device for a week ----

    private val _dailyMovies = MutableStateFlow<Load<List<WorkCard>>>(Load.Loading)
    val dailyMovies: StateFlow<Load<List<WorkCard>>> = _dailyMovies.asStateFlow()
    private val _dailySeries = MutableStateFlow<Load<List<WorkCard>>>(Load.Loading)
    val dailySeries: StateFlow<Load<List<WorkCard>>> = _dailySeries.asStateFlow()
    private var dailyStarted = false

    fun loadDaily(force: Boolean = false) {
        if (dailyStarted && !force && _dailyMovies.value !is Load.Fail && _dailySeries.value !is Load.Fail) return
        dailyStarted = true
        loadDaily("movie", _dailyMovies)
        loadDaily("tv", _dailySeries)
    }

    private fun loadDaily(media: String, target: MutableStateFlow<Load<List<WorkCard>>>) {
        viewModelScope.launch {
            val today = TopLists.today()
            val stored = c.prefs.cardPool(media)
            if (TopLists.cardPoolIsFresh(stored, today)) {
                target.value = Load.Ok(stored?.cards.orEmpty().distinctBy { it.key })
                return@launch
            }
            target.value = Load.Loading
            target.value = try {
                val built = fetchTitlePool(media)
                if (built.isNotEmpty()) c.prefs.setCardPool(media, CardPool(today, built))
                Load.Ok(built)
            } catch (e: TmdbError) {
                // The network failed: keep showing the last scan if there is one.
                if (stored != null && stored.cards.isNotEmpty()) Load.Ok(stored.cards) else Load.Fail(e)
            }
        }
    }

    private suspend fun fetchTitlePool(media: String): List<WorkCard> {
        val q = TopLists.titleQuery(c.repo.baseQuery(), media)
        val out = ArrayList<WorkCard>()
        for (page in 1..TITLE_POOL_PAGES) {
            out += c.repo.discover(media, page, q).results.mapNotNull { it.toCard(media) }
        }
        return out.filter { (it.rating ?: 0.0) >= TopLists.MIN_TITLE_RATING }.distinctBy { it.key }
    }

    private val _actors = MutableStateFlow<Load<List<ActorEntry>>>(Load.Loading)
    val actors: StateFlow<Load<List<ActorEntry>>> = _actors.asStateFlow()
    private var actorsJob: Job? = null

    /** Loads the actor pool: the copy kept on the device when it is under a week old, otherwise a fresh scan of TMDB. */
    fun loadActors(force: Boolean = false) {
        if (!force && (actorsJob?.isActive == true || _actors.value is Load.Ok)) return
        actorsJob = viewModelScope.launch {
            val today = TopLists.today()
            val stored = c.prefs.actorPool()
            if (!force && TopLists.poolIsFresh(stored, today)) {
                _actors.value = Load.Ok(stored?.actors.orEmpty().distinctBy { it.id })
                return@launch
            }
            _actors.value = Load.Loading
            _actors.value = try {
                val built = fetchActorPool()
                if (built.isNotEmpty()) c.prefs.setActorPool(ActorPool(today, built))
                Load.Ok(built)
            } catch (e: TmdbError) {
                // The network failed: keep showing the last scan if there is one.
                if (stored != null && stored.actors.isNotEmpty()) Load.Ok(stored.actors.distinctBy { it.id }) else Load.Fail(e)
            }
        }
    }

    /**
     * TMDB's most popular people, kept when they act and the average rating of their best-known titles is 7 or more.
     * Stops after [POOL_TARGET] actors or [POOL_PAGES] pages. A person whose request fails is skipped.
     */
    private suspend fun fetchActorPool(): List<ActorEntry> {
        val found = ArrayList<ActorEntry>()
        val gate = Semaphore(ACTOR_PARALLEL)
        for (page in 1..POOL_PAGES) {
            val people = c.repo.list("person/popular", page).results.filter { TopLists.isActor(it) && TopLists.worthChecking(it) }
            val rated = coroutineScope {
                people.map { item ->
                    async {
                        gate.withPermit {
                            try {
                                val p = c.repo.person(item.id)
                                val r = PersonScore.forPerson(p, directing = false)
                                if (TopLists.actorPasses(r)) {
                                    ActorEntry(item.id, item.name.orEmpty(), PersonScore.arabicName(p.alsoKnownAs), item.profilePath, r ?: 0.0)
                                } else {
                                    null
                                }
                            } catch (e: TmdbError) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            // The popular list shifts while we read it, so the same person can come on two pages: keep each once.
            found += rated.filter { r -> found.none { it.id == r.id } }
            if (found.size >= POOL_TARGET) break
        }
        return found.distinctBy { it.id }
    }

    private companion object {
        const val TOP8_PAGES = 2
        const val TITLE_POOL_PAGES = 5
        const val WATCHED_LOOKUP_MAX = 40
        const val FOR_YOU_SIZE = 20
        const val POOL_PAGES = 12
        const val POOL_TARGET = 60
        const val ACTOR_PARALLEL = 4
    }

    fun savedConditionCount(): Int = c.prefs.loadSelection().conditions().size

    // Must stay LAST: it runs after every property above is initialised.
    init {
        loadAll()
        loadGems()
    }
}

private fun preset(axis: String, value: String) = Selection(include = mapOf(axis to setOf(value)))

private class Feature(
    val emoji: String,
    val titleAr: String,
    val titleEn: String,
    val subAr: String,
    val subEn: String,
    val colors: List<Color>,
    val badge: String?,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    onMood: () -> Unit,
    onChoose: () -> Unit,
    onParty: () -> Unit,
    onTimeGate: () -> Unit,
    onWeb: (String, Int) -> Unit,
    onOpen: (WorkCard) -> Unit,
    onSearch: () -> Unit,
    onFilter: (Selection?) -> Unit,
    onPerson: (Int) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val saved = remember { vm.savedConditionCount() }
    val type by vm.contentType.collectAsState()
    androidx.compose.runtime.LaunchedEffect(type) {
        vm.loadGems()
        vm.loadTop8()
        vm.loadActors()
        vm.loadDaily()
    }
    val watchedNow by vm.watchedKeys.collectAsState()
    androidx.compose.runtime.LaunchedEffect(watchedNow) {
        vm.loadForYou()
    }
    val savedFilters by vm.savedFilters.collectAsState()
    val trending by vm.flow("trending").collectAsState()

    val quick = listOf(
        Triple("أجنبي", "Western", preset(Axes.SPHERE, "sphere.western")),
        Triple("شرق آسيا (ياباني · صيني · كوري)", "East Asia (JP · CN · KR)", preset(Axes.SPHERE, "sphere.east_asia")),
        Triple("عربي", "Arab", preset(Axes.SPHERE, "sphere.arab")),
        Triple("أنيميشن", "Animation", preset(Axes.SPHERE, "sphere.animation")),
        Triple("أفلام", "Movies", preset(Axes.FORMAT, "format.movie")),
        Triple("مسلسلات", "Series", preset(Axes.FORMAT, "format.tv")),
        Triple("مسلسلات عربية", "Arabic series", Selection(include = mapOf(Axes.FORMAT to setOf("format.tv"), Axes.COUNTRY to setOf("region.arab")))),
        Triple("أفلام عربية", "Arabic films", Selection(include = mapOf(Axes.FORMAT to setOf("format.movie"), Axes.COUNTRY to setOf("region.arab")))),
        Triple("أنيمي", "Anime", preset(Axes.FORMAT, "format.anime")),
        Triple("وثائقيات", "Documentaries", preset(Axes.GENRE, "genre.99")),
        Triple("رعب", "Horror", preset(Axes.GENRE, "genre.27")),
        Triple("كوميديا", "Comedy", preset(Axes.GENRE, "genre.35")),
        Triple("جريمة", "Crime", preset(Axes.GENRE, "genre.80")),
        Triple("خيال علمي", "Sci-Fi", preset(Axes.GENRE, "genre.878")),
        Triple("الأردن", "Jordan", preset(Axes.COUNTRY, "country.JO")),
        Triple("مصر", "Egypt", preset(Axes.COUNTRY, "country.EG")),
        Triple("تركيا", "Türkiye", preset(Axes.COUNTRY, "country.TR")),
        Triple("كوريا الجنوبية", "South Korea", preset(Axes.COUNTRY, "country.KR")),
        Triple("اليابان", "Japan", preset(Axes.COUNTRY, "country.JP")),
        Triple("الهند", "India", preset(Axes.COUNTRY, "country.IN")),
    )
    val chipColors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(50)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SearchPill(onSearch) }

            item {
                when (val t = trending) {
                    is Load.Ok -> HeroBanner(
                        t.value.filter { ContentType.allows(type, it.key) && (it.backdropPath != null || it.posterPath != null) }.take(5),
                        onOpen = onOpen, onExplore = { c -> onWeb(c.mediaType, c.id) },
                    )
                    is Load.Loading -> Box(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).aspectRatio(16f / 10f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    is Load.Fail -> {}
                }
            }

            item {
                SectionTitle(tr("ابدأ رحلتك", "Start your journey"))
                val features = listOf(
                    Feature("🌀", "بوابة الزمن السينمائية", "Cinema Time Gate", "رحلة بين العصور والعوالم", "A journey through eras and worlds",
                        listOf(Color(0xFF6A5CFF), Color(0xFF2A1B6B)), null) { onTimeGate() },
                    Feature("🕸️", "شبكة العوالم", "Web of worlds", "تنقّل بين أعمال مترابطة", "Hop between linked titles",
                        listOf(Color(0xFF00B8D4), Color(0xFF0B3B5C)), null) { onWeb("random", 0) },
                    Feature("🎯", "التصفية الذكية", "Smart filter", "سؤال واحد في كل مرة", "One question at a time",
                        listOf(Color(0xFFFFB300), Color(0xFF7A4B00)), if (saved > 0) tr("متابعة ($saved)", "Resume ($saved)") else null) { onFilter(null) },
                    Feature("🎭", "حسب مزاجي", "By my mood", "اختر ما يناسب حالتك", "Pick what suits your state",
                        listOf(Color(0xFFFF5C93), Color(0xFF6E1B47)), null) { onMood() },
                    Feature("🎉", "سهرة جماعية", "Group watch", "اتفاق شخصين على عمل", "Two people, one title",
                        listOf(Color(0xFF22C55E), Color(0xFF0E4A2B)), null) { onParty() },
                    Feature("🤷", "اختر لي", "Choose for me", "لا أعرف ماذا أريد", "I don't know what I want",
                        listOf(Color(0xFF8B5CF6), Color(0xFF34187F)), null) { onChoose() },
                )
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    features.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { f ->
                                FeatureCard(f.emoji, tr(f.titleAr, f.titleEn), tr(f.subAr, f.subEn), f.colors, f.onClick, Modifier.weight(1f), f.badge)
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                SectionTitle(tr("تصفّح سريع", "Quick browse"))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quick) { (ar, en, sel) ->
                        androidx.compose.material3.AssistChip(
                            onClick = { onFilter(sel) }, label = { Text(tr(ar, en)) },
                            colors = chipColors, border = null, shape = pillShape,
                        )
                    }
                }
            }

            if (savedFilters.isNotEmpty()) {
                item {
                    SectionTitle(tr("تصفياتي المحفوظة", "My saved filters"))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedFilters) { sf ->
                            androidx.compose.material3.AssistChip(
                                onClick = { onFilter(sf.selection) }, label = { Text(sf.name) },
                                colors = chipColors, border = null, shape = pillShape,
                            )
                        }
                    }
                }
            }

            item {
                val gems by vm.gems.collectAsState()
                SectionTitle(tr("كنوز مخفية 💎", "Hidden gems 💎"))
                Text(
                    tr(
                        "تقييم 7+ وأصوات متوسطة (1,500–20,000): أعمال جيدة خارج دائرة الأكثر شهرة، مرتّبة بتقييم موزون بعدد الأصوات.",
                        "Rating 7+ with moderate votes (1,500–20,000): good titles outside the most popular, ranked by a vote-weighted score.",
                    ),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                when (val g = gems) {
                    is Load.Loading -> CenterLoading()
                    is Load.Fail -> ErrorBox(g.error, onRetry = { vm.loadGems(true) })
                    is Load.Ok -> LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(g.value, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                    }
                }
            }

            item {
                val forYou by vm.forYou.collectAsState()
                ForYouBlock(forYou, type, onOpen)
            }
            if (type == "all" || type == "movie") {
                item {
                    val top by vm.top8Movies.collectAsState()
                    SectionTitle(tr("أفلام تقييمها 8 فأعلى", "Movies rated 8 and above"))
                    Text(
                        tr("الأعلى تقييمًا بين الجمهور، بحد أدنى 1,500 صوت.", "Highest rated by audiences, with at least 1,500 votes."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    when (val t = top) {
                        is Load.Loading -> CenterLoading()
                        is Load.Fail -> ErrorBox(t.error, onRetry = { vm.loadTop8(true) })
                        is Load.Ok -> LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(t.value, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                        }
                    }
                }
            }
            if (type == "all" || type == "tv") {
                item {
                    val top by vm.top8Series.collectAsState()
                    SectionTitle(tr("مسلسلات تقييمها 8 فأعلى", "Series rated 8 and above"))
                    Text(
                        tr("الأعلى تقييمًا بين الجمهور، بحد أدنى 500 صوت.", "Highest rated by audiences, with at least 500 votes."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    when (val t = top) {
                        is Load.Loading -> CenterLoading()
                        is Load.Fail -> ErrorBox(t.error, onRetry = { vm.loadTop8(true) })
                        is Load.Ok -> LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(t.value, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                        }
                    }
                }
            }
            item {
                val pool by vm.actors.collectAsState()
                val title = tr("أشهر الممثلين (تقييم 7 فأعلى)", "Famous actors (rated 7 and above)")
                val caption = tr("تقييم الممثل = متوسط تقييم أشهر أعماله على TMDB.", "An actor's rating = the average TMDB rating of their best-known titles.")
                when (val a = pool) {
                    is Load.Loading -> {
                        ActorRowHeader(title, caption)
                        CenterLoading()
                    }
                    is Load.Fail -> {
                        ActorRowHeader(title, caption)
                        ErrorBox(a.error, onRetry = { vm.loadActors(true) })
                    }
                    is Load.Ok -> {
                        val list = a.value.filter { it.rating >= TopLists.MIN_ACTOR_RATING }.take(20)
                        if (list.isNotEmpty()) {
                            ActorRowHeader(title, caption)
                            ActorRow(list, onPerson)
                        }
                    }
                }
            }
            item {
                val pool by vm.actors.collectAsState()
                val day = androidx.compose.runtime.remember { TopLists.today() }
                val picks = when (val a = pool) {
                    is Load.Ok -> TopLists.dailyPick(a.value, day)
                    else -> emptyList()
                }
                if (picks.isNotEmpty()) {
                    ActorRowHeader(
                        tr("الممثلون الأعلى تقييمًا (10 كل يوم)", "Top-rated actors (10 a day)"),
                        tr("10 ممثلين جدد كل يوم، تقييمهم 7.5 فأعلى (متوسط تقييم أشهر أعمالهم).", "10 new actors every day, rated 7.5 and above (the average rating of their best-known titles)."),
                    )
                    ActorRow(picks, onPerson)
                }
            }

            if (type == "all" || type == "movie") {
                item {
                    val daily by vm.dailyMovies.collectAsState()
                    DailyTitlesRow(
                        daily, "movie",
                        tr("أفلام اليوم الأعلى تقييمًا (10 كل يوم)", "Top-rated movies (10 a day)"),
                        tr("10 أفلام جديدة كل يوم، تقييمها 8 فأعلى (بحد أدنى 1,500 صوت).", "10 new movies every day, rated 8 and above (at least 1,500 votes)."),
                        onOpen, onRetry = { vm.loadDaily(true) },
                    )
                }
            }
            if (type == "all" || type == "tv") {
                item {
                    val daily by vm.dailySeries.collectAsState()
                    DailyTitlesRow(
                        daily, "tv",
                        tr("مسلسلات اليوم الأعلى تقييمًا (10 كل يوم)", "Top-rated series (10 a day)"),
                        tr("10 مسلسلات جديدة كل يوم، تقييمها 8 فأعلى (بحد أدنى 500 صوت).", "10 new series every day, rated 8 and above (at least 500 votes)."),
                        onOpen, onRetry = { vm.loadDaily(true) },
                    )
                }
            }

            items(
                vm.sections.filter { s -> type == "all" || (s.path.startsWith("movie") && type == "movie") || (s.path.startsWith("tv") && type == "tv") || s.path.startsWith("trending") },
                key = { it.id },
            ) { s ->
                val state by vm.flow(s.id).collectAsState()
                SectionTitle(tr(s.titleAr, s.titleEn))
                when (val st = state) {
                    is Load.Loading -> CenterLoading()
                    is Load.Fail -> ErrorBox(st.error, onRetry = { vm.loadAll() })
                    is Load.Ok -> LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(st.value.filter { ContentType.allows(type, it.key) }, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActorRowHeader(title: String, caption: String) {
    SectionTitle(title)
    Text(
        caption,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActorRow(list: List<ActorEntry>, onPerson: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list.distinctBy { it.id }, key = { it.id }) { actor ->
            PersonCard(tr(actor.nameAr ?: actor.name, actor.name), actor.photo, actor.rating, onClick = { onPerson(actor.id) })
        }
    }
}

@Composable
private fun DailyTitlesRow(
    state: Load<List<WorkCard>>,
    media: String,
    title: String,
    caption: String,
    onOpen: (WorkCard) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is Load.Loading -> {
            ActorRowHeader(title, caption)
            CenterLoading()
        }
        is Load.Fail -> {
            ActorRowHeader(title, caption)
            ErrorBox(state.error, onRetry = onRetry)
        }
        is Load.Ok -> {
            val day = androidx.compose.runtime.remember { TopLists.today() }
            val picks = TopLists.dailyTitles(state.value, day, media)
            if (picks.isNotEmpty()) {
                ActorRowHeader(title, caption)
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(picks, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                }
            }
        }
    }
}

/** "What you watched": a row per genre the user watches most; a short hint until enough titles are marked as watched. */
@Composable
private fun ForYouBlock(state: Load<ForYou>, type: String, onOpen: (WorkCard) -> Unit) {
    val ok = state as? Load.Ok<ForYou> ?: return // loading or failed: this row is optional, so nothing is shown
    val forYou = ok.value
    val taxonomy = androidx.compose.runtime.remember { Taxonomy(TaxonomySeed.nodes()) }
    if (forYou.sections.isEmpty()) {
        if (forYou.known < WatchedProfile.MIN_KNOWN) {
            ActorRowHeader(
                tr("ماذا شاهدت؟", "What have you watched?"),
                tr(
                    "علّم الأعمال التي شاهدتها بزر «شاهدتُه» في صفحة العمل (3 أعمال على الأقل) لتظهر لك اقتراحات تشبه ما تحب.",
                    "Mark what you have watched with the \"Watched\" button on a title page (at least 3) to get suggestions like what you enjoy.",
                ),
            )
        }
        return
    }
    for (section in forYou.sections) {
        val cards = section.cards.filter { ContentType.allows(type, it.key) }
        if (cards.isEmpty()) continue
        val name = taxonomy.node("genre.${section.genreId}")?.name(isArabic()) ?: continue
        ActorRowHeader(
            tr("لأنك تشاهد $name كثيرًا", "Because you watch a lot of $name"),
            tr("${section.count} من أصل ${forYou.known} عمل شاهدتَه من هذا النوع.", "${section.count} of the ${forYou.known} titles you watched are $name."),
        )
        LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
        }
    }
}
