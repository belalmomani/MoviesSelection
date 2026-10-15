package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Source
import com.tvseries.Movies.core.TaxonomyNode
import com.tvseries.Movies.core.Verification
import java.util.Locale

/** Curated geographic grouping (app-curated, verified facts). Countries without a region simply have no parent. */
object Regions {
    val members: Map<String, List<String>> = mapOf(
        "region.arab" to listOf("JO", "SA", "EG", "SY", "LB", "PS", "AE", "KW", "QA", "BH", "OM", "IQ", "YE", "LY", "TN", "DZ", "MA", "SD", "SO", "DJ", "MR", "KM"),
        "region.east_asia" to listOf("JP", "KR", "CN", "HK", "TW", "MO", "MN", "KP"),
        "region.south_asia" to listOf("IN", "PK", "BD", "LK", "NP", "AF"),
        "region.southeast_asia" to listOf("TH", "ID", "MY", "SG", "PH", "VN", "KH", "LA", "MM"),
        "region.west_asia" to listOf("TR", "IR", "IL", "AM", "GE", "AZ"),
        "region.europe" to listOf("GB", "FR", "DE", "ES", "IT", "PT", "NL", "BE", "SE", "NO", "DK", "FI", "IE", "PL", "CZ", "AT", "CH", "GR", "RU", "UA", "RO", "HU", "BG", "RS", "HR", "IS", "CY"),
        "region.north_america" to listOf("US", "CA"),
        "region.latin_america" to listOf("MX", "BR", "AR", "CL", "CO", "PE", "VE", "UY", "CU"),
        "region.oceania" to listOf("AU", "NZ"),
        "region.africa" to listOf("ZA", "NG", "KE", "GH", "ET"),
    )

    private val byCountry: Map<String, String> =
        members.flatMap { (region, codes) -> codes.map { it to region } }.toMap()

    fun regionOf(code: String): String? = byCountry[code.uppercase()]
}

/** Creates nodes for dynamic values found in TMDB data (countries, languages, keywords, people). */
object NodeFactory {
    @Suppress("DEPRECATION")
    private fun countryName(code: String, locale: Locale): String = Locale("", code).getDisplayCountry(locale)

    @Suppress("DEPRECATION")
    private fun languageName(code: String, locale: Locale): String = Locale(code).getDisplayLanguage(locale)

    private val customLanguages = mapOf(
        "cn" to ("الكانتونية" to "Cantonese"),
        "sh" to ("الصربية الكرواتية" to "Serbo-Croatian"),
    )

    fun country(codeRaw: String, fallbackName: String? = null): TaxonomyNode {
        val code = codeRaw.uppercase()
        val ar = countryName(code, Locale("ar")).ifBlank { fallbackName ?: code }
        val en = countryName(code, Locale.ENGLISH).ifBlank { fallbackName ?: code }
        val region = Regions.regionOf(code)
        return TaxonomyNode(
            id = "country.$code", axis = Axes.COUNTRY, nameAr = ar, nameEn = en,
            parents = if (region != null) setOf(region) else emptySet(),
            source = Source.TMDB_OFFICIAL, synonyms = setOf(code),
        )
    }

    fun language(codeRaw: String, axis: String = Axes.LANGUAGE): TaxonomyNode {
        val code = codeRaw.lowercase()
        val custom = customLanguages[code]
        val ar = custom?.first ?: languageName(code, Locale("ar")).ifBlank { code }
        val en = custom?.second ?: languageName(code, Locale.ENGLISH).ifBlank { code }
        val prefix = if (axis == Axes.SPOKEN) "spoken" else "language"
        return TaxonomyNode(
            id = "$prefix.$code", axis = axis, nameAr = ar, nameEn = en,
            source = Source.TMDB_OFFICIAL, synonyms = setOf(code),
        )
    }

    fun keyword(id: Int, name: String) = TaxonomyNode(
        id = "keyword.$id", axis = Axes.KEYWORD, nameAr = name, nameEn = name,
        source = Source.TMDB_OFFICIAL, // TMDB keywords are English only; no Arabic translation exists in the source.
    )

