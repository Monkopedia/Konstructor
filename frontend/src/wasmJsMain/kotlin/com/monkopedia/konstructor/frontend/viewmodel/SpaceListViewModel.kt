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
import com.monkopedia.konstructor.common.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SpaceListViewModel(private val serviceHolder: ServiceHolder) : ViewModel() {

    private val _workspaces = MutableStateFlow<List<Space>?>(null)
    val workspaces: StateFlow<List<Space>?> = _workspaces.asStateFlow()

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    val selectedWorkspaceId: StateFlow<String?> = _selectedWorkspaceId.asStateFlow()

    init {
        viewModelScope.launch {
            serviceHolder.service.collectLatest { service ->
                if (service != null) {
                    try {
                        _workspaces.value = service.list()
                    } catch (e: Exception) {
                        _workspaces.value = null
                    }
                } else {
                    _workspaces.value = null
                }
            }
        }
    }

    fun selectWorkspace(id: String?) {
        _selectedWorkspaceId.value = id
    }

    fun refreshWorkspaces() {
        viewModelScope.launch {
            val service = serviceHolder.service.value ?: return@launch
            try {
                _workspaces.value = service.list()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun createWorkspace(name: String): Space? {
        val service = serviceHolder.service.value ?: return null
        return try {
            val space = service.create(Space(id = "", name = name))
            refreshWorkspaces()
            space
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteWorkspace(space: Space) {
        val service = serviceHolder.service.value ?: return
        try {
            service.delete(space)
            refreshWorkspaces()
        } catch (_: Exception) {
        }
    }
}
