package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.core.TaxonomyNode
import com.tvseries.Movies.core.Work

data class PoolSnapshot(
    val works: List<Work>,
    val cards: Map<String, WorkCard>,
    val taxonomy: Taxonomy,
    val enrichedKeys: Set<String>,
)

class StreamState(var nextPage: Int = 1, var totalPages: Int = Int.MAX_VALUE) {
    val hasMore: Boolean get() = nextPage <= totalPages && nextPage <= 500
}

/**
 * Mutable set of titles loaded from TMDB (main thread only). Details-level data ("enriched") is never
 * overwritten by the poorer list-level data.
 */
class WorkPool {
    private val nodes = LinkedHashMap<String, TaxonomyNode>().apply { TaxonomySeed.nodes().forEach { put(it.id, it) } }
    private val raw = LinkedHashMap<String, Work>()
    private val enriched = HashSet<String>()
    private val cards = LinkedHashMap<String, WorkCard>()
    private val streams = HashMap<String, StreamState>()

    val size: Int get() = raw.size
    fun stream(media: String, signature: String): StreamState = streams.getOrPut("$media|$signature") { StreamState() }
    fun contains(key: String): Boolean = key in raw
    fun isEnriched(key: String): Boolean = key in enriched
    fun card(key: String): WorkCard? = cards[key]

    /** [stampCountry]: the query was filtered to exactly this country, so every returned title has it. */
    fun addListItems(media: String, items: List<ListItemDto>, stampCountry: String? = null): Int {
        var added = 0
        for (d in items) {
            val card = d.toCard(media) ?: continue
            if (card.key in raw) continue
            val m = WorkMapper.fromListItem(media, d)
            var work = m.work
            if (stampCountry != null && Axes.COUNTRY !in work.facets) {
                val node = NodeFactory.country(stampCountry)
                nodes.putIfAbsent(node.id, node)
                work = work.copy(facets = work.facets + (Axes.COUNTRY to setOf(node.id)))
            }
            raw[card.key] = work
            cards[card.key] = card
            for (n in m.nodes) nodes.putIfAbsent(n.id, n)
            added++
        }
        return added
    }

    fun addDetails(media: String, d: DetailsDto) {
        val card = d.toCard(media)
        val m = WorkMapper.fromDetails(media, d)
        raw[card.key] = m.work
        cards[card.key] = card
        enriched += card.key
        for (n in m.nodes) nodes.putIfAbsent(n.id, n)
    }

    fun snapshot(): PoolSnapshot {
        val taxonomy = Taxonomy(nodes.values, Axes.all)
        return PoolSnapshot(raw.values.map { it.expanded(taxonomy) }, cards.toMap(), taxonomy, enriched.toSet())
    }
}
