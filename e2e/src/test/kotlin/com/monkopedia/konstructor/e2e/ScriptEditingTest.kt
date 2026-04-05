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

class ScriptEditingTest : BaseE2eTest() {

    @Test
    fun testTypeInEditorAndVerifyContent() {
        loadApp()
        createFirstWorkspaceViaUi("EditWs")
        openNavigationPane()
        expandWorkspace("EditWs")
        createKonstructionViaUi("EditTest")

        // After creating, should be in editor mode
        val editor = waitForEditor()
        assertNotNull(editor, "CodeMirror editor should appear")

        editor.click()
        page.keyboard().type("// test marker content")
        page.keyboard().press("Escape")
        page.waitForTimeout(500.0)
        page.keyboard().press("Control+s")
        page.waitForTimeout(2000.0)

        val content = getEditorContent()
        assertTrue(
            content.contains("test marker content"),
            "Editor should contain typed text. Got: ${content.take(200)}"
        )
    }

    @Test
    fun testEditorContentPersistsAcrossNavigation() {
        loadApp()
        createFirstWorkspaceViaUi("PersistWs")
        openNavigationPane()
        expandWorkspace("PersistWs")
        createKonstructionViaUi("PersistTest")

        val editor = waitForEditor()
        assertNotNull(editor)
        editor.click()
        page.keyboard().type("// persist marker")
        page.keyboard().press("Escape")
        page.waitForTimeout(500.0)
        page.keyboard().press("Control+s")
        page.waitForTimeout(2000.0)

        // Navigate away
        openNavigationPane()
        page.waitForTimeout(1000.0)

        // Navigate back
        expandWorkspace("PersistWs")
        selectKonstruction("PersistTest")
        waitForEditor()
        page.waitForTimeout(1000.0)

        val content = getEditorContent()
        assertTrue(
            content.contains("persist marker"),
            "Content should persist. Got: ${content.take(200)}"
        )
    }
}
