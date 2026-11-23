package com.chrogecko.browser.feature.chromeui

import android.os.Build
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrogecko.browser.BuildConfig
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.SiteRuleEntity
import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import com.chrogecko.browser.feature.updates.UpdateState

private data class ClearDataOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

/** Settings subpage to selectively clear history, cookies, cache, and download records. */
@Composable
internal fun ClearBrowsingDataSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }

    val options = listOf(
        ClearDataOption(
            title = stringResource(R.string.settings_clear_history_title),
            description = stringResource(R.string.settings_clear_history_desc),
            icon = Icons.Outlined.History,
            checked = history,
            onToggle = { history = it },
        ),
        ClearDataOption(
            title = stringResource(R.string.settings_clear_cookies_title),
            description = stringResource(R.string.settings_clear_cookies_desc),
            icon = Icons.Outlined.Cookie,
            checked = cookies,
            onToggle = { cookies = it },
        ),
        ClearDataOption(
            title = stringResource(R.string.settings_clear_cache_title),
            description = stringResource(R.string.settings_clear_cache_desc),
            icon = Icons.Outlined.Cached,
            checked = cache,
            onToggle = { cache = it },
        ),
        ClearDataOption(
            title = stringResource(R.string.settings_clear_downloads_title),
            description = stringResource(R.string.settings_clear_downloads_desc),
            icon = Icons.Outlined.Download,
            checked = downloads,
            onToggle = { downloads = it },
        ),
    )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_clear_data_title),
        subtitle = stringResource(R.string.settings_clear_data_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(options, key = { _, opt -> opt.title }) { index, option ->
                val shape = settingsGroupItemShape(index, options.size)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .clickable { option.onToggle(!option.checked) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            option.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = option.checked, onCheckedChange = option.onToggle)
                    }
                }
            }
            item(key = "action") {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        viewModel.clearBrowsingData(history, cookies, cache, downloads)
                        onBack()
                    },
                    enabled = history || cookies || cache || downloads,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_data_button))
                }
            }
        }
    }
}

/** Settings subpage managing per-domain JavaScript, desktop mode, and reader mode overrides. */
@Composable
internal fun SiteSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val siteRules by viewModel.siteRuleList.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_site_settings_title),
        subtitle = stringResource(R.string.settings_site_settings_subtitle),
        hasFab = true,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addOpen = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_add_site)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (siteRules.isEmpty()) {
                item(key = "empty") {
                    SettingsEmptyState(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_site_empty_title),
                        body = stringResource(R.string.settings_site_empty_body),
                    )
                }
            } else {
                itemsIndexed(siteRules, key = { _, rule -> rule.host }) { _, rule ->
                    SiteRuleCard(
                        rule = rule,
                        onRuleChange = { updated ->
                            viewModel.setSiteRule(
                                updated.host,
                                updated.blockJavascript,
                                updated.desktopSite,
                                updated.readerMode,
                            )
                        },
                        onDelete = { viewModel.deleteSiteRule(rule.host) },
                    )
                }
            }
        }
    }

    if (addOpen) {
        val submitSite = {
            if (host.isNotBlank()) {
                viewModel.addSiteRule(host)
                host = ""
                addOpen = false
            }
        }
        AlertDialog(
            onDismissRequest = {
                host = ""
                addOpen = false
            },
            title = { Text(stringResource(R.string.settings_add_site_rule_title)) },
            text = {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.field_domain)) },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitSite() }),
                )
            },
            confirmButton = {
                TextButton(enabled = host.isNotBlank(), onClick = submitSite) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        host = ""
                        addOpen = false
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SiteRuleCard(
    rule: SiteRuleEntity,
    onRuleChange: (SiteRuleEntity) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    rule.host,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_remove_site_rule))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SiteRuleToggleRow(
                icon = Icons.Outlined.Code,
                label = stringResource(R.string.settings_site_javascript),
                checked = !rule.blockJavascript,
                onCheckedChange = { onRuleChange(rule.copy(blockJavascript = !it)) },
            )
            SiteRuleToggleRow(
                icon = Icons.Outlined.DesktopWindows,
                label = stringResource(R.string.menu_desktop_site),
                checked = rule.desktopSite,
                onCheckedChange = { onRuleChange(rule.copy(desktopSite = it)) },
            )
            SiteRuleToggleRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                label = stringResource(R.string.settings_site_reader_mode),
                checked = rule.readerMode,
                onCheckedChange = { onRuleChange(rule.copy(readerMode = it)) },
            )
        }
    }
}

