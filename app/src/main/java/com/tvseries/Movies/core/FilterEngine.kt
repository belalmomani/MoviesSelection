package com.tvseries.Movies.core

import kotlinx.serialization.Serializable

/**
 * One title as the engine sees it.
 *
 * facets: axisId -> category ids (already expanded with ancestors).
 *  - axis ABSENT  = the information is UNKNOWN (not loaded / not provided by the source)
 *  - axis PRESENT but empty = known, and the work has no value on that axis (or the axis does not apply)
 */
data class Work(val key: String, val facets: Map<String, Set<String>>) {
    fun has(axis: String, value: String): Boolean = facets[axis]?.contains(value) == true

    /** Returns a copy whose values are expanded with all ancestors from [taxonomy]. */
    fun expanded(taxonomy: Taxonomy): Work =
        Work(key, facets.mapValues { (_, v) -> taxonomy.closure(v) })
}

@Serializable
data class Condition(val axis: String, val value: String, val excluded: Boolean = false)

/**
 * User selection. Within one axis values are OR-ed; different axes are AND-ed; excluded values
 * remove any work known to have them.
 */
@Serializable
data class Selection(
    val include: Map<String, Set<String>> = emptyMap(),
    val exclude: Map<String, Set<String>> = emptyMap(),
) {
    val isEmpty: Boolean get() = include.isEmpty() && exclude.isEmpty()

    fun conditions(): List<Condition> =
        include.flatMap { (a, vs) -> vs.sorted().map { Condition(a, it, false) } } +
            exclude.flatMap { (a, vs) -> vs.sorted().map { Condition(a, it, true) } }

    fun isIncluded(axis: String, value: String): Boolean = include[axis]?.contains(value) == true
    fun isExcluded(axis: String, value: String): Boolean = exclude[axis]?.contains(value) == true

    fun toggleInclude(axis: String, value: String): Selection {
        val cur = include[axis].orEmpty()
        val next = if (value in cur) cur - value else cur + value
        return copy(include = put(include, axis, next), exclude = put(exclude, axis, exclude[axis].orEmpty() - value))
    }

    fun toggleExclude(axis: String, value: String): Selection {
        val cur = exclude[axis].orEmpty()
        val next = if (value in cur) cur - value else cur + value
        return copy(exclude = put(exclude, axis, next), include = put(include, axis, include[axis].orEmpty() - value))
    }

    fun remove(c: Condition): Selection =
        if (c.excluded) copy(exclude = put(exclude, c.axis, exclude[c.axis].orEmpty() - c.value))
        else copy(include = put(include, c.axis, include[c.axis].orEmpty() - c.value))

    private fun put(m: Map<String, Set<String>>, k: String, v: Set<String>): Map<String, Set<String>> =
        if (v.isEmpty()) m - k else m + (k to v)
}

/** Selection + undo history. Any previous condition can still be removed/changed without restarting. */
data class FilterState(val selection: Selection = Selection(), val history: List<Selection> = emptyList()) {
    fun apply(next: Selection): FilterState =
        if (next == selection) this else FilterState(next, history + selection)

    fun back(): FilterState =
        if (history.isEmpty()) this else FilterState(history.last(), history.dropLast(1))

    fun clear(): FilterState = apply(Selection())
    val canGoBack: Boolean get() = history.isNotEmpty()
}

enum class Match { YES, NO, UNKNOWN }

/**
 * YES     = every condition is verified true.
 * NO      = at least one condition is verified false.
 * UNKNOWN = nothing is verified false but some needed information is missing.
 */
fun matchWork(work: Work, sel: Selection, ignoreIncludeAxis: String? = null): Match {
    var unknown = false
    for ((axis, wanted) in sel.include) {
        if (axis == ignoreIncludeAxis) continue
        val have = work.facets[axis]
        if (have == null) unknown = true
        else if (have.none { it in wanted }) return Match.NO
    }
    for ((axis, banned) in sel.exclude) {
        val have = work.facets[axis]
        if (have == null) unknown = true
        else if (have.any { it in banned }) return Match.NO
    }
    return if (unknown) Match.UNKNOWN else Match.YES
}

data class FilterResult(val matched: List<Work>, val possible: List<Work>)

/**
 * inResult: how many current results have this value.
 * afterInclude: how many results there would be after toggling this value as an include.
 */
data class FacetCount(
    val value: String,
    val inResult: Int,
    val afterInclude: Int,
    val selected: Boolean,
    val excluded: Boolean,
)

data class AxisView(val axis: String, val values: List<FacetCount>, val knownInResult: Int, val unknownInResult: Int)

/** Lower score = better next question. */
data class AxisSuggestion(val axis: String, val score: Double, val usefulValues: Int, val coverage: Double)

sealed interface Outcome {
    /** Nothing has been loaded at all (different from "zero results"). */
    data object PoolEmpty : Outcome

    /** Verified zero results inside the loaded data. [poolComplete] tells whether the whole source was loaded. */
    data class NoResults(val poolComplete: Boolean) : Outcome

    /** No work is confirmed, but some could match once missing data is known. */
    data class OnlyPossible(val possibleCount: Int) : Outcome

    data class Single(val work: Work, val reasons: List<Condition>, val otherPossible: Int) : Outcome

    /** Several works that cannot be told apart with the known attributes. All are shown. */
    data class Indistinguishable(val works: List<Work>) : Outcome

    data class Many(val count: Int, val next: List<AxisSuggestion>) : Outcome
}

