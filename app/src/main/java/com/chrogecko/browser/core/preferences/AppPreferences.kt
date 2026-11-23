package com.chrogecko.browser.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mozilla.geckoview.GeckoRuntimeSettings

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "chrogecko_preferences")

/** A new tab page shortcut the user added by hand, with an optional custom label. */
data class PinnedShortcut(val url: String, val label: String)

/** A custom RSS/Atom feed configured for the new tab page Discover section. */
data class RssFeedSource(val url: String, val title: String = "")

const val DISCOVER_MODE_HACKER_NEWS = "hacker_news"
const val DISCOVER_MODE_RSS = "rss"

/** Hacker News feeds published by the public Firebase API. */
val DISCOVER_SOURCES = listOf("top", "new", "best", "show")

/** Valid values of the optional extra toolbar button. */
val TOOLBAR_SHORTCUTS = listOf("none", "home", "newTab", "bookmarks", "history", "closeTab")

@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {

    val forceDark: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[FORCE_DARK] ?: false }
        .distinctUntilChanged()

    /** Gecko's enhanced tracking protection, applied to every session. */
    val trackingProtection: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[TRACKING_PROTECTION] ?: true }
        .distinctUntilChanged()

    /** HTTPS-only enforcement mode across normal and private tabs. */
    val httpsOnlyMode: Flow<HttpsOnlyMode> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { HttpsOnlyMode.fromId(it[HTTPS_ONLY_MODE]) }
        .distinctUntilChanged()

    /** Third-party and cross-site cookie policy. */
    val cookiePolicy: Flow<CookiePolicy> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { CookiePolicy.fromId(it[COOKIE_POLICY]) }
        .distinctUntilChanged()

    /** Purges state stored by redirect-based bounce trackers. */
    val bounceTrackingProtection: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[BOUNCE_TRACKING_PROTECTION] ?: true }
        .distinctUntilChanged()

    /** Automatic cookie-consent banner handling mode. */
    val cookieBannerMode: Flow<CookieBannerMode> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { CookieBannerMode.fromId(it[COOKIE_BANNER_MODE]) }
        .distinctUntilChanged()

    /** Gecko Safe Browsing protection against phishing and malware sites. */
    val safeBrowsing: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[SAFE_BROWSING] ?: true }
        .distinctUntilChanged()

    /** Global Privacy Control (`Sec-GPC`) opt-out signal sent to websites. */
    val globalPrivacyControl: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[GLOBAL_PRIVACY_CONTROL] ?: true }
        .distinctUntilChanged()

    /** Strips known UTM/click-tracking query parameters on navigation. */
    val queryParameterStripping: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[QUERY_PARAM_STRIPPING] ?: true }
        .distinctUntilChanged()

    /** DNS-over-HTTPS (Trusted Recursive Resolver) mode. */
    val dohMode: Flow<DohMode> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { DohMode.fromId(it[DOH_MODE]) }
        .distinctUntilChanged()

    /** Selected DNS-over-HTTPS provider preset or custom endpoint. */
    val dohProvider: Flow<DohProvider> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { DohProvider.fromId(it[DOH_PROVIDER]) }
        .distinctUntilChanged()

    /** Custom DNS-over-HTTPS resolver URL used when [dohProvider] is [DohProvider.CUSTOM]. */
    val dohCustomUri: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[DOH_CUSTOM_URI] ?: "" }
        .distinctUntilChanged()

    /** Tier-2 engine used by the bundled dark-mode extension: "invert" or "dynamic". */
    val darkEngine: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[DARK_ENGINE] ?: "invert" }
        .distinctUntilChanged()

    val toolbarPosition: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOOLBAR_POSITION] ?: "top" }
        .distinctUntilChanged()

    /** Chrome-style behaviour: hide the toolbar while the page scrolls down. */
    val toolbarAutoHide: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOOLBAR_AUTO_HIDE] ?: true }
        .distinctUntilChanged()

    /** Optional extra toolbar button: none, home, newTab, bookmarks, history or closeTab. */
    val toolbarShortcut: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOOLBAR_SHORTCUT] ?: "none" }
        .distinctUntilChanged()

    /** Offer completion suggestions while typing in the omnibox. */
    val searchSuggestions: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[SEARCH_SUGGESTIONS] ?: true }
        .distinctUntilChanged()

    /** Include matching bookmarks in omnibox suggestions. */
    val suggestionsFromBookmarks: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[SUGGESTIONS_BOOKMARKS] ?: true }
        .distinctUntilChanged()

    /** Include matching history entries in omnibox suggestions. */
    val suggestionsFromHistory: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[SUGGESTIONS_HISTORY] ?: true }
        .distinctUntilChanged()

    val searchEngine: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[SEARCH_ENGINE] ?: "duckduckgo" }
        .distinctUntilChanged()

    /** Display name for the user's custom search engine. */
    val customSearchName: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[CUSTOM_SEARCH_NAME] ?: "" }
        .distinctUntilChanged()

    /** URL template (containing `%s`) for the user's custom search engine. */
    val customSearchTemplate: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[CUSTOM_SEARCH_TEMPLATE] ?: "" }
        .distinctUntilChanged()

    /** Where finished downloads are saved: public Downloads or the app's private folder. */
    val downloadLocation: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map {
            val value = it[DOWNLOAD_LOCATION] ?: "downloads"
            if (value == "app") "app" else "downloads"
        }
        .distinctUntilChanged()

    /** Whether the new tab page shows the Discover feed at all. */
    val discoverEnabled: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[DISCOVER_ENABLED] ?: true }
        .distinctUntilChanged()

    /** Discover feed type: Hacker News lists or user-added RSS/Atom feeds. */
    val discoverMode: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { if (it[DISCOVER_MODE] == DISCOVER_MODE_RSS) DISCOVER_MODE_RSS else DISCOVER_MODE_HACKER_NEWS }
        .distinctUntilChanged()

    /** User-configured RSS/Atom feeds for Discover, in the order they were added. */
    val discoverRssFeeds: Flow<List<RssFeedSource>> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseRssFeeds(prefs[DISCOVER_RSS_FEEDS].orEmpty()) }
        .distinctUntilChanged()

    /** Hacker News lists used for Discover; can be any subset of [DISCOVER_SOURCES]. */
    val discoverSources: Flow<Set<String>> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val saved = prefs[DISCOVER_SOURCES_KEY]
            val value = if (saved == null) {
                // Migrate the old single-source preference on first read.
                when (prefs[DISCOVER_SOURCE]) {
                    "new" -> setOf("new")
                    else -> setOf("top")
                }
            } else {
                saved.filter { it in DISCOVER_SOURCES }.toSet()
            }
            value.ifEmpty { setOf("top") }
        }
        .distinctUntilChanged()

    /** Number of articles requested for the Discover feed. */
    val discoverLimit: Flow<Int> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[DISCOVER_LIMIT] ?: 12 }
        .distinctUntilChanged()

    /** Article URLs the user marked as "not interested". */
    val hiddenDiscover: Flow<Set<String>> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[HIDDEN_DISCOVER] ?: emptySet() }
        .distinctUntilChanged()

    /** Hosts the user removed from the new tab page shortcuts. */
    val hiddenShortcuts: Flow<Set<String>> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[HIDDEN_SHORTCUTS] ?: emptySet() }
        .distinctUntilChanged()

    /** User-pinned new tab page shortcuts, in the order they were added. */
    val customShortcuts: Flow<List<PinnedShortcut>> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parsePinned(prefs[CUSTOM_SHORTCUTS].orEmpty()) }
        .distinctUntilChanged()

    /** Master switch for the bundled text expander extension. */
    val textExpanderEnabled: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[TEXT_EXPANDER] ?: true }
        .distinctUntilChanged()

    val appLanguage: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { AppLanguage.fromTag(it[APP_LANGUAGE]).tag }
        .distinctUntilChanged()

    val androidAutofillEnabled: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[ANDROID_AUTOFILL] ?: true }
        .distinctUntilChanged()

    val builtInPasswordSaveEnabled: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[BUILTIN_PASSWORD_SAVE] ?: true }
        .distinctUntilChanged()

    /** Automatic background checks against GitHub Releases. */
    val autoUpdateEnabled: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUTO_UPDATE_ENABLED] ?: true }
        .distinctUntilChanged()

    /** Whether experimental/pre-release GitHub builds are offered as updates. */
    val autoUpdatePrereleases: Flow<Boolean> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUTO_UPDATE_PRERELEASES] ?: true }
        .distinctUntilChanged()

    /** Epoch millis of the last completed GitHub Releases check. */
    val lastUpdateCheckMs: Flow<Long> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_UPDATE_CHECK_MS] ?: 0L }
        .distinctUntilChanged()

    /** Release tag dismissed by the user in the update prompt. */
    val dismissedUpdateTag: Flow<String> = context.preferences.data
        .catch { emit(emptyPreferences()) }
        .map { it[DISMISSED_UPDATE_TAG] ?: "" }
        .distinctUntilChanged()

    val preferredColorScheme: Flow<Int> = forceDark.map { enabled ->
        if (enabled) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    }.distinctUntilChanged()

    suspend fun setForceDark(enabled: Boolean) {
        context.preferences.edit { it[FORCE_DARK] = enabled }
    }

    suspend fun hideShortcut(host: String) {
        if (host.isBlank()) return
        context.preferences.edit { prefs ->
            prefs[HIDDEN_SHORTCUTS] = (prefs[HIDDEN_SHORTCUTS] ?: emptySet()) + host
        }
    }

    suspend fun restoreShortcuts() {
        context.preferences.edit { it.remove(HIDDEN_SHORTCUTS) }
    }

    suspend fun addCustomShortcut(url: String, label: String = "") {
        val cleanUrl = sanitizeField(url)
        if (cleanUrl.isBlank()) return
        val cleanLabel = sanitizeField(label)
        context.preferences.edit { prefs ->
            val current = parsePinned(prefs[CUSTOM_SHORTCUTS].orEmpty())
            if (current.none { it.url == cleanUrl }) {
                prefs[CUSTOM_SHORTCUTS] = encodePinned(current + PinnedShortcut(cleanUrl, cleanLabel))
            }
        }
    }

    suspend fun renameCustomShortcut(url: String, label: String) {
        val cleanLabel = sanitizeField(label)
        context.preferences.edit { prefs ->
            val current = parsePinned(prefs[CUSTOM_SHORTCUTS].orEmpty())
            prefs[CUSTOM_SHORTCUTS] = encodePinned(
                current.map { if (it.url == url) it.copy(label = cleanLabel) else it },
            )
        }
    }

    suspend fun removeCustomShortcut(url: String) {
        context.preferences.edit { prefs ->
            val current = parsePinned(prefs[CUSTOM_SHORTCUTS].orEmpty()).filterNot { it.url == url }
            prefs[CUSTOM_SHORTCUTS] = encodePinned(current)
        }
    }

    private fun parsePinned(raw: String): List<PinnedShortcut> = raw.split('\n')
        .filter { it.isNotBlank() }
        .map { line ->
            val url = line.substringBefore('|')
            PinnedShortcut(url = url, label = line.substringAfter('|', "").trim())
        }

    private fun encodePinned(items: List<PinnedShortcut>): String =
        items.joinToString("\n") { "${sanitizeField(it.url)}|${sanitizeField(it.label)}" }

    private fun sanitizeField(value: String): String =
        value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun parseRssFeeds(raw: String): List<RssFeedSource> = raw.split('\n')
        .filter { it.isNotBlank() }
        .map { line ->
            val url = line.substringBefore('|').trim()
            RssFeedSource(url = url, title = line.substringAfter('|', "").trim())
        }
        .filter { it.url.isNotEmpty() }

    private fun encodeRssFeeds(items: List<RssFeedSource>): String =
        items.joinToString("\n") { "${sanitizeField(it.url)}|${sanitizeField(it.title)}" }

    suspend fun setTextExpanderEnabled(enabled: Boolean) {
        context.preferences.edit { it[TEXT_EXPANDER] = enabled }
    }

    suspend fun setToolbarPosition(position: String) {
        if (position != "top" && position != "bottom") return
        context.preferences.edit { it[TOOLBAR_POSITION] = position }
    }

    suspend fun setDarkEngine(engine: String) {
        if (engine != "invert" && engine != "dynamic") return
        context.preferences.edit { it[DARK_ENGINE] = engine }
    }

    suspend fun setSearchEngine(id: String) {
        context.preferences.edit { it[SEARCH_ENGINE] = id }
    }

    suspend fun setCustomSearchEngine(name: String, template: String) {
        context.preferences.edit {
            it[CUSTOM_SEARCH_NAME] = name.trim()
            it[CUSTOM_SEARCH_TEMPLATE] = template.trim()
            it[SEARCH_ENGINE] = com.chrogecko.browser.feature.chromeui.SearchEngines.CUSTOM_ID
        }
    }

    suspend fun setAppLanguage(tag: String) {
        val normalized = AppLanguage.fromTag(tag).tag
        AppLocaleManager.writeSavedTag(context, normalized)
        context.preferences.edit { it[APP_LANGUAGE] = normalized }
    }

    suspend fun setToolbarAutoHide(enabled: Boolean) {
        context.preferences.edit { it[TOOLBAR_AUTO_HIDE] = enabled }
    }

    suspend fun setToolbarShortcut(shortcut: String) {
        val normalized = if (shortcut in TOOLBAR_SHORTCUTS) shortcut else "none"
        context.preferences.edit { it[TOOLBAR_SHORTCUT] = normalized }
    }

    suspend fun setSearchSuggestions(enabled: Boolean) {
        context.preferences.edit { it[SEARCH_SUGGESTIONS] = enabled }
    }

    suspend fun setSuggestionsFromBookmarks(enabled: Boolean) {
        context.preferences.edit { it[SUGGESTIONS_BOOKMARKS] = enabled }
    }

    suspend fun setSuggestionsFromHistory(enabled: Boolean) {
        context.preferences.edit { it[SUGGESTIONS_HISTORY] = enabled }
    }

    suspend fun setDownloadLocation(location: String) {
        context.preferences.edit { it[DOWNLOAD_LOCATION] = if (location == "app") "app" else "downloads" }
    }

    suspend fun setDiscoverEnabled(enabled: Boolean) {
        context.preferences.edit { it[DISCOVER_ENABLED] = enabled }
    }

    suspend fun setDiscoverMode(mode: String) {
        val normalized = if (mode == DISCOVER_MODE_RSS) DISCOVER_MODE_RSS else DISCOVER_MODE_HACKER_NEWS
        context.preferences.edit { it[DISCOVER_MODE] = normalized }
    }

    suspend fun addDiscoverRssFeed(url: String, title: String = "") {
        val cleanUrl = sanitizeField(url)
        val cleanTitle = sanitizeField(title)
        if (cleanUrl.isEmpty()) return
        context.preferences.edit { prefs ->
            val existing = parseRssFeeds(prefs[DISCOVER_RSS_FEEDS].orEmpty())
            val withoutDuplicate = existing.filterNot { it.url.equals(cleanUrl, ignoreCase = true) }
            val updated = (withoutDuplicate + RssFeedSource(cleanUrl, cleanTitle)).takeLast(20)
            prefs[DISCOVER_RSS_FEEDS] = encodeRssFeeds(updated)
        }
    }

    suspend fun removeDiscoverRssFeed(url: String) {
        context.preferences.edit { prefs ->
            val existing = parseRssFeeds(prefs[DISCOVER_RSS_FEEDS].orEmpty())
            prefs[DISCOVER_RSS_FEEDS] = encodeRssFeeds(
                existing.filterNot { it.url.equals(url.trim(), ignoreCase = true) },
            )
        }
    }

    suspend fun setDiscoverSources(sources: Set<String>) {
        val normalized = DiscoverSources.normalize(sources)
        context.preferences.edit {
            it[DISCOVER_SOURCES_KEY] = normalized
            it.remove(DISCOVER_SOURCE)
        }
    }

    suspend fun setDiscoverLimit(limit: Int) {
        context.preferences.edit { it[DISCOVER_LIMIT] = limit.coerceIn(4, 24) }
    }

    suspend fun hideDiscoverArticle(url: String) {
        context.preferences.edit { prefs ->
            prefs[HIDDEN_DISCOVER] = (prefs[HIDDEN_DISCOVER] ?: emptySet()) + url
        }
    }

    suspend fun resetHiddenDiscoverArticles() {
        context.preferences.edit { it.remove(HIDDEN_DISCOVER) }
    }

    suspend fun setAndroidAutofillEnabled(enabled: Boolean) {
        context.preferences.edit { it[ANDROID_AUTOFILL] = enabled }
    }

    suspend fun setTrackingProtection(enabled: Boolean) {
        context.preferences.edit { it[TRACKING_PROTECTION] = enabled }
    }

    suspend fun setHttpsOnlyMode(mode: HttpsOnlyMode) {
        context.preferences.edit { it[HTTPS_ONLY_MODE] = mode.id }
    }

    suspend fun setCookiePolicy(policy: CookiePolicy) {
        context.preferences.edit { it[COOKIE_POLICY] = policy.id }
    }

    suspend fun setBounceTrackingProtection(enabled: Boolean) {
        context.preferences.edit { it[BOUNCE_TRACKING_PROTECTION] = enabled }
    }

    suspend fun setCookieBannerMode(mode: CookieBannerMode) {
        context.preferences.edit { it[COOKIE_BANNER_MODE] = mode.id }
    }

    suspend fun setSafeBrowsing(enabled: Boolean) {
        context.preferences.edit { it[SAFE_BROWSING] = enabled }
    }

    suspend fun setGlobalPrivacyControl(enabled: Boolean) {
        context.preferences.edit { it[GLOBAL_PRIVACY_CONTROL] = enabled }
    }

    suspend fun setQueryParameterStripping(enabled: Boolean) {
        context.preferences.edit { it[QUERY_PARAM_STRIPPING] = enabled }
    }

    suspend fun setDohMode(mode: DohMode) {
        context.preferences.edit { it[DOH_MODE] = mode.id }
    }

    suspend fun setDohProvider(provider: DohProvider) {
        context.preferences.edit { it[DOH_PROVIDER] = provider.id }
    }

    suspend fun setDohCustomUri(uri: String) {
        context.preferences.edit { it[DOH_CUSTOM_URI] = uri }
    }

    suspend fun setBuiltInPasswordSaveEnabled(enabled: Boolean) {
        context.preferences.edit { it[BUILTIN_PASSWORD_SAVE] = enabled }
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.preferences.edit { it[AUTO_UPDATE_ENABLED] = enabled }
    }

    suspend fun setAutoUpdatePrereleases(enabled: Boolean) {
        context.preferences.edit { it[AUTO_UPDATE_PRERELEASES] = enabled }
    }

    suspend fun setLastUpdateCheckMs(timestampMs: Long) {
        context.preferences.edit { it[LAST_UPDATE_CHECK_MS] = timestampMs }
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        context.preferences.edit { it[DISMISSED_UPDATE_TAG] = tag }
    }

    private companion object {
        val FORCE_DARK = booleanPreferencesKey("force_dark_mode")
        val TRACKING_PROTECTION = booleanPreferencesKey("tracking_protection")
        val HTTPS_ONLY_MODE = stringPreferencesKey("https_only_mode")
        val COOKIE_POLICY = stringPreferencesKey("cookie_policy")
        val BOUNCE_TRACKING_PROTECTION = booleanPreferencesKey("bounce_tracking_protection")
        val COOKIE_BANNER_MODE = stringPreferencesKey("cookie_banner_mode")
        val SAFE_BROWSING = booleanPreferencesKey("safe_browsing")
        val GLOBAL_PRIVACY_CONTROL = booleanPreferencesKey("global_privacy_control")
        val QUERY_PARAM_STRIPPING = booleanPreferencesKey("query_param_stripping")
        val DOH_MODE = stringPreferencesKey("doh_mode")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val DOH_CUSTOM_URI = stringPreferencesKey("doh_custom_uri")
        val DARK_ENGINE = stringPreferencesKey("dark_engine")
        val TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val TOOLBAR_AUTO_HIDE = booleanPreferencesKey("toolbar_auto_hide")
        val TOOLBAR_SHORTCUT = stringPreferencesKey("toolbar_shortcut")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        val SUGGESTIONS_BOOKMARKS = booleanPreferencesKey("suggestions_bookmarks")
        val SUGGESTIONS_HISTORY = booleanPreferencesKey("suggestions_history")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH_NAME = stringPreferencesKey("custom_search_name")
        val CUSTOM_SEARCH_TEMPLATE = stringPreferencesKey("custom_search_template")
        val TEXT_EXPANDER = booleanPreferencesKey("text_expander_enabled")
        val HIDDEN_SHORTCUTS = stringSetPreferencesKey("hidden_shortcuts")
        val CUSTOM_SHORTCUTS = stringPreferencesKey("custom_shortcuts")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
        val DISCOVER_ENABLED = booleanPreferencesKey("discover_enabled")
        val DISCOVER_MODE = stringPreferencesKey("discover_mode")
        val DISCOVER_SOURCE = stringPreferencesKey("discover_source")
        val DISCOVER_SOURCES_KEY = stringSetPreferencesKey("discover_sources")
        val DISCOVER_RSS_FEEDS = stringPreferencesKey("discover_rss_feeds")
        val DISCOVER_LIMIT = intPreferencesKey("discover_limit")
        val HIDDEN_DISCOVER = stringSetPreferencesKey("hidden_discover")
        val ANDROID_AUTOFILL = booleanPreferencesKey("android_autofill_enabled")
        val BUILTIN_PASSWORD_SAVE = booleanPreferencesKey("builtin_password_save_enabled")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val AUTO_UPDATE_PRERELEASES = booleanPreferencesKey("auto_update_prereleases")
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
    }
}
