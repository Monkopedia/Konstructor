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
 * LSP transport pipe + bridge graceful-off (epic #35, Phases 1–2 / issues #36, #37).
 *
 * Phase 1 proved the canned-diagnostic round-trip through a stub server. Phase 2
 * replaces the stub with a REAL bridge to a JetBrains `kotlin-lsp` subprocess. CI
 * has no 393MB binary, so [com.monkopedia.konstructor.Config.isKotlinLspAvailable]
 * is false and the bridge degrades to an inert server: turning the flag ON still
 * opens the nested ksrpc LSP sub-service and runs `initialize`/`initialized`/
 * `didOpen` cleanly, but no engine diagnostics flow (and none should — there is no
 * engine). The real kcsg-aware diagnostics path is verified LOCALLY against the
 * binary (see the PR description); it is intentionally NOT CI-tested.
 *
 * This test therefore asserts the flag-ON path is wired and harmless WITHOUT a
 * binary: the editor mounts, the session establishes, the app stays on the main
 * screen, and no spurious diagnostics appear. The flag-OFF behavior (editor
 * byte-for-byte unchanged) is covered by the rest of the suite running with the
 * default-off flag.
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
    fun testLspSessionEstablishesWhenFlagOnWithoutEngine() {
        loadApp()
        waitForBridge()

        bridgeAction("createWorkspace", "LspWs")
        // The version bump can land a beat before the workspaceIds StateFlow propagates
        // into bridge state; wait for the list to be non-empty before reading it.
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.workspaceIds && " +
                "globalThis.__konstructor.state.workspaceIds.length >= 1",
            null,
            Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
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

        // Turn the LSP flag ON: the editor (re)builds the LSP session, which opens the
        // nested ksrpc LSP sub-service and drives initialize/initialized/didOpen against
        // the backend bridge. With no engine binary on CI the bridge is inert, so this
        // must not crash the editor or surface any diagnostics.
        bridgeAction("setLspEnabled", "true")

        // Give the session time to establish + didOpen to round-trip (no engine, so no
        // diagnostics are expected — we assert the app stays healthy, not a count).
        page.waitForTimeout(5000.0)

        // The app must still be on the main screen (the inert bridge didn't break it).
        waitForMainScreen()

        // No engine ⇒ no diagnostics. `lspDiagnostics` is only set when the editor
        // client actually receives a publishDiagnostics; it must remain unset/zero.
        val count = (
            page.evaluate(
                "() => (globalThis.__konstructor.lspDiagnostics && " +
                    "globalThis.__konstructor.lspDiagnostics.count) || 0"
            ) as? Number
            )?.toInt() ?: 0
        System.err.println("LSP (no-engine) diagnostics count=$count")
        assert(count == 0) {
            "With no kotlin-lsp binary on CI the bridge is inert; expected 0 " +
                "diagnostics, got count=$count"
        }
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
