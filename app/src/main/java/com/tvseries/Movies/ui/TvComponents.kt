package com.tvseries.Movies.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.WorkCard

/**
 * Remote-control focus look: a focused element grows a little and gets a gold outline.
 * Put it BEFORE `.clickable` so it observes that element's focus.
 */
@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(14.dp), scale: Float = 1.08f): Modifier {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, label = "tvFocus")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
}

@Composable
fun TvPoster(card: WorkCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(150.dp).tvFocus().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        PosterImage(Img.poster(card.posterPath), card.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(6.dp))
        Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 2.dp))
        val meta = listOfNotNull(card.year, card.rating?.let { "★ " + fmt1(it) }).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class RailItem(val route: String, val emoji: String, val ar: String, val en: String)

private val railItems = listOf(
    RailItem("tv_home", "🏠", "الرئيسية", "Home"),
    RailItem("tv_filter", "🎯", "التصفية", "Filter"),
    RailItem("tv_search", "🔍", "بحث", "Search"),
    RailItem("tv_lists", "❤️", "قوائمي", "My lists"),
    RailItem("tv_settings", "⚙️", "الإعدادات", "Settings"),
)

/** Side navigation for the TV edition; every item is reachable with the D-pad. */
@Composable
fun TvRail(currentRoute: String?, onGo: (String) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(200.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.tvseries.Movies.R.drawable.ic_logo),
                contentDescription = null, modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Movies\nSelection", style = MaterialTheme.typography.titleSmall)
        }
        railItems.forEach { item ->
            val selected = currentRoute == item.route
            Row(
                Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(14.dp), 1.04f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onGo(item.route) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Text(tr(item.ar, item.en), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}
