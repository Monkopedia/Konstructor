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
import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionRender
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val serviceHolder: ServiceHolder,
    private val targetDisplayRepo: TargetDisplayRepository
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

    /** Delegates to TargetDisplayRepository — exposed here for convenience. */
    val targetDisplays: StateFlow<Map<String, TargetDisplay>> = targetDisplayRepo.displays

    /** Map of target name → (render URL, color) for enabled targets with ready renders. */
    private val _enabledRenderedTargets =
        MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val enabledRenderedTargets: StateFlow<Map<String, Pair<String, String>>> =
        _enabledRenderedTargets.asStateFlow()

    private val renderPaths = MutableStateFlow<Map<String, String>>(emptyMap())

    // Render output files keep a stable URL across rebuilds (same target name →
    // same path). Without a changing token, both the enabledRenderedTargets
    // StateFlow (value-equality dedup) and the renderer's URL-keyed mesh cache
    // suppress reloading the new geometry — the model only refreshes after a
    // manual visibility toggle. A monotonic token per delivered render yields a
    // distinct URL so it actually reloads.
    private var renderGeneration = 0
    private fun freshen(path: String): String = "$path?v=${++renderGeneration}"

    private var konstructionService: KonstructionService? = null
    private var listenerKey: String? = null

    // Targets currently being auto-fetched/built via setTargetEnabled, to avoid
    // kicking off duplicate work for the same target.
    private val inFlightEnables = mutableSetOf<String>()

    init {
        // Re-derive enabled-rendered targets whenever displays change
        viewModelScope.launch {
            targetDisplayRepo.displays.collect { recomputeEnabledTargets() }
        }
    }

    fun loadKonstruction(konstruction: Konstruction) {
        viewModelScope.launch {
            _state.value = UiState.LOADING
            _renderPath.value = null
            renderPaths.value = emptyMap()
            targetDisplayRepo.activate(konstruction.workspaceId, konstruction.id)
            recomputeEnabledTargets()
            val service = serviceHolder.service.value
            if (service == null) {
                _state.value = UiState.DEFAULT
                return@launch
            }
            try {
                val ks = service.konstruction(konstruction)
                konstructionService = ks

                // STL files: show directly in 3D pane, no editor content
                if (konstruction.type == KonstructionType.STL) {
                    _content.value = ""
                    _info.value = null
                    _state.value = UiState.DEFAULT
                    _renderPath.value =
                        "model/${konstruction.workspaceId}/${konstruction.id}/content.csgs"
                    return@launch
                }

                _content.value = ks.fetch()
                _info.value = ks.getInfo()
                _state.value = UiState.DEFAULT
                registerListener(ks)
                val info = _info.value
                if (info != null) {
                    targetDisplayRepo.mergeTargets(info.targets.map { it.name })
                }
                // If already CLEAN, fetch existing render paths in parallel;
                // otherwise trigger a compile/build cycle.
                if (info != null && info.dirtyState == DirtyState.CLEAN) {
                    val cleanTargets = info.targets.filter { it.state == DirtyState.CLEAN }
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
                    val newPaths = renderPaths.value.toMutableMap()
                    for ((name, path) in paths) {
                        if (path != null) {
                            val fresh = freshen(path)
                            newPaths[name] = fresh
                            _renderPath.value = fresh
                        }
                    }
                    renderPaths.value = newPaths
                    recomputeEnabledTargets()
                } else {
                    autoCompileAndBuild(ks)
                }
            } catch (_: Exception) {
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
                override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> =
                    listOf(
                        KonstructionCallbacks.INFO_CHANGE,
                        KonstructionCallbacks.DIRTY_CHANGE,
                        KonstructionCallbacks.CONTENT_CHANGE,
                        KonstructionCallbacks.TASK_COMPLETE,
                        KonstructionCallbacks.RENDER_CHANGE
                    )

                override suspend fun onInfoChanged(info: KonstructionInfo) {
                    _info.value = info
                    targetDisplayRepo.mergeTargets(info.targets.map { it.name })
                    when (info.dirtyState) {
                        DirtyState.NEEDS_EXEC -> {
                            val targets = enabledTargets(info.targets.map { it.name })
                            if (targets.isEmpty()) {
                                _state.value = UiState.DEFAULT
                            } else {
                                _state.value = UiState.EXECUTING
                                try {
                                    ks.requestKonstructs(targets)
                                } catch (_: Exception) {
                                    _state.value = UiState.DEFAULT
                                }
                            }
                        }

                        DirtyState.CLEAN -> {
                            _state.value = UiState.DEFAULT
                            val cleanTargets = info.targets.filter { it.state == DirtyState.CLEAN }
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
                            val newPaths = renderPaths.value.toMutableMap()
                            for ((name, path) in paths) {
                                if (path != null) {
                                    val fresh = freshen(path)
                                    newPaths[name] = fresh
                                    _renderPath.value = fresh
                                }
                            }
                            renderPaths.value = newPaths
                            recomputeEnabledTargets()
                        }

                        else -> { /* other dirty states: no-op */ }
                    }
                }

                override suspend fun onDirtyStateChanged(state: DirtyState) {
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
                    val path = render.renderPath
                    if (path != null) {
                        val fresh = freshen(path)
                        _renderPath.value = fresh
                        renderPaths.value = renderPaths.value + (render.name to fresh)
                    } else {
                        _renderPath.value = null
                        renderPaths.value = renderPaths.value - render.name
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

            // Auto-build the enabled export targets after a successful compile.
            // Don't set DEFAULT here — onTaskComplete callback will do it (unless
            // there's nothing to build, in which case settle the state now).
            val targets = enabledTargets(_info.value?.targets?.map { it.name } ?: emptyList())
            if (targets.isEmpty()) {
                _state.value = UiState.DEFAULT
            } else {
                _state.value = UiState.EXECUTING
                try {
                    ks.requestKonstructs(targets)
                } catch (_: Exception) {
                    _state.value = UiState.DEFAULT
                }
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

    suspend fun getKonstructedPath(target: String): String? =
        konstructionService?.konstructed(target)

    fun setTargetEnabled(name: String, enabled: Boolean) {
        // Update the toggle state immediately.
        targetDisplayRepo.setEnabled(name, enabled)

        // Enabling a target that has no current render: fetch the existing render
        // if one is already built, otherwise build it so it shows up. (Disabling
        // skips builds, so a previously-disabled target may never have been built.)
        if (!enabled) return
        if (renderPaths.value.containsKey(name)) return
        val ks = konstructionService ?: return
        if (!inFlightEnables.add(name)) return

        viewModelScope.launch {
            try {
                val existing = try {
                    ks.konstructed(name)
                } catch (_: Exception) {
                    null
                }
                if (existing != null) {
                    // Render already exists — surface it without rebuilding.
                    val fresh = freshen(existing)
                    renderPaths.value = renderPaths.value + (name to fresh)
                    _renderPath.value = fresh
                    recomputeEnabledTargets()
                } else {
                    // Not built yet — build it. onRenderChanged will populate
                    // renderPaths, and onTaskComplete resets _state to DEFAULT.
                    _state.value = UiState.EXECUTING
                    try {
                        val result = ks.konstruct(name)
                        _messages.value = result.messages
                    } catch (_: Exception) {
                        _state.value = UiState.DEFAULT
                    }
                }
            } finally {
                inFlightEnables.remove(name)
            }
        }
    }

    fun setTargetColor(name: String, color: String) = targetDisplayRepo.setColor(name, color)

    /**
     * Restrict a build request to the targets the user has enabled for display —
     * disabling a target should also skip rebuilding it on subsequent saves.
     * Targets not yet tracked (e.g. just created by an edit) default to enabled.
     */
    private fun enabledTargets(targets: List<String>): List<String> {
        val displays = targetDisplayRepo.displays.value
        return targets.filter { displays[it]?.isEnabled ?: true }
    }

    private fun recomputeEnabledTargets() {
        val displays = targetDisplayRepo.displays.value
        val paths = renderPaths.value
        _enabledRenderedTargets.value = displays
            .filter { (_, d) -> d.isEnabled }
            .mapNotNull { (name, d) ->
                val path = paths[name] ?: return@mapNotNull null
                name to (path to d.color)
            }
            .toMap()
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
