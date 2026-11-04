@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.data.ContentType
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.delay

/** Wraps a phone-designed screen so it is centred and not stretched across a wide TV. */
@Composable
private fun TvPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) { content() }
    }
}

/**
 * The TV edition: side rail + remote-friendly screens (D-pad: arrows to move, OK to select, Back to go back).
 * No touch gestures, no ads, larger text. Time Gate / Web of worlds are touch-first and not part of this edition yet.
 */
@Composable
fun TvRoot(container: AppContainer) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val d = LocalDensity.current
    val openCard: (WorkCard) -> Unit = { nav.navigate("tv_details/${it.mediaType}/${it.id}") }

    // Bigger text for viewing from a sofa.
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * 1.2f)) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TvRail(route) { target ->
                nav.navigate(target) {
                    popUpTo("tv_home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            NavHost(nav, startDestination = "tv_home", modifier = Modifier.weight(1f).fillMaxSize()) {
                composable("tv_home") { TvHomeScreen(container, openCard) }
                composable("tv_search") { TvSearchScreen(openCard) }
                composable("tv_filter") {
                    TvPage {
                        FilterScreen(
                            onOpen = openCard,
                            onCompare = { list ->
                                container.compare.value = list.take(3)
                                nav.navigate("tv_compare")
                            },
                            onFinalStage = { list ->
                                container.finalCandidates = list.take(12)
                                nav.navigate("tv_final")
                            },
                        )
                    }
                }
                composable("tv_lists") { TvPage { ListsScreen(container, openCard) } }
                composable("tv_settings") { TvPage { SettingsScreen(container, onAbout = { nav.navigate("tv_about") }) } }
                composable("tv_about") { TvPage { AboutScreen(onBack = { nav.popBackStack() }) } }
                composable("tv_compare") { TvPage { CompareScreen(container, onBack = { nav.popBackStack() }, onOpen = openCard) } }
                composable("tv_final") { TvPage { FinalStageScreen(container, onBack = { nav.popBackStack() }, onOpen = openCard) } }
                composable(
                    "tv_details/{type}/{id}",
                    arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
                ) { be ->
                    TvDetailsScreen(
                        media = be.arguments?.getString("type") ?: "movie", id = be.arguments?.getInt("id") ?: 0,
                        onOpen = openCard,
                    )
                }
            }
        }
    }
}

@Composable
fun TvHomeScreen(container: AppContainer, onOpen: (WorkCard) -> Unit, vm: HomeViewModel = viewModel()) {
    val type by vm.contentType.collectAsState()
    val trending by vm.flow("trending").collectAsState()
    val focus = remember { FocusRequester() }
    val heroCards = ((trending as? Load.Ok)?.value.orEmpty())
        .filter { ContentType.allows(type, it.key) && (it.backdropPath != null || it.posterPath != null) }.take(6)
    var heroIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(heroCards.size) {
        while (heroCards.size > 1) {
            delay(8000)
            heroIdx = (heroIdx + 1) % heroCards.size
        }
    }
    // Put the remote's focus on the main button once the content is there.
    LaunchedEffect(heroCards.isNotEmpty()) {
        if (heroCards.isNotEmpty()) {
            delay(400)
            runCatching { focus.requestFocus() }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("أهلًا بك", "Welcome"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TypePill(type) { container.prefs.setContentType(it) }
            }
        }
        item {
            if (heroCards.isNotEmpty()) {
                val card = heroCards[heroIdx % heroCards.size]
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(250.dp).clip(RoundedCornerShape(24.dp))) {
                    AsyncImage(
                        model = Img.backdrop(card.backdropPath ?: card.posterPath), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD05060C)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("الرائج الآن", "Trending now"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(card.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            listOfNotNull(card.year, card.rating?.let { "★ " + fmt1(it) }).joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { onOpen(card) }, modifier = Modifier.focusRequester(focus)) { Text(tr("التفاصيل", "Details")) }
                    }
                }
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
                is Load.Ok -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(st.value.filter { ContentType.allows(type, it.key) }, key = { it.key }) { card -> TvPoster(card, onClick = { onOpen(card) }) }
                }
            }
        }
    }
}

