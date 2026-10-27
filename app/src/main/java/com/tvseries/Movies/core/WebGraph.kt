package com.tvseries.Movies.core

/** Why one title is linked to another (every link comes from real TMDB data). */
enum class LinkKind { ACTOR, DIRECTOR, WORLD, GENRE, SIMILAR }

data class WebCand(val key: String, val kind: LinkKind, val label: String)

object WebGraph {
    /**
     * Chooses the neighbours shown around a title: balanced across link kinds (a lead actor, the director,
     * a story world, a genre, TMDB suggestions), never repeating a title and never going back to [exclude].
     */
    fun pick(cands: List<WebCand>, exclude: Set<String>, limit: Int = 8, perKind: Int = 2): List<WebCand> {
        val seen = HashSet<String>(exclude)
        val out = ArrayList<WebCand>()
        val byKind = LinkKind.values().associateWith { k -> cands.filter { it.kind == k } }
        for (round in 0 until perKind) {
            for (k in LinkKind.values()) {
                if (out.size >= limit) return out
                val c = byKind.getValue(k).firstOrNull { it.key !in seen }
                if (c != null) {
                    seen += c.key
                    out += c
                }
            }
        }
        for (c in cands) {
            if (out.size >= limit) break
            if (c.key !in seen) {
                seen += c.key
                out += c
            }
        }
        return out
    }

    /** A discovery nobody asked for: no genre in common with where the journey started. */
    fun unexpected(start: Set<Int>, node: Set<Int>): Boolean =
        start.isNotEmpty() && node.isNotEmpty() && start.intersect(node).isEmpty()
}
