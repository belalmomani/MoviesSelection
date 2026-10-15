@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.core.AxisDef
import com.tvseries.Movies.core.AxisView
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Condition
import com.tvseries.Movies.core.FacetCount
import com.tvseries.Movies.core.Outcome
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.data.WorkCard

@Composable
fun FilterScreen(onOpen: (WorkCard) -> Unit, vm: FilterViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val state by vm.state.collectAsState()
    val loading by vm.loading.collectAsState()
    val enriching by vm.enriching.collectAsState()
    val error by vm.error.collectAsState()
    val excludeMode by vm.excludeMode.collectAsState()
    val arabic = isArabic()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) { vm.consumePreset() }

    val u = ui
    val sel = state.selection

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("التصفية الذكية", "Smart filter"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.back() }, enabled = state.canGoBack) { Text(tr("رجوع خطوة", "Undo")) }
                TextButton(onClick = { vm.clearAll() }, enabled = !sel.isEmpty) { Text(tr("مسح الكل", "Clear all")) }
            }
        }
        if (loading || enriching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        // Current path (breadcrumb): every condition can be removed independently.
        item {
            val conds = sel.conditions()
            if (conds.isNotEmpty() && u != null) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(tr("المسار الحالي", "Current path"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        conds.forEach { cond -> ConditionChip(cond, u.snapshot.taxonomy, arabic) { vm.removeCondition(cond) } }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = excludeMode, onCheckedChange = { vm.setExcludeMode(it) })
                Text(
                    if (excludeMode) tr("وضع الاستبعاد: اضغط قيمة لاستبعادها", "Exclude mode: tap a value to exclude it")
                    else tr("وضع الاختيار: اضغط قيمة لتضمينها", "Include mode: tap a value to include it"),
                    modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        error?.let { e -> item { ErrorBox(e, onRetry = { vm.loadMore(false) }) } }

        if (u == null) {
            item { CenterLoading() }
        } else {
            item { OutcomeCard(u.outcome, u.snapshot.cards, u.snapshot.taxonomy, arabic, onOpen, u.result.matched.size, u.result.possible.size, u.snapshot.works.size, u.poolComplete) }

            // Axis suggestions first, then every other axis that still has useful values.
            val suggested = u.suggestions.map { it.axis }
            val order = suggested + u.axisViews.keys.filter { it !in suggested }
            item {
                Text(
                    tr("الخطوة التالية المقترحة هي المحاور المفتوحة بالأعلى؛ الترتيب يتغير حسب اختياراتك وبياناتك.", "Suggested next steps are the open axes on top; the order changes with your selection and data."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            items(order, key = { "axis-$it" }) { axisId ->
                val view = u.axisViews[axisId]
                val def = u.snapshot.taxonomy.axes.firstOrNull { it.id == axisId }
                if (view != null && def != null) {
                    val isSuggestedTop = suggested.take(3).contains(axisId)
                    val isOpen = expanded[axisId] ?: (isSuggestedTop || sel.include[axisId] != null || sel.exclude[axisId] != null)
                    AxisSection(
                        def = def, view = view, taxonomy = u.snapshot.taxonomy, arabic = arabic, open = isOpen,
                        suggested = isSuggestedTop, expandedNodes = expandedNodes,
                        onToggleOpen = { expanded[axisId] = !isOpen },
                        onPick = { value -> vm.toggle(axisId, value) },
                    )
                }
            }

            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("المحمّل الآن: ${u.snapshot.works.size} عمل من TMDB.", "Loaded now: ${u.snapshot.works.size} titles from TMDB."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.loadMore(false) }, enabled = !loading) { Text(tr("تحميل المزيد", "Load more")) }
                        OutlinedButton(onClick = { vm.loadMore(true) }, enabled = !loading && !sel.isEmpty) { Text(tr("المزيد المطابق", "More matching")) }
                    }
                    Button(onClick = { vm.enrichShown() }, enabled = !enriching) {
                        Text(tr("إثراء النتائج (جلب بيانات ناقصة لـ 20 عملًا)", "Enrich results (fetch missing data for 20 titles)"))
                    }
                }
                HorizontalDivider()
            }

            val shown = u.result.matched.take(60)
            item {
                SectionTitle(tr("النتائج المؤكدة: ${u.result.matched.size}", "Confirmed results: ${u.result.matched.size}"))
            }
            items(shown, key = { "m-" + it.key }) { w ->
                u.snapshot.cards[w.key]?.let { WorkRow(it, onClick = { onOpen(it) }) }
            }
            if (u.result.possible.isNotEmpty()) {
                item {
                    SectionTitle(tr("محتملة (بيانات ناقصة): ${u.result.possible.size}", "Possible (missing data): ${u.result.possible.size}"))
                    Text(
                        tr("هذه الأعمال لا تخالف شروطك، لكن معلومة مطلوبة عنها غير معروفة بعد.", "These do not contradict your conditions, but some needed information is not known yet."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                items(u.result.possible.take(40), key = { "p-" + it.key }) { w ->
                    u.snapshot.cards[w.key]?.let { WorkRow(it, onClick = { onOpen(it) }) }
                }
            }
        }
    }
}

@Composable
private fun ConditionChip(cond: Condition, taxonomy: Taxonomy, arabic: Boolean, onRemove: () -> Unit) {
    val name = taxonomy.node(cond.value)?.name(arabic) ?: cond.value
    val axisName = taxonomy.axes.firstOrNull { it.id == cond.axis }?.name(arabic) ?: cond.axis
    AssistChip(
        onClick = onRemove,
        label = {
            Text(
                (if (cond.excluded) "⊘ " else "") + axisName + ": " + name,
                textDecoration = if (cond.excluded) TextDecoration.LineThrough else null,
            )
        },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = tr("إزالة", "Remove"), modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun OutcomeCard(
    outcome: Outcome,
    cards: Map<String, WorkCard>,
    taxonomy: Taxonomy,
    arabic: Boolean,
    onOpen: (WorkCard) -> Unit,
    matched: Int,
    possible: Int,
    loaded: Int,
    complete: Boolean,
) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (outcome) {
                is Outcome.PoolEmpty -> Text(tr("لم تُحمَّل أي بيانات بعد. هذا لا يعني عدم وجود نتائج.", "No data loaded yet. This does not mean there are no results."))
                is Outcome.NoResults -> {
                    Text(tr("لا توجد نتائج مؤكدة.", "No confirmed results."), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (outcome.poolComplete) tr("لا يوجد عمل مطابق ضمن كل ما حُمّل ($loaded عمل). خفّف أحد الشروط.", "Nothing matches in all loaded data ($loaded titles). Relax a condition.")
                        else tr("لا توجد نتيجة ضمن الأعمال المحمّلة فقط ($loaded). اضغط «المزيد المطابق» لجلب أعمال جديدة من TMDB قبل الحكم بعدم وجودها.", "No match among the $loaded loaded titles only. Tap \"More matching\" to fetch more from TMDB before concluding none exist."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is Outcome.OnlyPossible -> {
                    Text(tr("لا نتيجة مؤكدة، لكن ${outcome.possibleCount} عمل قد يطابق.", "No confirmed result, but ${outcome.possibleCount} titles might match."), style = MaterialTheme.typography.titleSmall)
                    Text(tr("بعض بياناتها غير معروفة بعد. اضغط «إثراء النتائج» لاستكمالها.", "Some of their data is not known yet. Tap \"Enrich results\" to complete it."), style = MaterialTheme.typography.bodySmall)
                }
                is Outcome.Single -> {
                    val card = cards[outcome.work.key]
                    Text(tr("وصلت إلى عمل واحد 🎯", "You reached a single title 🎯"), style = MaterialTheme.typography.titleSmall)
                    if (card != null) WorkRow(card, onClick = { onOpen(card) })
                    Text(tr("أسباب المطابقة (مؤكدة من البيانات):", "Why it matches (verified from data):"), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        outcome.reasons.forEach { r ->
                            val n = taxonomy.node(r.value)?.name(arabic) ?: r.value
                            val ax = taxonomy.axes.firstOrNull { it.id == r.axis }?.name(arabic) ?: r.axis
                            AssistChip(onClick = {}, label = { Text((if (r.excluded) "⊘ " else "") + ax + ": " + n) })
                        }
                    }
                    if (outcome.otherPossible > 0) {
                        Text(tr("يوجد ${outcome.otherPossible} عمل آخر محتمل ببيانات ناقصة.", "${outcome.otherPossible} other titles are possible with missing data."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                is Outcome.Indistinguishable -> {
                    Text(tr("تعذّر التمييز بين ${outcome.works.size} أعمال بالسمات المعروفة.", "Cannot tell ${outcome.works.size} titles apart with the known attributes."), style = MaterialTheme.typography.titleSmall)
                    Text(tr("لا توجد خطوة تصفية مفيدة أخرى؛ كل الأعمال المتبقية معروضة أدناه. جرّب «إثراء النتائج» لإضافة سمات.", "No further useful filter step; all remaining titles are listed below. Try \"Enrich results\" to add attributes."), style = MaterialTheme.typography.bodySmall)
                }
                is Outcome.Many -> {
                    Text(tr("${outcome.count} نتيجة مؤكدة", "${outcome.count} confirmed results"), style = MaterialTheme.typography.titleSmall)
                    val names = outcome.next.take(3).mapNotNull { s -> taxonomy.axes.firstOrNull { it.id == s.axis }?.name(arabic) }
                    if (names.isNotEmpty()) Text(tr("الأنسب للتضييق الآن: ", "Best next narrowing: ") + names.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                    if (possible > 0) Text(tr("+ $possible محتملة ببيانات ناقصة.", "+ $possible possible with missing data."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AxisSection(
    def: AxisDef,
    view: AxisView,
    taxonomy: Taxonomy,
    arabic: Boolean,
    open: Boolean,
    suggested: Boolean,
    expandedNodes: SnapshotStateMap<String, Boolean>,
    onToggleOpen: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggleOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                def.name(arabic) + (if (suggested) " ★" else ""),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            Text("${view.values.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (open) {
            var query by remember(def.id) { mutableStateOf("") }
            var limit by remember(def.id) { mutableStateOf(40) }
            if (view.values.size > 12) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(tr("ابحث داخل ${def.nameAr}", "Search in ${def.nameEn}")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val counts = view.values.associateBy { it.value }
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val hits = view.values.filter { fc ->
                    val n = taxonomy.node(fc.value)
                    n != null && (q in n.nameAr.lowercase() || q in n.nameEn.lowercase() || n.synonyms.any { q in it.lowercase() })
                }.take(limit)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hits.forEach { ValueChip(it, taxonomy, arabic, onPick) }
                }
                if (hits.isEmpty()) Text(tr("لا قيم مطابقة للبحث.", "No values match the search."), style = MaterialTheme.typography.bodySmall)
            } else {
                val present = counts.keys
                val childrenOf = HashMap<String, MutableList<String>>()
                val roots = ArrayList<String>()
                for (fc in view.values) {
                    val parents = taxonomy.node(fc.value)?.parents.orEmpty().filter { it in present }
                    if (parents.isEmpty()) roots += fc.value
                    else for (p in parents) childrenOf.getOrPut(p) { ArrayList() }.add(fc.value)
                }
                ValueTree(roots.take(limit), counts, childrenOf, taxonomy, arabic, expandedNodes, onPick)
                if (roots.size > limit) TextButton(onClick = { limit += 40 }) { Text(tr("عرض المزيد", "Show more")) }
            }
            if (view.unknownInResult > 0) {
                Text(
                    tr("${view.unknownInResult} نتيجة بلا بيانات لهذا المحور (غير معروف وليس غير مطابق).", "${view.unknownInResult} results have no data on this axis (unknown, not a mismatch)."),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ValueTree(
    ids: List<String>,
    counts: Map<String, FacetCount>,
    childrenOf: Map<String, List<String>>,
    taxonomy: Taxonomy,
    arabic: Boolean,
    expandedNodes: SnapshotStateMap<String, Boolean>,
    onPick: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.forEach { id ->
            val fc = counts[id] ?: return@forEach
            val kids = childrenOf[id].orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueChip(fc, taxonomy, arabic, onPick)
                if (kids.isNotEmpty()) {
                    val isOpen = expandedNodes[id] == true
                    Icon(
                        if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = tr("التصنيفات الفرعية", "Sub-categories"),
                        modifier = Modifier.size(28.dp).clickable { expandedNodes[id] = !isOpen },
                    )
                }
            }
        }
    }
    ids.forEach { id ->
        val kids = childrenOf[id].orEmpty()
        if (kids.isNotEmpty() && expandedNodes[id] == true) {
            Column(Modifier.padding(start = 16.dp, top = 4.dp)) {
                ValueTree(kids, counts, childrenOf, taxonomy, arabic, expandedNodes, onPick)
            }
        }
    }
}

@Composable
private fun ValueChip(fc: FacetCount, taxonomy: Taxonomy, arabic: Boolean, onPick: (String) -> Unit) {
    val name = taxonomy.node(fc.value)?.name(arabic) ?: fc.value
    val count = if (fc.selected || fc.excluded) fc.inResult else fc.afterInclude
    FilterChip(
        selected = fc.selected,
        onClick = { onPick(fc.value) },
        label = {
            Text(
                (if (fc.excluded) "⊘ " else "") + name + " · " + count,
                textDecoration = if (fc.excluded) TextDecoration.LineThrough else null,
            )
        },
    )
}
