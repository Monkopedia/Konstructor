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
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilationUiTest : BaseE2eTest() {

    private val VALID_SCRIPT = listOf(
        "val simpleCube by primitive {",
        "    cube {",
        "        dimensions = xyz(10.0, 10.0, 10.0)",
        "    }",
        "}",
        "export(\"simpleCube\")"
    ).joinToString("\n")

    @Test
    fun testSuccessfulCompilation() {
        loadApp()
        createFirstWorkspaceViaUi("CompileWs")
        openNavigationPane()
        expandWorkspace("CompileWs")
        createKonstructionViaUi("CompileTest")

        // Ensure we're in editor mode
        ensureEditorMode("CompileWs", "CompileTest")
        val editor = waitForEditor()
        assertNotNull(editor)
        typeInEditor(VALID_SCRIPT)
        saveEditor()

        // Compile via ksrpc (since compile button requires menu navigation)
        val result = runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection(
                "${server.baseUrl}/konstructor", env
            )
            val service = conn.defaultChannel()
                .toStub<Konstructor, String>()
            val ws = service.list().first()
            val workspace = service.get(ws.id)
            val k = workspace.list().first()
            service.konstruction(k).compile()
        }
        assertEquals(
            TaskStatus.SUCCESS, result.status,
            "Should compile successfully: ${result.messages}"
        )
    }

    @Test
    fun testCompilationErrorReported() {
        loadApp()
        createFirstWorkspaceViaUi("ErrorWs")
        openNavigationPane()
        expandWorkspace("ErrorWs")
        createKonstructionViaUi("ErrorTest")

        ensureEditorMode("ErrorWs", "ErrorTest")
        val editor = waitForEditor()
        assertNotNull(editor)
        typeInEditor("this is not valid kotlin!!!")
        saveEditor()

        val result = runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection(
                "${server.baseUrl}/konstructor", env
            )
            val service = conn.defaultChannel()
                .toStub<Konstructor, String>()
            val ws = service.list().first()
            val workspace = service.get(ws.id)
            val k = workspace.list().first()
            service.konstruction(k).compile()
        }
        assertEquals(TaskStatus.FAILURE, result.status)
        assertTrue(result.messages.isNotEmpty(), "Should have error messages")
    }
}
