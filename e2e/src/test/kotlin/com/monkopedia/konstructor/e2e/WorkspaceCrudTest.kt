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

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class WorkspaceCrudTest : BaseE2eTest() {

    @Test
    fun testCreateWorkspace() {
        loadApp()
        waitForBridge()

        bridgeAction("createWorkspace", "TestWs")

        // Wait for workspace list to show 1 workspace
        waitForState("globalThis.__konstructor.state.workspaceCount === 1")
        val wsCount = bridgeStateInt("workspaceCount")
        assertEquals(1, wsCount, "Should have 1 workspace after creation")
        val names = bridgeStateStringList("workspaceNames")
        assertTrue(names.contains("TestWs"), "Workspace names should contain 'TestWs', got: $names")
    }

    @Test
    fun testDeleteWorkspace() {
        loadApp()
        waitForBridge()

        // Create a workspace first
        bridgeAction("createWorkspace", "ToDelete")

        waitForState("globalThis.__konstructor.state.workspaceCount === 1")

        // Get the workspace id
        val ids = bridgeStateStringList("workspaceIds")
        assertTrue(ids.isNotEmpty(), "Should have at least one workspace id")
        val wsId = ids.first()

        // Delete it
        bridgeAction("deleteWorkspace", wsId)

        waitForState("globalThis.__konstructor.state.workspaceCount === 0")

        val wsCount = bridgeStateInt("workspaceCount")
        assertEquals(0, wsCount, "Should have 0 workspaces after deletion")
    }
}
