@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.DetailsDto
import com.tvseries.Movies.data.EpisodeDto
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.NodeFactory
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val prefs = c.prefs

    private val _ui = MutableStateFlow<Load<DetailsDto>>(Load.Loading)
    val ui: StateFlow<Load<DetailsDto>> = _ui.asStateFlow()

    private val _episodes = MutableStateFlow<Map<Int, Load<List<EpisodeDto>>>>(emptyMap())
    val episodes: StateFlow<Map<Int, Load<List<EpisodeDto>>>> = _episodes.asStateFlow()

    fun load(media: String, id: Int) {
        viewModelScope.launch {
            _ui.value = Load.Loading
            _ui.value = try {
                Load.Ok(c.repo.details(media, id))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    fun loadSeason(tvId: Int, season: Int) {
        if (_episodes.value[season] is Load.Ok) return
        viewModelScope.launch {
            _episodes.value = _episodes.value + (season to Load.Loading)
            val r: Load<List<EpisodeDto>> = try {
                Load.Ok(c.repo.season(tvId, season).episodes)
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
            _episodes.value = _episodes.value + (season to r)
        }
    }
}

@Composable
fun DetailsScreen(
    media: String,
    id: Int,
    onBack: () -> Unit,
    onOpen: (WorkCard) -> Unit,
    onPerson: (Int) -> Unit,
    onGenre: (Selection) -> Unit,
    vm: DetailsViewModel = viewModel(),
) {
    LaunchedEffect(media, id) { vm.load(media, id) }
    val ui by vm.ui.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val watchlist by vm.prefs.watchlist.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val arabic = isArabic()
    val expandedSeasons = remember { mutableStateMapOf<Int, Boolean>() }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        }
        when (val st = ui) {
            is Load.Loading -> CenterLoading()
            is Load.Fail -> ErrorBox(st.error, onRetry = { vm.load(media, id) })
            is Load.Ok -> {
                val d = st.value
                val card = d.toCard(media)
                val isFav = favorites.any { it.key == card.key }
                val inWatch = watchlist.any { it.key == card.key }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        val backdrop = Img.backdrop(d.backdropPath ?: d.posterPath)
                        if (backdrop != null) {
                            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(card.title, style = MaterialTheme.typography.headlineSmall)
                            card.originalTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            d.tagline?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    item {
                        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val kind = if (media == "movie") tr("فيلم", "Movie") else tr("مسلسل", "TV series")
                            AssistChip(onClick = {}, label = { Text(kind) })
                            card.year?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                            card.rating?.let { r -> AssistChip(onClick = {}, label = { Text("★ " + fmt1(r) + " (${card.votes ?: 0})") }) }
                            if (media == "movie") {
                                d.runtime?.takeIf { it > 0 }?.let { AssistChip(onClick = {}, label = { Text("$it " + tr("دقيقة", "min")) }) }
                            } else {
                                d.numberOfSeasons?.let { AssistChip(onClick = {}, label = { Text("$it " + tr("مواسم", "seasons")) }) }
                                d.numberOfEpisodes?.let { AssistChip(onClick = {}, label = { Text("$it " + tr("حلقة", "episodes")) }) }
                                d.episodeRunTime?.firstOrNull()?.let { AssistChip(onClick = {}, label = { Text("$it " + tr("د/حلقة", "min/ep")) }) }
                            }
                            d.status?.takeIf { it.isNotBlank() }?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                            d.originalLanguage?.takeIf { it.isNotBlank() }?.let {
                                AssistChip(onClick = {}, label = { Text(tr("اللغة الأصلية: ", "Original language: ") + NodeFactory.language(it).name(arabic)) })
                            }
                        }
                        val countries = (d.productionCountries?.map { it.iso to it.name }.orEmpty())
                            .ifEmpty { d.originCountry.orEmpty().map { it to null } }
                        if (countries.isNotEmpty()) {
                            Text(
                                tr("بلد الإنتاج: ", "Production country: ") + countries.joinToString("، ".takeIf { arabic } ?: ", ") { (iso, name) -> NodeFactory.country(iso, name).name(arabic) },
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { vm.prefs.toggleFavorite(card) }) {
                                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isFav) tr("في المفضلة", "Favorited") else tr("أضف للمفضلة", "Favorite"))
                            }
                            OutlinedButton(onClick = { vm.prefs.toggleWatchlist(card) }) {
                                if (inWatch) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                if (inWatch) Spacer(Modifier.width(6.dp))
                                Text(if (inWatch) tr("في قائمة المشاهدة", "In watch list") else tr("شاهد لاحقًا", "Watch later"))
                            }
                        }
                    }
                    d.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                        item {
                            SectionTitle(tr("الأنواع (اضغط للتصفية)", "Genres (tap to filter)"))
                            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                genres.forEach { g ->
                                    AssistChip(
                                        onClick = { onGenre(Selection(include = mapOf(Axes.GENRE to setOf("genre.${g.id}")))) },
                                        label = { Text(g.name) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        SectionTitle(tr("القصة", "Overview"))
                        Text(
                            d.overview?.takeIf { it.isNotBlank() } ?: tr("لا يوجد وصف متاح لهذا العمل في TMDB.", "No overview is available on TMDB."),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        d.collection?.takeIf { it.name.isNotBlank() }?.let {
                            Text(tr("ضمن سلسلة: ", "Part of: ") + it.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                        }
                    }
                    val directors = if (media == "movie") d.credits?.crew.orEmpty().filter { it.job == "Director" } else d.createdBy.orEmpty()
                    if (directors.isNotEmpty()) {
                        item {
                            SectionTitle(if (media == "movie") tr("المخرج", "Director") else tr("المنشئ", "Created by"))
                            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                directors.forEach { p -> AssistChip(onClick = { onPerson(p.id) }, label = { Text(p.name) }) }
                            }
                        }
                    }
                    val cast = d.credits?.cast.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }.take(20)
                    if (cast.isNotEmpty()) {
                        item {
                            SectionTitle(tr("الممثلون", "Cast"))
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(cast, key = { it.id }) { p ->
                                    Column(Modifier.width(84.dp).clickable { onPerson(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        PosterImage(Img.profile(p.profilePath), p.name, Modifier.size(72.dp).clip(CircleShape))
                                        Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                        p.character?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val kws = d.keywords?.all().orEmpty().take(16)
                    if (kws.isNotEmpty()) {
                        item {
                            SectionTitle(tr("كلمات مفتاحية (من TMDB، بالإنجليزية)", "Keywords (from TMDB)"))
                            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                kws.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
                            }
                        }
                    }
                    val seasons = d.seasons.orEmpty().filter { it.seasonNumber > 0 }
                    if (media == "tv" && seasons.isNotEmpty()) {
                        item { SectionTitle(tr("المواسم والحلقات", "Seasons & episodes")) }
                        items(seasons, key = { "s-${it.seasonNumber}" }) { s ->
                            val open = expandedSeasons[s.seasonNumber] == true
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        expandedSeasons[s.seasonNumber] = !open
                                        if (!open) vm.loadSeason(d.id, s.seasonNumber)
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(s.name ?: (tr("الموسم ", "Season ") + s.seasonNumber), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    val meta = listOfNotNull(s.episodeCount?.let { "$it " + tr("حلقة", "ep.") }, s.airDate?.take(4)).joinToString(" · ")
                                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (open) {
                                    when (val e = episodes[s.seasonNumber]) {
                                        null, is Load.Loading -> CenterLoading()
                                        is Load.Fail -> ErrorBox(e.error, onRetry = { vm.loadSeason(d.id, s.seasonNumber) })
                                        is Load.Ok -> e.value.forEach { ep ->
                                            Text(
                                                "${ep.episodeNumber}. " + (ep.name ?: "") + (ep.airDate?.let { " · $it" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    val similar = d.similar?.results.orEmpty().mapNotNull { it.toCard(media) }
                    if (similar.isNotEmpty()) {
                        item {
                            SectionTitle(tr("أعمال مشابهة", "Similar titles"))
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(similar, key = { it.key }) { s -> PosterCard(s, onClick = { onOpen(s) }) }
                            }
                        }
                    }
                    item {
                        Text(
                            tr("البيانات والصور من TMDB.", "Data and images from TMDB."),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
