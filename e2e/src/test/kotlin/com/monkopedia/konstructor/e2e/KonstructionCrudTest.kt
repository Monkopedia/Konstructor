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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests konstruction CRUD using ksrpc API for mutations
 * and bridge state + screenshots for verification.
 */
class KonstructionCrudTest : BaseE2eTest() {

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testCreateKonstruction() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "KonTestWs"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(
            Konstruction(name = "MyCube", workspaceId = ws.id, id = "")
        )

        assertTrue(kon.id.isNotEmpty(), "Konstruction should have an id")
        assertEquals("MyCube", kon.name)

        val list = workspace.list()
        assertTrue(list.any { it.name == "MyCube" }, "Should find MyCube in list")
        Unit
    }

    @Test
    fun testDeleteKonstruction() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "KonDeleteWs"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(
            Konstruction(name = "ToDelete", workspaceId = ws.id, id = "")
        )

        workspace.delete(kon)

        val list = workspace.list()
        assertTrue(list.none { it.name == "ToDelete" }, "ToDelete should be gone")
        Unit
    }
}
