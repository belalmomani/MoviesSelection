package com.tvseries.Movies.ads

import android.content.Context
import com.tvseries.Movies.data.Prefs

/** How many ads were shown today and when the last one was (persisted by [Prefs]). */
data class AdCapState(val day: String = "", val count: Int = 0, val lastShownMs: Long = 0L)

/** At most [PER_DAY] ads a day, never close together, never right after the app was opened. */
object DailyAdCap {
    const val PER_DAY = 3
    const val MIN_GAP_MS = 20 * 60 * 1000L
    const val MIN_SESSION_MS = 90 * 1000L

    fun dayOf(nowMs: Long): String =
        java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    fun canShow(state: AdCapState, today: String, nowMs: Long, sessionStartMs: Long): Boolean {
        if (nowMs - sessionStartMs < MIN_SESSION_MS) return false
        val shownToday = if (state.day == today) state.count else 0
        if (shownToday >= PER_DAY) return false
        // A last-shown time in the future (the clock was changed) never blocks.
        val gapOk = state.lastShownMs == 0L || nowMs < state.lastShownMs || nowMs - state.lastShownMs >= MIN_GAP_MS
        return gapOk
    }

    fun record(state: AdCapState, today: String, nowMs: Long): AdCapState =
        AdCapState(today, (if (state.day == today) state.count else 0) + 1, nowMs)
}

/**
 * Decides when an ad may appear: only when the user comes back from a Details page to a main tab (a natural break),
 * never in the filtering flow, within [DailyAdCap]. Phone: a full-screen ad. TV: a QR code the viewer scans with a phone.
 */
class AdMoments(
    private val prefs: Prefs,
    private val ads: AdsProvider,
    private val adsEnabled: Boolean,
    private val tvQrUrl: String,
    private val sessionStartMs: Long = System.currentTimeMillis(),
) {
    enum class Kind { NONE, INTERSTITIAL, TV_QR }

    fun onReturnFromDetails(context: Context, isTv: Boolean, userIsFiltering: Boolean, nowMs: Long = System.currentTimeMillis()): Kind {
        if (!adsEnabled || userIsFiltering) return Kind.NONE
        val today = DailyAdCap.dayOf(nowMs)
        val state = prefs.adCap()
        if (!DailyAdCap.canShow(state, today, nowMs, sessionStartMs)) return Kind.NONE
        if (isTv) {
            if (tvQrUrl.isBlank()) return Kind.NONE
            prefs.setAdCap(DailyAdCap.record(state, today, nowMs))
            return Kind.TV_QR
        }
        if (!ads.canShowAt(AdPlacement.INTERSTITIAL_AFTER_DETAILS, userIsFiltering = false)) return Kind.NONE
        if (!ads.showInterstitial(context)) return Kind.NONE
        prefs.setAdCap(DailyAdCap.record(state, today, nowMs))
        return Kind.INTERSTITIAL
    }
}
