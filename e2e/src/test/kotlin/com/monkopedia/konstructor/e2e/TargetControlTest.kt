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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Tests target visibility (switch) and color controls on the SelectionPane.
 */
class TargetControlTest : BaseE2eTest() {

    private val twoTargetsScript = """
        val cubeA by primitive {
            cube {
                dimensions = xyz(10.0, 10.0, 10.0)
            }
        }
        val cubeB by primitive {
            cube {
                dimensions = xyz(5.0, 5.0, 5.0)
            }
        }
        export("cubeA")
        export("cubeB")
    """.trimIndent()

    private fun setupWithTwoTargets() {
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.create(Space(id = "", name = "TargetTestWs"))
            val workspace = service.get(ws.id)
            val kon = workspace.create(
                Konstruction(name = "TargetTest", workspaceId = ws.id, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(twoTargetsScript)
        }
        loadApp()
        waitForBridge()
        page.reload()
        waitForBridge()
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
        bridgeAction("setCodePaneMode", "EDITOR")
        // Wait for compile + build to finish and target displays to populate
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.targets && " +
                "globalThis.__konstructor.state.targets.length === 2",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(120000.0)
        )
    }

    private val parser = Json { ignoreUnknownKeys = true }

    private fun targetSnapshot(name: String): JsonObject? {
        val jsonStr = page.evaluate(
            "(n) => JSON.stringify(" +
                "globalThis.__konstructor.state.targets.find(t => t.name === n) || null)",
            name
        ) as? String ?: return null
        if (jsonStr == "null") return null
        return parser.decodeFromString(JsonObject.serializer(), jsonStr)
    }

    private fun JsonObject.getBool(key: String): Boolean = this[key]!!.jsonPrimitive.boolean
    private fun JsonObject.getStr(key: String): String = this[key]!!.jsonPrimitive.content

    @Test
    fun testTargetSwitchTogglesEnabled() {
        setupWithTwoTargets()

        // Both targets start enabled by default (new targets default to isEnabled=true)
        val initialA = targetSnapshot("cubeA")
        val initialB = targetSnapshot("cubeB")
        assertTrue(initialA != null, "cubeA should exist in targets")
        assertTrue(initialB != null, "cubeB should exist in targets")
        assertEquals(true, initialA!!.getBool("isEnabled"))
        assertEquals(true, initialB!!.getBool("isEnabled"))

        // Disable cubeA
        bridgeAction("setTargetEnabled", """{"name":"cubeA","enabled":"false"}""")
        page.waitForTimeout(500.0)
        val afterDisable = targetSnapshot("cubeA")!!
        assertEquals(false, afterDisable.getBool("isEnabled"))
        assertEquals(true, targetSnapshot("cubeB")!!.getBool("isEnabled"))

        // Re-enable cubeA
        bridgeAction("setTargetEnabled", """{"name":"cubeA","enabled":"true"}""")
        page.waitForTimeout(500.0)
        assertEquals(true, targetSnapshot("cubeA")!!.getBool("isEnabled"))

        bridgeAction("setCodePaneMode", "SELECTION")
        page.waitForTimeout(2000.0)
        screenshot("target-selection-both-enabled")
    }

    @Test
    fun testTargetColorChange() {
        setupWithTwoTargets()

        // Default color is white
        val initial = targetSnapshot("cubeA")!!
        assertEquals("#ffffff", initial.getStr("color").lowercase())

        // Change to red
        bridgeAction("setTargetColor", """{"name":"cubeA","color":"#cc0000"}""")
        page.waitForTimeout(500.0)
        assertEquals("#cc0000", targetSnapshot("cubeA")!!.getStr("color").lowercase())

        // Change to green
        bridgeAction("setTargetColor", """{"name":"cubeA","color":"#4e9a06"}""")
        page.waitForTimeout(500.0)
        assertEquals("#4e9a06", targetSnapshot("cubeA")!!.getStr("color").lowercase())

        bridgeAction("setCodePaneMode", "SELECTION")
        page.waitForTimeout(2000.0)
        screenshot("target-colors-applied")
    }

    @Test
    fun testTargetDisplaysPersistAcrossReload() {
        setupWithTwoTargets()

        // Customize cubeA
        bridgeAction("setTargetEnabled", """{"name":"cubeA","enabled":"false"}""")
        bridgeAction("setTargetColor", """{"name":"cubeA","color":"#729fcf"}""")
        page.waitForTimeout(500.0)

        // Reload and verify settings survived
        page.reload()
        waitForBridge()
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.targets && " +
                "globalThis.__konstructor.state.targets.length === 2",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
        val reloaded = targetSnapshot("cubeA")!!
        assertEquals(false, reloaded.getBool("isEnabled"))
        assertEquals("#729fcf", reloaded.getStr("color").lowercase())
    }
}
