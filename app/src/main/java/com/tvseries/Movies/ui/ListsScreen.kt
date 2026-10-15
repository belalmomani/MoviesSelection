@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.data.WorkCard

@Composable
fun ListsScreen(container: AppContainer, onOpen: (WorkCard) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val favorites by container.prefs.favorites.collectAsState()
    val watchlist by container.prefs.watchlist.collectAsState()
    val list = if (tab == 0) favorites else watchlist
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("المفضلة", "Favorites") + " (${favorites.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("شاهد لاحقًا", "Watch list") + " (${watchlist.size})") })
        }
        if (list.isEmpty()) {
            EmptyBox(if (tab == 0) tr("لم تضف أي عمل إلى المفضلة بعد.", "No favorites yet.") else tr("قائمة المشاهدة فارغة.", "Your watch list is empty."))
        } else {
            LazyColumn {
                items(list, key = { it.key }) { card ->
                    WorkRow(card, onClick = { onOpen(card) }, trailing = {
                        IconButton(onClick = {
                            if (tab == 0) container.prefs.toggleFavorite(card) else container.prefs.toggleWatchlist(card)
                        }) { Icon(Icons.Default.Delete, contentDescription = tr("إزالة", "Remove")) }
                    })
                }
            }
        }
    }
}
