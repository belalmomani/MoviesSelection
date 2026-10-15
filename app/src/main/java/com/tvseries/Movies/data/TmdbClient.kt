package com.tvseries.Movies.data

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class TmdbError(val kind: Kind, message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    enum class Kind { NO_KEY, NETWORK, UNAUTHORIZED, RATE_LIMIT, NOT_FOUND, SERVER, PARSE }
}

/**
 * Minimal TMDB v3/v4 client on OkHttp.
 *  - a v3 "API key" is sent as ?api_key=...
 *  - a v4 "Read Access Token" (starts with "eyJ") is sent as a Bearer header
 *  - responses are cached on disk; on network failure the cached copy (up to 7 days) is used
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val keyProvider: () -> String,
    private val baseUrl: String = "https://api.themoviedb.org/3/",
) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun <T> get(path: String, params: Map<String, String>, strategy: DeserializationStrategy<T>): T {
        val key = keyProvider().trim()
        if (key.isEmpty()) throw TmdbError(TmdbError.Kind.NO_KEY, "TMDB API key is not set")

        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).apply {
            for ((k, v) in params) addQueryParameter(k, v)
            if (!key.startsWith("eyJ")) addQueryParameter("api_key", key)
        }.build()
        val builder = Request.Builder().url(url).get()
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer $key")
        val request = builder.build()

        var response = try {
            execute(request)
        } catch (e: IOException) {
            // Offline / DNS failure: fall back to a stale cached copy if we have one.
            val cached = request.newBuilder()
                .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build())
                .build()
            val r = try { execute(cached) } catch (ignored: IOException) { null }
            if (r == null || r.code == 504) {
                r?.close()
                throw TmdbError(TmdbError.Kind.NETWORK, "No network connection", cause = e)
            }
            r
        }

        if (response.code == 429) {
            val wait = (response.header("Retry-After")?.toLongOrNull() ?: 1L).coerceIn(1L, 5L)
            response.close()
            delay(wait * 1000)
            response = try {
                execute(request)
            } catch (e: IOException) {
                throw TmdbError(TmdbError.Kind.NETWORK, "No network connection", cause = e)
            }
        }

        return response.use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> try {
                    json.decodeFromString(strategy, body)
                } catch (e: Exception) {
                    throw TmdbError(TmdbError.Kind.PARSE, "Unexpected response format", r.code, e)
                }
                r.code == 401 -> throw TmdbError(TmdbError.Kind.UNAUTHORIZED, "Invalid TMDB API key", 401)
                r.code == 404 -> throw TmdbError(TmdbError.Kind.NOT_FOUND, "Not found", 404)
                r.code == 429 -> throw TmdbError(TmdbError.Kind.RATE_LIMIT, "Rate limit reached", 429)
                else -> throw TmdbError(TmdbError.Kind.SERVER, "Server error ${r.code}", r.code)
            }
        }
    }

    private suspend fun execute(request: Request): Response = http.newCall(request).await()

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

    companion object {
        fun buildHttp(cacheDir: File?): OkHttpClient {
            val b = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
            if (cacheDir != null) {
                b.cache(Cache(File(cacheDir, "tmdb_http"), 20L * 1024 * 1024))
                // Cache successful GETs for an hour so repeated screens do not re-hit TMDB.
                b.addNetworkInterceptor { chain ->
                    val resp = chain.proceed(chain.request())
                    if (resp.isSuccessful) {
                        resp.newBuilder()
                            .removeHeader("Pragma")
                            .header("Cache-Control", "public, max-age=3600")
                            .build()
                    } else resp
                }
            }
            return b.build()
        }
    }
}
