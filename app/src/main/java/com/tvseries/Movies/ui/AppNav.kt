package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.WorkCard

object Routes {
    const val HOME = "home"
    const val FILTER = "filter"
    const val SEARCH = "search"
    const val LISTS = "lists"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val MOOD = "mood"
    const val PARTY = "party"
    const val COMPARE = "compare"
    const val CHOOSE = "choose"
    const val FINAL = "final"
    const val WEB = "web/{type}/{id}"
    const val TIMEGATE = "timegate"
    const val TASTE = "taste"
    const val MYSTERY = "mystery"
    const val WORLDS = "worlds"
    const val ROULETTE = "roulette"
    const val DOORS = "doors"
    const val SLIDERS = "sliders"
    const val DETAILS = "details/{type}/{id}"
    const val PERSON = "person/{id}"
    fun details(type: String, id: Int) = "details/$type/$id"
    fun web(type: String, id: Int) = "web/$type/$id"
    fun person(id: Int) = "person/$id"
}

private class Tab(val route: String, val ar: String, val en: String, val icon: ImageVector)

private class TopItem(val route: String, val ar: String, val en: String)

private val topItems = listOf(
    TopItem(Routes.CHOOSE, "🤷 اختر لي", "🤷 Choose for me"),
    TopItem(Routes.TASTE, "👍 ذوقي", "👍 My taste"),
    TopItem(Routes.MYSTERY, "🎁 الصندوق الغامض", "🎁 Mystery box"),
    TopItem(Routes.WORLDS, "🗺️ العوالم", "🗺️ Worlds"),
)

private val moreItems = listOf(
    TopItem("web/random/0", "🕸️ شبكة العوالم", "🕸️ Web of worlds"),
    TopItem(Routes.ROULETTE, "🎡 الروليت الذكي", "🎡 Smart roulette"),
    TopItem(Routes.DOORS, "🚪 ثلاثة أبواب", "🚪 Three doors"),
    TopItem(Routes.SLIDERS, "🎚️ اضبط تفضيلاتي", "🎚️ Tune preferences"),
)

