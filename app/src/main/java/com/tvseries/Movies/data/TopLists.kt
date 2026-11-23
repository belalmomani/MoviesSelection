package com.tvseries.Movies.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** A well-known actor whose [rating] is the average TMDB rating of their best-known titles. */
@Serializable
data class ActorEntry(val id: Int, val name: String, val nameAr: String? = null, val photo: String? = null, val rating: Double)

/** The actors found by the last scan of TMDB's popular people, kept on the device so the scan runs about once a week. */
@Serializable
data class ActorPool(val builtDay: String, val actors: List<ActorEntry>)

/** The titles rated 8 and above found by the last scan of TMDB, kept on the device so the scan runs about once a week. */
@Serializable
data class CardPool(val builtDay: String, val cards: List<WorkCard>)

/** Rules for the Home rows "rated 8 and above", "famous actors 7+" and "top-rated actors 7.5+, 10 a day". */
object TopLists {
    const val MIN_TITLE_RATING = 8
    const val MIN_ACTOR_RATING = 7.0
    const val MIN_TOP_ACTOR_RATING = 7.5
    const val DAILY_ACTORS = 10
    const val DAILY_TITLES = 10
    const val POOL_MAX_AGE_DAYS = 7L
    private const val MOVIE_MIN_VOTES = 1500
    private const val SERIES_MIN_VOTES = 500
    private const val PREFILTER_MIN_AVG = 6.8

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

    /**
     * Cheap check from the 2 to 3 titles TMDB lists as "known for": drops actors clearly below the bar before the
     * expensive per-person request. With too little to judge it says yes (the person is then checked properly).
     */
    fun worthChecking(item: ListItemDto): Boolean {
        val rated = item.knownFor.orEmpty().filter { (it.voteCount ?: 0) >= 100 && (it.voteAverage ?: 0.0) > 0.0 }
        if (rated.size < 2) return true
        return rated.sumOf { it.voteAverage ?: 0.0 } / rated.size >= PREFILTER_MIN_AVG
    }

    fun today(): String = LocalDate.now().toString()

    /** The stored scan is used for [POOL_MAX_AGE_DAYS] days; an empty one, a broken date or a clock set back means rescan. */
    fun poolIsFresh(pool: ActorPool?, today: String): Boolean = pool != null && isFresh(pool.builtDay, pool.actors.isNotEmpty(), today)

    fun cardPoolIsFresh(pool: CardPool?, today: String): Boolean = pool != null && isFresh(pool.builtDay, pool.cards.isNotEmpty(), today)

    private fun isFresh(builtDay: String, hasItems: Boolean, today: String): Boolean {
        if (!hasItems) return false
        val age = try {
            ChronoUnit.DAYS.between(LocalDate.parse(builtDay), LocalDate.parse(today))
        } catch (e: Exception) {
            return false
        }
        return age in 0L..POOL_MAX_AGE_DAYS
    }

    /** The same [DAILY_ACTORS] for everybody all day and different ones tomorrow: a shuffle seeded by the day. Best rated first. */
    fun dailyPick(pool: List<ActorEntry>, day: String): List<ActorEntry> {
        val eligible = pool.filter { it.rating >= MIN_TOP_ACTOR_RATING }.sortedBy { it.id }
        val picked = eligible.shuffled(java.util.Random(day.hashCode().toLong())).take(DAILY_ACTORS)
        return picked.sortedByDescending { it.rating }
    }

    /**
     * [DAILY_TITLES] titles rated [MIN_TITLE_RATING] or more: the same for everybody all day, different ones tomorrow
     * (a shuffle seeded by the day and the kind, so movies and series do not follow the same pattern). Best rated first.
     */
    fun dailyTitles(pool: List<WorkCard>, day: String, media: String): List<WorkCard> {
        val eligible = pool.filter { (it.rating ?: 0.0) >= MIN_TITLE_RATING }.distinctBy { it.key }.sortedBy { it.key }
        val picked = eligible.shuffled(java.util.Random((day + media).hashCode().toLong())).take(DAILY_TITLES)
        return picked.sortedByDescending { it.rating ?: 0.0 }
    }
}
