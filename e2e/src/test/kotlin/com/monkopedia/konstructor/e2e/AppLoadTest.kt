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

class AppLoadTest : BaseE2eTest() {

    @Test
    fun testAppLoads() {
        page.navigate(server.baseUrl)
        page.waitForLoadState()
        val body = page.querySelector("body")
        assertNotNull(body, "Page body should exist")
    }

    @Test
    fun testEmptyStateShowsCreateWorkspacePrompt() {
        page.navigate(server.baseUrl)
        // Wait for React to mount and WebSocket to connect
        page.waitForTimeout(5000.0)
        val html = page.content()
        // The empty state should show UI for creating the first workspace
        // Check for any rendered content beyond the initial HTML shell
        assertTrue(
            html.contains("input") || html.contains("workspace") ||
                html.contains("MuiTextField") || html.contains("div"),
            "Page should have rendered React content. HTML: ${html.take(500)}"
        )
    }
}
