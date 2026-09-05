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
package com.monkopedia.konstructor.frontend

import com.monkopedia.hauler.Shipper
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode
import com.monkopedia.konstructor.frontend.viewmodel.ConnectionFactory
import com.monkopedia.konstructor.frontend.viewmodel.EditorThemeName
import com.monkopedia.konstructor.frontend.viewmodel.KeymapName
import com.monkopedia.konstructor.frontend.viewmodel.ServiceConnection
import com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceMutations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * `globalThis.__konstructor.state` is the ONLY channel the Playwright e2e suite
 * has for reading app state — Compose renders to a WebGL canvas, so there are no
 * DOM selectors. A snapshot that reports data-class defaults instead of real
 * view-model state therefore lets an e2e assertion pass (or fail) describing
 * state the app is not in.
 *
 * Issue #106: [JsBridge.refreshKonstructions] hand-built its own
 * [AppStateSnapshot] rather than going through `buildSnapshot`. It hardcoded
 * `codePaneMode = "EDITOR"` and never passed `editorTheme`, `keymap` or
 * `lspEnabled` at all, so those three silently took the data-class defaults on
 * every konstruction create/rename/delete.
 *
 * These tests drive all four fields OFF both their view-model defaults and their
 * [AppStateSnapshot] defaults before triggering the refresh, then read the
 * published JSON back out of `globalThis`. A test that left them on their
 * defaults would pass identically against the unfixed code — which is exactly
 * the class of bug this file exists to catch — so
 * [theInstrumentWouldDetectAFieldReportingItsDefault] asserts, on the values
 * this file uses, that each one really does differ from the default it would
 * have been reported as.
 */
class JsBridgeRefreshSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Deliberately not the AppStateSnapshot default, and not the
    // SettingsViewModel default either, so neither can produce a false pass.
    private val expectedPaneMode = CodePaneMode.SETTINGS
    private val expectedTheme = EditorThemeName.TOMORROW
    private val expectedKeymap = KeymapName.EMACS
    private val expectedLspEnabled = false

    @Test
    fun theInstrumentWouldDetectAFieldReportingItsDefault() {
        // If any of these ever coincides with the default, the corresponding
        // assertion below becomes vacuous: it would pass against the pre-fix
        // hand-built snapshot too.
        val defaults = AppStateSnapshot()
        assertNotEquals(defaults.codePaneMode, expectedPaneMode.name)
        assertNotEquals(defaults.editorTheme, expectedTheme.name)
        assertNotEquals(defaults.keymap, expectedKeymap.name)
        assertNotEquals(defaults.lspEnabled, expectedLspEnabled)
        // The pre-fix site also hardcoded EDITOR regardless of the defaults.
        assertNotEquals("EDITOR", expectedPaneMode.name)
    }

    @Test
    fun refreshKonstructionsPublishesRealSettingsNotDefaults() = runTest {
        val fixture = bridgeFixture(backgroundScope)

        fixture.settingsVm.setCodePaneMode(expectedPaneMode)
        fixture.settingsVm.setEditorTheme(expectedTheme)
        fixture.settingsVm.setKeymap(expectedKeymap)
        fixture.settingsVm.setLspEnabled(expectedLspEnabled)
        fixture.spaceListVm.selectWorkspace(WORKSPACE_ID)
        runCurrent()

        testInitBridge()
        JsBridge.refreshKonstructions(
            serviceHolder = fixture.serviceHolder,
            spaceListVm = fixture.spaceListVm,
            settingsVm = fixture.settingsVm,
            konstructionVm = null
        )
        advanceUntilIdle()

        val state = publishedSnapshot()

        // Instrument check: the refresh really did publish, and really did reach
        // the konstruction list. Without this a snapshot of all-defaults could be
        // mistaken for "no publish happened".
        assertEquals(
            listOf("alpha", "beta"),
            state.konstructionNames,
            "refresh should have published the current konstruction list"
        )

        // The four fields issue #106 is about, compared as one list so a
        // regression reports every field that drifted, not just the first.
        assertEquals(
            listOf(
                "codePaneMode=${expectedPaneMode.name}",
                "editorTheme=${expectedTheme.name}",
                "keymap=${expectedKeymap.name}",
                "lspEnabled=$expectedLspEnabled"
            ),
            listOf(
                "codePaneMode=${state.codePaneMode}",
                "editorTheme=${state.editorTheme}",
                "keymap=${state.keymap}",
                "lspEnabled=${state.lspEnabled}"
            ),
            "refreshKonstructions must publish real view-model state, not defaults"
        )
    }

    @Test
    fun refreshKonstructionsDoesNotClobberSettingsPublishedByTheMainSnapshot() = runTest {
        // The e2e-visible failure mode: a test sets a mode, something creates or
        // deletes a konstruction, and the bridge then reports the mode back to
        // its default. Assert the value SURVIVES the refresh rather than merely
        // being present in one snapshot.
        val fixture = bridgeFixture(backgroundScope)
        fixture.settingsVm.setCodePaneMode(expectedPaneMode)
        fixture.settingsVm.setKeymap(expectedKeymap)
        fixture.spaceListVm.selectWorkspace(WORKSPACE_ID)
        runCurrent()

        testInitBridge()
        repeat(2) {
            JsBridge.refreshKonstructions(
                serviceHolder = fixture.serviceHolder,
                spaceListVm = fixture.spaceListVm,
                settingsVm = fixture.settingsVm,
                konstructionVm = null
            )
            advanceUntilIdle()
            val state = publishedSnapshot()
            assertEquals(expectedPaneMode.name, state.codePaneMode, "after refresh #${it + 1}")
            assertEquals(expectedKeymap.name, state.keymap, "after refresh #${it + 1}")
        }
    }

    private fun publishedSnapshot(): AppStateSnapshot {
        val raw = readBridgeStateJson()
        assertNotNull(raw, "nothing was published to globalThis.__konstructor.state")
        return json.decodeFromString(AppStateSnapshot.serializer(), raw)
    }
}

