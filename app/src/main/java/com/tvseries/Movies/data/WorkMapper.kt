package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.TaxonomyNode
import com.tvseries.Movies.core.Work
import kotlin.math.floor

data class Mapped(val work: Work, val nodes: List<TaxonomyNode>)

/**
 * Converts TMDB data to engine facets. An axis is only added when TMDB actually provided the data:
 * list responses give genres/language/date/rating (+ origin country for TV); details add the rest.
 * Missing data stays "unknown" (axis absent) and is never treated as "does not match".
 */
object WorkMapper {
    private val THINK = setOf(9648, 878, 10765, 99, 36)
    private val NERVES = setOf(53, 27, 80, 28, 10752, 10759)
    private val RELAX = setOf(10751, 10749, 16, 10402, 10762)
    private val HIGH_TENSION = setOf(53, 27, 80, 10752, 10768, 28, 10759)
    private val LOW_TENSION = setOf(10751, 35, 10749, 16, 10402, 10762, 10767)

    private class Facts(
        val media: String,
        val genreIds: List<Int>?,
        val date: String?,
        val language: String?,
        val countries: List<Pair<String, String?>>?,
        val voteAverage: Double?,
        val voteCount: Int?,
        val runtime: Int? = null,
        val episodeRuntime: Int? = null,
        val seasons: Int? = null,
        val status: String? = null,
        val tvType: String? = null,
        val keywords: List<Pair<Int, String>>? = null,
        val cast: List<Pair<Int, String>>? = null,
        val directors: List<Pair<Int, String>>? = null,
        val spoken: List<String>? = null,
        val isDetails: Boolean = false,
    )

    fun fromListItem(media: String, d: ListItemDto): Mapped {
        val countries = d.originCountry?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.map { it to null }
        return build(
            "$media:${d.id}",
            Facts(
                media = media,
                genreIds = d.genreIds,
                date = if (media == "movie") d.releaseDate else d.firstAirDate,
                language = d.originalLanguage,
                countries = countries,
                voteAverage = d.voteAverage,
                voteCount = d.voteCount,
            ),
        )
    }

    fun fromDetails(media: String, d: DetailsDto): Mapped {
        val prod = d.productionCountries?.filter { it.iso.isNotBlank() }?.map { it.iso to it.name.ifBlank { null } }
        val origin = d.originCountry?.filter { it.isNotBlank() }?.map { it to null }
        val countries = (if (media == "movie") prod else (origin ?: prod))?.takeIf { it.isNotEmpty() }
            ?: (prod ?: origin)?.takeIf { it.isNotEmpty() }
        val directors: List<Pair<Int, String>> =
            if (media == "movie") d.credits?.crew.orEmpty().filter { it.job == "Director" }.map { it.id to it.name }
            else d.createdBy.orEmpty().map { it.id to it.name }
        return build(
            "$media:${d.id}",
            Facts(
                media = media,
                genreIds = d.genres?.map { it.id },
                date = if (media == "movie") d.releaseDate else d.firstAirDate,
                language = d.originalLanguage,
                countries = countries,
                voteAverage = d.voteAverage,
                voteCount = d.voteCount,
                runtime = d.runtime,
                episodeRuntime = d.episodeRunTime?.firstOrNull(),
                seasons = d.numberOfSeasons,
                status = d.status,
                tvType = d.type,
                keywords = d.keywords?.all()?.map { it.id to it.name },
                cast = d.credits?.cast?.sortedBy { it.order ?: Int.MAX_VALUE }?.take(5)?.map { it.id to it.name },
                directors = if (d.credits != null || media == "tv") directors else null,
                spoken = d.spokenLanguages?.filter { it.iso.isNotBlank() }?.map { it.iso },
                isDetails = true,
            ),
        )
    }

