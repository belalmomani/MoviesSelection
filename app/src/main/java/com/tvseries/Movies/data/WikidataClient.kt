package com.tvseries.Movies.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
data class SparqlValue(val value: String = "")

@Serializable
data class SparqlResults(val bindings: List<Map<String, SparqlValue>> = emptyList())

@Serializable
data class SparqlResponse(val results: SparqlResults = SparqlResults())

class WikidataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Discovery source for Arab productions: Wikidata (CC0, free, no key). It only returns TMDB ids; all details,
 * images and facts still come from TMDB, so nothing is invented. Wikidata coverage is partial.
 */
class WikidataClient(
    private val http: OkHttpClient,
    private val endpoint: String = "https://query.wikidata.org/sparql",
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** P4947 = TMDB movie id, P4983 = TMDB TV series id, P495 = country of origin, P297 = ISO 3166-1 alpha-2. */
    fun buildQuery(media: String, iso: Set<String>, offset: Int, limit: Int): String {
        val prop = if (media == "movie") "P4947" else "P4983"
        val values = iso.sorted().joinToString(" ") { "\"$it\"" }
        return """
            SELECT ?tmdb (MAX(?links) AS ?l) WHERE {
              VALUES ?iso { $values }
              ?country wdt:P297 ?iso .
              ?item wdt:P495 ?country .
              ?item wdt:$prop ?tmdb .
              ?item wikibase:sitelinks ?links .
            } GROUP BY ?tmdb ORDER BY DESC(?l) OFFSET $offset LIMIT $limit
        """.trimIndent()
    }

    suspend fun tmdbIds(media: String, iso: Set<String>, offset: Int, limit: Int): List<Int> {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("query", buildQuery(media, iso, offset, limit))
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "MoviesSelection/1.0 (Android app; Wikidata SPARQL reader)")
            .header("Accept", "application/sparql-results+json")
            .get().build()
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw WikidataException("Wikidata is unreachable", e)
        }
        return response.use { r ->
            if (!r.isSuccessful) throw WikidataException("Wikidata error ${r.code}")
            val body = r.body?.string().orEmpty()
            val parsed = try {
                json.decodeFromString(SparqlResponse.serializer(), body)
            } catch (e: Exception) {
                throw WikidataException("Unexpected Wikidata response", e)
            }
            parsed.results.bindings.mapNotNull { it["tmdb"]?.value?.toIntOrNull() }.distinct()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}
