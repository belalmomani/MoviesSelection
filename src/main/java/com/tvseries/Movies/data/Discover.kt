package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.Taxonomy

/** Parameters for TMDB /discover/{movie|tv}. */
data class DiscoverQuery(
    val genres: Set<Int> = emptySet(),
    val withoutGenres: Set<Int> = emptySet(),
    val language: String? = null,
    val countries: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Int? = null,
    val maxVotes: Int? = null,
    val keywords: Set<Int> = emptySet(),
    val minVotes: Int = 100,
    val includeAdult: Boolean = false,
    val sort: String = "popularity.desc",
    /** Runtime window in minutes (film runtime or TV episode runtime). */
    val runtimeFrom: Int? = null,
    val runtimeTo: Int? = null,
    /** TMDB person ids: lead cast / crew. Films only (TV discover has no people filter). */
    val cast: Set<Int> = emptySet(),
    val crew: Set<Int> = emptySet(),
    val withoutKeywords: Set<Int> = emptySet(),
    /** TMDB TV status codes (0 returning, 1 planned, 2 in production, 3 ended, 4 canceled, 5 pilot). TV only. */
    val statuses: Set<Int> = emptySet(),
) {
    fun signature(): String =
        "g=${genres.sorted()};wg=${withoutGenres.sorted()};l=$language;c=${countries.sorted()};y=$yearFrom-$yearTo;r=$minRating;v=$minVotes-$maxVotes;k=${keywords.sorted()};a=$includeAdult;s=$sort;rt=$runtimeFrom-$runtimeTo;cs=${cast.sorted()};cr=${crew.sorted()};wk=${withoutKeywords.sorted()};st=${statuses.sorted()}"

    fun params(media: String, page: Int, uiLanguage: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["language"] = uiLanguage
        m["page"] = page.toString()
        m["sort_by"] = sort
        m["include_adult"] = includeAdult.toString()
        m["vote_count.gte"] = minVotes.toString()
        if (genres.isNotEmpty()) m["with_genres"] = genres.sorted().joinToString("|")
        if (withoutGenres.isNotEmpty()) m["without_genres"] = withoutGenres.sorted().joinToString(",")
        language?.let { m["with_original_language"] = it }
        if (countries.isNotEmpty()) m["with_origin_country"] = countries.sorted().joinToString("|")
        minRating?.let { m["vote_average.gte"] = it.toString() }
        maxVotes?.let { m["vote_count.lte"] = it.toString() }
        if (keywords.isNotEmpty()) m["with_keywords"] = keywords.sorted().joinToString("|")
        runtimeFrom?.let { m["with_runtime.gte"] = it.toString() }
        runtimeTo?.let { m["with_runtime.lte"] = it.toString() }
        if (withoutKeywords.isNotEmpty()) m["without_keywords"] = withoutKeywords.sorted().joinToString(",")
        if (media == "movie") {
            if (cast.isNotEmpty()) m["with_cast"] = cast.sorted().joinToString("|")
            if (crew.isNotEmpty()) m["with_crew"] = crew.sorted().joinToString("|")
        } else if (statuses.isNotEmpty()) {
            m["with_status"] = statuses.sorted().joinToString("|")
        }
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

    /** Applies a production sphere to a query; null when the sphere cannot match the other conditions. */
    private fun applySphere(sphere: String, q: DiscoverQuery): DiscoverQuery? {
        if (sphere == Spheres.ANIMATION) return q.copy(genres = setOf(16))
        val wanted = Spheres.countriesOf(sphere) ?: return null
        val c = if (q.countries.isEmpty()) wanted else q.countries.intersect(wanted)
        if (c.isEmpty()) return null
        // animation is its own sphere, so the other spheres never include it
        return q.copy(countries = c, withoutGenres = q.withoutGenres + 16)
    }

    /**
     * One or more discover queries for the selection. A chosen production sphere (Arab / East Asia / Western /
     * Animation / Other) becomes its own query. Arab and East-Asian spheres are split into one query per country
     * (so Japanese, Korean and Chinese titles are all represented together); Western / Other use a single query.
     */
    fun refineAll(sel: Selection, taxonomy: Taxonomy, media: String, base: DiscoverQuery): List<DiscoverQuery> {
        val spheres = sel.include[Axes.SPHERE].orEmpty()
        if (spheres.isNotEmpty()) {
            val without = sel.copy(include = sel.include - Axes.SPHERE)
            val b = refine(without, taxonomy, media, base) ?: return emptyList()
            return spheres.mapNotNull { sp -> applySphere(sp, b)?.let { lowVotesForArabic(it) to sp } }
                .flatMap { (q, sp) -> if (sp == Spheres.WESTERN || sp == Spheres.OTHER) listOf(q) else expand(q) }
                .distinctBy { it.signature() }
        }
        val single = refine(sel, taxonomy, media, base) ?: return emptyList()
        // Several original languages cannot be OR-ed in one TMDB query: one query per language (max 4).
        val langs = sel.include[Axes.LANGUAGE].orEmpty().map { it.removePrefix("language.") }
        if (langs.size in 2..MAX_LANGUAGE_QUERIES) {
            return langs.sorted().flatMap { expand(lowVotesForArabic(single.copy(language = it))) }.distinctBy { it.signature() }
        }
        return expand(lowVotesForArabic(single))
    }

    private const val MAX_LANGUAGE_QUERIES = 4

    /** Axes whose conditions are (at least partly) sent to TMDB. Everything else is checked only on this device. */
    private val serverIncludeAxes = setOf(
        Axes.SPHERE, Axes.GENRE, Axes.LANGUAGE, Axes.COUNTRY, Axes.YEAR, Axes.RATING,
        Axes.RUNTIME, Axes.KEYWORD, Axes.CAST, Axes.DIRECTOR, Axes.STATUS,
    )
    private val serverExcludeAxes = setOf(Axes.GENRE, Axes.KEYWORD, Axes.RUNTIME)

    /** True when the selection has at least one condition TMDB can filter on its side. */
    fun canRefine(sel: Selection): Boolean =
        sel.include.any { (a, v) -> a in serverIncludeAxes && v.isNotEmpty() } ||
            sel.exclude.any { (a, v) -> a in serverExcludeAxes && v.isNotEmpty() }

    /** True when some condition is NOT sent to TMDB for the given media (so TMDB's count is an upper bound). */
    fun hasClientOnlyConditions(sel: Selection, media: String): Boolean {
        val people = media == "tv" && (sel.include[Axes.CAST].orEmpty().isNotEmpty() || sel.include[Axes.DIRECTOR].orEmpty().isNotEmpty())
        val otherInclude = sel.include.any { (a, v) -> v.isNotEmpty() && a !in serverIncludeAxes && a != Axes.FORMAT }
        val otherExclude = sel.exclude.any { (a, v) -> v.isNotEmpty() && a !in serverExcludeAxes }
        return people || otherInclude || otherExclude
    }

    private class Band(val id: String, val lo: Int, val hi: Int?)

    private val filmBands = listOf(
        Band("runtime.m.u90", 0, 89), Band("runtime.m.90_120", 90, 119),
        Band("runtime.m.120_150", 120, 149), Band("runtime.m.o150", 150, null),
    )
    private val episodeBands = listOf(
        Band("runtime.e.u30", 0, 29), Band("runtime.e.30_50", 30, 50), Band("runtime.e.o50", 51, null),
    )

    private fun applyRuntime(sel: Selection, media: String, q: DiscoverQuery): DiscoverQuery {
        val bands = if (media == "movie") filmBands else episodeBands
        val ids = bands.map { it.id }.toSet()
        val inc = sel.include[Axes.RUNTIME].orEmpty().filter { it in ids }
        val exc = sel.exclude[Axes.RUNTIME].orEmpty()
        val allowed = bands.filter { (inc.isEmpty() || it.id in inc) && it.id !in exc }
        // Nothing selected for this kind, or everything allowed / nothing allowed: do not narrow (the engine re-checks anyway).
        if (allowed.isEmpty() || allowed.size == bands.size) return q
        val from = allowed.minOf { it.lo }.takeIf { it > 0 }
        val to = if (allowed.any { it.hi == null }) null else allowed.maxOf { it.hi!! }
        // The window is the hull of the allowed bands: a superset when they are not adjacent; the engine removes the rest.
        return q.copy(runtimeFrom = from, runtimeTo = to)
    }

    private fun personIds(values: Set<String>, axis: String): Set<Int> =
        values.mapNotNull { it.removePrefix("$axis.").toIntOrNull() }.toSet()

    private fun tvStatusCodes(values: Set<String>): Set<Int>? {
        val out = HashSet<Int>()
        for (v in values) {
            when (v) {
                "status.ended", "status.completed" -> out += 3
                "status.ongoing" -> out += 0
                "status.upcoming" -> out += setOf(1, 2, 5)
                "status.canceled" -> out += 4
                else -> return null // film-only / unknown value: do not narrow TV by status
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private val eastAsiaPriority = listOf("KR", "JP", "CN", "HK", "TW", "MO")

    private fun expand(q: DiscoverQuery): List<DiscoverQuery> {
        if (q.countries.size <= 1) return listOf(q)
        val ordered = when {
            q.countries.all { it in arabSet } -> arabPriority.filter { it in q.countries } + q.countries.filter { it !in arabPriority }.sorted()
            q.countries.all { it in Spheres.eastAsiaCountries } -> eastAsiaPriority.filter { it in q.countries }
            else -> q.countries.sorted()
        }
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

        // Several years / decades: one window covering all of them (a superset; the engine re-checks every title).
        val yearRanges = sel.include[Axes.YEAR].orEmpty().mapNotNull { v ->
            when {
                v.startsWith("year.") -> v.removePrefix("year.").toIntOrNull()?.let { it to it }
                v.startsWith("decade.") -> v.removePrefix("decade.").toIntOrNull()?.let { it to it + 9 }
                else -> null
            }
        }
        if (yearRanges.isNotEmpty()) q = q.copy(yearFrom = yearRanges.minOf { it.first }, yearTo = yearRanges.maxOf { it.second })

        val ratings = sel.include[Axes.RATING].orEmpty().mapNotNull { it.removePrefix("rating.").toIntOrNull() }
        if (ratings.isNotEmpty()) q = q.copy(minRating = ratings.min())

        // Excluded genres ("no horror"...) are sent as without_genres.
        val badGenres = sel.exclude[Axes.GENRE].orEmpty().mapNotNull { it.removePrefix("genre.").toIntOrNull() }.toSet()
        if (badGenres.isNotEmpty()) q = q.copy(withoutGenres = q.withoutGenres + badGenres)

        q = applyRuntime(sel, media, q)

        val kw = personIds(sel.include[Axes.KEYWORD].orEmpty(), "keyword")
        if (kw.isNotEmpty()) q = q.copy(keywords = q.keywords + kw)
        val badKw = personIds(sel.exclude[Axes.KEYWORD].orEmpty(), "keyword")
        if (badKw.isNotEmpty()) q = q.copy(withoutKeywords = badKw)

        if (media == "movie") {
            val cast = personIds(sel.include[Axes.CAST].orEmpty(), Axes.CAST)
            if (cast.isNotEmpty()) q = q.copy(cast = cast)
            val crew = personIds(sel.include[Axes.DIRECTOR].orEmpty(), Axes.DIRECTOR)
            if (crew.isNotEmpty()) q = q.copy(crew = crew)
        } else {
            tvStatusCodes(sel.include[Axes.STATUS].orEmpty())?.let { q = q.copy(statuses = it) }
        }

        return q
    }
}
