/*
 * Copyright 2022 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.frontend.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import org.koin.compose.koinInject

@Composable
fun NavigationPane(modifier: Modifier = Modifier) {
    val spaceListVm = koinInject<SpaceListViewModel>()
    val workspaceVm = koinInject<WorkspaceViewModel>()
    val dialogVm = koinInject<NavigationDialogViewModel>()
    val settingsVm = koinInject<SettingsViewModel>()

    val workspaces by spaceListVm.workspaces.collectAsState()
    val selectedWorkspaceId by spaceListVm.selectedWorkspaceId.collectAsState()
    val konstructions by workspaceVm.konstructions.collectAsState()

    LazyColumn(modifier = modifier.padding(8.dp)) {
        val spaceList = workspaces ?: emptyList()
        items(spaceList, key = { it.id }) { space ->
            var isExpanded by remember(space.id) {
                mutableStateOf(space.id == selectedWorkspaceId)
            }
            WorkspaceItem(
                space = space,
                isExpanded = isExpanded,
                konstructions = if (space.id == selectedWorkspaceId) konstructions else emptyList(),
                onExpand = {
                    if (space.id != selectedWorkspaceId) {
                        spaceListVm.selectWorkspace(space.id)
                        workspaceVm.loadWorkspace(space.id)
                        isExpanded = true
                    } else {
                        isExpanded = !isExpanded
                    }
                },
                onEditSpace = { dialogVm.showEditWorkspaceDialog(space) },
                onSelectKonstruction = { k ->
                    workspaceVm.selectKonstruction(k.id)
                    settingsVm.setCodePaneMode(CodePaneMode.EDITOR)
                },
                onEditKonstruction = { k -> dialogVm.showEditKonstructionDialog(k) },
                onAddKonstruction = { dialogVm.showCreateKonstructionDialog(space.id) },
                onUploadStl = {
                    kotlinx.browser.window.alert(
                        "STL upload is not yet available. " +
                            "Use 'Add new konstruction' to create script-based models."
                    )
                }
            )
        }

        item(key = "add-space") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dialogVm.showCreateWorkspaceDialog() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add workspace",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Add new space",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun WorkspaceItem(
    space: Space,
    isExpanded: Boolean,
    konstructions: List<Konstruction>,
    onExpand: () -> Unit,
    onEditSpace: () -> Unit,
    onSelectKonstruction: (Konstruction) -> Unit,
    onEditKonstruction: (Konstruction) -> Unit,
    onAddKonstruction: () -> Unit,
    onUploadStl: () -> Unit
) {
    Column {
        // Workspace header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = space.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEditSpace) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit workspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expanded content
        if (isExpanded) {
            konstructions.forEach { k ->
                KonstructionItem(
                    konstruction = k,
                    onClick = { onSelectKonstruction(k) },
                    onEdit = { onEditKonstruction(k) }
                )
            }

            // Add konstruction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddKonstruction() }
                    .padding(start = 40.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add konstruction",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Add new konstruction",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Upload STL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUploadStl() }
                    .padding(start = 40.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = "Upload STL",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Upload STL",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun KonstructionItem(konstruction: Konstruction, onClick: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 40.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (konstruction.type == KonstructionType.STL) {
                Icons.Filled.ViewInAr
            } else {
                Icons.Filled.Widgets
            },
            contentDescription =
                if (konstruction.type == KonstructionType.STL) "STL file" else "Script",
            tint = if (konstruction.type == KonstructionType.STL) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = konstruction.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit konstruction",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
