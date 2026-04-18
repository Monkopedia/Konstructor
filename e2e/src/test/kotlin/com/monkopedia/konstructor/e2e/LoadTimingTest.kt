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
import kotlin.math.roundToLong
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Measures various stages of initial page load and prints a breakdown.
 * Run with: ./gradlew :e2e:test -Pe2e --tests "*.LoadTimingTest"
 */
class LoadTimingTest : BaseE2eTest() {

    private val FOUR_TARGET_SCRIPT = """
        val cubeA by primitive {
            cube { dimensions = xyz(10.0, 10.0, 10.0) }
        }
        val cubeB by primitive {
            cube { dimensions = xyz(8.0, 8.0, 8.0) }
        }
        val cubeC by primitive {
            cube { dimensions = xyz(6.0, 6.0, 6.0) }
        }
        val cubeD by primitive {
            cube { dimensions = xyz(4.0, 4.0, 4.0) }
        }
        export("cubeA")
        export("cubeB")
        export("cubeC")
        export("cubeD")
    """.trimIndent()

    @Test
    fun measureLoadWithExistingBuilds() {
        // Set up a konstruction with 4 targets and pre-build them via API
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.create(Space(id = "", name = "LoadTestWs"))
            val workspace = service.get(ws.id)
            val kon = workspace.create(
                Konstruction(name = "PrebuiltTest", workspaceId = ws.id, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(FOUR_TARGET_SCRIPT)
            val compileResult = ks.compile()
            require(compileResult.status == TaskStatus.SUCCESS) {
                "Compile failed: ${compileResult.messages}"
            }
            // Build all targets
            ks.requestKonstructs(listOf("cubeA", "cubeB", "cubeC", "cubeD"))
            // Wait for build to finish — poll info
            var attempts = 0
            val cleanState = com.monkopedia.konstructor.common.DirtyState.CLEAN
            while (attempts < 60) {
                val info = ks.getInfo()
                if (info.targets.size == 4 &&
                    info.targets.all { it.state == cleanState }
                ) break
                kotlinx.coroutines.delay(1000)
                attempts++
            }
        }

        val tStart = System.currentTimeMillis()
        loadApp()
        val tAfterGoto = System.currentTimeMillis()

        waitForBridge(60000.0)
        val tAfterBridge = System.currentTimeMillis()

        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
        val tAfterMain = System.currentTimeMillis()

        // Wait for all 4 STL requests to complete
        page.waitForFunction(
            """() => {
                var entries = performance.getEntriesByType('resource')
                    .filter(r => r.name.endsWith('.stl'));
                return entries.length >= 4 && entries.every(r => r.responseEnd > 0);
            }""",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
        val tAfterStls = System.currentTimeMillis()

        val resourceJson = page.evaluate(
            """() => JSON.stringify(
                performance.getEntriesByType('resource').map(r => ({
                    name: r.name.split('/').pop(),
                    duration: Math.round(r.duration),
                    transferSize: r.transferSize || 0,
                    encodedBodySize: r.encodedBodySize || 0,
                    decodedBodySize: r.decodedBodySize || 0
                }))
            )"""
        ) as? String ?: "[]"

        val sep = "=".repeat(70)
        System.err.println(sep)
        System.err.println("LOAD WITH EXISTING BUILDS (4 STLs)")
        System.err.println(sep)
        System.err.println("  page.navigate():        ${tAfterGoto - tStart} ms")
        System.err.println("  → bridge ready:         ${tAfterBridge - tAfterGoto} ms")
        System.err.println("  → screen == 'main':     ${tAfterMain - tAfterBridge} ms")
        System.err.println("  → all STLs loaded:      ${tAfterStls - tAfterMain} ms")
        System.err.println("  TOTAL:                  ${tAfterStls - tStart} ms")
        System.err.println()
        System.err.println("STL downloads:")
        val resources = parseResources(resourceJson)
        for (r in resources.filter { it.name.endsWith(".stl") }) {
            val kb = r.decodedBodySize / 1024
            System.err.println(
                "  ${r.name.padEnd(30)} ${r.duration.toString().padStart(5)}ms  ${kb}KB"
            )
        }
        val stlResources = resources.filter { it.name.endsWith(".stl") }
        val stlTotalMs = stlResources.sumOf { it.duration }
        val stlTotalKb = stlResources.sumOf { it.decodedBodySize } / 1024
        System.err.println("  TOTAL STL: ${stlTotalMs}ms (cumulative), ${stlTotalKb}KB")
        System.err.println(sep)
    }

    @Test
    fun measureInitialLoad() {
        val tStart = System.currentTimeMillis()
        loadApp()
        val tAfterGoto = System.currentTimeMillis()

        waitForBridge(60000.0)
        val tAfterBridge = System.currentTimeMillis()

        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen !== 'loading'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(60000.0)
        )
        val tAfterScreenReady = System.currentTimeMillis()

        // Collect browser-side performance timings
        val navJson = page.evaluate(
            """() => {
                var nav = performance.getEntriesByType('navigation')[0];
                if (!nav) return JSON.stringify({});
                return JSON.stringify({
                    domContentLoaded: nav.domContentLoadedEventEnd - nav.startTime,
                    load: nav.loadEventEnd - nav.startTime,
                    responseEnd: nav.responseEnd - nav.startTime,
                    domInteractive: nav.domInteractive - nav.startTime
                });
            }"""
        ) as? String ?: "{}"

        // Collect resource timings
        val resourceJson = page.evaluate(
            """() => {
                var resources = performance.getEntriesByType('resource');
                var result = resources.map(r => ({
                    name: r.name.split('/').pop(),
                    duration: Math.round(r.duration),
                    transferSize: r.transferSize || 0,
                    encodedBodySize: r.encodedBodySize || 0,
                    decodedBodySize: r.decodedBodySize || 0
                }));
                return JSON.stringify(result);
            }"""
        ) as? String ?: "[]"

        val separator = "=".repeat(70)
        System.err.println(separator)
        System.err.println("LOAD TIMING BREAKDOWN")
        System.err.println(separator)
        System.err.println("Wall-clock timings (Playwright):")
        System.err.println("  page.navigate():         ${tAfterGoto - tStart} ms")
        System.err.println("  → bridge ready:          ${tAfterBridge - tAfterGoto} ms")
        System.err.println("  → screen != loading:     ${tAfterScreenReady - tAfterBridge} ms")
        System.err.println("  TOTAL:                   ${tAfterScreenReady - tStart} ms")
        System.err.println()
        System.err.println("Browser navigation timing:")
        System.err.println("  $navJson")
        System.err.println()
        System.err.println("Resources (sorted by decoded size):")
        val resources = parseResources(resourceJson)
            .sortedByDescending { it.decodedBodySize }
        for (r in resources.take(15)) {
            val transferKb = r.transferSize / 1024
            val decodedKb = r.decodedBodySize / 1024
            val savings = if (r.decodedBodySize > 0 && r.transferSize > 0) {
                val pct = (100 - (r.transferSize * 100.0 / r.decodedBodySize)).roundToLong()
                " (saved $pct%)"
            } else ""
            System.err.println(
                "  ${r.name.padEnd(40)} ${r.duration.toString().padStart(5)}ms  " +
                    "${transferKb}KB xfer / ${decodedKb}KB decoded$savings"
            )
        }
        System.err.println(separator)
    }

    private data class ResourceTiming(
        val name: String,
        val duration: Long,
        val transferSize: Long,
        val encodedBodySize: Long,
        val decodedBodySize: Long
    )

    private fun parseResources(jsonStr: String): List<ResourceTiming> {
        val parser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val arr = parser.parseToJsonElement(jsonStr).let {
            it as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        }
        return arr.mapNotNull { el ->
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            ResourceTiming(
                name = obj["name"]?.toString()?.trim('"') ?: "",
                duration = obj["duration"]?.toString()?.toLongOrNull() ?: 0L,
                transferSize = obj["transferSize"]?.toString()?.toLongOrNull() ?: 0L,
                encodedBodySize = obj["encodedBodySize"]?.toString()?.toLongOrNull() ?: 0L,
                decodedBodySize = obj["decodedBodySize"]?.toString()?.toLongOrNull() ?: 0L
            )
        }
    }
}
