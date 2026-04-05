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

class WorkspaceCrudTest : BaseE2eTest() {

    @Test
    fun testCreateFirstWorkspace() {
        loadApp()
        createFirstWorkspaceViaUi("My Test Workspace")

        val bodyText = page.content()
        assertTrue(
            bodyText.contains("My Test Workspace") ||
                !bodyText.contains("First workspace name"),
            "Should have navigated away from create workspace screen"
        )
    }

    @Test
    fun testRenameWorkspace() {
        loadApp()
        createFirstWorkspaceViaUi("OriginalWs")
        openNavigationPane()

        clickEditButton("OriginalWs")

        // Dialog "Change workspace name" — fill new name and save
        val dialogInput = page.waitForSelector(
            ".MuiDialog-root input", waitOpts(5000.0)
        )
        assertNotNull(dialogInput)
        dialogInput.fill("RenamedWs")
        page.locator(".MuiDialog-root button:has-text('Save')").click()
        page.waitForTimeout(2000.0)

        val content = page.content()
        assertTrue(content.contains("RenamedWs"), "Workspace should be renamed")
    }

    @Test
    fun testDeleteWorkspace() {
        loadApp()
        createFirstWorkspaceViaUi("ToDeleteWs")
        openNavigationPane()

        clickEditButton("ToDeleteWs")
        page.locator(".MuiDialog-root button:has-text('Delete')").click()
        page.waitForTimeout(2000.0)

        // After deleting the only workspace, we should be back at empty state
        val input = page.waitForSelector("input", waitOpts(10000.0))
        assertNotNull(input, "Should be back to empty state after deleting only workspace")
    }
}
