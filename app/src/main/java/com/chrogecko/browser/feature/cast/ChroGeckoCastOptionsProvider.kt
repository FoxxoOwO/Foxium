package com.chrogecko.browser.feature.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Casts to the default media receiver so no Google Cast developer registration is required.
 * The local Ktor proxy (feature/cast) hands the receiver a LAN URL it can fetch without auth.
 */
class ChroGeckoCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(CastMediaOptions.Builder().build())
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
