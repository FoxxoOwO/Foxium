package com.chrogecko.browser.feature.chromeui

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrogecko.browser.core.engine.AutofillController
import com.chrogecko.browser.core.engine.ChroGeckoRuntime
import com.chrogecko.browser.core.engine.ContextMenu
import com.chrogecko.browser.core.engine.PermissionAsk
import com.chrogecko.browser.core.engine.EnginePrompt
import com.chrogecko.browser.core.engine.AndroidPermissionAsk
import com.chrogecko.browser.core.engine.SelectionMenu
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeEvent
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import com.chrogecko.browser.core.network.ExternalIntentHandler
import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import com.chrogecko.browser.core.database.entity.HistoryEntity
import com.chrogecko.browser.core.database.entity.DownloadEntity
import com.chrogecko.browser.core.database.entity.TabEntity
import com.chrogecko.browser.core.database.entity.TabGroupEntity
import com.chrogecko.browser.core.database.entity.UserscriptEntity
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.entity.SiteRuleEntity
import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import com.chrogecko.browser.core.database.repository.BookmarkRepository
import com.chrogecko.browser.core.database.repository.HistoryRepository
import com.chrogecko.browser.core.database.repository.AutofillOption
import com.chrogecko.browser.core.database.repository.AutofillRepository
import com.chrogecko.browser.core.database.repository.LoginRepository
import com.chrogecko.browser.core.database.repository.SavedCard
import com.chrogecko.browser.core.database.repository.SavedLogin
import com.chrogecko.browser.core.database.repository.SiteRuleRepository
import com.chrogecko.browser.core.database.repository.TabRepository
import com.chrogecko.browser.core.engine.EngineSession
import com.chrogecko.browser.core.engine.GeckoEngine
import com.chrogecko.browser.core.engine.SessionState
import com.chrogecko.browser.core.preferences.AppLanguage
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.core.preferences.CookieBannerMode
import com.chrogecko.browser.core.preferences.CookiePolicy
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_HACKER_NEWS
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_RSS
import com.chrogecko.browser.core.preferences.DiscoverSources
import com.chrogecko.browser.core.preferences.AppPreferences
import com.chrogecko.browser.core.preferences.DohMode
import com.chrogecko.browser.core.preferences.DohProvider
import com.chrogecko.browser.core.preferences.HttpsOnlyMode
import com.chrogecko.browser.core.preferences.PinnedShortcut
import com.chrogecko.browser.core.preferences.RssFeedSource
import com.chrogecko.browser.feature.addons.AddonManagerController
import com.chrogecko.browser.feature.addons.AmoInstallUrls
import com.chrogecko.browser.feature.addons.ExtensionPopup
import com.chrogecko.browser.feature.addons.InstalledAddon
import com.chrogecko.browser.feature.addons.PendingAddonInstall
import com.chrogecko.browser.feature.cast.CastManager
import com.chrogecko.browser.feature.cast.CastMedia
import com.chrogecko.browser.feature.cast.CastPlaybackState
import com.chrogecko.browser.feature.darkmode.DarkModeEngine
import com.chrogecko.browser.feature.darkmode.DarkModeFeature
import com.chrogecko.browser.feature.expander.TextExpanderFeature
import com.chrogecko.browser.feature.reader.ReaderModeFeature
import com.chrogecko.browser.feature.darkmode.DarkModeFeature.Companion.hostOf
import com.chrogecko.browser.feature.downloads.DownloadManager
import com.chrogecko.browser.feature.favicons.FaviconRepository
import com.chrogecko.browser.feature.discover.DiscoverItem
import com.chrogecko.browser.feature.discover.DiscoverRepository
import com.chrogecko.browser.feature.discover.RssFeedParser
import com.chrogecko.browser.feature.search.SearchSuggestion
import com.chrogecko.browser.feature.updates.AppUpdateManager
import com.chrogecko.browser.feature.updates.AvailableUpdate
import com.chrogecko.browser.feature.updates.UpdateState
import com.chrogecko.browser.feature.favicons.faviconHost
import com.chrogecko.browser.feature.userscripts.ParsedUserScript
import com.chrogecko.browser.feature.userscripts.UpdateCheck
import com.chrogecko.browser.feature.userscripts.UserScriptFeature
import com.chrogecko.browser.feature.userscripts.UserScriptMenuCommand
import com.chrogecko.browser.feature.userscripts.UserScriptParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.io.File
import javax.inject.Inject
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import com.chrogecko.browser.R

data class BrowserTab(
    val id: String,
    val session: EngineSession,
    val isPrivate: Boolean,
    val groupId: String? = null,
    val state: SessionState = SessionState(),
    val thumbnail: Bitmap? = null,
)

data class TabGroup(
    val id: String,
    val title: String,
    val color: Long,
)

/** Enough of a closed tab to bring it back from the undo snackbar. */
data class ClosedTab(
    val url: String,
    val isPrivate: Boolean,
    val groupId: String?,
    val wasActive: Boolean = false,
)

data class PendingUserScript(
    val parsed: ParsedUserScript,
    val source: String,
    val sourceUrl: String?,
)