private const val WORKSPACE_ID = "ws-1"

private class BridgeFixture(
    val serviceHolder: ServiceHolder,
    val spaceListVm: SpaceListViewModel,
    val settingsVm: SettingsViewModel
)

private fun bridgeFixture(scope: CoroutineScope): BridgeFixture {
    val serviceHolder = ServiceHolder(
        ConnectionFactory { FakeServiceConnection() },
        scope
    )
    val spaceListVm = SpaceListViewModel(serviceHolder, WorkspaceMutations(serviceHolder))
    return BridgeFixture(serviceHolder, spaceListVm, SettingsViewModel())
}

private class FakeServiceConnection : ServiceConnection {
    override val stub: Konstructor = FakeKonstructor()
    override suspend fun close() = Unit
}

private class FakeKonstructor : Konstructor {
    override suspend fun ping(u: Unit) = Unit
    override suspend fun list(u: Unit): List<Space> = listOf(Space(WORKSPACE_ID, "Workspace 1"))
    override suspend fun get(id: String): Workspace = FakeWorkspace(id)
    override suspend fun konstruction(id: Konstruction): KonstructionService = error("unused")
    override suspend fun create(newItem: Space): Space = error("unused")
    override suspend fun delete(item: Space) = error("unused")
    override suspend fun getGlobalShipper(u: Unit): Shipper = error("unused")
}

private class FakeWorkspace(private val workspaceId: String) : Workspace {
    override suspend fun list(u: Unit): List<Konstruction> = listOf(
        Konstruction(name = "alpha", workspaceId = workspaceId, id = "k1"),
        Konstruction(name = "beta", workspaceId = workspaceId, id = "k2")
    )

    override suspend fun create(newItem: Konstruction): Konstruction = error("unused")
    override suspend fun delete(item: Konstruction) = error("unused")
    override suspend fun getName(u: Unit): String = "Workspace 1"
    override suspend fun setName(name: String) = error("unused")
}

/**
 * Stand-ins for JsBridge's own private `initBridge`/`setState` externs, reading
 * the same `globalThis.__konstructor.state` the Playwright suite reads.
 */
@JsFun("() => { globalThis.__konstructor = { ready: false, version: 0, state: null }; }")
private external fun testInitBridge()

@JsFun(
    "() => { " +
        "const s = globalThis.__konstructor && globalThis.__konstructor.state; " +
        "return s == null ? '' : JSON.stringify(s); " +
        "}"
)
private external fun readBridgeStateJsonRaw(): String

private fun readBridgeStateJson(): String? = readBridgeStateJsonRaw().ifEmpty { null }
