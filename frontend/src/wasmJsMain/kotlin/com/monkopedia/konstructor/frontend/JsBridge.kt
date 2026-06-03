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
package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode
import com.monkopedia.konstructor.frontend.viewmodel.EditorThemeName
import com.monkopedia.konstructor.frontend.viewmodel.KeymapName
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bridges app state and mutating actions to JavaScript via
 * `globalThis.__konstructor`. Used for:
 *  - Playwright e2e tests (read state, invoke actions).
 *  - Any non-Compose JS caller that needs to observe or mutate state.
 *
 * Read state:
 *   globalThis.__konstructor.state      // AppStateSnapshot JSON
 *   globalThis.__konstructor.version    // bumps on every change
 *   globalThis.__konstructor.ready      // true once first state emitted
 *
 * Invoke an action:
 *   globalThis.__konstructor.actions.<name>(jsonArg)
 *
 * See BridgeStateSnapshot.kt for the exported state shape.
 */
object JsBridge {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun install(
        scope: CoroutineScope,
        serviceHolder: ServiceHolder,
        spaceListVm: SpaceListViewModel,
        settingsVm: SettingsViewModel,
        konstructionVm: KonstructionViewModel? = null,
        workspaceVm: WorkspaceViewModel? = null
    ) {
        initBridge()

        // Expose action callbacks for Playwright to call
        exposeAction("createWorkspace") { name ->
            scope.launch {
                val space = spaceListVm.createWorkspace(name)
                if (space != null) {
                    spaceListVm.selectWorkspace(space.id)
                }
            }
        }
        exposeAction("selectWorkspace") { id ->
            spaceListVm.selectWorkspace(id)
        }
        exposeAction("setCodePaneMode") { mode ->
            settingsVm.setCodePaneMode(
                CodePaneMode.valueOf(mode)
            )
        }
        exposeAction("setEditorTheme") { theme ->
            settingsVm.setEditorTheme(
                EditorThemeName.valueOf(theme)
            )
            incrementVersion()
        }
        exposeAction("setKeymap") { keymap ->
            settingsVm.setKeymap(
                KeymapName.valueOf(keymap)
            )
            incrementVersion()
        }
        exposeAction("setLspEnabled") { enabled ->
            settingsVm.setLspEnabled(enabled.toBoolean())
            incrementVersion()
        }
        exposeAction("setTargetEnabled") { argJson ->
            try {
                val args = json.decodeFromString<JsonObject>(argJson)
                val name = args["name"]!!.jsonPrimitive.content
                val enabled = args["enabled"]!!.jsonPrimitive.content.toBoolean()
                konstructionVm?.setTargetEnabled(name, enabled)
                incrementVersion()
            } catch (e: Exception) {
                setError("setTargetEnabled failed: ${e.message}")
            }
        }
        exposeAction("setTargetColor") { argJson ->
            try {
                val args = json.decodeFromString<JsonObject>(argJson)
                val name = args["name"]!!.jsonPrimitive.content
                val color = args["color"]!!.jsonPrimitive.content
                konstructionVm?.setTargetColor(name, color)
                incrementVersion()
            } catch (e: Exception) {
                setError("setTargetColor failed: ${e.message}")
            }
        }
        exposeAction("deleteWorkspace") { id ->
            scope.launch {
                spaceListVm.deleteWorkspace(Space(id = id, name = ""))
            }
        }
        exposeAction("renameWorkspace") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val id = args["id"]!!.jsonPrimitive.content
                    val name = args["name"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(id)
                    ws.setName(name)
                    spaceListVm.refreshWorkspaces()
                } catch (e: Exception) {
                    setError("renameWorkspace failed: ${e.message}")
                }
            }
        }
        exposeAction("createKonstruction") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val name = args["name"]!!.jsonPrimitive.content
                    val workspaceId = args["workspaceId"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(workspaceId)
                    ws.create(Konstruction(name = name, workspaceId = workspaceId, id = ""))
                    // Refresh state so konstructions list updates
                    refreshKonstructions(serviceHolder, spaceListVm)
                } catch (e: Exception) {
                    setError("createKonstruction failed: ${e.message}")
                }
            }
        }
        exposeAction("deleteKonstruction") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    // Find the konstruction by id
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId }
                    if (kon != null) {
                        ws.delete(kon)
                        refreshKonstructions(serviceHolder, spaceListVm)
                    }
                } catch (e: Exception) {
                    setError("deleteKonstruction failed: ${e.message}")
                }
            }
        }
        exposeAction("renameKonstruction") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val name = args["name"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId } ?: return@launch
                    val ks = service.konstruction(kon)
                    ks.setName(name)
                    refreshKonstructions(serviceHolder, spaceListVm)
                } catch (e: Exception) {
                    setError("renameKonstruction failed: ${e.message}")
                }
            }
        }
        exposeAction("setContent") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val content = args["content"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId } ?: return@launch
                    val ks = service.konstruction(kon)
                    ks.set(content)
                    incrementVersion()
                } catch (e: Exception) {
                    setError("setContent failed: ${e.message}")
                }
            }
        }
        exposeAction("getContent") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId } ?: return@launch
                    val ks = service.konstruction(kon)
                    val content = ks.fetch()
                    // Wrap content as a JSON string for lastResult
                    val contentJson = json.encodeToString(
                        String.serializer(),
                        content
                    )
                    setLastResult(contentJson)
                    incrementVersion()
                } catch (e: Exception) {
                    setError("getContent failed: ${e.message}")
                }
            }
        }
        exposeAction("compile") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId } ?: return@launch
                    val ks = service.konstruction(kon)
                    val result = ks.compile()
                    setLastResult(
                        json.encodeToString(
                            com.monkopedia.konstructor.common.TaskResult.serializer(),
                            result
                        )
                    )
                    incrementVersion()
                } catch (e: Exception) {
                    setError("compile failed: ${e.message}")
                }
            }
        }
        exposeAction("build") { argJson ->
            scope.launch {
                try {
                    val args = json.decodeFromString<JsonObject>(argJson)
                    val wsId = args["wsId"]!!.jsonPrimitive.content
                    val konId = args["konId"]!!.jsonPrimitive.content
                    val target = args["target"]!!.jsonPrimitive.content
                    val service = serviceHolder.service.value ?: return@launch
                    val ws = service.get(wsId)
                    val kons = ws.list()
                    val kon = kons.firstOrNull { it.id == konId } ?: return@launch
                    val ks = service.konstruction(kon)
                    val result = ks.konstruct(target)
                    setLastResult(
                        json.encodeToString(
                            com.monkopedia.konstructor.common.TaskResult.serializer(),
                            result
                        )
                    )
                    incrementVersion()
                } catch (e: Exception) {
                    setError("build failed: ${e.message}")
                }
            }
        }
        exposeAction("navigate") { mode ->
            settingsVm.setCodePaneMode(
                CodePaneMode.valueOf(mode)
            )
        }
        exposeAction("selectKonstruction") { konId ->
            workspaceVm?.selectKonstruction(konId)
        }
        exposeAction("triggerSave") { _ ->
            scope.launch {
                try {
                    val content = konstructionVm?.content?.value ?: ""
                    konstructionVm?.save(content)
                    incrementVersion()
                } catch (e: Exception) {
                    setError("triggerSave failed: ${e.message}")
                }
            }
        }
        // Move the editor cursor to a 1-based line (issue #33). The editor draws
        // to a canvas with no DOM, so the e2e harness drives cursor moves through
        // this action rather than Playwright clicks. The selection listener then
        // updates cursorLine/footerError, which feeds back into the snapshot.
        exposeAction("moveCursorToLine") { lineStr ->
            try {
                konstructionVm?.moveCursorToLine(lineStr.trim().toInt())
                incrementVersion()
            } catch (e: Exception) {
                setError("moveCursorToLine failed: ${e.message}")
            }
        }

        val refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)
        refreshTriggerRef = refreshTrigger

        // Refresh the bridge state whenever reactive inputs not in the main
        // combine change (settings values + target displays).
        scope.launch {
            settingsVm.codePaneMode.collect { refreshTrigger.value++ }
        }
        scope.launch {
            settingsVm.editorTheme.collect { refreshTrigger.value++ }
        }
        scope.launch {
            settingsVm.keymap.collect { refreshTrigger.value++ }
        }
        if (konstructionVm != null) {
            scope.launch {
                konstructionVm.targetDisplays.collect { refreshTrigger.value++ }
            }
            // Re-snapshot when diagnostics or the cursor's active error change so
            // the error-footer surface (issue #33) stays current for e2e reads.
            scope.launch {
                konstructionVm.messages.collect { refreshTrigger.value++ }
            }
            scope.launch {
                konstructionVm.cursorLine.collect { refreshTrigger.value++ }
            }
            scope.launch {
                konstructionVm.activeMessage.collect { refreshTrigger.value++ }
            }
        }

        // Cache konstruction names — only re-fetches when workspace selection
        // or connection changes, not on every refresh.
        val konNamesCache = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
        scope.launch {
            combine(
                serviceHolder.connected,
                spaceListVm.selectedWorkspaceId
            ) { connected, wsId -> connected to wsId }.collect { pair ->
                val connected = pair.first
                val wsId = pair.second
                val names = if (wsId != null && connected) {
                    try {
                        val service = serviceHolder.service.value
                        service?.get(wsId)?.list()?.map { it.name } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                konNamesCache.value = names
            }
        }

        scope.launch {
            combine(
                serviceHolder.connected,
                spaceListVm.workspaces,
                spaceListVm.selectedWorkspaceId,
                konNamesCache,
                refreshTrigger
            ) { connected, workspaces, selectedWsId, konNames, _ ->
                buildSnapshot(
                    connected = connected,
                    workspaces = workspaces,
                    selectedWsId = selectedWsId,
                    konNames = konNames,
                    settingsVm = settingsVm,
                    konstructionVm = konstructionVm
                )
            }.collectLatest { state ->
                try {
                    val stateJson = json.encodeToString(
                        AppStateSnapshot.serializer(),
                        state
                    )
                    setState(stateJson)
                    setReady(true)
                    incrementVersion()
                } catch (e: Exception) {
                    setError(e.message ?: "unknown error")
                }
            }
        }
    }

    private fun buildSnapshot(
        connected: Boolean,
        workspaces: List<Space>?,
        selectedWsId: String?,
        konNames: List<String>,
        settingsVm: SettingsViewModel,
        konstructionVm: KonstructionViewModel?
    ): AppStateSnapshot {
        val targets = konstructionVm?.targetDisplays?.value?.values?.map { display ->
            TargetSnapshot(
                name = display.name,
                color = display.color,
                isEnabled = display.isEnabled
            )
        } ?: emptyList()
        val diagnostics = konstructionVm?.messages?.value
            ?.mapNotNull { msg ->
                val line = msg.line ?: return@mapNotNull null
                DiagnosticSnapshot(
                    line = line,
                    message = msg.message,
                    importance = msg.importance.name
                )
            } ?: emptyList()
        return AppStateSnapshot(
            ready = true,
            connected = connected,
            workspaceCount = workspaces?.size ?: -1,
            workspaceNames = workspaces?.map { it.name } ?: emptyList(),
            workspaceIds = workspaces?.map { it.id } ?: emptyList(),
            selectedWorkspaceId = selectedWsId,
            codePaneMode = settingsVm.codePaneMode.value.name,
            editorTheme = settingsVm.editorTheme.value.name,
            keymap = settingsVm.keymap.value.name,
            screen = when {
                workspaces == null -> "loading"
                workspaces.isEmpty() -> "empty"
                else -> "main"
            },
            konstructionCount = konNames.size,
            konstructionNames = konNames,
            targets = targets,
            diagnostics = diagnostics,
            cursorLine = konstructionVm?.cursorLine?.value ?: 0,
            footerError = konstructionVm?.activeMessage?.value?.message
        )
    }

    private var refreshTriggerRef: kotlinx.coroutines.flow.MutableStateFlow<Int>? = null

    private suspend fun refreshKonstructions(
        serviceHolder: ServiceHolder,
        spaceListVm: SpaceListViewModel
    ) {
        // Directly update the state snapshot with current konstruction list
        spaceListVm.refreshWorkspaces()
        kotlinx.coroutines.delay(500)
        try {
            val service = serviceHolder.service.value
            val wsId = spaceListVm.selectedWorkspaceId.value
            if (service != null && wsId != null) {
                val ws = service.get(wsId)
                val kons = ws.list()
                val workspaces = spaceListVm.workspaces.value
                val snapshot = AppStateSnapshot(
                    ready = true,
                    connected = serviceHolder.connected.value,
                    workspaceCount = workspaces?.size ?: 0,
                    workspaceNames = workspaces?.map { it.name } ?: emptyList(),
                    workspaceIds = workspaces?.map { it.id } ?: emptyList(),
                    selectedWorkspaceId = wsId,
                    codePaneMode = "EDITOR",
                    screen = "main",
                    konstructionCount = kons.size,
                    konstructionNames = kons.map { it.name }
                )
                val stateJson = json.encodeToString(AppStateSnapshot.serializer(), snapshot)
                setState(stateJson)
            }
        } catch (e: Exception) {
            setError("refreshKonstructions state update failed: ${e.message}")
        }
        incrementVersion()
    }
}

