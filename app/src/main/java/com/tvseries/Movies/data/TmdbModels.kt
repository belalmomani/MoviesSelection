package com.tvseries.Movies.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val page: Int = 1,
    val results: List<ListItemDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

/** One item of a list/search/credits response (movie, tv or person). Every field may be missing. */
@Serializable
data class ListItemDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("origin_country") val originCountry: List<String>? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    val adult: Boolean? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    // credits-only fields
    val character: String? = null,
    val job: String? = null,
    val order: Int? = null,
)

@Serializable
data class GenreDto(val id: Int = 0, val name: String = "")

@Serializable
data class CountryDto(
    @SerialName("iso_3166_1") val iso: String = "",
    val name: String = "",
)

@Serializable
data class SpokenLangDto(
    @SerialName("iso_639_1") val iso: String = "",
    @SerialName("english_name") val englishName: String? = null,
    val name: String? = null,
)

@Serializable
data class CompanyDto(val id: Int = 0, val name: String = "")

@Serializable
data class CollectionDto(val id: Int = 0, val name: String = "")

@Serializable
data class CastDto(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int? = null,
)

@Serializable
data class CrewDto(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class CreditsDto(val cast: List<CastDto>? = null, val crew: List<CrewDto>? = null)

@Serializable
data class KeywordDto(val id: Int = 0, val name: String = "")

/** Movies return {keywords:[...]}, TV returns {results:[...]}. */
@Serializable
data class KeywordsDto(val keywords: List<KeywordDto>? = null, val results: List<KeywordDto>? = null) {
    fun all(): List<KeywordDto> = keywords ?: results ?: emptyList()
}

@Serializable
data class SeasonDto(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class SeasonDetailDto(val episodes: List<EpisodeDto> = emptyList())

@Serializable
data class ProviderDto(
    @SerialName("provider_name") val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
)

@Serializable
data class RegionProvidersDto(
    val link: String? = null,
    val flatrate: List<ProviderDto>? = null,
    val rent: List<ProviderDto>? = null,
    val buy: List<ProviderDto>? = null,
)

@Serializable
data class WatchProvidersDto(val results: Map<String, RegionProvidersDto> = emptyMap())

/** Movie or TV details, with optional appended credits / keywords / similar. */
@Serializable
data class DetailsDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val genres: List<GenreDto>? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    val status: String? = null,
    val type: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val seasons: List<SeasonDto>? = null,
    @SerialName("production_countries") val productionCountries: List<CountryDto>? = null,
    @SerialName("origin_country") val originCountry: List<String>? = null,
    @SerialName("spoken_languages") val spokenLanguages: List<SpokenLangDto>? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("production_companies") val productionCompanies: List<CompanyDto>? = null,
    @SerialName("created_by") val createdBy: List<CrewDto>? = null,
    @SerialName("belongs_to_collection") val collection: CollectionDto? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val adult: Boolean? = null,
    val credits: CreditsDto? = null,
    val keywords: KeywordsDto? = null,
    val similar: PageDto? = null,
    @SerialName("watch/providers") val watchProviders: WatchProvidersDto? = null,
)

@Serializable
data class PersonDto(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    val birthday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    @SerialName("combined_credits") val combinedCredits: PersonCreditsDto? = null,
)

@Serializable
data class PersonCreditsDto(
    val cast: List<ListItemDto> = emptyList(),
    val crew: List<ListItemDto> = emptyList(),
)

/** Display model for a title card. Also what favorites / watchlist persist. */
@Serializable
data class WorkCard(
    val mediaType: String,
    val id: Int,
    val title: String,
    val originalTitle: String? = null,
    val posterPath: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
) {
    val key: String get() = "$mediaType:$id"
}

object Img {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?): String? = path?.let { BASE + "w342" + it }
    fun backdrop(path: String?): String? = path?.let { BASE + "w780" + it }
    fun profile(path: String?): String? = path?.let { BASE + "w185" + it }
}

/** Converts a list item to a card. Returns null for people and items without an id. */
fun ListItemDto.toCard(defaultMedia: String): WorkCard? {
    val media = mediaType ?: defaultMedia
    if (media != "movie" && media != "tv") return null
    if (id == 0) return null
    val t = (if (media == "movie") title else name) ?: title ?: name ?: return null
    val orig = if (media == "movie") originalTitle else originalName
    val date = if (media == "movie") releaseDate else firstAirDate
    return WorkCard(
        mediaType = media,
        id = id,
        title = t,
        originalTitle = orig?.takeIf { it != t },
        posterPath = posterPath,
        year = date?.take(4)?.takeIf { it.length == 4 },
        rating = voteAverage?.takeIf { (voteCount ?: 0) > 0 },
        votes = voteCount,
    )
}

fun DetailsDto.toCard(media: String): WorkCard {
    val t = (if (media == "movie") title else name) ?: title ?: name ?: ""
    val orig = if (media == "movie") originalTitle else originalName
    val date = if (media == "movie") releaseDate else firstAirDate
    return WorkCard(
        mediaType = media,
        id = id,
        title = t,
        originalTitle = orig?.takeIf { it != t },
        posterPath = posterPath,
        year = date?.take(4)?.takeIf { it.length == 4 },
        rating = voteAverage?.takeIf { (voteCount ?: 0) > 0 },
        votes = voteCount,
    )
}
