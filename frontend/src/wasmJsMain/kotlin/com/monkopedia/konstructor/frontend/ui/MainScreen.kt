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
package com.monkopedia.konstructor.frontend.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.monkopedia.konstructor.frontend.ui.dialogs.CreateKonstructionDialog
import com.monkopedia.konstructor.frontend.ui.dialogs.CreateWorkspaceDialog
import com.monkopedia.konstructor.frontend.ui.dialogs.EditKonstructionDialog
import com.monkopedia.konstructor.frontend.ui.dialogs.EditWorkspaceDialog
import com.monkopedia.konstructor.frontend.ui.dialogs.SyncConflictDialog
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen() {
    val settingsVm = koinInject<SettingsViewModel>()
    val dialogVm = koinViewModel<NavigationDialogViewModel>()
    val showCodeLeft by settingsVm.showCodeLeft.collectAsState()
    val dialogState by dialogVm.dialogState.collectAsState()

    // Dialogs
    if (dialogState.showCreateWorkspace) {
        CreateWorkspaceDialog()
    }
    dialogState.showEditWorkspace?.let { space ->
        EditWorkspaceDialog(space = space)
    }
    dialogState.showCreateKonstruction?.let { workspaceId ->
        CreateKonstructionDialog(workspaceId = workspaceId)
    }
    dialogState.showEditKonstruction?.let { konstruction ->
        EditKonstructionDialog(konstruction = konstruction)
    }
    if (dialogState.showSyncConflict) {
        SyncConflictDialog()
    }

    // Main layout: 50/50 split
    Row(modifier = Modifier.fillMaxSize()) {
        if (showCodeLeft) {
            ContentPane(modifier = Modifier.weight(1f).fillMaxHeight())
            GlPlaceholder(modifier = Modifier.weight(1f).fillMaxHeight())
        } else {
            GlPlaceholder(modifier = Modifier.weight(1f).fillMaxHeight())
            ContentPane(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}
