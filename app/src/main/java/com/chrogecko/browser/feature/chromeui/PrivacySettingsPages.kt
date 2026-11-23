package com.chrogecko.browser.feature.chromeui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrogecko.browser.R
import com.chrogecko.browser.core.preferences.CookieBannerMode
import com.chrogecko.browser.core.preferences.CookiePolicy
import com.chrogecko.browser.core.preferences.DohMode
import com.chrogecko.browser.core.preferences.DohProvider
import com.chrogecko.browser.core.preferences.DohUriValidator
import com.chrogecko.browser.core.preferences.HttpsOnlyMode

/** Subpage configuring third-party cookie isolation, automatic banner handling, and bounce tracking cleanup. */
@Composable
internal fun CookiesSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val policy by viewModel.cookiePolicy.collectAsStateWithLifecycle()
    val bannerMode by viewModel.cookieBannerMode.collectAsStateWithLifecycle()
    val bounceProtection by viewModel.bounceTrackingProtection.collectAsStateWithLifecycle()
    val bannerModes = remember {
        listOf(CookieBannerMode.REJECT, CookieBannerMode.REJECT_OR_ACCEPT, CookieBannerMode.DISABLED)
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_cookies_title),
        subtitle = stringResource(R.string.settings_cookies_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "cookie_header") {
                SettingsSectionHeader(stringResource(R.string.settings_cookies_policy_header))
            }
            radioGroup(
                items = CookiePolicy.entries,
                selected = policy,
                keyOf = { it.id },
                titleRes = ::cookiePolicyLabelRes,
                descRes = ::cookiePolicyDescRes,
                onSelect = viewModel::setCookiePolicy,
            )
            item(key = "banner_header") {
                SettingsSectionHeader(stringResource(R.string.settings_cookie_banners_header))
            }
            radioGroup(
                items = bannerModes,
                selected = bannerMode,
                keyOf = { "banner_${it.id}" },
                titleRes = ::cookieBannerTitleRes,
                descRes = ::cookieBannerDescRes,
                onSelect = viewModel::setCookieBannerMode,
            )
            item(key = "bounce_header") {
                SettingsSectionHeader(stringResource(R.string.settings_bounce_tracking_header))
            }
            item(key = "bounce_toggle") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_bounce_tracking_title),
                    description = stringResource(R.string.settings_bounce_tracking_desc),
                    checked = bounceProtection,
                    shape = RoundedCornerShape(20.dp),
                    onCheckedChange = viewModel::setBounceTrackingProtection,
                )
            }
        }
    }
}

/** Subpage configuring HTTPS-Only connection upgrading across normal and incognito tabs. */
@Composable
internal fun HttpsOnlySettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val mode by viewModel.httpsOnlyMode.collectAsStateWithLifecycle()
    val modes = remember { listOf(HttpsOnlyMode.ALL, HttpsOnlyMode.PRIVATE_ONLY, HttpsOnlyMode.OFF) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_https_title),
        subtitle = stringResource(R.string.settings_https_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            radioGroup(
                items = modes,
                selected = mode,
                keyOf = { it.id },
                titleRes = ::httpsOnlyTitleRes,
                descRes = ::httpsOnlyDescRes,
                onSelect = viewModel::setHttpsOnlyMode,
            )
        }
    }
}

