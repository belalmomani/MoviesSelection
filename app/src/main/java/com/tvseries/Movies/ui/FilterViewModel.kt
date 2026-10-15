package com.tvseries.Movies.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.AxisSuggestion
import com.tvseries.Movies.core.AxisView
import com.tvseries.Movies.core.Condition
import com.tvseries.Movies.core.FilterEngine
import com.tvseries.Movies.core.FilterResult
import com.tvseries.Movies.core.FilterState
import com.tvseries.Movies.core.Outcome
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.data.PoolSnapshot
import com.tvseries.Movies.data.ServerRefine
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class FilterUi(
    val snapshot: PoolSnapshot,
    val result: FilterResult,
    val outcome: Outcome,
    val axisViews: Map<String, AxisView>,
    val suggestions: List<AxisSuggestion>,
    val poolComplete: Boolean,
)

class FilterViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val pool = WorkPool()
    private var snapshot: PoolSnapshot = pool.snapshot()

    private val _state = MutableStateFlow(FilterState(c.prefs.loadSelection()))
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private val _ui = MutableStateFlow<FilterUi?>(null)
    val ui: StateFlow<FilterUi?> = _ui.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _enriching = MutableStateFlow(false)
    val enriching: StateFlow<Boolean> = _enriching.asStateFlow()

    private val _error = MutableStateFlow<TmdbError?>(null)
    val error: StateFlow<TmdbError?> = _error.asStateFlow()

    private val _excludeMode = MutableStateFlow(false)
    val excludeMode: StateFlow<Boolean> = _excludeMode.asStateFlow()

    private val _skipped = MutableStateFlow<Set<String>>(emptySet())
    val skipped: StateFlow<Set<String>> = _skipped.asStateFlow()

    /** Skip the current question; the wizard moves to the next best axis. */
    fun skip(axis: String) {
        _skipped.value = _skipped.value + axis
    }

    private var recomputeJob: Job? = null
    private val autoRefined = HashSet<String>()

    init {
        recompute()
        loadMore(refine = false)
    }

    fun setExcludeMode(v: Boolean) {
        _excludeMode.value = v
    }

    fun toggle(axis: String, value: String) {
        val sel = _state.value.selection
        applySelection(if (_excludeMode.value) sel.toggleExclude(axis, value) else sel.toggleInclude(axis, value))
    }

    fun toggleAvoid(opt: AvoidOption) = applySelection(opt.toggle(_state.value.selection))

    /** A random confirmed result ("surprise me"). */
    fun pickRandom(): com.tvseries.Movies.data.WorkCard? {
        val u = _ui.value ?: return null
        val w = u.result.matched.randomOrNull() ?: return null
        return u.snapshot.cards[w.key]
    }

    fun removeCondition(cond: Condition) = applySelection(_state.value.selection.remove(cond))

    fun clearAll() {
        _state.value = _state.value.clear()
        persistAndRefresh()
    }

    fun back() {
        _state.value = _state.value.back()
        persistAndRefresh()
    }

    /** Called when the screen is shown: picks up a preset chosen on the home screen. */
    fun consumePreset() {
        val preset = c.presetSelection ?: return
        c.presetSelection = null
        _state.value = FilterState().apply(preset)
        persistAndRefresh()
        loadMore(refine = true)
    }

    private fun applySelection(next: Selection) {
        _state.value = _state.value.apply(next)
        persistAndRefresh()
    }

    private fun persistAndRefresh() {
        _skipped.value = emptySet()
        c.prefs.saveSelection(_state.value.selection)
        recompute()
        val base = c.repo.baseQuery().signature()
        if (mediaTypes(_state.value.selection).any { pool.stream(it, base).nextPage == 1 }) loadMore(refine = false)
    }

    /** Which TMDB endpoints are needed for the current type selection. */
    private fun mediaTypes(sel: Selection): List<String> {
        val f = sel.include[Axes.FORMAT].orEmpty()
        val movie = f.any { it == "format.movie" || it == "format.tv_movie" || it == "format.short" }
        val tv = f.any { it == "format.tv" || it == "format.miniseries" }
        return when {
            movie && !tv -> listOf("movie")
            tv && !movie -> listOf("tv")
            else -> listOf("movie", "tv")
        }
    }

    private fun isMappable(sel: Selection): Boolean =
        listOf(Axes.GENRE, Axes.LANGUAGE, Axes.COUNTRY, Axes.YEAR, Axes.RATING).any { sel.include[it]?.isNotEmpty() == true }

    /** Loads more titles from TMDB. [refine] = ask TMDB for titles matching the server-filterable conditions. */
    fun loadMore(refine: Boolean) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val sel = _state.value.selection
                val base = c.repo.baseQuery()
                for (media in mediaTypes(sel)) {
                    val q = if (refine) {
                        ServerRefine.refine(sel, snapshot.taxonomy, media, base) ?: continue
                    } else base
                    val s = pool.stream(media, q.signature())
                    var pages = 0
                    while (pages < PAGES_PER_LOAD && s.hasMore) {
                        val page = c.repo.discover(media, s.nextPage, q)
                        pool.addListItems(media, page.results)
                        s.nextPage += 1
                        s.totalPages = page.totalPages
                        pages++
                    }
                }
            } catch (e: TmdbError) {
                _error.value = e
            } finally {
                _loading.value = false
                snapshot = pool.snapshot()
                recompute()
            }
        }
    }

    /** Fetches full details (country, runtime, seasons, status, keywords, cast...) for up to 20 shown works. */
    fun enrichShown(limit: Int = ENRICH_BATCH) {
        val u = _ui.value ?: return
        if (_enriching.value) return
        // Works with missing data first: they are the ones that may flip to confirmed / excluded.
        val keys = (u.result.possible + u.result.matched).map { it.key }.filter { !pool.isEnriched(it) }.take(limit)
        if (keys.isEmpty()) return
        viewModelScope.launch {
            _enriching.value = true
            _error.value = null
            try {
                val gate = Semaphore(4)
                val fetched = coroutineScope {
                    keys.map { k ->
                        async {
                            gate.withPermit {
                                val media = k.substringBefore(":")
                                val id = k.substringAfter(":").toInt()
                                k to runCatching { c.repo.details(media, id) }
                            }
                        }
                    }.awaitAll()
                }
                var firstError: TmdbError? = null
                for ((k, r) in fetched) {
                    val d = r.getOrNull()
                    if (d != null) pool.addDetails(k.substringBefore(":"), d)
                    else if (firstError == null) firstError = r.exceptionOrNull() as? TmdbError
                }
                _error.value = firstError
            } finally {
                _enriching.value = false
                snapshot = pool.snapshot()
                recompute()
            }
        }
    }

    private fun recompute() {
        recomputeJob?.cancel()
        val snap = snapshot
        val sel = _state.value.selection
        val base = c.repo.baseQuery().signature()
        val complete = mediaTypes(sel).all { !pool.stream(it, base).hasMore }
        recomputeJob = viewModelScope.launch {
            val built = withContext(Dispatchers.Default) {
                val engine = FilterEngine(snap.works, snap.taxonomy, complete)
                val result = engine.evaluate(sel)
                val views = snap.taxonomy.axes
                    .associate { it.id to engine.axisView(sel, it.id, result) }
                    .filterValues { it.values.isNotEmpty() }
                FilterUi(snap, result, engine.outcome(sel, result), views, engine.suggest(sel, result), complete)
            }
            _ui.value = built
            maybeAutoRefine(built.result.matched.size)
            maybeAutoEnrich()
        }
    }

    private val enrichSigs = HashSet<String>()
    private val detailAxes = setOf(
        Axes.RUNTIME, Axes.SEASONS, Axes.STATUS, Axes.ENDING, Axes.AVOID, Axes.KEYWORD, Axes.CAST, Axes.DIRECTOR, Axes.SPOKEN,
    )

    /** Conditions on axes TMDB only provides in details (runtime, keywords...) -> fetch details once per selection. */
    private fun maybeAutoEnrich() {
        val sel = _state.value.selection
        if (_enriching.value || (sel.include.keys + sel.exclude.keys).none { it in detailAxes }) return
        if (!enrichSigs.add(sel.toString())) return
        enrichShown(AUTO_ENRICH_BATCH)
    }

    /** Few matches + server-filterable conditions -> fetch matching titles once per selection. */
    private fun maybeAutoRefine(matched: Int) {
        val sel = _state.value.selection
        if (matched >= AUTO_REFINE_BELOW || !isMappable(sel) || _loading.value) return
        val sig = sel.toString()
        if (!autoRefined.add(sig)) return
        loadMore(refine = true)
    }

    companion object {
        const val PAGES_PER_LOAD = 2
        const val ENRICH_BATCH = 20
        const val AUTO_ENRICH_BATCH = 30
        const val AUTO_REFINE_BELOW = 12
    }
}