    fun person(axis: String, id: Int, name: String) = TaxonomyNode(
        id = "$axis.$id", axis = axis, nameAr = name, nameEn = name, source = Source.TMDB_OFFICIAL,
    )
}

object TaxonomySeed {
    const val GENRE_ANIMATION = 16
    const val GENRE_DOCUMENTARY = 99
    const val GENRE_TV_MOVIE = 10770

    val movieGenreIds = setOf(28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 10770, 53, 10752, 37)
    val tvGenreIds = setOf(10759, 16, 35, 80, 99, 18, 10751, 10762, 9648, 10763, 10764, 10765, 10766, 10767, 10768, 37)

    /** Official TMDB genre ids with Arabic names (Arabic names are the app's translation). */
    private val genres: List<Triple<Int, String, String>> = listOf(
        Triple(28, "أكشن", "Action"),
        Triple(12, "مغامرات", "Adventure"),
        Triple(16, "أنيميشن", "Animation"),
        Triple(35, "كوميديا", "Comedy"),
        Triple(80, "جريمة", "Crime"),
        Triple(99, "وثائقي", "Documentary"),
        Triple(18, "دراما", "Drama"),
        Triple(10751, "عائلي", "Family"),
        Triple(14, "فانتازيا", "Fantasy"),
        Triple(36, "تاريخي", "History"),
        Triple(27, "رعب", "Horror"),
        Triple(10402, "موسيقي", "Music"),
        Triple(9648, "غموض", "Mystery"),
        Triple(10749, "رومانسي", "Romance"),
        Triple(878, "خيال علمي", "Science Fiction"),
        Triple(10770, "فيلم تلفزيوني", "TV Movie"),
        Triple(53, "إثارة وتشويق", "Thriller"),
        Triple(10752, "حربي", "War"),
        Triple(37, "غربي (ويسترن)", "Western"),
        Triple(10759, "أكشن ومغامرات (تلفزيون)", "Action & Adventure (TV)"),
        Triple(10762, "أطفال", "Kids"),
        Triple(10763, "أخبار", "News"),
        Triple(10764, "تلفزيون الواقع", "Reality"),
        Triple(10765, "خيال علمي وفانتازيا (تلفزيون)", "Sci-Fi & Fantasy (TV)"),
        Triple(10766, "مسلسلات يومية", "Soap"),
        Triple(10767, "برامج حوارية", "Talk"),
        Triple(10768, "حرب وسياسة (تلفزيون)", "War & Politics (TV)"),
    )

    private fun n(
        id: String, axis: String, ar: String, en: String,
        parents: Set<String> = emptySet(),
        source: Source = Source.TMDB_OFFICIAL,
        verification: Verification = Verification.VERIFIED,
        confidence: Double = 1.0,
        synonyms: Set<String> = emptySet(),
    ) = TaxonomyNode(id, axis, ar, en, parents, source, verification, confidence, synonyms)

