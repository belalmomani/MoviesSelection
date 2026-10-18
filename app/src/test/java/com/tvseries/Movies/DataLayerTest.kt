package com.tvseries.Movies

import com.tvseries.Movies.ads.AdPlacement
import com.tvseries.Movies.ads.AdPolicy
import com.tvseries.Movies.ads.AdsModule
import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.Taxonomy
import com.tvseries.Movies.data.DiscoverQuery
import com.tvseries.Movies.data.ListItemDto
import com.tvseries.Movies.data.MemoryStore
import com.tvseries.Movies.data.PageDto
import com.tvseries.Movies.data.Prefs
import com.tvseries.Movies.data.ServerRefine
import com.tvseries.Movies.data.TaxonomySeed
import com.tvseries.Movies.data.TmdbClient
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.WorkMapper
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLayerTest {
    @Test fun seedTaxonomyIsValid() {
        val issues = Taxonomy(TaxonomySeed.nodes()).validate()
        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test fun prefsPersistAndRestore() {
        val store = MemoryStore()
        val p1 = Prefs(store)
        p1.setLanguage("en"); p1.setTheme("dark"); p1.setShowAdult(true); p1.setMinVotes(500)
        p1.setHistoryEnabled(true); p1.addHistory("matrix")
        p1.toggleFavorite(WorkCard("movie", 603, "The Matrix", year = "1999"))
        p1.toggleWatchlist(WorkCard("tv", 1396, "Breaking Bad"))
        val sel = Selection().toggleInclude(Axes.GENRE, "genre.28").toggleExclude(Axes.COUNTRY, "country.US")
        p1.saveSelection(sel)

        val p2 = Prefs(store) // fresh instance over the same storage = app restart
        assertEquals("en", p2.language.value)
        assertEquals("dark", p2.theme.value)
        assertTrue(p2.showAdult.value)
        assertEquals(500, p2.minVotes.value)
        assertEquals(listOf("matrix"), p2.history.value)
        assertEquals("movie:603", p2.favorites.value.single().key)
        assertEquals("tv:1396", p2.watchlist.value.single().key)
        assertEquals(sel, p2.loadSelection())
    }

    @Test fun intentAndTastePersist() {
        val store = MemoryStore()
        val p1 = Prefs(store)
        val intent = com.tvseries.Movies.core.UserIntent(0.5, null, -0.3, Selection().toggleExclude(Axes.AVOID, "avoid.gore"))
        p1.setIntent(intent)
        p1.setTaste(com.tvseries.Movies.core.TasteProfile().update(listOf(35), "movie:9", true, emptySet()))
        val p2 = Prefs(store)
        assertEquals(intent, p2.intent.value)
        assertEquals(listOf("movie:9"), p2.taste.value.accepted)
        assertEquals(1.0, p2.taste.value.genre[35]!!, 1e-9)
    }

    @Test fun savedFiltersAndWatchedPersist() {
        val store = MemoryStore()
        val p1 = Prefs(store)
        val sel = Selection().toggleInclude(Axes.GENRE, "genre.35")
        p1.saveFilter("ليلة الجمعة", sel)
        p1.toggleWatched("movie:1")
        assertTrue(p1.hideWatched.value) // default on
        val p2 = Prefs(store)
        assertEquals(sel, p2.savedFilters.value.single().selection)
        assertEquals("ليلة الجمعة", p2.savedFilters.value.single().name)
        assertTrue("movie:1" in p2.watched.value)
        p2.deleteFilter("ليلة الجمعة")
        assertTrue(Prefs(store).savedFilters.value.isEmpty())
    }

    @Test fun arabSelectionSplitsIntoCountryQueriesWithLowVoteThreshold() {
        val t = Taxonomy(TaxonomySeed.nodes())
        val sel = Selection().toggleInclude(Axes.COUNTRY, "region.arab")
        val qs = ServerRefine.refineAll(sel, t, "tv", DiscoverQuery(minVotes = 100))
        assertTrue(qs.size > 2)
        assertTrue(qs.all { it.minVotes <= 5 })
        assertTrue(qs.any { it.language == "ar" && it.countries.isEmpty() })
        assertTrue(qs.filter { it.countries.isNotEmpty() }.all { it.countries.size == 1 })
        // a non-Arab selection keeps the normal vote threshold
        val kr = ServerRefine.refineAll(Selection().toggleInclude(Axes.COUNTRY, "country.KR"), t, "tv", DiscoverQuery(minVotes = 100))
        assertEquals(100, kr.single().minVotes)
    }

    @Test fun countryStampedWhenQueryHasSingleCountry() {
        val pool = com.tvseries.Movies.data.WorkPool()
        pool.addListItems("movie", listOf(ListItemDto(id = 7, title = "x", genreIds = listOf(18))), "JO")
        val w = pool.snapshot().works.single()
        assertTrue("country.JO" in w.facets[Axes.COUNTRY]!!)
        assertTrue("region.arab" in w.facets[Axes.COUNTRY]!!) // parent via hierarchy
    }

    @Test fun cinemaWorldsFromGenresAndKeywords() {
        val w = com.tvseries.Movies.data.Worlds
        assertTrue("world.crime_mystery.detective" in w.tags(listOf(80, 9648), null))
        assertFalse("world.crime_mystery.underworld" in w.tags(listOf(80, 9648), null))
        assertTrue("world.future_sf.time_travel" in w.tags(listOf(878), listOf("Time Travel")))
        assertFalse("world.future_sf.time_travel" in w.tags(listOf(878), null)) // unknown keywords -> not tagged
        val work = WorkMapper.fromListItem("movie", ListItemDto(id = 1, title = "x", genreIds = listOf(35, 10749))).work
        assertTrue("world.comedy_light.romcom" in work.facets[Axes.WORLD]!!)
        val t = Taxonomy(TaxonomySeed.nodes())
        assertTrue("world.comedy_light" in t.closure(setOf("world.comedy_light.romcom")))
    }

    @Test fun historyIsOffByDefault() {
        val p = Prefs(MemoryStore())
        p.addHistory("something")
        assertTrue(p.history.value.isEmpty())
        assertFalse(p.historyEnabled.value)
    }

    @Test fun listItemMappingKeepsUnknownAsUnknown() {
        val movie = WorkMapper.fromListItem(
            "movie",
            ListItemDto(id = 1, title = "M", releaseDate = "1999-03-31", genreIds = listOf(28), originalLanguage = "en", voteAverage = 8.2, voteCount = 5000),
        ).work
        assertNull(movie.facets[Axes.COUNTRY]) // TMDB list data has no country for movies -> unknown
        assertEquals(setOf("genre.28"), movie.facets[Axes.GENRE])
        assertEquals(setOf("year.1999"), movie.facets[Axes.YEAR])
        assertEquals(setOf("rating.8"), movie.facets[Axes.RATING])
        assertEquals(emptySet<String>(), movie.facets[Axes.SEASONS]) // not applicable to films

        val tv = WorkMapper.fromListItem(
            "tv",
            ListItemDto(id = 2, name = "T", firstAirDate = "2016-01-01", genreIds = listOf(16), originalLanguage = "ja", originCountry = listOf("JP"), voteAverage = 3.0, voteCount = 2),
        ).work
        assertEquals(setOf("country.JP"), tv.facets[Axes.COUNTRY])
        assertTrue("format.anime" in tv.facets[Axes.FORMAT]!!)
        assertNull(tv.facets[Axes.RATING]) // too few votes -> unknown
    }

    @Test fun serverRefineBuildsDiscoverQuery() {
        val t = Taxonomy(TaxonomySeed.nodes())
        val sel = Selection().toggleInclude(Axes.GENRE, "genre.27").toggleInclude(Axes.COUNTRY, "country.KR")
            .toggleInclude(Axes.YEAR, "decade.2010").toggleInclude(Axes.RATING, "rating.7")
        val q = ServerRefine.refine(sel, t, "movie", DiscoverQuery())!!
        assertEquals(setOf(27), q.genres)
        assertEquals(setOf("KR"), q.countries)
        assertEquals(2010, q.yearFrom); assertEquals(2019, q.yearTo)
        assertEquals(7, q.minRating)
        // genre that only exists for TV cannot match movies
        val tvOnly = Selection().toggleInclude(Axes.GENRE, "genre.10762")
        assertNull(ServerRefine.refine(tvOnly, t, "movie", DiscoverQuery()))
        assertNotNull(ServerRefine.refine(tvOnly, t, "tv", DiscoverQuery()))
    }

    @Test fun adsNeverShownWhileFilteringAndNoInterstitials() {
        assertFalse(AdPolicy.allowed(AdPlacement.HOME_BANNER, userIsFiltering = true))
        assertFalse(AdPolicy.allowed(AdPlacement.INTERSTITIAL_BETWEEN_SESSIONS, userIsFiltering = false))
        assertTrue(AdPolicy.allowed(AdPlacement.HOME_BANNER, userIsFiltering = false))
        assertTrue(AdPolicy.allowed(AdPlacement.DETAILS_BANNER, userIsFiltering = false))
    }

    @Test fun moodTensionAndKeywordDerivedTags() {
        val d = com.tvseries.Movies.data.DetailsDto(
            id = 9, title = "T", genres = listOf(com.tvseries.Movies.data.GenreDto(35, "Comedy")), releaseDate = "2001-01-01",
            keywords = com.tvseries.Movies.data.KeywordsDto(
                keywords = listOf(
                    com.tvseries.Movies.data.KeywordDto(1, "happy ending"),
                    com.tvseries.Movies.data.KeywordDto(2, "High School"),
                ),
            ),
        )
        val w = WorkMapper.fromDetails("movie", d).work
        assertEquals(setOf("mood.laugh"), w.facets[Axes.MOOD])
        assertEquals(setOf("tension.low"), w.facets[Axes.TENSION])
        assertEquals(setOf("ending.happy"), w.facets[Axes.ENDING])
        assertEquals(setOf("avoid.school"), w.facets[Axes.AVOID])
        // keywords known but none matches: ending stays UNKNOWN, avoid is known-empty
        val plain = WorkMapper.fromDetails(
            "movie",
            d.copy(keywords = com.tvseries.Movies.data.KeywordsDto(keywords = listOf(com.tvseries.Movies.data.KeywordDto(3, "robot")))),
        ).work
        assertNull(plain.facets[Axes.ENDING])
        assertEquals(emptySet<String>(), plain.facets[Axes.AVOID])
        // list-level data has no keywords -> both unknown
        val list = WorkMapper.fromListItem("movie", ListItemDto(id = 1, title = "x", genreIds = listOf(27, 53))).work
        assertNull(list.facets[Axes.AVOID])
        assertEquals(setOf("tension.high"), list.facets[Axes.TENSION])
    }

    @Test fun watchProvidersParse() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val d = json.decodeFromString(
            com.tvseries.Movies.data.DetailsDto.serializer(),
            """{"id":1,"title":"x","watch/providers":{"results":{"JO":{"link":"https://x","flatrate":[{"provider_name":"Netflix"}]}}}}""",
        )
        assertEquals("Netflix", d.watchProviders!!.results["JO"]!!.flatrate!!.single().name)
    }

    @Test fun avoidOptionsExcludeAndRestore() {
        val opt = com.tvseries.Movies.ui.Presets.avoid.first { it.id == "no_long" }
        val on = opt.toggle(Selection())
        assertTrue(opt.isOn(on))
        assertTrue(on.isExcluded(Axes.RUNTIME, "runtime.m.o150"))
        assertTrue(opt.toggle(on).isEmpty)
    }

    // ---- TMDB client against a local mock server (no real network / key needed) ----

    private fun client(server: MockWebServer, key: String = "k") =
        TmdbClient(OkHttpClient(), { key }, server.url("/3/").toString())

    @Test fun wikidataQueryAndParsing() = runBlocking {
        val s = MockWebServer()
        s.enqueue(MockResponse().setBody("""{"results":{"bindings":[{"tmdb":{"value":"123"}},{"tmdb":{"value":"456"}},{"tmdb":{"value":"abc"}}]}}"""))
        s.start()
        val wd = com.tvseries.Movies.data.WikidataClient(OkHttpClient(), s.url("/sparql").toString())
        val ids = wd.tmdbIds("tv", setOf("EG", "JO"), 0, 20)
        assertEquals(listOf(123, 456), ids)
        val q = wd.buildQuery("tv", setOf("EG", "JO"), 40, 20)
        assertTrue(q.contains("P4983") && q.contains("\"EG\" \"JO\"") && q.contains("OFFSET 40"))
        assertTrue(wd.buildQuery("movie", setOf("EG"), 0, 5).contains("P4947"))
        val req = s.takeRequest()
        assertTrue(req.getHeader("User-Agent")!!.startsWith("MoviesSelection"))
        s.enqueue(MockResponse().setResponseCode(500))
        try {
            wd.tmdbIds("tv", setOf("EG"), 0, 20)
            error("expected failure")
        } catch (e: com.tvseries.Movies.data.WikidataException) {
            assertTrue(e.message!!.contains("500"))
        }
        s.shutdown()
    }

    @Test fun clientParsesPage() = runBlocking {
        val s = MockWebServer()
        s.enqueue(MockResponse().setBody("""{"page":1,"total_pages":3,"results":[{"id":5,"title":"X","genre_ids":[28],"unknown_field":1}]}"""))
        s.start()
        val page = client(s).get("discover/movie", mapOf("page" to "1"), PageDto.serializer())
        assertEquals(3, page.totalPages)
        assertEquals(5, page.results.single().id)
        assertTrue(s.takeRequest().path!!.contains("api_key=k"))
        s.shutdown()
    }

    @Test fun clientMapsErrors() = runBlocking {
        suspend fun kind(code: Int, key: String = "k"): TmdbError.Kind {
            val s = MockWebServer()
            s.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
            if (code == 429) s.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("{}"))
            s.start()
            return try {
                client(s, key).get("x", emptyMap(), PageDto.serializer())
                error("expected failure")
            } catch (e: TmdbError) {
                e.kind
            } finally {
                s.shutdown()
            }
        }
        assertEquals(TmdbError.Kind.UNAUTHORIZED, kind(401))
        assertEquals(TmdbError.Kind.NOT_FOUND, kind(404))
        assertEquals(TmdbError.Kind.SERVER, kind(500))
        assertEquals(TmdbError.Kind.NO_KEY, kind(200, key = ""))
    }

    @Test fun clientReportsParseErrorsAndOffline() = runBlocking {
        val s = MockWebServer()
        s.enqueue(MockResponse().setBody("not json"))
        s.start()
        try {
            client(s).get("x", emptyMap(), PageDto.serializer())
            error("expected failure")
        } catch (e: TmdbError) {
            assertEquals(TmdbError.Kind.PARSE, e.kind)
        }
        val url = s.url("/3/").toString()
        s.shutdown() // server gone -> connection refused
        try {
            TmdbClient(OkHttpClient(), { "k" }, url).get("x", emptyMap(), PageDto.serializer())
            error("expected failure")
        } catch (e: TmdbError) {
            assertEquals(TmdbError.Kind.NETWORK, e.kind)
        }
    }
}
