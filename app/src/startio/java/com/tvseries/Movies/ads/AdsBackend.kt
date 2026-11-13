package com.tvseries.Movies.ads

import android.content.Context
import android.view.View
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.tvseries.Movies.BuildConfig

class StartIoAdsProvider(private val appId: String) : AdsProvider {
    override val enabled: Boolean = true

    @Volatile
    private var ready = false

    override fun initialize(context: Context, personalized: Boolean) {
        // The user's choice is passed to the ad SDK BEFORE it starts ("pas" = personalised ad serving).
        StartAppSDK.setUserConsent(context, "pas", System.currentTimeMillis(), personalized)
        StartAppSDK.init(context, appId, false)
        StartAppAd.disableSplash()
        // Debug builds show TEST ads only (clicking real ads on your own build can get the account banned).
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
        ready = true
    }

    override fun setPersonalized(context: Context, personalized: Boolean) {
        StartAppSDK.setUserConsent(context, "pas", System.currentTimeMillis(), personalized)
    }

    /** No ad is shown before the user has answered the consent screen (the SDK is not even started). */
    override fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean =
        ready && AdPolicy.allowed(placement, userIsFiltering)

    override fun createBanner(context: Context): View? = Banner(context)
}

object AdsBackend {
    fun create(appId: String): AdsProvider =
        if (appId.isBlank()) NoOpAdsProvider() else StartIoAdsProvider(appId)
}
