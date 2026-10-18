package com.tvseries.Movies.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.serialization.Serializable

/** Position of a title on three ESTIMATED axes, each in [-1, 1]. Derived from genres only (not official data). */
data class TraitProfile(val tension: Double, val fantasy: Double, val depth: Double)

object Traits {
    // genre id -> (tension: calm -1..tense +1, fantasy: realistic -1..fantasy +1, depth: light -1..deep +1)
    private val table: Map<Int, Triple<Double, Double, Double>> = mapOf(
        28 to Triple(0.6, 0.1, -0.3), 12 to Triple(0.2, 0.4, -0.4), 16 to Triple(-0.3, 0.6, -0.2),
        35 to Triple(-0.7, 0.0, -0.6), 80 to Triple(0.6, -0.4, 0.3), 99 to Triple(-0.1, -1.0, 0.6),
        18 to Triple(0.0, -0.6, 0.5), 10751 to Triple(-0.8, 0.3, -0.6), 14 to Triple(0.0, 1.0, 0.0),
        36 to Triple(0.1, -0.9, 0.5), 27 to Triple(1.0, 0.4, 0.0), 10402 to Triple(-0.6, 0.0, -0.2),
        9648 to Triple(0.5, -0.2, 0.6), 10749 to Triple(-0.5, -0.3, -0.1), 878 to Triple(0.2, 0.9, 0.5),
        10770 to Triple(-0.2, -0.4, -0.3), 53 to Triple(0.9, -0.2, 0.3), 10752 to Triple(0.8, -0.7, 0.4),
        37 to Triple(0.3, -0.2, 0.0), 10759 to Triple(0.5, 0.2, -0.3), 10762 to Triple(-0.8, 0.4, -0.7),
        10763 to Triple(0.0, -1.0, 0.2), 10764 to Triple(0.0, -0.9, -0.5), 10765 to Triple(0.2, 1.0, 0.3),
        10766 to Triple(-0.2, -0.5, -0.3), 10767 to Triple(-0.5, -1.0, -0.4), 10768 to Triple(0.6, -0.7, 0.5),
    )

    fun fromGenres(ids: Collection<Int>): TraitProfile? {
        val rows = ids.mapNotNull { table[it] }
        if (rows.isEmpty()) return null
        return TraitProfile(rows.map { it.first }.average(), rows.map { it.second }.average(), rows.map { it.third }.average())
    }

    fun genreIds(work: Work): List<Int> =
        work.facets[Axes.GENRE].orEmpty().mapNotNull { it.removePrefix("genre.").toIntOrNull() }

    /** null = genres unknown. */
    fun of(work: Work): TraitProfile? = if (work.facets[Axes.GENRE] == null) null else fromGenres(genreIds(work))

    fun yearOf(work: Work): Int? =
        work.facets[Axes.YEAR].orEmpty().firstOrNull { it.startsWith("year.") }?.removePrefix("year.")?.toIntOrNull()
}

/** What the user wants right now: preferences on sliders (null = don't care) + mandatory conditions. */
@Serializable
data class UserIntent(
    val tension: Double? = null,
    val fantasy: Double? = null,
    val depth: Double? = null,
    val selection: Selection = Selection(),
)

/** Locally learned taste (accepted / rejected titles and reasons). Never leaves the device. */
@Serializable
data class TasteProfile(
    val genre: Map<Int, Double> = emptyMap(),
    val oldRejects: Int = 0,
    val slowRejects: Int = 0,
    val repeatedRejects: Int = 0,
    val accepted: List<String> = emptyList(),
    val rejected: List<String> = emptyList(),
) {
    fun seen(): Set<String> = (accepted + rejected).toSet()

    /** [-1, 1]; 0 = no opinion. */
    fun score(genres: Collection<Int>, year: Int?): Double {
        val g = genres.mapNotNull { genre[it] }
        var s = if (g.isEmpty()) 0.0 else g.average().coerceIn(-1.0, 1.0)
        if (year != null && oldRejects > 0) {
            s += min(oldRejects, 5) / 5.0 * ((year - 1995) / 40.0).coerceIn(-0.5, 0.5) * 0.5
        }
        return s.coerceIn(-1.0, 1.0)
    }

    /** reasons: slow, old, violent, romance, repeated, mood */
    fun update(genres: Collection<Int>, key: String, accept: Boolean, reasons: Set<String>): TasteProfile {
        val m = genre.toMutableMap()
        fun bump(id: Int, d: Double) { m[id] = ((m[id] ?: 0.0) + d).coerceIn(-3.0, 3.0) }
        if (accept) {
            genres.forEach { bump(it, 1.0) }
        } else {
            val base = if (reasons == setOf("mood")) -0.2 else -0.6
            genres.forEach { bump(it, base) }
            if ("violent" in reasons) listOf(27, 53, 28, 80, 10752, 10759).forEach { if (it in genres) bump(it, -1.0) }
            if ("romance" in reasons && 10749 in genres) bump(10749, -1.0)
        }
        return copy(
            genre = m,
            oldRejects = oldRejects + if (!accept && "old" in reasons) 1 else 0,
            slowRejects = slowRejects + if (!accept && "slow" in reasons) 1 else 0,
            repeatedRejects = repeatedRejects + if (!accept && "repeated" in reasons) 1 else 0,
            accepted = if (accept) (accepted + key).takeLast(300) else accepted,
            rejected = if (!accept) (rejected + key).takeLast(300) else rejected,
        )
    }
}

