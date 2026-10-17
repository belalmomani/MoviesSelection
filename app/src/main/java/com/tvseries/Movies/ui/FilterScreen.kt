@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.AxisView
import com.tvseries.Movies.core.Condition
import com.tvseries.Movies.core.FacetCount
import com.tvseries.Movies.core.Outcome
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.data.WorkCard

/** Axes asked first in the guided flow. Others (cast, director, spoken languages) are only a fallback. */
private val primaryAxes = setOf(
    Axes.FORMAT, Axes.MOOD, Axes.TENSION, Axes.GENRE, Axes.COUNTRY, Axes.LANGUAGE, Axes.YEAR, Axes.RATING,
    Axes.RUNTIME, Axes.SEASONS, Axes.STATUS, Axes.KEYWORD,
)

@Composable
private fun questionTitle(axis: String, fallback: String): String = when (axis) {
    Axes.FORMAT -> tr("ما نوع العمل؟", "What kind of title?")
    Axes.GENRE -> tr("ما النوع الذي تفضّله؟", "Which genre?")
    Axes.COUNTRY -> tr("من أي بلد؟", "From which country?")
    Axes.LANGUAGE -> tr("بأي لغة أصلية؟", "Original language?")
    Axes.YEAR -> tr("من أي فترة؟", "From which period?")
    Axes.RATING -> tr("ما الحد الأدنى للتقييم؟", "Minimum rating?")
    Axes.RUNTIME -> tr("ما المدة المناسبة؟", "How long?")
    Axes.SEASONS -> tr("كم عدد المواسم؟", "How many seasons?")
    Axes.STATUS -> tr("ما حالة العمل؟", "Status?")
    Axes.KEYWORD -> tr("أي موضوع؟", "Which topic?")
    Axes.MOOD -> tr("ماذا تريد أن يفعل بك العمل؟", "What should it do for you?")
    Axes.TENSION -> tr("ما مستوى التوتر؟", "How tense?")
    Axes.ENDING -> tr("أي نهاية؟", "Which ending?")
    else -> fallback
}

