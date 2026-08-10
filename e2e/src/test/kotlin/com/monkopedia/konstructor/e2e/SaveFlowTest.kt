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
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests the save flow via the triggerSave bridge action.
 */
class SaveFlowTest : BaseE2eTest() {

    private val validScript = """
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

        waitForMainScreen()

        // Get workspace ID
        val wsId = bridgeStateStringList("workspaceIds").first()
        System.err.println("Workspace ID: $wsId")

        // Create konstruction and set content via API
        val konstruction = runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val workspace = service.get(wsId)
            val kon = workspace.create(
                Konstruction(name = "SaveTest", workspaceId = wsId, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(validScript)
            System.err.println("Created konstruction ${kon.id} and set content")
            kon
        }

        // Reload again so Initializer picks up the konstruction
        page.reload()
        waitForBridge()

        waitForMainScreen()

        // Switch to editor
        bridgeAction("setCodePaneMode", "EDITOR")
        page.waitForTimeout(8000.0)

        screenshot("save-flow-before")

        // Trigger save via bridge
        System.err.println("Calling triggerSave...")
        val vBefore = getVersion()
        page.evaluate("() => globalThis.__konstructor.actions.triggerSave('')")

        // The version increment IS the "save happened" signal. If it never
        // arrives, this throws and the test FAILS — a broken save must not pass.
        page.waitForFunction(
            "(v) => globalThis.__konstructor.version > v",
            vBefore,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(120000.0)
        )
        System.err.println("Save completed!")

        page.waitForTimeout(3000.0)
        screenshot("save-flow-after")

        // Round-trip assertion: the saved content must be readable back via the
        // API and match what we saved. Without this the save could "complete"
        // (version bump) while persisting the wrong content and still pass.
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ks = service.konstruction(konstruction)
            val fetched = ks.fetch()
            System.err.println("Fetched back ${fetched.length} chars after save")
            assertEquals(validScript, fetched)
        }
    }
}