/** Top bar: four quick-start experiences, plus "More" for the extra pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopActionBar(type: String, onType: (String) -> Unit, onGo: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Movies Selection", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                listOf("all" to tr("الكل", "All"), "movie" to tr("أفلام", "Movies"), "tv" to tr("مسلسلات", "Series")).forEach { (v, label) ->
                    FilterChip(
                        selected = type == v, onClick = { onType(v) }, label = { Text(label) },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(topItems, key = { it.route }) { t ->
                    AssistChip(onClick = { onGo(t.route) }, label = { Text(tr(t.ar, t.en)) })
                }
                item {
                    Box {
                        AssistChip(onClick = { menu = true }, label = { Text(tr("المزيد ▾", "More ▾")) })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            moreItems.forEach { m ->
                                DropdownMenuItem(text = { Text(tr(m.ar, m.en)) }, onClick = { menu = false; onGo(m.route) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private val tabs = listOf(
    Tab(Routes.HOME, "الرئيسية", "Home", Icons.Default.Home),
    Tab(Routes.FILTER, "تصفية", "Filter", Icons.Default.Menu),
    Tab(Routes.SEARCH, "بحث", "Search", Icons.Default.Search),
    Tab(Routes.LISTS, "قوائمي", "My lists", Icons.Default.Favorite),
    Tab(Routes.SETTINGS, "الإعدادات", "Settings", Icons.Default.Settings),
)

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesRoot(container: AppContainer, pickFromWatchlist: Boolean = false) {
    val update by container.updateAvailable.collectAsState()
    val contentType by container.prefs.contentType.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { container.checkUpdateQuietly() }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val openCard: (WorkCard) -> Unit = { nav.navigate(Routes.details(it.mediaType, it.id)) }
    val openFilter: (Selection?) -> Unit = { sel ->
        if (sel != null) container.presetSelection = sel
        nav.goTab(Routes.FILTER)
    }

    androidx.compose.runtime.LaunchedEffect(pickFromWatchlist) {
        if (pickFromWatchlist) {
            val watched = container.prefs.watched.value
            container.prefs.watchlist.value.filter { it.key !in watched }.randomOrNull()?.let(openCard)
        }
    }

    Scaffold(
        topBar = {
            if (current in tabs.map { it.route }) {
                TopActionBar(type = contentType, onType = { container.prefs.setContentType(it) }, onGo = { nav.navigate(it) })
            }
        },
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = {
                                if (t.route == Routes.SETTINGS && update != null) {
                                    BadgedBox(badge = { Badge() }) { Icon(t.icon, contentDescription = null) }
                                } else {
                                    Icon(t.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tr(t.ar, t.en)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    ads = container.ads, onOpen = openCard, onSearch = { nav.goTab(Routes.SEARCH) },
                    onFilter = openFilter, onMood = { nav.navigate(Routes.MOOD) }, onParty = { nav.navigate(Routes.PARTY) }, onTimeGate = { nav.navigate(Routes.TIMEGATE) }, onWeb = { nav.navigate(Routes.web("random", 0)) },
                )
            }
            composable(Routes.FILTER) {
                FilterScreen(onOpen = openCard, onFinalStage = { list ->
                    container.finalCandidates = list.take(12)
                    nav.navigate(Routes.FINAL)
                }, onCompare = { list ->
                    container.compare.value = list.take(3)
                    nav.navigate(Routes.COMPARE)
                })
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpen = openCard, onPerson = { nav.navigate(Routes.person(it)) })
            }
            composable(Routes.LISTS) { ListsScreen(container, onOpen = openCard) }
            composable(Routes.SETTINGS) { SettingsScreen(container, onAbout = { nav.navigate(Routes.ABOUT) }) }
            composable(Routes.MOOD) {
                MoodScreen(onBack = { nav.popBackStack() }, onApply = { sel -> nav.popBackStack(); openFilter(sel) })
            }
            composable(Routes.TIMEGATE) { TimeGateScreen(onExit = { nav.popBackStack() }, onOpen = openCard) }
            composable(
                Routes.WEB,
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
            ) { be ->
                WebScreen(
                    type = be.arguments?.getString("type") ?: "random", id = be.arguments?.getInt("id") ?: 0,
                    onExit = { nav.popBackStack() }, onOpen = openCard,
                )
            }
            composable(Routes.FINAL) { FinalStageScreen(container, onBack = { nav.popBackStack() }, onOpen = openCard) }
            composable(Routes.CHOOSE) {
                ChooseScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack(); nav.navigate(Routes.DOORS) })
            }
            composable(Routes.TASTE) { TasteScreen(onBack = { nav.popBackStack() }, onOpen = openCard) }
            composable(Routes.MYSTERY) { MysteryScreen(onBack = { nav.popBackStack() }, onOpen = openCard) }
            composable(Routes.WORLDS) { WorldsScreen(onBack = { nav.popBackStack() }, onOpen = openCard, onFilter = { sel -> nav.popBackStack(); openFilter(sel) }) }
            composable(Routes.ROULETTE) {
                RouletteScreen(onBack = { nav.popBackStack() }, onOpen = openCard, onSliders = { nav.navigate(Routes.SLIDERS) })
            }
            composable(Routes.DOORS) {
                DoorsScreen(
                    onBack = { nav.popBackStack() }, onOpen = openCard,
                    onSliders = { nav.navigate(Routes.SLIDERS) }, onChoose = { nav.navigate(Routes.CHOOSE) },
                )
            }
            composable(Routes.SLIDERS) {
                SlidersScreen(container, onBack = { nav.popBackStack() }, onDoors = { nav.popBackStack(); nav.navigate(Routes.DOORS) })
            }
            composable(Routes.PARTY) { PartyScreen(onBack = { nav.popBackStack() }, onOpen = openCard) }
            composable(Routes.COMPARE) { CompareScreen(container, onBack = { nav.popBackStack() }, onOpen = openCard) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.DETAILS,
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
            ) { be ->
                val type = be.arguments?.getString("type") ?: "movie"
                val id = be.arguments?.getInt("id") ?: 0
                DetailsScreen(
                    ads = container.ads, container = container, onWeb = { t, i -> nav.navigate(Routes.web(t, i)) }, onOpenCompare = { nav.navigate(Routes.COMPARE) }, media = type, id = id,
                    onBack = { nav.popBackStack() },
                    onOpen = openCard,
                    onPerson = { nav.navigate(Routes.person(it)) },
                    onGenre = { sel -> openFilter(sel) },
                )
            }
            composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.IntType })) { be ->
                PersonScreen(id = be.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() }, onOpen = openCard)
            }
        }
    }
}
