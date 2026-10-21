@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.FinalStage
import com.tvseries.Movies.core.Stage
import com.tvseries.Movies.data.DetailsDto
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.WorkCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FinalEntry(val card: WorkCard, val details: DetailsDto?) {
    val lead get() = details?.credits?.cast.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }.firstOrNull()
    val genreIds: Set<Int> get() = details?.genres.orEmpty().map { it.id }.toSet()
}

class FinalStageViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _stage = MutableStateFlow(Stage.HEROES)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    private val _remaining = MutableStateFlow<List<FinalEntry>>(emptyList())
    val remaining: StateFlow<List<FinalEntry>> = _remaining.asStateFlow()

    private var started = false

    fun start(cards: List<WorkCard>) {
        if (started) return
        started = true
        viewModelScope.launch {
            val gate = Semaphore(4)
            val entries = coroutineScope {
                cards.map { card ->
                    async { gate.withPermit { FinalEntry(card, runCatching { c.repo.details(card.mediaType, card.id) }.getOrNull()) } }
                }.awaitAll()
            }
            _remaining.value = entries
            _stage.value = FinalStage.next(entries.size, null)
            _loading.value = false
        }
    }

    private fun narrowTo(keys: Set<String>) {
        val next = FinalStage.narrow(_remaining.value.map { it.card.key }, keys)
        _remaining.value = _remaining.value.filter { it.card.key in next }
        _stage.value = FinalStage.next(_remaining.value.size, _stage.value)
    }

    fun applyHeroes(keys: Set<String>) = narrowTo(keys)
    fun applyStory(keys: Set<String>) = narrowTo(keys)
    fun skipStage() {
        _stage.value = FinalStage.next(_remaining.value.size, _stage.value)
    }

    fun applyGenres(ids: Set<Int>) {
        if (ids.isNotEmpty()) {
            _remaining.value = _remaining.value.filter { e -> e.details == null || e.genreIds.any { it in ids } }
        }
        _stage.value = FinalStage.next(_remaining.value.size, _stage.value)
    }

    fun pick(key: String) {
        _remaining.value = _remaining.value.filter { it.card.key == key }
        _stage.value = Stage.WINNER
    }

    fun addToWatchLater(card: WorkCard) {
        if (c.prefs.watchlist.value.none { it.key == card.key }) c.prefs.toggleWatchlist(card)
    }
}

