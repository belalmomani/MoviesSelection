package com.tvseries.Movies.data

import kotlin.math.ln
import kotlin.math.round

/** What the filter shows next to a person: [rating] and, for Arabic users, an Arabic spelling of the name when TMDB has one. */
data class PersonInfo(val rating: Double?, val arabicName: String?)

/**
 * TMDB has no rating for a person. The number shown is the average TMDB rating of the person's best-known titles
 * (most popular first, only titles with enough votes), weighted by how many people voted. Null when there is too little data.
 */
object PersonScore {
    private const val MIN_VOTES = 300
    private const val MIN_TITLES = 3
    private const val TOP_TITLES = 15

    fun rating(items: List<ListItemDto>): Double? {
        val good = items
            .filter { (it.mediaType ?: "movie") == "movie" || it.mediaType == "tv" }
            .filter { (it.voteCount ?: 0) >= MIN_VOTES && (it.voteAverage ?: 0.0) > 0.0 }
            .distinctBy { (it.mediaType ?: "movie") + ":" + it.id }
            .sortedByDescending { it.popularity ?: 0.0 }
            .take(TOP_TITLES)
        if (good.size < MIN_TITLES) return null
        val weights = good.map { ln(1.0 + (it.voteCount ?: 0)) }
        val sum = good.indices.sumOf { weights[it] * (good[it].voteAverage ?: 0.0) }
        return round(sum / weights.sum() * 10.0) / 10.0
    }

    /** Actors: their acting credits. Directors: only the titles they directed or created. */
    fun forPerson(person: PersonDto, directing: Boolean): Double? {
        val credits = person.combinedCredits ?: return null
        return if (directing) rating(credits.crew.filter { it.job == "Director" || it.job == "Creator" }) else rating(credits.cast)
    }

    fun arabicName(alsoKnownAs: List<String>): String? =
        alsoKnownAs.firstOrNull { n -> n.isNotBlank() && n.any { it in '\u0600'..'\u06FF' } }?.trim()
}
