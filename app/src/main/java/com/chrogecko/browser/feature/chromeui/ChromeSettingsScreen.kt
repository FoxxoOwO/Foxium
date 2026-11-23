package com.chrogecko.browser.feature.chromeui

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrogecko.browser.BuildConfig
import com.chrogecko.browser.R
import com.chrogecko.browser.core.preferences.AppLanguage
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_HACKER_NEWS
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_RSS
import com.chrogecko.browser.core.preferences.DISCOVER_SOURCES
import com.chrogecko.browser.feature.darkmode.DarkModeEngine

sealed interface SettingsRoute {
    data object Main : SettingsRoute
    data object SearchEngine : SettingsRoute
    data object Suggestions : SettingsRoute
    data object Downloads : SettingsRoute
    data object Discover : SettingsRoute
    data object Language : SettingsRoute
    data object AddressBar : SettingsRoute
    data object Appearance : SettingsRoute
    data object Passwords : SettingsRoute
    data object PaymentMethods : SettingsRoute
    data object Addresses : SettingsRoute
    data object ClearBrowsingData : SettingsRoute
    data object Cookies : SettingsRoute
    data object HttpsOnly : SettingsRoute
    data object SecureDns : SettingsRoute
    data object SiteSettings : SettingsRoute
    data object TextExpander : SettingsRoute
    data object About : SettingsRoute
}

private data class MainSettingsNavRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: SettingsRoute,
)

private data class MainSettingsToggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

