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
    val minVotes: Int = 100,
    val includeAdult: Boolean = false,
    val sort: String = "popularity.desc",
) {
    fun signature(): String =
        "g=${genres.sorted()};l=$language;c=${countries.sorted()};y=$yearFrom-$yearTo;r=$minRating;v=$minVotes;a=$includeAdult;s=$sort"

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