    fun nodes(): List<TaxonomyNode> {
        val out = ArrayList<TaxonomyNode>()

        // Format. Anime / short film are DERIVED from fields (not official TMDB types) and flagged unverified.
        out += n("format.movie", Axes.FORMAT, "فيلم", "Movie")
        out += n("format.tv", Axes.FORMAT, "مسلسل", "TV series")
        out += n("format.miniseries", Axes.FORMAT, "مسلسل قصير / محدود", "Miniseries", setOf("format.tv"))
        out += n("format.tv_movie", Axes.FORMAT, "فيلم تلفزيوني", "TV movie", setOf("format.movie"), Source.DERIVED)
        out += n(
            "format.short", Axes.FORMAT, "فيلم قصير (≤ 40 دقيقة)", "Short film (≤ 40 min)", setOf("format.movie"),
            Source.DERIVED, Verification.UNVERIFIED, 0.8,
        )
        out += n(
            "format.anime", Axes.FORMAT, "أنيمي ياباني", "Japanese anime", emptySet(),
            Source.DERIVED, Verification.UNVERIFIED, 0.85, setOf("anime"),
        )

        for ((id, ar, en) in genres) out += n("genre.$id", Axes.GENRE, ar, en)

        // Years grouped by decade.
        for (d in 1880..2030 step 10) out += n("decade.$d", Axes.YEAR, "عقد ${d}", "${d}s", emptySet(), Source.DERIVED)
        for (y in 1880..2039) out += n("year.$y", Axes.YEAR, "$y", "$y", setOf("decade.${y / 10 * 10}"), Source.DERIVED)

        for ((region, _) in Regions.members) {
            val (ar, en) = when (region) {
                "region.arab" -> "الدول العربية" to "Arab world"
                "region.east_asia" -> "شرق آسيا" to "East Asia"
                "region.south_asia" -> "جنوب آسيا" to "South Asia"
                "region.southeast_asia" -> "جنوب شرق آسيا" to "Southeast Asia"
                "region.west_asia" -> "غرب آسيا (غير عربية)" to "West Asia (non-Arab)"
                "region.europe" -> "أوروبا" to "Europe"
                "region.north_america" -> "أمريكا الشمالية" to "North America"
                "region.latin_america" -> "أمريكا اللاتينية" to "Latin America"
                "region.oceania" -> "أوقيانوسيا" to "Oceania"
                else -> "أفريقيا (غير عربية)" to "Africa (non-Arab)"
            }
            out += n(region, Axes.COUNTRY, ar, en, emptySet(), Source.APP_CURATED)
        }

        // Rating thresholds form a chain: 9+ is a child of 8+ ... so choosing "7+" includes everything >= 7.
        for (r in 5..9) {
            out += n(
                "rating.$r", Axes.RATING, "تقييم $r+", "Rating $r+",
                if (r > 5) setOf("rating.${r - 1}") else emptySet(), Source.DERIVED,
            )
        }

        out += n("runtime.m.u90", Axes.RUNTIME, "فيلم أقل من 90 دقيقة", "Film under 90 min", emptySet(), Source.DERIVED)
        out += n("runtime.m.90_120", Axes.RUNTIME, "فيلم 90–120 دقيقة", "Film 90–120 min", emptySet(), Source.DERIVED)
        out += n("runtime.m.120_150", Axes.RUNTIME, "فيلم 120–150 دقيقة", "Film 120–150 min", emptySet(), Source.DERIVED)
        out += n("runtime.m.o150", Axes.RUNTIME, "فيلم 150 دقيقة أو أكثر", "Film 150+ min", emptySet(), Source.DERIVED)
        out += n("runtime.e.u30", Axes.RUNTIME, "حلقة أقل من 30 دقيقة", "Episode under 30 min", emptySet(), Source.DERIVED)
        out += n("runtime.e.30_50", Axes.RUNTIME, "حلقة 30–50 دقيقة", "Episode 30–50 min", emptySet(), Source.DERIVED)
        out += n("runtime.e.o50", Axes.RUNTIME, "حلقة أكثر من 50 دقيقة", "Episode over 50 min", emptySet(), Source.DERIVED)

        out += n("seasons.1", Axes.SEASONS, "موسم واحد", "1 season", emptySet(), Source.DERIVED)
        out += n("seasons.2_3", Axes.SEASONS, "2–3 مواسم", "2–3 seasons", emptySet(), Source.DERIVED)
        out += n("seasons.4_6", Axes.SEASONS, "4–6 مواسم", "4–6 seasons", emptySet(), Source.DERIVED)
        out += n("seasons.7p", Axes.SEASONS, "7 مواسم أو أكثر", "7+ seasons", emptySet(), Source.DERIVED)

        out += n("status.completed", Axes.STATUS, "مكتمل", "Completed", emptySet(), Source.APP_CURATED)
        out += n("status.ended", Axes.STATUS, "انتهى عرضه", "Ended", setOf("status.completed"))
        out += n("status.released", Axes.STATUS, "صدر (فيلم)", "Released (film)", setOf("status.completed"))
        out += n("status.ongoing", Axes.STATUS, "مستمر", "Ongoing")
        out += n("status.upcoming", Axes.STATUS, "قيد الإنتاج / قادم", "In production / upcoming")
        out += n("status.canceled", Axes.STATUS, "ملغى", "Canceled")
        out += n("status.other", Axes.STATUS, "حالة أخرى", "Other status")
        return out
    }
}
