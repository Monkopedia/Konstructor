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
package com.monkopedia.konstructor.e2e

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests theme switching and keymap selection.
 */
class ThemeAndKeymapTest : BaseE2eTest() {

    private val VALID_SCRIPT = """
        val simpleCube by primitive {
            cube {
                dimensions = xyz(10.0, 10.0, 10.0)
            }
        }
        export("simpleCube")
    """.trimIndent()

    private fun setupWorkspaceWithCode() {
        // Create workspace + konstruction via API
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.create(Space(id = "", name = "ThemeTestWs"))
            val workspace = service.get(ws.id)
            val kon = workspace.create(
                Konstruction(name = "ThemeTest", workspaceId = ws.id, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(VALID_SCRIPT)
        }

        // Load app — first load triggers workspace discovery, reload ensures Initializer auto-selects
        loadApp()
        waitForBridge()
        page.reload()
        waitForBridge()
        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
        // Switch to editor and wait for content to load
        bridgeAction("setCodePaneMode", "EDITOR")
        page.waitForTimeout(5000.0)
    }

    @Test
    fun testThemeSwitching() {
        setupWorkspaceWithCode()

        // Default should be Dracula
        assertEquals("DRACULA", bridgeStateString("editorTheme"))
        screenshot("theme-dracula")

        // Switch to One Dark
        bridgeAction("setEditorTheme", "ONE_DARK")
        page.waitForTimeout(3000.0)
        assertEquals("ONE_DARK", bridgeStateString("editorTheme"))
        screenshot("theme-one-dark")

        // Switch to a light theme
        bridgeAction("setEditorTheme", "SOLARIZED_LIGHT")
        page.waitForTimeout(3000.0)
        assertEquals("SOLARIZED_LIGHT", bridgeStateString("editorTheme"))
        screenshot("theme-solarized-light")

        // Switch to another dark theme
        bridgeAction("setEditorTheme", "COBALT")
        page.waitForTimeout(3000.0)
        assertEquals("COBALT", bridgeStateString("editorTheme"))
        screenshot("theme-cobalt")

        // Switch back to Dracula
        bridgeAction("setEditorTheme", "DRACULA")
        page.waitForTimeout(3000.0)
        assertEquals("DRACULA", bridgeStateString("editorTheme"))
    }

    @Test
    fun testKeymapSwitching() {
        setupWorkspaceWithCode()

        // Default should be Vim
        assertEquals("VIM", bridgeStateString("keymap"))

        // Switch to Default
        bridgeAction("setKeymap", "DEFAULT")
        page.waitForTimeout(3000.0)
        assertEquals("DEFAULT", bridgeStateString("keymap"))
        screenshot("keymap-default")

        // Switch to Emacs
        bridgeAction("setKeymap", "EMACS")
        page.waitForTimeout(3000.0)
        assertEquals("EMACS", bridgeStateString("keymap"))
        screenshot("keymap-emacs")

        // Switch back to Vim
        bridgeAction("setKeymap", "VIM")
        page.waitForTimeout(3000.0)
        assertEquals("VIM", bridgeStateString("keymap"))
        screenshot("keymap-vim")
    }

    @Test
    fun testSettingsPaneShowsDropdowns() {
        setupWorkspaceWithCode()

        // Switch to settings pane
        bridgeAction("setCodePaneMode", "SETTINGS")
        page.waitForTimeout(3000.0)
        screenshot("settings-with-dropdowns")
    }
}
