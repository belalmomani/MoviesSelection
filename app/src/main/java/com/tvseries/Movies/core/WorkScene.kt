package com.tvseries.Movies.core

/**
 * Picks an ORIGINAL procedural scenery (drawn by the app) inspired by a title's genres, for the
 * "screen that swallows you" transition. It never recreates scenes from the film itself.
 */
object WorkScene {
    fun envFor(genres: Collection<Int>): Env {
        val g = genres.toSet()
        val darkCrime = 80 in g || 53 in g || 9648 in g
        return when {
            27 in g -> Env.ASH
            (878 in g || 10765 in g) && darkCrime -> Env.CYBER
            878 in g || 10765 in g -> Env.FUTURE
            14 in g -> Env.CASTLE
            37 in g -> Env.WEST
            10752 in g || 10768 in g -> Env.WAR
            36 in g -> Env.ROME
            darkCrime -> Env.COLD
            12 in g || 10759 in g -> Env.JUNGLE
            16 in g || 10751 in g || 10762 in g || 10402 in g -> Env.NORTH
            10749 in g -> Env.SEA
            35 in g -> Env.DESERT
            28 in g -> Env.WAR
            else -> Env.FILM
        }
    }

    fun colorsFor(env: Env): Pair<Long, Long> = TimeWorlds.all.first { it.env == env }.colors
}