@Composable
private fun StoryText(e: FinalEntry, maxLines: Int) {
    val text = e.details?.overview?.takeIf { it.isNotBlank() }
    Text(
        text ?: tr("لا تتوفر قصة لهذا العمل في TMDB.", "No story available for this title on TMDB."),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun FinalStageScreen(container: AppContainer, onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: FinalStageViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.start(container.finalCandidates) }
    val loading by vm.loading.collectAsState()
    val stage by vm.stage.collectAsState()
    val remaining by vm.remaining.collectAsState()
    val arabic = isArabic()
    var chosen by remember(stage, remaining.size) { mutableStateOf(setOf<String>()) }
    var chosenGenres by remember(stage, remaining.size) { mutableStateOf(setOf<Int>()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("الفلترة النهائية", "Final filtering"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) {
                CenterLoading()
                Text(tr("نجلب أبطال الأعمال وقصصها...", "Fetching leads and stories..."), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(tr("${remaining.size} عمل متبقٍ", "${remaining.size} titles left"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (stage) {
                    Stage.HEROES -> {
                        Text(tr("اختر الأبطال الذين يعجبونك", "Pick the leads you like"), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("اضغط على الأعمال التي يعجبك بطلها (ويبقى منها ما تختاره).", "Tap the titles whose lead you like; only those stay."), style = MaterialTheme.typography.bodySmall)
                        remaining.forEach { e ->
                            val on = e.card.key in chosen
                            Card(
                                Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - e.card.key else chosen + e.card.key }
                                    .border(if (on) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val lead = e.lead
                                    PosterImage(Img.profile(lead?.profilePath), lead?.name ?: e.card.title, Modifier.size(72.dp).clip(CircleShape))
                                    Column(Modifier.weight(1f)) {
                                        Text(lead?.name ?: tr("البطل غير معروف", "Lead unknown"), style = MaterialTheme.typography.titleSmall)
                                        lead?.character?.takeIf { it.isNotBlank() }?.let { Text(tr("بدور: ", "as ") + it, style = MaterialTheme.typography.bodySmall) }
                                        Text(e.card.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (on) Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.applyHeroes(chosen) }, enabled = chosen.isNotEmpty()) { Text(tr("تصفية (${chosen.size})", "Filter (${chosen.size})")) }
                            TextButton(onClick = { vm.skipStage() }) { Text(tr("تخطَّ", "Skip")) }
                        }
                    }
                    Stage.GENRES -> {
                        val counts = HashMap<Int, Pair<String, Int>>()
                        remaining.forEach { e -> e.details?.genres.orEmpty().forEach { g -> counts[g.id] = g.name to ((counts[g.id]?.second ?: 0) + 1) } }
                        Text(tr("أي أجواء تريد؟", "Which vibes do you want?"), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("اختر عدة خيارات (مثل إثارة أو أكشن أو رعب)؛ يبقى ما يحمل أيًّا منها.", "Pick several (e.g. thriller, action, horror); titles with any of them stay."), style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            counts.entries.sortedByDescending { it.value.second }.forEach { (id, p) ->
                                FilterChip(
                                    selected = id in chosenGenres,
                                    onClick = { chosenGenres = if (id in chosenGenres) chosenGenres - id else chosenGenres + id },
                                    label = { Text(p.first + " · " + p.second) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.applyGenres(chosenGenres) }, enabled = chosenGenres.isNotEmpty()) { Text(tr("تصفية (${chosenGenres.size})", "Filter (${chosenGenres.size})")) }
                            TextButton(onClick = { vm.skipStage() }) { Text(tr("تخطَّ", "Skip")) }
                        }
                    }
                    Stage.STORY -> {
                        Text(tr("اختر بحسب القصة", "Choose by story"), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("اقرأ القصص واختر واحدة أو اثنتين تجذبانك.", "Read the stories and pick one or two that draw you."), style = MaterialTheme.typography.bodySmall)
                        remaining.forEach { e ->
                            val on = e.card.key in chosen
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    chosen = if (on) chosen - e.card.key else if (chosen.size < 2) chosen + e.card.key else chosen
                                }.border(if (on) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(e.card.title + (if (on) "  ✓" else ""), style = MaterialTheme.typography.titleSmall)
                                    StoryText(e, 7)
                                }
                            }
                        }
                        Button(onClick = { vm.applyStory(chosen) }, enabled = chosen.isNotEmpty()) { Text(tr("هذه قصصي المفضلة (${chosen.size}/2)", "These stories (${chosen.size}/2)")) }
                    }
                    Stage.DUEL -> {
                        Text(tr("الاختيار الأخير: أيهما؟", "Final choice: which one?"), style = MaterialTheme.typography.headlineSmall)
                        remaining.forEach { e ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PosterImage(Img.poster(e.card.posterPath), e.card.title, Modifier.width(90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(e.card.title, style = MaterialTheme.typography.titleSmall)
                                        Text(e.details?.genres.orEmpty().joinToString(if (arabic) "، " else ", ") { it.name }, style = MaterialTheme.typography.labelMedium)
                                        StoryText(e, 8)
                                        Button(onClick = { vm.pick(e.card.key) }) { Text(tr("اخترُه", "Pick this")) }
                                    }
                                }
                            }
                        }
                    }
                    Stage.WINNER -> {
                        val w = remaining.firstOrNull()
                        if (w == null) Text(tr("لا يوجد عمل متبقٍ.", "Nothing left."))
                        else {
                            Text(tr("هذا هو اختيارك 🎯", "This is your pick 🎯"), style = MaterialTheme.typography.headlineSmall)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WorkRow(w.card, onClick = { onOpen(w.card) })
                                    StoryText(w, 10)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpen(w.card) }) { Text(tr("افتح التفاصيل", "Open details")) }
                                OutlinedButton(onClick = { vm.addToWatchLater(w.card) }) { Text(tr("شاهد لاحقًا", "Watch later")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
