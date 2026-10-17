package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.Taxonomy

/** Parameters for TMDB /discover/{movie|tv}. */
data class DiscoverQuery(
    val genres: Set<Int> = emptySet(),
    val language: String? = null,
    val countries: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Int? = null,
    val maxVotes: Int? = null,
    val minVotes: Int = 100,
    val includeAdult: Boolean = false,
    val sort: String = "popularity.desc",
) {
    fun signature(): String =
        "g=${genres.sorted()};l=$language;c=${countries.sorted()};y=$yearFrom-$yearTo;r=$minRating;v=$minVotes-$maxVotes;a=$includeAdult;s=$sort"

    fun params(media: String, page: Int, uiLanguage: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["language"] = uiLanguage
        m["page"] = page.toString()
        m["sort_by"] = sort
        m["include_adult"] = includeAdult.toString()
        m["vote_count.gte"] = minVotes.toString()
        if (genres.isNotEmpty()) m["with_genres"] = genres.sorted().joinToString("|")
        language?.let { m["with_original_language"] = it }
        if (countries.isNotEmpty()) m["with_origin_country"] = countries.sorted().joinToString("|")
        minRating?.let { m["vote_average.gte"] = it.toString() }
        maxVotes?.let { m["vote_count.lte"] = it.toString() }
        val from = if (media == "movie") "primary_release_date.gte" else "first_air_date.gte"
        val to = if (media == "movie") "primary_release_date.lte" else "first_air_date.lte"
        yearFrom?.let { m[from] = "$it-01-01" }
        yearTo?.let { m[to] = "$it-12-31" }
        return m
    }
}

/**
 * Turns the part of a user selection that TMDB can filter server-side into a discover query, so
 * "load more matching" fetches works that actually match instead of more of the same popular titles.
 * Everything is still re-checked client-side by the filter engine.
 */
object ServerRefine {
    private val arabPriority = listOf("EG", "SA", "SY", "JO", "LB", "AE", "KW", "IQ", "MA", "TN", "DZ", "PS")
    private val arabSet = Regions.members["region.arab"].orEmpty().toSet()

    /** Arabic productions have far fewer TMDB votes, so the usual vote threshold would hide most of them. */
    private fun lowVotesForArabic(q: DiscoverQuery): DiscoverQuery =
        if (q.language == "ar" || q.countries.any { it in arabSet }) q.copy(minVotes = minOf(q.minVotes, 5)) else q

    /**
     * One or more discover queries for the selection. Several countries are split into one query each
     * (so each loaded title can be stamped with its country; list data has no country for movies), and
     * Arab-region selections also add an "original language = Arabic" query.
     */
    fun refineAll(sel: Selection, taxonomy: Taxonomy, media: String, base: DiscoverQuery): List<DiscoverQuery> {
        val q = lowVotesForArabic(refine(sel, taxonomy, media, base) ?: return emptyList())
        if (q.countries.size <= 1) return listOf(q)
        val ordered = if (q.countries.all { it in arabSet }) {
            arabPriority.filter { it in q.countries } + q.countries.filter { it !in arabPriority }.sorted()
        } else q.countries.sorted()
        val out = ordered.take(8).map { q.copy(countries = setOf(it)) }.toMutableList()
        if (q.language == null && q.countries.all { it in arabSet }) out += q.copy(countries = emptySet(), language = "ar")
        return out
    }

    /** Returns null when no work of [media] can possibly match the selected genres. */
    fun refine(sel: Selection, taxonomy: Taxonomy, media: String, base: DiscoverQuery): DiscoverQuery? {
        var q = base

        val genreIds = sel.include[Axes.GENRE].orEmpty().mapNotNull { it.removePrefix("genre.").toIntOrNull() }.toSet()
        if (genreIds.isNotEmpty()) {
            val valid = if (media == "movie") TaxonomySeed.movieGenreIds else TaxonomySeed.tvGenreIds
            val ok = genreIds.filter { it in valid }.toSet()
            if (ok.isEmpty()) return null
            q = q.copy(genres = ok)
        }

        val langs = sel.include[Axes.LANGUAGE].orEmpty().map { it.removePrefix("language.") }
        if (langs.size == 1) q = q.copy(language = langs[0])

        val countries = LinkedHashSet<String>()
        for (v in sel.include[Axes.COUNTRY].orEmpty()) {
            when {
                v.startsWith("country.") -> countries += v.removePrefix("country.")
                v.startsWith("region.") -> countries += Regions.members[v].orEmpty()
            }
        }
        if (countries.isNotEmpty()) q = q.copy(countries = countries)

        val years = sel.include[Axes.YEAR].orEmpty()
        if (years.size == 1) {
            val v = years.first()
            when {
                v.startsWith("year.") -> v.removePrefix("year.").toIntOrNull()?.let { q = q.copy(yearFrom = it, yearTo = it) }
                v.startsWith("decade.") -> v.removePrefix("decade.").toIntOrNull()?.let { q = q.copy(yearFrom = it, yearTo = it + 9) }
            }
        }

        val ratings = sel.include[Axes.RATING].orEmpty().mapNotNull { it.removePrefix("rating.").toIntOrNull() }
        if (ratings.isNotEmpty()) q = q.copy(minRating = ratings.min())

        return q
    }
}
