package com.tvseries.Movies.core

/** Degrees wrapped into the range from just above -180 up to 180. */
fun wrapDegrees(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x <= -180f) x += 360f
    return x
}

/** Where each screen of the current station sits around the viewer: ring row (0 middle, -1 above, +1 below) and angle. */
data class Slot(
    val index: Int,
    val row: Int,
    val angle: Float,
    /** Size factor (screens come in different sizes). */
    val scale: Float = 1f,
    /** Distance factor (screens sit at different distances from the viewer). */
    val distance: Float = 1f,
)

private fun sizeFactor(i: Int): Float = 0.78f + ((i * 53) % 7) * 0.075f
private fun distanceFactor(i: Int): Float = 0.85f + ((i * 31) % 5) * 0.09f

fun layoutSlots(count: Int): List<Slot> {
    if (count <= 0) return emptyList()
    val mid = minOf(count, 8)
    val rest = count - mid
    val top = (rest + 1) / 2
    val bottom = rest - top
    fun ring(n: Int, row: Int, start: Int, offset: Float) =
        (0 until n).map { Slot(start + it, row, offset + it * 360f / n, sizeFactor(start + it), distanceFactor(start + it)) }
    return ring(mid, 0, 0, 0f) + ring(top, -1, mid, 20f) + ring(bottom, 1, mid + top, 40f)
}

/**
 * Journey timing. The index changes at the middle of the warp transition (when the screen is faded out).
 * [advancing] = automatic station changes. Turning it off keeps the current scene and the remaining dwell time,
 * so resuming continues from the same place instead of restarting.
 */
class JourneyClock(
    val stationCount: Int,
    private val dwellMs: Long = 40000,
    private val warpMs: Long = 1600,
    start: Int = 0,
) {
    var index: Int = start
        private set
    var dwellLeft: Long = dwellMs
        private set
    var advancing: Boolean = true

    /** Journey tempo: scales how fast the dwell time runs out (rotation speed is handled by the screen). */
    var speed: Float = 1f

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
            dwellLeft -= (dtMs * speed).toLong()
            if (dwellLeft <= 0) jumpTo(index + 1)
        }
        return changed
    }
}
