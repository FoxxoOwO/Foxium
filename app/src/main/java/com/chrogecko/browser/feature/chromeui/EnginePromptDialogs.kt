package com.chrogecko.browser.feature.chromeui

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chrogecko.browser.core.engine.EnginePrompt
import com.chrogecko.browser.core.engine.PermissionAsk
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R

/**
 * Renders whatever Gecko is blocked on: JS dialogs, the file picker and HTTP auth.
 * The file picker is answered through SAF, everything else through the callbacks.
 */
@Composable
fun EnginePromptHost(
    prompt: EnginePrompt?,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onChoice: (Int) -> Unit,
    onAuth: (String, String) -> Unit,
    onFiles: (List<Uri>) -> Unit,
) {
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> onFiles(listOfNotNull(uri)) }
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onFiles(uris) }

    if (prompt == null) return

    when (prompt) {
        is EnginePrompt.Alert -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.prompt_this_page_says)) },
            text = { Text(prompt.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )

        is EnginePrompt.Confirm -> AlertDialog(
            onDismissRequest = { onConfirm(false) },
            title = { Text(stringResource(R.string.prompt_this_page_says)) },
            text = { Text(prompt.message) },
            confirmButton = { TextButton(onClick = { onConfirm(true) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.cancel)) } },
        )

        is EnginePrompt.TextInput -> TextPromptDialog(prompt, onDismiss, onText)

        is EnginePrompt.Choice -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(prompt.message.ifBlank { stringResource(R.string.prompt_choose_option) }) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    prompt.choices.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoice(index) }
                                .padding(vertical = 14.dp),
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )

        is EnginePrompt.Auth -> AuthPromptDialog(prompt, onDismiss, onAuth)

        is EnginePrompt.FilePick -> {
            // Gecko blocks the picker request until an answer arrives, so open SAF immediately.
            LaunchedEffect(prompt.id) {
                val mime = normalizeMimeTypes(prompt.mimeTypes)
                runCatching {
                    if (prompt.multiple) multiPicker.launch(mime) else singlePicker.launch(mime)
                }.onFailure {
                    onFiles(emptyList())
                }
            }
        }
    }
}

private fun normalizeMimeTypes(rawTypes: List<String>): Array<String> {
    val nonBlank = rawTypes.map { it.substringBefore(';').trim() }.filter { it.isNotEmpty() }
    if (nonBlank.isEmpty()) return arrayOf("*/*")
    val mimeMap = MimeTypeMap.getSingleton()
    val mapped = nonBlank.map { token ->
        when {
            token.startsWith('.') -> {
                mimeMap.getMimeTypeFromExtension(token.removePrefix(".").lowercase())
                    ?: return arrayOf("*/*")
            }
            token.contains('/') -> token
            else -> return arrayOf("*/*")
        }
    }
    return mapped.toTypedArray()
}

@Composable
private fun TextPromptDialog(
    prompt: EnginePrompt.TextInput,
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
) {
    var value by remember(prompt.id) { mutableStateOf(prompt.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.message.ifBlank { stringResource(R.string.prompt_this_page_says) }) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onText(value) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AuthPromptDialog(
    prompt: EnginePrompt.Auth,
    onDismiss: () -> Unit,
    onAuth: (String, String) -> Unit,
) {
    var user by remember(prompt.id) { mutableStateOf(prompt.user.orEmpty()) }
    var password by remember(prompt.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.message.ifBlank { stringResource(R.string.prompt_sign_in) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_username)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAuth(user, password) }) { Text(stringResource(R.string.prompt_sign_in)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Camera, microphone, location and notification prompts, worded like Chrome. */
@Composable
fun SitePermissionHost(
    ask: PermissionAsk?,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    if (ask == null) return
    val site = ask.host.ifBlank { stringResource(R.string.permission_this_site) }
    val (title, message) = when (ask.kind) {
        PermissionAsk.Kind.CAMERA -> stringResource(R.string.permission_camera_title) to stringResource(R.string.permission_camera_message, site)
        PermissionAsk.Kind.MICROPHONE -> stringResource(R.string.permission_microphone_title) to stringResource(R.string.permission_microphone_message, site)
        PermissionAsk.Kind.CAMERA_AND_MICROPHONE ->
            stringResource(R.string.permission_cam_mic_title) to stringResource(R.string.permission_cam_mic_message, site)
        PermissionAsk.Kind.GEOLOCATION -> stringResource(R.string.permission_location_title) to stringResource(R.string.permission_location_message, site)
        PermissionAsk.Kind.NOTIFICATIONS -> stringResource(R.string.permission_notifications_title) to stringResource(R.string.permission_notifications_message, site)
    }
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_change_later),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) } },
        dismissButton = { TextButton(onClick = onDeny) { Text(stringResource(R.string.block)) } },
    )
}
