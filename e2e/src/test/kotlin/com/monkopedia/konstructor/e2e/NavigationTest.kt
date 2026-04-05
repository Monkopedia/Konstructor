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

class NavigationTest : BaseE2eTest() {

    @Test
    fun testNavigateBetweenKonstructions() {
        loadApp()
        createFirstWorkspaceViaUi("NavWs")

        // Create first konstruction and type content
        openNavigationPane()
        expandWorkspace("NavWs")
        createKonstructionViaUi("First")
        ensureEditorMode("NavWs", "First")
        val editor1 = waitForEditor()
        assertNotNull(editor1)
        typeInEditor("// first content")
        saveEditor()

        // Create second konstruction and type different content
        ensureNavigationWithExpandedWorkspace("NavWs")
        createKonstructionViaUi("Second")
        ensureEditorMode("NavWs", "Second")
        val editor2 = waitForEditor()
        assertNotNull(editor2)
        typeInEditor("// second content")
        saveEditor()

        // Navigate to first
        ensureNavigationWithExpandedWorkspace("NavWs")
        selectKonstruction("First")
        waitForEditor()
        page.waitForTimeout(1000.0)
        val c1 = getEditorContent()
        assertTrue(c1.contains("first content"), "First content expected. Got: ${c1.take(200)}")

        // Navigate to second
        ensureNavigationWithExpandedWorkspace("NavWs")
        selectKonstruction("Second")
        waitForEditor()
        page.waitForTimeout(1000.0)
        val c2 = getEditorContent()
        assertTrue(c2.contains("second content"), "Second content expected. Got: ${c2.take(200)}")
    }

    @Test
    fun testWorkspaceNameInTitleBar() {
        loadApp()
        createFirstWorkspaceViaUi("TitleWs")
        openNavigationPane()
        expandWorkspace("TitleWs")
        createKonstructionViaUi("TitleKon")

        // Ensure we're in editor mode so the title shows "Ws > Kon"
        ensureEditorMode("TitleWs", "TitleKon")
        page.waitForTimeout(1000.0)
        val titleText = page.locator(".MuiToolbar-root").first().textContent() ?: ""
        assertTrue(
            titleText.contains("TitleWs") && titleText.contains("TitleKon"),
            "Title should show workspace and konstruction. Got: $titleText"
        )
    }
}
