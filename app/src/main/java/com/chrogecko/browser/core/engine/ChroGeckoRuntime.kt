package com.chrogecko.browser.core.engine

import android.content.Context
import com.chrogecko.browser.BuildConfig
import com.chrogecko.browser.core.preferences.AppLanguage
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.core.preferences.CookieBannerMode
import com.chrogecko.browser.core.preferences.CookiePolicy
import com.chrogecko.browser.core.preferences.DohMode
import com.chrogecko.browser.core.preferences.DohProvider
import com.chrogecko.browser.core.preferences.DohUriValidator
import com.chrogecko.browser.core.preferences.HttpsOnlyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

/**
 * Process-wide Gecko runtime. Creating more than one runtime is not supported by GeckoView,
 * so every session and extension in the app shares this instance.
 */
@Singleton
class ChroGeckoRuntime @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Application context, shared with sessions that need to talk to Android (SAF, permissions). */
    val appContext: Context get() = context

    val runtime: GeckoRuntime by lazy { GeckoRuntime.create(context, buildSettings()) }

    fun warmUp() {
        runtime
    }

    /** Clears cookies, caches or site data; flags come from [StorageController.ClearFlags]. */
    fun clearData(flags: Long) = runtime.storageController.clearData(flags)

    fun applyColorScheme(colorScheme: Int) {
        runtime.settings.preferredColorScheme = colorScheme
    }

    fun applyLocales(locales: Array<String>?) {
        runtime.settings.locales = locales
    }

    fun applyHttpsOnlyMode(mode: HttpsOnlyMode) {
        runtime.settings.allowInsecureConnections = mode.geckoValue
    }

    fun applyCookiePolicy(policy: CookiePolicy) {
        runtime.settings.contentBlocking
            .setCookieBehavior(policy.geckoValue)
            .setCookieBehaviorPrivateMode(policy.geckoValue)
    }

    fun applyCookieBannerMode(mode: CookieBannerMode) {
        val enabled = mode != CookieBannerMode.DISABLED
        runtime.settings.contentBlocking
            .setCookieBannerMode(mode.geckoValue)
            .setCookieBannerModePrivateBrowsing(mode.geckoValue)
            .setCookieBannerGlobalRulesEnabled(enabled)
            .setCookieBannerGlobalRulesSubFramesEnabled(enabled)
    }

    fun applySafeBrowsing(enabled: Boolean) {
        runtime.settings.contentBlocking.setSafeBrowsing(
            if (enabled) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE,
        )
    }

    fun applyGlobalPrivacyControl(enabled: Boolean) {
        runtime.settings.setGlobalPrivacyControl(enabled)
    }

    fun applyBounceTrackingProtection(enabled: Boolean) {
        val mode = if (enabled) {
            ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
        } else {
            ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_DISABLED
        }
        runtime.settings.contentBlocking.setBounceTrackingProtectionMode(mode)
    }

    fun applyQueryParameterStripping(enabled: Boolean) {
        runtime.settings.contentBlocking
            .setQueryParameterStrippingEnabled(enabled)
            .setQueryParameterStrippingPrivateBrowsingEnabled(enabled)
    }

    fun applyDoh(mode: DohMode, provider: DohProvider, customUri: String) {
        val uri = if (provider == DohProvider.CUSTOM) {
            DohUriValidator.normalize(customUri)
        } else {
            provider.uri
        }
        val settings = runtime.settings
        if (mode == DohMode.OFF || uri == null) {
            settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            settings.setTrustedRecursiveResolverUri("")
        } else {
            settings.setTrustedRecursiveResolverUri(uri)
            settings.setTrustedRecursiveResolverMode(mode.geckoMode)
        }
    }

    private fun buildSettings(): GeckoRuntimeSettings {
        val contentBlocking = ContentBlocking.Settings.Builder()
            .cookieBehavior(CookiePolicy.ISOLATE_CROSS_SITE.geckoValue)
            .cookieBehaviorPrivateMode(CookiePolicy.ISOLATE_CROSS_SITE.geckoValue)
            .cookieBannerHandlingMode(CookieBannerMode.REJECT.geckoValue)
            .cookieBannerHandlingModePrivateBrowsing(CookieBannerMode.REJECT.geckoValue)
            .cookieBannerGlobalRulesEnabled(true)
            .cookieBannerGlobalRulesSubFramesEnabled(true)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .bounceTrackingProtectionMode(
                ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED,
            )
            .build()
        val builder = GeckoRuntimeSettings.Builder()
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .loginAutofillEnabled(true)
            .globalPrivacyControlEnabled(true)
            .contentBlocking(contentBlocking)
            // Private tabs refuse plaintext http by default; user preference may tighten or relax this.
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE)
            .consoleOutput(BuildConfig.DEBUG)
        val savedLanguage = AppLanguage.fromTag(AppLocaleManager.readSavedTag(context))
        AppLocaleManager.geckoLocalesFor(savedLanguage)?.let { builder.locales(it) }
        return builder.build()
    }
}
