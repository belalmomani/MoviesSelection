package com.tvseries.Movies.core

/** Group watching: a title is suggested only if it fits BOTH people (intersection, not union). */
object PartyMatcher {
    data class Result(val confirmed: List<Work>, val possible: List<Work>)

    /** confirmed = verified for both; possible = nothing contradicts either, but some data is missing. */
    fun intersect(works: List<Work>, a: Selection, b: Selection): Result {
        val confirmed = ArrayList<Work>()
        val possible = ArrayList<Work>()
        for (w in works) {
            val ma = matchWork(w, a)
            val mb = matchWork(w, b)
            if (ma == Match.NO || mb == Match.NO) continue
            if (ma == Match.YES && mb == Match.YES) confirmed += w else possible += w
        }
        return Result(confirmed, possible)
    }
}

fun <T> intersectAll(sets: List<Set<T>>): Set<T> =
    if (sets.isEmpty()) emptySet() else sets.reduce { acc, s -> acc intersect s }

/**
 * "Hidden gems": good rating AND a meaningful number of votes, but not among the most voted titles.
 * Ranking uses a Bayesian-weighted rating so a 9.0 with 20 votes cannot beat a 7.8 with 8,000 votes.
 */
object GemFinder {
    fun score(avg: Double, votes: Int, priorVotes: Int = 1000, priorMean: Double = 6.5): Double {
        val v = votes.toDouble()
        return v / (v + priorVotes) * avg + priorVotes / (v + priorVotes) * priorMean
    }

    fun isGem(avg: Double, votes: Int, minAvg: Double = 7.2, minVotes: Int = 1500, maxVotes: Int = 20000): Boolean =
        avg >= minAvg && votes in minVotes..maxVotes
}
