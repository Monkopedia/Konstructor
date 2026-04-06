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

import com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        settingsVm: SettingsViewModel
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

        scope.launch {
            combine(
                serviceHolder.connected,
                spaceListVm.workspaces,
                spaceListVm.selectedWorkspaceId,
                settingsVm.codePaneMode
            ) { connected, workspaces, selectedWsId, mode ->
                AppStateSnapshot(
                    ready = true,
                    connected = connected,
                    workspaceCount = workspaces?.size ?: -1,
                    workspaceNames = workspaces?.map { it.name } ?: emptyList(),
                    selectedWorkspaceId = selectedWsId,
                    codePaneMode = mode.name,
                    screen = when {
                        workspaces == null -> "loading"
                        workspaces.isEmpty() -> "empty"
                        else -> "main"
                    }
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

    @Serializable
    data class AppStateSnapshot(
        val ready: Boolean = false,
        val connected: Boolean = false,
        val workspaceCount: Int = -1,
        val workspaceNames: List<String> = emptyList(),
        val selectedWorkspaceId: String? = null,
        val codePaneMode: String = "EDITOR",
        val screen: String = "loading"
    )
}

@JsFun("() => { globalThis.__konstructor = { ready: false, version: 0, state: null }; }")
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

private fun exposeAction(name: String, action: (String) -> Unit) {
    exposeActionJs(name, action)
}

@JsFun("(name, fn) => { if (!globalThis.__konstructor.actions) globalThis.__konstructor.actions = {}; globalThis.__konstructor.actions[name] = fn; }")
private external fun exposeActionJs(name: String, fn: (String) -> Unit)

