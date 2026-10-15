package com.tvseries.Movies.ads

import android.content.Context

/**
 * Ads integration point (Start.io) — PREPARED ONLY.
 *
 * Current state: no ad SDK is on the classpath, no app id is configured, no ad request is ever made and
 * no ad space exists in any screen. [AdsModule.provide] always returns [NoOpAdsProvider].
 *
 * To enable later (see README, section "Start.io"):
 *  1. add the Start.io SDK dependency in app/build.gradle.kts,
 *  2. add a real provider implementing [AdsProvider] in this package,
 *  3. return it from [AdsModule.provide] when BuildConfig.ADS_ENABLED is true,
 *  4. call [AdsProvider.canShowAt] from the screens that host a placement.
 * Ads must never be shown during filtering: see [AdPolicy].
 */
enum class AdPlacement { HOME_BANNER, DETAILS_BANNER, INTERSTITIAL_BETWEEN_SESSIONS }

interface AdsProvider {
    val enabled: Boolean
    fun initialize(context: Context)
    fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean
}

object AdPolicy {
    /** Never interrupt or decorate the filtering flow. */
    fun allowed(placement: AdPlacement, userIsFiltering: Boolean): Boolean = when {
        userIsFiltering -> false
        else -> placement == AdPlacement.HOME_BANNER ||
            placement == AdPlacement.DETAILS_BANNER ||
            placement == AdPlacement.INTERSTITIAL_BETWEEN_SESSIONS
    }
}

class NoOpAdsProvider : AdsProvider {
    override val enabled: Boolean = false
    override fun initialize(context: Context) {}
    override fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean = false
}

object AdsModule {
    fun provide(): AdsProvider = NoOpAdsProvider()
}
