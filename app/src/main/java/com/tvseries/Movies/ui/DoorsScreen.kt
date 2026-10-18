@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.DoorPicks
import com.tvseries.Movies.core.Doors
import com.tvseries.Movies.core.Roulette
import com.tvseries.Movies.core.Scored
import com.tvseries.Movies.core.Traits
import com.tvseries.Movies.core.UserIntent
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.RankedResult
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared by Doors / Mystery box / Roulette: ranked titles that pass every mandatory condition. */
class RankedViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val prefs = c.prefs

    private val _ui = MutableStateFlow<Load<RankedResult>>(Load.Loading)
    val ui: StateFlow<Load<RankedResult>> = _ui.asStateFlow()

    private val _doors = MutableStateFlow<DoorPicks?>(null)
    val doors: StateFlow<DoorPicks?> = _doors.asStateFlow()

    private val _mystery = MutableStateFlow<List<Scored>>(emptyList())
    val mystery: StateFlow<List<Scored>> = _mystery.asStateFlow()

    private val _revealed = MutableStateFlow<Set<String>>(emptySet())
    val revealed: StateFlow<Set<String>> = _revealed.asStateFlow()

    private val _shown = MutableStateFlow<WorkCard?>(null)
    val shown: StateFlow<WorkCard?> = _shown.asStateFlow()

    private val _winner = MutableStateFlow<WorkCard?>(null)
    val winner: StateFlow<WorkCard?> = _winner.asStateFlow()

    private val _spinning = MutableStateFlow(false)
    val spinning: StateFlow<Boolean> = _spinning.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.value = Load.Loading
            try {
                val r = c.session.ranked(c.prefs.intent.value, c.prefs.taste.value)
                _ui.value = Load.Ok(r)
                _doors.value = Doors.choose(r.ranked, r.enriched, Random.Default)
                _mystery.value = Roulette.drawMany(r.ranked, 3, Random.Default)
                _revealed.value = emptySet()
            } catch (e: TmdbError) {
                _ui.value = Load.Fail(e)
            }
        }
    }

    fun rerollDoors() {
        val r = (_ui.value as? Load.Ok)?.value ?: return
        _doors.value = Doors.choose(r.ranked, r.enriched, Random.Default)
    }

    fun rerollMystery() {
        val r = (_ui.value as? Load.Ok)?.value ?: return
        _mystery.value = Roulette.drawMany(r.ranked, 3, Random.Default)
        _revealed.value = emptySet()
    }

    fun reveal(key: String) {
        _revealed.value = _revealed.value + key
    }

    /** Spin: only qualified titles are in the draw; better-fitting ones are likelier; ~10% pure surprises. */
    fun spin() {
        val r = (_ui.value as? Load.Ok)?.value ?: return
        if (_spinning.value || r.ranked.isEmpty()) return
        viewModelScope.launch {
            _spinning.value = true
            _winner.value = null
            repeat(14) {
                _shown.value = r.cards[r.ranked.random().work.key]
                delay(110)
            }
            val w = Roulette.draw(r.ranked, Random.Default)
            _winner.value = w?.let { r.cards[it.work.key] }
            _shown.value = _winner.value
            _spinning.value = false
        }
    }
}

@Composable
private fun IntentSummary(intent: UserIntent, taxonomyNames: (String, String) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        intent.tension?.let { AssistChip(onClick = {}, label = { Text(if (it < 0) tr("هادئ", "Calm") else tr("مشدود", "Tense")) }) }
        intent.fantasy?.let { AssistChip(onClick = {}, label = { Text(if (it < 0) tr("واقعي", "Realistic") else tr("خيالي", "Fantasy")) }) }
        intent.depth?.let { AssistChip(onClick = {}, label = { Text(if (it < 0) tr("خفيف", "Light") else tr("عميق", "Deep")) }) }
        intent.selection.conditions().forEach { cond ->
            AssistChip(onClick = {}, label = { Text((if (cond.excluded) "⊘ " else "") + taxonomyNames(cond.axis, cond.value)) })
        }
    }
}