/** Subpage configuring DNS-over-HTTPS mode, built-in providers, and validated custom resolver URLs. */
@Composable
internal fun SecureDnsSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val mode by viewModel.dohMode.collectAsStateWithLifecycle()
    val provider by viewModel.dohProvider.collectAsStateWithLifecycle()
    val savedCustomUri by viewModel.dohCustomUri.collectAsStateWithLifecycle()
    var customUriInput by remember(savedCustomUri) { mutableStateOf(savedCustomUri) }
    val normalizedCustom = DohUriValidator.normalize(customUriInput)
    val showCustomError = customUriInput.isNotBlank() && normalizedCustom == null

    SettingsPageScaffold(
        title = stringResource(R.string.settings_doh_title),
        subtitle = stringResource(R.string.settings_doh_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "doh_mode_header") {
                SettingsSectionHeader(stringResource(R.string.settings_doh_mode_header))
            }
            radioGroup(
                items = DohMode.entries,
                selected = mode,
                keyOf = { it.id },
                titleRes = ::dohModeTitleRes,
                descRes = ::dohModeDescRes,
                onSelect = viewModel::setDohMode,
            )
            if (mode != DohMode.OFF) {
                item(key = "doh_provider_header") {
                    SettingsSectionHeader(stringResource(R.string.settings_doh_provider_header))
                }
                radioGroup(
                    items = DohProvider.entries,
                    selected = provider,
                    keyOf = { it.id },
                    titleRes = ::dohProviderTitleRes,
                    descRes = ::dohProviderDescRes,
                    onSelect = viewModel::setDohProvider,
                )
                if (provider == DohProvider.CUSTOM) {
                    item(key = "doh_custom_form") {
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
                                    value = customUriInput,
                                    onValueChange = { customUriInput = it },
                                    label = { Text(stringResource(R.string.settings_doh_custom_label)) },
                                    placeholder = {
                                        Text(stringResource(R.string.settings_doh_custom_placeholder))
                                    },
                                    singleLine = true,
                                    isError = showCustomError,
                                    supportingText = if (showCustomError) {
                                        { Text(stringResource(R.string.settings_doh_custom_error)) }
                                    } else {
                                        null
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (normalizedCustom != null) {
                                                viewModel.setDohCustomUri(normalizedCustom)
                                            }
                                        },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        if (normalizedCustom != null) {
                                            viewModel.setDohCustomUri(normalizedCustom)
                                        }
                                    },
                                    enabled = normalizedCustom != null,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(R.string.settings_doh_custom_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> LazyListScope.radioGroup(
    items: List<T>,
    selected: T,
    keyOf: (T) -> String,
    titleRes: (T) -> Int,
    descRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
        val shape = settingsGroupItemShape(index, items.size)
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { onSelect(item) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(titleRes(item)), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(descRes(item)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@StringRes
internal fun cookiePolicyShortRes(policy: CookiePolicy): Int = when (policy) {
    CookiePolicy.ISOLATE_CROSS_SITE -> R.string.settings_cookies_isolate_short
    CookiePolicy.BLOCK_TRACKERS -> R.string.settings_cookies_trackers_short
    CookiePolicy.BLOCK_THIRD_PARTY -> R.string.settings_cookies_third_party_short
    CookiePolicy.BLOCK_ALL -> R.string.settings_cookies_none_short
    CookiePolicy.ALLOW_ALL -> R.string.settings_cookies_all_short
}

@StringRes
private fun cookiePolicyLabelRes(policy: CookiePolicy): Int = when (policy) {
    CookiePolicy.ISOLATE_CROSS_SITE -> R.string.settings_cookies_isolate
    CookiePolicy.BLOCK_TRACKERS -> R.string.settings_cookies_trackers
    CookiePolicy.BLOCK_THIRD_PARTY -> R.string.settings_cookies_third_party
    CookiePolicy.BLOCK_ALL -> R.string.settings_cookies_none
    CookiePolicy.ALLOW_ALL -> R.string.settings_cookies_all
}

@StringRes
private fun cookiePolicyDescRes(policy: CookiePolicy): Int = when (policy) {
    CookiePolicy.ISOLATE_CROSS_SITE -> R.string.settings_cookies_isolate_desc
    CookiePolicy.BLOCK_TRACKERS -> R.string.settings_cookies_trackers_desc
    CookiePolicy.BLOCK_THIRD_PARTY -> R.string.settings_cookies_third_party_desc
    CookiePolicy.BLOCK_ALL -> R.string.settings_cookies_none_desc
    CookiePolicy.ALLOW_ALL -> R.string.settings_cookies_all_desc
}

@StringRes
private fun cookieBannerTitleRes(mode: CookieBannerMode): Int = when (mode) {
    CookieBannerMode.REJECT -> R.string.settings_cookie_banners_reject
    CookieBannerMode.REJECT_OR_ACCEPT -> R.string.settings_cookie_banners_reject_or_accept
    CookieBannerMode.DISABLED -> R.string.settings_cookie_banners_off
}

@StringRes
private fun cookieBannerDescRes(mode: CookieBannerMode): Int = when (mode) {
    CookieBannerMode.REJECT -> R.string.settings_cookie_banners_reject_desc
    CookieBannerMode.REJECT_OR_ACCEPT -> R.string.settings_cookie_banners_reject_or_accept_desc
    CookieBannerMode.DISABLED -> R.string.settings_cookie_banners_off_desc
}

@StringRes
internal fun httpsOnlyShortRes(mode: HttpsOnlyMode): Int = when (mode) {
    HttpsOnlyMode.ALL -> R.string.settings_https_all_short
    HttpsOnlyMode.PRIVATE_ONLY -> R.string.settings_https_private_short
    HttpsOnlyMode.OFF -> R.string.settings_https_off
}

@StringRes
private fun httpsOnlyTitleRes(mode: HttpsOnlyMode): Int = when (mode) {
    HttpsOnlyMode.ALL -> R.string.settings_https_all
    HttpsOnlyMode.PRIVATE_ONLY -> R.string.settings_https_private
    HttpsOnlyMode.OFF -> R.string.settings_https_off
}

@StringRes
private fun httpsOnlyDescRes(mode: HttpsOnlyMode): Int = when (mode) {
    HttpsOnlyMode.ALL -> R.string.settings_https_all_desc
    HttpsOnlyMode.PRIVATE_ONLY -> R.string.settings_https_private_desc
    HttpsOnlyMode.OFF -> R.string.settings_https_off_desc
}

@StringRes
private fun dohModeTitleRes(mode: DohMode): Int = when (mode) {
    DohMode.OFF -> R.string.settings_doh_off
    DohMode.INCREASED -> R.string.settings_doh_increased
    DohMode.MAX -> R.string.settings_doh_max
}

@StringRes
private fun dohModeDescRes(mode: DohMode): Int = when (mode) {
    DohMode.OFF -> R.string.settings_doh_off_desc
    DohMode.INCREASED -> R.string.settings_doh_increased_desc
    DohMode.MAX -> R.string.settings_doh_max_desc
}

@StringRes
internal fun dohProviderTitleRes(provider: DohProvider): Int = when (provider) {
    DohProvider.CLOUDFLARE -> R.string.settings_doh_provider_cloudflare
    DohProvider.QUAD9 -> R.string.settings_doh_provider_quad9
    DohProvider.NEXTDNS -> R.string.settings_doh_provider_nextdns
    DohProvider.CUSTOM -> R.string.settings_doh_provider_custom
}

@StringRes
private fun dohProviderDescRes(provider: DohProvider): Int = when (provider) {
    DohProvider.CLOUDFLARE -> R.string.settings_doh_provider_cloudflare_desc
    DohProvider.QUAD9 -> R.string.settings_doh_provider_quad9_desc
    DohProvider.NEXTDNS -> R.string.settings_doh_provider_nextdns_desc
    DohProvider.CUSTOM -> R.string.settings_doh_provider_custom_desc
}

@Composable
internal fun dohSummaryText(
    mode: DohMode,
    provider: DohProvider,
    customUri: String,
): String {
    if (mode == DohMode.OFF) return stringResource(R.string.settings_doh_summary_off)
    val providerName = if (provider == DohProvider.CUSTOM) {
        DohUriValidator.normalize(customUri) ?: stringResource(R.string.settings_doh_summary_custom)
    } else {
        stringResource(dohProviderTitleRes(provider))
    }
    val modeLabel = stringResource(dohModeTitleRes(mode))
    return "$modeLabel · $providerName"
}