/**
 * Full-screen Material 3 Expressive Chrome settings experience with an internal back stack
 * so both main settings and every submenu occupy the entire window with system-back support.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChromeSettingsScreen(
    viewModel: BrowserViewModel,
    onClose: () -> Unit,
) {
    var backStack by remember { mutableStateOf<List<SettingsRoute>>(listOf(SettingsRoute.Main)) }

    val navigateTo: (SettingsRoute) -> Unit = { route ->
        if (backStack.lastOrNull() != route) {
            backStack = backStack + route
        }
    }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true) { navigateBack() }

    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = backStack,
            contentKey = { it.last() },
            transitionSpec = {
                val forward = targetState.size >= initialState.size
                if (forward) {
                    (slideInHorizontally(spatialSpec) { it / 3 } + fadeIn(effectsSpec)) togetherWith
                        (slideOutHorizontally(spatialSpec) { -it / 6 } + fadeOut(effectsSpec))
                } else {
                    (slideInHorizontally(spatialSpec) { -it / 4 } + fadeIn(effectsSpec)) togetherWith
                        (slideOutHorizontally(spatialSpec) { it / 3 } + fadeOut(effectsSpec))
                }
            },
            label = "settingsRoute",
        ) { stack ->
            when (stack.last()) {
                SettingsRoute.Main -> MainSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                    onNavigate = navigateTo,
                )
                SettingsRoute.SearchEngine -> SearchEngineSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Suggestions -> SuggestionsSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Downloads -> DownloadsSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Discover -> DiscoverSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Language -> LanguageSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.AddressBar -> AddressBarSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Appearance -> AppearanceSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Passwords -> PasswordsSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.PaymentMethods -> PaymentMethodsSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Addresses -> AddressesSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.ClearBrowsingData -> ClearBrowsingDataSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.Cookies -> CookiesSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.HttpsOnly -> HttpsOnlySettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.SecureDns -> SecureDnsSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.SiteSettings -> SiteSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.TextExpander -> TextExpanderSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
                SettingsRoute.About -> AboutSettingsPage(
                    viewModel = viewModel,
                    onBack = navigateBack,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    large: Boolean = false,
    hasFab: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            val navIcon: @Composable () -> Unit = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            }
            val titleContent: @Composable () -> Unit = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val subtitleContent: (@Composable () -> Unit)? = subtitle?.let { text ->
                { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (large) {
                LargeFlexibleTopAppBar(
                    title = titleContent,
                    subtitle = subtitleContent,
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumFlexibleTopAppBar(
                    title = titleContent,
                    subtitle = subtitleContent,
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val extraBottom: Dp = if (hasFab) 96.dp else 28.dp
        val listPadding = PaddingValues(
            start = innerPadding.calculateLeftPadding(layoutDirection) + 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            end = innerPadding.calculateRightPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + extraBottom,
        )
        content(listPadding)
    }
}

internal fun settingsGroupItemShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(6.dp)
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroupSection(
    title: String,
    items: List<MainSettingsNavRow>,
    onNavigate: (SettingsRoute) -> Unit,
    toggles: List<MainSettingsToggle> = emptyList(),
) {
    SettingsSectionHeader(title)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val rows = toggles.size + items.size
        toggles.forEachIndexed { index, toggle ->
            SettingsToggleRow(
                title = toggle.title,
                description = toggle.description,
                checked = toggle.checked,
                shape = settingsGroupItemShape(index, rows),
                onCheckedChange = toggle.onCheckedChange,
            )
        }
        items.forEachIndexed { index, item ->
            val shape = settingsGroupItemShape(toggles.size + index, rows)
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable { onNavigate(item.route) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun countText(
    count: Int,
    @StringRes none: Int,
    @PluralsRes plural: Int,
): String = if (count == 0) stringResource(none) else pluralStringResource(plural, count, count)

@Composable
private fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.CZECH -> R.string.settings_language_czech
        AppLanguage.ENGLISH -> R.string.settings_language_english
    },
)

@Composable
private fun MainSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
) {
    val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val darkEngine by viewModel.darkEngine.collectAsStateWithLifecycle()
    val toolbarBottom by viewModel.toolbarBottom.collectAsStateWithLifecycle()
    val searchEngine by viewModel.activeSearchEngine.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val savedLogins by viewModel.savedLogins.collectAsStateWithLifecycle()
    val savedCards by viewModel.savedCards.collectAsStateWithLifecycle()
    val savedAddresses by viewModel.savedAddresses.collectAsStateWithLifecycle()
    val siteRules by viewModel.siteRuleList.collectAsStateWithLifecycle()
    val textExpanderEnabled by viewModel.textExpanderEnabled.collectAsStateWithLifecycle()
    val textSnippets by viewModel.textSnippets.collectAsStateWithLifecycle()
    val trackingProtection by viewModel.trackingProtection.collectAsStateWithLifecycle()
    val safeBrowsing by viewModel.safeBrowsing.collectAsStateWithLifecycle()
    val globalPrivacyControl by viewModel.globalPrivacyControl.collectAsStateWithLifecycle()
    val queryParameterStripping by viewModel.queryParameterStripping.collectAsStateWithLifecycle()
    val cookiePolicy by viewModel.cookiePolicy.collectAsStateWithLifecycle()
    val httpsOnlyMode by viewModel.httpsOnlyMode.collectAsStateWithLifecycle()
    val dohMode by viewModel.dohMode.collectAsStateWithLifecycle()
    val dohProvider by viewModel.dohProvider.collectAsStateWithLifecycle()
    val dohCustomUri by viewModel.dohCustomUri.collectAsStateWithLifecycle()

    val engineLabel = stringResource(
        if (darkEngine == DarkModeEngine.DYNAMIC.id) {
            R.string.settings_dark_engine_dynamic_short
        } else {
            R.string.settings_dark_engine_invert_short
        },
    )
    val darkSummary = if (forceDark) {
        stringResource(R.string.settings_dark_summary_on, engineLabel)
    } else {
        stringResource(R.string.settings_dark_summary_off)
    }
    val basicsItems = listOf(
        MainSettingsNavRow(
            title = stringResource(R.string.settings_search_engine_title),
            subtitle = searchEngine.name,
            icon = Icons.Outlined.Search,
            route = SettingsRoute.SearchEngine,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_suggestions_title),
            subtitle = stringResource(R.string.settings_suggestions_subtitle),
            icon = Icons.Outlined.Search,
            route = SettingsRoute.Suggestions,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_downloads_title),
            subtitle = stringResource(R.string.settings_download_location_title),
            icon = Icons.Outlined.Download,
            route = SettingsRoute.Downloads,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.ntp_discover),
            subtitle = stringResource(R.string.discover_settings_subtitle),
            icon = Icons.Outlined.Description,
            route = SettingsRoute.Discover,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_language_title),
            subtitle = languageName(AppLanguage.fromTag(appLanguage)),
            icon = Icons.Outlined.Translate,
            route = SettingsRoute.Language,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_address_bar_title),
            subtitle = stringResource(
                if (toolbarBottom) R.string.settings_toolbar_bottom else R.string.settings_toolbar_top,
            ),
            icon = Icons.Outlined.ViewAgenda,
            route = SettingsRoute.AddressBar,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_dark_mode_title),
            subtitle = darkSummary,
            icon = Icons.Outlined.DarkMode,
            route = SettingsRoute.Appearance,
        ),
    )
    val autofillItems = listOf(
        MainSettingsNavRow(
            title = stringResource(R.string.settings_passwords_title),
            subtitle = countText(
                savedLogins.size,
                R.string.settings_passwords_none,
                R.plurals.settings_passwords_count,
            ),
            icon = Icons.Outlined.Key,
            route = SettingsRoute.Passwords,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_payments_title),
            subtitle = countText(
                savedCards.size,
                R.string.settings_cards_none,
                R.plurals.settings_cards_count,
            ),
            icon = Icons.Outlined.CreditCard,
            route = SettingsRoute.PaymentMethods,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_addresses_title),
            subtitle = countText(
                savedAddresses.size,
                R.string.settings_addresses_none,
                R.plurals.settings_addresses_count,
            ),
            icon = Icons.Outlined.LocationOn,
            route = SettingsRoute.Addresses,
        ),
    )
    val privacyItems = listOf(
        MainSettingsNavRow(
            title = stringResource(R.string.settings_clear_data_title),
            subtitle = stringResource(R.string.settings_clear_data_summary),
            icon = Icons.Outlined.CleaningServices,
            route = SettingsRoute.ClearBrowsingData,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_cookies_title),
            subtitle = stringResource(cookiePolicyShortRes(cookiePolicy)),
            icon = Icons.Outlined.Cookie,
            route = SettingsRoute.Cookies,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_https_title),
            subtitle = stringResource(httpsOnlyShortRes(httpsOnlyMode)),
            icon = Icons.Outlined.Lock,
            route = SettingsRoute.HttpsOnly,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_doh_title),
            subtitle = dohSummaryText(dohMode, dohProvider, dohCustomUri),
            icon = Icons.Outlined.Dns,
            route = SettingsRoute.SecureDns,
        ),
        MainSettingsNavRow(
            title = stringResource(R.string.settings_site_settings_title),
            subtitle = countText(
                siteRules.size,
                R.string.settings_site_settings_default,
                R.plurals.settings_site_settings_count,
            ),
            icon = Icons.Outlined.Language,
            route = SettingsRoute.SiteSettings,
        ),
    )
    val toolsItems = listOf(
        MainSettingsNavRow(
            title = stringResource(R.string.settings_expander_title),
            subtitle = if (!textExpanderEnabled) {
                stringResource(R.string.settings_expander_off)
            } else {
                countText(
                    textSnippets.size,
                    R.string.settings_expander_on_empty,
                    R.plurals.settings_expander_on_count,
                )
            },
            icon = Icons.Outlined.AutoAwesome,
            route = SettingsRoute.TextExpander,
        ),
    )
    val aboutItems = listOf(
        MainSettingsNavRow(
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_version_summary, BuildConfig.VERSION_NAME),
            icon = Icons.Outlined.Info,
            route = SettingsRoute.About,
        ),
    )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_title),
        large = true,
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "basics") {
                SettingsGroupSection(stringResource(R.string.settings_section_basics), basicsItems, onNavigate)
            }
            item(key = "autofill") {
                SettingsGroupSection(stringResource(R.string.settings_section_autofill), autofillItems, onNavigate)
            }
            item(key = "privacy") {
                SettingsGroupSection(
                    stringResource(R.string.settings_section_privacy),
                    privacyItems,
                    onNavigate,
                    toggles = listOf(
                        MainSettingsToggle(
                            title = stringResource(R.string.settings_tracking_title),
                            description = stringResource(R.string.settings_tracking_desc),
                            checked = trackingProtection,
                            onCheckedChange = viewModel::setTrackingProtection,
                        ),
                        MainSettingsToggle(
                            title = stringResource(R.string.settings_safe_browsing_title),
                            description = stringResource(R.string.settings_safe_browsing_desc),
                            checked = safeBrowsing,
                            onCheckedChange = viewModel::setSafeBrowsing,
                        ),
                        MainSettingsToggle(
                            title = stringResource(R.string.settings_gpc_title),
                            description = stringResource(R.string.settings_gpc_desc),
                            checked = globalPrivacyControl,
                            onCheckedChange = viewModel::setGlobalPrivacyControl,
                        ),
                        MainSettingsToggle(
                            title = stringResource(R.string.settings_qps_title),
                            description = stringResource(R.string.settings_qps_desc),
                            checked = queryParameterStripping,
                            onCheckedChange = viewModel::setQueryParameterStripping,
                        ),
                    ),
                )
            }
            item(key = "tools") {
                SettingsGroupSection(stringResource(R.string.settings_section_productivity), toolsItems, onNavigate)
            }
            item(key = "about") {
                SettingsGroupSection(stringResource(R.string.settings_section_about), aboutItems, onNavigate)
            }
        }
    }
}

@Composable
private fun LanguageSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val selected = AppLanguage.fromTag(appLanguage)
    val systemLanguageName = remember {
        val locale = AppLocaleManager.resolveLocale(AppLanguage.SYSTEM.tag)
        val name = locale.getDisplayLanguage(locale)
        if (name.isEmpty()) name else name.substring(0, 1).uppercase(locale) + name.substring(1)
    }
    val languages = AppLanguage.entries

    SettingsPageScaffold(
        title = stringResource(R.string.settings_language_title),
        subtitle = stringResource(R.string.settings_language_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(languages, key = { _, language -> language.tag }) { index, language ->
                val shape = settingsGroupItemShape(index, languages.size)
                val isSelected = selected == language
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    label = "languageColor",
                )
                Surface(
                    shape = shape,
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .clickable { viewModel.setAppLanguage(language) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setAppLanguage(language) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(languageName(language), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (language) {
                                    AppLanguage.SYSTEM -> stringResource(
                                        R.string.settings_language_system_desc,
                                        systemLanguageName,
                                    )
                                    AppLanguage.CZECH -> stringResource(R.string.settings_language_czech_desc)
                                    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val enabled by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val fromBookmarks by viewModel.suggestionsFromBookmarks.collectAsStateWithLifecycle()
    val fromHistory by viewModel.suggestionsFromHistory.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_suggestions_title),
        subtitle = stringResource(R.string.settings_suggestions_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "master") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_suggestions_title),
                    description = stringResource(R.string.settings_suggestions_subtitle),
                    checked = enabled,
                    shape = settingsGroupItemShape(0, 3),
                    onCheckedChange = viewModel::setSearchSuggestions,
                )
            }
            item(key = "bookmarks") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_suggestions_bookmarks),
                    description = stringResource(R.string.settings_suggestions_bookmarks_desc),
                    checked = fromBookmarks && enabled,
                    shape = settingsGroupItemShape(1, 3),
                    onCheckedChange = viewModel::setSuggestionsFromBookmarks,
                )
            }
            item(key = "history") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_suggestions_history),
                    description = stringResource(R.string.settings_suggestions_history_desc),
                    checked = fromHistory && enabled,
                    shape = settingsGroupItemShape(2, 3),
                    onCheckedChange = viewModel::setSuggestionsFromHistory,
                )
            }
        }
    }
}

@Composable
private fun DownloadsSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val location by viewModel.downloadLocation.collectAsStateWithLifecycle()
    SettingsPageScaffold(
        title = stringResource(R.string.settings_downloads_title),
        subtitle = stringResource(R.string.settings_download_location_desc),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "location") {
                val options = listOf("downloads" to R.string.settings_download_public, "app" to R.string.settings_download_private)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEach { (value, label) ->
                        val shape = RoundedCornerShape(16.dp)
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { viewModel.setDownloadLocation(value) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = location == value,
                                    onClick = { viewModel.setDownloadLocation(value) },
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val enabled by viewModel.discoverEnabled.collectAsStateWithLifecycle()
    val mode by viewModel.discoverMode.collectAsStateWithLifecycle()
    val sources by viewModel.discoverSources.collectAsStateWithLifecycle()
    val rssFeeds by viewModel.discoverRssFeeds.collectAsStateWithLifecycle()
    val limit by viewModel.discoverLimit.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenDiscover.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.ntp_discover),
        subtitle = stringResource(R.string.discover_settings_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "show") {
                SettingsToggleRow(
                    title = stringResource(R.string.discover_show),
                    description = stringResource(R.string.discover_settings_subtitle),
                    checked = enabled,
                    shape = settingsGroupItemShape(0, 3),
                    onCheckedChange = viewModel::setDiscoverEnabled,
                )
            }
            item(key = "feed_type") {
                SettingsSectionHeader(stringResource(R.string.discover_feed_type))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = mode != DISCOVER_MODE_RSS,
                        onClick = { viewModel.setDiscoverMode(DISCOVER_MODE_HACKER_NEWS) },
                        label = { Text(stringResource(R.string.discover_mode_hn)) },
                    )
                    FilterChip(
                        selected = mode == DISCOVER_MODE_RSS,
                        onClick = { viewModel.setDiscoverMode(DISCOVER_MODE_RSS) },
                        label = { Text(stringResource(R.string.discover_mode_rss)) },
                    )
                }
            }
            item(key = "source") {
                if (mode == DISCOVER_MODE_RSS) {
                    SettingsSectionHeader(stringResource(R.string.discover_rss_sources))
                    DiscoverRssSourcesEditor(
                        rssFeeds = rssFeeds,
                        onAddRssFeed = viewModel::addDiscoverRssFeed,
                        onRemoveRssFeed = viewModel::removeDiscoverRssFeed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    SettingsSectionHeader(stringResource(R.string.discover_source))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DISCOVER_SOURCES.forEach { key ->
                            val label = when (key) {
                                "new" -> stringResource(R.string.discover_source_new)
                                "best" -> stringResource(R.string.discover_source_best)
                                "show" -> stringResource(R.string.discover_source_show)
                                else -> stringResource(R.string.discover_source_top)
                            }
                            FilterChip(
                                selected = key in sources,
                                onClick = { viewModel.toggleDiscoverSource(key, key !in sources) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            item(key = "count") {
                SettingsSectionHeader(stringResource(R.string.discover_count))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(6, 12, 20).forEach { count ->
                        FilledTonalButton(
                            onClick = { viewModel.setDiscoverLimit(count) },
                            enabled = limit != count,
                        ) { Text(count.toString()) }
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item(key = "reset") {
                    TextButton(onClick = viewModel::resetHiddenDiscoverArticles) {
                        Text(stringResource(R.string.discover_reset_hidden, hidden.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val savedCustomName by viewModel.customSearchName.collectAsStateWithLifecycle()
    val savedCustomTemplate by viewModel.customSearchTemplate.collectAsStateWithLifecycle()
    val engines = SearchEngines.all
    val totalRows = engines.size + 1

    SettingsPageScaffold(
        title = stringResource(R.string.settings_search_engine_title),
        subtitle = stringResource(R.string.settings_search_engine_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(engines, key = { _, item -> item.id }) { index, engine ->
                val selected = searchEngine == engine.id
                val shape = settingsGroupItemShape(index, totalRows)
                val host = remember(engine.template) {
                    engine.template.toUri().host?.removePrefix("www.") ?: engine.template
                }
                SearchEngineOptionRow(
                    title = engine.name,
                    subtitle = host,
                    selected = selected,
                    shape = shape,
                    onClick = { viewModel.setSearchEngine(engine.id) },
                )
            }
            item(key = SearchEngines.CUSTOM_ID) {
                val customSelected = searchEngine == SearchEngines.CUSTOM_ID
                val subtitle = SearchEngines.normalizeCustomTemplate(savedCustomTemplate)
                    ?: stringResource(R.string.settings_search_custom_desc)
                SearchEngineOptionRow(
                    title = stringResource(R.string.settings_search_custom_title),
                    subtitle = subtitle,
                    selected = customSelected,
                    shape = settingsGroupItemShape(engines.size, totalRows),
                    onClick = { viewModel.setSearchEngine(SearchEngines.CUSTOM_ID) },
                )
            }
            if (searchEngine == SearchEngines.CUSTOM_ID) {
                item(key = "custom_form") {
                    CustomSearchEngineForm(
                        savedName = savedCustomName,
                        savedTemplate = savedCustomTemplate,
                        onSave = viewModel::setCustomSearchEngine,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEngineOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "searchEngineColor",
    )
    Surface(
        shape = shape,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CustomSearchEngineForm(
    savedName: String,
    savedTemplate: String,
    onSave: (name: String, template: String) -> Unit,
) {
    var nameInput by remember(savedName) { mutableStateOf(savedName) }
    var templateInput by remember(savedTemplate) { mutableStateOf(savedTemplate) }
    val normalizedTemplate = SearchEngines.normalizeCustomTemplate(templateInput)
    val showError = templateInput.isNotBlank() && normalizedTemplate == null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.settings_search_custom_name_label)) },
                placeholder = { Text(stringResource(R.string.settings_search_custom_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = templateInput,
                onValueChange = { templateInput = it },
                label = { Text(stringResource(R.string.settings_search_custom_template_label)) },
                placeholder = { Text(stringResource(R.string.settings_search_custom_template_placeholder)) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.settings_search_custom_template_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (normalizedTemplate != null) {
                            onSave(nameInput, normalizedTemplate)
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (normalizedTemplate != null) {
                        onSave(nameInput, normalizedTemplate)
                    }
                },
                enabled = normalizedTemplate != null,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.settings_search_custom_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddressBarSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val toolbarBottom by viewModel.toolbarBottom.collectAsStateWithLifecycle()
    val toolbarAutoHide by viewModel.toolbarAutoHide.collectAsStateWithLifecycle()
    val toolbarShortcut by viewModel.toolbarShortcut.collectAsStateWithLifecycle()
    val toggleColors = ToggleButtonDefaults.tonalToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val verticalBias by animateFloatAsState(
        targetValue = if (toolbarBottom) 1f else -1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "addressBarPreviewBias",
    )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_address_bar_title),
        subtitle = stringResource(R.string.settings_address_bar_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "preview") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.width(156.dp).height(216.dp),
                        ) {
                            Box(Modifier.fillMaxSize().padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .align(BiasAlignment(0f, verticalBias))
                                        .fillMaxWidth()
                                        .height(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier
                                                .width(64.dp)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            ToggleButton(
                                checked = !toolbarBottom,
                                onCheckedChange = { if (it) viewModel.setToolbarBottom(false) },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                colors = toggleColors,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.settings_toolbar_top))
                            }
                            ToggleButton(
                                checked = toolbarBottom,
                                onCheckedChange = { if (it) viewModel.setToolbarBottom(true) },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                colors = toggleColors,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.settings_toolbar_bottom))
                            }
                        }
                    }
                }
            }
            item(key = "options") {
                val placements = listOf(
                    false to (stringResource(R.string.settings_toolbar_top) to stringResource(R.string.settings_toolbar_top_desc)),
                    true to (stringResource(R.string.settings_toolbar_bottom) to stringResource(R.string.settings_toolbar_bottom_desc)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    placements.forEachIndexed { index, (isBottom, copy) ->
                        val shape = settingsGroupItemShape(index, placements.size)
                        val selected = toolbarBottom == isBottom
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { viewModel.setToolbarBottom(isBottom) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.setToolbarBottom(isBottom) },
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(copy.first, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        copy.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "autohide") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toolbar_autohide_title),
                    description = stringResource(R.string.settings_toolbar_autohide_desc),
                    checked = toolbarAutoHide,
                    shape = RoundedCornerShape(16.dp),
                    onCheckedChange = viewModel::setToolbarAutoHide,
                )
            }
            item(key = "shortcut") {
                SettingsSectionHeader(stringResource(R.string.settings_toolbar_shortcut_title))
                val shortcuts = listOf("none", "home", "newTab", "bookmarks", "history", "closeTab")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shortcuts.forEach { key ->
                        val labelRes = when (key) {
                            "home" -> R.string.cd_home
                            "newTab" -> R.string.new_tab
                            "bookmarks" -> R.string.bookmarks_title
                            "history" -> R.string.history_title
                            "closeTab" -> R.string.close
                            else -> R.string.settings_toolbar_shortcut_none
                        }
                        val shape = RoundedCornerShape(16.dp)
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { viewModel.setToolbarShortcut(key) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = toolbarShortcut == key,
                                    onClick = { viewModel.setToolbarShortcut(key) },
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val darkEngine by viewModel.darkEngine.collectAsStateWithLifecycle()
    val heroContainer by animateColorAsState(
        targetValue = if (forceDark) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "darkHeroColor",
    )
    val heroContent = if (forceDark) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_dark_mode_title),
        subtitle = stringResource(R.string.settings_dark_mode_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "master") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = heroContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { viewModel.setForceDark(!forceDark) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_force_dark_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = heroContent,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.settings_force_dark_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = heroContent.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = forceDark, onCheckedChange = viewModel::setForceDark)
                    }
                }
            }
            item(key = "engine_header") {
                SettingsSectionHeader(stringResource(R.string.settings_dark_engine_header))
            }
            itemsIndexed(DarkModeEngine.entries, key = { _, e -> e.id }) { index, candidate ->
                val shape = settingsGroupItemShape(index, DarkModeEngine.entries.size)
                val selected = darkEngine == candidate.id
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .clickable { viewModel.setDarkEngine(candidate) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.setDarkEngine(candidate) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (candidate) {
                                    DarkModeEngine.INVERT -> stringResource(R.string.settings_dark_engine_invert)
                                    DarkModeEngine.DYNAMIC -> stringResource(R.string.settings_dark_engine_dynamic)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                when (candidate) {
                                    DarkModeEngine.INVERT ->
                                        stringResource(R.string.settings_dark_engine_invert_desc)
                                    DarkModeEngine.DYNAMIC ->
                                        stringResource(R.string.settings_dark_engine_dynamic_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "note") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_dark_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    shape: RoundedCornerShape,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
