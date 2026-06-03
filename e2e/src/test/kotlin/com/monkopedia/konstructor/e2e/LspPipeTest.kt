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

import com.microsoft.playwright.Page
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
 * Phase 1 LSP transport pipe (epic #35 / issue #36).
 *
 * Proves the flag-ON round-trip: with the `lspEnabled` flag on, opening a
 * konstruction in the editor opens the nested ksrpc LSP sub-service, the backend
 * stub server pushes one canned `publishDiagnostics` back over the reverse
 * channel, and the editor's client records it on the JS bridge
 * (`globalThis.__konstructor.lspDiagnostics`).
 *
 * The flag-OFF behavior (editor byte-for-byte unchanged) is covered by the rest
 * of the suite running with the default-off flag.
 */
class LspPipeTest : BaseE2eTest() {

    private val validScript = """
        val simpleCube by primitive {
            cube {
                dimensions = xyz(10.0, 10.0, 10.0)
            }
        }
        export("simpleCube")
    """.trimIndent()

    @Test
    fun testCannedDiagnosticRoundTripsWhenFlagOn() {
        loadApp()
        waitForBridge()

        bridgeAction("createWorkspace", "LspWs")
        val wsId = bridgeStateStringList("workspaceIds").first()

        // Create a konstruction with valid content via the API up-front, so the
        // single reload below lands on a workspace that already has it.
        val konId = runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val workspace = service.get(wsId)
            val kon = workspace.create(
                Konstruction(name = "LspTest", workspaceId = wsId, id = "")
            )
            service.konstruction(kon).set(validScript)
            kon.id
        }

        // Reload so Initializer auto-selects the workspace + konstruction.
        page.reload()
        waitForBridge()
        waitForMainScreen()

        // Open the editor and select the konstruction so its editor pane mounts.
        bridgeAction("setCodePaneMode", "EDITOR")
        bridgeActionNoWait("selectKonstruction", konId)

        // Turn the LSP flag ON: the editor (re)builds the LSP session, which
        // sends didOpen and triggers the backend stub's canned publishDiagnostics.
        bridgeAction("setLspEnabled", "true")

        // Wait for the canned diagnostic to round-trip back to the editor client.
        page.waitForFunction(
            "() => globalThis.__konstructor.lspDiagnostics && " +
                "globalThis.__konstructor.lspDiagnostics.count >= 1",
            null,
            Page.WaitForFunctionOptions().setTimeout(60000.0)
        )

        val count = (
            page.evaluate("() => globalThis.__konstructor.lspDiagnostics.count") as? Number
            )?.toInt() ?: 0
        val uri = page.evaluate("() => globalThis.__konstructor.lspDiagnostics.uri")?.toString()
        System.err.println("LSP diagnostics received: count=$count uri=$uri")
        assert(count >= 1) { "Expected the canned LSP diagnostic to round-trip, got count=$count" }
    }

    private fun waitForMainScreen() {
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen === 'main'",
            null,
            Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
    }
}
