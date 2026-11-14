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
import com.tvseries.Movies.data.ContentType
import com.tvseries.Movies.data.PoolSnapshot
import com.tvseries.Movies.data.ServerRefine
import com.tvseries.Movies.data.TaxonomySeed
import com.tvseries.Movies.data.Spheres
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkPool
import com.tvseries.Movies.data.Regions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/**
 * How many titles TMDB itself reports for the conditions that were sent to it (from total_results).
 * [exact] = a single query per media (no overlap between queries, no Wikidata additions);
 * [partial] = some conditions are only checked on the device, so the real number is lower;
 * [capped] = TMDB stops reporting at 10,000.
 */
data class ServerCount(val total: Int, val exact: Boolean, val partial: Boolean, val capped: Boolean)

class FilterViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    private val pool = WorkPool()
    val contentType = c.prefs.contentType
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

    private val _serverCount = MutableStateFlow<ServerCount?>(null)
    val serverCount: StateFlow<ServerCount?> = _serverCount.asStateFlow()

    /** total_results reported by TMDB per discover query (key = media + query signature). */
    private val serverTotals = HashMap<String, Int>()
    private var pendingRefine = false

    /** TMDB totals for "current selection + this one value" of the question on screen (value id -> titles). */
    private val _probe = MutableStateFlow<Map<String, Int>>(emptyMap())
    val probe: StateFlow<Map<String, Int>> = _probe.asStateFlow()
    private var probeJob: Job? = null
    private var probeKey = ""

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _excludeMode = MutableStateFlow(false)
    val excludeMode: StateFlow<Boolean> = _excludeMode.asStateFlow()

    val savedFilters = c.prefs.savedFilters

    fun saveCurrent(name: String) = c.prefs.saveFilter(name, _state.value.selection)
    fun deleteSaved(name: String) = c.prefs.deleteFilter(name)

    /** Replaces the current selection (saved filter / preset) and loads matching titles. */
    fun loadSelection(sel: Selection) {
        _state.value = FilterState().apply(sel)
        persistAndRefresh() // starts the TMDB-side load itself when the selection has server-filterable conditions
    }

    /** The global all / movies / series choice changed. */
    fun onTypeChanged() = persistAndRefresh()

    /** Re-evaluates (e.g. after marking a title as watched elsewhere). */
    fun refresh() = recompute()

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

    /** Applies several values of one axis at once (multi-select question). */
    fun applyMany(axis: String, values: Set<String>) {
        var s = _state.value.selection
        for (v in values) {
            s = if (_excludeMode.value) { if (s.isExcluded(axis, v)) s else s.toggleExclude(axis, v) }
            else { if (s.isIncluded(axis, v)) s else s.toggleInclude(axis, v) }
        }
        applySelection(s)
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
        loadSelection(preset)
    }

    private fun applySelection(next: Selection) {
        _state.value = _state.value.apply(next)
        persistAndRefresh()
    }

    private fun persistAndRefresh() {
        _skipped.value = emptySet()
        _notice.value = null
        c.prefs.saveSelection(_state.value.selection)
        recompute()
        _serverCount.value = null
        probeJob?.cancel()
        probeKey = ""
        _probe.value = emptyMap()
        val sel = _state.value.selection
        // Ask TMDB for titles that really match the server-filterable conditions as soon as the selection changes.
        if (ServerRefine.canRefine(sel) && autoRefined.add(sel.toString())) {
            loadMore(refine = true)
            return
        }
        publishServerCount(sel) // already refined before (e.g. the user stepped back): totals are cached
        val base = c.repo.baseQuery().signature()
        if (mediaTypes(sel).any { pool.stream(it, base).nextPage == 1 }) loadMore(refine = false)
    }

    /** Which TMDB endpoints are needed for the current type selection. */
    private fun mediaTypes(sel: Selection): List<String> {
        val f = sel.include[Axes.FORMAT].orEmpty()
        val movie = f.any { it == "format.movie" || it == "format.tv_movie" || it == "format.short" }
        val tv = f.any { it == "format.tv" || it == "format.miniseries" }
        val base = when {
            movie && !tv -> listOf("movie")
            tv && !movie -> listOf("tv")
            else -> listOf("movie", "tv")
        }
        val type = c.prefs.contentType.value
        return if (type == ContentType.ALL) base else base.filter { it == type }.ifEmpty { listOf(type) }
    }

    private fun isMappable(sel: Selection): Boolean = ServerRefine.canRefine(sel)

    /** Loads more titles from TMDB. [refine] = ask TMDB for titles matching the server-filterable conditions. */
    fun loadMore(refine: Boolean) {
        if (_loading.value) {
            if (refine) pendingRefine = true // selection changed while loading: refine again when this load ends
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val startSel = _state.value.selection
            try {
                val sel = _state.value.selection
                val base = c.repo.baseQuery()
                for (media in mediaTypes(sel)) {
                    val queries = if (refine) ServerRefine.refineAll(sel, snapshot.taxonomy, media, base) else listOf(base)
                    val pagesEach = if (queries.size > 1) 1 else PAGES_PER_LOAD
                    if (refine) {
                        val iso = arabCountries(sel)
                        if (iso.isNotEmpty()) {
                            try {
                                loadWikidata(media, iso)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _notice.value = "wikidata"
                            }
                        }
                    }
                    for (q in queries) {
                        val s = pool.stream(media, q.signature())
                        var pages = 0
                        while (pages < pagesEach && s.hasMore) {
                            val page = c.repo.discover(media, s.nextPage, q)
                            pool.addListItems(media, page.results, q.countries.singleOrNull())
                            if (refine) serverTotals["$media|${q.signature()}"] = page.totalResults
                            s.nextPage += 1
                            s.totalPages = page.totalPages
                            pages++
                        }
                    }
                }
            } catch (e: TmdbError) {
                _error.value = e
            } finally {
                _loading.value = false
                snapshot = pool.snapshot()
                recompute()
                if (refine && _state.value.selection == startSel) publishServerCount(startSel)
                if (pendingRefine) {
                    pendingRefine = false
                    loadMore(refine = true)
                }
            }
        }
    }

    /**
     * Values the TMDB side can answer for [axis] that the loaded sample may not contain at all
     * (a genre / decade / sphere nobody loaded yet). Only values that exist in the taxonomy and are not
     * already shown, selected or excluded.
     */
    fun extraCandidates(axis: String, shown: Set<String>): List<String> {
        val sel = _state.value.selection
        val all: List<String> = when (axis) {
            Axes.GENRE -> (TaxonomySeed.movieGenreIds + TaxonomySeed.tvGenreIds).sorted().map { "genre.$it" }
            Axes.SPHERE -> listOf(Spheres.ARAB, Spheres.EAST_ASIA, Spheres.WESTERN, Spheres.OTHER, Spheres.ANIMATION)
            Axes.YEAR -> (1930..2020 step 10).reversed().map { "decade.$it" }
            Axes.RATING -> (5..9).map { "rating.$it" }
            else -> emptyList()
        }
        val tax = snapshot.taxonomy
        return all.filter { it !in shown && tax.contains(it) && !sel.isIncluded(axis, it) && !sel.isExcluded(axis, it) }
    }

    /**
     * Asks TMDB how many titles each candidate value of the current question would leave (one light request
     * per value, 3 at a time, cached). Results arrive one by one in [probe]. Nothing is added to the loaded pool.
     */
    fun probe(axis: String, values: List<String>) {
        val sel = _state.value.selection
        if (_excludeMode.value || axis !in PROBE_AXES || values.isEmpty()) {
            probeJob?.cancel()
            probeKey = ""
            _probe.value = emptyMap()
            return
        }
        val key = sel.toString() + "|" + axis + "|" + values.joinToString(",")
        if (key == probeKey) return
        probeKey = key
        probeJob?.cancel()
        _probe.value = emptyMap()
        probeJob = viewModelScope.launch {
            val gate = Semaphore(PROBE_PARALLEL)
            coroutineScope {
                for (v in values) {
                    if (sel.isIncluded(axis, v)) continue
                    launch {
                        gate.withPermit {
                            val next = sel.toggleInclude(axis, v)
                            val total = totalFor(next) ?: return@withPermit
                            // Arab titles also come from Wikidata, which TMDB's count does not include: a 0 proves nothing there.
                            if (total == 0 && arabCountries(next).isNotEmpty()) return@withPermit
                            _probe.update { it + (v to total) }
                        }
                    }
                }
            }
        }
    }

    /** TMDB's count for [sel] (sum over the discover queries it needs); null if a request failed. */
    private suspend fun totalFor(sel: Selection): Int? {
        val base = c.repo.baseQuery()
        var total = 0
        for (media in mediaTypes(sel)) {
            for (q in ServerRefine.refineAll(sel, snapshot.taxonomy, media, base)) {
                val k = "$media|${q.signature()}"
                val t = serverTotals[k] ?: try {
                    c.repo.discover(media, 1, q).totalResults.also { serverTotals[k] = it }
                } catch (e: TmdbError) {
                    return null
                }
                total += t
            }
        }
        return total
    }

    /** Builds the TMDB count for [sel] from the totals recorded for each of its discover queries (null while unknown). */
    private fun publishServerCount(sel: Selection) {
        if (!ServerRefine.canRefine(sel)) {
            _serverCount.value = null
            return
        }
        val base = c.repo.baseQuery()
        var total = 0
        var singleQueryPerMedia = true
        var capped = false
        var partial = false
        val medias = mediaTypes(sel)
        for (media in medias) {
            val queries = ServerRefine.refineAll(sel, snapshot.taxonomy, media, base)
            if (queries.size > 1) singleQueryPerMedia = false
            for (q in queries) {
                val t = serverTotals["$media|${q.signature()}"] ?: return // a query has not answered yet
                if (t >= TMDB_TOTAL_CAP) capped = true
                total += t
            }
            if (ServerRefine.hasClientOnlyConditions(sel, media)) partial = true
        }
        val exact = singleQueryPerMedia && arabCountries(sel).isEmpty()
        _serverCount.value = ServerCount(total, exact, partial, capped)
    }

    private fun arabCountries(sel: Selection): Set<String> {
        val out = LinkedHashSet<String>()
        for (v in sel.include[Axes.COUNTRY].orEmpty()) {
            when {
                v.startsWith("country.") -> out += v.removePrefix("country.")
                v.startsWith("region.") -> out += Regions.members[v].orEmpty()
            }
        }
        if ("sphere.arab" in sel.include[Axes.SPHERE].orEmpty()) out += Regions.arab
        return out.filter { it in Regions.arab }.toSet()
    }

    /** Discovery of Arab productions through Wikidata, then full details from TMDB (no invented data). */
    private suspend fun loadWikidata(media: String, iso: Set<String>) {
        val s = pool.stream("wd-$media", "wd|" + iso.sorted())
        if (!s.hasMore) return
        val ids = c.wikidata.tmdbIds(media, iso, (s.nextPage - 1) * WD_PAGE, WD_PAGE)
        s.nextPage += 1
        if (ids.size < WD_PAGE) s.totalPages = s.nextPage - 1
        val fresh = ids.filter { !pool.contains("$media:$it") }
        val gate = Semaphore(4)
        val fetched = coroutineScope {
            fresh.map { id -> async { gate.withPermit { runCatching { c.repo.details(media, id) }.getOrNull() } } }.awaitAll()
        }
        for (d in fetched) if (d != null) pool.addDetails(media, d, fromWikidata = true)
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
        val type = c.prefs.contentType.value
        val hide = c.prefs.hideWatched.value
        val watchedKeys = c.prefs.watched.value
        val complete = mediaTypes(sel).all { !pool.stream(it, base).hasMore }
        recomputeJob = viewModelScope.launch {
            val built = withContext(Dispatchers.Default) {
                val visible = snap.works.filter { ContentType.allows(type, it.key) && !(hide && it.key in watchedKeys) }
                val engine = FilterEngine(visible, snap.taxonomy, complete)
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
        const val WD_PAGE = 20
        const val ENRICH_BATCH = 20
        const val AUTO_ENRICH_BATCH = 30
        const val AUTO_REFINE_BELOW = 12
        const val TMDB_TOTAL_CAP = 10000
        const val PROBE_PARALLEL = 3
        val PROBE_AXES = setOf(Axes.GENRE, Axes.SPHERE, Axes.YEAR, Axes.RATING, Axes.COUNTRY, Axes.LANGUAGE)
    }
}
