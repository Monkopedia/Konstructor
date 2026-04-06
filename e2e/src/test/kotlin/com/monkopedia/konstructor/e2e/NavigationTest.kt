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
import kotlin.test.assertTrue

class NavigationTest : BaseE2eTest() {

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testMultipleWorkspacesAndKonstructions() = runBlocking {
        val service = connectService()
        val ws1 = service.create(Space(id = "", name = "Workspace A"))
        val ws2 = service.create(Space(id = "", name = "Workspace B"))

        val workspace1 = service.get(ws1.id)
        workspace1.create(Konstruction(name = "Cube", workspaceId = ws1.id, id = ""))
        workspace1.create(Konstruction(name = "Sphere", workspaceId = ws1.id, id = ""))

        val workspace2 = service.get(ws2.id)
        workspace2.create(Konstruction(name = "Cylinder", workspaceId = ws2.id, id = ""))

        val allWorkspaces = service.list()
        assertEquals(2, allWorkspaces.size)
        assertEquals(2, workspace1.list().size)
        assertEquals(1, workspace2.list().size)
        Unit
    }

    @Test
    fun testWorkspaceAndKonstructionRename() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "NameTestWs"))
        val workspace = service.get(ws.id)
        assertEquals("NameTestWs", workspace.getName())

        workspace.setName("RenamedWs")
        assertEquals("RenamedWs", workspace.getName())

        val kon = workspace.create(Konstruction(name = "Original", workspaceId = ws.id, id = ""))
        val ks = service.konstruction(kon)
        assertEquals("Original", ks.getName())
        ks.setName("Renamed")
        assertEquals("Renamed", ks.getName())
        Unit
    }
}
