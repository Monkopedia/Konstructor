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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

/**
 * Codifies the Compose-migration regressions catalogued in issue #3.
 *
 * Tests split into two groups:
 *  - **Locked-in fixes** (passing): selection persistence (#0596479), render
 *    cache-busting (#7adf24c), and the localStorage `konstructor.` prefix
 *    contract. These fail if the fix regresses.
 *  - **Open regressions** (`@Ignore`): "show code on left" no-op, STL-upload
 *    dialog, and sync-conflict detection. Bodies encode the *expected*
 *    pre-migration behavior; `@Ignore` reasons link the issue so CI stays green.
 *
 * Headless note: assertions here go through the TestBridge state, the ksrpc API,
 * and HTTP — none require reading the WebGL canvas, which cannot be verified
 * headlessly. Canvas-visual regressions live in [MigrationVisualRegressionTest].
 */
class MigrationRegressionTest : BaseE2eTest() {

    private val cubeScript = """
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

    private fun localStorageKeys(): List<String> {
        val raw = page.evaluate(
            "() => JSON.stringify(Object.keys(globalThis.localStorage))"
        )?.toString() ?: "[]"
        return json.decodeFromString<List<String>>(raw)
    }

    // --- Locked-in fixes (must keep passing) --------------------------------

    /**
     * Finding #5b / commit 0596479: a fresh page load must restore the user's
     * last workspace selection instead of auto-selecting the first workspace.
     * Pre-migration GlobalFlows persisted this; the Compose-era ViewModels
     * originally used plain MutableStateFlow and lost it on reload.
     */
    @Test
    @MigrationRegression(5, "Workspace selection persists across reload")
    fun workspaceSelectionPersistsAcrossReload() {
        val service = connectService()
        // Two workspaces so "restored" differs from "auto-select first".
        runBlocking {
            service.create(Space(id = "", name = "First"))
            service.create(Space(id = "", name = "Second"))
        }

        loadApp()
        waitForBridge()
        waitForMainScreen()

        val ids = bridgeStateStringList("workspaceIds")
        assertEquals(2, ids.size, "expected two workspaces")
        // Select the SECOND workspace (not the default-first).
        val target = ids[1]
        bridgeAction("selectWorkspace", target)
        page.waitForTimeout(500.0)
        assertEquals(target, bridgeStateString("selectedWorkspaceId"))

        // Fresh page load.
        page.reload()
        waitForBridge()
        waitForMainScreen()

        assertEquals(
            target,
            bridgeStateString("selectedWorkspaceId"),
            "selection must survive reload (regression #3, fixed in 0596479)"
        )
    }

    /**
     * Finding #9 / commit 0596479: persisted state moved under a
     * `konstructor.`-prefixed namespace. Verify the prefix is actually used and
     * that the orphaned pre-migration unprefixed keys are not being written.
     */
    @Test
    @MigrationRegression(9, "Persisted localStorage keys use konstructor. prefix")
    fun persistedKeysUseKonstructorPrefix() {
        loadApp()
        waitForBridge()

        // Mutate a couple of persisted settings so their keys get written.
        bridgeAction("setEditorTheme", "ONE_DARK")
        bridgeAction("setKeymap", "EMACS")
        page.waitForTimeout(500.0)

        val keys = localStorageKeys()
        val persisted = keys.filter { it.startsWith("konstructor.") }
        assertTrue(
            persisted.any { it == "konstructor.editorTheme" },
            "editorTheme should persist under the prefix, got: $keys"
        )
        assertTrue(
            persisted.any { it == "konstructor.keymap" },
            "keymap should persist under the prefix, got: $keys"
        )
        // Pre-migration orphan keys must not be re-introduced.
        assertFalse(keys.contains("workspace"), "orphaned unprefixed 'workspace' key present")
        assertFalse(
            keys.contains("settings.showFps"),
            "orphaned unprefixed 'settings.showFps' key present"
        )
    }

    /**
     * Finding #5a / commit 7adf24c: rebuilt geometry keeps a stable path, so the
     * frontend appends a monotonic `?v=N` cache-bust token to force a reload.
     * The `/model/{target...}` route must resolve purely by path segments and
     * ignore the query, otherwise the cache-bust would 404.
     *
     * This is the server-side half of the render-reload fix and is the only part
     * verifiable headlessly (the canvas reload itself cannot be read back).
     */
    @Test
    @MigrationRegression(5, "Render model route ignores cache-bust query token")
    fun renderModelRouteIgnoresCacheBustQuery() {
        val service = connectService()
        val modelPath = runBlocking {
            val ws = service.create(Space(id = "", name = "RenderWs"))
            val workspace = service.get(ws.id)
            val kon = workspace.create(
                Konstruction(name = "cube", workspaceId = ws.id, id = "")
            )
            val ks = service.konstruction(kon)
            ks.set(cubeScript)
            assertEquals(TaskStatus.SUCCESS, ks.compile().status, "compile failed")
            assertEquals(TaskStatus.SUCCESS, ks.konstruct("simpleCube").status, "build failed")
            ks.konstructed("simpleCube")
        }
        assertNotNull(modelPath, "konstructed() should return a model path")

        val plain = fetchText("${server.baseUrl}/$modelPath")
        // Frontend appends a token like ?v=3; route must ignore it.
        val busted = fetchText("${server.baseUrl}/$modelPath?v=999")

        assertTrue(plain.first == 200, "plain model fetch should 200, got ${plain.first}")
        assertTrue(busted.first == 200, "cache-busted model fetch should 200, got ${busted.first}")
        assertEquals(
            plain.second,
            busted.second,
            "cache-bust query must not change the served bytes (regression #3, fixed in 7adf24c)"
        )
    }

    private fun fetchText(url: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            ""
        }
        return code to body
    }

    // --- Open regressions (documented, @Ignore so CI stays green) -----------

    /**
     * Finding #1: the "Show code on left" setting is collected in MainScreen.kt
     * but never read — panes are hard-positioned by index.html, so toggling it
     * does nothing. There is also no bridge action to toggle it yet.
     *
     * Expected pre-migration behavior: toggling swaps the code pane and GL pane
     * left/right. When fixed, expose a `setShowCodeLeft` action + a layout-side
     * field in the bridge snapshot, then assert the swap here and drop @Ignore.
     */
    @Test
    @Ignore("Open regression #1 — showCodeLeft is a no-op; see $MIGRATION_ISSUE")
    @MigrationRegression(1, "Toggling showCodeLeft swaps the code/GL panes")
    fun showCodeLeftSwapsPanes() {
        loadApp()
        waitForBridge()
        // No bridge surface exists for this yet. Once a `setShowCodeLeft` action
        // and a layout indicator (e.g. state.codePaneSide) are added:
        //   bridgeAction("setShowCodeLeft", "true")
        //   assertEquals("LEFT", bridgeStateString("codePaneSide"))
        //   bridgeAction("setShowCodeLeft", "false")
        //   assertEquals("RIGHT", bridgeStateString("codePaneSide"))
        error(
            "showCodeLeft has no observable effect (MainScreen.kt:36 collects but never reads it)"
        )
    }

    /**
     * Finding #2: STL upload is stubbed — NavigationPane.kt shows an alert
     * ("STL upload is not yet available") instead of a file dialog. Backend
     * support is intact (see [StlUploadTest.testCreateStlKonstruction]); only
     * the UI dialog is missing. Being fixed separately.
     *
     * Expected behavior: choosing "upload STL" opens a dialog that accepts a
     * file and creates an STL-type konstruction whose content round-trips.
     */
    @Test
    @Ignore(
        "Open regression #2 — STL upload dialog stubbed; fixed separately; see $MIGRATION_ISSUE"
    )
    @MigrationRegression(2, "STL upload dialog opens and accepts a file")
    fun stlUploadDialogOpensAndAcceptsFile() {
        loadApp()
        waitForBridge()
        // No bridge action exists for the upload dialog (NavigationPane.kt:97
        // currently just alerts). When the dialog lands, expose an
        // `openStlUpload` / `uploadStl` action and assert a new STL konstruction
        // appears in state.konstructionNames here.
        error("STL upload is stubbed to an alert; no dialog to drive")
    }

    /**
     * Finding #5c: SyncConflictDialog exists (ui/dialogs/SyncConflictDialog.kt)
     * but is never invoked — `onContentChange` silently overwrites local edits
     * when the server copy diverges. Pre-migration detected the conflict and
     * prompted the user (Overwrite / Discard).
     *
     * Expected behavior: with unsaved local edits, a concurrent server-side
     * change to the same konstruction should raise the sync-conflict dialog
     * rather than clobbering local content.
     */
    @Test
    @Ignore("Open regression #5 — sync-conflict detection never fires; see $MIGRATION_ISSUE")
    @MigrationRegression(5, "Concurrent server edit raises the sync-conflict dialog")
    fun syncConflictDialogFiresOnConcurrentEdit() {
        loadApp()
        waitForBridge()
        // The dialog's visibility (NavigationDialogViewModel.showSyncConflict) is
        // never set true, and is not exposed in the bridge snapshot. When the
        // detection is wired, expose `state.syncConflictVisible` and assert it
        // flips to true after a concurrent server-side `ks.set(...)` lands while
        // the editor holds dirty local content.
        error("SyncConflictDialog is wired into MainScreen but never triggered")
    }
}
