package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_HACKER_NEWS
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_RSS
import com.chrogecko.browser.core.preferences.DISCOVER_SOURCES
import com.chrogecko.browser.core.preferences.RssFeedSource
import com.chrogecko.browser.feature.discover.DiscoverItem
import com.chrogecko.browser.feature.discover.RssFeedParser
import com.chrogecko.browser.feature.favicons.faviconHost
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R

/** A most-visited tile; [host] keys the favicon cache. */
data class NewTabShortcut(val url: String, val label: String, val host: String)

/**
 * Bookmarks first, then history, one tile per host, capped at [limit].
 * Non-http(s) entries (about:, file:, telegram:) have no host and are skipped.
 */
fun buildNewTabShortcuts(
    bookmarks: List<Pair<String, String>>,
    history: List<Pair<String, String>>,
    limit: Int = 8,
): List<NewTabShortcut> =
    (bookmarks + history)
        .mapNotNull { (url, title) ->
            val host = faviconHost(url) ?: return@mapNotNull null
            NewTabShortcut(url.trim(), title.ifBlank { host }, host) to host
        }
        .distinctBy { it.second }
        .take(limit)
        .map { it.first }

private val TILE_COLORS = listOf(
    Color(0xFF1A73E8),
    Color(0xFF188038),
    Color(0xFFD93025),
    Color(0xFF9334E6),
    Color(0xFFE37400),
    Color(0xFF12B5CB),
)

