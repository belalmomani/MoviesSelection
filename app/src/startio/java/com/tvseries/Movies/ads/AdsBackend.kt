package com.tvseries.Movies.ads

import android.content.Context
import android.view.View
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.tvseries.Movies.BuildConfig

class StartIoAdsProvider(private val appId: String) : AdsProvider {
    override val enabled: Boolean = true

    override fun initialize(context: Context) {
        StartAppSDK.init(context, appId, false)
        StartAppAd.disableSplash()
        // Debug builds show TEST ads only (clicking real ads on your own build can get the account banned).
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
    }

    override fun canShowAt(placement: AdPlacement, userIsFiltering: Boolean): Boolean =
        AdPolicy.allowed(placement, userIsFiltering)

    override fun createBanner(context: Context): View? = Banner(context)
}

object AdsBackend {
    fun create(appId: String): AdsProvider =
        if (appId.isBlank()) NoOpAdsProvider() else StartIoAdsProvider(appId)
}