@Composable
fun TvDetailsScreen(media: String, id: Int, onOpen: (WorkCard) -> Unit, vm: DetailsViewModel = viewModel()) {
    LaunchedEffect(media, id) { vm.load(media, id) }
    val ui by vm.ui.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val watchlist by vm.prefs.watchlist.collectAsState()
    val watched by vm.prefs.watched.collectAsState()
    val focus = remember { FocusRequester() }
    val arabic = isArabic()

    when (val st = ui) {
        is Load.Loading -> CenterLoading(Modifier.fillMaxSize())
        is Load.Fail -> ErrorBox(st.error, onRetry = { vm.load(media, id) })
        is Load.Ok -> {
            val d = st.value
            val card = d.toCard(media)
            val isFav = favorites.any { it.key == card.key }
            val inWatch = watchlist.any { it.key == card.key }
            val seen = card.key in watched
            LaunchedEffect(card.key) {
                delay(450)
                runCatching { focus.requestFocus() }
            }
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Img.backdrop(d.backdropPath ?: d.posterPath), contentDescription = null,
                    contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE05060C), Color(0x6605060C)))))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            PosterImage(Img.poster(card.posterPath), card.title, Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(16.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                                card.originalTitle?.let { Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium) }
                                val meta = listOfNotNull(
                                    if (media == "movie") tr("فيلم", "Movie") else tr("مسلسل", "Series"),
                                    card.year, card.rating?.let { "★ " + fmt1(it) + " (${card.votes ?: 0})" },
                                    if (media == "movie") d.runtime?.takeIf { it > 0 }?.let { "$it " + tr("دقيقة", "min") }
                                    else d.numberOfSeasons?.let { "$it " + tr("مواسم", "seasons") },
                                ).joinToString("  ·  ")
                                Text(meta, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                                Text(d.genres.orEmpty().joinToString(if (arabic) "، " else ", ") { it.name }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    d.overview?.takeIf { it.isNotBlank() } ?: tr("لا تتوفر قصة لهذا العمل في TMDB.", "No story available for this title on TMDB."),
                                    color = Color.White.copy(alpha = 0.9f), maxLines = 7, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                )
                                val region = remember { android.content.res.Resources.getSystem().configuration.locales[0].country.ifBlank { "US" } }
                                val providers = d.watchProviders?.results?.get(region)?.flatrate.orEmpty().joinToString(if (arabic) "، " else ", ") { it.name }
                                if (providers.isNotBlank()) {
                                    Text(tr("🎞️ متاح على: ", "🎞️ Available on: ") + providers, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { vm.prefs.toggleFavorite(card) }, modifier = Modifier.focusRequester(focus)) {
                                        Text(if (isFav) tr("✓ في المفضلة", "✓ Favorited") else tr("❤️ المفضلة", "❤️ Favorite"))
                                    }
                                    OutlinedButton(onClick = { vm.prefs.toggleWatchlist(card) }) {
                                        Text(if (inWatch) tr("✓ شاهد لاحقًا", "✓ Watch later") else tr("شاهد لاحقًا", "Watch later"), color = Color.White)
                                    }
                                    OutlinedButton(onClick = { vm.prefs.toggleWatched(card.key) }) {
                                        Text(if (seen) tr("✓ شاهدته", "✓ Watched") else tr("شاهدته؟", "Watched?"), color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    val cast = d.credits?.cast.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }.take(8)
                    if (cast.isNotEmpty()) {
                        item {
                            SectionTitle(tr("الممثلون", "Cast"))
                            Text(
                                cast.joinToString("  ·  ") { it.name + (it.character?.takeIf { c -> c.isNotBlank() }?.let { c -> " ($c)" } ?: "") },
                                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    val similar = d.similar?.results.orEmpty().mapNotNull { it.toCard(media) }
                    if (similar.isNotEmpty()) {
                        item {
                            SectionTitle(tr("أعمال مشابهة", "Similar titles"))
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(similar, key = { it.key }) { s -> TvPoster(s, onClick = { onOpen(s) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvSearchScreen(onOpen: (WorkCard) -> Unit, vm: SearchViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val contentType by vm.contentType.collectAsState()
    LaunchedEffect(contentType) { vm.rerun() }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { vm.setQuery(it) }, singleLine = true,
            label = { Text(tr("ابحث عن فيلم أو مسلسل (اضغط OK لفتح لوحة المفاتيح)", "Search a movie or series (press OK for the keyboard)")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (val r = results) {
            null -> EmptyBox(tr("اكتب حرفين على الأقل.", "Type at least 2 characters."))
            is Load.Loading -> CenterLoading()
            is Load.Fail -> ErrorBox(r.error, onRetry = { vm.rerun() })
            is Load.Ok -> {
                if (r.value.works.isEmpty()) EmptyBox(tr("لا نتائج.", "No results."))
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    gridItems(r.value.works, key = { it.key }) { w -> TvPoster(w, onClick = { vm.commit(); onOpen(w) }) }
                }
            }
        }
    }
}
