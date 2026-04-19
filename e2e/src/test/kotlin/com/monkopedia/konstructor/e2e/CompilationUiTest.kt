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
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompilationUiTest : BaseE2eTest() {

    private val validScript = """
        val simpleCube by primitive {
            cube {
                dimensions = xyz(10.0, 10.0, 10.0)
            }
        }
        export("simpleCube")
    """.trimIndent()

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testSuccessfulCompilation() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "CompileWs"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(Konstruction(name = "CompileTest", workspaceId = ws.id, id = ""))
        val ks = service.konstruction(kon)
        ks.set(validScript)
        val result = ks.compile()
        assertEquals(TaskStatus.SUCCESS, result.status, "Compilation failed: ${result.messages}")
        Unit
    }

    @Test
    fun testCompilationError() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "ErrorWs"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(Konstruction(name = "ErrorTest", workspaceId = ws.id, id = ""))
        val ks = service.konstruction(kon)
        ks.set("this is not valid kotlin!!!")
        val result = ks.compile()
        assertEquals(TaskStatus.FAILURE, result.status)
        assertTrue(result.messages.isNotEmpty(), "Should have error messages")
        Unit
    }
}
