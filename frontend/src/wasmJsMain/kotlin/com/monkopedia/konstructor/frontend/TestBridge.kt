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
import com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exposes app state to `globalThis.__konstructor` for Playwright e2e testing.
 *
 * Playwright reads state via:
 *   page.evaluate(() => globalThis.__konstructor.state)
 *   page.evaluate(() => globalThis.__konstructor.ready)
 *   page.evaluate(() => globalThis.__konstructor.version)
 *
 * Keyboard input goes through the canvas:
 *   document.body.shadowRoot.querySelector("canvas").focus()
 *   page.keyboard.type("text")
 */
object TestBridge {
    private val json = Json { ignoreUnknownKeys = true }

    fun install(
        scope: CoroutineScope,
        serviceHolder: ServiceHolder,
        spaceListVm: SpaceListViewModel,
        settingsVm: SettingsViewModel,
        konstructionVm: com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel? = null,
        workspaceVm: com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel? = null
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
                com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode.valueOf(mode)
            )
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
                        String.serializer(), content
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
                    setLastResult(json.encodeToString(
                        com.monkopedia.konstructor.common.TaskResult.serializer(), result
                    ))
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
                    setLastResult(json.encodeToString(
                        com.monkopedia.konstructor.common.TaskResult.serializer(), result
                    ))
                    incrementVersion()
                } catch (e: Exception) {
                    setError("build failed: ${e.message}")
                }
            }
        }
        exposeAction("navigate") { mode ->
            settingsVm.setCodePaneMode(
                com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode.valueOf(mode)
            )
        }
        exposeAction("selectKonstruction") { konId ->
            workspaceVm?.selectKonstruction(konId)
        }
        exposeAction("triggerSave") { _ ->
            scope.launch {
                try {
                    val content = konstructionVm?.content?.value ?: ""
                    com.monkopedia.konstructor.frontend.threejs.consoleLog(
                        "triggerSave bridge action: content.length=${content.length}"
                    )
                    konstructionVm?.save(content)
                    incrementVersion()
                } catch (e: Exception) {
                    setError("triggerSave failed: ${e.message}")
                }
            }
        }

        val refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)
        refreshTriggerRef = refreshTrigger

        scope.launch {
            combine(
                serviceHolder.connected,
                spaceListVm.workspaces,
                spaceListVm.selectedWorkspaceId,
                settingsVm.codePaneMode,
                refreshTrigger
            ) { connected, workspaces, selectedWsId, mode, _ ->
                // Fetch konstructions for the selected workspace if possible
                val konNames = if (selectedWsId != null && connected) {
                    try {
                        val service = serviceHolder.service.value
                        if (service != null) {
                            val ws = service.get(selectedWsId)
                            ws.list().map { it.name }
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                AppStateSnapshot(
                    ready = true,
                    connected = connected,
                    workspaceCount = workspaces?.size ?: -1,
                    workspaceNames = workspaces?.map { it.name } ?: emptyList(),
                    workspaceIds = workspaces?.map { it.id } ?: emptyList(),
                    selectedWorkspaceId = selectedWsId,
                    codePaneMode = mode.name,
                    screen = when {
                        workspaces == null -> "loading"
                        workspaces.isEmpty() -> "empty"
                        else -> "main"
                    },
                    konstructionCount = konNames.size,
                    konstructionNames = konNames
                )
            }.collectLatest { state ->
                try {
                    val stateJson = json.encodeToString(
                        AppStateSnapshot.serializer(), state
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

    @Serializable
    data class AppStateSnapshot(
        val ready: Boolean = false,
        val connected: Boolean = false,
        val workspaceCount: Int = -1,
        val workspaceNames: List<String> = emptyList(),
        val workspaceIds: List<String> = emptyList(),
        val selectedWorkspaceId: String? = null,
        val codePaneMode: String = "EDITOR",
        val screen: String = "loading",
        val konstructionCount: Int = 0,
        val konstructionNames: List<String> = emptyList()
    )
}

@JsFun("() => { globalThis.__konstructor = { ready: false, version: 0, state: null, lastResult: null }; }")
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

private fun exposeAction(name: String, action: (String) -> Unit) {
    exposeActionJs(name, action)
}

@JsFun("(name, fn) => { if (!globalThis.__konstructor.actions) globalThis.__konstructor.actions = {}; globalThis.__konstructor.actions[name] = fn; }")
private external fun exposeActionJs(name: String, fn: (String) -> Unit)
