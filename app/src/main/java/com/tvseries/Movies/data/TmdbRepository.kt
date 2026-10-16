package com.tvseries.Movies.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TmdbRepository(private val client: TmdbClient, private val prefs: Prefs) {
    private val uiLang: String get() = if (prefs.language.value == "ar") "ar" else "en-US"

    private val detailsCache = object : LinkedHashMap<String, DetailsDto>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DetailsDto>?): Boolean = size > 80
    }
    private val cacheLock = Mutex()

    fun baseQuery(): DiscoverQuery =
        DiscoverQuery(minVotes = prefs.minVotes.value, includeAdult = prefs.showAdult.value)

    suspend fun discover(media: String, page: Int, q: DiscoverQuery): PageDto =
        client.get("discover/$media", q.params(media, page, uiLang), PageDto.serializer())

    /** path examples: movie/popular, tv/popular, movie/top_rated, movie/now_playing, tv/on_the_air, trending/all/week */
    suspend fun list(path: String, page: Int = 1): PageDto =
        client.get(path, mapOf("language" to uiLang, "page" to page.toString()), PageDto.serializer())

    suspend fun search(query: String, page: Int = 1): PageDto =
        client.get(
            "search/multi",
            mapOf(
                "query" to query,
                "language" to uiLang,
                "page" to page.toString(),
                "include_adult" to prefs.showAdult.value.toString(),
            ),
            PageDto.serializer(),
        )

    suspend fun details(media: String, id: Int): DetailsDto {
        val key = "$media:$id:$uiLang"
        cacheLock.withLock { detailsCache[key] }?.let { return it }
        val params = mapOf("language" to uiLang, "append_to_response" to "credits,keywords,similar,watch/providers")
        var d = client.get("$media/$id", params, DetailsDto.serializer())
        if (d.overview.isNullOrBlank() && uiLang != "en-US") {
            // Many titles have no Arabic overview; fall back to the English one instead of showing nothing.
            val en = runCatching { client.get("$media/$id", mapOf("language" to "en-US"), DetailsDto.serializer()) }.getOrNull()
            if (en != null && !en.overview.isNullOrBlank()) d = d.copy(overview = en.overview)
        }
        cacheLock.withLock { detailsCache[key] = d }
        return d
    }

    suspend fun season(tvId: Int, season: Int): SeasonDetailDto =
        client.get("tv/$tvId/season/$season", mapOf("language" to uiLang), SeasonDetailDto.serializer())

    suspend fun person(id: Int): PersonDto =
        client.get("person/$id", mapOf("language" to uiLang, "append_to_response" to "combined_credits"), PersonDto.serializer())
}
