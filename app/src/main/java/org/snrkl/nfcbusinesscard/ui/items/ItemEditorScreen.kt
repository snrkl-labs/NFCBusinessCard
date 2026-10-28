package org.snrkl.nfcbusinesscard.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType

/**
 * The item editor.
 *
 * One form for both item types, branching on [ShareableItemType]. Text fields
 * are MD3 outlined fields with supporting text; validation errors are attached
 * to the field that failed rather than raised as a dialog, so the operator can
 * see what to fix and fix it in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemEditorScreen(
    state: ItemEditorUiState,
    onLabelChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onVCardFieldChange: (VCardField, String) -> Unit,
    onPhoneChange: (Int, String) -> Unit,
    onPhoneTypeChange: (Int, ContactFieldType) -> Unit,
    onAddPhone: () -> Unit,
    onRemovePhone: (Int) -> Unit,
    onEmailChange: (Int, String) -> Unit,
    onEmailTypeChange: (Int, ContactFieldType) -> Unit,
    onAddEmail: () -> Unit,
    onRemoveEmail: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    val item = state.item
    val isUrl = item.type == ShareableItemType.Url

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                isUrl && state.isNew -> R.string.editor_title_new_url
                                isUrl -> R.string.editor_title_edit_url
                                state.isNew -> R.string.editor_title_new_vcard
                                else -> R.string.editor_title_edit_vcard
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The app is edge to edge, so the keyboard is an inset this
                // content must handle itself. Without imePadding the scroll
                // region keeps its full height and the fields under the "More"
                // heading sit behind the keyboard with nothing to scroll them
                // into view.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = item.label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.editor_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (EditorError.LabelRequired in state.errors) {
                                R.string.editor_label_required
                            } else {
                                R.string.editor_label_supporting
                            },
                        ),
                    )
                },
                isError = EditorError.LabelRequired in state.errors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            if (isUrl) {
                val urlError = when {
                    EditorError.UrlRequired in state.errors -> R.string.editor_url_required
                    EditorError.UrlInvalid in state.errors -> R.string.editor_url_invalid
                    else -> null
                }
                OutlinedTextField(
                    value = item.url.orEmpty(),
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.editor_url)) },
                    supportingText = {
                        Text(stringResource(urlError ?: R.string.editor_url_supporting))
                    },
                    isError = urlError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                VCardForm(
                    state = state,
                    onPickPhoto = onPickPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onVCardFieldChange = onVCardFieldChange,
                    onPhoneChange = onPhoneChange,
                    onPhoneTypeChange = onPhoneTypeChange,
                    onAddPhone = onAddPhone,
                    onRemovePhone = onRemovePhone,
                    onEmailChange = onEmailChange,
                    onEmailTypeChange = onEmailTypeChange,
                    onAddEmail = onAddEmail,
                    onRemoveEmail = onRemoveEmail,
                )
            }
        }
    }
}

/** The vCard fields the editor exposes. */
internal enum class VCardField {
    FormattedName, GivenName, FamilyName, Organisation, JobTitle, Url, Address, Note
}

