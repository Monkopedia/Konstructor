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

class AppLoadTest : BaseE2eTest() {

    @Test
    fun testAppLoads() {
        loadApp()
        waitForBridge()
        val screen = bridgeStateString("screen")
        assertTrue(
            screen == "empty" || screen == "loading",
            "Bridge screen should be 'empty' or 'loading', got: $screen"
        )
    }

    @Test
    fun testEmptyStateScreen() {
        loadApp()
        waitForBridge()
        // Wait a moment for the service to connect and load the workspace list
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen !== 'loading'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000.0)
        )
        val screen = bridgeStateString("screen")
        assertEquals("empty", screen, "Fresh server should show empty screen")
        val wsCount = bridgeStateInt("workspaceCount")
        assertEquals(0, wsCount, "Fresh server should have 0 workspaces")
    }
}
