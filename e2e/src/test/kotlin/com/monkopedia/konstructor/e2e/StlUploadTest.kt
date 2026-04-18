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
import com.monkopedia.konstructor.common.KonstructionType
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

class StlUploadTest : BaseE2eTest() {

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testCreateStlKonstruction() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "StlWs"))
        val workspace = service.get(ws.id)

        // Create an STL type konstruction
        val kon = workspace.create(
            Konstruction(name = "Mesh", workspaceId = ws.id, id = "", type = KonstructionType.STL)
        )
        assertEquals(KonstructionType.STL, kon.type)
        assertEquals("Mesh", kon.name)

        // Set STL content as text
        val ks = service.konstruction(kon)
        val stlContent = """
            solid test
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 1 0 0
                vertex 0 1 0
              endloop
            endfacet
            endsolid test
        """.trimIndent()
        ks.set(stlContent)

        // Verify content persists
        val fetched = ks.fetch()
        assertTrue(fetched.contains("solid test"), "STL content should persist")
        Unit
    }
}
