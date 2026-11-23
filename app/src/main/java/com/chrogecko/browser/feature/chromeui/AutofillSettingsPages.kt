package com.chrogecko.browser.feature.chromeui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.repository.SavedCard
import com.chrogecko.browser.core.database.repository.SavedLogin

private fun autofillServiceState(context: Context): Int {
    val manager = context.getSystemService(AutofillManager::class.java) ?: return 0
    if (!manager.isAutofillSupported) return 0
    return if (manager.hasEnabledAutofillServices()) 1 else 2
}

/** Settings subpage managing Android Autofill integration, password saving, and stored logins. */
@Composable
internal fun PasswordsSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val savedLogins by viewModel.savedLogins.collectAsStateWithLifecycle()
    val androidAutofill by viewModel.androidAutofillEnabled.collectAsStateWithLifecycle()
    val builtInSave by viewModel.builtInPasswordSaveEnabled.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var deletingLogin by remember { mutableStateOf<SavedLogin?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var autofillState by remember { mutableIntStateOf(autofillServiceState(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) autofillState = autofillServiceState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openAutofillSettings = {
        val intent = Intent(
            Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
            "package:${context.packageName}".toUri(),
        )
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        Unit
    }

    val filtered = remember(savedLogins, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            savedLogins
        } else {
            savedLogins.filter {
                it.host.contains(needle, ignoreCase = true) ||
                    it.username.contains(needle, ignoreCase = true)
            }
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_passwords_title),
        subtitle = stringResource(R.string.settings_passwords_subtitle),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "services_header") {
                SettingsSectionHeader(stringResource(R.string.settings_autofill_services_header))
            }
            item(key = "android_autofill") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_android_autofill_title),
                    description = stringResource(R.string.settings_android_autofill_desc),
                    checked = androidAutofill,
                    shape = settingsGroupItemShape(0, 3),
                    onCheckedChange = viewModel::setAndroidAutofillEnabled,
                )
            }
            item(key = "system_autofill") {
                val shape = settingsGroupItemShape(1, 3)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .clickable(enabled = autofillState != 0, onClick = openAutofillSettings),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_system_autofill_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(
                                    when (autofillState) {
                                        1 -> R.string.settings_system_autofill_active
                                        2 -> R.string.settings_system_autofill_inactive
                                        else -> R.string.settings_system_autofill_unsupported
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (autofillState != 0) {
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
            item(key = "builtin_save") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_builtin_save_title),
                    description = stringResource(R.string.settings_builtin_save_desc),
                    checked = builtInSave,
                    shape = settingsGroupItemShape(2, 3),
                    onCheckedChange = viewModel::setBuiltInPasswordSaveEnabled,
                )
            }
            item(key = "saved_header") {
                SettingsSectionHeader(stringResource(R.string.settings_saved_passwords_header))
            }
            if (savedLogins.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.settings_search_passwords)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    SettingsEmptyState(
                        icon = Icons.Outlined.Key,
                        title = stringResource(
                            if (savedLogins.isEmpty()) {
                                R.string.settings_passwords_none
                            } else {
                                R.string.settings_passwords_no_match
                            },
                        ),
                        body = stringResource(
                            if (savedLogins.isEmpty()) {
                                R.string.settings_passwords_empty_body
                            } else {
                                R.string.settings_passwords_no_match_body
                            },
                        ),
                    )
                }
            } else {
                itemsIndexed(filtered, key = { _, item -> item.id }) { index, entry ->
                    val shape = settingsGroupItemShape(index, filtered.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.host, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    entry.username.ifBlank { stringResource(R.string.settings_no_username) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { deletingLogin = entry }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_remove_password),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deletingLogin?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingLogin = null },
            title = { Text(stringResource(R.string.settings_delete_password_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_delete_password_message,
                        entry.username.ifBlank { stringResource(R.string.settings_no_username) },
                        entry.host,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLogin(entry.id)
                        deletingLogin = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingLogin = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Settings subpage listing saved encrypted payment cards. */
@Composable
internal fun PaymentMethodsSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val savedCards by viewModel.savedCards.collectAsStateWithLifecycle()
    var cardFormOpen by remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_payments_title),
        subtitle = stringResource(R.string.settings_payments_subtitle),
        hasFab = true,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { cardFormOpen = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_add_card)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (savedCards.isEmpty()) {
                item(key = "empty") {
                    SettingsEmptyState(
                        icon = Icons.Outlined.CreditCard,
                        title = stringResource(R.string.settings_cards_none),
                        body = stringResource(R.string.settings_cards_empty_body),
                    )
                }
            } else {
                itemsIndexed(savedCards, key = { _, item -> item.id }) { index, card ->
                    CardSettingsRow(
                        card = card,
                        shape = settingsGroupItemShape(index, savedCards.size),
                        onDelete = { viewModel.deleteCard(card.id) },
                    )
                }
            }
        }
    }

    if (cardFormOpen) {
        CardDialog(
            onSave = { label, name, number, expiry ->
                viewModel.saveCard(label, name, number, expiry)
                cardFormOpen = false
            },
            onDismiss = { cardFormOpen = false },
        )
    }
}

@Composable
private fun CardSettingsRow(
    card: SavedCard,
    shape: RoundedCornerShape,
    onDelete: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (card.last4.isNotBlank()) "${card.label} •••• ${card.last4}" else card.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(card.nameOnCard, card.expiry).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_remove_card))
            }
        }
    }
}

/** Settings subpage managing saved postal addresses for form autofill. */
@Composable
internal fun AddressesSettingsPage(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val savedAddresses by viewModel.savedAddresses.collectAsStateWithLifecycle()
    var addressFormOpen by remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_addresses_title),
        subtitle = stringResource(R.string.settings_addresses_subtitle),
        hasFab = true,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addressFormOpen = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_address_title)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (savedAddresses.isEmpty()) {
                item(key = "empty") {
                    SettingsEmptyState(
                        icon = Icons.Outlined.LocationOn,
                        title = stringResource(R.string.settings_addresses_none),
                        body = stringResource(R.string.settings_addresses_empty_body),
                    )
                }
            } else {
                itemsIndexed(savedAddresses, key = { _, item -> item.id }) { index, address ->
                    AddressSettingsRow(
                        address = address,
                        shape = settingsGroupItemShape(index, savedAddresses.size),
                        onDelete = { viewModel.deleteAddress(address.id) },
                    )
                }
            }
        }
    }

    if (addressFormOpen) {
        AddressDialog(
            onSave = {
                viewModel.saveAddress(it)
                addressFormOpen = false
            },
            onDismiss = { addressFormOpen = false },
        )
    }
}

@Composable
private fun AddressSettingsRow(
    address: AddressEntity,
    shape: RoundedCornerShape,
    onDelete: () -> Unit,
) {
    val summary = remember(address) {
        listOf(address.name, address.street, address.city, address.postal, address.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(address.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_remove_address))
            }
        }
    }
}
