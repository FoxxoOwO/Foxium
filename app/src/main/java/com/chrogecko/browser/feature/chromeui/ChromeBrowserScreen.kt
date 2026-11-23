package com.chrogecko.browser.feature.chromeui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.speech.RecognizerIntent
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrogecko.browser.MainActivity
import com.chrogecko.browser.R
import com.chrogecko.browser.core.engine.GeckoEngineView
import com.chrogecko.browser.core.engine.RawGeckoView
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.core.ui.Haptics
import com.chrogecko.browser.feature.cast.MirrorCaptureService
import com.chrogecko.browser.feature.favicons.faviconHost
import com.chrogecko.browser.feature.updates.AvailableUpdate
import com.chrogecko.browser.feature.updates.UpdateState
import com.chrogecko.browser.ui.theme.ChroGeckoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Height reserved for the toolbar; matches the pill (44dp) plus its vertical padding. */
internal val TOOLBAR_HEIGHT = 60.dp

enum class ChromeSheet {
    None,
    Menu,
    Addons,
    Userscripts,
    Bookmarks,
    History,
    Recent,
    Downloads,
    Cast,
    PageInfo,
}

@Composable
fun ChromeBrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    externalUrl: String? = null,
    onExternalUrlConsumed: () -> Unit = {},
) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    val isPrivate = activeTab?.isPrivate == true && !settingsOpen
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val baseConfig = LocalConfiguration.current
    val baseContext = LocalContext.current
    val localizedConfig = remember(baseConfig, appLanguage) {
        AppLocaleManager.localizedConfiguration(baseConfig, appLanguage)
    }
    val localizedResources = remember(baseContext, localizedConfig) {
        baseContext.createConfigurationContext(localizedConfig).resources
    }
    LaunchedEffect(Unit) { viewModel.syncSystemLanguage() }
    // Only configuration and resources are overridden; LocalContext must stay the real Activity.
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalResources provides localizedResources,
    ) {
        ChroGeckoTheme(
            darkTheme = isSystemInDarkTheme() || isPrivate,
            isPrivate = isPrivate,
        ) {
            ChromeBrowserContent(
                viewModel = viewModel,
                externalUrl = externalUrl,
                onExternalUrlConsumed = onExternalUrlConsumed,
                settingsOpen = settingsOpen,
                onOpenSettings = { settingsOpen = true },
                onCloseSettings = { settingsOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChromeBrowserContent(
    viewModel: BrowserViewModel,
    externalUrl: String?,
    onExternalUrlConsumed: () -> Unit,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val omnibox by viewModel.omniboxText.collectAsStateWithLifecycle()
    val focused by viewModel.omniboxFocused.collectAsStateWithLifecycle()
    val grid by viewModel.gridOpen.collectAsStateWithLifecycle()
    val find by viewModel.findState.collectAsStateWithLifecycle()
    val fullscreen by viewModel.fullscreen.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val siteDark by viewModel.siteDarkMode.collectAsStateWithLifecycle()
    val desktop by viewModel.desktopMode.collectAsStateWithLifecycle()
    val readerStates by viewModel.readerStates.collectAsStateWithLifecycle()
    val readerState = activeTab?.state?.url?.let { readerStates[it] }
    val popup by viewModel.addonPopup.collectAsStateWithLifecycle()
    val toolbarBottom by viewModel.toolbarBottom.collectAsStateWithLifecycle()
    val toolbarAutoHide by viewModel.toolbarAutoHide.collectAsStateWithLifecycle()
    val toolbarShortcut by viewModel.toolbarShortcut.collectAsStateWithLifecycle()
    val toolbarHidden by viewModel.toolbarHidden.collectAsStateWithLifecycle()
    val omniboxSuggestions by viewModel.omniboxSuggestions.collectAsStateWithLifecycle()
    val pendingUserscript by viewModel.pendingUserScript.collectAsStateWithLifecycle()
    val pendingAddonInstall by viewModel.pendingAddonInstall.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val favicons by viewModel.favicons.collectAsStateWithLifecycle()
    val castPlayback by viewModel.castPlayback.collectAsStateWithLifecycle()
    val mirrorUrl by viewModel.mirrorUrl.collectAsStateWithLifecycle()
    val contextMenu by viewModel.contextMenu.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val enginePrompt by viewModel.prompt.collectAsStateWithLifecycle()
    val sitePermission by viewModel.permission.collectAsStateWithLifecycle()
    val androidPermissions by viewModel.androidPermissions.collectAsStateWithLifecycle()
    val loginSuggestion by viewModel.loginSuggestion.collectAsStateWithLifecycle()
    val autofillOptions by viewModel.autofillOptions.collectAsStateWithLifecycle()
    val printFile by viewModel.printFile.collectAsStateWithLifecycle()
    val saveLoginRequest by viewModel.saveLoginRequest.collectAsStateWithLifecycle()
    // Bumped by the new tab page so tapping its search box focuses the real omnibox.
    var omniboxFocusRequest by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val androidAutofillEnabled by viewModel.androidAutofillEnabled.collectAsStateWithLifecycle()
    // An intent handed over by another app opens in its own tab, as Chrome does.
    LaunchedEffect(externalUrl) {
        val url = externalUrl
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            viewModel.openExternalUrl(url)
        }
        if (url != null) onExternalUrlConsumed()
    }
    val snackbar = remember { SnackbarHostState() }
    val undoMessage by viewModel.undoMessage.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf(ChromeSheet.None) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var castExpanded by remember { mutableStateOf(false) }
    var editingGroupId by remember { mutableStateOf<String?>(null) }

    val addonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::installAddon) }
    val userscriptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::installUserscriptFromUri) }
    val bookmarkImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBookmarksFromUri) }
    val bookmarkExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri -> uri?.let(viewModel::exportBookmarksToUri) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let {
                viewModel.onOmniboxChange(it)
                viewModel.submit()
            }
    }
    val startVoice: () -> Unit = {
        runCatching {
            voiceLauncher.launch(
                android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, resources.getString(R.string.omnibox_placeholder))
                },
            )
        }
        Unit
    }
    val startSelectedShare: (String) -> Unit = { text ->
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, resources.getString(R.string.share_text)),
        )
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> viewModel.answerAndroidPermissions(granted.values.all { it }) }

    LaunchedEffect(androidPermissions) {
        val ask = androidPermissions ?: return@LaunchedEffect
        runtimePermissionLauncher.launch(ask.permissions.toTypedArray())
    }

    val mirrorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        android.util.Log.i("ChroGeckoMirror", "consent result=${result.resultCode}")
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            MirrorCaptureService.start(context, result.resultCode, data)
            viewModel.publishMirror()
        }
    }
    val startMirror: () -> Unit = {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        runCatching { mirrorLauncher.launch(manager.createScreenCaptureIntent()) }
            .onFailure { android.util.Log.w("ChroGeckoMirror", "no capture intent: ${it.message}") }
        Unit
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(printFile) {
        val pdf = printFile ?: return@LaunchedEffect
        printPdf(context, activeTab?.state?.title.orEmpty(), pdf)
        viewModel.consumePrint()
    }

    LaunchedEffect(Unit) {
        launch { viewModel.messages.collect { snackbar.showSnackbar(it) } }
        // Asked when a download actually starts, not on the first screen: the dialog would cover
        // the new tab page and steal focus from whatever the user opened first.
        launch {
            viewModel.notificationPermissionRequests.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    // Chrome offers "Undo" right after a tab disappears.
    LaunchedEffect(undoMessage) {
        val message = undoMessage ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = message,
            actionLabel = resources.getString(R.string.undo),
            // Long, not Short: the action has to stay tappable while the user reacts.
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoClose() else viewModel.dismissUndo()
    }

    val activity = LocalActivity.current
    // A single handler with an explicit priority keeps the back gesture deterministic. Multiple
    // overlapping BackHandlers could let the page-history branch run while an overlay was opening
    // (typically the page-info icon), which looked like "back opened the info sheet".
    PredictiveBackHandler(enabled = true) { progress ->
        viewModel.setBackGestureActive(true)
        try {
            progress.collect { }
            when {
                viewModel.contextMenu.value != null -> viewModel.dismissContextMenu()
                sheet != ChromeSheet.None -> sheet = ChromeSheet.None
                find.visible -> viewModel.closeFind()
                grid -> viewModel.setGridOpen(false)
                fullscreen -> viewModel.exitFullscreen()
                activeTab?.state?.canGoBack == true -> viewModel.goBack()
                else -> activity?.finish()
            }
        } catch (_: CancellationException) {
            // Back gesture cancelled; stay on the current page.
        } finally {
            viewModel.setBackGestureActive(false)
        }
    }

    LaunchedEffect(fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val tabCount = remember(tabs, activeTab?.isPrivate) {
        val isPrivate = activeTab?.isPrivate ?: false
        tabs.count { it.isPrivate == isPrivate }
    }
    val hasExtensionBadge by remember(viewModel) {
        viewModel.installedAddons
            .map { addons -> addons.any { !it.builtIn && !it.badgeText.isNullOrBlank() } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val startPageShare: () -> Unit = {
        val url = activeTab?.state?.url
        if (!url.isNullOrBlank() && url != "about:blank") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(intent, resources.getString(R.string.share_page)))
        }
    }

    val currentUrl = activeTab?.state?.url.orEmpty()
    val isNewTab = currentUrl.isBlank() || currentUrl == "about:blank"
    val isPrivateTab = activeTab?.isPrivate == true
    val hideToolbar = toolbarAutoHide && toolbarHidden && !fullscreen && !focused && !isNewTab && !grid
    val focusManager = LocalFocusManager.current
    val currentFocused by rememberUpdatedState(focused)
    var toolbarFocusGuard by remember { mutableStateOf(false) }
    var focusGuardToken by remember { mutableIntStateOf(0) }
    val insetsView = LocalView.current
    var imeVisible by remember { mutableStateOf(false) }
    // Observe layout passes instead of polling root insets on a timer, keeping Compose's own
    // insets dispatch untouched.
    DisposableEffect(insetsView) {
        val readIme = {
            ViewCompat.getRootWindowInsets(insetsView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        imeVisible = readIme()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val visible = readIme()
            if (imeVisible != visible) {
                if (imeVisible && !visible && !currentFocused) {
                    // Keyboard just closed after typing into a page; guard frame 0 so the
                    // re-appearing toolbar cannot steal focus during Android's focus search.
                    toolbarFocusGuard = true
                    focusGuardToken++
                    focusManager.clearFocus()
                }
                imeVisible = visible
            }
        }
        insetsView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            if (insetsView.viewTreeObserver.isAlive) {
                insetsView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }
    val topToolbarVisible = !toolbarBottom && !fullscreen && !hideToolbar
    // Hide only while typing into the page. When the omnibox itself has focus, it lives inside
    // this toolbar - hiding it would make the field vanish under the user's fingers.
    val bottomToolbarVisible = toolbarBottom && !fullscreen && !hideToolbar &&
        (!imeVisible || focused)
    val toolbarVisible = topToolbarVisible || bottomToolbarVisible
    var wasToolbarVisible by remember { mutableStateOf(toolbarVisible) }
    LaunchedEffect(toolbarVisible) {
        if (!wasToolbarVisible && toolbarVisible) {
            toolbarFocusGuard = true
            focusGuardToken++
        }
        wasToolbarVisible = toolbarVisible
    }
    LaunchedEffect(focusGuardToken) {
        if (focusGuardToken > 0) {
            try {
                toolbarFocusGuard = true
                focusManager.clearFocus()
                kotlinx.coroutines.delay(450)
            } finally {
                toolbarFocusGuard = false
            }
        }
    }
    val amoAddonSlug by viewModel.amoAddonSlug.collectAsStateWithLifecycle()

    val scrollSession = activeTab?.session
    LaunchedEffect(scrollSession?.id, toolbarAutoHide) {
        if (!toolbarAutoHide) return@LaunchedEffect
        scrollSession?.scrollY?.collect { y -> viewModel.onPageScroll(y) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val toolbar: @Composable () -> Unit = {
            ChromeToolbar(
                omnibox = omnibox,
                focused = focused,
                isLoading = activeTab?.state?.isLoading == true,
                isSecure = activeTab?.state?.isSecure == true,
                isPrivate = isPrivateTab,
                isNewTab = isNewTab,
                toolbarBottom = toolbarBottom,
                hasExtensionBadge = hasExtensionBadge,
                tabCount = tabCount,
                onOmniboxChange = viewModel::onOmniboxChange,
                onFocusChange = viewModel::onOmniboxFocusChanged,
                onSubmit = viewModel::submit,
                onStop = viewModel::stop,
                onHome = viewModel::goHome,
                onPageInfo = { sheet = ChromeSheet.PageInfo },
                onShare = startPageShare,
                onOpenTabs = { viewModel.setGridOpen(true) },
                onMenu = { sheet = ChromeSheet.Menu },
                onSwipeTab = viewModel::switchAdjacentTab,
                onPullDown = { viewModel.setGridOpen(true) },
                onVoice = startVoice,
                amoAddonSlug = amoAddonSlug,
                onInstallAmoAddon = viewModel::installAmoAddon,
                focusGuard = toolbarFocusGuard,
                toolbarShortcut = toolbarShortcut,
                suggestions = omniboxSuggestions,
                onNewTab = { viewModel.createTab(false) },
                onBookmarks = { sheet = ChromeSheet.Bookmarks },
                onHistory = { sheet = ChromeSheet.History },
                onCloseTab = { activeTab?.id?.let(viewModel::closeTab) },
                onSuggestionPick = { suggestion ->
                    viewModel.clearSuggestions()
                    viewModel.navigate(suggestion.url)
                },
                focusRequest = omniboxFocusRequest,
                onFocusRequestConsumed = { omniboxFocusRequest = 0 },
            )
        }
        // The page receives its padding in one step; only the toolbar itself is animated with a
        // translation, so GeckoView is not re-laid out on every animation frame.
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(
                    top = if (topToolbarVisible) TOOLBAR_HEIGHT else 0.dp,
                    bottom = if (bottomToolbarVisible) TOOLBAR_HEIGHT else 0.dp,
                ),
        ) {
            val progress = activeTab?.state?.progress ?: 0
            val isPageLoading = !fullscreen && activeTab?.state?.isLoading == true
            val animatedProgress by animateFloatAsState(
                targetValue = (progress / 100f).coerceIn(0.05f, 1f),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "pageProgress",
            )
            AnimatedVisibility(
                visible = isPageLoading,
                enter = expandVertically(MaterialTheme.motionScheme.fastEffectsSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastEffectsSpec()) + fadeOut(),
            ) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    wavelength = 24.dp,
                )
            }
            AnimatedVisibility(
                visible = find.visible && !fullscreen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                FindBar(
                    state = find,
                    onPrevious = viewModel::findPrevious,
                    onNext = viewModel::findNext,
                    onQueryChange = viewModel::updateFindQuery,
                    onClose = viewModel::closeFind,
                )
            }
            val loginPrompt = loginSuggestion
            AnimatedVisibility(
                visible = loginPrompt != null && !fullscreen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                if (loginPrompt != null) {
                    LoginSuggestionBar(
                        suggestion = loginPrompt,
                        onFill = viewModel::fillLogin,
                        onDismiss = viewModel::dismissLoginSuggestion,
                    )
                }
            }
            AnimatedVisibility(
                visible = autofillOptions.isNotEmpty() && !fullscreen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                AutofillBar(
                    options = autofillOptions,
                    onPick = viewModel::fillAutofill,
                    onDismiss = viewModel::dismissAutofill,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val pullRefresh by viewModel.pullRefresh.collectAsStateWithLifecycle()
                val pullView = LocalView.current
                val tab = activeTab
                if (tab == null) {
                    Text(stringResource(R.string.no_tab), modifier = Modifier.align(Alignment.Center))
                } else {
                    GeckoEngineView(tab.session, Modifier.fillMaxSize(), androidAutofillEnabled = androidAutofillEnabled)
                    // Gecko paints about:blank white; the Compose page covers it.
                    if (tab.state.url.isBlank() || tab.state.url == "about:blank") {
                        NewTabPageHost(
                            viewModel = viewModel,
                            icons = favicons,
                            isPrivate = tab.isPrivate,
                            onSearch = { omniboxFocusRequest++ },
                            onVoice = startVoice,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Drawn last so the engine surface does not cover it.
                PullRefreshIndicator(
                    state = pullRefresh,
                    view = pullView,
                    enabled = !fullscreen && !grid,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                )
                val selected = selection
                NativeSelectionMenu(
                    selection = if (fullscreen || grid) null else selected,
                    onCopy = viewModel::copySelection,
                    onShare = { selected?.let { startSelectedShare(it.text) } },
                    onSelectAll = viewModel::selectAllSelection,
                    onDismiss = viewModel::clearSelection,
                )
            }
            AnimatedVisibility(
                visible = castPlayback.connected && !fullscreen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                CastMiniController(
                    state = castPlayback,
                    onExpand = { castExpanded = true },
                    onPlayPause = {
                        if (castPlayback.playing) viewModel.pauseCast() else viewModel.resumeCast()
                    },
                    onStop = viewModel::stopCast,
                )
            }
            val activeGroup = remember(groups, activeTab?.groupId) {
                activeTab?.groupId?.let { id -> groups.firstOrNull { it.id == id } }
            }
            var displayGroup by remember { mutableStateOf<TabGroup?>(null) }
            if (activeGroup != null) displayGroup = activeGroup
            val visibleGroup = displayGroup
            AnimatedVisibility(
                visible = activeGroup != null && !fullscreen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                if (visibleGroup != null) {
                    val groupTabs = remember(tabs, visibleGroup.id) {
                        tabs.filter { it.groupId == visibleGroup.id }
                    }
                    TabGroupStrip(
                        group = visibleGroup,
                        icons = favicons,
                        tabs = groupTabs,
                        activeTabId = activeTab?.id,
                        onSelect = viewModel::selectTab,
                        onAdd = viewModel::addTabToActiveGroup,
                        onRemove = viewModel::removeFromGroup,
                        onEdit = { editingGroupId = visibleGroup.id },
                    )
                }
            }
        }

        // Overlay toolbars: pure translation animations, no layout measurement per frame.
        AnimatedVisibility(
            visible = topToolbarVisible,
            enter = slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        ) {
            toolbar()
        }
        AnimatedVisibility(
            visible = bottomToolbarVisible,
            enter = slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            // Keep the overlay above the system navigation bar and above the IME when it opens;
            // otherwise a dark strip appears between the page and the toolbar/keyboard.
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            toolbar()
        }

        AnimatedVisibility(
            visible = grid,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleIn(initialScale = 0.94f, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleOut(targetScale = 0.94f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            ChromeTabGridSwitcher(
                tabs = tabs,
                groups = groups,
                icons = favicons,
                activeTabId = activeTab?.id,
                onSelect = viewModel::selectTab,
                onCloseTab = { id -> viewModel.closeTab(id, keepGridOpen = true) },
                onNewTab = { viewModel.createTab(it) },
                onCloseAllPrivate = viewModel::closeAllPrivateTabs,
                onMoveTab = viewModel::moveTab,
                onDuplicateTab = viewModel::duplicateTab,
                onCloseOtherTabs = viewModel::closeOtherTabs,
                onGroupWith = viewModel::groupWith,
                onGroupAll = viewModel::groupAllTabs,
                onDismiss = { viewModel.setGridOpen(false) },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 4 } +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            ChromeSettingsScreen(
                viewModel = viewModel,
                onClose = onCloseSettings,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            // Material 3's SnackbarHost does not dismiss on swipe by itself; wrap each snackbar
            // so "Tab closed / Undo" can be swiped away just like in Chrome.
            snackbar = { data ->
                val snackbarView = LocalView.current
                val dismissState = rememberSwipeToDismissBoxState()
                // Modern anchored-draggable pattern: react to the settled value instead of the
                // deprecated confirmValueChange callback.
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        Haptics.gestureEnd(snackbarView)
                        data.dismiss()
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundContent = {},
                    content = { Snackbar(data) },
                )
            },
        )
    }

    if (castExpanded && castPlayback.connected) {
        CastExpandedController(
            state = castPlayback,
            onDismiss = { castExpanded = false },
            onPlayPause = {
                if (castPlayback.playing) viewModel.pauseCast() else viewModel.resumeCast()
            },
            onSeek = viewModel::seekCast,
            onStop = {
                viewModel.stopCast()
                castExpanded = false
            },
            onDisconnect = {
                viewModel.disconnectCast()
                castExpanded = false
            },
        )
    }

    editingGroupId?.let { groupId ->
        val group = remember(groups, groupId) { groups.firstOrNull { it.id == groupId } }
        if (group == null) {
            LaunchedEffect(groupId) { editingGroupId = null }
        } else {
            GroupEditDialog(
                group = group,
                onRename = { viewModel.renameGroup(groupId, it) },
                onRecolor = { viewModel.recolorGroup(groupId, it) },
                onDelete = {
                    viewModel.deleteGroup(groupId)
                    editingGroupId = null
                },
                onDismiss = { editingGroupId = null },
            )
        }
    }

    val saveRequest = saveLoginRequest
    if (saveRequest != null) {
        SaveLoginDialog(
            request = saveRequest,
            onSave = viewModel::saveLogin,
            onDismiss = viewModel::dismissSaveLogin,
        )
    }
    EnginePromptHost(
        prompt = enginePrompt,
        onDismiss = viewModel::dismissPrompt,
        onConfirm = viewModel::answerConfirm,
        onText = viewModel::answerText,
        onChoice = viewModel::answerChoice,
        onAuth = viewModel::answerAuth,
        onFiles = viewModel::answerFiles,
    )

    SitePermissionHost(
        ask = sitePermission,
        onAllow = { viewModel.answerPermission(true) },
        onDeny = { viewModel.answerPermission(false) },
    )

    contextMenu?.let { menu ->
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissContextMenu,
            sheetState = menuSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ContextMenuSheet(
                menu = menu,
                onOpen = { viewModel.openContextUrl(it) },
                onOpenPrivate = { viewModel.openContextUrl(it, isPrivate = true) },
                onDownload = viewModel::downloadContextUrl,
                onCopy = { url ->
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("link", url),
                    )
                },
                onShare = { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(intent, resources.getString(R.string.share_link)),
                    )
                },
                onDismiss = viewModel::dismissContextMenu,
            )
        }
    }

    pendingUserscript?.let { pending ->
        UserscriptInstallDialog(
            pending = pending,
            onInstall = viewModel::confirmUserscriptInstall,
            onDismiss = viewModel::dismissUserscriptPreview,
        )
    }

    pendingAddonInstall?.let { pending ->
        AddonInstallDialog(
            pending = pending,
            onInstall = { viewModel.respondToAddonInstall(true) },
            onDismiss = { viewModel.respondToAddonInstall(false) },
        )
    }

    if (!settingsOpen) {
        when (val upd = updateState) {
            is UpdateState.Available -> if (!upd.bannerDismissed) {
                AppUpdateDialog(
                    update = upd.update,
                    downloading = null,
                    readyToInstall = null,
                    onDownload = { viewModel.downloadAvailableUpdate(upd.update) },
                    onInstall = {},
                    onDismiss = { viewModel.dismissUpdateBanner(upd.update.tagName) },
                )
            }
            is UpdateState.Downloading -> AppUpdateDialog(
                update = upd.update,
                downloading = upd,
                readyToInstall = null,
                onDownload = {},
                onInstall = {},
                onDismiss = {},
            )
            is UpdateState.ReadyToInstall -> AppUpdateDialog(
                update = upd.update,
                downloading = null,
                readyToInstall = upd,
                onDownload = {},
                onInstall = viewModel::installReadyUpdate,
                onDismiss = { viewModel.dismissUpdateBanner(upd.update.tagName) },
            )
            else -> Unit
        }
    }

    if (sheet != ChromeSheet.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = ChromeSheet.None },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            when (sheet) {
                ChromeSheet.Menu -> {
                    val installedAddons by viewModel.installedAddons.collectAsStateWithLifecycle()
                    val userAddons = remember(installedAddons) { installedAddons.filterNot { it.builtIn } }
                    val scriptCommands by viewModel.userscriptMenuCommands.collectAsStateWithLifecycle()
                    val language = remember(appLanguage) { AppLocaleManager.resolveLocale(appLanguage).language }
                    ChromeThreeDotMenu(
                        activeTab = activeTab,
                        bookmarked = bookmarked,
                        siteDarkMode = siteDark,
                        desktopMode = desktop,
                        addonCount = userAddons.size,
                        canTranslate = activeTab?.state?.url?.let {
                            translateUrl(it, language)
                        } != null,
                        readerAvailable = readerState?.available == true,
                        readerActive = readerState?.active == true,
                        extensionBadges = userAddons
                            .filter { !it.badgeText.isNullOrBlank() }
                            .joinToString(" · ") { "${it.name} ${it.badgeText}" }
                            .take(48)
                            .ifBlank { null },
                        menuCommands = scriptCommands,
                        onMenuCommand = { command ->
                            viewModel.runUserscriptCommand(command)
                            sheet = ChromeSheet.None
                        },
                        onAction = { action ->
                            when (action) {
                                MenuAction.Back -> viewModel.goBack()
                                MenuAction.Forward -> viewModel.goForward()
                                MenuAction.Reload -> viewModel.reload()
                                MenuAction.ToggleBookmark -> viewModel.toggleBookmark()
                                MenuAction.NewTab -> viewModel.createTab(false)
                                MenuAction.NewIncognitoTab -> viewModel.createTab(true)
                                MenuAction.Translate -> activeTab?.state?.url?.let { pageUrl ->
                                    translateUrl(pageUrl, language)
                                }?.let(viewModel::navigate)
                                MenuAction.ReaderMode -> viewModel.toggleReaderMode()
                                MenuAction.FindInPage -> viewModel.openFind()
                                MenuAction.Print -> viewModel.printPage()
                                MenuAction.Share -> startPageShare()
                                MenuAction.PageInfo -> Unit
                                MenuAction.AddToHomeScreen -> {
                                    val tab = activeTab
                                    if (tab != null) {
                                        pinToHomeScreen(context, tab.state.url, tab.state.title)
                                    }
                                }
                                MenuAction.Settings -> onOpenSettings()
                                MenuAction.Downloads,
                                MenuAction.History, MenuAction.RecentTabs, MenuAction.Bookmarks,
                                MenuAction.Extensions,
                                MenuAction.Userscripts, MenuAction.Cast,
                                MenuAction.ToggleSiteDarkMode, MenuAction.ToggleDesktopSite -> Unit
                            }
                            sheet = when (action) {
                                MenuAction.History -> ChromeSheet.History
                                MenuAction.RecentTabs -> ChromeSheet.Recent
                                MenuAction.Bookmarks -> ChromeSheet.Bookmarks
                                MenuAction.Downloads -> ChromeSheet.Downloads
                                MenuAction.Extensions -> ChromeSheet.Addons
                                MenuAction.Userscripts -> ChromeSheet.Userscripts
                                MenuAction.Cast -> ChromeSheet.Cast
                                MenuAction.PageInfo -> ChromeSheet.PageInfo
                                else -> ChromeSheet.None
                            }
                        },
                        onToggleSiteDarkMode = viewModel::toggleSiteDarkMode,
                        onToggleDesktopMode = viewModel::setDesktopMode,
                    )
                }
                ChromeSheet.Addons -> {
                    val installedAddons by viewModel.installedAddons.collectAsStateWithLifecycle()
                    val userAddons = remember(installedAddons) { installedAddons.filterNot { it.builtIn } }
                    AddonsSheet(
                        addons = userAddons,
                        onPickFile = { addonPicker.launch(arrayOf("*/*")) },
                        onInstallUrl = viewModel::installAddonFromUrl,
                        onToggle = viewModel::setAddonEnabled,
                        onUninstall = viewModel::uninstallAddon,
                        onOpenPopup = viewModel::openAddonPopup,
                    )
                }
                ChromeSheet.Userscripts -> {
                    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
                    UserscriptsSheet(
                        scripts = scripts,
                        onToggle = viewModel::setUserscriptEnabled,
                        onDelete = viewModel::deleteUserscript,
                        onEdit = { editorId = it },
                        onCheckUpdate = viewModel::checkUserscriptUpdates,
                        onPickFile = { userscriptPicker.launch(arrayOf("*/*")) },
                        onInstallUrl = viewModel::installUserscript,
                        onInstallSource = viewModel::installUserscriptSource,
                    )
                }
                ChromeSheet.Bookmarks -> {
                    val bookmarkList by viewModel.bookmarkList.collectAsStateWithLifecycle()
                    val bookmarkFolders by viewModel.bookmarkFolders.collectAsStateWithLifecycle()
                    BookmarksSheet(
                        bookmarks = bookmarkList,
                        folders = bookmarkFolders,
                        icons = favicons,
                        onOpen = { viewModel.openUrlInNewTab(it) },
                        onCreateFolder = viewModel::createBookmarkFolder,
                        onDeleteFolder = viewModel::deleteBookmarkFolder,
                        onMoveBookmark = viewModel::moveBookmark,
                        onRemoveBookmark = viewModel::removeBookmark,
                        onImportBookmarks = {
                            bookmarkImportLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "*/*"))
                        },
                        onExportBookmarks = {
                            bookmarkExportLauncher.launch("foxium-bookmarks.html")
                        },
                    )
                }
                ChromeSheet.History -> {
                    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
                    HistorySheet(
                        entries = historyList,
                        icons = favicons,
                        onOpen = { viewModel.openUrlInNewTab(it) },
                    )
                }
                ChromeSheet.Recent -> {
                    val recentTabs by viewModel.recentTabs.collectAsStateWithLifecycle()
                    RecentTabsSheet(
                        tabs = recentTabs,
                        onOpen = { url ->
                            viewModel.createTab(false, null, url)
                            sheet = ChromeSheet.None
                        },
                    )
                }
                ChromeSheet.Downloads -> {
                    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
                    DownloadsSheet(
                        downloads = downloads,
                        onDelete = viewModel::deleteDownload,
                        onCancel = viewModel::cancelDownload,
                        onClear = viewModel::clearDownloads,
                    )
                }
                ChromeSheet.Cast -> {
                    val allCastMedia by viewModel.allCastMedia.collectAsStateWithLifecycle()
                    val pageUrl = activeTab?.state?.url
                    val pageMedia = remember(allCastMedia, pageUrl) {
                        if (pageUrl == null) {
                            emptyList()
                        } else {
                            allCastMedia.values.flatten().filter { it.pageUrl == pageUrl }
                        }
                    }
                    CastSheet(
                        media = pageMedia,
                        playback = castPlayback,
                        mirrorUrl = mirrorUrl,
                        onMirror = startMirror,
                        onStopMirror = {
                            MirrorCaptureService.stop(context)
                            viewModel.stopMirror()
                        },
                        onCast = viewModel::castMedia,
                        onPause = viewModel::pauseCast,
                        onResume = viewModel::resumeCast,
                        onStop = viewModel::stopCast,
                        onDisconnect = viewModel::disconnectCast,
                    )
                }
                ChromeSheet.PageInfo -> PageInfoSheet(activeTab)
                ChromeSheet.None -> Unit
            }
        }
    }

    val popupValue = popup
    if (popupValue != null) {
        val density = LocalDensity.current
        val containerHeightPx = LocalWindowInfo.current.containerSize.height
        val popupHeight = (with(density) { containerHeightPx.toDp() } * 0.62f).coerceAtLeast(320.dp)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeAddonPopup,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                popupValue.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            RawGeckoView(
                popupValue.session,
                Modifier.fillMaxWidth().height(popupHeight).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    val editing = editorId
    if (editing != null) {
        val scripts by viewModel.scripts.collectAsStateWithLifecycle()
        val script = remember(scripts, editing) { scripts.firstOrNull { it.id == editing } }
        if (script == null) {
            LaunchedEffect(editing) { editorId = null }
        } else {
            UserscriptEditorDialog(
                script = script,
                onSave = { source ->
                    viewModel.saveUserscriptSource(script.id, source) { editorId = null }
                },
                onDismiss = { editorId = null },
            )
        }
    }
}

@Composable
private fun NewTabPageHost(
    viewModel: BrowserViewModel,
    icons: Map<String, ImageBitmap>,
    isPrivate: Boolean,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookmarkList by viewModel.bookmarkList.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val hiddenShortcuts by viewModel.hiddenShortcuts.collectAsStateWithLifecycle()
    val customShortcuts by viewModel.customShortcuts.collectAsStateWithLifecycle()
    val discoverItems by viewModel.discoverItems.collectAsStateWithLifecycle()
    val discoverEnabled by viewModel.discoverEnabled.collectAsStateWithLifecycle()
    val discoverMode by viewModel.discoverMode.collectAsStateWithLifecycle()
    val discoverSources by viewModel.discoverSources.collectAsStateWithLifecycle()
    val discoverRssFeeds by viewModel.discoverRssFeeds.collectAsStateWithLifecycle()
    val discoverLimit by viewModel.discoverLimit.collectAsStateWithLifecycle()
    val hiddenDiscover by viewModel.hiddenDiscover.collectAsStateWithLifecycle()
    LaunchedEffect(
        discoverEnabled,
        discoverMode,
        discoverSources,
        discoverRssFeeds,
        discoverLimit,
        hiddenDiscover,
    ) {
        viewModel.loadDiscover(force = true)
    }
    val shortcutTiles = remember(bookmarkList, historyList, hiddenShortcuts, customShortcuts) {
        val derived = buildNewTabShortcuts(
            bookmarks = bookmarkList.map { it.url to it.title },
            history = historyList.map { it.url to it.title },
            limit = 16,
        ).filterNot { it.host in hiddenShortcuts }
        val pinned = customShortcuts.mapNotNull { shortcut ->
            val host = faviconHost(shortcut.url) ?: return@mapNotNull null
            NewTabShortcut(
                url = shortcut.url,
                label = shortcut.label.ifBlank { host.removePrefix("www.") },
                host = host,
            )
        }
        (pinned + derived.filterNot { tile -> pinned.any { it.host == tile.host } }).take(8)
    }
    val pinnedUrls = remember(customShortcuts) { customShortcuts.map { it.url }.toSet() }
    val activeSearchEngine by viewModel.activeSearchEngine.collectAsStateWithLifecycle()
    ChromeNewTabPage(
        shortcuts = shortcutTiles,
        icons = icons,
        isPrivate = isPrivate,
        discover = discoverItems,
        discoverEnabled = discoverEnabled,
        discoverMode = discoverMode,
        discoverSources = discoverSources,
        discoverRssFeeds = discoverRssFeeds,
        discoverLimit = discoverLimit,
        hiddenDiscoverCount = hiddenDiscover.size,
        onOpen = viewModel::navigate,
        onSearch = onSearch,
        onVoice = onVoice,
        onRemoveShortcut = viewModel::hideShortcut,
        onRestoreShortcuts = viewModel::restoreShortcuts,
        pinnedUrls = pinnedUrls,
        searchEngineName = activeSearchEngine.name,
        onAddShortcut = viewModel::addCustomShortcut,
        onRenameShortcut = viewModel::renameCustomShortcut,
        onRefreshDiscover = { viewModel.loadDiscover(force = true) },
        onSetDiscoverEnabled = viewModel::setDiscoverEnabled,
        onSetDiscoverMode = viewModel::setDiscoverMode,
        onToggleDiscoverSource = viewModel::toggleDiscoverSource,
        onAddDiscoverRssFeed = viewModel::addDiscoverRssFeed,
        onRemoveDiscoverRssFeed = viewModel::removeDiscoverRssFeed,
        onSetDiscoverLimit = viewModel::setDiscoverLimit,
        onHideDiscoverArticle = viewModel::hideDiscoverArticle,
        onResetHiddenDiscoverArticles = viewModel::resetHiddenDiscoverArticles,
        modifier = modifier,
    )
}

/**
 * Chrome's "Add to home screen": a pinned shortcut that re-enters the activity with the page URL,
 * which [ChromeBrowserScreen] then opens as a tab.
 */
private fun pinToHomeScreen(context: Context, url: String, title: String) {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return
    if (uri.scheme != "http" && uri.scheme != "https") return
    val shortcut = ShortcutInfoCompat.Builder(context, "page:${uri.toString().hashCode()}")
        .setShortLabel((title.ifBlank { uri.host.orEmpty() }.ifBlank { "Foxium" }).take(40))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
        .setIntent(
            Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}

@Composable
private fun AppUpdateDialog(
    update: AvailableUpdate,
    downloading: UpdateState.Downloading?,
    readyToInstall: UpdateState.ReadyToInstall?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val titleText = if (update.isPrerelease) {
        stringResource(R.string.update_dialog_title_experimental, update.versionName)
    } else {
        stringResource(R.string.update_dialog_title, update.versionName)
    }
    AlertDialog(
        onDismissRequest = { if (downloading == null) onDismiss() },
        title = { Text(titleText) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val sizeLabel = Formatter.formatShortFileSize(context, update.apkSizeBytes)
                Text(
                    text = "${update.apkName} • $sizeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (downloading != null) {
                    Spacer(Modifier.height(14.dp))
                    if (downloading.totalBytes > 0L) {
                        val fraction = (downloading.downloadedBytes.toFloat() / downloading.totalBytes.toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.update_download_progress,
                                Formatter.formatShortFileSize(context, downloading.downloadedBytes),
                                Formatter.formatShortFileSize(context, downloading.totalBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else if (readyToInstall?.needsInstallPermission == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_updates_permission_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            when {
                downloading != null -> TextButton(enabled = false, onClick = {}) {
                    Text(stringResource(R.string.update_downloading))
                }
                readyToInstall != null -> TextButton(onClick = onInstall) {
                    Text(
                        stringResource(
                            if (readyToInstall.needsInstallPermission) {
                                R.string.update_allow_and_install
                            } else {
                                R.string.update_install
                            },
                        ),
                    )
                }
                else -> TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download_install))
                }
            }
        },
        dismissButton = if (downloading == null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        } else {
            null
        },
    )
}
