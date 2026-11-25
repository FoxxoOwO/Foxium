package com.chrogecko.browser.feature.chromeui

import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.DownloadEntity
import com.chrogecko.browser.core.engine.ContextMenu
import com.chrogecko.browser.feature.cast.CastMedia
import com.chrogecko.browser.feature.cast.CastPlaybackState
import com.chrogecko.browser.feature.downloads.DownloadStorage
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun ContextMenuSheet(
    menu: ContextMenu,
    onOpen: (String) -> Unit,
    onOpenPrivate: (String) -> Unit,
    onSearchWeb: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val link = menu.linkUrl
    val image = menu.imageUrl
    // Prefer the long-pressed text (Chrome's "Search for ..."), then fall back to the target.
    val searchText = listOf(menu.text, link, image).firstOrNull { !it.isNullOrBlank() }?.trim()
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = link ?: image ?: menu.text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (searchText != null) {
            ContextFlatRow(stringResource(R.string.context_search_web)) {
                onSearchWeb(searchText)
                onDismiss()
            }
        }
        if (link != null) {
            ContextFlatRow(stringResource(R.string.context_open_new_tab)) { onOpen(link); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_open_incognito)) { onOpenPrivate(link); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_copy_link)) { onCopy(link); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_share_link)) { onShare(link); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_download_link)) { onDownload(link); onDismiss() }
        }
        if (image != null) {
            ContextFlatRow(stringResource(R.string.context_open_image)) { onOpen(image); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_download_image)) { onDownload(image); onDismiss() }
            ContextFlatRow(stringResource(R.string.context_copy_image_address)) { onCopy(image); onDismiss() }
        }
    }
}

@Composable
private fun ContextFlatRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SheetSearchField(hint: String, query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint) },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun DownloadsSheet(
    downloads: List<DownloadEntity>,
    onDelete: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val noAppMessage = stringResource(R.string.no_app_can_open)
    var query by remember { mutableStateOf("") }
    val shown = remember(downloads, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            downloads
        } else {
            downloads.filter { it.fileName.contains(needle, true) }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        SheetHeader(stringResource(R.string.downloads_title)) {
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear_all)) }
            }
        }
        SheetSearchField(stringResource(R.string.search_downloads), query) { query = it }
        if (shown.isEmpty()) {
            EmptyRow(if (downloads.isEmpty()) stringResource(R.string.downloads_empty) else stringResource(R.string.no_matches))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(shown, key = { it.id }, contentType = { "download" }) { download ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = download.status == DownloadEntity.STATUS_COMPLETED) {
                                if (!openDownload(context, download)) {
                                    Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                    ) {
                        Text(download.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (download.status) {
                                DownloadEntity.STATUS_RUNNING -> {
                                    val total = download.sizeBytes
                                    val written = download.downloadedBytes
                                    if (total > 0) {
                                        stringResource(R.string.download_progress_bytes, formatBytes(written), formatBytes(total))
                                    } else {
                                        stringResource(R.string.downloading_progress, formatBytes(written))
                                    }
                                }
                                DownloadEntity.STATUS_COMPLETED -> formatBytes(download.sizeBytes)
                                DownloadEntity.STATUS_CANCELLED -> stringResource(R.string.download_cancelled)
                                else -> download.error ?: stringResource(R.string.download_failed)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (download.status == DownloadEntity.STATUS_RUNNING &&
                            download.sizeBytes > 0
                        ) {
                            LinearProgressIndicator(
                                progress = {
                                    (download.downloadedBytes.toFloat() / download.sizeBytes)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                    if (download.status == DownloadEntity.STATUS_RUNNING) {
                        TextButton(onClick = { onCancel(download.id) }) { Text(stringResource(R.string.cancel)) }
                    } else {
                        DownloadRowMenu(
                            download = download,
                            context = context,
                            onDelete = { onDelete(download.id) },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DownloadRowMenu(
    download: DownloadEntity,
    context: android.content.Context,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val noAppMessage = stringResource(R.string.no_app_can_open)
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_download_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (download.status == DownloadEntity.STATUS_COMPLETED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_with)) },
                    onClick = {
                        expanded = false
                        if (!openDownload(context, download, chooser = true)) {
                            Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    onClick = {
                        expanded = false
                        shareDownload(context, download)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private fun shareDownload(context: android.content.Context, download: DownloadEntity) {
    val path = download.localPath ?: return
    if (!path.startsWith("content://") && !java.io.File(path).exists()) return
    runCatching {
        val uri = DownloadStorage.uriFor(context, path)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = download.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_download)))
    }
}

private fun openDownload(
    context: android.content.Context,
    download: DownloadEntity,
    chooser: Boolean = false,
): Boolean {
    val path = download.localPath ?: return false
    if (!path.startsWith("content://") && !java.io.File(path).exists()) return false
    return runCatching {
        val uri = DownloadStorage.uriFor(context, path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, download.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(if (chooser) Intent.createChooser(intent, context.getString(R.string.open_with_title)) else intent)
    }.isSuccess
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun CastSheet(
    media: List<CastMedia>,
    playback: CastPlaybackState,
    mirrorUrl: String?,
    onMirror: () -> Unit,
    onStopMirror: () -> Unit,
    onCast: (CastMedia) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).heightIn(max = 520.dp)) {
        SheetHeader(stringResource(R.string.cast_title))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(
                factory = { ctx ->
                    // MediaRouteButton reads android:colorBackground from its theme; the bottom-sheet
                    // context can resolve that to transparent, which makes mediarouter throw.
                    val themed = ContextThemeWrapper(ctx, R.style.Theme_ChroGecko)
                    MediaRouteButton(themed).also { button ->
                        runCatching { CastButtonFactory.setUpMediaRouteButton(themed, button) }
                    }
                },
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (playback.connected) {
                        playback.deviceName ?: stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.no_device_connected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (playback.mediaTitle != null) {
                    Text(
                        playback.mediaTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (playback.connected) {
                TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mirror_this_screen))
                Text(
                    mirrorUrl ?: stringResource(R.string.mirror_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mirrorUrl == null) {
                Button(onClick = onMirror) { Text(stringResource(R.string.mirror_action)) }
            } else {
                TextButton(onClick = onStopMirror) { Text(stringResource(R.string.stop)) }
            }
        }
        if (playback.mediaUrl != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = if (playback.playing) onPause else onResume) {
                    Text(if (playback.playing) stringResource(R.string.pause) else stringResource(R.string.play))
                }
                TextButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
                Text(
                    "${playback.positionMs / 1000}s / ${playback.durationMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider()
        if (media.isEmpty()) {
            EmptyRow(stringResource(R.string.cast_empty_media))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            media.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.kind.uppercase()} · ${item.mime.ifBlank { "media" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { onCast(item) }) { Text(stringResource(R.string.cast_title)) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun SheetHeader(title: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
internal fun EmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
fun GroupEditDialog(
    group: TabGroup,
    onRename: (String) -> Unit,
    onRecolor: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(group.id) { mutableStateOf(group.title) }
    var color by remember(group.id) { mutableLongStateOf(group.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_group_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUP_PALETTE.forEach { paletteColor ->
                        Surface(
                            shape = CircleShape,
                            color = Color(paletteColor),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { color = paletteColor },
                            border = if (color == paletteColor) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            },
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onRename(title.trim())
                    onRecolor(color)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.delete_group)) }
        },
    )
}

val GROUP_PALETTE = listOf(
    0xFF1A73E8,
    0xFF188038,
    0xFFD93025,
    0xFFF9AB00,
    0xFF9334E6,
    0xFFE8710A,
)
