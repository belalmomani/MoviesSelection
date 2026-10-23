package com.tvseries.Movies.core

/** Visual environment drawn for a station. */
enum class Env { FUTURE, ROME, CASTLE, JUNGLE, DESERT, NORTH, WEST, SEA, WAR, COLD, CYBER, ASH, FILM }

/**
 * One station of the Time Gate. Titles come from TMDB: keywords are resolved at run time with the official
 * keyword search (no invented ids), optionally combined with a genre or a release-year range.
 * If TMDB returns too few titles, the app says so and shows only what exists.
 */
class TimeWorld(
    val id: String,
    val ar: String,
    val en: String,
    val subAr: String,
    val subEn: String,
    val env: Env,
    val colors: Pair<Long, Long>,
    val keywords: List<String> = emptyList(),
    val genres: Set<Int> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minVotes: Int = 30,
) {
    fun name(arabic: Boolean): String = if (arabic) ar else en
    fun sub(arabic: Boolean): String = if (arabic) subAr else subEn
}

object TimeWorlds {
    /** Titles below this count are flagged as "thin data". */
    const val ENOUGH = 4

    val all: List<TimeWorld> = listOf(
        TimeWorld("future", "بوابة المستقبل", "The future gate", "مدن وأحلام ما بعد اليوم", "Cities and dreams of tomorrow", Env.FUTURE,
            0xFF0B1030L to 0xFF00B8D4L, keywords = listOf("future", "dystopia", "space colony")),
        TimeWorld("rome", "روما القديمة", "Ancient Rome", "ساحات وأعمدة وإمبراطورية", "Forums, columns and an empire", Env.ROME,
            0xFF3A2410L to 0xFFE0A458L, keywords = listOf("ancient rome", "roman empire", "gladiator")),
        TimeWorld("castle", "قلعة العصور الوسطى", "Medieval castle", "فرسان وأسوار ومشاعل", "Knights, walls and torches", Env.CASTLE,
            0xFF10131FL to 0xFF5C6B8AL, keywords = listOf("middle ages", "medieval", "knight", "castle")),
        TimeWorld("dino", "غابة الديناصورات", "Dinosaur forest", "عالم ما قبل التاريخ", "The prehistoric world", Env.JUNGLE,
            0xFF04210FL to 0xFF2E8B57L, keywords = listOf("dinosaur", "prehistoric")),
        TimeWorld("egypt", "مصر القديمة", "Ancient Egypt", "أهرامات وصحراء وفراعنة", "Pyramids, desert and pharaohs", Env.DESERT,
            0xFF4A2C0AL to 0xFFF2C36BL, keywords = listOf("ancient egypt", "pharaoh")),
        TimeWorld("viking", "عصر الفايكنج", "The Viking age", "سفن وجليد وشفق", "Ships, ice and aurora", Env.NORTH,
            0xFF08162BL to 0xFF4FC3F7L, keywords = listOf("viking")),
        TimeWorld("west", "الغرب الأمريكي", "The Wild West", "غبار وجبال حمراء وغروب", "Dust, red mesas and sunset", Env.WEST,
            0xFF3B1608L to 0xFFFF8A3DL, genres = setOf(37), minVotes = 100),
        TimeWorld("pirates", "عصر القراصنة", "Age of pirates", "بحار وسفن وليالٍ مقمرة", "Seas, ships and moonlit nights", Env.SEA,
            0xFF031326L to 0xFF1E5F8CL, keywords = listOf("pirate")),
        TimeWorld("wwii", "الحرب العالمية الثانية", "World War II", "كشّافات ودخان وأفق محطّم", "Searchlights, smoke and broken skylines", Env.WAR,
            0xFF1B1B18L to 0xFF7A7560L, keywords = listOf("world war ii")),
        TimeWorld("cold", "الحرب الباردة", "The Cold War", "رادار وتجسس وظلال", "Radar, espionage and shadows", Env.COLD,
            0xFF1A0B12L to 0xFFB23A48L, keywords = listOf("cold war", "spy")),
        TimeWorld("cyber", "عالم السايبربانك", "Cyberpunk city", "نيون ومطر وناطحات", "Neon, rain and towers", Env.CYBER,
            0xFF12031FL to 0xFFFF2E93L, keywords = listOf("cyberpunk")),
        TimeWorld("ash", "ما بعد الكارثة", "After the collapse", "رماد وأطلال وغبار", "Ash, ruins and dust", Env.ASH,
            0xFF2A0F06L to 0xFFD9693AL, keywords = listOf("post-apocalyptic")),
        TimeWorld("classic", "السينما الكلاسيكية", "Classic cinema", "أبيض وأسود وشريط فيلم", "Black & white and film strips", Env.FILM,
            0xFF111111L to 0xFF8C8C8CL, yearFrom = 1930, yearTo = 1959, minVotes = 100),
    )
}