@Composable
fun FilterScreen(onOpen: (WorkCard) -> Unit, vm: FilterViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val state by vm.state.collectAsState()
    val loading by vm.loading.collectAsState()
    val enriching by vm.enriching.collectAsState()
    val error by vm.error.collectAsState()
    val excludeMode by vm.excludeMode.collectAsState()
    val skipped by vm.skipped.collectAsState()
    val notice by vm.notice.collectAsState()
    val arabic = isArabic()

    LaunchedEffect(Unit) {
        vm.consumePreset()
        vm.refresh()
    }
    val savedFilters by vm.savedFilters.collectAsState()
    var showSave by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val u = ui
    val sel = state.selection

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(tr("اسم التصفية", "Filter name")) },
            text = { OutlinedTextField(value = saveName, onValueChange = { saveName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.saveCurrent(saveName); saveName = ""; showSave = false }, enabled = saveName.isNotBlank()) { Text(tr("حفظ", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("اختر خطوة بخطوة", "Step by step"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.back() }, enabled = state.canGoBack) { Text(tr("رجوع", "Back")) }
                TextButton(onClick = { vm.clearAll() }, enabled = !sel.isEmpty) { Text(tr("ابدأ من جديد", "Restart")) }
            }
        }
        if (loading || enriching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        item {
            val conds = sel.conditions()
            if (conds.isNotEmpty() && u != null) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        conds.forEach { cond -> ConditionChip(cond, u.snapshot.taxonomy, arabic) { vm.removeCondition(cond) } }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showSave = true }, enabled = !sel.isEmpty) { Text(tr("💾 حفظ التصفية", "💾 Save filter")) }
                    TextButton(onClick = { showSaved = !showSaved }, enabled = savedFilters.isNotEmpty()) {
                        Text(tr("تصفياتي (${savedFilters.size})", "My filters (${savedFilters.size})"))
                    }
                }
                if (showSaved) {
                    savedFilters.forEach { sf ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sf.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.loadSelection(sf.selection); showSaved = false }) { Text(tr("تطبيق", "Apply")) }
                            TextButton(onClick = { vm.deleteSaved(sf.name) }) { Text(tr("حذف", "Delete")) }
                        }
                    }
                }
            }
        }
        item {
            var avoidOpen by remember { mutableStateOf(false) }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { avoidOpen = !avoidOpen }) {
                        Text(if (avoidOpen) tr("إخفاء «ما لا أريده»", "Hide \"things I don't want\"") else tr("ما لا أريده ▾", "Things I don't want ▾"))
                    }
                    TextButton(onClick = { vm.pickRandom()?.let(onOpen) }, enabled = (u?.result?.matched?.isNotEmpty() == true)) {
                        Text(tr("🎲 فاجئني", "🎲 Surprise me"))
                    }
                }
                if (avoidOpen) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Presets.avoid.forEach { a ->
                            FilterChip(selected = a.isOn(sel), onClick = { vm.toggleAvoid(a) }, label = { Text(tr(a.ar, a.en)) })
                        }
                    }
                    Text(
                        tr("بعض الاستبعادات تعتمد على كلمات TMDB المفتاحية وقد تفوتها أعمال.", "Some exclusions rely on TMDB keywords and may miss titles."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        error?.let { e -> item { ErrorBox(e, onRetry = { vm.loadMore(false) }) } }
        notice?.let {
            item {
                Text(
                    tr("تعذّر الاتصال بـ Wikidata؛ عُرضت نتائج TMDB فقط.", "Wikidata was unreachable; showing TMDB results only."),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        if (u == null) {
            item { CenterLoading() }
        } else {
            val outcome = u.outcome
            val tax = u.snapshot.taxonomy

            // Pick ONE question: best-ranked axis not yet skipped, preferring the friendly axes.
            val candidates = u.suggestions.map { it.axis }.filter { it != Axes.AVOID && it !in skipped && u.axisViews[it] != null }
            val questionAxis = (candidates.firstOrNull { it in primaryAxes } ?: candidates.firstOrNull())
                .takeIf { outcome is Outcome.Many }
            val questionView = questionAxis?.let { u.axisViews[it] }

            if (questionAxis != null && questionView != null) {
                item {
                    val def = tax.axes.first { it.id == questionAxis }
                    QuestionCard(
                        title = questionTitle(questionAxis, def.name(arabic)),
                        resultCount = u.result.matched.size,
                        axis = questionAxis, view = questionView, taxonomy = tax, arabic = arabic,
                        excludeMode = excludeMode,
                        onMode = { vm.setExcludeMode(it) },
                        onPick = { value -> vm.toggle(questionAxis, value) },
                        onApplyMany = { vals -> vm.applyMany(questionAxis, vals) },
                        onSkip = { vm.skip(questionAxis) },
                    )
                }
            } else {
                if (outcome !is Outcome.Many) {
                    item { OutcomeCard(outcome, u.snapshot.cards, tax, arabic, onOpen, u.result.possible.size, u.snapshot.works.size) }
                }
            }

            if (outcome is Outcome.Many && questionAxis == null) {
                item {
                    Text(
                        tr("لا توجد أسئلة مفيدة أخرى. هذه كل الأعمال المتبقية:", "No more useful questions. Here are all remaining titles:"),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val showList = outcome !is Outcome.Single && outcome !is Outcome.NoResults && outcome !is Outcome.PoolEmpty
            if (showList) {
                item { SectionTitle(tr("النتائج الحالية: ${u.result.matched.size}", "Current results: ${u.result.matched.size}")) }
                items(u.result.matched.take(40), key = { "m-" + it.key }) { w ->
                    u.snapshot.cards[w.key]?.let { WorkRow(it, onClick = { onOpen(it) }, badge = if (w.key in u.snapshot.wikidataKeys) tr("🌐 اكتُشف عبر Wikidata", "🌐 Found via Wikidata") else null) }
                }
            }
            if (u.result.possible.isNotEmpty() && outcome !is Outcome.Single) {
                item {
                    SectionTitle(tr("قد تطابق (بياناتها ناقصة): ${u.result.possible.size}", "Might match (missing data): ${u.result.possible.size}"))
                }
                items(u.result.possible.take(15), key = { "p-" + it.key }) { w ->
                    u.snapshot.cards[w.key]?.let { WorkRow(it, onClick = { onOpen(it) }, badge = if (w.key in u.snapshot.wikidataKeys) tr("🌐 اكتُشف عبر Wikidata", "🌐 Found via Wikidata") else null) }
                }
            }

            item {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.loadMore(false) }, enabled = !loading) { Text(tr("تحميل المزيد", "Load more")) }
                    OutlinedButton(onClick = { vm.loadMore(true) }, enabled = !loading && !sel.isEmpty) { Text(tr("المزيد المطابق", "More matching")) }
                    OutlinedButton(onClick = { vm.enrichShown() }, enabled = !enriching) { Text(tr("إكمال البيانات", "Complete data")) }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    title: String,
    resultCount: Int,
    axis: String,
    view: AxisView,
    taxonomy: Taxonomy,
    arabic: Boolean,
    excludeMode: Boolean,
    onMode: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onApplyMany: (Set<String>) -> Unit,
    onSkip: () -> Unit,
) {
    // Show the clearest level of a hierarchy: countries (not regions), decades (not single years) when possible.
    val values: List<FacetCount> = run {
        var v = view.values
        if (axis == Axes.COUNTRY) v = v.filter { !it.value.startsWith("region.") }.ifEmpty { view.values }
        if (axis == Axes.YEAR) {
            val decades = v.filter { it.value.startsWith("decade.") }
            if (decades.size > 1) v = decades
        }
        v
    }
    var limit by remember(axis) { mutableStateOf(14) }
    var multi by remember(axis) { mutableStateOf(false) }
    var pending by remember(axis) { mutableStateOf(setOf<String>()) }

    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("$resultCount عمل متبقٍ", "$resultCount titles left"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.take(limit).forEach { fc ->
                    if (multi) {
                        val name = taxonomy.node(fc.value)?.name(arabic) ?: fc.value
                        val on = fc.value in pending
                        FilterChip(
                            selected = on,
                            onClick = { pending = if (on) pending - fc.value else pending + fc.value },
                            label = { Text(name + " · " + fc.afterInclude) },
                        )
                    } else ValueChip(fc, taxonomy, arabic, onPick)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = multi,
                    onClick = { multi = !multi; pending = emptySet() },
                    label = { Text(tr("اختيار متعدد", "Multi-select")) },
                )
                if (multi) {
                    Button(
                        onClick = { onApplyMany(pending); pending = emptySet() },
                        enabled = pending.isNotEmpty(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text(tr("التالي (${pending.size})", "Next (${pending.size})")) }
                }
            }
            if (values.size > limit) TextButton(onClick = { limit += 14 }) { Text(tr("عرض المزيد", "Show more")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = excludeMode, onCheckedChange = onMode)
                Text(
                    if (excludeMode) tr("استبعاد: اضغط ما لا تريده", "Exclude: tap what you don't want")
                    else tr("استبعاد بدل الاختيار", "Exclude instead of choose"),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                TextButton(onClick = onSkip) { Text(tr("تخطَّ السؤال", "Skip")) }
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
        label = { Text((if (fc.excluded) "⊘ " else "") + name + " · " + count) },
    )
}

@Composable
private fun ConditionChip(cond: Condition, taxonomy: Taxonomy, arabic: Boolean, onRemove: () -> Unit) {
    val name = taxonomy.node(cond.value)?.name(arabic) ?: cond.value
    AssistChip(
        onClick = onRemove,
        label = {
            Text(
                (if (cond.excluded) "⊘ " else "") + name,
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
    possible: Int,
    loaded: Int,
) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (outcome) {
                is Outcome.PoolEmpty -> Text(tr("لم تُحمَّل أي بيانات بعد. هذا لا يعني عدم وجود نتائج.", "No data loaded yet. This does not mean there are no results."))
                is Outcome.NoResults -> {
                    Text(tr("لا توجد نتائج مؤكدة.", "No confirmed results."), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (outcome.poolComplete) tr("لا يوجد عمل مطابق ضمن كل ما حُمّل ($loaded عمل). احذف أحد الشروط من الأعلى.", "Nothing matches in all loaded data ($loaded titles). Remove a condition above.")
                        else tr("لا نتيجة ضمن الأعمال المحمّلة ($loaded). اضغط «المزيد المطابق» قبل الحكم بعدم وجودها.", "No match among the $loaded loaded titles. Tap \"More matching\" before concluding none exist."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is Outcome.OnlyPossible -> {
                    Text(tr("لا نتيجة مؤكدة، لكن ${outcome.possibleCount} عمل قد يطابق.", "No confirmed result, but ${outcome.possibleCount} titles might match."), style = MaterialTheme.typography.titleSmall)
                    Text(tr("بياناتها ناقصة. اضغط «إكمال البيانات».", "Their data is incomplete. Tap \"Complete data\"."), style = MaterialTheme.typography.bodySmall)
                }
                is Outcome.Single -> {
                    val card = cards[outcome.work.key]
                    Text(tr("وصلت إلى العمل المناسب 🎯", "You found your title 🎯"), style = MaterialTheme.typography.titleMedium)
                    if (card != null) WorkRow(card, onClick = { onOpen(card) })
                    Text(tr("لماذا يناسبك:", "Why it matches:"), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        outcome.reasons.forEach { r ->
                            val n = taxonomy.node(r.value)?.name(arabic) ?: r.value
                            AssistChip(onClick = {}, label = { Text((if (r.excluded) "⊘ " else "") + n) })
                        }
                    }
                    if (card != null) Button(onClick = { onOpen(card) }) { Text(tr("افتح التفاصيل", "Open details")) }
                    if (outcome.otherPossible > 0) {
                        Text(tr("يوجد ${outcome.otherPossible} آخر محتمل ببيانات ناقصة.", "${outcome.otherPossible} others are possible with missing data."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                is Outcome.Indistinguishable -> {
                    Text(tr("تعذّر التمييز بين ${outcome.works.size} أعمال بالمعلومات المتوفرة.", "Cannot tell ${outcome.works.size} titles apart with the available data."), style = MaterialTheme.typography.titleSmall)
                    Text(tr("هذه كلها أدناه. اضغط «إكمال البيانات» لعلّ معلومات جديدة تفرّق بينها.", "All listed below. Tap \"Complete data\" - new info may tell them apart."), style = MaterialTheme.typography.bodySmall)
                }
                is Outcome.Many -> {}
            }
        }
    }
}
