@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mybible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.TagDefinition
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.NeSectionLabel
import com.example.mybible.ui.components.NeTextField

/**
 * Full-page Tags list, pushed over Notes (opened from its top-bar tag
 * icon) — same BackTopBar-plus-FAB chrome as [NotesScreen] rather than a
 * dialog, so managing tags feels like its own place instead of a popup
 * bolted onto Notes.
 *
 * Each row: tag name in bold gold (tertiary), its description underneath
 * (or an italic "No description" placeholder), separated by a hairline —
 * matching the flat divider-separated look of the Notes list rather than
 * elevated cards. A trailing ⋮ menu offers Edit/Delete; tapping the row
 * itself hands off to Notes pre-filtered to that tag.
 */
@Composable
fun TagsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tagDefinitions by viewModel.tagDefinitions.collectAsState(initial = emptyList())
    val notes by viewModel.notes.collectAsState(initial = emptyList())

    // null = sheet closed. A non-null TagDefinition with a blank name
    // signals "new tag" (Add), anything else is "editing this tag".
    var editingTag by remember { mutableStateOf<TagDefinition?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var tagPendingDelete by remember { mutableStateOf<TagDefinition?>(null) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Tags",
                onBack = { viewModel.closeTagsScreen() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag")
            }
        },
        modifier = modifier
    ) { padding ->
        if (tagDefinitions.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Text(
                    text = "No tags yet. Tap + to create one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(tagDefinitions, key = { it.name.lowercase() }) { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openNotesFilteredByTag(tag.name) }
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, size.height - strokeWidth / 2),
                                    end = Offset(size.width, size.height - strokeWidth / 2),
                                    strokeWidth = strokeWidth
                                )
                            }
                            .padding(vertical = 14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tag.name,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = tag.description.ifBlank { "No description" },
                                fontSize = 13.5.sp,
                                fontStyle = if (tag.description.isBlank()) FontStyle.Italic else FontStyle.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Tag options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        editingTag = tag
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        tagPendingDelete = tag
                                    }
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddSheet) {
        TagEditorSheet(
            initial = null,
            knownNames = tagDefinitions.map { it.name },
            onSave = { name, description ->
                viewModel.addTag(name, description)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    val currentlyEditing = editingTag
    if (currentlyEditing != null) {
        TagEditorSheet(
            initial = currentlyEditing,
            knownNames = tagDefinitions.map { it.name }.filterNot { it.equals(currentlyEditing.name, ignoreCase = true) },
            onSave = { name, description ->
                viewModel.updateTag(currentlyEditing.name, name, description)
                editingTag = null
            },
            onDismiss = { editingTag = null }
        )
    }

    val deleteTarget = tagPendingDelete
    if (deleteTarget != null) {
        val usageCount = notes.count { note -> note.tags.any { it.equals(deleteTarget.name, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text("Delete \u201c${deleteTarget.name}\u201d?") },
            text = {
                Text(
                    if (usageCount == 0) "This tag isn't used on any notes. This can't be undone."
                    else "This will remove the tag from " +
                        (if (usageCount == 1) "1 note" else "$usageCount notes") +
                        ". This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(deleteTarget.name)
                        tagPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// Add/Edit sheet — same visual language as the Note editor: NeSectionLabel
// gold headers over flat-bordered NeTextField inputs, in a ModalBottomSheet
// rather than a full page, since two short fields don't need a whole screen.
@Composable
private fun TagEditorSheet(
    initial: TagDefinition?,
    knownNames: List<String>,
    onSave: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotBlank() && knownNames.any { it.equals(trimmedName, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = if (initial == null) "New Tag" else "Edit Tag",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            NeSectionLabel("Name")
            NeTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Tag name",
                bold = true,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth()
            )
            if (isDuplicate) {
                Text(
                    text = "A tag with this name already exists.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            NeSectionLabel("Description", optionalNote = "(optional)")
            // Multi-line, so this doesn't reuse NeTextField (which forces
            // singleLine) — same flat-bordered container look, just taller
            // and left-aligned to the top for multi-line text.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxSize()
                )
                if (description.isEmpty()) {
                    Text(
                        text = "What is this tag for\u2026",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(trimmedName, description.trim()) },
                    enabled = trimmedName.isNotBlank() && !isDuplicate,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
        }
    }
}