data class Scored(val work: Work, val fit: Double, val taste: Double, val total: Double)

object Ranker {
    /** 0..1; 0.5 when the user set no sliders or the work's genres are unknown. */
    fun fit(p: TraitProfile?, i: UserIntent): Double {
        if (p == null) return 0.5
        val parts = ArrayList<Double>()
        i.tension?.let { parts += abs(it - p.tension) / 2 }
        i.fantasy?.let { parts += abs(it - p.fantasy) / 2 }
        i.depth?.let { parts += abs(it - p.depth) / 2 }
        return if (parts.isEmpty()) 0.5 else 1.0 - parts.average()
    }

    /** Only works that VERIFIABLY satisfy the mandatory conditions (selection) are ranked. */
    fun rank(works: List<Work>, intent: UserIntent, taste: TasteProfile): List<Scored> =
        works.filter { matchWork(it, intent.selection) == Match.YES }
            .map { w ->
                val f = fit(Traits.of(w), intent)
                val t = taste.score(Traits.genreIds(w), Traits.yearOf(w))
                Scored(w, f, t, f + 0.5 * t)
            }
            .sortedByDescending { it.total }
}

class DoorPicks(val closest: Scored?, val surprise: Scored?, val safe: Scored?)

object Doors {
    /**
     * closest = best overall fit; surprise = mid-ranked title with a different genre mix (still passes every
     * mandatory condition); safe = best-ranked title whose full details were fetched (most verifiable).
     */
    fun choose(ranked: List<Scored>, enriched: Set<String>, rnd: Random): DoorPicks {
        val closest = ranked.firstOrNull() ?: return DoorPicks(null, null, null)
        val rest = ranked.drop(1)
        val closestGenres = Traits.genreIds(closest.work).toSet()
        val band = rest.drop(rest.size / 4).take(max(1, rest.size / 2))
        val different = band.filter { Traits.genreIds(it.work).toSet() != closestGenres }.ifEmpty { band }
        val surprise = if (different.isEmpty()) null else different[rnd.nextInt(different.size)]
        val remaining = rest.filter { it.work.key != surprise?.work?.key }
        val safe = remaining.firstOrNull { it.work.key in enriched } ?: remaining.firstOrNull()
        return DoorPicks(closest, surprise, safe)
    }
}

object Roulette {
    /** Weighted draw among the given (already qualified) candidates, with a small share of pure surprises. */
    fun draw(c: List<Scored>, rnd: Random, surpriseRate: Double = 0.1): Scored? {
        if (c.isEmpty()) return null
        if (rnd.nextDouble() < surpriseRate) return c[rnd.nextInt(c.size)]
        val weights = c.map { 0.1 + max(0.0, it.total + 0.5).pow(2) }
        var r = rnd.nextDouble() * weights.sum()
        for (i in c.indices) {
            r -= weights[i]
            if (r <= 0) return c[i]
        }
        return c.last()
    }

    fun drawMany(c: List<Scored>, n: Int, rnd: Random): List<Scored> {
        val pool = c.toMutableList()
        val out = ArrayList<Scored>()
        while (out.size < n && pool.isNotEmpty()) {
            val pick = draw(pool, rnd) ?: break
            out += pick
            pool.remove(pick)
        }
        return out
    }
}
