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

/**
 * State-restoration round-trips for the [com.monkopedia.konstructor.frontend.viewmodel.PersistedStateFlow]
 * instances that the migration regressed (issue #3, findings #5/#8/#9).
 *
 * `PersistedStateFlow` reads/writes the browser's `window.localStorage`, so it
 * cannot be unit-tested on the JVM — these go through the real frontend via the
 * TestBridge: set a value, reload the page (fresh ViewModel over the same
 * localStorage), assert the value survived.
 *
 * Target-display persistence is covered by
 * [TargetControlTest.testTargetDisplaysPersistAcrossReload]; workspace selection
 * by [MigrationRegressionTest.workspaceSelectionPersistsAcrossReload].
 */
class MigrationRegressionStateTest : BaseE2eTest() {

    private fun connectService(): Konstructor = runBlocking {
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        val conn = client.asWebsocketConnection("${server.baseUrl}/konstructor", env)
        conn.defaultChannel().toStub<Konstructor, String>()
    }

    /**
     * Editor theme + keymap are persisted enums. A fresh page load must restore
     * the user's choices rather than reverting to the ONE_DARK/VIM defaults.
     */
    @Test
    @MigrationRegression(9, "Editor theme and keymap survive a page reload")
    fun editorThemeAndKeymapPersistAcrossReload() {
        loadApp()
        waitForBridge()

        // Defaults are ONE_DARK / VIM; pick something else.
        assertEquals("ONE_DARK", bridgeStateString("editorTheme"))
        assertEquals("VIM", bridgeStateString("keymap"))

        bridgeAction("setEditorTheme", "SOLARIZED_LIGHT")
        bridgeAction("setKeymap", "EMACS")
        page.waitForTimeout(500.0)
        assertEquals("SOLARIZED_LIGHT", bridgeStateString("editorTheme"))
        assertEquals("EMACS", bridgeStateString("keymap"))

        page.reload()
        waitForBridge()

        assertEquals(
            "SOLARIZED_LIGHT",
            bridgeStateString("editorTheme"),
            "editorTheme should persist across reload"
        )
        assertEquals(
            "EMACS",
            bridgeStateString("keymap"),
            "keymap should persist across reload"
        )
    }

    /**
     * Finding #4 (data-contract half): the restored editor decorations rely on
     * compiler messages carrying a 1-based line number, which
     * EditorDiagnostics.toDiagnostics maps onto a line range. Verify the backend
     * still supplies that line info — without it the (canvas-rendered) red/yellow
     * line decorations could not be anchored.
     */
    @Test
    @MigrationRegression(4, "Compile errors carry line numbers for editor decorations")
    fun compileErrorsCarryLineForDecorations() = runBlocking {
        val service = connectService()
        val ws = service.create(Space(id = "", name = "DiagWs"))
        val workspace = service.get(ws.id)
        val kon = workspace.create(Konstruction(name = "broken", workspaceId = ws.id, id = ""))
        val ks = service.konstruction(kon)
        // Valid first line, error on a later line so a non-null line number is
        // meaningful for line-range mapping.
        ks.set("val ok = 1\nval bad: Int = \"not an int\"\n")
        val result = ks.compile()
        assertEquals(TaskStatus.FAILURE, result.status)
        assertTrue(result.messages.isNotEmpty(), "expected compiler messages")
        assertTrue(
            result.messages.any { it.line != null && it.line!! >= 1 },
            "at least one message must carry a 1-based line for decoration anchoring: " +
                "${result.messages}"
        )
        Unit
    }

    /**
     * Finding #9: a reload must not leave stray non-namespaced keys behind, and
     * the values we set must round-trip under the `konstructor.` prefix. This is
     * the persistence-side guard complementing
     * [MigrationRegressionTest.persistedKeysUseKonstructorPrefix].
     */
    @Test
    @MigrationRegression(9, "Only konstructor.-prefixed keys persist across reload")
    fun noOrphanedLocalStorageKeysAfterReload() {
        loadApp()
        waitForBridge()
        bridgeAction("setEditorTheme", "COBALT")
        page.waitForTimeout(300.0)

        page.reload()
        waitForBridge()

        val raw = page.evaluate(
            "() => JSON.stringify(Object.keys(globalThis.localStorage))"
        )?.toString() ?: "[]"
        val keys = json.decodeFromString<List<String>>(raw)
        val nonPrefixed = keys.filter { !it.startsWith("konstructor.") }
        assertTrue(
            nonPrefixed.isEmpty(),
            "no non-konstructor.-prefixed keys should be written, found: $nonPrefixed"
        )
        assertEquals("COBALT", bridgeStateString("editorTheme"))
    }
}
