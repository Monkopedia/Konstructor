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

/**
 * Captures screenshots of all major UI states for visual inspection.
 * Screenshots are saved to e2e/build/screenshots/.
 */
class ScreenshotTest : BaseE2eTest() {

    @Test
    fun captureEmptyState() {
        loadApp()
        waitForBridge()
        // Wait for the screen to settle
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen !== 'loading'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000.0)
        )
        screenshot("01-empty-state")
    }

    @Test
    fun captureMainScreen() {
        loadApp()
        waitForBridge()

        // Create a workspace via bridge
        bridgeAction("createWorkspace", "Screenshot Workspace")
        // Reload so Compose starts fresh with the workspace already existing
        page.reload()
        waitForBridge()
        page.waitForFunction(
            "() => globalThis.__konstructor.state && " +
                "globalThis.__konstructor.state.screen === 'main'",
            null,
            com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
        page.waitForTimeout(3000.0)
        screenshot("02-main-screen-with-workspace")

        // Switch to navigation mode
        bridgeAction("setCodePaneMode", "NAVIGATION")
        page.waitForTimeout(3000.0)
        screenshot("03-navigation-mode")

        // Switch to editor mode
        bridgeAction("setCodePaneMode", "EDITOR")
        page.waitForTimeout(3000.0)
        screenshot("04-editor-mode")

        // Switch to settings mode
        bridgeAction("setCodePaneMode", "SETTINGS")
        page.waitForTimeout(3000.0)
        screenshot("05-settings-mode")
    }
}
