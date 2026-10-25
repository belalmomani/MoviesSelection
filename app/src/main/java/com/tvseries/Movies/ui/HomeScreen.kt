@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.tvseries.Movies.R
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
import com.tvseries.Movies.ads.AdPlacement
import com.tvseries.Movies.ads.AdsProvider
import androidx.compose.foundation.layout.fillMaxSize
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.GemFinder
import com.tvseries.Movies.data.ContentType
import com.tvseries.Movies.data.ListItemDto
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val error: TmdbError) : Load<Nothing>
}

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

    fun savedConditionCount(): Int = c.prefs.loadSelection().conditions().size

    // Must stay LAST: it runs after every property above is initialised.
    init {
        loadAll()
        loadGems()
    }
}

private fun preset(axis: String, value: String) = Selection(include = mapOf(axis to setOf(value)))

@Composable
fun HomeScreen(
    ads: AdsProvider,
    onMood: () -> Unit,
    onParty: () -> Unit,
    onTimeGate: () -> Unit,
    onOpen: (WorkCard) -> Unit,
    onSearch: () -> Unit,
    onFilter: (Selection?) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val saved = remember { vm.savedConditionCount() }
    val type by vm.contentType.collectAsState()
    androidx.compose.runtime.LaunchedEffect(type) { vm.loadGems() }
    val savedFilters by vm.savedFilters.collectAsState()
    val typePresets = listOf(
        Triple("أفلام", "Movies", preset(Axes.FORMAT, "format.movie")),
        Triple("مسلسلات", "Series", preset(Axes.FORMAT, "format.tv")),
        Triple("أنيمي ياباني", "Anime", preset(Axes.FORMAT, "format.anime")),
        Triple("وثائقيات", "Documentaries", preset(Axes.GENRE, "genre.99")),
        Triple("رعب", "Horror", preset(Axes.GENRE, "genre.27")),
        Triple("كوميديا", "Comedy", preset(Axes.GENRE, "genre.35")),
        Triple("جريمة", "Crime", preset(Axes.GENRE, "genre.80")),
        Triple("خيال علمي", "Sci-Fi", preset(Axes.GENRE, "genre.878")),
    )
    val regionPresets = listOf(
        Triple("مسلسلات عربية", "Arabic series", Selection(include = mapOf(Axes.FORMAT to setOf("format.tv"), Axes.COUNTRY to setOf("region.arab")))),
        Triple("أفلام عربية", "Arabic films", Selection(include = mapOf(Axes.FORMAT to setOf("format.movie"), Axes.COUNTRY to setOf("region.arab")))),
        Triple("الدول العربية", "Arab world", preset(Axes.COUNTRY, "region.arab")),
        Triple("الأردن", "Jordan", preset(Axes.COUNTRY, "country.JO")),
        Triple("مصر", "Egypt", preset(Axes.COUNTRY, "country.EG")),
        Triple("تركيا", "Türkiye", preset(Axes.COUNTRY, "country.TR")),
        Triple("كوريا الجنوبية", "South Korea", preset(Axes.COUNTRY, "country.KR")),
        Triple("اليابان", "Japan", preset(Axes.COUNTRY, "country.JP")),
        Triple("الهند", "India", preset(Axes.COUNTRY, "country.IN")),
        Triple("العربية (لغة)", "Arabic (language)", preset(Axes.LANGUAGE, "language.ar")),
    )

    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Movies Selection", style = MaterialTheme.typography.headlineSmall)
                }
                OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("ابحث عن فيلم أو مسلسل أو شخص", "Search a movie, series or person"))
                }
                androidx.compose.material3.FilledTonalButton(onClick = onTimeGate, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("🌀 بوابة الزمن السينمائية", "🌀 Cinema Time Gate"))
                }
                OutlinedButton(onClick = onParty, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("🎉 سهرة جماعية", "🎉 Group watch"))
                }
                OutlinedButton(onClick = onMood, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("🎭 اختر لي حسب مزاجي", "🎭 Pick for my mood"))
                }
                Button(onClick = { onFilter(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (saved > 0) tr("متابعة التصفية ($saved شرط)", "Continue filtering ($saved conditions)")
                        else tr("ابدأ التصفية الذكية", "Start smart filtering"),
                    )
                }
            }
        }
        if (savedFilters.isNotEmpty()) {
            item {
                SectionTitle(tr("تصفياتي المحفوظة", "My saved filters"))
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedFilters.forEach { sf -> AssistChip(onClick = { onFilter(sf.selection) }, label = { Text(sf.name) }) }
                }
            }
        }
        item {
            SectionTitle(tr("اكتشاف حسب النوع", "Discover by type & genre"))
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typePresets.forEach { (ar, en, sel) -> AssistChip(onClick = { onFilter(sel) }, label = { Text(tr(ar, en)) }) }
            }
            SectionTitle(tr("اكتشاف حسب الدولة واللغة", "Discover by country & language"))
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                regionPresets.forEach { (ar, en, sel) -> AssistChip(onClick = { onFilter(sel) }, label = { Text(tr(ar, en)) }) }
            }
        }
        item {
            val gems by vm.gems.collectAsState()
            SectionTitle(tr("كنوز مخفية 💎", "Hidden gems 💎"))
            Text(
                tr(
                    "تقييم 7+ وعدد أصوات متوسط (1,500–20,000): أعمال جيدة خارج دائرة الأكثر شهرة. الترتيب يوازن بين التقييم وعدد الأصوات ولا يعتمد على التقييم وحده.",
                    "Rating 7+ with a moderate vote count (1,500–20,000): good titles outside the most popular. Ranking balances rating and votes, not rating alone.",
                ),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            when (val g = gems) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(g.error, onRetry = { vm.loadGems(true) })
                is Load.Ok -> LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(g.value, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                }
            }
        }
        items(vm.sections.filter { s -> type == "all" || (s.path.startsWith("movie") && type == "movie") || (s.path.startsWith("tv") && type == "tv") || s.path.startsWith("trending") }, key = { it.id }) { s ->
            val state by vm.flow(s.id).collectAsState()
            SectionTitle(tr(s.titleAr, s.titleEn))
            when (val st = state) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(st.error, onRetry = { vm.loadAll() })
                is Load.Ok -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(st.value.filter { ContentType.allows(type, it.key) }, key = { it.key }) { card -> PosterCard(card, onClick = { onOpen(card) }) }
                }
            }
        }
    }
    BannerSlot(ads, AdPlacement.HOME_BANNER)
    }
}
