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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    // The editor's current primary-cursor line (1-based; 0 = unknown / no
    // cursor yet). Pushed in from the EditorPane's selection listener so the
    // context-aware footer (and the e2e bridge) can map the cursor onto the
    // diagnostic, if any, covering that line.
    private val _cursorLine = MutableStateFlow(0)
    val cursorLine: StateFlow<Int> = _cursorLine.asStateFlow()

    /**
     * The compiler message whose [TaskMessage.line] matches the current cursor
     * line, or null when the cursor is not on a line that has a message. This is
     * the text the editor footer surfaces — restoring the pre-migration
     * cursor-line error behavior. Recomputed whenever messages or the cursor
     * move.
     */
    val activeMessage: StateFlow<TaskMessage?> =
        combine(_messages, _cursorLine) { messages, line ->
            if (line < 1) null else messages.firstOrNull { it.line == line }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Set by the EditorPane once a session exists so external callers (the e2e
    // bridge) can drive the cursor to a given 1-based line deterministically.
    private var cursorMover: ((Int) -> Unit)? = null

    /** Register the callback the editor uses to move its cursor to a line. */
    fun setCursorMover(mover: ((Int) -> Unit)?) {
        cursorMover = mover
    }

    /** Report the editor's current 1-based primary-cursor line. */
    fun updateCursorLine(line: Int) {
        _cursorLine.value = line
    }

    /** Drive the editor cursor to [line] (1-based), if a mover is registered. */
    fun moveCursorToLine(line: Int) {
        cursorMover?.invoke(line)
    }

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
            _cursorLine.value = 0
            renderPaths.value = emptyMap()
            // Drop any in-flight enable bookkeeping from the previous
            // konstruction — keyed by target name on this singleton, a leftover
            // entry could otherwise suppress a legitimate enable after switching.
            inFlightEnables.clear()
            // Unregister the previous konstruction's listener before wiring the
            // new one, so a late callback from the de-selected konstruction can't
            // keep mutating the now-shared flows. Capture the old refs first,
            // clear them, then unregister.
            val previousKs = konstructionService
            val previousKey = listenerKey
            konstructionService = null
            listenerKey = null
            if (previousKs != null && previousKey != null) {
                try {
                    previousKs.unregister(previousKey)
                } catch (_: Exception) {
                }
            }
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
                    applyCleanRenders(ks, info)
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

    /**
     * Run [block] only if [ks] is still the selected konstruction. Used after a
     * suspending call to drop work for a service that was de-selected while the
     * call was in flight (a switch landing mid-await).
     */
    private suspend inline fun runIfCurrent(ks: KonstructionService, block: () -> Unit) {
        if (ks === konstructionService) block()
    }

    /**
     * Fetch the render paths of [info]'s CLEAN targets in parallel and, if [ks]
     * is still selected once they resolve, publish them. Shared by the initial
     * load and the onInfoChanged CLEAN transition.
     */
    private suspend fun applyCleanRenders(ks: KonstructionService, info: KonstructionInfo) {
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
        runIfCurrent(ks) {
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
    }

    private suspend fun registerListener(ks: KonstructionService) {
        try {
            listenerKey = ks.register(guardStale(ks, listenerFor(ks)))
        } catch (_: Exception) {
        }
    }

    /**
     * The state-mutating listener for [ks]. Assumes [ks] is the current
     * selection — staleness is filtered upstream by [guardStale] — so the
     * notification callbacks here carry no identity guard of their own.
     */
    private fun listenerFor(ks: KonstructionService) = object : KonstructionListener {
        override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> = listOf(
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
                    val all = info.targets.map { it.name }
                    val targets = buildTargets(all)
                    if (targets == null) {
                        // Every known target is disabled — nothing to
                        // build, settle the UI instead of spinning.
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
                    applyCleanRenders(ks, info)
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

    /**
     * Wrap [delegate] so its notification callbacks fire only while [ks] is the
     * selected konstruction; callbacks arriving for a de-selected service (a
     * late delivery after a switch) are dropped centrally. The pure-query
     * [requestedCallbacks] is always forwarded, as it mutates no state.
     */
    private fun guardStale(ks: KonstructionService, delegate: KonstructionListener) =
        object : KonstructionListener {
            override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> =
                delegate.requestedCallbacks(u)

            override suspend fun onInfoChanged(info: KonstructionInfo) =
                runIfCurrent(ks) { delegate.onInfoChanged(info) }

            override suspend fun onDirtyStateChanged(state: DirtyState) =
                runIfCurrent(ks) { delegate.onDirtyStateChanged(state) }

            override suspend fun onTargetChanged(target: KonstructionTarget) =
                runIfCurrent(ks) { delegate.onTargetChanged(target) }

            override suspend fun onRenderChanged(render: KonstructionRender) =
                runIfCurrent(ks) { delegate.onRenderChanged(render) }

            override suspend fun onContentChange(u: Unit) =
                runIfCurrent(ks) { delegate.onContentChange(u) }

            override suspend fun onTaskComplete(taskResult: TaskResult) =
                runIfCurrent(ks) { delegate.onTaskComplete(taskResult) }
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
            // every known target is disabled, in which case settle the state now).
            val all = _info.value?.targets?.map { it.name } ?: emptyList()
            val targets = buildTargets(all)
            if (targets == null) {
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
                // A switch may have landed while konstructed() was in flight;
                // don't write the de-selected konstruction's render into the
                // now-current view.
                runIfCurrent(ks) {
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
                }
            } finally {
                inFlightEnables.remove(name)
            }
        }
    }

    fun setTargetColor(name: String, color: String) = targetDisplayRepo.setColor(name, color)

    /**
     * Decide which targets to request for a build, given the targets currently
     * known to exist (`known`).
     *
     * Returns:
     *  - `known` filtered to the user-enabled targets when at least one is
     *    enabled (untracked targets default to enabled, preserving the
     *    enabled-by-default behavior — so disabling a target skips rebuilding it);
     *  - an empty list when `known` is itself empty. A fresh konstruction has no
     *    discovered targets yet — targets are only surfaced by the server's
     *    execute pass — so we MUST still request the (empty) build to trigger
     *    discovery. Short-circuiting here was the #7 regression: it skipped the
     *    discovery build and `state.targets` never populated;
     *  - `null` only when targets are known but every one is disabled, signalling
     *    the caller to settle the UI rather than spin on an empty build.
     */
    private fun buildTargets(known: List<String>): List<String>? {
        if (known.isEmpty()) return emptyList()
        val displays = targetDisplayRepo.displays.value
        val enabled = known.filter { displays[it]?.isEnabled ?: true }
        return enabled.ifEmpty { null }
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