@Composable
private fun DoorCard(label: String, reason: String, card: WorkCard?, onOpen: (WorkCard) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (card == null) Text(tr("لا يوجد عمل مناسب لهذا الباب ضمن المحمّل.", "No suitable title for this door among loaded ones."), style = MaterialTheme.typography.bodySmall)
            else {
                WorkRow(card, onClick = { onOpen(card) })
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DoorsScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, onSliders: () -> Unit, onChoose: () -> Unit, vm: RankedViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    val doors by vm.doors.collectAsState()
    val intent by vm.prefs.intent.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("ثلاثة أبواب", "Three doors"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = ui) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(s.error, onRetry = { vm.load() })
                is Load.Ok -> {
                    val r = s.value
                    IntentSummary(intent) { axis, v -> r.taxonomy.node(v)?.name(isArabic()) ?: v }
                    val d = doors
                    if (d == null || d.closest == null) {
                        Text(
                            tr("لا يوجد عمل مؤكد يحقق كل شروطك (من ${r.loaded} محمّل" + (if (r.possibleCount > 0) "، و${r.possibleCount} بيانات ناقصة" else "") + "). خفّف شرطًا أو غيّر تفضيلاتك.",
                                "No confirmed title meets all your conditions (of ${r.loaded} loaded). Relax a condition or change preferences."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        fun pct(x: Scored?) = x?.let { (it.fit * 100).toInt() } ?: 0
                        DoorCard(
                            tr("🚪 الأقرب لذوقك", "🚪 Closest to your taste"),
                            tr("أعلى ترتيب بين الأعمال التي حققت كل شروطك. ملاءمة المنزلقات ≈ ${pct(d.closest)}% (تقدير).", "Top-ranked among titles that meet all your conditions. Slider fit ≈ ${pct(d.closest)}% (estimate)."),
                            d.closest?.let { r.cards[it.work.key] }, onOpen,
                        )
                        DoorCard(
                            tr("🎲 المفاجأة المحسوبة", "🎲 Calculated surprise"),
                            tr("مختلف قليلًا في مزيج الأنواع، لكنه يحقق شروطك كلها وقد يناسبك.", "Slightly different genre mix, yet it meets all your conditions and may suit you."),
                            d.surprise?.let { r.cards[it.work.key] }, onOpen,
                        )
                        DoorCard(
                            tr("🛡️ الخيار الآمن", "🛡️ Safe choice"),
                            tr("أعلى الأعمال ترتيبًا التي جُلبت تفاصيلها كاملة، فشروطك (${intent.selection.conditions().size}) متحقَّق منها بأكبر قدر من البيانات.", "Best-ranked title with full details fetched, so your ${intent.selection.conditions().size} conditions are verified with the most data."),
                            d.safe?.let { r.cards[it.work.key] }, onOpen,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.rerollDoors() }) { Text(tr("غيّر الاختيار", "Reroll")) }
                        OutlinedButton(onClick = onSliders) { Text(tr("اضبط تفضيلاتي", "Tune preferences")) }
                        OutlinedButton(onClick = onChoose) { Text(tr("اختر لي", "Choose for me")) }
                    }
                }
            }
        }
    }
}