    private fun build(key: String, f: Facts): Mapped {
        val facets = LinkedHashMap<String, Set<String>>()
        val nodes = ArrayList<TaxonomyNode>()
        val isMovie = f.media == "movie"

        // format: always known
        val format = LinkedHashSet<String>()
        format += if (isMovie) "format.movie" else "format.tv"
        if (f.genreIds != null && TaxonomySeed.GENRE_TV_MOVIE in f.genreIds) format += "format.tv_movie"
        if (isMovie && f.runtime != null && f.runtime in 1..40) format += "format.short"
        if (!isMovie && f.tvType == "Miniseries") format += "format.miniseries"
        if (f.genreIds != null && TaxonomySeed.GENRE_ANIMATION in f.genreIds && f.language == "ja") format += "format.anime"
        facets[Axes.FORMAT] = format

        f.genreIds?.let { ids ->
            facets[Axes.GENRE] = ids.map { "genre.$it" }.toSet()
            // Estimates from genres (documented as derived / unverified in the taxonomy).
            val moods = LinkedHashSet<String>()
            if (ids.any { it == 35 }) moods += "mood.laugh"
            if (ids.any { it in THINK }) moods += "mood.think"
            if (ids.any { it in NERVES }) moods += "mood.nerves"
            if (ids.any { it in RELAX }) moods += "mood.relax"
            facets[Axes.MOOD] = moods
            val score = ids.count { it in HIGH_TENSION } - ids.count { it in LOW_TENSION }
            facets[Axes.TENSION] = setOf(
                when {
                    score >= 1 -> "tension.high"
                    score <= -1 -> "tension.low"
                    else -> "tension.medium"
                },
            )
        }

        val year = f.date?.take(4)?.toIntOrNull()
        if (year != null && year in 1880..2039) facets[Axes.YEAR] = setOf("year.$year")

        f.countries?.let { list ->
            val ids = LinkedHashSet<String>()
            for ((code, name) in list) {
                val node = NodeFactory.country(code, name)
                nodes += node
                ids += node.id
            }
            facets[Axes.COUNTRY] = ids
        }

        f.language?.takeIf { it.isNotBlank() && it != "xx" }?.let {
            val node = NodeFactory.language(it)
            nodes += node
            facets[Axes.LANGUAGE] = setOf(node.id)
        }

        // Ratings with fewer than 10 votes are not reliable -> unknown.
        if (f.voteAverage != null && (f.voteCount ?: 0) >= 10) {
            val band = floor(f.voteAverage).toInt()
            facets[Axes.RATING] = if (band >= 5) setOf("rating.${minOf(band, 9)}") else emptySet()
        }

        if (isMovie) {
            facets[Axes.SEASONS] = emptySet() // not applicable to films (known, empty)
            if (f.runtime != null && f.runtime > 0) {
                facets[Axes.RUNTIME] = setOf(
                    when {
                        f.runtime < 90 -> "runtime.m.u90"
                        f.runtime < 120 -> "runtime.m.90_120"
                        f.runtime < 150 -> "runtime.m.120_150"
                        else -> "runtime.m.o150"
                    },
                )
            }
        } else {
            if (f.seasons != null && f.seasons > 0) {
                facets[Axes.SEASONS] = setOf(
                    when {
                        f.seasons == 1 -> "seasons.1"
                        f.seasons <= 3 -> "seasons.2_3"
                        f.seasons <= 6 -> "seasons.4_6"
                        else -> "seasons.7p"
                    },
                )
            }
            if (f.episodeRuntime != null && f.episodeRuntime > 0) {
                facets[Axes.RUNTIME] = setOf(
                    when {
                        f.episodeRuntime < 30 -> "runtime.e.u30"
                        f.episodeRuntime <= 50 -> "runtime.e.30_50"
                        else -> "runtime.e.o50"
                    },
                )
            }
        }

        f.status?.takeIf { it.isNotBlank() }?.let { st ->
            facets[Axes.STATUS] = setOf(
                when (st) {
                    "Ended" -> "status.ended"
                    "Released" -> "status.released"
                    "Returning Series" -> "status.ongoing"
                    "In Production", "Planned", "Pilot", "Post Production" -> "status.upcoming"
                    "Canceled" -> "status.canceled"
                    else -> "status.other"
                },
            )
        }

        f.keywords?.let { kws ->
            val ids = LinkedHashSet<String>()
            for ((id, name) in kws) {
                val node = NodeFactory.keyword(id, name)
                nodes += node
                ids += node.id
            }
            facets[Axes.KEYWORD] = ids
        }
        f.keywords?.let { kws ->
            val names = kws.map { it.second }
            val ending = KeywordRules.match(KeywordRules.ending, names)
            if (ending.isNotEmpty()) facets[Axes.ENDING] = ending // no match = unknown, not "no such ending"
            facets[Axes.AVOID] = KeywordRules.match(KeywordRules.avoid, names)
        }
        f.cast?.let { list ->
            val ids = LinkedHashSet<String>()
            for ((id, name) in list) {
                nodes += NodeFactory.person(Axes.CAST, id, name)
                ids += "${Axes.CAST}.$id"
            }
            facets[Axes.CAST] = ids
        }
        f.directors?.let { list ->
            val ids = LinkedHashSet<String>()
            for ((id, name) in list) {
                nodes += NodeFactory.person(Axes.DIRECTOR, id, name)
                ids += "${Axes.DIRECTOR}.$id"
            }
            facets[Axes.DIRECTOR] = ids
        }
        f.spoken?.let { list ->
            val ids = LinkedHashSet<String>()
            for (code in list) {
                val node = NodeFactory.language(code, Axes.SPOKEN)
                nodes += node
                ids += node.id
            }
            facets[Axes.SPOKEN] = ids
        }

        return Mapped(Work(key, facets), nodes)
    }
}
