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

/**
 * Takes screenshots at every step to verify the full UI works.
 * Run with: DISPLAY=:99 ./gradlew :e2e:test -Pe2e --tests "*.ManualVerificationTest" --no-daemon
 */
class ManualVerificationTest : BaseE2eTest() {

    private val CUBE_SCRIPT = """
val simpleCube by primitive {
    cube {
        dimensions = xyz(10.0, 10.0, 10.0)
    }
}
export("simpleCube")
    """.trim()

    @Test
    fun verifyFullUserFlow() = runBlocking {
        // Step 1: Set up data via API
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        val service = conn.defaultChannel().toStub<Konstructor, String>()

        val ws = service.create(Space(id = "", name = "TestWorkspace"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(
            Konstruction(name = "MyCube", workspaceId = ws.id, id = "")
        )
        val ks = service.konstruction(kon)
        ks.set(CUBE_SCRIPT)

        // Step 2: Load the app in browser (with data already present)
        loadApp()
        waitForBridge()
        page.waitForTimeout(5000.0) // let Compose fully render

        val state = page.evaluate("() => JSON.stringify(globalThis.__konstructor.state)")?.toString() ?: "{}"
        System.err.println("=== BRIDGE STATE: $state ===")

        // Check if content is available via API
        val apiContent = ks.fetch()
        System.err.println("=== API CONTENT LENGTH: ${apiContent.length} ===")
        System.err.println("=== API CONTENT: ${apiContent.take(100)} ===")

        screenshot("verify-01-app-loaded")

        // Step 3: Navigate to navigation pane
        bridgeActionNoWait("setCodePaneMode", "NAVIGATION")
        page.waitForTimeout(3000.0)
        screenshot("verify-02-navigation-pane")

        // Step 4: Back to editor
        bridgeActionNoWait("setCodePaneMode", "EDITOR")
        page.waitForTimeout(3000.0)
        screenshot("verify-03-editor-with-content")

        // Step 5: Settings
        bridgeActionNoWait("setCodePaneMode", "SETTINGS")
        page.waitForTimeout(3000.0)
        screenshot("verify-04-settings")

        // Step 6: GL Settings
        bridgeActionNoWait("setCodePaneMode", "GL_SETTINGS")
        page.waitForTimeout(3000.0)
        screenshot("verify-05-gl-settings")

        // Step 7: Selection pane
        bridgeActionNoWait("setCodePaneMode", "SELECTION")
        page.waitForTimeout(3000.0)
        screenshot("verify-06-selection-pane")

        Unit
    }
}