data class FindState(
    val visible: Boolean = false,
    val query: String = "",
    val found: Boolean = false,
    val total: Int = 0,
    val current: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val engine: GeckoEngine,
    private val bookmarks: BookmarkRepository,
    private val history: HistoryRepository,
    private val preferences: AppPreferences,
    private val darkMode: DarkModeFeature,
    private val addons: AddonManagerController,
    private val userscripts: UserScriptFeature,
    private val cast: CastManager,
    private val tabRepository: TabRepository,
    private val downloadManager: DownloadManager,
    private val faviconRepository: FaviconRepository,
    private val bridge: NativeMessagingBridge,
    private val runtime: ChroGeckoRuntime,
    private val autofill: AutofillController,
    private val loginRepository: LoginRepository,
    private val autofillRepository: AutofillRepository,
    private val siteRules: SiteRuleRepository,
    private val textExpander: TextExpanderFeature,
    private val readerMode: ReaderModeFeature,
    private val discoverRepository: DiscoverRepository,
    private val suggestionRepository: com.chrogecko.browser.feature.search.SearchSuggestionRepository,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {

    val favicons: StateFlow<Map<String, ImageBitmap>> = faviconRepository.icons

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _groups = MutableStateFlow<List<TabGroup>>(emptyList())
    val groups: StateFlow<List<TabGroup>> = _groups.asStateFlow()

    private val _undoMessage = MutableStateFlow<String?>(null)

    /** Non-null while the "Tab closed / Undo" snackbar is relevant. */
    val undoMessage: StateFlow<String?> = _undoMessage.asStateFlow()

    private var closedTabs: List<ClosedTab> = emptyList()

    val activeTab: StateFlow<BrowserTab?> =
        combine(_tabs, _activeTabId) { tabs, id -> tabs.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeTabIsPrivate: StateFlow<Boolean> = activeTab
        .map { it?.isPrivate == true }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _contextMenu = MutableStateFlow<ContextMenu?>(null)

    /** Long-press targets of the active tab, driving the Chrome context menu. */
    val contextMenu: StateFlow<ContextMenu?> = _contextMenu.asStateFlow()

    @Volatile
    private var backGestureActive = false

    @Volatile
    private var suppressContextMenuUntilMs = 0L

    private val _prompt = MutableStateFlow<EnginePrompt?>(null)

    /** Active tab's blocking Gecko dialog (`alert`, `confirm`, `prompt`, file picker, auth). */
    val prompt: StateFlow<EnginePrompt?> = _prompt.asStateFlow()

    private val _permission = MutableStateFlow<PermissionAsk?>(null)

    /** Site permission the active tab is waiting for. */
    val permission: StateFlow<PermissionAsk?> = _permission.asStateFlow()

    private val _androidPermissions = MutableStateFlow<AndroidPermissionAsk?>(null)

    /** Android runtime permissions Gecko needs; the UI requests them, then answers. */
    val androidPermissions: StateFlow<AndroidPermissionAsk?> = _androidPermissions.asStateFlow()

    private val _selection = MutableStateFlow<SelectionMenu?>(null)

    /** Text the user long-pressed, driving the selection action bar. */
    val selection: StateFlow<SelectionMenu?> = _selection.asStateFlow()

    private val _fullscreen = MutableStateFlow(false)

    /** True while a page element asked for the whole screen; hides the browser chrome. */
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    /** Progress of the page-level pull-to-refresh gesture (0..1); null when idle. */
    data class PullRefreshState(val progress: Float, val triggered: Boolean)

    private val _pullRefresh = MutableStateFlow<PullRefreshState?>(null)
    val pullRefresh: StateFlow<PullRefreshState?> = _pullRefresh.asStateFlow()
    private var pullHideJob: Job? = null

    private val _omniboxText = MutableStateFlow("")
    val omniboxText: StateFlow<String> = _omniboxText.asStateFlow()

    private val _omniboxFocused = MutableStateFlow(false)
    val omniboxFocused: StateFlow<Boolean> = _omniboxFocused.asStateFlow()

    private val _omniboxSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val omniboxSuggestions: StateFlow<List<SearchSuggestion>> = _omniboxSuggestions.asStateFlow()
    private var suggestJob: Job? = null

    private val _gridOpen = MutableStateFlow(false)
    val gridOpen: StateFlow<Boolean> = _gridOpen.asStateFlow()

    private val _find = MutableStateFlow(FindState())
    val findState: StateFlow<FindState> = _find.asStateFlow()

    private val _bookmarked = MutableStateFlow(false)
    val bookmarked: StateFlow<Boolean> = _bookmarked.asStateFlow()

    private val _currentHost = MutableStateFlow<String?>(null)
    val currentHost: StateFlow<String?> = _currentHost.asStateFlow()

    private val _siteDarkMode = MutableStateFlow(false)
    val siteDarkMode: StateFlow<Boolean> = _siteDarkMode.asStateFlow()

    private val _desktopMode = MutableStateFlow(false)
    val desktopMode: StateFlow<Boolean> = _desktopMode.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val appLanguage: StateFlow<String> = preferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLocaleManager.readSavedTag(runtime.appContext))
    val androidAutofillEnabled: StateFlow<Boolean> = preferences.androidAutofillEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val builtInPasswordSaveEnabled: StateFlow<Boolean> = preferences.builtInPasswordSaveEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private fun localizedString(@StringRes id: Int, vararg args: Any): String =
        AppLocaleManager.localizedResources(runtime.appContext, appLanguage.value).getString(id, *args)

    private fun localizedPlural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        AppLocaleManager.localizedResources(runtime.appContext, appLanguage.value)
            .getQuantityString(id, quantity, *args)

    fun setAppLanguage(language: AppLanguage) {
        AppLocaleManager.applyPlatformLocale(runtime.appContext, language)
        runtime.applyLocales(AppLocaleManager.geckoLocalesFor(language))
        viewModelScope.launch { preferences.setAppLanguage(language.tag) }
    }

    /** Picks up a language chosen in Android 13+ system settings. */
    fun syncSystemLanguage() {
        val tag = AppLocaleManager.syncFromSystemIfApplicable(runtime.appContext) ?: return
        if (tag == appLanguage.value) return
        AppLocaleManager.writeSavedTag(runtime.appContext, tag)
        runtime.applyLocales(AppLocaleManager.geckoLocalesFor(AppLanguage.fromTag(tag)))
        viewModelScope.launch { preferences.setAppLanguage(tag) }
    }

    fun setAndroidAutofillEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAndroidAutofillEnabled(enabled) }
    }

    fun setBuiltInPasswordSaveEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setBuiltInPasswordSaveEnabled(enabled) }
    }

    val forceDark: StateFlow<Boolean> = preferences.forceDark
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val darkEngine: StateFlow<String> = preferences.darkEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, DarkModeEngine.INVERT.id)
    val trackingProtection: StateFlow<Boolean> = preferences.trackingProtection
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val httpsOnlyMode: StateFlow<HttpsOnlyMode> = preferences.httpsOnlyMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, HttpsOnlyMode.PRIVATE_ONLY)
    val cookiePolicy: StateFlow<CookiePolicy> = preferences.cookiePolicy
        .stateIn(viewModelScope, SharingStarted.Eagerly, CookiePolicy.ISOLATE_CROSS_SITE)
    val bounceTrackingProtection: StateFlow<Boolean> = preferences.bounceTrackingProtection
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val cookieBannerMode: StateFlow<CookieBannerMode> = preferences.cookieBannerMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, CookieBannerMode.REJECT)
    val safeBrowsing: StateFlow<Boolean> = preferences.safeBrowsing
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val globalPrivacyControl: StateFlow<Boolean> = preferences.globalPrivacyControl
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val queryParameterStripping: StateFlow<Boolean> = preferences.queryParameterStripping
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val dohMode: StateFlow<DohMode> = preferences.dohMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, DohMode.OFF)
    val dohProvider: StateFlow<DohProvider> = preferences.dohProvider
        .stateIn(viewModelScope, SharingStarted.Eagerly, DohProvider.CLOUDFLARE)
    val dohCustomUri: StateFlow<String> = preferences.dohCustomUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    /** Hosts removed from the new tab page shortcuts. */
    val hiddenShortcuts: StateFlow<Set<String>> = preferences.hiddenShortcuts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val customShortcuts: StateFlow<List<PinnedShortcut>> = preferences.customShortcuts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _discoverItems = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val discoverItems: StateFlow<List<DiscoverItem>> = _discoverItems.asStateFlow()
    private var discoverRequested = false

    val discoverEnabled: StateFlow<Boolean> = preferences.discoverEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val discoverMode: StateFlow<String> = preferences.discoverMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, DISCOVER_MODE_HACKER_NEWS)
    val discoverSources: StateFlow<Set<String>> = preferences.discoverSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, setOf("top"))
    val discoverRssFeeds: StateFlow<List<RssFeedSource>> = preferences.discoverRssFeeds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val discoverLimit: StateFlow<Int> = preferences.discoverLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, 12)
    val hiddenDiscover: StateFlow<Set<String>> = preferences.hiddenDiscover
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Loads the Discover feed. Set [force] to re-evaluate changed preferences and [bypassCache]
     * to force a fresh network fetch instead of reading the 10-minute repository cache.
     */
    fun loadDiscover(force: Boolean = false, bypassCache: Boolean = force) {
        if (!force && discoverRequested) return
        discoverRequested = true
        if (!discoverEnabled.value) {
            _discoverItems.value = emptyList()
            return
        }
        val mode = discoverMode.value
        val rssFeeds = discoverRssFeeds.value
        if (mode == DISCOVER_MODE_RSS && rssFeeds.isEmpty()) {
            _discoverItems.value = emptyList()
            return
        }
        viewModelScope.launch {
            val items = discoverRepository.suggestions(
                limit = discoverLimit.value,
                force = bypassCache,
                sources = discoverSources.value,
                hidden = hiddenDiscover.value,
                mode = mode,
                rssFeeds = rssFeeds,
            )
            if (items.isNotEmpty() || !bypassCache || mode == DISCOVER_MODE_RSS) {
                _discoverItems.value = items
            }
        }
    }

    fun setDiscoverEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDiscoverEnabled(enabled) }
    }

    fun setDiscoverMode(mode: String) {
        viewModelScope.launch { preferences.setDiscoverMode(mode) }
    }

    fun setDiscoverSources(sources: Set<String>) {
        viewModelScope.launch { preferences.setDiscoverSources(sources) }
    }

    fun toggleDiscoverSource(source: String, enabled: Boolean) {
        setDiscoverSources(DiscoverSources.toggle(discoverSources.value, source, enabled))
    }

    fun addDiscoverRssFeed(url: String, title: String = "") {
        val normalized = RssFeedParser.normalizeFeedUrl(url)
        if (normalized == null) {
            viewModelScope.launch { _messages.emit(localizedString(R.string.discover_rss_invalid_url)) }
            return
        }
        viewModelScope.launch {
            preferences.addDiscoverRssFeed(normalized, title)
            if (discoverMode.value != DISCOVER_MODE_RSS) {
                preferences.setDiscoverMode(DISCOVER_MODE_RSS)
            }
        }
    }

    fun removeDiscoverRssFeed(url: String) {
        viewModelScope.launch { preferences.removeDiscoverRssFeed(url) }
    }

    fun setDiscoverLimit(limit: Int) {
        viewModelScope.launch { preferences.setDiscoverLimit(limit) }
    }

    fun hideDiscoverArticle(url: String) {
        viewModelScope.launch { preferences.hideDiscoverArticle(url) }
    }

    fun resetHiddenDiscoverArticles() {
        viewModelScope.launch { preferences.resetHiddenDiscoverArticles() }
    }

    fun addCustomShortcut(input: String, label: String = "") {
        val trimmed = input.trim()
        if (!trimmed.contains('.')) {
            viewModelScope.launch { _messages.emit(localizedString(R.string.msg_enter_web_address)) }
            return
        }
        val url = normalizeOmniboxInput(trimmed)
        viewModelScope.launch { preferences.addCustomShortcut(url, label) }
    }

    fun renameCustomShortcut(url: String, label: String) {
        viewModelScope.launch { preferences.renameCustomShortcut(url, label) }
    }

    fun removeCustomShortcut(url: String) {
        viewModelScope.launch { preferences.removeCustomShortcut(url) }
    }

    val toolbarBottom: StateFlow<Boolean> = preferences.toolbarPosition
        .map { it == "bottom" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val downloadLocation: StateFlow<String> = preferences.downloadLocation
        .stateIn(viewModelScope, SharingStarted.Eagerly, "downloads")

    val toolbarAutoHide: StateFlow<Boolean> = preferences.toolbarAutoHide
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val toolbarShortcut: StateFlow<String> = preferences.toolbarShortcut
        .stateIn(viewModelScope, SharingStarted.Eagerly, "none")
    val searchSuggestions: StateFlow<Boolean> = preferences.searchSuggestions
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val suggestionsFromBookmarks: StateFlow<Boolean> = preferences.suggestionsFromBookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val suggestionsFromHistory: StateFlow<Boolean> = preferences.suggestionsFromHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _toolbarHidden = MutableStateFlow(false)
    val toolbarHidden: StateFlow<Boolean> = _toolbarHidden.asStateFlow()
    private var lastScrollY = 0

    /** Chrome-style auto-hide: hide while scrolling down, reveal on any upward movement. */
    fun onPageScroll(y: Int) {
        val next = ToolbarAutoHidePolicy.next(y, lastScrollY, _toolbarHidden.value)
        lastScrollY = next.lastScrollY
        _toolbarHidden.value = next.hidden
    }

    fun resetToolbarVisibility() {
        lastScrollY = 0
        _toolbarHidden.value = false
    }

    fun setDownloadLocation(location: String) {
        viewModelScope.launch { preferences.setDownloadLocation(location) }
    }

    fun setToolbarAutoHide(enabled: Boolean) {
        viewModelScope.launch { preferences.setToolbarAutoHide(enabled) }
        if (!enabled) resetToolbarVisibility()
    }

    fun setToolbarShortcut(shortcut: String) {
        viewModelScope.launch { preferences.setToolbarShortcut(shortcut) }
    }

    fun setSearchSuggestions(enabled: Boolean) {
        viewModelScope.launch { preferences.setSearchSuggestions(enabled) }
    }

    fun setSuggestionsFromBookmarks(enabled: Boolean) {
        viewModelScope.launch { preferences.setSuggestionsFromBookmarks(enabled) }
    }

    fun setSuggestionsFromHistory(enabled: Boolean) {
        viewModelScope.launch { preferences.setSuggestionsFromHistory(enabled) }
    }

    val searchEngine: StateFlow<String> = preferences.searchEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngines.DEFAULT_ID)
    val customSearchName: StateFlow<String> = preferences.customSearchName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val customSearchTemplate: StateFlow<String> = preferences.customSearchTemplate
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val activeSearchEngine: StateFlow<SearchEngine> = combine(
        preferences.searchEngine,
        preferences.customSearchName,
        preferences.customSearchTemplate,
    ) { id, name, template -> SearchEngines.resolve(id, name, template) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngines.byId(null))
    val installedAddons: StateFlow<List<InstalledAddon>> = addons.addons
    val addonPopup: StateFlow<ExtensionPopup?> = addons.popup
    val amoAddonSlug: StateFlow<String?> = activeTab
        .map { tab -> tab?.state?.url?.let(AmoInstallUrls::addonSlug) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun installAmoAddon() = installAmoAddonFromUrl(activeTab.value?.state?.url.orEmpty())

    fun installAmoAddonFromUrl(url: String) {
        if (!addons.installFromAmoPage(url)) {
            viewModelScope.launch { _messages.emit(localizedString(R.string.addons_amo_invalid_page)) }
        }
    }
    private val _pendingUserScript = MutableStateFlow<PendingUserScript?>(null)
    val pendingUserScript: StateFlow<PendingUserScript?> = _pendingUserScript.asStateFlow()
    val userscriptMenuCommands: StateFlow<List<UserScriptMenuCommand>> =
        combine(activeTab, userscripts.menuCommands) { tab, commands ->
            commands[tab?.state?.url].orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scripts: StateFlow<List<UserscriptEntity>> = userscripts.scripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentTabs: StateFlow<List<TabEntity>> = tabRepository.recentTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarkList: StateFlow<List<BookmarkEntity>> = bookmarks.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarkFolders: StateFlow<List<BookmarkFolderEntity>> = bookmarks.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historyList: StateFlow<List<HistoryEntity>> = history.observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = downloadManager.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Download progress cannot be shown until the user approves notifications. */
    val notificationPermissionRequests: Flow<Unit> = merge(
        downloadManager.notificationPermissionRequests,
        appUpdateManager.notificationPermissionRequests,
    )
    val castPlayback: StateFlow<CastPlaybackState> = cast.playback
    val loginSuggestion: StateFlow<AutofillController.Suggestion?> = autofill.suggestion
    val saveLoginRequest: StateFlow<AutofillController.SaveRequest?> = autofill.saveRequest
    val savedLogins: StateFlow<List<SavedLogin>> = loginRepository.logins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedAddresses: StateFlow<List<AddressEntity>> = autofillRepository.addresses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedCards: StateFlow<List<SavedCard>> = autofillRepository.cards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _autofillOptions = MutableStateFlow<List<AutofillOption>>(emptyList())
    val autofillOptions: StateFlow<List<AutofillOption>> = _autofillOptions.asStateFlow()
    private val _autofillKind = MutableStateFlow("address")
    val autofillKind: StateFlow<String> = _autofillKind.asStateFlow()
    private var autofillTabId: Long = -1L
    private val _mirrorUrl = MutableStateFlow<String?>(null)
    val mirrorUrl: StateFlow<String?> = _mirrorUrl.asStateFlow()
    val allCastMedia: StateFlow<Map<Long, List<CastMedia>>> = cast.media

    val updateState: StateFlow<UpdateState> = appUpdateManager.state
    val autoUpdateEnabled: StateFlow<Boolean> = preferences.autoUpdateEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoUpdatePrereleases: StateFlow<Boolean> = preferences.autoUpdatePrereleases
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var groupCounter = 0
    /** Per-site rules mirrored from Room so a navigation can apply them synchronously. */
    private var siteRuleCache: Map<String, SiteRuleEntity> = emptyMap()

    private var lastRecordedUrl = ""
    private var restoring = false
    private val observers = mutableMapOf<String, Job>()

    /** Last URL whose icons were requested, per tab, so one page is queried once. */
    private val iconQueriedUrls = mutableMapOf<String, String>()

    /** Script-opened child tabs (`window.open`) remember their opener so `window.close()` returns there. */
    private val openerTabIds = HashMap<String, String>()

    private val restored = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch { preferences.trackingProtection.collect { engine.applyTrackingProtection(it) } }
        viewModelScope.launch { preferences.httpsOnlyMode.collect { runtime.applyHttpsOnlyMode(it) } }
        viewModelScope.launch { preferences.cookiePolicy.collect { runtime.applyCookiePolicy(it) } }
        viewModelScope.launch {
            preferences.bounceTrackingProtection.collect { runtime.applyBounceTrackingProtection(it) }
        }
        viewModelScope.launch {
            preferences.cookieBannerMode.collect { runtime.applyCookieBannerMode(it) }
        }
        viewModelScope.launch {
            preferences.safeBrowsing.collect { runtime.applySafeBrowsing(it) }
        }
        viewModelScope.launch {
            preferences.globalPrivacyControl.collect { runtime.applyGlobalPrivacyControl(it) }
        }
        viewModelScope.launch {
            preferences.queryParameterStripping.collect { runtime.applyQueryParameterStripping(it) }
        }
        viewModelScope.launch {
            combine(
                preferences.dohMode,
                preferences.dohProvider,
                preferences.dohCustomUri,
            ) { mode, provider, uri -> Triple(mode, provider, uri) }
                .distinctUntilChanged()
                .collect { (mode, provider, uri) -> runtime.applyDoh(mode, provider, uri) }
        }
        viewModelScope.launch {
            siteRules.rules.collect { rules -> siteRuleCache = rules.associateBy { it.host } }
        }
        viewModelScope.launch { restoreTabs() }
        watchMirror()
        watchContextMenu()
        watchSessionDialogs()
        viewModelScope.launch {
            activeTab.collectLatest { tab -> onActiveTabChanged(tab) }
        }
        viewModelScope.launch {
            activeTab.map { it?.state?.url }.distinctUntilChanged().collectLatest { url ->
                _bookmarked.value = url != null && url != "about:blank" &&
                    bookmarks.isBookmarked(url)
                val host = url?.let(DarkModeFeature::hostOf)
                _currentHost.value = host
                val explicit = host?.let { darkMode.explicitMode(it) }
                _siteDarkMode.value = explicit?.let { it != com.chrogecko.browser.core.database.entity.DarkModeMode.OFF }
                    ?: forceDark.value
            }
        }
        viewModelScope.launch {
            forceDark.collect { enabled ->
                val host = _currentHost.value
                if (host == null) {
                    _siteDarkMode.value = false
                } else {
                    val explicit = darkMode.explicitMode(host)
                    _siteDarkMode.value = explicit?.let {
                        it != com.chrogecko.browser.core.database.entity.DarkModeMode.OFF
                    } ?: enabled
                }
            }
        }
        viewModelScope.launch {
            cast.errors.collect { _messages.tryEmit(it) }
        }
        viewModelScope.launch {
            userscripts.errors.collect { error -> if (error != null) _messages.tryEmit(error) }
        }
        viewModelScope.launch {
            addons.messages.collect { _messages.tryEmit(it) }
        }
        viewModelScope.launch {
            appUpdateManager.checkForUpdates(force = false)
        }
    }

    fun checkForUpdatesNow() {
        viewModelScope.launch { appUpdateManager.checkForUpdates(force = true) }
    }

    fun downloadAvailableUpdate(update: AvailableUpdate) {
        appUpdateManager.startDownload(update)
    }

    fun hideUpdateDownloadToBackground() = appUpdateManager.hideDownloadToBackground()

    fun cancelUpdateDownload() = appUpdateManager.cancelDownload()

    fun installReadyUpdate() {
        val current = appUpdateManager.state.value
        if (current is UpdateState.ReadyToInstall) {
            appUpdateManager.launchInstaller(current.apkFile)
        }
    }

    fun dismissUpdateBanner(tagName: String) {
        viewModelScope.launch { appUpdateManager.dismissUpdateBanner(tagName) }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdateEnabled(enabled) }
    }

    fun setAutoUpdatePrereleases(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdatePrereleases(enabled) }
    }

    fun createTab(isPrivate: Boolean, groupId: String? = null, url: String? = null): String {
        val session = engine.createSession(isPrivate)
        val tab = BrowserTab(
            id = session.id,
            session = session,
            isPrivate = isPrivate,
            groupId = groupId,
        )
        _tabs.update { it + tab }
        observeTab(tab)
        selectTab(tab.id)
        if (url != null) session.load(url)
        if (!isPrivate) {
            viewModelScope.launch {
                tabRepository.saveTab(
                    TabEntity(
                        id = tab.id,
                        url = url,
                        groupId = groupId,
                        isPrivate = false,
                        sortOrder = tabRepository.nextSortOrder(false),
                    ),
                )
            }
        }
        return tab.id
    }

    private fun createChildSession(
        isPrivate: Boolean,
        groupId: String?,
        uri: String,
        parentTabId: String? = null,
    ): GeckoSession {
        val session = engine.createSession(isPrivate = isPrivate, openNow = false)
        val tab = BrowserTab(
            id = session.id,
            session = session,
            isPrivate = isPrivate,
            groupId = groupId,
        )
        if (parentTabId != null) openerTabIds[tab.id] = parentTabId
        _tabs.update { it + tab }
        observeTab(tab)
        selectTab(tab.id)
        if (!isPrivate) {
            viewModelScope.launch {
                tabRepository.saveTab(
                    TabEntity(
                        id = tab.id,
                        url = uri.takeIf { it.isNotBlank() && it != "about:blank" },
                        groupId = groupId,
                        isPrivate = false,
                        sortOrder = tabRepository.nextSortOrder(false),
                    ),
                )
            }
        }
        return session.geckoSession
    }

    private suspend fun restoreTabs() {
        try {
            restoreTabsLocked()
        } finally {
            restored.complete(Unit)
        }
    }

    private suspend fun restoreTabsLocked() {
        tabRepository.clearPrivateTabs()
        val savedGroups = tabRepository.groupsOnce()
        _groups.value = savedGroups.map {
            TabGroup(it.id, it.title, it.color.toLong() and 0xFFFFFFFFL)
        }
        groupCounter = savedGroups.size
        val saved = tabRepository.normalTabs()
        if (saved.isEmpty()) {
            createTab(isPrivate = false)
            return
        }
        restoring = true
        val restoredTabs = saved.map { entity ->
            val session = engine.createSession(isPrivate = false)
            entity to BrowserTab(
                id = session.id,
                session = session,
                isPrivate = false,
                groupId = entity.groupId,
            )
        }
        val active = restoredTabs.maxByOrNull { it.first.lastActiveAt }?.second
            ?: restoredTabs.first().second
        _tabs.value = restoredTabs.map { it.second }
        restoredTabs.forEach { (entity, tab) ->
            tab.session.setActive(tab.id == active.id)
            observeTab(tab)
            val url = entity.url
            val state = entity.sessionState
            // Restore first: Gecko replays the tab's history and its scroll offsets.
            if (state == null || !tab.session.restoreState(state)) {
                if (url != null && url != "about:blank") tab.session.load(url)
            }
        }
        val replacementEntities = restoredTabs.mapIndexed { index, (entity, tab) ->
            TabEntity(
                id = tab.id,
                url = tab.state.url.takeIf { it != "about:blank" } ?: entity.url,
                title = entity.title,
                groupId = entity.groupId,
                isPrivate = false,
                sortOrder = index,
                lastActiveAt = entity.lastActiveAt,
                sessionState = entity.sessionState,
            )
        }
        tabRepository.replaceNormalTabs(replacementEntities)
        restoring = false
        selectTab(active.id)
    }


    fun selectTab(id: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        val previous = activeTab.value
        if (previous != null && previous.id != id) {
            previous.session.clearFind()
            captureThumbnail(previous)
        }
        _tabs.value.forEach { it.session.setActive(it.id == id) }
        _activeTabId.value = id
        if (!tab.isPrivate && !restoring) {
            viewModelScope.launch { tabRepository.touch(id) }
        }
        _desktopMode.value = tab.session.desktopMode
        _gridOpen.value = false
        _find.value = FindState()
    }

    fun closeTab(
        id: String,
        recordUndo: Boolean = true,
        allowReplacement: Boolean = true,
        keepGridOpen: Boolean = false,
    ) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        val wasActive = _activeTabId.value == id
        val gridWasOpen = _gridOpen.value
        if (recordUndo) {
            rememberClosed(listOf(tab), localizedString(if (tab.isPrivate) R.string.msg_incognito_tab_closed else R.string.msg_tab_closed))
        }
        val parentId = openerTabIds.remove(id)
        openerTabIds.values.removeAll { it == id }
        _tabs.update { list -> list.filterNot { it.id == id } }
        observers.remove(id)?.cancel()
        iconQueriedUrls.remove(id)
        engine.closeSession(id)
        if (!tab.isPrivate) viewModelScope.launch { tabRepository.closeTab(id) }
        if (!wasActive) return
        if (parentId != null && _tabs.value.any { it.id == parentId }) {
            selectTab(parentId)
        } else {
            when (
                val replacement = TabClosePolicy.decide(
                    closingIsPrivate = tab.isPrivate,
                    remaining = _tabs.value.map { it.id to it.isPrivate },
                    allowReplacement = allowReplacement,
                )
            ) {
                is TabReplacement.Select -> selectTab(replacement.id)
                is TabReplacement.Create -> createTab(replacement.isPrivate)
                TabReplacement.None -> Unit
            }
        }
        // selectTab/createTab leave the grid; closing a card in the switcher keeps it open.
        if (keepGridOpen && gridWasOpen) _gridOpen.value = true
    }

    fun undoClose() {
        val restored = closedTabs
        closedTabs = emptyList()
        _undoMessage.value = null
        val restoredIds = restored.map { closed ->
            createTab(
                isPrivate = closed.isPrivate,
                groupId = closed.groupId,
                url = closed.url.ifBlank { null },
            )
        }
        // createTab() selects each new tab; reselect the originally active one by its new id.
        val activeIndex = restored.indexOfFirst { it.wasActive }
        if (activeIndex >= 0 && activeIndex < restoredIds.size) {
            selectTab(restoredIds[activeIndex])
        }
    }

    fun dismissUndo() {
        closedTabs = emptyList()
        _undoMessage.value = null
    }

    private fun rememberClosed(tabs: List<BrowserTab>, message: String, activeId: String? = _activeTabId.value) {
        if (tabs.isEmpty()) return
        closedTabs = tabs.map {
            ClosedTab(it.state.url, it.isPrivate, it.groupId, it.id == activeId)
        }
        _undoMessage.value = message
    }

    /** Grid card menu: opens a copy of the tab next to the original, keeping its group. */
    fun duplicateTab(id: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        createTab(isPrivate = tab.isPrivate, groupId = tab.groupId, url = tab.state.url)
    }

    /** Grid card menu: closes every other tab of the same profile. */
    fun closeOtherTabs(id: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        val closing = _tabs.value.filter { it.id != id && it.isPrivate == tab.isPrivate }
        val activeBefore = _activeTabId.value
        closing.forEach { closeTab(it.id, recordUndo = false) }
        rememberClosed(
            closing,
            localizedPlural(R.plurals.msg_tabs_closed, closing.size, closing.size),
            activeId = activeBefore,
        )
        if (_activeTabId.value != id) selectTab(id)
    }

    fun closeAllPrivateTabs() {
        val privateTabs = _tabs.value.filter { it.isPrivate }
        val activeBefore = _activeTabId.value
        privateTabs.forEach { closeTab(it.id, recordUndo = false, allowReplacement = false) }
        rememberClosed(
            privateTabs,
            activeId = activeBefore,
            message = if (privateTabs.size == 1) {
                localizedString(R.string.msg_incognito_tab_closed)
            } else {
                localizedPlural(R.plurals.msg_tabs_closed, privateTabs.size, privateTabs.size)
            },
        )
    }

    fun switchAdjacentTab(delta: Int) {
        val current = activeTab.value ?: return
        val siblings = _tabs.value.filter { it.isPrivate == current.isPrivate }
        val index = siblings.indexOfFirst { it.id == current.id }
        val target = siblings.getOrNull(index + delta) ?: return
        selectTab(target.id)
    }

    fun selectProfile(isPrivate: Boolean) {
        val target = _tabs.value.firstOrNull { it.isPrivate == isPrivate }
        if (target == null) {
            createTab(isPrivate)
        } else {
            selectTab(target.id)
        }
    }

    fun setGridOpen(open: Boolean) {
        _gridOpen.value = open
        if (open) captureThumbnails()
    }

    fun onOmniboxChange(text: String) {
        _omniboxText.value = text
        refreshSuggestions(text)
    }

    private fun refreshSuggestions(text: String) {
        suggestJob?.cancel()
        val query = text.trim()
        if (activeTab.value?.isPrivate == true || !searchSuggestions.value || query.length < 2) {
            _omniboxSuggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            val needle = query.lowercase()
            val local = buildList {
                if (suggestionsFromHistory.value) {
                    historyList.value
                        .filter { it.title.contains(needle, true) || it.url.contains(needle, true) }
                        .distinctBy { it.url }
                        .take(3)
                        .forEach { add(SearchSuggestion(it.title.ifBlank { it.url }, it.url, SearchSuggestion.Kind.HISTORY)) }
                }
                if (suggestionsFromBookmarks.value) {
                    bookmarkList.value
                        .filter { it.title.contains(needle, true) || it.url.contains(needle, true) }
                        .distinctBy { it.url }
                        .take(3)
                        .forEach { add(SearchSuggestion(it.title.ifBlank { it.url }, it.url, SearchSuggestion.Kind.BOOKMARK)) }
                }
            }
            val template = activeSearchEngine.value.template
            val remote = suggestionRepository.engineSuggestions(query, searchEngine.value)
                .map { SearchSuggestion(it, normalizeOmniboxInput(it, template), SearchSuggestion.Kind.QUERY) }
            _omniboxSuggestions.value = SuggestionMerger.merge(local, remote)
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        _omniboxSuggestions.value = emptyList()
    }

    fun onOmniboxFocusChanged(focused: Boolean) {
        _omniboxFocused.value = focused
        if (!focused) clearSuggestions()
        // Do not restore the tab URL on blur: the IME may drop focus before onGo
        // is delivered, which would discard what the user just typed.
        if (!focused && _omniboxText.value.isBlank()) {
            _omniboxText.value = displayUrl(activeTab.value?.state?.url.orEmpty())
        }
    }

    /** The new tab page has no URL, so the omnibox shows its hint instead of about:blank. */
    private fun displayUrl(url: String): String =
        if (url == "about:blank" || url.isBlank()) "" else url

    val siteRuleList: StateFlow<List<SiteRuleEntity>> = siteRules.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSiteRule(
        host: String,
        blockJavascript: Boolean,
        desktopSite: Boolean,
        readerModeForSite: Boolean = false,
    ) {
        if (host.isBlank()) return
        viewModelScope.launch {
            siteRules.set(host, blockJavascript, desktopSite, readerModeForSite)
            val tab = activeTab.value
            val activeHost = tab?.let { hostOf(it.state.url) }
            if (tab != null && activeHost != null && activeHost.removePrefix("www.") == host.removePrefix("www.")) {
                tab.session.applySiteRules(blockJavascript, desktopSite)
                _desktopMode.value = tab.session.desktopMode
                // Session settings only affect the next document, so reload to show the effect.
                tab.session.reload()
            }
        }
    }

    fun deleteSiteRule(host: String) {
        viewModelScope.launch {
            siteRules.delete(host)
            val tab = activeTab.value
            val activeHost = tab?.let { hostOf(it.state.url) }
            if (tab != null && activeHost != null && activeHost.removePrefix("www.") == host.removePrefix("www.")) {
                tab.session.applySiteRules(false, false)
                _desktopMode.value = false
                tab.session.reload()
            }
        }
    }

    val textExpanderEnabled: StateFlow<Boolean> = textExpander.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val textSnippets: StateFlow<List<TextSnippetEntity>> = textExpander.snippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTextExpanderEnabled(enabled: Boolean) {
        viewModelScope.launch { textExpander.setEnabled(enabled) }
    }

    fun saveTextSnippet(id: Long, trigger: String, expansion: String) {
        viewModelScope.launch {
            if (textExpander.save(id, trigger, expansion) == 0L) {
                _messages.tryEmit(localizedString(R.string.msg_trigger_one_word))
            }
        }
    }

    fun setTextSnippetEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { textExpander.setSnippetEnabled(id, enabled) }
    }

    fun deleteTextSnippet(id: Long) {
        viewModelScope.launch { textExpander.delete(id) }
    }

    val readerStates: StateFlow<Map<String, ReaderModeFeature.ReaderState>> = readerMode.states

    /** Turns reading mode on or off for the visible tab. */
    fun toggleReaderMode() {
        readerMode.toggle(activeTab.value?.state?.url.orEmpty())
    }

    fun readerState(url: String): ReaderModeFeature.ReaderState = readerMode.stateFor(url)

    fun addSiteRule(host: String) {
        val clean = host.trim().substringAfter("://").substringBefore('/').substringBefore(':')
        if (clean.isBlank()) return
        setSiteRule(clean.removePrefix("www."), false, false)
    }

    private val _printFile = MutableStateFlow<File?>(null)
    val printFile: StateFlow<File?> = _printFile.asStateFlow()

    /** Asks Gecko to render the active page to PDF; [printFile] receives the result. */
    fun printPage() {
        val session = activeTab.value?.session ?: return
        if (!session.printPage()) _printFile.value = null
    }

    fun consumePrint() { _printFile.value = null }

    fun submit() = navigate(_omniboxText.value)

    fun goHome() {
        val tab = activeTab.value ?: return
        tab.session.load("about:blank")
        _omniboxText.value = ""
        _omniboxFocused.value = false
    }

    fun navigate(input: String) {
        val target = normalizeOmniboxInput(input, activeSearchEngine.value.template)
        val tab = activeTab.value ?: return
        if (target.substringBefore('?').endsWith(".user.js", ignoreCase = true)) {
            installUserscript(target)
            return
        }
        tab.session.load(target)
        _omniboxText.value = target
        _omniboxFocused.value = false
    }

    fun openContextUrl(url: String, isPrivate: Boolean = false) {
        dismissContextMenu()
        createTab(isPrivate = isPrivate, url = url)
        _gridOpen.value = false
    }

    fun downloadContextUrl(url: String) {
        downloadManager.enqueueUrl(url, activeTab.value?.state?.url)
        dismissContextMenu()
    }

    /** Chrome's "Clear browsing data": the page data lives in Gecko, the rest in Room. */
    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean, downloads: Boolean) {
        viewModelScope.launch {
            if (history) this@BrowserViewModel.history.clear()
            if (downloads) downloadManager.clear()
            var flags = 0L
            if (cookies) {
                flags = flags or StorageController.ClearFlags.COOKIES or
                    StorageController.ClearFlags.DOM_STORAGES or
                    StorageController.ClearFlags.SITE_DATA or
                    StorageController.ClearFlags.PERMISSIONS
            }
            if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
            if (flags == 0L) {
                _messages.tryEmit(localizedString(R.string.msg_browsing_data_cleared))
                return@launch
            }
            runtime.clearData(flags).accept(
                { _messages.tryEmit(localizedString(R.string.msg_browsing_data_cleared)) },
                { _messages.tryEmit(localizedString(R.string.msg_clear_data_failed)) },
            )
        }
    }

    // The dialogs are answered on the session that raised them, not necessarily the tab that is
    // active at answer time (an ACTION_VIEW handover or a window.open popup can change it).
    @Volatile
    private var promptSession: EngineSession? = null
    @Volatile
    private var permissionSession: EngineSession? = null
    @Volatile
    private var androidPermissionSession: EngineSession? = null

    fun dismissPrompt() = promptSession?.dismissPrompt()

    fun answerConfirm(positive: Boolean) = promptSession?.answerConfirm(positive)

    fun answerText(value: String) = promptSession?.answerText(value)

    fun answerChoice(index: Int) = promptSession?.answerChoice(index)

    fun answerAuth(user: String, password: String) =
        promptSession?.answerAuth(user, password)

    fun answerFiles(uris: List<android.net.Uri>) = promptSession?.answerFiles(uris)

    fun answerPermission(allow: Boolean) = permissionSession?.answerPermission(allow)

    fun answerAndroidPermissions(granted: Boolean) =
        androidPermissionSession?.answerAndroidPermissions(granted)

    fun copySelection() = activeTab.value?.session?.copySelection()

    fun selectAllSelection() = activeTab.value?.session?.selectAllSelection()

    fun clearSelection() = activeTab.value?.session?.clearSelection()

    fun exitFullscreen() = activeTab.value?.session?.exitFullScreen()

    fun dismissContextMenu() {
        _contextMenu.value = null
        activeTab.value?.session?.dismissContextMenu()
    }

    fun setBackGestureActive(active: Boolean) {
        backGestureActive = active
        if (active) {
            dismissContextMenu()
        } else {
            suppressContextMenuUntilMs = android.os.SystemClock.elapsedRealtime() + 350L
        }
    }

    private fun isContextMenuSuppressed(): Boolean =
        backGestureActive || android.os.SystemClock.elapsedRealtime() < suppressContextMenuUntilMs

    /** External intents wait for the session restore, which otherwise replaces the tab list. */
    fun openExternalUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            restored.await()
            openUrlInNewTab(url)
        }
    }

    fun openUrlInNewTab(url: String) {
        createTab(isPrivate = activeTab.value?.isPrivate ?: false, url = url)
        _gridOpen.value = false
    }

    fun reload() = activeTab.value?.session?.reload()

    fun stop() = activeTab.value?.session?.stop()

    fun goBack() = activeTab.value?.session?.goBack()

    fun goForward() = activeTab.value?.session?.goForward()

    fun toggleBookmark() {
        val tab = activeTab.value ?: return
        if (tab.state.url.isBlank() || tab.state.url == "about:blank") return
        viewModelScope.launch {
            _bookmarked.value = bookmarks.toggle(tab.state.url, tab.state.title)
        }
    }

    fun createBookmarkFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            if (bookmarks.createFolder(name, parentId) == 0L) {
                _messages.emit(localizedString(R.string.msg_folder_name_empty))
            }
        }
    }

    fun deleteBookmarkFolder(id: Long) {
        viewModelScope.launch { bookmarks.deleteFolder(id) }
    }

    fun moveBookmark(id: Long, folderId: Long?) {
        viewModelScope.launch { bookmarks.moveBookmark(id, folderId) }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch { bookmarks.removeById(id) }
    }

    fun toggleSiteDarkMode() {
        val host = _currentHost.value ?: return
        viewModelScope.launch {
            _siteDarkMode.value = darkMode.toggle(host, forceDark.value, darkEngine.value) !=
                com.chrogecko.browser.core.database.entity.DarkModeMode.OFF
        }
    }

    fun hideShortcut(host: String) {
        viewModelScope.launch {
            preferences.hideShortcut(host)
            // A pinned tile is removed outright, otherwise editing could never drop it for good.
            customShortcuts.value
                .filter { faviconHost(it.url) == host }
                .forEach { preferences.removeCustomShortcut(it.url) }
        }
    }

    fun restoreShortcuts() {
        viewModelScope.launch { preferences.restoreShortcuts() }
    }

    fun setToolbarBottom(bottom: Boolean) {
        viewModelScope.launch {
            preferences.setToolbarPosition(if (bottom) "bottom" else "top")
        }
    }

    fun setForceDark(enabled: Boolean) {
        viewModelScope.launch { darkMode.setGlobalEnabled(enabled) }
    }

    fun setTrackingProtection(enabled: Boolean) {
        viewModelScope.launch { preferences.setTrackingProtection(enabled) }
    }

    fun setHttpsOnlyMode(mode: HttpsOnlyMode) {
        viewModelScope.launch { preferences.setHttpsOnlyMode(mode) }
    }

    fun setCookiePolicy(policy: CookiePolicy) {
        viewModelScope.launch { preferences.setCookiePolicy(policy) }
    }

    fun setBounceTrackingProtection(enabled: Boolean) {
        viewModelScope.launch { preferences.setBounceTrackingProtection(enabled) }
    }

    fun setCookieBannerMode(mode: CookieBannerMode) {
        viewModelScope.launch { preferences.setCookieBannerMode(mode) }
    }

    fun setSafeBrowsing(enabled: Boolean) {
        viewModelScope.launch { preferences.setSafeBrowsing(enabled) }
    }

    fun setGlobalPrivacyControl(enabled: Boolean) {
        viewModelScope.launch { preferences.setGlobalPrivacyControl(enabled) }
    }

    fun setDohMode(mode: DohMode) {
        viewModelScope.launch { preferences.setDohMode(mode) }
    }

    fun setDohProvider(provider: DohProvider) {
        viewModelScope.launch { preferences.setDohProvider(provider) }
    }

    fun setDohCustomUri(uri: String) {
        viewModelScope.launch { preferences.setDohCustomUri(uri) }
    }

    fun setDarkEngine(engine: DarkModeEngine) {
        viewModelScope.launch { darkMode.setEngine(engine) }
    }

    fun setSearchEngine(id: String) {
        viewModelScope.launch { preferences.setSearchEngine(id) }
    }

    fun setQueryParameterStripping(enabled: Boolean) {
        viewModelScope.launch { preferences.setQueryParameterStripping(enabled) }
    }

    fun setCustomSearchEngine(name: String, template: String) {
        viewModelScope.launch { preferences.setCustomSearchEngine(name, template) }
    }

    fun exportBookmarksToUri(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val html = bookmarks.exportHtml()
                    val output = runtime.appContext.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Unable to open output stream for $uri")
                    output.use { stream ->
                        stream.write(html.toByteArray(Charsets.UTF_8))
                    }
                }
                _messages.tryEmit(localizedString(R.string.msg_bookmarks_exported))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.tryEmit(localizedString(R.string.msg_bookmarks_export_failed))
            }
        }
    }

    fun importBookmarksFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val maxImportBytes = 10 * 1024 * 1024
                val importedCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val html = readUtf8TextWithLimit(uri, maxImportBytes)
                    bookmarks.importHtml(html)
                }
                val message = if (importedCount > 0) {
                    localizedPlural(R.plurals.msg_bookmarks_imported, importedCount, importedCount)
                } else {
                    localizedString(R.string.msg_bookmarks_import_empty)
                }
                _messages.tryEmit(message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.tryEmit(localizedString(R.string.msg_bookmarks_import_failed))
            }
        }
    }

    private fun readUtf8TextWithLimit(uri: android.net.Uri, maxBytes: Int): String {
        val input = runtime.appContext.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Unable to open input stream for $uri")
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw java.io.IOException("Input exceeds $maxBytes bytes")
                }
                out.write(buffer, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    fun setDesktopMode(enabled: Boolean) {
        val tab = activeTab.value ?: return
        tab.session.setDesktopMode(enabled)
        _desktopMode.value = enabled
    }

    fun openFind() {
        _find.value = FindState(visible = true)
    }

    fun updateFindQuery(query: String) {
        val session = activeTab.value?.session
        _find.update { it.copy(query = query) }
        if (session == null || query.isBlank()) {
            session?.clearFind()
            _find.update { it.copy(found = false, total = 0, current = 0) }
            return
        }
        session.find(query) { result ->
            _find.update {
                it.copy(
                    found = result.found,
                    total = result.total.coerceAtLeast(0),
                    current = result.current,
                )
            }
        }
    }

    fun findNext() = findAgain(forward = true)

    fun findPrevious() = findAgain(forward = false)

    private fun findAgain(forward: Boolean) {
        val session = activeTab.value?.session ?: return
        if (_find.value.query.isBlank()) return
        session.findAgain(forward) { result ->
            _find.update {
                it.copy(
                    found = result.found,
                    total = result.total.coerceAtLeast(0),
                    current = result.current,
                )
            }
        }
    }

    fun closeFind() {
        activeTab.value?.session?.clearFind()
        _find.value = FindState()
    }

    fun groupAllTabs() {
        val groupable = _tabs.value.filterNot { it.isPrivate }
        if (groupable.isEmpty()) return
        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            title = localizedString(R.string.tab_group_default_name, ++groupCounter),
            color = GROUP_COLORS[groupCounter % GROUP_COLORS.size],
        )
        _groups.update { it + group }
        val ids = groupable.map { it.id }.toSet()
        _tabs.update { tabs -> tabs.map { if (it.id in ids) it.copy(groupId = group.id) else it } }
        persistGroup(group, ids)
    }

    fun addTabToActiveGroup() {
        val tab = activeTab.value ?: return
        val groupId = tab.groupId ?: return
        createTab(isPrivate = false, groupId = groupId)
    }

    fun renameGroup(groupId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val group = _groups.value.firstOrNull { it.id == groupId } ?: return
        val updated = group.copy(title = trimmed)
        _groups.update { groups -> groups.map { if (it.id == groupId) updated else it } }
        viewModelScope.launch {
            tabRepository.updateGroup(groupId, updated.title, updated.color.toInt())
        }
    }

    fun recolorGroup(groupId: String, color: Long) {
        val group = _groups.value.firstOrNull { it.id == groupId } ?: return
        val updated = group.copy(color = color)
        _groups.update { groups -> groups.map { if (it.id == groupId) updated else it } }
        viewModelScope.launch {
            tabRepository.updateGroup(groupId, updated.title, color.toInt())
        }
    }

    fun deleteGroup(groupId: String) {
        _groups.update { groups -> groups.filterNot { it.id == groupId } }
        _tabs.update { tabs -> tabs.map { if (it.groupId == groupId) it.copy(groupId = null) else it } }
        viewModelScope.launch { tabRepository.deleteGroup(groupId) }
    }

    fun removeFromGroup(tabId: String) {
        _tabs.update { tabs -> tabs.map { if (it.id == tabId) it.copy(groupId = null) else it } }
        viewModelScope.launch { tabRepository.assignToGroup(tabId, null) }
    }

    private fun persistGroup(group: TabGroup, tabIds: Set<String>) {
        viewModelScope.launch {
            tabRepository.saveGroup(
                TabGroupEntity(id = group.id, title = group.title, color = group.color.toInt()),
            )
            tabIds.forEach { tabRepository.assignToGroup(it, group.id) }
        }
    }

    /**
     * Grid drag-and-drop: the dragged tab takes the target tab's slot and the rest shift
     * around it, matching Chrome. Order is persisted as sortOrder.
     */
    fun moveTab(tabId: String, targetId: String) {
        if (tabId == targetId) return
        val current = _tabs.value
        val from = current.indexOfFirst { it.id == tabId }
        val to = current.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0) return
        if (current[from].isPrivate != current[to].isPrivate) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        _tabs.value = reordered
        viewModelScope.launch { tabRepository.reorderTabs(reordered.filterNot { it.isPrivate }.map { it.id }) }
    }

    /**
     * Drag-and-drop grouping: the dragged tab joins the target tab's group, or a new
     * group is created holding both when the target is not grouped yet.
     */
    fun groupWith(tabId: String, targetId: String) {
        if (tabId == targetId) return
        val tabs = _tabs.value
        val dragged = tabs.firstOrNull { it.id == tabId } ?: return
        val target = tabs.firstOrNull { it.id == targetId } ?: return
        if (dragged.isPrivate != target.isPrivate) return

        val existing = target.groupId?.let { id -> _groups.value.firstOrNull { it.id == id } }
        if (existing != null) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(groupId = existing.id) else it }
            }
            viewModelScope.launch { tabRepository.assignToGroup(tabId, existing.id) }
            return
        }

        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            title = localizedString(R.string.tab_group_default_name, ++groupCounter),
            color = GROUP_COLORS[groupCounter % GROUP_COLORS.size],
        )
        _groups.update { it + group }
        val ids = setOf(tabId, targetId)
        _tabs.update { list ->
            list.map { if (it.id in ids) it.copy(groupId = group.id) else it }
        }
        persistGroup(group, ids)
    }

    fun tabsInGroup(groupId: String?): List<BrowserTab> =
        groupId?.let { id -> _tabs.value.filter { it.groupId == id } }.orEmpty()

    fun groupById(groupId: String): TabGroup? =
        _groups.value.firstOrNull { it.id == groupId }

    fun groupOf(tab: BrowserTab?): TabGroup? =
        tab?.groupId?.let { id -> _groups.value.firstOrNull { it.id == id } }

    fun captureThumbnails() {
        activeTab.value?.let(::captureThumbnail)
    }

    private fun captureThumbnail(tab: BrowserTab) {
        tab.session.capturePixels { bitmap ->
            viewModelScope.launch {
                val scaled = withContext(Dispatchers.Default) { downscaleThumbnail(bitmap) }
                _tabs.update { tabs ->
                    tabs.map { if (it.id == tab.id) it.copy(thumbnail = scaled) else it }
                }
            }
        }
    }

    private fun downscaleThumbnail(bitmap: Bitmap): Bitmap {
        val maxWidth = 360
        if (bitmap.width <= maxWidth || bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val targetHeight =
            ((bitmap.height.toLong() * maxWidth) / bitmap.width).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(maxWidth, targetHeight)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun installAddon(uri: Uri) = addons.installFromUri(uri)

    fun installAddonFromUrl(url: String) = addons.installFromUrl(url)

    val pendingAddonInstall: StateFlow<PendingAddonInstall?> = addons.pendingInstall

    fun respondToAddonInstall(allow: Boolean) = addons.respondToPendingInstall(allow)

    fun setAddonEnabled(id: String, enabled: Boolean) = addons.setEnabled(id, enabled)

    fun uninstallAddon(id: String) = addons.uninstall(id)

    fun openAddonPopup(id: String) = addons.clickAction(id, activeTab.value?.isPrivate == true)

    fun closeAddonPopup() = addons.closePopup()

    fun installUserscriptFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching { userscripts.sourceFromUri(uri) }
                .onSuccess { previewUserscript(it, uri.toString()) }
                .onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_cannot_read_script)) }
        }
    }

    fun installUserscript(url: String) {
        viewModelScope.launch {
            runCatching { userscripts.sourceFromUrl(url) }
                .onSuccess { previewUserscript(it, url) }
                .onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_userscript_download_failed)) }
        }
    }

    fun installUserscriptSource(source: String) {
        previewUserscript(source, null)
    }

    private fun previewUserscript(source: String, sourceUrl: String?) {
        runCatching { UserScriptParser.parse(source) }
            .onSuccess { parsed ->
                _pendingUserScript.value = PendingUserScript(parsed, source, sourceUrl)
            }
            .onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_invalid_userscript)) }
    }

    fun confirmUserscriptInstall() {
        val pending = _pendingUserScript.value ?: return
        _pendingUserScript.value = null
        viewModelScope.launch {
            runCatching { userscripts.install(pending.source, pending.sourceUrl) }
                .onSuccess { _messages.tryEmit(localizedString(R.string.msg_installed_item, it.name)) }
                .onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_userscript_install_failed)) }
        }
    }

    fun dismissUserscriptPreview() {
        _pendingUserScript.value = null
    }

    fun runUserscriptCommand(command: UserScriptMenuCommand) {
        if (command.tabId >= 0) userscripts.runMenuCommand(command.tabId, command.id)
    }

    fun setUserscriptEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { userscripts.setEnabled(id, enabled) }
    }

    fun saveUserscriptSource(id: String, source: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { userscripts.saveSource(id, source) }
                .onSuccess {
                    _messages.tryEmit(localizedString(R.string.msg_saved_item, it.name))
                    onSaved()
                }
                .onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_save_failed)) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun deleteUserscript(id: String) {
        viewModelScope.launch { userscripts.delete(id) }
    }

    fun checkUserscriptUpdates(id: String) {
        viewModelScope.launch {
            val result = userscripts.checkForUpdates(id)
            val text = when (result) {
                is UpdateCheck.NoUpdateUrl -> localizedString(R.string.msg_no_update_url)
                is UpdateCheck.UpToDate -> localizedString(R.string.msg_up_to_date)
                is UpdateCheck.Available -> {
                    runCatching { userscripts.update(id) }
                        .fold(
                            onSuccess = { updated -> localizedString(R.string.msg_updated_item, updated.name, updated.version) },
                            onFailure = { error -> error.message ?: localizedString(R.string.msg_update_failed) },
                        )
                }
                is UpdateCheck.Failed -> result.message
            }
            _messages.tryEmit(text)
        }
    }

    fun publishMirror() {
        viewModelScope.launch {
            _mirrorUrl.value = cast.publishMirror()
        }
    }

    private fun watchContextMenu() {
        viewModelScope.launch {
            launch {
                activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    if (tab == null) {
                        _contextMenu.value = null
                    } else {
                        tab.session.contextMenu.collect { menu ->
                            if (menu != null && isContextMenuSuppressed()) {
                                tab.session.dismissContextMenu()
                                return@collect
                            }
                            _contextMenu.value = menu
                        }
                    }
                }
            }
            launch {
                activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    if (tab == null) {
                        _selection.value = null
                    } else {
                        tab.session.selection.collect { _selection.value = it }
                    }
                }
            }
            launch {
                activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    if (tab == null) {
                        _fullscreen.value = false
                    } else {
                        tab.session.fullscreen.collect { _fullscreen.value = it }
                    }
                }
            }
            bridge.events.collect { event ->
                if (event.extensionId == SystemExtensionIds.AUTOFILL) {
                    if (event.type == "autofill/fields") offerAutofill(event)
                    return@collect
                }
                if (event.extensionId == SystemExtensionIds.GESTURES &&
                    event.type == "amo/install"
                ) {
                    installAmoAddonFromUrl(event.payload.optString("url"))
                    return@collect
                }
                if (event.extensionId == SystemExtensionIds.GESTURES &&
                    event.type == "gesture/pull"
                ) {
                    val progress = event.payload.optDouble("progress", 0.0)
                        .toFloat().coerceIn(0f, 1f)
                    val triggered = event.payload.optBoolean("triggered")
                    pullHideJob?.cancel()
                    _pullRefresh.value = if (progress <= 0f && !triggered) {
                        null
                    } else {
                        PullRefreshState(progress, triggered)
                    }
                    // Safety net: if the page drops the terminal message (navigation, crash,
                    // cancelled touch), never leave the indicator stuck on screen.
                    if (_pullRefresh.value != null) {
                        pullHideJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(if (triggered) 700L else 1_200L)
                            if (_pullRefresh.value != null) _pullRefresh.value = null
                        }
                    }
                    return@collect
                }
                if (event.type != CONTEXT_MENU_REQUEST) return@collect
                if (isContextMenuSuppressed()) return@collect
                val link = event.payload.optString("link").takeIf { it.isNotBlank() }
                val image = event.payload.optString("image").takeIf { it.isNotBlank() }
                if (link == null && image == null) return@collect
                _contextMenu.value = ContextMenu(
                    linkUrl = link,
                    imageUrl = image,
                    text = event.payload.optString("text").takeIf { it.isNotBlank() },
                )
            }
        }
    }

    /** Clears the published URL when the capture service ends on its own (Stop action, revoke). */
    /** Mirrors the active tab's Gecko dialogs and permission requests into Compose state. */
    private fun watchSessionDialogs() {
        viewModelScope.launch {
            launch {
                activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    if (tab == null) {
                        promptSession = null
                        _prompt.value = null
                    } else {
                        tab.session.prompt.collect { value ->
                            promptSession = if (value != null) tab.session else null
                            _prompt.value = value
                        }
                    }
                }
            }
            launch {
                activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    if (tab == null) {
                        permissionSession = null
                        _permission.value = null
                    } else {
                        tab.session.permission.collect { value ->
                            permissionSession = if (value != null) tab.session else null
                            _permission.value = value
                        }
                    }
                }
            }
            activeTab.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                if (tab == null) {
                    androidPermissionSession = null
                    _androidPermissions.value = null
                } else {
                    tab.session.androidPermissions.collect { value ->
                        androidPermissionSession = if (value != null) tab.session else null
                        _androidPermissions.value = value
                    }
                }
            }
        }
    }

    private fun watchMirror() {
        viewModelScope.launch {
            var started = false
            com.chrogecko.browser.feature.cast.MirrorFrames.running.collect { running ->
                if (running) {
                    started = true
                } else if (started) {
                    started = false
                    _mirrorUrl.value = null
                    cast.stopMirror()
                }
            }
        }
    }

    fun fillLogin(entry: SavedLogin) = autofill.fill(entry)

    fun saveLogin() = autofill.save()

    fun dismissLoginSuggestion() = autofill.dismissSuggestion()

    fun dismissSaveLogin() = autofill.dismissSave()

    fun deleteLogin(id: Long) {
        viewModelScope.launch { loginRepository.delete(id) }
    }

    fun deleteLoginsForHost(host: String) {
        viewModelScope.launch { loginRepository.deleteHost(host) }
    }

    /** `sys_autofill` reported the tokens of the focused form; offer what fits the kind. */
    private fun offerAutofill(event: NativeEvent) {
        val kind = event.payload.optString("kind", "address")
        val tabId = event.payload.optLong("tabId", -1L)
        if (tabId < 0) return
        autofillTabId = tabId
        _autofillKind.value = if (kind == "card") "card" else "address"
        viewModelScope.launch {
            _autofillOptions.value = if (_autofillKind.value == "card") {
                autofillRepository.cards.first().map {
                    AutofillOption(it.id, "card", "${it.label} •••• ${it.last4}")
                }
            } else {
                autofillRepository.addresses.first().map {
                    AutofillOption(it.id, "address", "${it.label} — ${it.street}")
                }
            }
        }
    }

    fun fillAutofill(option: AutofillOption) {
        val tabId = autofillTabId
        _autofillOptions.value = emptyList()
        if (tabId < 0) return
        viewModelScope.launch {
            val values = if (option.kind == "card") {
                autofillRepository.cardValues(option.id)
            } else {
                autofillRepository.addressValues(option.id)
            }
            if (values.isEmpty()) return@launch
            bridge.post(
                SystemExtensionIds.AUTOFILL,
                JSONObject()
                    .put("type", "autofill/fill")
                    .put("tabId", tabId)
                    .put("values", JSONObject(values as Map<*, *>)),
            )
        }
    }

    fun dismissAutofill() {
        _autofillOptions.value = emptyList()
    }

    fun saveAddress(entity: AddressEntity) {
        viewModelScope.launch { autofillRepository.saveAddress(entity) }
    }

    fun deleteAddress(id: Long) {
        viewModelScope.launch { autofillRepository.deleteAddress(id) }
    }

    fun saveCard(label: String, nameOnCard: String, number: String, expiry: String) {
        viewModelScope.launch { autofillRepository.saveCard(label, nameOnCard, number, expiry) }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { autofillRepository.deleteCard(id) }
    }

    fun stopMirror() {
        _mirrorUrl.value = null
        cast.stopMirror()
    }

    fun castMedia(media: CastMedia) {
        viewModelScope.launch {
            cast.cast(media).onFailure { _messages.tryEmit(it.message ?: localizedString(R.string.msg_cast_failed)) }
        }
    }

    fun castMediaForActiveTab(): List<CastMedia> {
        val tab = activeTab.value ?: return emptyList()
        val url = tab.state.url
        return allCastMedia.value.values.flatten().filter { it.pageUrl == url }
    }

    fun pauseCast() = cast.pause()

    fun resumeCast() = cast.play()

    fun stopCast() = cast.stopPlayback()

    fun disconnectCast() = cast.disconnect()

    fun seekCast(positionMs: Long) = cast.seek(positionMs)

    private fun observeTab(tab: BrowserTab) {
        tab.session.externalResponseHandler = ::handleExternalResponse
        // window.open / target="_blank" becomes a new tab, using an unopened child session.
        tab.session.popupHandler = { url -> openUrlInNewTab(url) }
        tab.session.newSessionHandler = { uri ->
            createChildSession(tab.isPrivate, tab.groupId, uri, parentTabId = tab.id)
        }
        tab.session.closeRequestHandler = { closeTab(tab.id, recordUndo = false) }
        tab.session.printHandler = { file -> _printFile.value = file }
        // Applied on each load request so the rules affect the document that is loading.
        tab.session.siteRuleApplier = { host ->
            val rule = siteRuleCache[host] ?: siteRuleCache[host.removePrefix("www.")]
            tab.session.applySiteRules(
                blockJavascript = rule?.blockJavascript == true,
                desktopSite = rule?.desktopSite == true,
            )
            if (tab.id == activeTab.value?.id) _desktopMode.value = tab.session.desktopMode
        }
        tab.session.navigationInterceptor = { url ->
            when {
                ExternalIntentHandler.isIntentUri(url) ->
                    ExternalIntentHandler.launchIntentUri(runtime.appContext, url) { fallback ->
                        tab.session.load(fallback)
                    }
                ExternalIntentHandler.isExternalAppScheme(url) ->
                    ExternalIntentHandler.launchExternalUri(runtime.appContext, url)
                responseIsUserscript(url) -> {
                    installUserscript(url)
                    true
                }
                else -> false
            }
        }
        observers[tab.id] = viewModelScope.launch {
            launch {
                // A dialog raised in a background tab (e.g. window.open) must still be answered.
                // The active-tab collectors in watchSessionDialogs own the normal case; this only
                // fills the gap while no dialog is published globally.
                tab.session.prompt.collect { value ->
                    if (tab.id == activeTab.value?.id) return@collect
                    if (value != null && _prompt.value == null) {
                        promptSession = tab.session
                        _prompt.value = value
                    } else if (value == null && promptSession === tab.session) {
                        promptSession = null
                        _prompt.value = null
                    }
                }
            }
            launch {
                tab.session.permission.collect { value ->
                    if (tab.id == activeTab.value?.id) return@collect
                    if (value != null && _permission.value == null) {
                        permissionSession = tab.session
                        _permission.value = value
                    } else if (value == null && permissionSession === tab.session) {
                        permissionSession = null
                        _permission.value = null
                    }
                }
            }
            launch {
                // Persist session history so back/forward and scroll survive a restart.
                tab.session.historyStateFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .debounce(500)
                    .collect { saved ->
                        if (!tab.isPrivate && !restoring) {
                            tabRepository.updateSessionState(tab.id, saved)
                        }
                    }
            }
            var lastPersistedUrl: String? = null
            var lastPersistedTitle = ""
            var wasLoading = false
            tab.session.state.collect { state ->
                // Re-send the dark-mode policy on every navigation: if a content script started
                // before the extension had the current config, this broadcast fixes it.
                if (state.isLoading && !wasLoading) darkMode.refresh()
                wasLoading = state.isLoading
                _tabs.update { tabs ->
                    tabs.map { if (it.id == tab.id) it.copy(state = state) else it }
                }
                val persistUrl = state.url.takeIf { it != "about:blank" }
                if (!tab.isPrivate && !restoring &&
                    (persistUrl != lastPersistedUrl || state.title != lastPersistedTitle)
                ) {
                    lastPersistedUrl = persistUrl
                    lastPersistedTitle = state.title
                    tabRepository.updateContent(tab.id, persistUrl, state.title)
                }
                // GeckoView exposes no favicon API, so icons are resolved per host in Kotlin.
                if (!tab.isPrivate && !state.isLoading && state.progress >= 100 &&
                    state.url.startsWith("http") &&
                    iconQueriedUrls[tab.id] != faviconHost(state.url)
                ) {
                    iconQueriedUrls[tab.id] = faviconHost(state.url).orEmpty()
                    faviconRepository.ensure(state.url)
                }
            }
        }
    }

    private fun responseIsUserscript(url: String): Boolean =
        url.substringBefore('#').substringBefore('?').endsWith(".user.js", ignoreCase = true)

    private fun handleExternalResponse(response: WebResponse): Boolean {
        val path = response.uri.substringBefore('#').substringBefore('?')
        when {
            // `.xpi` is downloaded like any other file: a page must never be able to install an
            // extension by linking to it. Installing stays an explicit in-app action.
            path.endsWith(".user.js", ignoreCase = true) -> {
                runCatching { response.body?.close() }
                installUserscript(response.uri)
            }
            else -> downloadManager.enqueue(response)
        }
        return true
    }

    fun deleteDownload(id: Long) = downloadManager.delete(id)

    fun cancelDownload(id: Long) = downloadManager.cancel(id)

    fun clearDownloads() = downloadManager.clear()

    private fun onActiveTabChanged(tab: BrowserTab?) {
        if (tab == null) return
        if (!_omniboxFocused.value) _omniboxText.value = displayUrl(tab.state.url)
        addons.setActiveSession(tab.session.geckoSession)
        _desktopMode.value = tab.session.desktopMode
        val state = tab.state
        if (!tab.isPrivate && !state.isLoading && state.progress >= 100 &&
            state.url != lastRecordedUrl && state.url != "about:blank"
        ) {
            lastRecordedUrl = state.url
            viewModelScope.launch { history.record(state.url, state.title) }
        }
    }

    override fun onCleared() {
        observers.values.forEach(Job::cancel)
        engine.closeAll()
    }

    companion object {
        private const val CONTEXT_MENU_REQUEST = "contextmenu/request"

        private val GROUP_COLORS = listOf(0xFF1A73E8, 0xFF188038, 0xFFD93025, 0xFF9334E6)
    }
}