@Composable
fun ChromeNewTabPage(
    shortcuts: List<NewTabShortcut>,
    icons: Map<String, ImageBitmap>,
    isPrivate: Boolean,
    discover: List<DiscoverItem>,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onRestoreShortcuts: () -> Unit,
    pinnedUrls: Set<String>,
    onAddShortcut: (String) -> Unit,
    onRenameShortcut: (String, String) -> Unit,
    onRefreshDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    discoverEnabled: Boolean = true,
    discoverMode: String = DISCOVER_MODE_HACKER_NEWS,
    discoverSources: Set<String> = setOf("top"),
    discoverRssFeeds: List<RssFeedSource> = emptyList(),
    discoverLimit: Int = 12,
    hiddenDiscoverCount: Int = 0,
    searchEngineName: String = "Google",
    onSetDiscoverEnabled: (Boolean) -> Unit = {},
    onSetDiscoverMode: (String) -> Unit = {},
    onToggleDiscoverSource: (String, Boolean) -> Unit = { _, _ -> },
    onAddDiscoverRssFeed: (String, String) -> Unit = { _, _ -> },
    onRemoveDiscoverRssFeed: (String) -> Unit = {},
    onSetDiscoverLimit: (Int) -> Unit = {},
    onHideDiscoverArticle: (String) -> Unit = {},
    onResetHiddenDiscoverArticles: () -> Unit = {},
    onVoice: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var addingShortcut by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<NewTabShortcut?>(null) }
    var customizingDiscover by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isPrivate) {
            IncognitoNotice(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 28.dp),
            )
        } else {
            Wordmark(Modifier.padding(top = 36.dp, bottom = 22.dp))
            SearchField(
                searchEngineName = searchEngineName,
                onSearch = onSearch,
                onVoice = onVoice,
            )
            ShortcutTiles(
                shortcuts = shortcuts.take(8),
                icons = icons,
                onOpen = onOpen,
                editing = editing,
                onEditChange = { editing = it },
                onRemove = onRemoveShortcut,
                onRestore = onRestoreShortcuts,
                onAdd = { addingShortcut = true },
                pinnedUrls = pinnedUrls,
                onRename = { renaming = it },
                modifier = Modifier.padding(top = 20.dp),
            )
            if (discoverEnabled) {
                when {
                    discover.isNotEmpty() -> {
                        DiscoverFeed(
                            title = stringResource(
                                if (discoverMode == DISCOVER_MODE_RSS) {
                                    R.string.discover_mode_rss
                                } else {
                                    R.string.ntp_discover
                                },
                            ),
                            items = discover,
                            icons = icons,
                            onOpen = onOpen,
                            onRefresh = onRefreshDiscover,
                            onCustomize = { customizingDiscover = true },
                            onHide = onHideDiscoverArticle,
                            modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
                        )
                    }
                    discoverMode == DISCOVER_MODE_RSS -> {
                        DiscoverEmptyRssCard(
                            hasFeeds = discoverRssFeeds.isNotEmpty(),
                            onRefresh = onRefreshDiscover,
                            onCustomize = { customizingDiscover = true },
                            modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.discover_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { customizingDiscover = true }) {
                            Text(stringResource(R.string.discover_customize))
                        }
                    }
                }
            }
        }
    }
    if (addingShortcut) {
        AddShortcutDialog(
            onAdd = { url ->
                onAddShortcut(url)
                addingShortcut = false
            },
            onDismiss = { addingShortcut = false },
        )
    }
    renaming?.let { tile ->
        RenameShortcutDialog(
            current = tile.label,
            onRename = { label ->
                onRenameShortcut(tile.url, label)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    if (customizingDiscover) {
        DiscoverCustomizeSheet(
            enabled = discoverEnabled,
            mode = discoverMode,
            sources = discoverSources,
            rssFeeds = discoverRssFeeds,
            limit = discoverLimit,
            hiddenCount = hiddenDiscoverCount,
            onDismiss = { customizingDiscover = false },
            onEnabledChange = onSetDiscoverEnabled,
            onModeChange = onSetDiscoverMode,
            onSourceToggle = onToggleDiscoverSource,
            onAddRssFeed = onAddDiscoverRssFeed,
            onRemoveRssFeed = onRemoveDiscoverRssFeed,
            onLimitChange = onSetDiscoverLimit,
            onResetHidden = onResetHiddenDiscoverArticles,
        )
    }
}

@Composable
private fun DiscoverEmptyRssCard(
    hasFeeds: Boolean,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiscoverHeader(
            title = stringResource(R.string.discover_mode_rss),
            onRefresh = onRefresh,
            onCustomize = onCustomize,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(
                        if (hasFeeds) R.string.discover_rss_no_articles else R.string.discover_rss_empty_feeds,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCustomize) {
                    Text(stringResource(R.string.discover_customize))
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeader(
    title: String,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = onRefresh,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_suggestions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        FilledTonalIconButton(
            onClick = onCustomize,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.discover_customize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DiscoverFeed(
    title: String,
    items: List<DiscoverItem>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
    onHide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiscoverHeader(
            title = title,
            onRefresh = onRefresh,
            onCustomize = onCustomize,
        )
        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpen(item.url) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = icons[item.host]
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(TILE_COLORS[item.host.hashCode().mod(TILE_COLORS.size)]),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.host.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.host,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onHide(item.url) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_hide_discover_article),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameShortcutDialog(
    current: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ntp_rename_shortcut)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(label.trim()) }, enabled = label.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AddShortcutDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    // Only host-like input becomes a visible tile; a search phrase would resolve to the search
    // engine's domain and would show the wrong site.
    val looksLikeHost = url.contains('.') && !url.contains(' ')
    val normalized = if (looksLikeHost) normalizeOmniboxInput(url) else ""
    val canAdd = normalized.startsWith("http://") || normalized.startsWith("https://")
    val submit = {
        if (canAdd) {
            onAdd(normalized)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_shortcut)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text(stringResource(R.string.ntp_site_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = canAdd) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ChroGeckoLogoEmblem(modifier = Modifier.size(44.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun IncognitoNotice(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                IncognitoBadgeIcon(
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = stringResource(R.string.ntp_incognito_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.ntp_incognito_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IncognitoInfoCard(
                title = stringResource(R.string.ntp_incognito_wont_save_title),
                bullets = listOf(
                    stringResource(R.string.ntp_incognito_wont_save_1),
                    stringResource(R.string.ntp_incognito_wont_save_2),
                    stringResource(R.string.ntp_incognito_wont_save_3),
                ),
                modifier = Modifier.weight(1f),
            )
            IncognitoInfoCard(
                title = stringResource(R.string.ntp_incognito_visible_title),
                bullets = listOf(
                    stringResource(R.string.ntp_incognito_visible_1),
                    stringResource(R.string.ntp_incognito_visible_2),
                    stringResource(R.string.ntp_incognito_visible_3),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IncognitoInfoCard(
    title: String,
    bullets: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    searchEngineName: String,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSearch,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = 8.dp),
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.ntp_search_placeholder, searchEngineName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            IconButton(onClick = onVoice) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.cd_voice_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ShortcutTiles(
    shortcuts: List<NewTabShortcut>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    editing: Boolean,
    onEditChange: (Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onRestore: () -> Unit,
    onAdd: () -> Unit,
    pinnedUrls: Set<String>,
    onRename: (NewTabShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.ntp_most_visited),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
                Spacer(Modifier.width(4.dp))
            }
            FilledTonalButton(
                onClick = { onEditChange(!editing) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.height(32.dp),
            ) {
                Text(if (editing) stringResource(R.string.done) else stringResource(R.string.edit), style = MaterialTheme.typography.labelMedium)
            }
        }
        // Include the add tile in the chunking so a full 4- or 8-shortcut grid cannot put five
        // weighted children into one row and shrink every tile.
        val tiles: List<NewTabShortcut?> = if (editing) shortcuts + null else shortcuts
        val rows = if (tiles.isEmpty()) listOf(listOf<NewTabShortcut?>(null)) else tiles.chunked(4)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { shortcut ->
                    if (shortcut == null) {
                        AddShortcutTile(onClick = onAdd, modifier = Modifier.weight(1f))
                    } else {
                        ShortcutTile(
                            shortcut = shortcut,
                            icon = icons[shortcut.host],
                            color = TILE_COLORS[shortcut.label.hashCode().mod(TILE_COLORS.size)],
                            editing = editing,
                            onClick = {
                                when {
                                    !editing -> onOpen(shortcut.url)
                                    shortcut.url in pinnedUrls -> onRename(shortcut)
                                }
                            },
                            onRemove = { onRemove(shortcut.host) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Keep a full row of four so tiles never stretch to the edges.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AddShortcutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_shortcut),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.add),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ShortcutTile(
    shortcut: NewTabShortcut,
    icon: ImageBitmap?,
    color: Color,
    editing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (icon != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp).clip(CircleShape),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shortcut.label.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (editing) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clickable(onClick = onRemove),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_item, shortcut.host),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Quick controls for the Discover channel, opened from the feed header or the "off" row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverCustomizeSheet(
    enabled: Boolean,
    mode: String,
    sources: Set<String>,
    rssFeeds: List<RssFeedSource>,
    limit: Int,
    hiddenCount: Int,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (String) -> Unit,
    onSourceToggle: (String, Boolean) -> Unit,
    onAddRssFeed: (String, String) -> Unit,
    onRemoveRssFeed: (String) -> Unit,
    onLimitChange: (Int) -> Unit,
    onResetHidden: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.discover_customize),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.discover_show),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.discover_feed_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode != DISCOVER_MODE_RSS,
                    onClick = { onModeChange(DISCOVER_MODE_HACKER_NEWS) },
                    label = { Text(stringResource(R.string.discover_mode_hn)) },
                )
                FilterChip(
                    selected = mode == DISCOVER_MODE_RSS,
                    onClick = { onModeChange(DISCOVER_MODE_RSS) },
                    label = { Text(stringResource(R.string.discover_mode_rss)) },
                )
            }
            Spacer(Modifier.height(18.dp))
            if (mode == DISCOVER_MODE_RSS) {
                Text(stringResource(R.string.discover_rss_sources), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                DiscoverRssSourcesEditor(
                    rssFeeds = rssFeeds,
                    onAddRssFeed = onAddRssFeed,
                    onRemoveRssFeed = onRemoveRssFeed,
                )
            } else {
                Text(stringResource(R.string.discover_source), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DISCOVER_SOURCES.forEach { key ->
                        val label = when (key) {
                            "new" -> stringResource(R.string.discover_source_new)
                            "best" -> stringResource(R.string.discover_source_best)
                            "show" -> stringResource(R.string.discover_source_show)
                            else -> stringResource(R.string.discover_source_top)
                        }
                        FilterChip(
                            selected = key in sources,
                            onClick = { onSourceToggle(key, key !in sources) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.discover_count), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 20).forEach { count ->
                    FilledTonalButton(
                        onClick = { onLimitChange(count) },
                        enabled = limit != count,
                    ) { Text(count.toString()) }
                }
            }
            if (hiddenCount > 0) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onResetHidden(); onDismiss() }) {
                    Text(stringResource(R.string.discover_reset_hidden, hiddenCount))
                }
            }
        }
    }
}

@Composable
internal fun DiscoverRssSourcesEditor(
    rssFeeds: List<RssFeedSource>,
    onAddRssFeed: (String, String) -> Unit,
    onRemoveRssFeed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                if (urlError) urlError = false
            },
            label = { Text(stringResource(R.string.discover_rss_url_label)) },
            placeholder = { Text("https://example.com/feed.xml") },
            isError = urlError,
            supportingText = if (urlError) {
                { Text(stringResource(R.string.discover_rss_invalid_url)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.discover_rss_name_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = {
                    val normalized = RssFeedParser.normalizeFeedUrl(urlInput)
                    if (normalized == null) {
                        urlError = true
                    } else {
                        onAddRssFeed(normalized, nameInput.trim())
                        urlInput = ""
                        nameInput = ""
                        urlError = false
                    }
                },
                enabled = urlInput.isNotBlank(),
            ) { Text(stringResource(R.string.discover_rss_add)) }
        }
        Spacer(Modifier.height(12.dp))
        if (rssFeeds.isEmpty()) {
            Text(
                stringResource(R.string.discover_rss_empty_feeds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rssFeeds.forEach { feed ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    feed.title.ifBlank { faviconHost(feed.url) ?: feed.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    feed.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onRemoveRssFeed(feed.url) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_remove_rss_source),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
