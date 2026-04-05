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
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the full backend pipeline via ksrpc API:
 * create workspace -> create konstruction -> set script ->
 * compile -> build -> download STL via HTTP.
 */
class BuildAndDownloadStlTest {

    companion object {
        val server = ServerFixture()

        @JvmStatic
        @BeforeClass
        fun setUpAll() {
            server.start()
        }

        @JvmStatic
        @AfterClass
        fun tearDownAll() {
            server.stop()
        }

        val CUBE_SCRIPT = """
            val simpleCube by primitive {
                cube {
                    dimensions = xyz(10.0, 10.0, 10.0)
                }
            }
            export("simpleCube")
        """.trimIndent()

        val MULTI_TARGET_SCRIPT = """
            val myCube by primitive {
                cube {
                    dimensions = xyz(5.0, 5.0, 5.0)
                }
            }
            val mySphere by primitive {
                Sphere(radius = 3.0)
            }
            export("myCube")
            export("mySphere")
        """.trimIndent()
    }

    private fun connectToService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val url = "${server.baseUrl}/konstructor"
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection(url, env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    @Test
    fun testCompileBuildAndDownloadStl() = runBlocking {
        val service = connectToService()

        // Create workspace
        val workspace = service.create(Space(id = "", name = "STL Test"))
        assertNotNull(workspace.id)
        assertTrue(workspace.id.isNotEmpty())

        // Create konstruction
        val ws = service.get(workspace.id)
        val konstruction = ws.create(
            Konstruction(name = "cube", workspaceId = workspace.id, id = "")
        )
        assertNotNull(konstruction.id)

        // Set script content
        val ks = service.konstruction(konstruction)
        ks.set(CUBE_SCRIPT)

        // Compile
        val compileResult = ks.compile()
        assertEquals(
            TaskStatus.SUCCESS, compileResult.status,
            "Compilation failed: ${compileResult.messages}"
        )

        // Build target
        val buildResult = ks.konstruct("simpleCube")
        assertEquals(
            TaskStatus.SUCCESS, buildResult.status,
            "Build failed: ${buildResult.messages}"
        )

        // Download STL via HTTP
        val stlPath = ks.konstructed("simpleCube")
        assertNotNull(stlPath, "konstructed() should return STL path")

        val stlUrl = "${server.baseUrl}/$stlPath"
        val conn = URL(stlUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        assertEquals(200, conn.responseCode, "STL download should succeed")

        val stlContent = conn.inputStream.bufferedReader().readText()
        assertTrue(
            stlContent.contains("solid") || stlContent.contains("facet"),
            "Downloaded content should be valid STL. Got: ${stlContent.take(200)}"
        )
        assertTrue(
            stlContent.length > 100,
            "STL content should be non-trivial (got ${stlContent.length} bytes)"
        )
    }

    @Test
    fun testMultipleTargetsBuildAndDownload() = runBlocking {
        val service = connectToService()

        val workspace = service.create(Space(id = "", name = "Multi Test"))
        val ws = service.get(workspace.id)
        val konstruction = ws.create(
            Konstruction(name = "multi", workspaceId = workspace.id, id = "")
        )
        val ks = service.konstruction(konstruction)
        ks.set(MULTI_TARGET_SCRIPT)

        val compileResult = ks.compile()
        assertEquals(
            TaskStatus.SUCCESS, compileResult.status,
            "Compilation failed: ${compileResult.messages}"
        )

        // Build cube target
        val cubeBuild = ks.konstruct("myCube")
        assertEquals(
            TaskStatus.SUCCESS, cubeBuild.status,
            "Cube build failed: ${cubeBuild.messages}"
        )
        val cubePath = ks.konstructed("myCube")
        assertNotNull(cubePath, "Cube STL path should exist")

        // Build sphere target
        val sphereBuild = ks.konstruct("mySphere")
        assertEquals(
            TaskStatus.SUCCESS, sphereBuild.status,
            "Sphere build failed: ${sphereBuild.messages}"
        )
        val spherePath = ks.konstructed("mySphere")
        assertNotNull(spherePath, "Sphere STL path should exist")

        // Download both and verify content differs
        val cubeStl = URL("${server.baseUrl}/$cubePath").readText()
        val sphereStl = URL("${server.baseUrl}/$spherePath").readText()

        assertTrue(cubeStl.contains("solid") || cubeStl.contains("facet"))
        assertTrue(sphereStl.contains("solid") || sphereStl.contains("facet"))
        assertTrue(
            cubeStl != sphereStl,
            "Cube and sphere STL should be different"
        )
    }

    @Test
    fun testCompilationErrorReportsMessages() = runBlocking {
        val service = connectToService()

        val workspace = service.create(Space(id = "", name = "Error Test"))
        val ws = service.get(workspace.id)
        val konstruction = ws.create(
            Konstruction(name = "broken", workspaceId = workspace.id, id = "")
        )
        val ks = service.konstruction(konstruction)
        ks.set("this is not valid kotlin code!!!")

        val compileResult = ks.compile()
        assertEquals(TaskStatus.FAILURE, compileResult.status)
        assertTrue(
            compileResult.messages.isNotEmpty(),
            "Should have error messages"
        )
        assertTrue(
            compileResult.messages.any { it.line != null },
            "At least one error should have line info: ${compileResult.messages}"
        )
    }
}
