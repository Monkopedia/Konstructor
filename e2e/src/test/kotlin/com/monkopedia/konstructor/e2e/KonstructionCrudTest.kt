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
class KonstructionCrudTest : BaseE2eTest() {

    private fun createWorkspaceAndGetId(name: String): String {
        bridgeAction("createWorkspace", name)
        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.workspaceCount >= 1",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
        val ids = bridgeStateStringList("workspaceIds")
        assertTrue(ids.isNotEmpty(), "Should have workspace ids")
        val wsId = ids.first()
        bridgeAction("selectWorkspace", wsId)
        return wsId
    }

    private fun getFirstKonstructionId(wsId: String): String = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        val service = conn.defaultChannel().toStub<Konstructor, String>()
        val ws = service.get(wsId)
        val kons = ws.list()
        kons.firstOrNull()?.id ?: ""
    }

    @Test
    fun testCreateKonstruction() {
        loadApp()
        waitForBridge()

        val wsId = createWorkspaceAndGetId("KonTestWs")

        val arg = """{"name":"MyCube","workspaceId":"$wsId"}"""
        bridgeActionNoWait("createKonstruction", arg)

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.konstructionCount >= 1",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )

        val konCount = bridgeStateInt("konstructionCount")
        assertTrue(konCount >= 1, "Should have at least 1 konstruction, got: $konCount")
        val konNames = bridgeStateStringList("konstructionNames")
        assertTrue(konNames.contains("MyCube"), "Konstruction names should contain 'MyCube', got: $konNames")
    }

    @Test
    fun testDeleteKonstruction() {
        loadApp()
        waitForBridge()

        val wsId = createWorkspaceAndGetId("KonDeleteWs")

        val arg = """{"name":"ToDelete","workspaceId":"$wsId"}"""
        bridgeActionNoWait("createKonstruction", arg)

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.konstructionCount >= 1",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )

        val konId = getFirstKonstructionId(wsId)
        assertTrue(konId.isNotEmpty(), "Should find a konstruction id")

        val deleteArg = """{"wsId":"$wsId","konId":"$konId"}"""
        bridgeActionNoWait("deleteKonstruction", deleteArg)

        page.waitForFunction(
            "() => globalThis.__konstructor.state && globalThis.__konstructor.state.konstructionCount === 0",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )

        val konCount = bridgeStateInt("konstructionCount")
        assertEquals(0, konCount, "Should have 0 konstructions after deletion")
    }
}
