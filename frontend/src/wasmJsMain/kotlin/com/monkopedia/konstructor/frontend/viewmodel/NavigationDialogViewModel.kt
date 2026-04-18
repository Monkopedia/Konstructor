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
package com.monkopedia.konstructor.frontend.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.common.Space
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DialogState(
    val showCreateWorkspace: Boolean = false,
    val showEditWorkspace: Space? = null,
    val showCreateKonstruction: String? = null, // workspaceId
    val showEditKonstruction: Konstruction? = null,
    val showConnectionLost: Boolean = false,
    val showSyncConflict: Boolean = false
)

class NavigationDialogViewModel(
    private val serviceHolder: ServiceHolder
) : ViewModel() {

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    /** Callback invoked after any mutation so UI can refresh lists. */
    var onMutation: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            serviceHolder.connected.collect { connected ->
                if (!connected && serviceHolder.service.value == null) {
                    _dialogState.value = _dialogState.value.copy(showConnectionLost = true)
                } else {
                    _dialogState.value = _dialogState.value.copy(showConnectionLost = false)
                }
            }
        }
    }

    fun showCreateWorkspaceDialog() {
        _dialogState.value = _dialogState.value.copy(showCreateWorkspace = true)
    }

    fun hideCreateWorkspaceDialog() {
        _dialogState.value = _dialogState.value.copy(showCreateWorkspace = false)
    }

    fun showEditWorkspaceDialog(space: Space) {
        _dialogState.value = _dialogState.value.copy(showEditWorkspace = space)
    }

    fun hideEditWorkspaceDialog() {
        _dialogState.value = _dialogState.value.copy(showEditWorkspace = null)
    }

    fun showCreateKonstructionDialog(workspaceId: String) {
        _dialogState.value = _dialogState.value.copy(showCreateKonstruction = workspaceId)
    }

    fun hideCreateKonstructionDialog() {
        _dialogState.value = _dialogState.value.copy(showCreateKonstruction = null)
    }

    fun showEditKonstructionDialog(konstruction: Konstruction) {
        _dialogState.value = _dialogState.value.copy(showEditKonstruction = konstruction)
    }

    fun hideEditKonstructionDialog() {
        _dialogState.value = _dialogState.value.copy(showEditKonstruction = null)
    }

    fun showSyncConflictDialog() {
        _dialogState.value = _dialogState.value.copy(showSyncConflict = true)
    }

    fun hideSyncConflictDialog() {
        _dialogState.value = _dialogState.value.copy(showSyncConflict = false)
    }

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                service.create(Space(id = "", name = name))
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideCreateWorkspaceDialog()
        }
    }

    fun deleteWorkspace(space: Space) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                service.delete(space)
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideEditWorkspaceDialog()
        }
    }

    fun renameWorkspace(id: String, name: String) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ws = service.get(id)
                ws.setName(name)
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideEditWorkspaceDialog()
        }
    }

    fun createKonstruction(name: String, workspaceId: String) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ws = service.get(workspaceId)
                ws.create(Konstruction(name = name, workspaceId = workspaceId, id = ""))
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideCreateKonstructionDialog()
        }
    }

    fun deleteKonstruction(konstruction: Konstruction) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ws = service.get(konstruction.workspaceId)
                ws.delete(konstruction)
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideEditKonstructionDialog()
        }
    }

    fun renameKonstruction(workspaceId: String, id: String, name: String) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ks = service.konstruction(
                    Konstruction(name = "", workspaceId = workspaceId, id = id)
                )
                ks.setName(name)
                onMutation?.invoke()
            } catch (_: Exception) {
            }
            hideEditKonstructionDialog()
        }
    }

    fun uploadStl(name: String, workspaceId: String, data: ByteArray) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ws = service.get(workspaceId)
                val k = ws.create(
                    Konstruction(
                        name = name,
                        workspaceId = workspaceId,
                        id = "",
                        type = KonstructionType.STL
                    )
                )
                val ks = service.konstruction(k)
                ks.setBinary(ByteReadChannel(data))
                onMutation?.invoke()
            } catch (_: Exception) {
            }
        }
    }
}
