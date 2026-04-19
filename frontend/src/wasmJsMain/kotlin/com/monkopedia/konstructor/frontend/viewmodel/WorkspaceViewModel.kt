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
import com.monkopedia.konstructor.common.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceViewModel(private val serviceHolder: ServiceHolder) : ViewModel() {

    private val _konstructions = MutableStateFlow<List<Konstruction>>(emptyList())
    val konstructions: StateFlow<List<Konstruction>> = _konstructions.asStateFlow()

    private val _selectedKonstructionId = MutableStateFlow<String?>(null)
    val selectedKonstructionId: StateFlow<String?> = _selectedKonstructionId.asStateFlow()

    private val _workspaceName = MutableStateFlow("")
    val workspaceName: StateFlow<String> = _workspaceName.asStateFlow()

    private var currentWorkspace: Workspace? = null

    fun selectKonstruction(id: String?) {
        _selectedKonstructionId.value = id
    }

    fun loadWorkspace(workspaceId: String) {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ws = service.get(workspaceId)
                currentWorkspace = ws
                _workspaceName.value = ws.getName()
                _konstructions.value = ws.list()
            } catch (_: Exception) {
            }
        }
    }

    fun refreshKonstructions() {
        viewModelScope.launch {
            val ws = currentWorkspace ?: return@launch
            try {
                _konstructions.value = ws.list()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun createKonstruction(name: String, workspaceId: String): Konstruction? {
        val ws = currentWorkspace ?: return null
        return try {
            val k = ws.create(
                Konstruction(
                    name = name,
                    workspaceId = workspaceId,
                    id = ""
                )
            )
            refreshKonstructions()
            k
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteKonstruction(konstruction: Konstruction) {
        val ws = currentWorkspace ?: return
        try {
            ws.delete(konstruction)
            refreshKonstructions()
        } catch (_: Exception) {
        }
    }

    suspend fun renameWorkspace(name: String) {
        val ws = currentWorkspace ?: return
        try {
            ws.setName(name)
            _workspaceName.value = name
        } catch (_: Exception) {
        }
    }
}