class FilterEngine(
    val works: List<Work>,
    val taxonomy: Taxonomy,
    private val poolComplete: Boolean = false,
) {
    fun evaluate(sel: Selection): FilterResult {
        val matched = ArrayList<Work>()
        val possible = ArrayList<Work>()
        for (w in works) {
            when (matchWork(w, sel)) {
                Match.YES -> matched += w
                Match.UNKNOWN -> possible += w
                Match.NO -> {}
            }
        }
        return FilterResult(matched, possible)
    }

    /** Values of [axis] that can still produce a useful (different) result, with counts before/after. */
    fun axisView(sel: Selection, axis: String, current: FilterResult = evaluate(sel)): AxisView {
        val selected = sel.include[axis].orEmpty()
        val excluded = sel.exclude[axis].orEmpty()
        val n = current.matched.size

        val inResult = HashMap<String, Int>()
        var known = 0
        var unknown = 0
        for (w in current.matched) {
            val vs = w.facets[axis]
            if (vs == null) unknown++ else {
                known++
                for (v in vs) inResult[v] = (inResult[v] ?: 0) + 1
            }
        }

        val base = if (selected.isEmpty()) current.matched
        else works.filter { matchWork(it, sel, axis) == Match.YES }
        var baseWithSelected = 0
        val extra = HashMap<String, Int>()
        for (w in base) {
            val vs = w.facets[axis] ?: continue
            if (selected.isNotEmpty() && vs.any { it in selected }) baseWithSelected++
            else for (v in vs) extra[v] = (extra[v] ?: 0) + 1
        }

        val ids = LinkedHashSet<String>()
        ids.addAll(inResult.keys)
        ids.addAll(extra.keys)
        ids.addAll(selected)
        ids.addAll(excluded)

        val values = ArrayList<FacetCount>()
        for (v in ids) {
            val isSel = v in selected
            val after = if (isSel) {
                val rest = selected - v
                if (rest.isEmpty()) base.size
                else base.count { w -> w.facets[axis]?.any { it in rest } == true }
            } else baseWithSelected + (extra[v] ?: 0)
            val isEx = v in excluded
            // Hide values that cannot change the result (0 results, or identical to the current result).
            if (isSel || isEx || (after > 0 && after != n)) {
                values += FacetCount(v, inResult[v] ?: 0, after, isSel, isEx)
            }
        }
        val sorted = values.sortedWith(
            compareByDescending<FacetCount> { it.selected }
                .thenByDescending { it.inResult }
                .thenBy { it.value },
        )
        return AxisView(axis, sorted, known, unknown)
    }

    /**
     * Ranks axes (not already narrowed by an include) by expected ability to shrink the results.
     * expected remaining fraction = mean size of the group each work falls into / n; axes with many
     * unknowns are penalised so we do not push the user toward data we mostly lack.
     * No fixed order: the ranking changes with the selection and with the loaded data.
     */
    fun suggest(sel: Selection, current: FilterResult = evaluate(sel)): List<AxisSuggestion> {
        val n = current.matched.size
        if (n < 2) return emptyList()
        val out = ArrayList<AxisSuggestion>()
        for (ax in taxonomy.axes) {
            if (sel.include[ax.id]?.isNotEmpty() == true) continue
            val counts = HashMap<String, Int>()
            var known = 0
            for (w in current.matched) {
                val vs = w.facets[ax.id] ?: continue
                known++
                for (v in vs) counts[v] = (counts[v] ?: 0) + 1
            }
            val usefulIds = counts.filter { it.value in 1 until n }.keys
            if (usefulIds.isEmpty()) continue
            // For each work: size of the group it would fall into (average over its useful values);
            // works with no useful value stay at n. Lower expected size = better question.
            var total = 0.0
            for (w in current.matched) {
                val mine = w.facets[ax.id].orEmpty().filter { it in usefulIds }
                total += if (mine.isEmpty()) n.toDouble() else mine.sumOf { counts.getValue(it) }.toDouble() / mine.size
            }
            val expected = total / n / n
            val coverage = known.toDouble() / n
            out += AxisSuggestion(ax.id, expected + (1.0 - coverage) * 0.5, usefulIds.size, coverage)
        }
        val order = taxonomy.axes.map { it.id }
        return out.sortedWith(compareBy<AxisSuggestion> { it.score }.thenBy { order.indexOf(it.axis) })
    }

    fun outcome(sel: Selection, current: FilterResult = evaluate(sel)): Outcome {
        val m = current.matched
        return when {
            works.isEmpty() -> Outcome.PoolEmpty
            m.isEmpty() ->
                if (current.possible.isNotEmpty()) Outcome.OnlyPossible(current.possible.size)
                else Outcome.NoResults(poolComplete)
            m.size == 1 -> Outcome.Single(m[0], reasonsFor(m[0], sel), current.possible.size)
            else -> {
                val s = suggest(sel, current)
                if (s.isEmpty()) Outcome.Indistinguishable(m) else Outcome.Many(m.size, s)
            }
        }
    }

    /** Verified reasons why [w] matches. */
    fun reasonsFor(w: Work, sel: Selection): List<Condition> {
        val out = ArrayList<Condition>()
        for ((axis, wanted) in sel.include) for (v in wanted.sorted()) if (w.has(axis, v)) out += Condition(axis, v, false)
        for ((axis, banned) in sel.exclude) {
            val have = w.facets[axis] ?: continue
            for (v in banned.sorted()) if (v !in have) out += Condition(axis, v, true)
        }
        return out
    }
}
