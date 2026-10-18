package com.tvseries.Movies.data

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Source
import com.tvseries.Movies.core.TaxonomyNode
import com.tvseries.Movies.core.Verification

/**
 * "Cinema worlds": a LOCAL map built from TMDB genres and (when known) keywords. Worlds and branches are
 * the app's own estimates (DERIVED / UNVERIFIED), never official TMDB categories.
 * Keyword-only branches are tagged only when a keyword actually matched (otherwise unknown).
 */
object Worlds {
    class Branch(
        val id: String, val ar: String, val en: String,
        val any: Set<Int> = emptySet(), val all: Set<Int> = emptySet(), val none: Set<Int> = emptySet(),
        val keywords: List<String> = emptyList(),
    )

    class World(val id: String, val ar: String, val en: String, val branches: List<Branch>)

    val worlds: List<World> = listOf(
        World("crime_mystery", "جريمة وغموض", "Crime & mystery", listOf(
            Branch("detective", "تحقيق وألغاز", "Detective & puzzles", any = setOf(9648)),
            Branch("underworld", "عالم الجريمة والعصابات", "Crime underworld", any = setOf(80), none = setOf(9648)),
            Branch("suspense", "إثارة وتشويق", "Suspense thrillers", any = setOf(53)),
            Branch("serial_killer", "قتلة متسلسلون", "Serial killers", keywords = listOf("serial killer")),
        )),
        World("future_sf", "خيال مستقبلي", "Future & sci-fi", listOf(
            Branch("sf_general", "خيال علمي عام", "General sci-fi", any = setOf(878, 10765)),
            Branch("space", "الفضاء", "Space", keywords = listOf("outer space", "spaceship", "space")),
            Branch("ai_robots", "ذكاء اصطناعي وروبوتات", "AI & robots", keywords = listOf("artificial intelligence", "robot", "android")),
            Branch("time_travel", "السفر عبر الزمن", "Time travel", keywords = listOf("time travel", "time loop")),
            Branch("dystopia", "ديستوبيا وما بعد الكارثة", "Dystopia & post-apocalypse", keywords = listOf("dystopia", "post-apocalyptic")),
        )),
        World("real_life", "حياة واقعية", "Real life", listOf(
            Branch("human_drama", "دراما إنسانية", "Human drama", any = setOf(18), none = setOf(14, 878, 10765, 27, 16)),
            Branch("love", "قصص حب", "Love stories", any = setOf(10749)),
            Branch("true_story", "قصص حقيقية", "True stories", keywords = listOf("based on true story", "biography")),
        )),
        World("history_war", "تاريخ وحرب", "History & war", listOf(
            Branch("history", "أحداث تاريخية", "Historical events", any = setOf(36)),
            Branch("war", "حروب وصراعات", "War & conflict", any = setOf(10752, 10768)),
        )),
        World("fantasy_magic", "فانتازيا وسحر", "Fantasy & magic", listOf(
            Branch("magic", "عوالم السحر", "Magic worlds", any = setOf(14, 10765)),
            Branch("animated_fantasy", "فانتازيا مرسومة", "Animated fantasy", all = setOf(16, 14)),
            Branch("superhero", "أبطال خارقون", "Superheroes", keywords = listOf("superhero")),
        )),
        World("horror_supernatural", "رعب وخوارق", "Horror & supernatural", listOf(
            Branch("horror_general", "رعب عام", "General horror", any = setOf(27)),
            Branch("psych_horror", "رعب نفسي", "Psychological horror", all = setOf(27, 53)),
            Branch("supernatural", "أشباح وقوى خفية", "Ghosts & dark forces", keywords = listOf("ghost", "haunted", "demon", "possession")),
            Branch("slasher", "رعب الشرائح الدموية", "Slasher horror", keywords = listOf("slasher")),
        )),
        World("comedy_light", "ضحك وخفة", "Comedy & light", listOf(
            Branch("comedy_general", "كوميديا عامة", "General comedy", any = setOf(35)),
            Branch("romcom", "كوميديا رومانسية", "Romantic comedy", all = setOf(35, 10749)),
            Branch("family_comedy", "كوميديا عائلية", "Family comedy", all = setOf(35, 10751)),
            Branch("satire", "سخرية اجتماعية", "Satire", keywords = listOf("satire")),
        )),
        World("family_animation", "عائلة وأنيميشن", "Family & animation", listOf(
            Branch("kids_family", "للصغار والعائلة", "Kids & family", any = setOf(10762, 10751)),
            Branch("animation", "أنيميشن", "Animation", any = setOf(16)),
        )),
        World("docs_reality", "وثائقي وواقع", "Documentary & reality", listOf(
            Branch("documentary", "وثائقيات", "Documentaries", any = setOf(99)),
            Branch("reality", "تلفزيون الواقع", "Reality TV", any = setOf(10764)),
            Branch("talk_news", "حوارات وأخبار", "Talk & news", any = setOf(10763, 10767)),
        )),
        World("action_adventure", "أكشن ومغامرات", "Action & adventure", listOf(
            Branch("action", "أكشن", "Action", any = setOf(28, 10759)),
            Branch("adventure", "مغامرات", "Adventures", any = setOf(12)),
            Branch("martial_arts", "فنون قتالية", "Martial arts", keywords = listOf("martial arts")),
        )),
    )

    private fun est(id: String, ar: String, en: String, parents: Set<String>) =
        TaxonomyNode(id, Axes.WORLD, ar, en, parents, Source.DERIVED, Verification.UNVERIFIED, 0.6)

    fun nodes(): List<TaxonomyNode> {
        val out = ArrayList<TaxonomyNode>()
        for (w in worlds) {
            out += est("world.${w.id}", w.ar, w.en, emptySet())
            for (b in w.branches) out += est("world.${w.id}.${b.id}", b.ar, b.en, setOf("world.${w.id}"))
        }
        return out
    }

    /** Branch ids matching the title. keywordNames == null means keywords are unknown. */
    fun tags(genreIds: List<Int>, keywordNames: List<String>?): Set<String> {
        val ids = genreIds.toSet()
        val kws = keywordNames?.map { it.lowercase() }
        val out = LinkedHashSet<String>()
        for (w in worlds) for (b in w.branches) {
            val genreOk = (b.any.isEmpty() || ids.any { it in b.any }) && ids.containsAll(b.all) && ids.none { it in b.none }
            val hasGenreRule = b.any.isNotEmpty() || b.all.isNotEmpty()
            val kwOk = if (b.keywords.isEmpty()) true else kws != null && b.keywords.any { n -> kws.any { it.contains(n) } }
            if (!hasGenreRule && b.keywords.isEmpty()) continue
            if (genreOk && kwOk) out += "world.${w.id}.${b.id}"
        }
        return out
    }
}