@Composable
private fun VCardForm(
    state: ItemEditorUiState,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onVCardFieldChange: (VCardField, String) -> Unit,
    onPhoneChange: (Int, String) -> Unit,
    onPhoneTypeChange: (Int, ContactFieldType) -> Unit,
    onAddPhone: () -> Unit,
    onRemovePhone: (Int) -> Unit,
    onEmailChange: (Int, String) -> Unit,
    onEmailTypeChange: (Int, ContactFieldType) -> Unit,
    onAddEmail: () -> Unit,
    onRemoveEmail: (Int) -> Unit,
) {
    val data = state.item.vCard ?: return

    PhotoRow(
        photoUri = data.photoUri,
        onPick = onPickPhoto,
        onRemove = onRemovePhoto,
    )

    SectionLabel(stringResource(R.string.editor_vcard_name_section))
    OutlinedTextField(
        value = data.formattedName,
        onValueChange = { onVCardFieldChange(VCardField.FormattedName, it) },
        label = { Text(stringResource(R.string.editor_formatted_name)) },
        isError = EditorError.NameRequired in state.errors,
        supportingText = {
            if (EditorError.NameRequired in state.errors) {
                Text(stringResource(R.string.editor_name_required))
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = data.givenName,
            onValueChange = { onVCardFieldChange(VCardField.GivenName, it) },
            label = { Text(stringResource(R.string.editor_given_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = data.familyName,
            onValueChange = { onVCardFieldChange(VCardField.FamilyName, it) },
            label = { Text(stringResource(R.string.editor_family_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(1f),
        )
    }

    SectionLabel(stringResource(R.string.editor_vcard_work_section))
    OutlinedTextField(
        value = data.organisation,
        onValueChange = { onVCardFieldChange(VCardField.Organisation, it) },
        label = { Text(stringResource(R.string.editor_organisation)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = data.jobTitle,
        onValueChange = { onVCardFieldChange(VCardField.JobTitle, it) },
        label = { Text(stringResource(R.string.editor_job_title)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )

    SectionLabel(stringResource(R.string.editor_vcard_contact_section))
    data.phones.forEachIndexed { index, phone ->
        TypedValueRow(
            value = phone.value,
            type = phone.type,
            label = stringResource(R.string.editor_phone),
            keyboardType = KeyboardType.Phone,
            onValueChange = { onPhoneChange(index, it) },
            onTypeChange = { onPhoneTypeChange(index, it) },
            onRemove = { onRemovePhone(index) },
            canRemove = data.phones.size > 1,
        )
    }
    TextButton(onClick = onAddPhone) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.editor_phone_add), modifier = Modifier.padding(start = 8.dp))
    }

    data.emails.forEachIndexed { index, email ->
        TypedValueRow(
            value = email.value,
            type = email.type,
            label = stringResource(R.string.editor_email),
            keyboardType = KeyboardType.Email,
            onValueChange = { onEmailChange(index, it) },
            onTypeChange = { onEmailTypeChange(index, it) },
            onRemove = { onRemoveEmail(index) },
            canRemove = data.emails.size > 1,
        )
    }
    TextButton(onClick = onAddEmail) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.editor_email_add), modifier = Modifier.padding(start = 8.dp))
    }

    SectionLabel(stringResource(R.string.editor_vcard_more_section))
    OutlinedTextField(
        value = data.url,
        onValueChange = { onVCardFieldChange(VCardField.Url, it) },
        label = { Text(stringResource(R.string.editor_website)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = data.address,
        onValueChange = { onVCardFieldChange(VCardField.Address, it) },
        label = { Text(stringResource(R.string.editor_address)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = data.note,
        onValueChange = { onVCardFieldChange(VCardField.Note, it) },
        label = { Text(stringResource(R.string.editor_note)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The portrait row.
 *
 * The supporting text says plainly that the photo stays on the device. That is
 * not decoration: a contact photo looks exactly like something that would be
 * shared, and someone adding one is entitled to know it will not be.
 */
@Composable
private fun PhotoRow(
    photoUri: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri == null) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    model = photoUri,
                    contentDescription = stringResource(R.string.editor_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.editor_photo),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.editor_photo_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = onPick) {
                    Text(stringResource(R.string.editor_photo_choose))
                }
                if (photoUri != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.editor_photo_remove))
                    }
                }
            }
        }
    }
}

/**
 * A value with a type selector beside it — a phone number or email address.
 *
 * The type dropdown is narrow and the value field takes the rest, because the
 * value is what gets typed and the type is usually left at its default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypedValueRow(
    value: String,
    type: ContactFieldType,
    label: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    onTypeChange: (ContactFieldType) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
        )

        // A compact chip rather than a second text field. The value beside it is
        // what gets typed into and deserves the width; the type is a four-way
        // choice that is usually left alone, and a full outlined field with its
        // own floating label is far more furniture than it earns.
        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(type.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.editor_field_type),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ContactFieldType.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            onTypeChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }

        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.editor_remove_field),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

/** Display names for the contact field types. */
internal val ContactFieldType.labelRes: Int
    get() = when (this) {
        ContactFieldType.Work -> R.string.editor_type_work
        ContactFieldType.Home -> R.string.editor_type_home
        ContactFieldType.Mobile -> R.string.editor_type_mobile
        ContactFieldType.Other -> R.string.editor_type_other
    }
