package com.tvseries.Movies.data

/** What the user has watched, summarised by genre, to suggest more of the same ("mostly action" gives action). */
object WatchedProfile {
    /** Fewer watched titles with known genres say too little about the person's taste. */
    const val MIN_KNOWN = 3
    const val MAX_SECTIONS = 2
    private const val MIN_SHARE = 0.25
    private const val MIN_SUGGESTION_VOTES = 300
    private const val MIN_SUGGESTION_RATING = 6

    /** TV genres that have a film twin are counted under the film genre: an "Action & Adventure" series counts as action. */
    private val tvToFilm = mapOf(10759 to listOf(28, 12), 10765 to listOf(878, 14), 10768 to listOf(10752))
    private val filmToTv = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768)

    data class Top(val genreId: Int, val count: Int)

    fun normalize(genreIds: List<Int>): Set<Int> = genreIds.flatMap { tvToFilm[it] ?: listOf(it) }.toSet()

    /**
     * The most-watched genres, strongest first (a title counts once per genre). A genre must appear in at least a quarter of the
     * watched titles. Empty when fewer than [MIN_KNOWN] watched titles have known genres.
     */
    fun top(watchedGenres: List<List<Int>>): List<Top> {
        val known = watchedGenres.map { normalize(it) }.filter { it.isNotEmpty() }
        if (known.size < MIN_KNOWN) return emptyList()
        val counts = HashMap<Int, Int>()
        for (genres in known) for (id in genres) counts[id] = (counts[id] ?: 0) + 1
        return counts.entries
            .filter { it.value.toDouble() / known.size >= MIN_SHARE }
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(MAX_SECTIONS)
            .map { Top(it.key, it.value) }
    }

    /** The TMDB genre id to ask for on [media] for a film-style genre id; null when that kind has no such genre. */
    fun genreFor(media: String, filmGenreId: Int): Int? =
        if (media == "movie") {
            filmGenreId.takeIf { it in TaxonomySeed.movieGenreIds }
        } else {
            (filmToTv[filmGenreId] ?: filmGenreId).takeIf { it in TaxonomySeed.tvGenreIds }
        }

    /** Well-liked titles of the genre (rating 6+, enough votes), most popular first. */
    fun suggestionQuery(base: DiscoverQuery, media: String, filmGenreId: Int): DiscoverQuery? {
        val genre = genreFor(media, filmGenreId) ?: return null
        return base.copy(
            genres = setOf(genre),
            minRating = MIN_SUGGESTION_RATING,
            minVotes = maxOf(base.minVotes, MIN_SUGGESTION_VOTES),
            sort = "popularity.desc",
        )
    }

    /** Movies and series alternately, each title once, at most [take]. */
    fun mix(movies: List<WorkCard>, series: List<WorkCard>, take: Int): List<WorkCard> {
        val out = ArrayList<WorkCard>()
        for (i in 0 until maxOf(movies.size, series.size)) {
            movies.getOrNull(i)?.let { out += it }
            series.getOrNull(i)?.let { out += it }
        }
        return out.distinctBy { it.key }.take(take)
    }
}
