package com.tvseries.Movies.ads

object AdsBackend {
    fun create(appId: String): AdsProvider = NoOpAdsProvider()
}
