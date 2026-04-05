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
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionRender
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UiState {
    LOADING,
    DEFAULT,
    SAVING,
    COMPILING,
    EXECUTING
}

class KonstructionViewModel(
    private val serviceHolder: ServiceHolder
) : ViewModel() {

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _info = MutableStateFlow<KonstructionInfo?>(null)
    val info: StateFlow<KonstructionInfo?> = _info.asStateFlow()

    private val _state = MutableStateFlow(UiState.LOADING)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<TaskMessage>>(emptyList())
    val messages: StateFlow<List<TaskMessage>> = _messages.asStateFlow()

    private var konstructionService: KonstructionService? = null
    private var listenerKey: String? = null

    fun loadKonstruction(konstruction: Konstruction) {
        viewModelScope.launch {
            _state.value = UiState.LOADING
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ks = service.konstruction(konstruction)
                konstructionService = ks
                _content.value = ks.fetch()
                _info.value = ks.getInfo()
                _state.value = UiState.DEFAULT
                registerListener(ks)
            } catch (e: Exception) {
                _state.value = UiState.DEFAULT
            }
        }
    }

    private suspend fun registerListener(ks: KonstructionService) {
        try {
            val listener = object : KonstructionListener {
                override suspend fun requestedCallbacks(): List<KonstructionCallbacks> {
                    return listOf(
                        KonstructionCallbacks.INFO_CHANGE,
                        KonstructionCallbacks.DIRTY_CHANGE,
                        KonstructionCallbacks.CONTENT_CHANGE,
                        KonstructionCallbacks.TASK_COMPLETE
                    )
                }

                override suspend fun onInfoChanged(info: KonstructionInfo) {
                    _info.value = info
                }

                override suspend fun onDirtyStateChanged(
                    state: com.monkopedia.konstructor.common.DirtyState
                ) {
                    // Update info with new dirty state
                    _info.value = _info.value?.copy(dirtyState = state)
                }

                override suspend fun onTargetChanged(target: KonstructionTarget) {
                    // Update info targets
                }

                override suspend fun onRenderChanged(render: KonstructionRender) {
                    // Phase 2: 3D rendering
                }

                override suspend fun onContentChange(u: Unit) {
                    try {
                        _content.value = ks.fetch()
                    } catch (_: Exception) {
                    }
                }

                override suspend fun onTaskComplete(taskResult: TaskResult) {
                    _messages.value = taskResult.messages
                    _state.value = UiState.DEFAULT
                }
            }
            listenerKey = ks.register(listener)
        } catch (_: Exception) {
        }
    }

    fun save(text: String) {
        viewModelScope.launch {
            val ks = konstructionService ?: return@launch
            _state.value = UiState.SAVING
            try {
                ks.set(text)
                _content.value = text
                _state.value = UiState.DEFAULT
            } catch (_: Exception) {
                _state.value = UiState.DEFAULT
            }
        }
    }

    fun compile() {
        viewModelScope.launch {
            val ks = konstructionService ?: return@launch
            _state.value = UiState.COMPILING
            _messages.value = emptyList()
            try {
                val result = ks.compile()
                _messages.value = result.messages
                if (result.status == TaskStatus.SUCCESS) {
                    _info.value = ks.getInfo()
                }
            } catch (_: Exception) {
            }
            _state.value = UiState.DEFAULT
        }
    }

    fun build(target: String) {
        viewModelScope.launch {
            val ks = konstructionService ?: return@launch
            _state.value = UiState.EXECUTING
            try {
                val result = ks.konstruct(target)
                _messages.value = result.messages
            } catch (_: Exception) {
            }
            _state.value = UiState.DEFAULT
        }
    }

    override fun onCleared() {
        super.onCleared()
        val ks = konstructionService
        val key = listenerKey
        if (ks != null && key != null) {
            viewModelScope.launch {
                try {
                    ks.unregister(key)
                } catch (_: Exception) {
                }
            }
        }
    }
}
