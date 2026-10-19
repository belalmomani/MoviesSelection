package com.tvseries.Movies.core

/** One stop of the journey = one decade of cinema. */
object Eras {
    val starts: List<Int> = (1920..2020 step 10).toList()

    // Short flavour text (not data): Arabic / English.
    private val names: Map<Int, Pair<String, String>> = mapOf(
        1920 to ("العصر الصامت" to "The silent era"),
        1930 to ("الأبيض والأسود والصوت" to "Black & white meets sound"),
        1940 to ("سينما الأربعينيات" to "The forties"),
        1950 to ("الألوان الباهرة" to "Dazzling colour"),
        1960 to ("موجات التجديد" to "Waves of change"),
        1970 to ("سينما السبعينيات" to "The seventies"),
        1980 to ("عصر الفيديو والنيون" to "Video & neon"),
        1990 to ("العصر الرقمي الأول" to "The first digital age"),
        2000 to ("الألفية الجديدة" to "The new millennium"),
        2010 to ("عصر المنصات" to "The streaming age"),
        2020 to ("الآن" to "Now"),
    )

    /** Background gradient colours (ARGB) per decade. */
    val palette: Map<Int, Pair<Long, Long>> = mapOf(
        1920 to (0xFF3B2F1EL to 0xFF8A6D3BL),
        1930 to (0xFF151515L to 0xFF6B6B6BL),
        1940 to (0xFF202B33L to 0xFF5E7380L),
        1950 to (0xFF2B1D3AL to 0xFFB5466BL),
        1960 to (0xFF3A2B12L to 0xFFE0A100L),
        1970 to (0xFF3B1F0FL to 0xFFC2662DL),
        1980 to (0xFF1B0B3BL to 0xFFFF2E93L),
        1990 to (0xFF0B2B3BL to 0xFF2EC4B6L),
        2000 to (0xFF0D1B2AL to 0xFF4A6FA5L),
        2010 to (0xFF111827L to 0xFF7C3AEDL),
        2020 to (0xFF050B18L to 0xFF00D4FFL),
    )

    fun name(start: Int, arabic: Boolean): String = names[start]?.let { if (arabic) it.first else it.second } ?: ""
    fun label(start: Int): String = "${start}s"

    /** Titles are picked by vote count, so the threshold only needs to exclude near-unknown entries. */
    fun minVotes(start: Int): Int = if (start <= 1940) 20 else if (start <= 1970) 100 else 300
}

/** Degrees wrapped into the range from just above -180 up to 180. */
fun wrapDegrees(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x <= -180f) x += 360f
    return x
}

/** Where each screen of the current station sits around the viewer: ring row (0 middle, -1 above, +1 below) and angle. */
data class Slot(val index: Int, val row: Int, val angle: Float)

fun layoutSlots(count: Int): List<Slot> {
    if (count <= 0) return emptyList()
    val mid = minOf(count, 8)
    val rest = count - mid
    val top = (rest + 1) / 2
    val bottom = rest - top
    fun ring(n: Int, row: Int, start: Int, offset: Float) = (0 until n).map { Slot(start + it, row, offset + it * 360f / n) }
    return ring(mid, 0, 0, 0f) + ring(top, -1, mid, 20f) + ring(bottom, 1, mid + top, 40f)
}

/**
 * Journey timing. The index changes at the middle of the warp transition (when the screen is faded out).
 * [advancing] = automatic station changes. Turning it off keeps the current scene and the remaining dwell time,
 * so resuming continues from the same place instead of restarting.
 */
class JourneyClock(
    val stationCount: Int,
    private val dwellMs: Long = 9000,
    private val warpMs: Long = 1600,
    start: Int = 0,
) {
    var index: Int = start
        private set
    var dwellLeft: Long = dwellMs
        private set
    var advancing: Boolean = true

    private var warpLeft = 0L
    private var pending = start
    private var switched = true

    val warping: Boolean get() = warpLeft > 0
    val warpFraction: Float get() = if (warpLeft <= 0) 0f else 1f - warpLeft.toFloat() / warpMs

    fun jumpTo(target: Int) {
        val t = ((target % stationCount) + stationCount) % stationCount
        if (!warping && t == index) return
        pending = t
        if (!warping || switched) {
            warpLeft = warpMs
            switched = false
        }
    }

    fun step(delta: Int) = jumpTo((if (warping) pending else index) + delta)

    /** Returns true when the station index changed during this tick. */
    fun tick(dtMs: Long): Boolean {
        var changed = false
        if (warpLeft > 0) {
            warpLeft -= dtMs
            if (!switched && warpLeft <= warpMs / 2) {
                index = pending
                switched = true
                changed = true
            }
            if (warpLeft <= 0) {
                warpLeft = 0
                dwellLeft = dwellMs
            }
        } else if (advancing) {
            dwellLeft -= dtMs
            if (dwellLeft <= 0) jumpTo(index + 1)
        }
        return changed
    }
}
