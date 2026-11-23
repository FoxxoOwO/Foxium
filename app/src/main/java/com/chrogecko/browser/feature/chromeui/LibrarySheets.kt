package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import com.chrogecko.browser.core.database.entity.HistoryEntity
import com.chrogecko.browser.feature.favicons.faviconHost

@Composable
fun LinkListSheet(
    title: String,
    items: List<Pair<String, String>>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    searchHint: String = stringResource(R.string.search_hint),
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { (label, url) ->
                label.contains(needle, true) || url.contains(needle, true)
            }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        SheetHeader(title)
        SheetSearchField(searchHint, query) { query = it }
        if (filtered.isEmpty()) {
            EmptyRow(if (items.isEmpty()) stringResource(R.string.nothing_here_yet) else stringResource(R.string.no_matches))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            filtered.forEach { (label, url) ->
                LinkRow(label, url, icons, onOpen)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    folders: List<BookmarkFolderEntity>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    onCreateFolder: (String, Long?) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onMoveBookmark: (Long, Long?) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onImportBookmarks: () -> Unit,
    onExportBookmarks: () -> Unit,
) {
    var folderId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<BookmarkFolderEntity?>(null) }
    var movingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    val current = folders.firstOrNull { it.id == folderId }
    val searching = query.isNotBlank()
    val visibleFolders = remember(folders, folderId, query) {
        if (query.isBlank()) folders.filter { it.parentId == folderId }
        else folders.filter { it.name.contains(query, ignoreCase = true) }
    }
    val visibleBookmarks = remember(bookmarks, folderId, query) {
        if (query.isBlank()) bookmarks.filter { it.folderId == folderId }
        else bookmarks.filter {
            it.url.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)
        }
    }
    val countsByFolder = remember(bookmarks, folders) {
        val bookmarkCounts = bookmarks.groupingBy { it.folderId }.eachCount()
        val childCounts = folders.groupingBy { it.parentId }.eachCount()
        folders.associate { folder ->
            folder.id to ((bookmarkCounts[folder.id] ?: 0) + (childCounts[folder.id] ?: 0))
        }
    }
    val folderNameById = remember(folders) { folders.associate { it.id to it.name } }

    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        SheetHeader(if (searching) stringResource(R.string.bookmarks_title) else current?.name ?: stringResource(R.string.bookmarks_title)) {
            if (!searching && folderId != null) {
                TextButton(onClick = { folderId = current?.parentId }) { Text(stringResource(R.string.up_folder)) }
            }
            IconButton(onClick = { newFolder = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
            }
            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_bookmark_more))
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks_import_html)) },
                        onClick = {
                            moreOpen = false
                            onImportBookmarks()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks_export_html)) },
                        onClick = {
                            moreOpen = false
                            onExportBookmarks()
                        },
                    )
                }
            }
        }
        SheetSearchField(stringResource(R.string.search_bookmarks), query) { query = it }
        if (visibleFolders.isEmpty() && visibleBookmarks.isEmpty()) {
            EmptyRow(
                when {
                    searching -> stringResource(R.string.no_matches)
                    folderId == null -> stringResource(R.string.bookmarks_empty_root)
                    else -> stringResource(R.string.bookmarks_empty_folder)
                },
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(visibleFolders, key = { "f_${it.id}" }, contentType = { "folder" }) { folder ->
                val count = countsByFolder[folder.id] ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { query = ""; folderId = folder.id }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            pluralStringResource(R.plurals.bookmarks_items_count, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { deletingFolder = folder }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_folder_title))
                    }
                }
                HorizontalDivider()
            }
            items(visibleBookmarks, key = { "b_${it.id}" }, contentType = { "bookmark" }) { bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    folderName = bookmark.folderId?.let(folderNameById::get),
                    icons = icons,
                    onOpen = onOpen,
                    onMove = { movingBookmark = bookmark },
                    onRemove = { onRemoveBookmark(bookmark.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreateFolder(name, if (searching) null else folderId)
                        newFolder = false
                    },
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (folderId == folder.id) folderId = folder.parentId
                    onDeleteFolder(folder.id)
                    deletingFolder = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    movingBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { movingBookmark = null },
            title = { Text(stringResource(R.string.move_to_folder)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    (listOf<Pair<Long?, String>>(null to stringResource(R.string.bookmarks_title)) +
                        folders.map { it.id to it.name }).forEach { (id, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveBookmark(bookmark.id, id)
                                    movingBookmark = null
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                name,
                                fontWeight = if (id == bookmark.folderId) FontWeight.SemiBold
                                else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movingBookmark = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    folderName: String?,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(bookmark.url) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = faviconHost(bookmark.url)?.let(icons::get)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        (bookmark.title.ifBlank { bookmark.url }).take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.title.ifBlank { bookmark.url },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                folderName ?: bookmark.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_bookmark_options))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder_ellipsis)) }, onClick = {
                    expanded = false
                    onMove()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = {
                    expanded = false
                    onRemove()
                })
            }
        }
    }
}

@Composable
fun HistorySheet(
    entries: List<HistoryEntity>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(entries, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { it.title.contains(needle, true) || it.url.contains(needle, true) }
        }
    }
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)
    val sections = remember(filtered, todayLabel, yesterdayLabel) {
        groupHistoryByDay(filtered, todayLabel = todayLabel, yesterdayLabel = yesterdayLabel)
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        SheetHeader(stringResource(R.string.history_title))
        SheetSearchField(stringResource(R.string.search_history), query) { query = it }
        if (filtered.isEmpty()) {
            EmptyRow(if (entries.isEmpty()) stringResource(R.string.nothing_here_yet) else stringResource(R.string.no_matches))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            sections.forEach { (day, rows) ->
                item(key = "h_$day", contentType = "header") {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { it.id }, contentType = { "entry" }) { entry ->
                    LinkRow(entry.title, entry.url, icons, onOpen)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    label: String,
    url: String,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = faviconHost(url)?.let(icons::get)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        (label.ifBlank { url }).take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label.ifBlank { url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RecentTabsSheet(
    tabs: List<com.chrogecko.browser.core.database.entity.TabEntity>,
    onOpen: (String) -> Unit,
) {
    val validTabs = remember(tabs) {
        tabs.filter { !it.url.isNullOrBlank() && it.url != "about:blank" }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        SheetHeader(stringResource(R.string.recent_tabs_title))
        if (validTabs.isEmpty()) {
            EmptyRow(stringResource(R.string.recent_tabs_empty))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(validTabs, key = { it.id }, contentType = { "recent_tab" }) { tab ->
                val url = tab.url.orEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(url) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tab.title.ifBlank { url },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
