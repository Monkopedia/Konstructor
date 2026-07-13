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

import com.microsoft.playwright.Page
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Reproduces and guards issue #33: the editor's error footer must be
 * context-aware — show the compile error for the line the cursor is on, update
 * as the cursor moves, and clear on a non-error line. Also verifies diagnostics
 * land on the correct (header-offset-adjusted) user-content lines.
 *
 * The errored script has two unresolved references on known user-content lines:
 *
 * ```
 * 1  val brokenCube by primitive {
 * 2      cube {
 * 3          dimensions = nonExistentSymbolXyz   // error here
 * 4      }
 * 5  }
 * 6  val goodCube by primitive {
 * 7      cube {
 * 8          dimensions = alsoMissingSymbolAbc   // error here
 * 9      }
 * 10 }
 * 11 export("brokenCube")
 * ```
 *
 * The backend wraps user content in a fixed 3-line `.kt` header before compiling
 * and subtracts that offset, so the diagnostics must report user lines 3 and 8.
 */
class ErrorFooterTest : BaseE2eTest() {

    private val erroredScript = """
        val brokenCube by primitive {
            cube {
                dimensions = nonExistentSymbolXyz
            }
        }
        val goodCube by primitive {
            cube {
                dimensions = alsoMissingSymbolAbc
            }
        }
        export("brokenCube")
    """.trimIndent()

    // The line numbers (1-based, in user content) where the two errors live.
    private val errorLineA = 3
    private val errorLineB = 8

    private val parser = Json { ignoreUnknownKeys = true }

    private fun setupWithErroredKonstruction() {
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.create(Space(id = "", name = "ErrorFooterWs"))
            val workspace = service.get(ws.id)
            val kon = workspace.create(
                Konstruction(name = "ErrorFooterKon", workspaceId = ws.id, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(erroredScript)
        }
        loadApp()
        waitForBridge()
        page.reload()
        waitForBridge()
        waitForMainScreen()
        bridgeAction("setCodePaneMode", "EDITOR")
        // Wait for the auto compile to surface diagnostics in the bridge state.
        waitForState(
            "globalThis.__konstructor.state.diagnostics && " +
                "globalThis.__konstructor.state.diagnostics.length >= 2",
            BUILD_TIMEOUT
        )
    }

    private fun diagnostics(): JsonArray {
        val raw = page.evaluate(
            "() => JSON.stringify(globalThis.__konstructor.state.diagnostics || [])"
        ) as? String ?: "[]"
        return parser.decodeFromString(JsonArray.serializer(), raw)
    }

    private fun diagnosticForLine(line: Int): JsonObject? =
        diagnostics().map { it.jsonObject }.firstOrNull { it["line"]!!.jsonPrimitive.int == line }

    private fun footerError(): String? {
        val raw = page.evaluate(
            "() => globalThis.__konstructor.state.footerError ?? null"
        ) as? String
        return raw
    }

    private fun cursorLine(): Int = (
        page.evaluate("() => globalThis.__konstructor.state.cursorLine") as? Number
        )?.toInt() ?: 0

    private fun moveCursorToLine(line: Int) {
        bridgeAction("moveCursorToLine", line.toString())
        // Wait for the selection listener round-trip to land in the snapshot.
        page.waitForFunction(
            "(l) => globalThis.__konstructor.state.cursorLine === l",
            line,
            Page.WaitForFunctionOptions().setTimeout(10000.0)
        )
    }

    /**
     * Behavior 1: diagnostics highlight the right lines (header offset applied).
     */
    @Test
    fun testDiagnosticsLandOnErrorLines() {
        setupWithErroredKonstruction()

        val diags = diagnostics()
        assertTrue(diags.size >= 2, "Expected at least two diagnostics, got: $diags")

        val diagA = diagnosticForLine(errorLineA)
        val diagB = diagnosticForLine(errorLineB)
        assertTrue(diagA != null, "Expected a diagnostic on line $errorLineA, got: $diags")
        assertTrue(diagB != null, "Expected a diagnostic on line $errorLineB, got: $diags")
        assertTrue(
            diagA!!["message"]!!.jsonPrimitive.content.contains("nonExistentSymbolXyz"),
            "Line $errorLineA diagnostic should mention the bad symbol: $diagA"
        )
        assertTrue(
            diagB!!["message"]!!.jsonPrimitive.content.contains("alsoMissingSymbolAbc"),
            "Line $errorLineB diagnostic should mention the bad symbol: $diagB"
        )
        // Every diagnostic must map inside the 11-line user content — a broken
        // header offset would push lines past the document end.
        for (d in diags) {
            val line = d.jsonObject["line"]!!.jsonPrimitive.int
            assertTrue(line in 1..11, "Diagnostic line $line out of user-content range: $d")
        }
    }

    /**
     * Behavior 2: cursor on an error line shows that line's error in the footer.
     */
    @Test
    fun testCursorOnErrorLineShowsItsError() {
        setupWithErroredKonstruction()

        moveCursorToLine(errorLineA)
        assertEquals(errorLineA, cursorLine())
        val footer = footerError()
        assertTrue(
            footer != null && footer.contains("nonExistentSymbolXyz"),
            "Footer should show the line-$errorLineA error, was: $footer"
        )
    }

    /**
     * Behavior 3: moving the cursor updates the footer — a different error line
     * shows that error; a non-error line clears it.
     */
    @Test
    fun testMovingCursorUpdatesFooter() {
        setupWithErroredKonstruction()

        // On the first error line.
        moveCursorToLine(errorLineA)
        assertTrue(
            footerError()?.contains("nonExistentSymbolXyz") == true,
            "Expected line-$errorLineA error, was: ${footerError()}"
        )

        // Onto the second error line — footer switches to that error.
        moveCursorToLine(errorLineB)
        val onB = footerError()
        assertTrue(
            onB?.contains("alsoMissingSymbolAbc") == true,
            "Expected line-$errorLineB error, was: $onB"
        )
        assertTrue(
            onB?.contains("nonExistentSymbolXyz") != true,
            "Footer should no longer show the line-$errorLineA error, was: $onB"
        )

        // Onto a non-error line (line 1) — footer clears.
        moveCursorToLine(1)
        assertNull(footerError(), "Footer should clear on a non-error line")
    }
}
