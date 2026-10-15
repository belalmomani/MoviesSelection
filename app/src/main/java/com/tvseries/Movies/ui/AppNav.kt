package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.padding
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
    const val DETAILS = "details/{type}/{id}"
    const val PERSON = "person/{id}"
    fun details(type: String, id: Int) = "details/$type/$id"
    fun person(id: Int) = "person/$id"
}

private class Tab(val route: String, val ar: String, val en: String, val icon: ImageVector)

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

@Composable
fun MoviesRoot(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val openCard: (WorkCard) -> Unit = { nav.navigate(Routes.details(it.mediaType, it.id)) }
    val openFilter: (Selection?) -> Unit = { sel ->
        if (sel != null) container.presetSelection = sel
        nav.goTab(Routes.FILTER)
    }

    Scaffold(
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
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
                    onFilter = openFilter, onMood = { nav.navigate(Routes.MOOD) },
                )
            }
            composable(Routes.FILTER) { FilterScreen(onOpen = openCard) }
            composable(Routes.SEARCH) {
                SearchScreen(onOpen = openCard, onPerson = { nav.navigate(Routes.person(it)) })
            }
            composable(Routes.LISTS) { ListsScreen(container, onOpen = openCard) }
            composable(Routes.SETTINGS) { SettingsScreen(container, onAbout = { nav.navigate(Routes.ABOUT) }) }
            composable(Routes.MOOD) {
                MoodScreen(onBack = { nav.popBackStack() }, onApply = { sel -> nav.popBackStack(); openFilter(sel) })
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.DETAILS,
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
            ) { be ->
                val type = be.arguments?.getString("type") ?: "movie"
                val id = be.arguments?.getInt("id") ?: 0
                DetailsScreen(
                    ads = container.ads, media = type, id = id,
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
