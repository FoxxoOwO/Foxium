package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.UserscriptEntity
import com.chrogecko.browser.feature.addons.InstalledAddon
import com.chrogecko.browser.feature.addons.PendingAddonInstall

@Composable
fun AddonsSheet(
    addons: List<InstalledAddon>,
    onPickFile: () -> Unit,
    onInstallUrl: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenPopup: (String) -> Unit,
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    val visibleAddons = remember(addons) { addons.filterNot { it.builtIn } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SheetHeader(stringResource(R.string.extensions_title)) {
            TextButton(onClick = onPickFile) { Text(stringResource(R.string.from_file)) }
            TextButton(onClick = { showUrlDialog = true }) { Text(stringResource(R.string.from_url)) }
        }
        if (visibleAddons.isEmpty()) {
            EmptyRow(stringResource(R.string.addons_empty))
        }
        visibleAddons.forEach { addon ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(addon.name, fontWeight = FontWeight.Medium)
                    Text(
                        addon.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (addon.description.isNotBlank()) {
                        Text(
                            addon.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (addon.enabled && (addon.hasAction || addon.actionTitle != null)) {
                    TextButton(onClick = { onOpenPopup(addon.id) }) { Text(stringResource(R.string.open)) }
                }
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = { onToggle(addon.id, it) },
                )
                IconButton(onClick = { onUninstall(addon.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove_extension))
                }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.addons_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    if (showUrlDialog) {
        UrlInputDialog(
            title = stringResource(R.string.addons_install_title),
            hint = "https://addons.mozilla.org/…/extension.xpi",
            onDismiss = { showUrlDialog = false },
            onConfirm = {
                onInstallUrl(it)
                showUrlDialog = false
            },
        )
    }
}

@Composable
fun UserscriptsSheet(
    scripts: List<UserscriptEntity>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCheckUpdate: (String) -> Unit,
    onPickFile: () -> Unit,
    onInstallUrl: (String) -> Unit,
    onInstallSource: (String) -> Unit,
) {
    var urlDialog by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        SheetHeader(stringResource(R.string.userscripts_title)) {
            TextButton(onClick = onPickFile) { Text(stringResource(R.string.from_file)) }
            TextButton(onClick = { urlDialog = true }) { Text(stringResource(R.string.from_url)) }
            TextButton(onClick = { pasteDialog = true }) { Text(stringResource(R.string.paste)) }
        }
        if (scripts.isEmpty()) {
            EmptyRow(stringResource(R.string.userscripts_empty))
        }
        scripts.forEach { script ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable { onEdit(script.id) }) {
                    Text(script.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.userscripts_version_run_at, script.version, script.runAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCheckUpdate(script.id) }) { Text(stringResource(R.string.update)) }
                IconButton(onClick = { onDelete(script.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_delete_userscript))
                }
                Switch(checked = script.enabled, onCheckedChange = { onToggle(script.id, it) })
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(16.dp))
    }
    if (urlDialog) {
        UrlInputDialog(
            title = stringResource(R.string.userscripts_install_title),
            hint = "https://example.com/script.user.js",
            onDismiss = { urlDialog = false },
            onConfirm = {
                onInstallUrl(it)
                urlDialog = false
            },
        )
    }
    if (pasteDialog) {
        SourceInputDialog(
            title = stringResource(R.string.userscripts_paste_title),
            onDismiss = { pasteDialog = false },
            onConfirm = {
                onInstallSource(it)
                pasteDialog = false
            },
        )
    }
}

@Composable
fun UserscriptEditorDialog(
    script: UserscriptEntity,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember(script.id) { mutableStateOf(script.source) }
    // The script can be replaced with the same id (e.g. after an update check); do not save stale
    // text over the newer source.
    LaunchedEffect(script.source) { source = script.source }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        script.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { onSave(source) }) { Text(stringResource(R.string.save)) }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UrlInputDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm(value.trim()) }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.install)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SourceInputDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(280.dp)) {
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = { if (value.isNotBlank()) onConfirm(value) },
                        enabled = value.isNotBlank(),
                    ) { Text(stringResource(R.string.install)) }
                }
            }
        }
    }
}

@Composable
fun UserscriptInstallDialog(
    pending: PendingUserScript,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val script = pending.parsed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(script.name) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                Text(
                    stringResource(R.string.userscript_meta_summary, script.version.ifBlank { "?" }, script.namespace),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (script.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(script.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                val targets = script.matchPatterns.size + script.includePatterns.size
                Text(
                    pluralStringResource(R.plurals.userscript_runs_at, targets, script.runAt, targets),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (script.grants.isNotEmpty()) {
                    Text(
                        stringResource(R.string.userscript_grants, script.grants.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.userscript_source), style = MaterialTheme.typography.labelLarge)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    Text(
                        pending.source,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                    )
                }
                pending.sourceUrl?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AddonInstallDialog(
    pending: PendingAddonInstall,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (pending.version.isBlank()) pending.name else "${pending.name} ${pending.version}",
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.addon_install_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pending.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.addon_install_permissions),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    pending.permissions.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (pending.origins.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.addon_install_origins),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    pending.origins.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
