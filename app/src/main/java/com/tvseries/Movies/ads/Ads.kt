package com.tvseries.Movies.ads

import android.content.Context
import android.view.View

/**
 * Ads integration (Start.io).
 *
 * Switch: ADS_ENABLED in app/config.properties.
 *  - false -> the "noads" source set is compiled (no SDK dependency at all, nothing is ever requested).
 *  - true  -> the "startio" source set is compiled (SDK dependency + banner provider).
 *
 * Placements: a banner on Home and on Details, plus at most [DailyAdCap.PER_DAY] full-screen ads a day when the user
 * comes back from a Details page to a main tab ([AdMoments]). Never on the filter / mood screens ([AdPolicy]).
 */
enum class AdPlacement { HOME_BANNER, DETAILS_BANNER, INTERSTITIAL_BETWEEN_SESSIONS, INTERSTITIAL_AFTER_DETAILS }

interface AdsProvider {
    val enabled: Boolean
    /** Called only after the user answered the consent screen. [personalized] = user agreed to personalised ads. */
    fun initialize(context: Context, personalized: Boolean)

    /** The user changed the choice in Settings. */
    fun setPersonalized(context: Context, personalized: Boolean)
    fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean
    fun createBanner(context: Context): View?

    /** Shows one full-screen ad now; false when none could be shown. The caller applies the daily cap. */
    fun showInterstitial(context: Context): Boolean
}

object AdPolicy {
    /** Never interrupt or decorate the filtering flow; no interstitials between sessions (only after a Details page, see [AdMoments]). */
    fun allowed(placement: AdPlacement, userIsFiltering: Boolean): Boolean = when {
        userIsFiltering -> false
        placement == AdPlacement.INTERSTITIAL_BETWEEN_SESSIONS -> false
        else -> true
    }
}

class NoOpAdsProvider : AdsProvider {
    override val enabled: Boolean = false
    override fun initialize(context: Context, personalized: Boolean) {}
    override fun setPersonalized(context: Context, personalized: Boolean) {}
    override fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean = false
    override fun createBanner(context: Context): View? = null
    override fun showInterstitial(context: Context): Boolean = false
}

object AdsModule {
    /** [AdsBackend] lives in src/noads or src/startio depending on ADS_ENABLED. */
    fun provide(startIoAppId: String): AdsProvider = AdsBackend.create(startIoAppId)
}
