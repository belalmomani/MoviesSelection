@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.PersonDto
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PersonHit(val id: Int, val name: String, val profilePath: String?, val department: String?)
data class SearchResults(val works: List<WorkCard>, val people: List<PersonHit>)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val prefs = c.prefs

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<Load<SearchResults>?>(null)
    val results: StateFlow<Load<SearchResults>?> = _results.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { q -> run(q.trim()) }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** Records the query in history (only if the user enabled history). */
    fun commit() = prefs.addHistory(_query.value)

    private suspend fun run(q: String) {
        if (q.length < 2) {
            _results.value = null
            return
        }
        _results.value = Load.Loading
        _results.value = try {
            val items = c.repo.search(q).results
            val works = items.filter { it.mediaType == "movie" || it.mediaType == "tv" }.mapNotNull { it.toCard("movie") }
            val people = items.filter { it.mediaType == "person" }
                .map { PersonHit(it.id, it.name ?: "", it.profilePath, it.knownForDepartment) }
            Load.Ok(SearchResults(works, people))
        } catch (e: TmdbError) {
            Load.Fail(e)
        }
    }
}

@Composable
fun SearchScreen(onOpen: (WorkCard) -> Unit, onPerson: (Int) -> Unit, vm: SearchViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.prefs.history.collectAsState()
    val historyOn by vm.prefs.historyEnabled.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { vm.setQuery(it) }, singleLine = true,
            label = { Text(tr("ابحث عن فيلم أو مسلسل أو ممثل", "Search movies, series or people")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        when (val r = results) {
            null -> {
                if (historyOn && history.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("سجل البحث", "Search history"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.prefs.clearHistory() }) { Text(tr("مسح", "Clear")) }
                    }
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.forEach { h -> AssistChip(onClick = { vm.setQuery(h) }, label = { Text(h) }) }
                    }
                } else {
                    EmptyBox(
                        if (historyOn) tr("اكتب حرفين على الأقل للبحث.", "Type at least 2 characters.")
                        else tr("اكتب حرفين على الأقل للبحث. سجل البحث معطّل (يمكن تفعيله من الإعدادات).", "Type at least 2 characters. Search history is off (enable it in Settings)."),
                    )
                }
            }
            is Load.Loading -> CenterLoading()
            is Load.Fail -> ErrorBox(r.error, onRetry = { vm.setQuery(query + " "); vm.setQuery(query) })
            is Load.Ok -> {
                if (r.value.works.isEmpty() && r.value.people.isEmpty()) {
                    EmptyBox(tr("لا نتائج لهذا البحث في TMDB.", "No results on TMDB for this search."))
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (r.value.people.isNotEmpty()) {
                            item { SectionTitle(tr("أشخاص", "People")) }
                            items(r.value.people.take(8), key = { "p-" + it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { vm.commit(); onPerson(p.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PosterImage(Img.profile(p.profilePath), p.name, Modifier.size(48.dp).clip(CircleShape))
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                                        p.department?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                        if (r.value.works.isNotEmpty()) {
                            item { SectionTitle(tr("أفلام ومسلسلات", "Movies & series")) }
                            items(r.value.works, key = { "w-" + it.key }) { w -> WorkRow(w, onClick = { vm.commit(); onOpen(w) }) }
                        }
                    }
                }
            }
        }
    }
}

class PersonViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val _ui = MutableStateFlow<Load<PersonDto>>(Load.Loading)
    val ui: StateFlow<Load<PersonDto>> = _ui.asStateFlow()

    fun load(id: Int) {
        viewModelScope.launch {
            _ui.value = Load.Loading
            _ui.value = try {
                Load.Ok(c.repo.person(id))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }
}

@Composable
fun PersonScreen(id: Int, onBack: () -> Unit, onOpen: (WorkCard) -> Unit, vm: PersonViewModel = viewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        when (val st = ui) {
            is Load.Loading -> CenterLoading()
            is Load.Fail -> ErrorBox(st.error, onRetry = { vm.load(id) })
            is Load.Ok -> {
                val p = st.value
                var showBio by remember { mutableStateOf(false) }
                // Lead vs supporting is only claimed when TMDB provides the billing order.
                val cast = p.combinedCredits?.cast.orEmpty().sortedByDescending { it.popularity ?: 0.0 }
                val crew = p.combinedCredits?.crew.orEmpty().sortedByDescending { it.popularity ?: 0.0 }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PosterImage(Img.profile(p.profilePath), p.name, Modifier.size(88.dp).clip(CircleShape))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleLarge)
                                p.knownForDepartment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                listOfNotNull(p.birthday, p.placeOfBirth).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        p.biography?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it, maxLines = if (showBio) Int.MAX_VALUE else 4, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp).clickable { showBio = !showBio },
                            )
                        }
                    }
                    if (cast.isNotEmpty()) item { SectionTitle(tr("الأعمال (تمثيل)", "Acting credits")) }
                    items(cast.mapNotNull { d -> d.toCard("movie")?.let { it to d } }.distinctBy { it.first.key }, key = { "c-" + it.first.key }) { (card, d) ->
                        val label = when {
                            d.order == null -> null
                            d.order <= 4 -> tr("بطولة", "Lead")
                            else -> tr("ظهور ثانوي", "Supporting")
                        }
                        Column {
                            WorkRow(card, onClick = { onOpen(card) })
                            val extra = listOfNotNull(d.character?.takeIf { it.isNotBlank() }, label).joinToString(" · ")
                            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 80.dp))
                        }
                    }
                    if (crew.isNotEmpty()) item { SectionTitle(tr("الأعمال (فريق العمل)", "Crew credits")) }
                    items(crew.mapNotNull { d -> d.toCard("movie")?.let { it to d } }.distinctBy { it.first.key }, key = { "k-" + it.first.key }) { (card, d) ->
                        Column {
                            WorkRow(card, onClick = { onOpen(card) })
                            d.job?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 80.dp)) }
                        }
                    }
                }
            }
        }
    }
}