@Composable
fun MysteryScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: RankedViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    val picks by vm.mystery.collectAsState()
    val revealed by vm.revealed.collectAsState()
    var hidePosters by remember { mutableStateOf(false) }
    val arabic = isArabic()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("🎁 الصندوق الغامض", "🎁 Mystery box"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hidePosters, onCheckedChange = { hidePosters = it })
                Text(tr("إخفاء الملصقات أيضًا", "Hide posters too"), Modifier.padding(start = 8.dp))
            }
            Text(
                tr("الأسماء مخفية؛ الأنواع والتنبيهات تبقى ظاهرة لتقرر بأمان. الشروط من «اضبط تفضيلاتي».", "Titles are hidden; genres and content hints stay visible so you can decide safely. Conditions come from \"Tune preferences\"."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = ui) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(s.error, onRetry = { vm.load() })
                is Load.Ok -> {
                    val r = s.value
                    if (picks.isEmpty()) Text(tr("لا توجد أعمال تحقق شروطك حاليًا.", "No titles meet your conditions right now."))
                    picks.forEach { sc ->
                        val card = r.cards[sc.work.key]
                        val open = sc.work.key in revealed
                        if (card != null) Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (open || !hidePosters) {
                                    PosterImage(Img.poster(card.posterPath), card.title, Modifier.width(90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                                } else {
                                    Box(Modifier.width(90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                        Text("?", style = MaterialTheme.typography.headlineLarge)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (open) card.title else tr("عمل غامض", "Mystery title"), style = MaterialTheme.typography.titleSmall)
                                    Text(workGenreNames(sc.work, r.taxonomy, arabic), style = MaterialTheme.typography.bodyMedium)
                                    val bits = listOfNotNull(
                                        if (card.mediaType == "movie") tr("فيلم", "Movie") else tr("مسلسل", "Series"),
                                        Traits.yearOf(sc.work)?.toString(),
                                        sc.work.facets[Axes.LANGUAGE].orEmpty().firstOrNull()?.let { r.taxonomy.node(it)?.name(arabic) },
                                        sc.work.facets[Axes.RATING].orEmpty().firstOrNull()?.let { r.taxonomy.node(it)?.name(arabic) },
                                    )
                                    Text(bits.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    contentNote(sc.work)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                                    if (open) Button(onClick = { onOpen(card) }) { Text(tr("التفاصيل", "Details")) }
                                    else OutlinedButton(onClick = { vm.reveal(sc.work.key) }) { Text(tr("اكشف العنوان", "Reveal title")) }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { vm.rerollMystery() }) { Text(tr("صندوق جديد", "New box")) }
                }
            }
        }
    }
}

@Composable
fun RouletteScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, onSliders: () -> Unit, vm: RankedViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    val shown by vm.shown.collectAsState()
    val winner by vm.winner.collectAsState()
    val spinning by vm.spinning.collectAsState()
    val intent by vm.prefs.intent.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("🎡 الروليت الذكي", "🎡 Smart roulette"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = ui) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(s.error, onRetry = { vm.load() })
                is Load.Ok -> {
                    val r = s.value
                    IntentSummary(intent) { _, v -> r.taxonomy.node(v)?.name(isArabic()) ?: v }
                    Text(
                        tr(
                            "في القرعة ${r.ranked.size} عملًا اجتازوا كل شروطك الإلزامية؛ الأعمال المخالفة لا تدخلها أبدًا. الأنسب لتفضيلاتك أكثر حظًا، ونحو 10% مفاجآت من داخل المؤهَّلين فقط.",
                            "${r.ranked.size} titles that pass every mandatory condition are in the draw; violating titles never enter. Better fits are likelier, with ~10% surprises from qualified titles only.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val card = shown
                    if (card != null) {
                        Card(Modifier.fillMaxWidth()) { WorkRow(card, onClick = { if (!spinning) onOpen(card) }) }
                    }
                    if (!spinning && winner != null) Text(tr("🎉 فاز هذا العمل!", "🎉 This title won!"), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { vm.spin() }, enabled = !spinning && r.ranked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (spinning) tr("تدور...", "Spinning...") else tr("أدر القرعة", "Spin"))
                    }
                    if (r.ranked.isEmpty()) Text(tr("لا عمل مؤكد يحقق شروطك. عدّلها من «اضبط تفضيلاتي».", "No confirmed title meets your conditions. Edit them in \"Tune preferences\"."))
                    OutlinedButton(onClick = onSliders) { Text(tr("اضبط تفضيلاتي", "Tune preferences")) }
                }
            }
        }
    }
}
