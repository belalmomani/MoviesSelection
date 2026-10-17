@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.intersectAll
import com.tvseries.Movies.data.DetailsDto
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.NodeFactory
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompareViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val _ui = MutableStateFlow<Load<List<Pair<WorkCard, DetailsDto>>>>(Load.Loading)
    val ui: StateFlow<Load<List<Pair<WorkCard, DetailsDto>>>> = _ui.asStateFlow()

    fun load(cards: List<WorkCard>) {
        viewModelScope.launch {
            _ui.value = Load.Loading
            _ui.value = try {
                Load.Ok(coroutineScope { cards.map { card -> async { card to c.repo.details(card.mediaType, card.id) } }.awaitAll() })
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, values: List<String>, maxLines: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { v ->
                Text(v.ifBlank { "—" }, Modifier.weight(1f), maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

/** Side-by-side comparison of 2–3 titles. Facts only; no verdict is invented. */
@Composable
fun CompareScreen(container: AppContainer, onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: CompareViewModel = viewModel()) {
    val chosen by container.compare.collectAsState()
    val ui by vm.ui.collectAsState()
    val arabic = isArabic()
    LaunchedEffect(chosen) { if (chosen.size >= 2) vm.load(chosen) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        Text(tr("⚖️ قارن قبل المشاهدة", "⚖️ Compare before watching"), style = MaterialTheme.typography.headlineSmall)
        if (chosen.size < 2) {
            EmptyBox(tr("اختر عملين على الأقل: من صفحة العمل اضغط «قارن».", "Pick at least two titles: tap \"Compare\" on a title page."), Modifier.fillMaxWidth())
            return@Column
        }
        when (val st = ui) {
            is Load.Loading -> CenterLoading()
            is Load.Fail -> ErrorBox(st.error, onRetry = { vm.load(chosen) })
            is Load.Ok -> {
                val items = st.value
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { (card, _) ->
                        Column(Modifier.weight(1f)) {
                            PosterImage(Img.poster(card.posterPath), card.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                            Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { onOpen(card) }) { Text(tr("التفاصيل", "Details")) }
                            TextButton(onClick = { container.toggleCompare(card) }) { Text(tr("إزالة", "Remove")) }
                        }
                    }
                }
                val d = items.map { it.second }
                val cards = items.map { it.first }
                CompareRow(tr("النوع", "Type"), cards.map { if (it.mediaType == "movie") tr("فيلم", "Movie") else tr("مسلسل", "TV series") })
                CompareRow(tr("سنة الإصدار", "Year"), cards.map { it.year ?: "" })
                CompareRow(tr("التقييم", "Rating"), cards.map { c -> c.rating?.let { "★ " + fmt1(it) } ?: "" })
                CompareRow(tr("عدد الأصوات", "Votes"), cards.map { it.votes?.toString() ?: "" })
                CompareRow(
                    tr("المدة", "Length"),
                    items.map { (card, x) ->
                        if (card.mediaType == "movie") x.runtime?.takeIf { it > 0 }?.let { "$it " + tr("دقيقة", "min") } ?: ""
                        else listOfNotNull(
                            x.numberOfSeasons?.let { "$it " + tr("مواسم", "seasons") },
                            x.numberOfEpisodes?.let { "$it " + tr("حلقة", "eps") },
                        ).joinToString(" · ")
                    },
                )
                CompareRow(tr("الأنواع", "Genres"), d.map { x -> x.genres.orEmpty().joinToString("، ".takeIf { arabic } ?: ", ") { it.name } }, 4)
                CompareRow(
                    tr("اللغة الأصلية", "Original language"),
                    d.map { x -> x.originalLanguage?.takeIf { it.isNotBlank() }?.let { NodeFactory.language(it).name(arabic) } ?: "" },
                )
                CompareRow(tr("القصة", "Overview"), d.map { it.overview.orEmpty() }, 6)
                CompareRow(tr("أبرز الممثلين", "Top cast"), d.map { x -> x.credits?.cast.orEmpty().take(5).joinToString("، ".takeIf { arabic } ?: ", ") { it.name } }, 4)

                val castSets = d.map { x -> x.credits?.cast.orEmpty().take(30).map { it.id }.toSet() }
                val common = intersectAll(castSets)
                val names = d.first().credits?.cast.orEmpty().filter { it.id in common }.map { it.name }
                Text(
                    tr("ممثلون مشتركون: ", "Shared cast: ") + (if (names.isEmpty()) tr("لا يوجد (ضمن أول 30 ممثلًا)", "none (among top 30 billed)") else names.joinToString("، ".takeIf { arabic } ?: ", ")),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
