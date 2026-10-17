package com.tvseries.Movies

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.FilterEngine
import com.tvseries.Movies.core.FilterState
import com.tvseries.Movies.core.Outcome
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.core.TaxonomyNode
import com.tvseries.Movies.core.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private fun node(id: String, axis: String, parents: Set<String> = emptySet()) = TaxonomyNode(id, axis, id, id, parents)

    private val taxonomy = Taxonomy(
        listOf(
            node("g.action", Axes.GENRE), node("g.crime", Axes.GENRE), node("g.drama", Axes.GENRE), node("g.horror", Axes.GENRE),
            node("c.us", Axes.COUNTRY), node("c.kr", Axes.COUNTRY), node("c.jp", Axes.COUNTRY),
            node("l.en", Axes.LANGUAGE), node("l.ko", Axes.LANGUAGE), node("l.ja", Axes.LANGUAGE),
            node("decade.1990", Axes.YEAR), node("year.1995", Axes.YEAR, setOf("decade.1990")), node("year.1999", Axes.YEAR, setOf("decade.1990")),
        ),
    )

    private fun w(key: String, genre: Set<String>? = null, country: Set<String>? = null, lang: Set<String>? = null, year: Set<String>? = null): Work {
        val f = LinkedHashMap<String, Set<String>>()
        genre?.let { f[Axes.GENRE] = it }
        country?.let { f[Axes.COUNTRY] = it }
        lang?.let { f[Axes.LANGUAGE] = it }
        year?.let { f[Axes.YEAR] = it }
        return Work(key, f).expanded(taxonomy)
    }

    private val works = listOf(
        w("a", setOf("g.action", "g.crime"), setOf("c.us"), setOf("l.en"), setOf("year.1995")),
        w("b", setOf("g.drama"), setOf("c.kr"), setOf("l.ko"), setOf("year.1999")),
        w("c", setOf("g.action"), setOf("c.kr"), setOf("l.ko"), setOf("year.1999")),
        w("d", setOf("g.horror"), setOf("c.jp"), setOf("l.ja"), setOf("year.1995")),
        w("e", setOf("g.action"), null, setOf("l.en"), setOf("year.1995")), // country unknown
    )
    private val engine = FilterEngine(works, taxonomy)

    private fun keys(sel: Selection) = engine.evaluate(sel).matched.map { it.key }.toSet()

    @Test fun combinesConditions_orInsideAxis_andBetweenAxes() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.action").toggleInclude(Axes.COUNTRY, "c.kr")
        assertEquals(setOf("c"), keys(sel))
    }

    @Test fun multipleGenres() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.drama").toggleInclude(Axes.GENRE, "g.horror")
        assertEquals(setOf("b", "d"), keys(sel))
    }

    @Test fun multipleCountries() {
        val sel = Selection().toggleInclude(Axes.COUNTRY, "c.kr").toggleInclude(Axes.COUNTRY, "c.jp")
        assertEquals(setOf("b", "c", "d"), keys(sel))
    }

    @Test fun languageGenreCountryTogether() {
        val sel = Selection().toggleInclude(Axes.LANGUAGE, "l.ko").toggleInclude(Axes.GENRE, "g.drama").toggleInclude(Axes.COUNTRY, "c.kr")
        assertEquals(setOf("b"), keys(sel))
    }

    @Test fun exclusion() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.action").toggleExclude(Axes.COUNTRY, "c.us")
        assertEquals(setOf("c"), keys(sel)) // "e" has unknown country -> possible, not matched
        assertEquals(setOf("e"), engine.evaluate(sel).possible.map { it.key }.toSet())
    }

    @Test fun parentSelectionMatchesChildren() {
        val sel = Selection().toggleInclude(Axes.YEAR, "decade.1990")
        assertEquals(setOf("a", "b", "c", "d", "e"), keys(sel))
    }

    @Test fun undoRestoresPreviousSelection() {
        var s = FilterState()
        s = s.apply(s.selection.toggleInclude(Axes.GENRE, "g.action"))
        s = s.apply(s.selection.toggleInclude(Axes.COUNTRY, "c.kr"))
        assertEquals(setOf("c"), keys(s.selection))
        s = s.back()
        assertEquals(setOf("a", "c", "e"), keys(s.selection))
        assertTrue(s.canGoBack)
        s = s.back()
        assertFalse(s.canGoBack)
        assertTrue(s.selection.isEmpty)
    }

    @Test fun removingAnEarlierConditionWithoutRestarting() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.action").toggleInclude(Axes.COUNTRY, "c.kr")
        val removed = sel.remove(sel.conditions().first { it.axis == Axes.GENRE })
        assertEquals(setOf("b", "c"), keys(removed))
    }

    @Test fun missingDataIsPossibleNotMismatch() {
        val sel = Selection().toggleInclude(Axes.COUNTRY, "c.us")
        val r = engine.evaluate(sel)
        assertEquals(setOf("a"), r.matched.map { it.key }.toSet())
        assertEquals(setOf("e"), r.possible.map { it.key }.toSet())
    }

    @Test fun trueZeroResults() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.horror").toggleInclude(Axes.COUNTRY, "c.kr")
        val o = engine.outcome(sel)
        assertTrue(o is Outcome.NoResults)
    }

    @Test fun zeroConfirmedButPossible() {
        val only = FilterEngine(listOf(w("x", setOf("g.action"), null, setOf("l.en"), setOf("year.1995"))), taxonomy)
        val o = only.outcome(Selection().toggleInclude(Axes.COUNTRY, "c.us"))
        assertTrue(o is Outcome.OnlyPossible)
        assertEquals(1, (o as Outcome.OnlyPossible).possibleCount)
    }

    @Test fun emptyPoolIsNotZeroResults() {
        assertTrue(FilterEngine(emptyList(), taxonomy).outcome(Selection()) is Outcome.PoolEmpty)
    }

    @Test fun singleResultWithReasons() {
        val sel = Selection().toggleInclude(Axes.GENRE, "g.drama")
        val o = engine.outcome(sel)
        assertTrue(o is Outcome.Single)
        o as Outcome.Single
        assertEquals("b", o.work.key)
        assertTrue(o.reasons.any { it.axis == Axes.GENRE && it.value == "g.drama" })
    }

    @Test fun indistinguishableWorksAreAllShown() {
        val a = w("p", setOf("g.action"), setOf("c.us"), setOf("l.en"), setOf("year.1995"))
        val b = w("q", setOf("g.action"), setOf("c.us"), setOf("l.en"), setOf("year.1995"))
        val o = FilterEngine(listOf(a, b), taxonomy).outcome(Selection())
        assertTrue(o is Outcome.Indistinguishable)
        assertEquals(2, (o as Outcome.Indistinguishable).works.size)
    }

    @Test fun axesAreRankedByUsefulness() {
        // language splits 50/50-ish; genre has one rare value; country is useless (identical for all)
        val ws = listOf(
            w("1", setOf("g.action"), setOf("c.us"), setOf("l.en")),
            w("2", setOf("g.action"), setOf("c.us"), setOf("l.en")),
            w("3", setOf("g.action"), setOf("c.us"), setOf("l.ko")),
            w("4", setOf("g.action", "g.crime"), setOf("c.us"), setOf("l.ko")),
        )
        val s = FilterEngine(ws, taxonomy).suggest(Selection())
        val axes = s.map { it.axis }
        assertFalse(Axes.COUNTRY in axes)
        assertEquals(Axes.LANGUAGE, axes.first())
        assertTrue(Axes.GENRE in axes)
    }

    @Test fun axisViewHidesValuesThatCannotChangeResult() {
        val view = engine.axisView(Selection(), Axes.GENRE)
        val ids = view.values.map { it.value }
        assertTrue("g.drama" in ids)
        val sel = Selection().toggleInclude(Axes.COUNTRY, "c.kr")
        val v2 = engine.axisView(sel, Axes.COUNTRY)
        val kr = v2.values.first { it.value == "c.kr" }
        assertTrue(kr.selected)
        assertEquals(2, kr.inResult)
        // adding another country would widen the result
        assertEquals(3, v2.values.first { it.value == "c.jp" }.afterInclude)
    }

    @Test fun selectionSerializationRoundTrip() {
        val json = kotlinx.serialization.json.Json
        val sel = Selection().toggleInclude(Axes.GENRE, "g.action").toggleExclude(Axes.COUNTRY, "c.us")
        val text = json.encodeToString(Selection.serializer(), sel)
        assertEquals(sel, json.decodeFromString(Selection.serializer(), text))
    }

    @Test fun taxonomyValidatesAndRejectsCycles() {
        assertTrue(taxonomy.validate().isEmpty())
        val bad = Taxonomy(listOf(node("x", Axes.GENRE, setOf("y")), node("y", Axes.GENRE, setOf("x"))))
        assertTrue(bad.validate().any { it.startsWith("cycle") })
    }

    @Test fun partyModeIsIntersectionNotUnion() {
        val a = Selection().toggleInclude(Axes.GENRE, "g.action")      // person 1
        val b = Selection().toggleInclude(Axes.COUNTRY, "c.kr")        // person 2
        val r = com.tvseries.Movies.core.PartyMatcher.intersect(works, a, b)
        assertEquals(setOf("c"), r.confirmed.map { it.key }.toSet()) // action AND Korea
        assertEquals(setOf("e"), r.possible.map { it.key }.toSet())  // action, country unknown
        // person 2 dislikes Korea: only works not known to be Korean remain
        val c = Selection().toggleExclude(Axes.COUNTRY, "c.kr")
        val r2 = com.tvseries.Movies.core.PartyMatcher.intersect(works, a, c)
        assertEquals(setOf("a"), r2.confirmed.map { it.key }.toSet())
    }

    @Test fun hiddenGemsNeedVotesAndAreNotRankedByRatingAlone() {
        val g = com.tvseries.Movies.core.GemFinder
        assertTrue(g.isGem(7.8, 8000))
        assertFalse(g.isGem(7.9, 400000)) // too popular
        assertFalse(g.isGem(9.0, 20))     // too few votes
        assertTrue(g.score(7.8, 8000) > g.score(9.0, 20))
    }

    @Test fun intersectAllFindsSharedItems() {
        assertEquals(setOf(2), com.tvseries.Movies.core.intersectAll(listOf(setOf(1, 2), setOf(2, 3), setOf(2))))
        assertTrue(com.tvseries.Movies.core.intersectAll(emptyList<Set<Int>>()).isEmpty())
    }
}
