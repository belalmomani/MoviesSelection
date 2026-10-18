@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.TasteProfile
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.core.Traits
import com.tvseries.Movies.core.Work
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TasteViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val prefs = c.prefs

    private val _card = MutableStateFlow<Load<Pair<WorkCard, Work>?>>(Load.Loading)
    val card: StateFlow<Load<Pair<WorkCard, Work>?>> = _card.asStateFlow()

    fun taxonomy(): Taxonomy = c.session.snapshot().taxonomy

    /** One title at a time: mostly the best fit for what was learned, sometimes an exploratory pick. */
    fun next() {
        viewModelScope.launch {
            _card.value = Load.Loading
            try {
                val taste = c.prefs.taste.value
                val r = c.session.ranked(c.prefs.intent.value, taste, exclude = taste.seen())
                if (r.ranked.isEmpty()) {
                    _card.value = Load.Ok(null)
                    return@launch
                }
                val pickFrom = if (Random.nextDouble() < 0.2) r.ranked.take(40) else r.ranked.take(5)
                val chosen = pickFrom[Random.nextInt(pickFrom.size)]
                val card = r.cards[chosen.work.key]
                _card.value = Load.Ok(if (card == null) null else card to chosen.work)
            } catch (e: TmdbError) {
                _card.value = Load.Fail(e)
            }
        }
    }

    fun accept(card: WorkCard, work: Work) {
        if (c.prefs.watchlist.value.none { it.key == card.key }) c.prefs.toggleWatchlist(card)
        c.prefs.setTaste(c.prefs.taste.value.update(Traits.genreIds(work), card.key, true, emptySet()))
        next()
    }

    fun reject(card: WorkCard, work: Work, reasons: Set<String>) {
        c.prefs.setTaste(c.prefs.taste.value.update(Traits.genreIds(work), card.key, false, reasons))
        next()
    }

    fun resetTaste() {
        c.prefs.setTaste(TasteProfile())
        next()
    }
}

private val reasonOptions = listOf(
    Triple("slow", "بطيء", "Slow"),
    Triple("old", "قديم", "Too old"),
    Triple("violent", "عنيف", "Too violent"),
    Triple("romance", "رومانسي أكثر من اللازم", "Too romantic"),
    Triple("repeated", "قصته مكررة", "Repetitive story"),
    Triple("mood", "لا يناسب مزاجي", "Not my mood"),
)

@Composable
fun TasteScreen(onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: TasteViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.next() }
    val state by vm.card.collectAsState()
    val taste by vm.prefs.taste.collectAsState()
    var rejecting by remember { mutableStateOf(false) }
    var reasons by remember { mutableStateOf(setOf<String>()) }
    val arabic = isArabic()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("👍 اكتشف ذوقي", "👍 Discover my taste"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                is Load.Loading -> CenterLoading()
                is Load.Fail -> ErrorBox(s.error, onRetry = { vm.next() })
                is Load.Ok -> {
                    val pair = s.value
                    if (pair == null) {
                        Text(tr("لا توجد أعمال جديدة تناسب شروطك الآن.", "No new titles fit your conditions right now."))
                    } else {
                        val (card, work) = pair
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PosterImage(Img.poster(card.posterPath), card.title, Modifier.fillMaxWidth(0.55f).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                                Text(card.title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    listOfNotNull(card.year, card.rating?.let { "★ " + fmt1(it) }, workGenreNames(work, vm.taxonomy(), arabic).ifBlank { null }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { onOpen(card) }) { Text(tr("عرض التفاصيل", "View details")) }
                            }
                        }
                        Text(tr("هل ترغب في مشاهدته؟", "Would you like to watch it?"), style = MaterialTheme.typography.titleMedium)
                        if (!rejecting) {
                            Button(onClick = { vm.accept(card, work) }, modifier = Modifier.fillMaxWidth()) { Text(tr("👍 نعم (أضفه لشاهد لاحقًا)", "👍 Yes (add to watch later)")) }
                            OutlinedButton(onClick = { rejecting = true; reasons = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text(tr("👎 لا", "👎 No")) }
                        } else {
                            Text(tr("لماذا؟ (اختياري)", "Why? (optional)"), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                reasonOptions.forEach { (id, ar, en) ->
                                    FilterChip(
                                        selected = id in reasons,
                                        onClick = { reasons = if (id in reasons) reasons - id else reasons + id },
                                        label = { Text(tr(ar, en)) },
                                    )
                                }
                            }
                            Button(onClick = { vm.reject(card, work, reasons); rejecting = false }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (reasons.isEmpty()) tr("تخطَّ بلا سبب", "Skip without reason") else tr("تأكيد الرفض", "Confirm"))
                            }
                        }
                    }

                    // What has been learned so far (local only).
                    val tax = vm.taxonomy()
                    val liked = taste.genre.filter { it.value > 0.5 }.entries.sortedByDescending { it.value }.take(4)
                        .mapNotNull { tax.node("genre.${it.key}")?.name(arabic) }
                    val disliked = taste.genre.filter { it.value < -0.5 }.entries.sortedBy { it.value }.take(4)
                        .mapNotNull { tax.node("genre.${it.key}")?.name(arabic) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("ما تعلّمه التطبيق عنك (على جهازك فقط)", "What the app learned (on this device only)"), style = MaterialTheme.typography.titleSmall)
                            Text(tr("قبلتَ ${taste.accepted.size} ورفضتَ ${taste.rejected.size}.", "You accepted ${taste.accepted.size} and rejected ${taste.rejected.size}."), style = MaterialTheme.typography.bodySmall)
                            if (liked.isNotEmpty()) Text(tr("تميل إلى: ", "You lean toward: ") + liked.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (disliked.isNotEmpty()) Text(tr("تتجنب: ", "You avoid: ") + disliked.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            Text(
                                tr("تقدير بسيط من أنواع الأعمال التي اخترتَها أو رفضتَها، وليس ذكاءً سحابيًا ولا يُرسل لأي خادم.", "A simple estimate from the genres you accepted or rejected; nothing is sent to any server."),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { vm.resetTaste() }) { Text(tr("إعادة ضبط ذوقي", "Reset my taste")) }
                        }
                    }
                }
            }
        }
    }
}
