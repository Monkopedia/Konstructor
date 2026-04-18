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
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class UiState {
    LOADING,
    DEFAULT,
    SAVING,
    COMPILING,
    EXECUTING
}

@Serializable
data class TargetDisplay(
    val name: String,
    val color: String = "#ffffff",
    val isEnabled: Boolean = true
)

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

    private val _targetDisplays = MutableStateFlow<Map<String, TargetDisplay>>(emptyMap())
    val targetDisplays: StateFlow<Map<String, TargetDisplay>> = _targetDisplays.asStateFlow()

    /** Map of target name → (render URL, color) for enabled targets with ready renders. */
    private val _enabledRenderedTargets = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val enabledRenderedTargets: StateFlow<Map<String, Pair<String, String>>> =
        _enabledRenderedTargets.asStateFlow()

    private val _renderPaths = MutableStateFlow<Map<String, String>>(emptyMap())

    private var konstructionService: KonstructionService? = null
    private var listenerKey: String? = null
    private var currentKonstructionKey: String? = null

    private val storage = window.localStorage
    private val json = Json { ignoreUnknownKeys = true }

    fun loadKonstruction(konstruction: Konstruction) {
        com.monkopedia.konstructor.frontend.threejs.consoleLog(
            "loadKonstruction(${konstruction.name}) serviceHolder.service.value=${serviceHolder.service.value != null}"
        )
        viewModelScope.launch {
            _state.value = UiState.LOADING
            _renderPath.value = null
            _renderPaths.value = emptyMap()
            currentKonstructionKey = "${konstruction.workspaceId}.${konstruction.id}"
            // Load saved target displays for this konstruction
            _targetDisplays.value = loadTargetDisplays(currentKonstructionKey!!)
            recomputeEnabledTargets()
            val service = serviceHolder.service.value
            if (service == null) {
                com.monkopedia.konstructor.frontend.threejs.consoleError(
                    "loadKonstruction failed: service is null"
                )
                _state.value = UiState.DEFAULT
                return@launch
            }
            try {
                com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: getting service...")
                val ks = service.konstruction(konstruction)
                konstructionService = ks

                // STL files: show directly in 3D pane, no editor content
                if (konstruction.type == com.monkopedia.konstructor.common.KonstructionType.STL) {
                    _content.value = ""
                    _info.value = null
                    _state.value = UiState.DEFAULT
                    val stlPath = "model/${konstruction.workspaceId}/${konstruction.id}/content.csgs"
                    com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: STL file, renderPath=$stlPath")
                    _renderPath.value = stlPath
                    return@launch
                }

                com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: fetching content...")
                _content.value = ks.fetch()
                com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: content length=${_content.value.length}")
                _info.value = ks.getInfo()
                com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: got info, dirtyState=${_info.value?.dirtyState}")
                _state.value = UiState.DEFAULT
                registerListener(ks)
                com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: listener registered")
                // Initialize target displays from info
                val info = _info.value
                if (info != null) {
                    mergeTargetDisplays(info.targets.map { it.name })
                }
                // If already CLEAN with targets, fetch existing render paths in parallel
                if (info != null && info.dirtyState == com.monkopedia.konstructor.common.DirtyState.CLEAN) {
                    val cleanTargets = info.targets.filter {
                        it.state == com.monkopedia.konstructor.common.DirtyState.CLEAN
                    }
                    val paths = coroutineScope {
                        cleanTargets.map { target ->
                            async {
                                try {
                                    target.name to ks.konstructed(target.name)
                                } catch (_: Exception) {
                                    target.name to null
                                }
                            }
                        }.awaitAll()
                    }
                    val newPaths = _renderPaths.value.toMutableMap()
                    for ((name, path) in paths) {
                        if (path != null) {
                            newPaths[name] = path
                            _renderPath.value = path
                        }
                    }
                    _renderPaths.value = newPaths
                    com.monkopedia.konstructor.frontend.threejs.consoleLog(
                        "loadKonstruction: fetched ${paths.count { it.second != null }} render paths"
                    )
                    recomputeEnabledTargets()
                } else {
                    // Not clean — trigger compile/build
                    com.monkopedia.konstructor.frontend.threejs.consoleLog("loadKonstruction: starting auto-compile")
                    autoCompileAndBuild(ks)
                }
            } catch (e: Exception) {
                com.monkopedia.konstructor.frontend.threejs.consoleError("loadKonstruction failed: ${e.message}")
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
                    mergeTargetDisplays(info.targets.map { it.name })
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
                        // Update render paths for clean targets (in parallel)
                        val cleanTargets = info.targets.filter {
                            it.state == com.monkopedia.konstructor.common.DirtyState.CLEAN
                        }
                        val paths = coroutineScope {
                            cleanTargets.map { target ->
                                async {
                                    try {
                                        target.name to ks.konstructed(target.name)
                                    } catch (_: Exception) {
                                        target.name to null
                                    }
                                }
                            }.awaitAll()
                        }
                        val newPaths = _renderPaths.value.toMutableMap()
                        for ((name, path) in paths) {
                            if (path != null) {
                                newPaths[name] = path
                                _renderPath.value = path
                            }
                        }
                        _renderPaths.value = newPaths
                        recomputeEnabledTargets()
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
                    val path = render.renderPath
                    if (path != null) {
                        _renderPaths.value = _renderPaths.value + (render.name to path)
                    } else {
                        _renderPaths.value = _renderPaths.value - render.name
                    }
                    recomputeEnabledTargets()
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
        com.monkopedia.konstructor.frontend.threejs.consoleLog(
            "KonstructionViewModel.save() called, konstructionService=${konstructionService != null}, text.length=${text.length}"
        )
        viewModelScope.launch {
            val ks = konstructionService
            if (ks == null) {
                com.monkopedia.konstructor.frontend.threejs.consoleError(
                    "KonstructionViewModel.save() failed: konstructionService is null"
                )
                return@launch
            }
            com.monkopedia.konstructor.frontend.threejs.consoleLog("save: setting content...")
            _state.value = UiState.SAVING
            try {
                ks.set(text)
                _content.value = text
                com.monkopedia.konstructor.frontend.threejs.consoleLog("save: content set, compiling...")
            } catch (e: Exception) {
                com.monkopedia.konstructor.frontend.threejs.consoleError("save: set failed: ${e.message}")
                _state.value = UiState.DEFAULT
                return@launch
            }

            // Auto-compile after save
            _state.value = UiState.COMPILING
            _messages.value = emptyList()
            try {
                val compileResult = ks.compile()
                _messages.value = compileResult.messages
                com.monkopedia.konstructor.frontend.threejs.consoleLog("save: compile result=${compileResult.status}, messages=${compileResult.messages.size}")
                if (compileResult.status != TaskStatus.SUCCESS) {
                    _state.value = UiState.DEFAULT
                    return@launch
                }
                _info.value = ks.getInfo()
            } catch (e: Exception) {
                com.monkopedia.konstructor.frontend.threejs.consoleError("save: compile failed: ${e.message}")
                _state.value = UiState.DEFAULT
                return@launch
            }

            // Auto-build all export targets after successful compile
            _state.value = UiState.EXECUTING
            com.monkopedia.konstructor.frontend.threejs.consoleLog("save: building targets...")
            try {
                val info = _info.value
                val targets = info?.targets?.map { it.name } ?: emptyList()
                ks.requestKonstructs(targets)
                com.monkopedia.konstructor.frontend.threejs.consoleLog("save: build requested for ${targets.size} targets")
                // Don't set DEFAULT here — onTaskComplete callback will do it
            } catch (e: Exception) {
                com.monkopedia.konstructor.frontend.threejs.consoleError("save: build failed: ${e.message}")
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

    fun setTargetEnabled(name: String, enabled: Boolean) {
        updateTargetDisplay(name) { it.copy(isEnabled = enabled) }
    }

    fun setTargetColor(name: String, color: String) {
        updateTargetDisplay(name) { it.copy(color = color) }
    }

    private fun updateTargetDisplay(name: String, transform: (TargetDisplay) -> TargetDisplay) {
        val current = _targetDisplays.value
        val existing = current[name] ?: TargetDisplay(name = name)
        _targetDisplays.value = current + (name to transform(existing))
        saveTargetDisplays()
        recomputeEnabledTargets()
    }

    private fun mergeTargetDisplays(names: List<String>) {
        val current = _targetDisplays.value.toMutableMap()
        var changed = false
        for (name in names) {
            if (name !in current) {
                current[name] = TargetDisplay(name = name)
                changed = true
            }
        }
        // Remove displays for targets that no longer exist
        val toRemove = current.keys - names.toSet()
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { current.remove(it) }
            changed = true
        }
        if (changed) {
            _targetDisplays.value = current
            saveTargetDisplays()
            recomputeEnabledTargets()
        }
    }

    private fun recomputeEnabledTargets() {
        val displays = _targetDisplays.value
        val paths = _renderPaths.value
        _enabledRenderedTargets.value = displays
            .filter { (_, d) -> d.isEnabled }
            .mapNotNull { (name, d) ->
                val path = paths[name] ?: return@mapNotNull null
                name to (path to d.color)
            }
            .toMap()
    }

    private fun loadTargetDisplays(key: String): Map<String, TargetDisplay> {
        val raw = storage.getItem("konstructor.targets.$key") ?: return emptyMap()
        return try {
            json.decodeFromString(
                MapSerializer(String.serializer(), TargetDisplay.serializer()),
                raw
            )
        } catch (t: Throwable) {
            // Corrupt or incompatible — fall back to empty in memory.
            // Leave localStorage alone in case a rollback can still read it.
            emptyMap()
        }
    }

    private fun saveTargetDisplays() {
        val key = currentKonstructionKey ?: return
        val jsonStr = json.encodeToString(
            MapSerializer(String.serializer(), TargetDisplay.serializer()),
            _targetDisplays.value
        )
        storage.setItem("konstructor.targets.$key", jsonStr)
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
