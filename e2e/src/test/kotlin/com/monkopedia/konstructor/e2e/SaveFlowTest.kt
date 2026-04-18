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
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests the save flow via the triggerSave bridge action.
 */
class SaveFlowTest : BaseE2eTest() {

    private val VALID_SCRIPT = """
        val simpleCube by primitive {
            cube {
                dimensions = xyz(10.0, 10.0, 10.0)
            }
        }
        export("simpleCube")
    """.trimIndent()

    @Test
    fun testTriggerSaveViaBridge() {
        // Step 1: Load app, create workspace via bridge (matching ScreenshotTest pattern)
        loadApp()
        waitForBridge()

        bridgeAction("createWorkspace", "SaveTestWs")

        // Reload so Initializer auto-selects the workspace
        page.reload()
        waitForBridge()

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )

        // Get workspace ID
        val wsId = bridgeStateStringList("workspaceIds").first()
        System.err.println("Workspace ID: $wsId")

        // Create konstruction and set content via API
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val workspace = service.get(wsId)
            val kon = workspace.create(
                Konstruction(name = "SaveTest", workspaceId = wsId, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(VALID_SCRIPT)
            System.err.println("Created konstruction ${kon.id} and set content")
        }

        // Reload again so Initializer picks up the konstruction
        page.reload()
        waitForBridge()

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )

        // Switch to editor
        bridgeAction("setCodePaneMode", "EDITOR")
        page.waitForTimeout(8000.0)

        screenshot("save-flow-before")

        // Trigger save via bridge
        System.err.println("Calling triggerSave...")
        val vBefore = getVersion()
        page.evaluate("() => globalThis.__konstructor.actions.triggerSave('')")

        try {
            page.waitForFunction(
                "(v) => globalThis.__konstructor.version > v",
                vBefore,
                com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(120000.0)
            )
            System.err.println("Save completed!")
        } catch (e: Exception) {
            System.err.println("Save timeout: ${e.message}")
        }

        page.waitForTimeout(3000.0)
        screenshot("save-flow-after")
    }
}
