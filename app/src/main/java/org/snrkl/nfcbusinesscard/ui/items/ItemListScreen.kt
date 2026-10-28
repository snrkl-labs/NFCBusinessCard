package org.snrkl.nfcbusinesscard.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType

/**
 * The Shareable Item library.
 *
 * Add, edit, reorder, delete, and pick the Active Item — everything NF2 asks the
 * settings surface to cover for the item library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemListScreen(
    state: ItemListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: (ShareableItemType) -> Unit,
    onImportVCardFile: () -> Unit,
    onImportFromContacts: () -> Unit,
    onEdit: (ShareableItem) -> Unit,
    onDelete: (ShareableItem) -> Unit,
    onSetActive: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addMenuVisible by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<ShareableItem?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Centred, because at the end of the row it lands squarely on the delete
        // button of whichever card is behind it — and "delete" is the worst
        // possible thing to mis-tap in a list of saved cards.
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.items_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(
                    expanded = addMenuVisible,
                    onDismissRequest = { addMenuVisible = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_add_url)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            addMenuVisible = false
                            onAdd(ShareableItemType.Url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_add_vcard)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        onClick = {
                            addMenuVisible = false
                            onAdd(ShareableItemType.VCard)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_import_contact)) },
                        onClick = {
                            addMenuVisible = false
                            onImportFromContacts()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_import_vcard)) },
                        onClick = {
                            addMenuVisible = false
                            onImportVCardFile()
                        },
                    )
                }
                FloatingActionButton(onClick = { addMenuVisible = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.items_add),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (state.items.isEmpty() && !state.isLoading) {
            EmptyState(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .selectableGroup(),
            // The bottom inset clears the FAB. Without it the last card can be
            // scrolled to but never fully seen, and its buttons stay unreachable.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                val index = state.items.indexOf(item)
                ItemRow(
                    item = item,
                    isActive = item.id == state.activeItemId,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    onSetActive = { onSetActive(item.id) },
                    onEdit = { onEdit(item) },
                    onDelete = { pendingDeletion = item },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
            }
        }
    }

    pendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.items_delete_confirm_title)) },
            text = { Text(stringResource(R.string.items_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item)
                        pendingDeletion = null
                    },
                ) {
                    Text(stringResource(R.string.items_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.editor_cancel))
                }
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: ShareableItem,
    isActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text(item.label.ifBlank { stringResource(R.string.items_title) }) },
                supportingContent = {
                    val detail = when (item.type) {
                        ShareableItemType.Url -> item.url
                        ShareableItemType.VCard -> item.vCard?.formattedName
                    }
                    if (!detail.isNullOrBlank()) Text(detail)
                },
                leadingContent = {
                    RadioButton(selected = isActive, onClick = null)
                },
                trailingContent = {
                    if (isActive) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(stringResource(R.string.items_active_badge)) },
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.selectable(
                    selected = isActive,
                    role = Role.RadioButton,
                    onClick = onSetActive,
                ),
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.items_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.items_move_down),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.items_edit),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.items_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.items_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.items_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
