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
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspaceCrudTest : BaseE2eTest() {

    @Test
    fun testCreateFirstWorkspace() {
        page.navigate(server.baseUrl)
        // Wait for React + WebSocket initialization
        val input = page.waitForSelector(
            "input",
            Page.WaitForSelectorOptions().setTimeout(15000.0)
        )
        assertNotNull(input, "Should show workspace name input")

        // Type workspace name and submit
        input.fill("My Test Workspace")
        page.locator("button:not([disabled])").last().click()

        // Wait for navigation away from empty state
        page.waitForTimeout(3000.0)

        // Verify we navigated away from create workspace screen
        val bodyText = page.content()
        assertTrue(
            bodyText.contains("My Test Workspace") ||
                page.querySelectorAll("input").isEmpty(),
            "Should have navigated away from create workspace screen"
        )
    }
}
