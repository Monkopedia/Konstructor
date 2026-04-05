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

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KonstructionCrudTest : BaseE2eTest() {

    @Test
    fun testCreateKonstruction() {
        loadApp()
        createFirstWorkspaceViaUi("TestWs")
        openNavigationPane()
        expandWorkspace("TestWs")
        createKonstructionViaUi("MyCube")

        val html = page.content()
        assertTrue(html.contains("MyCube"), "Konstruction should appear")
    }

    @Test
    fun testRenameKonstruction() {
        loadApp()
        createFirstWorkspaceViaUi("RenameWs")
        openNavigationPane()
        expandWorkspace("RenameWs")
        createKonstructionViaUi("Original")

        // After creation, we may have navigated to the editor.
        // Go back to navigation and ensure workspace is expanded.
        ensureNavigationWithExpandedWorkspace("RenameWs")

        clickEditButton("Original")
        val dialogInput = page.waitForSelector(
            ".MuiDialog-root input", waitOpts(5000.0)
        )
        assertNotNull(dialogInput)
        dialogInput.fill("Renamed")
        page.locator(".MuiDialog-root button:has-text('Save')").click()
        page.waitForTimeout(2000.0)

        val content = page.content()
        assertTrue(content.contains("Renamed"), "Konstruction should be renamed")
    }

    @Test
    fun testDeleteKonstruction() {
        loadApp()
        createFirstWorkspaceViaUi("DeleteWs")
        openNavigationPane()
        expandWorkspace("DeleteWs")
        createKonstructionViaUi("ToDelete")

        ensureNavigationWithExpandedWorkspace("DeleteWs")

        clickEditButton("ToDelete")
        page.locator(".MuiDialog-root button:has-text('Delete')").click()
        page.waitForTimeout(2000.0)

        val content = page.content()
        assertTrue(
            !content.contains(">ToDelete<"),
            "Deleted konstruction should not appear"
        )
    }

    /**
     * Ensure we're in navigation mode with the workspace expanded.
     * Handles the case where we might already be in navigation mode
     * after creating a konstruction.
     */
    private fun ensureNavigationWithExpandedWorkspace(wsName: String) {
        page.waitForTimeout(1000.0)
        // Check if we can see the workspace in a list
        val wsVisible = page.querySelector(
            ".MuiListItemButton-root:has-text('$wsName')"
        )
        if (wsVisible == null) {
            // Not in navigation mode — switch to it
            openNavigationPane()
            expandWorkspace(wsName)
        } else {
            // Already in navigation. Check if workspace is expanded
            // by looking for "Add new konstruction" visible
            val addBtn = page.querySelector("text=Add new konstruction")
            if (addBtn == null) {
                // Workspace is collapsed — expand it
                expandWorkspace(wsName)
            }
        }
    }
}
