package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.repository.AutofillOption
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R

/** Chrome's autofill strip above the page: pill chips for saved addresses or payment cards. */
@Composable
fun AutofillBar(
    options: List<AutofillOption>,
    onPick: (AutofillOption) -> Unit,
    onDismiss: () -> Unit,
) {
    if (options.isEmpty()) return
    val isCard = options.first().kind == "card"
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onPick(option) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isCard) Icons.Rounded.CreditCard else Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_not_now),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun AddressDialog(onSave: (AddressEntity) -> Unit, onDismiss: () -> Unit) {
    val defaultLabel = stringResource(R.string.default_address_label)
    val fallbackLabel = stringResource(R.string.default_address_fallback_label)
    var label by remember { mutableStateOf(defaultLabel) }
    var name by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_address_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Field(stringResource(R.string.field_label), label) { label = it }
                Field(stringResource(R.string.field_full_name), name) { name = it }
                Field(stringResource(R.string.field_street), street) { street = it }
                Field(stringResource(R.string.field_city), city) { city = it }
                Field(stringResource(R.string.field_postal), postal) { postal = it }
                Field(stringResource(R.string.field_country), country) { country = it }
                Field(stringResource(R.string.field_phone), phone, KeyboardType.Phone) { phone = it }
                Field(stringResource(R.string.field_email), email, KeyboardType.Email) { email = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && street.isNotBlank(),
                onClick = {
                    onSave(
                        AddressEntity(
                            label = label.trim().ifBlank { fallbackLabel },
                            name = name.trim(),
                            street = street.trim(),
                            city = city.trim(),
                            postal = postal.trim(),
                            country = country.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun CardDialog(
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultLabel = stringResource(R.string.default_card_label)
    var label by remember { mutableStateOf(defaultLabel) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_payment_method_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Field(stringResource(R.string.field_label), label) { label = it }
                Field(stringResource(R.string.field_name_on_card), name) { name = it }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { ch -> ch.isDigit() }.take(19) },
                    label = { Text(stringResource(R.string.field_card_number)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Field(stringResource(R.string.field_expiry), expiry) { expiry = it.take(7) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.length >= 12,
                onClick = { onSave(label.trim(), name.trim(), number, expiry.trim()) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}
