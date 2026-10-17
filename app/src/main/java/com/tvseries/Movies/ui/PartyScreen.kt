@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.PartyMatcher
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.WorkPool
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface PartyUi {
    data object Idle : PartyUi
    data object Loading : PartyUi
    data class Done(val confirmed: List<WorkCard>, val possible: List<WorkCard>, val loaded: Int) : PartyUi
    data class Failed(val error: TmdbError) : PartyUi
}

class PartyViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val pool = WorkPool()
    private val _ui = MutableStateFlow<PartyUi>(PartyUi.Idle)
    val ui: StateFlow<PartyUi> = _ui.asStateFlow()

    fun compute(a: Selection, b: Selection, loadMore: Boolean) {
        viewModelScope.launch {
            _ui.value = PartyUi.Loading
            _ui.value = try {
                execute(a, b, loadMore)
            } catch (e: TmdbError) {
                PartyUi.Failed(e)
            }
        }
    }

    private suspend fun execute(a: Selection, b: Selection, loadMore: Boolean): PartyUi {
        if (pool.size == 0 || loadMore) {
            val base = c.repo.baseQuery()
            for (media in listOf("movie", "tv")) {
                val s = pool.stream(media, base.signature())
                var pages = 0
                while (pages < 3 && s.hasMore) {
                    val page = c.repo.discover(media, s.nextPage, base)
                    pool.addListItems(media, page.results)
                    s.nextPage += 1
                    s.totalPages = page.totalPages
                    pages++
                }
            }
        }
        var done = match(a, b)
        // Titles that might fit but lack data (keywords, runtime...): fetch details once, then re-check.
        val need = done.second.possible.map { it.key }.filter { !pool.isEnriched(it) }.take(30)
        if (need.isNotEmpty()) {
            val gate = Semaphore(4)
            val fetched = coroutineScope {
                need.map { k ->
                    async {
                        gate.withPermit {
                            val m = k.substringBefore(":")
                            k to runCatching { c.repo.details(m, k.substringAfter(":").toInt()) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            for ((k, d) in fetched) if (d != null) pool.addDetails(k.substringBefore(":"), d)
            done = match(a, b)
        }
        return done.first
    }

    private fun match(a: Selection, b: Selection): Pair<PartyUi.Done, PartyMatcher.Result> {
        val snap = pool.snapshot()
        val watched = c.prefs.watched.value
        val works = snap.works.filter { it.key !in watched }
        val r = PartyMatcher.intersect(works, a, b)
        val done = PartyUi.Done(
            r.confirmed.mapNotNull { snap.cards[it.key] },
            r.possible.mapNotNull { snap.cards[it.key] },
            works.size,
        )
        return done to r
    }
}

@Composable
private fun PersonCard(
    title: String,
    mood: Presets.Choice?, onMood: (Presets.Choice?) -> Unit,
    tension: Presets.Choice?, onTension: (Presets.Choice?) -> Unit,
    avoidOn: List<AvoidOption>, onAvoid: (AvoidOption) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            ChoiceRow(tr("مزاجه", "Mood"), Presets.moods, mood, onMood)
            ChoiceRow(tr("مستوى التوتر", "Tension"), Presets.tensions, tension, onTension)
            Text(tr("لا يريد...", "Doesn't want..."), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.avoid.forEach { a ->
                    FilterChip(selected = avoidOn.any { it.id == a.id }, onClick = { onAvoid(a) }, label = { Text(tr(a.ar, a.en)) })
                }
            }
        }
    }
}

/** Two people pick mood / dislikes separately; the app suggests titles that fit BOTH. */
@Composable
fun PartyScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: PartyViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    var m1 by remember { mutableStateOf<Presets.Choice?>(null) }
    var t1 by remember { mutableStateOf<Presets.Choice?>(null) }
    var a1 by remember { mutableStateOf<List<AvoidOption>>(emptyList()) }
    var m2 by remember { mutableStateOf<Presets.Choice?>(null) }
    var t2 by remember { mutableStateOf<Presets.Choice?>(null) }
    var a2 by remember { mutableStateOf<List<AvoidOption>>(emptyList()) }

    fun toggle(list: List<AvoidOption>, a: AvoidOption) = if (list.any { it.id == a.id }) list.filterNot { it.id == a.id } else list + a
    fun sel1() = Presets.build(m1, t1, null, null, a1)
    fun sel2() = Presets.build(m2, t2, null, null, a2)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        Text(tr("🎉 سهرة جماعية", "🎉 Group watch"), style = MaterialTheme.typography.headlineSmall)
        Text(
            tr("يختار كل شخص مزاجه وما لا يريده، ويقترح التطبيق أعمالًا تناسب الاثنين معًا.", "Each person picks a mood and dislikes; the app suggests titles that fit both."),
            style = MaterialTheme.typography.bodySmall,
        )
        PersonCard(tr("الشخص الأول", "Person 1"), m1, { m1 = it }, t1, { t1 = it }, a1) { a1 = toggle(a1, it) }
        PersonCard(tr("الشخص الثاني", "Person 2"), m2, { m2 = it }, t2, { t2 = it }, a2) { a2 = toggle(a2, it) }
        Button(onClick = { vm.compute(sel1(), sel2(), false) }, modifier = Modifier.fillMaxWidth()) { Text(tr("اقترح لنا", "Suggest for us")) }

        when (val r = ui) {
            is PartyUi.Idle -> {}
            is PartyUi.Loading -> CenterLoading()
            is PartyUi.Failed -> ErrorBox(r.error, onRetry = { vm.compute(sel1(), sel2(), false) })
            is PartyUi.Done -> {
                Text(
                    tr("يناسب الاثنين (مؤكد): ${r.confirmed.size} من ${r.loaded} عمل محمّل", "Fits both (confirmed): ${r.confirmed.size} of ${r.loaded} loaded titles"),
                    style = MaterialTheme.typography.titleSmall,
                )
                val pick = r.confirmed.ifEmpty { r.possible }
                if (pick.isNotEmpty()) {
                    OutlinedButton(onClick = { pick.randomOrNull()?.let(onOpen) }) { Text(tr("🎲 اختر لنا واحدًا", "🎲 Pick one for us")) }
                }
                if (r.confirmed.isEmpty()) {
                    Text(tr("لا يوجد عمل مؤكد يناسب الاثنين ضمن المحمّل. خفّفوا شرطًا أو وسّعوا البحث.", "No confirmed title fits both among the loaded ones. Relax a condition or load more."), style = MaterialTheme.typography.bodySmall)
                }
                r.confirmed.take(20).forEach { WorkRow(it, onClick = { onOpen(it) }) }
                if (r.possible.isNotEmpty()) {
                    Text(tr("قد تناسب الاثنين (بيانات ناقصة): ${r.possible.size}", "Might fit both (missing data): ${r.possible.size}"), style = MaterialTheme.typography.titleSmall)
                    r.possible.take(10).forEach { WorkRow(it, onClick = { onOpen(it) }) }
                }
                OutlinedButton(onClick = { vm.compute(sel1(), sel2(), true) }) { Text(tr("وسّع البحث", "Load more titles")) }
            }
        }
    }
}
