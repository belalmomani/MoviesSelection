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
 * Placements: ONLY a banner on Home and on Details. Never on the filter / mood screens ([AdPolicy]).
 * Interstitials are intentionally not implemented.
 */
enum class AdPlacement { HOME_BANNER, DETAILS_BANNER, INTERSTITIAL_BETWEEN_SESSIONS }

interface AdsProvider {
    val enabled: Boolean
    fun initialize(context: Context)
    fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean
    fun createBanner(context: Context): View?
}

object AdPolicy {
    /** Never interrupt or decorate the filtering flow; no interstitials. */
    fun allowed(placement: AdPlacement, userIsFiltering: Boolean): Boolean = when {
        userIsFiltering -> false
        placement == AdPlacement.INTERSTITIAL_BETWEEN_SESSIONS -> false
        else -> true
    }
}

class NoOpAdsProvider : AdsProvider {
    override val enabled: Boolean = false
    override fun initialize(context: Context) {}
    override fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean = false
    override fun createBanner(context: Context): View? = null
}

object AdsModule {
    /** [AdsBackend] lives in src/noads or src/startio depending on ADS_ENABLED. */
    fun provide(startIoAppId: String): AdsProvider = AdsBackend.create(startIoAppId)
}
