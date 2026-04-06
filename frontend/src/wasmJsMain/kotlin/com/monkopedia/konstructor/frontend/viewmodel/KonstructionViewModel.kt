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

    private val _renderPath = MutableStateFlow<String?>(null)
    val renderPath: StateFlow<String?> = _renderPath.asStateFlow()

    private var konstructionService: KonstructionService? = null
    private var listenerKey: String? = null

    fun loadKonstruction(konstruction: Konstruction) {
        viewModelScope.launch {
            _state.value = UiState.LOADING
            _renderPath.value = null
            val service = serviceHolder.service.value ?: return@launch
            try {
                val ks = service.konstruction(konstruction)
                konstructionService = ks
                _content.value = ks.fetch()
                _info.value = ks.getInfo()
                _state.value = UiState.DEFAULT
                registerListener(ks)
                // Auto-compile and build on load (matching old behavior)
                autoCompileAndBuild(ks)
            } catch (e: Exception) {
                _state.value = UiState.DEFAULT
            }
        }
    }

    private fun autoCompileAndBuild(ks: KonstructionService) {
        viewModelScope.launch {
            try {
                _state.value = UiState.COMPILING
                ks.requestCompile()
                // requestCompile is async on server — the listener will get
                // task complete and info change callbacks
            } catch (_: Exception) {
                _state.value = UiState.DEFAULT
            }
        }
    }

    private suspend fun registerListener(ks: KonstructionService) {
        try {
            val listener = object : KonstructionListener {
                override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> {
                    return listOf(
                        KonstructionCallbacks.INFO_CHANGE,
                        KonstructionCallbacks.DIRTY_CHANGE,
                        KonstructionCallbacks.CONTENT_CHANGE,
                        KonstructionCallbacks.TASK_COMPLETE,
                        KonstructionCallbacks.RENDER_CHANGE
                    )
                }

                override suspend fun onInfoChanged(info: KonstructionInfo) {
                    _info.value = info
                    // Auto-build when compile succeeds and targets need execution
                    if (info.dirtyState == com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC) {
                        _state.value = UiState.EXECUTING
                        try {
                            ks.requestKonstructs(info.targets.map { it.name })
                        } catch (_: Exception) {
                            _state.value = UiState.DEFAULT
                        }
                    } else if (info.dirtyState == com.monkopedia.konstructor.common.DirtyState.CLEAN) {
                        _state.value = UiState.DEFAULT
                        // Update render paths for clean targets
                        info.targets.filter {
                            it.state == com.monkopedia.konstructor.common.DirtyState.CLEAN
                        }.forEach { target ->
                            try {
                                val path = ks.konstructed(target.name)
                                if (path != null) {
                                    _renderPath.value = path
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                override suspend fun onDirtyStateChanged(
                    state: com.monkopedia.konstructor.common.DirtyState
                ) {
                    _info.value = _info.value?.copy(dirtyState = state)
                }

                override suspend fun onTargetChanged(target: KonstructionTarget) {
                    val info = _info.value ?: return
                    val newTargets = info.targets.map {
                        if (it.name == target.name) target else it
                    }
                    _info.value = info.copy(targets = newTargets)
                }

                override suspend fun onRenderChanged(render: KonstructionRender) {
                    _renderPath.value = render.renderPath
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

    /**
     * Save content, then auto-compile and auto-build (matching old behavior).
     * Flow: save → compile → build all exports → update renders
     */
    fun save(text: String) {
        viewModelScope.launch {
            val ks = konstructionService ?: return@launch
            _state.value = UiState.SAVING
            try {
                ks.set(text)
                _content.value = text
            } catch (_: Exception) {
                _state.value = UiState.DEFAULT
                return@launch
            }

            // Auto-compile after save
            _state.value = UiState.COMPILING
            _messages.value = emptyList()
            try {
                val compileResult = ks.compile()
                _messages.value = compileResult.messages
                if (compileResult.status != TaskStatus.SUCCESS) {
                    _state.value = UiState.DEFAULT
                    return@launch
                }
                _info.value = ks.getInfo()
            } catch (_: Exception) {
                _state.value = UiState.DEFAULT
                return@launch
            }

            // Auto-build all export targets after successful compile
            _state.value = UiState.EXECUTING
            try {
                val info = _info.value
                val targets = info?.targets?.map { it.name } ?: emptyList()
                if (targets.isNotEmpty()) {
                    ks.requestKonstructs(targets)
                } else {
                    // No known targets — request build with empty list
                    // which builds all exports
                    ks.requestKonstructs(emptyList())
                }
                // Don't set DEFAULT here — onTaskComplete callback will do it
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

    suspend fun getKonstructedPath(target: String): String? {
        return konstructionService?.konstructed(target)
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
