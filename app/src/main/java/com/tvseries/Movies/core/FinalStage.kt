package com.tvseries.Movies.core

/** Second filtering stage: few titles remain, so the user decides by lead actor, mood, then story. */
enum class Stage { HEROES, GENRES, STORY, DUEL, WINNER }

object FinalStage {
    /** Keeps the chosen keys; an empty choice means "no preference" and keeps everything. */
    fun narrow(keys: List<String>, chosen: Set<String>): List<String> =
        if (chosen.isEmpty()) keys else keys.filter { it in chosen }

    /** Which stage comes next. STORY always ends with at most two titles (the screen enforces it). */
    fun next(count: Int, after: Stage?): Stage = when {
        count <= 1 -> Stage.WINNER
        count == 2 -> Stage.DUEL
        after == null -> Stage.HEROES
        after == Stage.HEROES -> Stage.GENRES
        else -> Stage.STORY
    }
}
