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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.monkopedia.konstructor.frontend.ui.dialogs.ConnectionLostDialog
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import org.koin.compose.koinInject

@Composable
fun Initializer() {
    val spaceListVm = koinInject<SpaceListViewModel>()
    val workspaceVm = koinInject<WorkspaceViewModel>()
    val dialogVm = koinInject<NavigationDialogViewModel>()
    val workspaces by spaceListVm.workspaces.collectAsState()
    val selectedWorkspaceId by spaceListVm.selectedWorkspaceId.collectAsState()
    val konstructions by workspaceVm.konstructions.collectAsState()
    val selectedKonstructionId by workspaceVm.selectedKonstructionId.collectAsState()
    val dialogState by dialogVm.dialogState.collectAsState()

    // Wire up mutation callback so dialogs can trigger list refreshes
    LaunchedEffect(dialogVm, spaceListVm, workspaceVm) {
        dialogVm.onMutation = {
            spaceListVm.refreshWorkspaces()
            workspaceVm.refreshKonstructions()
        }
    }

    // Auto-select first workspace on startup
    LaunchedEffect(workspaces) {
        val ws = workspaces
        if (ws != null && ws.isNotEmpty() && ws.none { it.id == selectedWorkspaceId }) {
            val firstWs = ws.first()
            spaceListVm.selectWorkspace(firstWs.id)
            workspaceVm.loadWorkspace(firstWs.id)
        }
    }

    // Load the selected workspace when the selection changes, or once the
    // workspace list first becomes available. `workspaces` is null until the
    // service connects, so a persisted selection (restored before the
    // websocket is up) would otherwise call loadWorkspace against a null
    // service, no-op, and never retry — leaving the workspace empty until a
    // manual reselect.
    LaunchedEffect(selectedWorkspaceId, workspaces) {
        val wsId = selectedWorkspaceId
        if (wsId != null && workspaces != null) {
            workspaceVm.loadWorkspace(wsId)
        }
    }

    // Auto-select first konstruction when workspace loads
    LaunchedEffect(konstructions) {
        if (konstructions.isNotEmpty() &&
            konstructions.none { it.id == selectedKonstructionId }
        ) {
            workspaceVm.selectKonstruction(konstructions.first().id)
        }
    }

    if (dialogState.showConnectionLost) {
        ConnectionLostDialog()
    }

    when {
        workspaces == null -> LoadingScreen()
        workspaces!!.isEmpty() -> CreateFirstWorkspaceScreen()
        else -> MainScreen()
    }
}
