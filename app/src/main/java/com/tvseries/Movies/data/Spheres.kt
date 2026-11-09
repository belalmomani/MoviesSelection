package com.tvseries.Movies.data

/**
 * "Production spheres": the first thing the user chooses when filtering.
 *  - Arab, East Asia (Japanese + Chinese + Korean together), Western (foreign), Other regions;
 *  - Animation is its OWN sphere: an animated title (any country) belongs only to it.
 * Derived from TMDB fields (genres, original language, origin/production countries), so it is an
 * estimate, flagged as such in the taxonomy. Language is checked first because it predicts the kind of
 * content better than a co-production's country list, and it is known even when a movie has no country yet.
 */
object Spheres {
    const val ARAB = "sphere.arab"
    const val EAST_ASIA = "sphere.east_asia"
    const val WESTERN = "sphere.western"
    const val OTHER = "sphere.other"
    const val ANIMATION = "sphere.animation"

    val arabCountries: Set<String> get() = Regions.arab
    val eastAsiaCountries = setOf("JP", "CN", "KR", "HK", "TW", "MO")
    val westernCountries = setOf(
        "US", "CA", "GB", "FR", "DE", "ES", "IT", "PT", "NL", "BE", "SE", "NO", "DK", "FI", "IE", "PL", "CZ", "AT", "CH",
        "GR", "RU", "UA", "RO", "HU", "BG", "RS", "HR", "IS", "AU", "NZ",
    )
    val otherCountries = setOf("TR", "IN", "MX", "BR", "AR", "IR", "TH", "ID", "VN", "PH", "PK", "CO", "CL", "PE", "ZA", "NG", "IL")

    private val eastAsiaLanguages = setOf("ja", "zh", "cn", "ko")
    private val westernLanguages = setOf(
        "en", "fr", "de", "es", "it", "pt", "ru", "nl", "sv", "no", "nb", "da", "fi", "pl", "cs", "sk", "hu", "ro", "bg", "el",
        "uk", "hr", "sr", "sl", "is", "ca", "eu", "et", "lv", "lt", "ga",
    )
    private val otherLanguages = setOf("tr", "hi", "ta", "te", "ml", "bn", "mr", "fa", "th", "id", "ms", "vi", "he", "tl", "ur", "kn", "pa", "sw")

    /** The sphere node id, or null when the data cannot tell (then the title stays "unknown" for this axis). */
    fun of(language: String?, countries: Collection<String>?, genres: Collection<Int>?): String? {
        if (genres != null && 16 in genres) return ANIMATION
        val l = language?.lowercase()?.takeIf { it.isNotBlank() }
        if (l == "ar") return ARAB
        if (l != null && l in eastAsiaLanguages) return EAST_ASIA
        val c = countries.orEmpty().map { it.uppercase() }
        if (c.isNotEmpty()) {
            return when {
                c.any { it in arabCountries } -> ARAB
                c.any { it in eastAsiaCountries } -> EAST_ASIA
                c.any { it in westernCountries } -> WESTERN
                else -> OTHER
            }
        }
        if (l != null) {
            if (l in westernLanguages) return WESTERN
            if (l in otherLanguages) return OTHER
        }
        return null
    }

    /** Countries to ask TMDB for; null for animation (it is a genre, not a country group). */
    fun countriesOf(sphere: String): Set<String>? = when (sphere) {
        ARAB -> arabCountries
        EAST_ASIA -> eastAsiaCountries
        WESTERN -> westernCountries
        OTHER -> otherCountries
        else -> null
    }
}