@Composable
private fun SiteRuleToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Settings subpage managing text expander activation and user-defined trigger snippets. */
@Composable
internal fun TextExpanderSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val snippets by viewModel.textSnippets.collectAsStateWithLifecycle()
    val enabled by viewModel.textExpanderEnabled.collectAsStateWithLifecycle()
    var editorId by remember { mutableLongStateOf(0L) }
    var trigger by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }

    val openEditor: (TextSnippetEntity?) -> Unit = { snippet ->
        editorId = snippet?.id ?: 0L
        trigger = snippet?.trigger.orEmpty()
        expansion = snippet?.expansion.orEmpty()
        editorOpen = true
    }

    val masterContainer by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "expanderMasterColor",
    )
    val masterContent = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_expander_title),
        subtitle = stringResource(R.string.settings_expander_subtitle),
        hasFab = true,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openEditor(null) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_add_snippet)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "master") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = masterContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { viewModel.setTextExpanderEnabled(!enabled) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_expander_master_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = masterContent,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.settings_expander_master_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = masterContent.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = enabled, onCheckedChange = viewModel::setTextExpanderEnabled)
                    }
                }
            }
            if (snippets.isEmpty()) {
                item(key = "empty") {
                    SettingsEmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.settings_expander_empty_title),
                        body = stringResource(R.string.settings_expander_empty_body),
                    )
                }
            } else {
                itemsIndexed(snippets, key = { _, snippet -> snippet.id }) { index, snippet ->
                    val shape = settingsGroupItemShape(index, snippets.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .clickable { openEditor(snippet) },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    snippet.trigger,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    snippet.expansion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = snippet.enabled,
                                onCheckedChange = { viewModel.setTextSnippetEnabled(snippet.id, it) },
                            )
                            IconButton(onClick = { viewModel.deleteTextSnippet(snippet.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_remove_snippet))
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editorId == 0L) stringResource(R.string.settings_add_snippet) else stringResource(R.string.settings_edit_snippet)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp)) {
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it.filterNot { char -> char.isWhitespace() } },
                        label = { Text(stringResource(R.string.field_trigger)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                    )
                    OutlinedTextField(
                        value = expansion,
                        onValueChange = { expansion = it },
                        label = { Text(stringResource(R.string.field_expansion)) },
                        modifier = Modifier.padding(top = 8.dp),
                        minLines = 2,
                    )
                    Text(
                        stringResource(R.string.settings_expander_placeholders_note),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = trigger.isNotBlank() && expansion.isNotBlank(),
                    onClick = {
                        viewModel.saveTextSnippet(editorId, trigger, expansion)
                        editorOpen = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Settings subpage showing app version, engine, release channel, OS details, and auto-updates. */
@Composable
internal fun AboutSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
    val autoUpdatePrereleases by viewModel.autoUpdatePrereleases.collectAsStateWithLifecycle()

    val rows = listOf(
        stringResource(R.string.settings_about_app_version) to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        stringResource(R.string.settings_about_engine) to "Mozilla GeckoView",
        stringResource(R.string.settings_about_channel) to if (BuildConfig.GECKO_NIGHTLY) {
            stringResource(R.string.settings_about_channel_nightly)
        } else {
            stringResource(R.string.settings_about_channel_stable)
        },
        stringResource(R.string.settings_about_os) to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_about_title),
        subtitle = stringResource(R.string.settings_about_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "hero") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_about_hero_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            itemsIndexed(rows, key = { _, row -> row.first }) { index, (label, value) ->
                val shape = settingsGroupItemShape(index, rows.size)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "updates_header") {
                SettingsSectionHeader(stringResource(R.string.settings_updates_header))
            }
            item(key = "updates_auto") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_updates_auto_title),
                    description = stringResource(R.string.settings_updates_auto_desc),
                    checked = autoUpdateEnabled,
                    shape = settingsGroupItemShape(0, 3),
                    onCheckedChange = viewModel::setAutoUpdateEnabled,
                )
            }
            item(key = "updates_prereleases") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_updates_prereleases_title),
                    description = stringResource(R.string.settings_updates_prereleases_desc),
                    checked = autoUpdatePrereleases,
                    shape = settingsGroupItemShape(1, 3),
                    onCheckedChange = viewModel::setAutoUpdatePrereleases,
                )
            }
            item(key = "updates_status") {
                Surface(
                    shape = settingsGroupItemShape(2, 3),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val statusText = when (val state = updateState) {
                            UpdateState.Idle -> stringResource(R.string.settings_updates_status_idle)
                            UpdateState.Checking -> stringResource(R.string.settings_updates_status_checking)
                            UpdateState.UpToDate -> stringResource(R.string.settings_updates_status_up_to_date)
                            is UpdateState.Available -> stringResource(
                                R.string.settings_updates_status_available,
                                state.update.versionName,
                                Formatter.formatShortFileSize(context, state.update.apkSizeBytes),
                            )
                            is UpdateState.Downloading -> {
                                val pct = if (state.totalBytes > 0L) {
                                    ((state.downloadedBytes * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                stringResource(R.string.settings_updates_status_downloading, pct)
                            }
                            is UpdateState.ReadyToInstall -> if (state.needsInstallPermission) {
                                stringResource(R.string.settings_updates_permission_hint)
                            } else {
                                stringResource(
                                    R.string.settings_updates_status_ready,
                                    state.update.versionName,
                                )
                            }
                            is UpdateState.Error -> stringResource(
                                R.string.settings_updates_status_error,
                                state.message,
                            )
                        }
                        Text(
                            stringResource(R.string.settings_updates_status_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val availableNotes = (updateState as? UpdateState.Available)?.update?.releaseNotes.orEmpty()
                        if (availableNotes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                availableNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val downloading = updateState as? UpdateState.Downloading
                        if (downloading != null) {
                            Spacer(Modifier.height(10.dp))
                            if (downloading.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = {
                                        (downloading.downloadedBytes.toFloat() / downloading.totalBytes.toFloat())
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (val state = updateState) {
                                UpdateState.Idle,
                                UpdateState.UpToDate,
                                is UpdateState.Error,
                                -> {
                                    Button(onClick = viewModel::checkForUpdatesNow) {
                                        Text(stringResource(R.string.settings_updates_check_now))
                                    }
                                }
                                UpdateState.Checking -> {
                                    Button(enabled = false, onClick = {}) {
                                        Text(stringResource(R.string.settings_updates_status_checking))
                                    }
                                }
                                is UpdateState.Available -> {
                                    Button(onClick = { viewModel.downloadAvailableUpdate(state.update) }) {
                                        Text(stringResource(R.string.update_download_install))
                                    }
                                    TextButton(onClick = viewModel::checkForUpdatesNow) {
                                        Text(stringResource(R.string.settings_updates_check_now))
                                    }
                                }
                                is UpdateState.Downloading -> {
                                    Button(enabled = false, onClick = {}) {
                                        Text(stringResource(R.string.update_downloading))
                                    }
                                }
                                is UpdateState.ReadyToInstall -> {
                                    Button(onClick = viewModel::installReadyUpdate) {
                                        Text(
                                            stringResource(
                                                if (state.needsInstallPermission) {
                                                    R.string.update_allow_and_install
                                                } else {
                                                    R.string.update_install
                                                },
                                            ),
                                        )
                                    }
                                    TextButton(onClick = viewModel::checkForUpdatesNow) {
                                        Text(stringResource(R.string.settings_updates_check_now))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
