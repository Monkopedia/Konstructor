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

class WebSocketReconnectionTest : BaseE2eTest() {

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testDataPersistsAcrossServerRestart() = runBlocking {
        // Create workspace
        val service1 = connectService()
        val ws = service1.create(Space(id = "", name = "PersistWs"))
        assertTrue(ws.id.isNotEmpty())

        // Stop and restart server (same data dir)
        server.stopProcess()
        Thread.sleep(1000)
        server.restart()

        // Connect again and verify data persists
        val service2 = connectService()
        val workspaces = service2.list()
        assertEquals(1, workspaces.size)
        assertEquals("PersistWs", workspaces[0].name)
        Unit
    }
}