@JsFun(
    "() => { " +
        "globalThis.__konstructor = { ready: false, version: 0, state: null, lastResult: null }; " +
        "}"
)
private external fun initBridge()

@JsFun("(v) => { globalThis.__konstructor.ready = v; }")
private external fun setReady(ready: Boolean)

@JsFun("(v) => { globalThis.__konstructor.version = v; }")
private external fun setVersion(version: Int)

@JsFun("(s) => { globalThis.__konstructor.state = JSON.parse(s); }")
private external fun setState(stateJson: String)

@JsFun("() => { globalThis.__konstructor.version = (globalThis.__konstructor.version || 0) + 1; }")
private external fun incrementVersion()

@JsFun("(s) => { globalThis.__konstructor.error = s; }")
private external fun setError(error: String)

@JsFun("(s) => { globalThis.__konstructor.lastResult = JSON.parse(s); }")
private external fun setLastResult(resultJson: String)

/**
 * Record the most recent LSP `publishDiagnostics` round-trip on the JS bridge so
 * Playwright e2e tests can assert the flag-ON LSP pipe delivered diagnostics
 * from the backend to the editor. No-op effect on the editor itself — this is
 * purely an observation hook (see EditorPane's LSP wiring).
 */
fun reportLspDiagnostics(uri: String, count: Int) {
    setLspDiagnostics(uri, count)
    incrementVersion()
}

@JsFun(
    "(uri, count) => { " +
        "globalThis.__konstructor.lspDiagnostics = { uri: uri, count: count }; " +
        "}"
)
private external fun setLspDiagnostics(uri: String, count: Int)

private fun exposeAction(name: String, action: (String) -> Unit) {
    exposeActionJs(name, action)
}

@JsFun(
    "(name, fn) => { " +
        "if (!globalThis.__konstructor.actions) globalThis.__konstructor.actions = {}; " +
        "globalThis.__konstructor.actions[name] = fn; " +
        "}"
)
private external fun exposeActionJs(name: String, fn: (String) -> Unit)
