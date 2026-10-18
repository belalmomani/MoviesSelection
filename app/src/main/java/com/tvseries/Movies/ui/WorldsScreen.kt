@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.FilterEngine
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.data.PoolSnapshot
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorldsData(
    val taxonomy: Taxonomy,
    val counts: Map<String, Int>,
    val cards: Map<String, WorkCard>,
    val members: Map<String, List<String>>, // branch id -> work keys
    val loaded: Int,
)

class WorldsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val _ui = MutableStateFlow<Load<WorldsData>>(Load.Loading)
    val ui: StateFlow<Load<WorldsData>> = _ui.asStateFlow()

    fun load(more: Boolean = false) {
        viewModelScope.launch {
            _ui.value = Load.Loading
            try {
                c.session.ensureLoaded(more)
                val snap: PoolSnapshot = c.session.snapshot()
                val works = c.session.visibleWorks()
                val view = FilterEngine(works, snap.taxonomy).axisView(Selection(), Axes.WORLD)
                val counts = view.values.associate { it.value to it.inResult }
                val members = HashMap<String, MutableList<String>>()
                for (w in works) for (id in w.facets[Axes.WORLD].orEmpty()) members.getOrPut(id) { ArrayList() }.add(w.key)
                _ui.value = Load.Ok(WorldsData(snap.taxonomy, counts, snap.cards, members, works.size))
            } catch (e: TmdbError) {
                _ui.value = Load.Fail(e)
            }
        }
    }
}

/** Worlds -> branches -> (filter). Built locally from genres/keywords; these are estimates, not official categories. */
@Composable
fun WorldsScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, onFilter: (Selection) -> Unit, vm: WorldsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    var world by remember { mutableStateOf<String?>(null) }
    var branch by remember { mutableStateOf<String?>(null) }
    val arabic = isArabic()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("🗺️ خريطة العوالم", "🗺️ Cinema worlds"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                tr("عوالم وفروع بُنيت محليًا من أنواع TMDB وكلماته المفتاحية. هي تقديرات من التطبيق وليست تصنيفات رسمية، والفروع المبنية على الكلمات تظهر فقط للأعمال التي عُرفت كلماتها.", "Worlds and branches built locally from TMDB genres and keywords. They are app estimates, not official categories; keyword-based branches appear only for titles whose keywords are known."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = ui) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(s.error, onRetry = { vm.load() })
                is Load.Ok -> {
                    val d = s.value
                    val tax = d.taxonomy
                    val worlds = tax.nodes.filter { it.axis == Axes.WORLD && it.parents.isEmpty() && (d.counts[it.id] ?: 0) > 0 }
                        .sortedByDescending { d.counts[it.id] ?: 0 }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        worlds.forEach { w ->
                            FilterChip(
                                selected = world == w.id,
                                onClick = { world = if (world == w.id) null else w.id; branch = null },
                                label = { Text(w.name(arabic) + " · " + (d.counts[w.id] ?: 0)) },
                            )
                        }
                    }
                    val wid = world
                    if (wid != null) {
                        Text(tr("فروع هذا العالم", "Branches of this world"), style = MaterialTheme.typography.titleSmall)
                        val branches = tax.childrenIds(wid).filter { (d.counts[it] ?: 0) > 0 }.sortedByDescending { d.counts[it] ?: 0 }
                        if (branches.isEmpty()) Text(tr("لا فروع ظاهرة ضمن المحمّل.", "No branches among loaded titles."), style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            branches.forEach { b ->
                                FilterChip(
                                    selected = branch == b,
                                    onClick = { branch = if (branch == b) null else b },
                                    label = { Text((tax.node(b)?.name(arabic) ?: b) + " · " + (d.counts[b] ?: 0)) },
                                )
                            }
                        }
                    }
                    val target = branch ?: world
                    if (target != null) {
                        val keys = d.members[target].orEmpty()
                        Text(tr("${keys.size} عملًا في «${tax.node(target)?.name(arabic) ?: target}»", "${keys.size} titles in \"${tax.node(target)?.name(arabic) ?: target}\""), style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { onFilter(Selection(include = mapOf(Axes.WORLD to setOf(target)))) }) {
                            Text(tr("تضييق خطوة بخطوة", "Narrow step by step"))
                        }
                        keys.take(15).forEach { k -> d.cards[k]?.let { card -> WorkRow(card, onClick = { onOpen(card) }) } }
                    }
                    OutlinedButton(onClick = { vm.load(true) }) { Text(tr("حمّل المزيد من الأعمال", "Load more titles")) }
                    Text(tr("المحمّل: ${d.loaded} عمل.", "Loaded: ${d.loaded} titles."), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
