package com.tvseries.Movies.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.WorkCard
import kotlinx.coroutines.delay

/** Compact all / movies / series switch: one pill, no wrapping. */
@Composable
fun TypePill(type: String, onType: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("all" to tr("الكل", "All"), "movie" to tr("أفلام", "Movies"), "tv" to tr("مسلسلات", "Series"))
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (value, label) ->
            val selected = type == value
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onType(value) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

/** A tappable "fake" search field that opens the search tab. */
@Composable
fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(tr("ابحث عن فيلم أو مسلسل أو ممثل", "Search a movie, series or actor"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Large featured banner that rotates through trending titles. */
@Composable
fun HeroBanner(cards: List<WorkCard>, onOpen: (WorkCard) -> Unit, onExplore: (WorkCard) -> Unit) {
    if (cards.isEmpty()) return
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards.size) {
        while (true) {
            delay(6000)
            idx = (idx + 1) % cards.size
        }
    }
    val card = cards[idx % cards.size]
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(24.dp))) {
        Crossfade(targetState = card, label = "hero") { c ->
            AsyncImage(
                model = Img.backdrop(c.backdropPath ?: c.posterPath), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05060C), Color(0xF005060C)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("الرائج الآن", "Trending now"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(card.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall)
            val meta = listOfNotNull(
                if (card.mediaType == "movie") tr("فيلم", "Movie") else tr("مسلسل", "Series"),
                card.year, card.rating?.let { "★ " + fmt1(it) },
            ).joinToString("  ·  ")
            Text(meta, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(card) }) { Text(tr("التفاصيل", "Details")) }
                OutlinedButton(onClick = { onExplore(card) }) { Text("🕸️ " + tr("استكشف الروابط", "Explore links"), color = Color.White) }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in cards.indices) {
                Box(
                    Modifier.size(if (i == idx % cards.size) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                        .background(if (i == idx % cards.size) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

/** A coloured entry card for one of the app's experiences. */
@Composable
fun FeatureCard(
    emoji: String,
    title: String,
    subtitle: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Box(
        modifier.height(118.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(colors))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(emoji, fontSize = 30.sp, modifier = Modifier.align(Alignment.TopStart))
        if (badge != null) {
            Text(
                badge, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}
