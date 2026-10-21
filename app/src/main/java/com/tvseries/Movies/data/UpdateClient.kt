package com.tvseries.Movies.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ReleaseAsset(
    val name: String = "",
    val url: String = "",
    @SerialName("browser_download_url") val browserUrl: String = "",
)

@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

data class UpdateInfo(val build: Int, val tag: String, val apiUrl: String, val browserUrl: String)

class UpdateException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NO_REPO, NOT_FOUND, UNAUTHORIZED, NETWORK, PARSE, NO_ASSET }
}

object Updates {
    /** Release tags look like "build-123". */
    fun buildOf(tag: String): Int? = tag.removePrefix("build-").toIntOrNull()
    fun isNewer(remote: Int, local: Int): Boolean = remote > local
}

/**
 * Checks GitHub Releases for a newer build and downloads its APK. A private repository needs a token
 * (stored only on this device). Installing works only if the new APK has the same signing key.
 */
class UpdateClient(
    private val http: OkHttpClient,
    private val repo: String,
    private val tokenProvider: () -> String,
    private val apiBase: String = "https://api.github.com/",
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun request(url: String, accept: String): Request.Builder {
        val b = Request.Builder().url(url.toHttpUrl()).header("Accept", accept).header("User-Agent", "MoviesSelection-updater")
        val token = tokenProvider().trim()
        if (token.isNotEmpty()) b.header("Authorization", "Bearer $token")
        return b
    }

    /** Latest published build, or null when the repository has no release yet. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank()) throw UpdateException(UpdateException.Kind.NO_REPO, "No update repository configured")
        val req = request("${apiBase}repos/$repo/releases/latest", "application/vnd.github+json").get().build()
        try {
            http.newCall(req).execute().use { r ->
                when {
                    r.code == 401 || r.code == 403 -> throw UpdateException(UpdateException.Kind.UNAUTHORIZED, "Not authorised")
                    r.code == 404 -> throw UpdateException(UpdateException.Kind.NOT_FOUND, "Release not found (private repo needs a token)")
                    !r.isSuccessful -> throw UpdateException(UpdateException.Kind.NETWORK, "HTTP ${r.code}")
                }
                val body = r.body?.string().orEmpty()
                val rel = try {
                    json.decodeFromString(ReleaseDto.serializer(), body)
                } catch (e: Exception) {
                    throw UpdateException(UpdateException.Kind.PARSE, "Unexpected response", e)
                }
                val build = Updates.buildOf(rel.tag) ?: return@use null
                val asset = rel.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: throw UpdateException(UpdateException.Kind.NO_ASSET, "Release has no APK")
                UpdateInfo(build, rel.tag, asset.url, asset.browserUrl)
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateException.Kind.NETWORK, "No connection", e)
        }
    }

    /** Streams the APK into [dest]. Returns when finished; [onProgress] gets 0..100. */
    suspend fun download(info: UpdateInfo, dest: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val hasToken = tokenProvider().isNotBlank()
        val req = if (hasToken) request(info.apiUrl, "application/octet-stream").get().build()
        else Request.Builder().url(info.browserUrl.toHttpUrl()).header("User-Agent", "MoviesSelection-updater").get().build()
        dest.parentFile?.mkdirs()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw UpdateException(UpdateException.Kind.NETWORK, "HTTP ${r.code}")
                val body = r.body ?: throw UpdateException(UpdateException.Kind.NETWORK, "Empty download")
                val total = body.contentLength()
                var done = 0L
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateException.Kind.NETWORK, "Download failed", e)
        }
    }
}
