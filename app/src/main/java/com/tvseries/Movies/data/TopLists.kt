package com.tvseries.Movies.data

/** Rules for the Home rows "rated 8 and above" (movies / series) and "well-known actors rated 7 and above". */
object TopLists {
    const val MIN_TITLE_RATING = 8
    const val MIN_ACTOR_RATING = 7.0
    private const val MOVIE_MIN_VOTES = 1500
    private const val SERIES_MIN_VOTES = 500

    /** TMDB discover query: rating 8+ with enough votes to trust it (series get fewer votes than films), best rated first. */
    fun titleQuery(base: DiscoverQuery, media: String): DiscoverQuery =
        base.copy(
            minVotes = if (media == "movie") MOVIE_MIN_VOTES else SERIES_MIN_VOTES,
            minRating = MIN_TITLE_RATING,
            sort = "vote_average.desc",
        )

    /** TMDB's popular-people list also holds directors and crew: keep actors only. */
    fun isActor(item: ListItemDto): Boolean =
        item.knownForDepartment == "Acting" && item.id != 0 && !item.name.isNullOrBlank()

    fun actorPasses(rating: Double?): Boolean = rating != null && rating >= MIN_ACTOR_RATING
}
