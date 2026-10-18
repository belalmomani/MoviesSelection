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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.FilterEngine
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.UserIntent
import com.tvseries.Movies.data.TmdbError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChooseUi {
    data class Fixed(val step: Int) : ChooseUi
    data object Loading : ChooseUi
    data class Adaptive(val axis: String, val values: List<Triple<String, String, Int>>) : ChooseUi
    data object Done : ChooseUi
    data class Failed(val error: TmdbError) : ChooseUi
}

/**
 * "Choose for me": three feeling questions set slider preferences; then up to two follow-up questions are
 * chosen from what the best-fitting titles actually differ in. The user never needs to know genre names.
 */
class ChooseViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val _ui = MutableStateFlow<ChooseUi>(ChooseUi.Fixed(0))
    val ui: StateFlow<ChooseUi> = _ui.asStateFlow()

    private var tension: Double? = null
    private var fantasy: Double? = null
    private var depth: Double? = null
    private var selection = Selection()
    private val skipped = HashSet<String>()
    private var rounds = 0

    fun restart() {
        tension = null; fantasy = null; depth = null
        selection = Selection(); skipped.clear(); rounds = 0
        _ui.value = ChooseUi.Fixed(0)
    }

    fun answer(step: Int, v: Double?) {
        when (step) {
            0 -> tension = v
            1 -> fantasy = v
            else -> depth = v
        }
        if (step < 2) _ui.value = ChooseUi.Fixed(step + 1) else next()
    }

    fun pick(axis: String, value: String) {
        selection = selection.toggleInclude(axis, value)
        rounds++
        next()
    }

    fun skip(axis: String) {
        skipped += axis
        rounds++
        next()
    }

    private fun next() {
        viewModelScope.launch {
            if (rounds >= 2) {
                finish()
                return@launch
            }
            _ui.value = ChooseUi.Loading
            try {
                val arabic = c.prefs.language.value == "ar"
                val r = c.session.ranked(UserIntent(tension, fantasy, depth, Selection()), c.prefs.taste.value)
                val engine = FilterEngine(r.ranked.take(60).map { it.work }, r.taxonomy)
                val res = engine.evaluate(selection)
                val allowed = setOf(Axes.GENRE, Axes.WORLD, Axes.COUNTRY, Axes.LANGUAGE, Axes.YEAR, Axes.FORMAT)
                val sug = engine.suggest(selection, res).firstOrNull { it.axis in allowed && it.axis !in skipped }
                if (sug == null || res.matched.size <= 3) {
                    finish()
                    return@launch
                }
                var values = engine.axisView(selection, sug.axis, res).values.filter { !it.selected }
                if (sug.axis == Axes.YEAR) values = values.filter { it.value.startsWith("decade.") }.ifEmpty { values }
                val shown = values.take(8).map { Triple(it.value, r.taxonomy.node(it.value)?.name(arabic) ?: it.value, it.afterInclude) }
                if (shown.isEmpty()) finish() else _ui.value = ChooseUi.Adaptive(sug.axis, shown)
            } catch (e: TmdbError) {
                _ui.value = ChooseUi.Failed(e)
            }
        }
    }

    private fun finish() {
        c.prefs.setIntent(UserIntent(tension, fantasy, depth, selection))
        _ui.value = ChooseUi.Done
    }
}

@Composable
private fun TwoChoices(question: String, aLabel: String, aValue: Double, bLabel: String, bValue: Double, onAnswer: (Double?) -> Unit) {
    Text(question, style = MaterialTheme.typography.headlineSmall)
    Button(onClick = { onAnswer(aValue) }, modifier = Modifier.fillMaxWidth()) { Text(aLabel) }
    Button(onClick = { onAnswer(bValue) }, modifier = Modifier.fillMaxWidth()) { Text(bLabel) }
    TextButton(onClick = { onAnswer(null) }) { Text(tr("لا فرق عندي", "Doesn't matter")) }
}

@Composable
fun ChooseScreen(onBack: () -> Unit, onDone: () -> Unit, vm: ChooseViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui) { if (ui is ChooseUi.Done) onDone() }
    Column(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        BackHeader(tr("اختر لي", "Choose for me"), onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = ui) {
                is ChooseUi.Fixed -> {
                    Text(tr("سؤال ${s.step + 1} من 3", "Question ${s.step + 1} of 3"), style = MaterialTheme.typography.labelMedium)
                    when (s.step) {
                        0 -> TwoChoices(
                            tr("هل تريد شيئًا هادئًا أم مشدود الأعصاب؟", "Something calm or nerve-racking?"),
                            tr("هادئًا 😌", "Calm 😌"), -0.8, tr("مشدود الأعصاب 😬", "Nerve-racking 😬"), 0.8,
                        ) { vm.answer(0, it) }
                        1 -> TwoChoices(
                            tr("قصة واقعية أم عالمًا خياليًا؟", "A realistic story or a fantasy world?"),
                            tr("قصة واقعية 🌍", "Realistic 🌍"), -0.8, tr("عالم خيالي 🪄", "Fantasy world 🪄"), 0.8,
                        ) { vm.answer(1, it) }
                        else -> TwoChoices(
                            tr("تنتهي المشاهدة بشعور مريح أم بتفكير طويل؟", "End with a comfy feeling or long thinking?"),
                            tr("شعور مريح 🛋️", "Comfy feeling 🛋️"), -0.7, tr("تفكير طويل 🤔", "Long thinking 🤔"), 0.7,
                        ) { vm.answer(2, it) }
                    }
                }
                is ChooseUi.Loading -> CenterLoading()
                is ChooseUi.Adaptive -> {
                    Text(questionTitle(s.axis, s.axis), style = MaterialTheme.typography.headlineSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.values.forEach { (id, name, count) ->
                            FilterChip(selected = false, onClick = { vm.pick(s.axis, id) }, label = { Text("$name · $count") })
                        }
                    }
                    TextButton(onClick = { vm.skip(s.axis) }) { Text(tr("لا يهم، التالي", "Doesn't matter, next")) }
                }
                is ChooseUi.Failed -> ErrorBox(s.error, onRetry = { vm.restart() })
                is ChooseUi.Done -> CenterLoading()
            }
            if (ui !is ChooseUi.Fixed) OutlinedButton(onClick = { vm.restart() }) { Text(tr("ابدأ من جديد", "Restart")) }
        }
    }
}
