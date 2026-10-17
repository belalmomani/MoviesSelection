package com.tvseries.Movies.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import com.tvseries.Movies.ads.AdPlacement
import com.tvseries.Movies.ads.AdsProvider
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import java.util.Locale

val LocalLang = staticCompositionLocalOf { "ar" }

@Composable
fun isArabic(): Boolean = LocalLang.current == "ar"

/** Inline bilingual text: Arabic first, English second. */
@Composable
fun tr(ar: String, en: String): String = if (LocalLang.current == "ar") ar else en

fun fmt1(d: Double): String = String.format(Locale.US, "%.1f", d)

@Composable
fun PosterImage(url: String?, fallbackText: String, modifier: Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(fallbackText.take(1), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun PosterCard(card: WorkCard, onClick: () -> Unit, modifier: Modifier = Modifier.width(120.dp)) {
    Column(modifier.clickable(onClick = onClick)) {
        PosterImage(
            Img.poster(card.posterPath), card.title,
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        val meta = listOfNotNull(card.year, card.rating?.let { "★ " + fmt1(it) }).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WorkRow(card: WorkCard, onClick: () -> Unit, badge: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            Img.poster(card.posterPath), card.title,
            Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            card.originalTitle?.let {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val type = if (card.mediaType == "movie") tr("فيلم", "Movie") else tr("مسلسل", "TV")
            val meta = listOfNotNull(type, card.year, card.rating?.let { "★ " + fmt1(it) + (card.votes?.let { v -> " ($v)" } ?: "") })
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        trailing?.invoke()
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun errorText(e: TmdbError): String = when (e.kind) {
    TmdbError.Kind.NO_KEY -> tr("لم يتم ضبط مفتاح TMDB. أدخله من الإعدادات.", "TMDB API key is not set. Enter it in Settings.")
    TmdbError.Kind.NETWORK -> tr("لا يوجد اتصال بالإنترنت ولا نسخة محفوظة لهذه البيانات.", "No internet connection and no cached copy of this data.")
    TmdbError.Kind.UNAUTHORIZED -> tr("مفتاح TMDB غير صالح.", "The TMDB API key is invalid.")
    TmdbError.Kind.RATE_LIMIT -> tr("تم بلوغ حد الطلبات في TMDB. حاول بعد قليل.", "TMDB rate limit reached. Try again shortly.")
    TmdbError.Kind.NOT_FOUND -> tr("العنصر غير موجود في TMDB.", "Not found on TMDB.")
    TmdbError.Kind.SERVER -> tr("خطأ في خادم TMDB (${e.httpCode ?: ""}).", "TMDB server error (${e.httpCode ?: ""}).")
    TmdbError.Kind.PARSE -> tr("تعذّر قراءة استجابة TMDB.", "Could not read the TMDB response.")
}

@Composable
fun ErrorBox(error: TmdbError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(errorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) Button(onClick = onRetry) { Text(tr("إعادة المحاولة", "Retry")) }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Banner placed only on Home and Details (never on the filter screen). Renders nothing when ads are off. */
@Composable
fun BannerSlot(ads: AdsProvider, placement: AdPlacement) {
    if (!ads.canShowAt(placement, userIsFiltering = false)) return
    AndroidView(factory = { ctx -> ads.createBanner(ctx) ?: View(ctx) }, modifier = Modifier.fillMaxWidth())
}
