package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Match
import com.tvseries.Movies.core.Ranker
import com.tvseries.Movies.core.Scored
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.core.TasteProfile
import com.tvseries.Movies.core.UserIntent
import com.tvseries.Movies.core.Work
import com.tvseries.Movies.core.matchWork
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class RankedResult(
    val ranked: List<Scored>,
    val cards: Map<String, WorkCard>,
    val enriched: Set<String>,
    val possibleCount: Int,
    val loaded: Int,
    val taxonomy: Taxonomy,
)

/**
 * Shared pool of titles for the "choose for me" features (one pool for the whole app session).
 * Used from the main thread only (view-model scopes); the mutex prevents duplicate loads.
 */
class DiscoverSession(private val repo: TmdbRepository, private val prefs: Prefs) {
    private val pool = WorkPool()
    private val mutex = Mutex()
    private var cache: PoolSnapshot = pool.snapshot()

    fun snapshot(): PoolSnapshot = cache

    /** Works not yet watched (when the user keeps "hide watched" on). */
    fun visibleWorks(): List<Work> {
        val watched = prefs.watched.value
        val type = prefs.contentType.value
        return cache.works.filter { w ->
            ContentType.allows(type, w.key) && !(prefs.hideWatched.value && w.key in watched)
        }
    }

    suspend fun ensureLoaded(more: Boolean = false) {
        mutex.withLock {
            if (pool.size > 0 && !more) return
            val base = repo.baseQuery()
            for (media in listOf("movie", "tv")) {
                val s = pool.stream(media, "session|" + base.signature())
                var pages = 0
                while (pages < PAGES && s.hasMore) {
                    val page = repo.discover(media, s.nextPage, base)
                    pool.addListItems(media, page.results)
                    s.nextPage += 1
                    s.totalPages = page.totalPages
                    pages++
                }
            }
            cache = pool.snapshot()
        }
    }

    suspend fun enrich(keys: List<String>) {
        if (keys.isEmpty()) return
        mutex.withLock {
            val gate = Semaphore(4)
            val fetched = coroutineScope {
                keys.map { k ->
                    async {
                        gate.withPermit {
                            k to runCatching { repo.details(k.substringBefore(":"), k.substringAfter(":").toInt()) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            for ((k, d) in fetched) if (d != null) pool.addDetails(k.substringBefore(":"), d)
            cache = pool.snapshot()
        }
    }

    /**
     * Ranks titles for the intent. Mandatory conditions (intent.selection) must be VERIFIED: titles with
     * missing data are fetched in detail first (up to 30), and anything still unknown is left out.
     */
    suspend fun ranked(intent: UserIntent, taste: TasteProfile, exclude: Set<String> = emptySet()): RankedResult {
        ensureLoaded()
        var works = visibleWorks().filter { it.key !in exclude }
        val unknown = works.filter { matchWork(it, intent.selection) == Match.UNKNOWN }.map { it.key }.filter { !pool.isEnriched(it) }
        if (unknown.isNotEmpty()) {
            enrich(unknown.take(30))
            works = visibleWorks().filter { it.key !in exclude }
        }
        val possible = works.count { matchWork(it, intent.selection) == Match.UNKNOWN }
        val snap = cache
        return RankedResult(Ranker.rank(works, intent, taste), snap.cards, snap.enrichedKeys, possible, works.size, snap.taxonomy)
    }

    companion object {
        const val PAGES = 5
    }
}
