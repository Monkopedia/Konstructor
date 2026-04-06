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

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@org.junit.Ignore("Bridge async actions timeout in headless - covered by BuildAndDownloadStlTest")
class ScriptEditingTest : BaseE2eTest() {

    private fun createWsAndKon(wsName: String, konName: String): Pair<String, String> {
        bridgeAction("createWorkspace", wsName)
        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.workspaceCount >= 1",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
        val wsId = bridgeStateStringList("workspaceIds").first()
        bridgeAction("selectWorkspace", wsId)

        val createArg = """{"name":"$konName","workspaceId":"$wsId"}"""
        bridgeActionNoWait("createKonstruction", createArg)
        page.waitForTimeout(2000.0)

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.konstructionCount >= 1",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )

        val konId = runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.get(wsId)
            ws.list().first().id
        }
        return wsId to konId
    }

    @Test
    fun testSetAndGetContent() {
        loadApp()
        waitForBridge()

        val (wsId, konId) = createWsAndKon("EditWs", "EditTest")

        val testContent = "// test marker content\nval x = 42"

        // Set content
        val setArg = """{"wsId":"$wsId","konId":"$konId","content":${jsonString(testContent)}}"""
        bridgeActionNoWait("setContent", setArg)
        page.waitForTimeout(2000.0)

        // Get content back
        val getArg = """{"wsId":"$wsId","konId":"$konId"}"""
        bridgeActionNoWait("getContent", getArg)
        page.waitForTimeout(2000.0)

        val result = bridgeLastResult()
        // lastResult is a JSON-encoded string, so it will be a quoted string
        assertTrue(
            result.contains("test marker content"),
            "Retrieved content should contain the set text. Got: $result"
        )
        assertTrue(
            result.contains("val x = 42"),
            "Retrieved content should contain all set text. Got: $result"
        )
    }
}
