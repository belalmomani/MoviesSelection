package com.tvseries.Movies.core

/** Where a category comes from. Official TMDB data, derived-from-fields, curated by the app team, or auto-suggested. */
enum class Source { TMDB_OFFICIAL, DERIVED, APP_CURATED, AUTO_SUGGESTED }

enum class Verification { VERIFIED, UNVERIFIED }

data class AxisDef(val id: String, val nameAr: String, val nameEn: String) {
    fun name(arabic: Boolean): String = if (arabic) nameAr else nameEn
}

/** Stable axis ids. New axes can be added to [Axes.all] without touching the filter engine. */
object Axes {
    const val FORMAT = "format"
    const val GENRE = "genre"
    const val YEAR = "year"
    const val COUNTRY = "country"
    const val LANGUAGE = "language"
    const val RATING = "rating"
    const val RUNTIME = "runtime"
    const val SEASONS = "seasons"
    const val STATUS = "status"
    const val KEYWORD = "keyword"
    const val CAST = "cast"
    const val DIRECTOR = "director"
    const val SPOKEN = "spoken"
    const val MOOD = "mood"
    const val TENSION = "tension"
    const val ENDING = "ending"
    const val AVOID = "avoid"

    val all: List<AxisDef> = listOf(
        AxisDef(FORMAT, "نوع العمل", "Type"),
        AxisDef(MOOD, "المزاج المناسب", "Mood"),
        AxisDef(TENSION, "مستوى التوتر", "Tension level"),
        AxisDef(GENRE, "النوع", "Genre"),
        AxisDef(YEAR, "سنة الإصدار", "Release year"),
        AxisDef(COUNTRY, "بلد الإنتاج", "Production country"),
        AxisDef(LANGUAGE, "اللغة الأصلية", "Original language"),
        AxisDef(RATING, "التقييم", "Rating"),
        AxisDef(RUNTIME, "المدة", "Runtime"),
        AxisDef(SEASONS, "عدد المواسم", "Seasons"),
        AxisDef(STATUS, "حالة العمل", "Status"),
        AxisDef(KEYWORD, "الكلمات المفتاحية", "Keywords"),
        AxisDef(CAST, "الممثلون", "Cast"),
        AxisDef(DIRECTOR, "المخرج / المنشئ", "Director / creator"),
        AxisDef(SPOKEN, "لغات الحوار", "Spoken languages"),
        AxisDef(ENDING, "النهاية", "Ending"),
        AxisDef(AVOID, "ما قد لا يعجبك", "May not suit you"),
    )
}

data class TaxonomyNode(
    val id: String,
    val axis: String,
    val nameAr: String,
    val nameEn: String,
    val parents: Set<String> = emptySet(),
    val source: Source = Source.TMDB_OFFICIAL,
    val verification: Verification = Verification.VERIFIED,
    /** 1.0 for official data; lower for derived / suggested categories. */
    val confidence: Double = 1.0,
    val synonyms: Set<String> = emptySet(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun name(arabic: Boolean): String = if (arabic) nameAr else nameEn
}

/**
 * Immutable category dictionary. Supports multiple parents, unlimited depth, and
 * adding nodes without changing any filter logic.
 */
class Taxonomy(nodes: Collection<TaxonomyNode>, val axes: List<AxisDef> = Axes.all) {
    private val byId: Map<String, TaxonomyNode>
    private val childrenOf: Map<String, List<String>>

    init {
        val m = LinkedHashMap<String, TaxonomyNode>()
        for (n in nodes) {
            require(n.id !in m) { "duplicate taxonomy id: ${n.id}" }
            m[n.id] = n
        }
        byId = m
        val ch = HashMap<String, MutableList<String>>()
        for (n in m.values) for (p in n.parents) ch.getOrPut(p) { ArrayList() }.add(n.id)
        childrenOf = ch
    }

    val nodes: Collection<TaxonomyNode> get() = byId.values

    fun node(id: String): TaxonomyNode? = byId[id]
    fun contains(id: String): Boolean = id in byId
    fun childrenIds(id: String): List<String> = childrenOf[id].orEmpty()

    /** All transitive parents (cycle-safe). */
    fun ancestors(id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayList<String>()
        byId[id]?.parents?.let { stack.addAll(it) }
        while (stack.isNotEmpty()) {
            val p = stack.removeAt(stack.size - 1)
            if (out.add(p)) byId[p]?.parents?.let { stack.addAll(it) }
        }
        return out
    }

    /** All transitive children (cycle-safe). */
    fun descendants(id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayList<String>()
        stack.addAll(childrenIds(id))
        while (stack.isNotEmpty()) {
            val c = stack.removeAt(stack.size - 1)
            if (out.add(c)) stack.addAll(childrenIds(c))
        }
        return out
    }

    /** The ids plus all their ancestors. Selecting a parent therefore matches works tagged with any child. */
    fun closure(ids: Set<String>): Set<String> {
        val out = LinkedHashSet<String>(ids)
        for (id in ids) out.addAll(ancestors(id))
        return out
    }

    fun withNodes(extra: Collection<TaxonomyNode>): Taxonomy {
        val m = LinkedHashMap<String, TaxonomyNode>()
        for (n in byId.values) m[n.id] = n
        for (n in extra) m[n.id] = n
        return Taxonomy(m.values, axes)
    }

    fun search(query: String, axis: String? = null): List<TaxonomyNode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return byId.values.filter { n ->
            (axis == null || n.axis == axis) &&
                (q in n.nameAr.lowercase() || q in n.nameEn.lowercase() || n.synonyms.any { q in it.lowercase() })
        }
    }

    /** Structural problems: unknown axis, missing parent, cycle, semantic duplicate in the same axis. */
    fun validate(): List<String> {
        val issues = ArrayList<String>()
        val axisIds = axes.map { it.id }.toSet()
        val seenNames = HashSet<String>()
        for (n in byId.values) {
            if (n.axis !in axisIds) issues += "unknown axis '${n.axis}' for ${n.id}"
            for (p in n.parents) if (p !in byId) issues += "missing parent '$p' for ${n.id}"
            if (n.id in ancestors(n.id)) issues += "cycle at ${n.id}"
            if (n.confidence < 0.0 || n.confidence > 1.0) issues += "bad confidence for ${n.id}"
            if (!seenNames.add(n.axis + "|" + n.nameEn.lowercase())) issues += "duplicate name '${n.nameEn}' in axis ${n.axis}"
        }
        return issues
    }
}
