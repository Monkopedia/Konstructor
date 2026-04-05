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
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import java.io.File
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Captures screenshots of all major UI states for visual inspection.
 * Screenshots are saved to e2e/build/screenshots/.
 */
class ScreenshotTest : BaseE2eTest() {

    private val screenshotDir by lazy {
        val baseDir = System.getProperty("user.dir")
        File(baseDir, "build/screenshots").also { it.mkdirs() }
    }

    private fun screenshot(name: String) {
        page.screenshot(
            Page.ScreenshotOptions()
                .setPath(Paths.get(screenshotDir.absolutePath, "$name.png"))
                .setFullPage(true)
        )
        System.err.println("Screenshot saved: $name.png")
    }

    @Test
    fun captureEmptyState() {
        loadApp()
        page.waitForSelector("input", waitOpts(15000.0))
        screenshot("01-empty-state")
    }

    @Test
    fun captureMainScreenAndPanels() {
        loadApp()
        createFirstWorkspaceViaUi("Screenshot Workspace")

        // Main screen with editor (default view after workspace creation)
        openNavigationPane()
        expandWorkspace("Screenshot Workspace")
        createKonstructionViaUi("Demo Cube")
        ensureEditorMode("Screenshot Workspace", "Demo Cube")
        waitForEditor()

        // Type some code so the editor isn't empty
        typeInEditor(DEMO_SCRIPT)
        page.waitForTimeout(500.0)
        screenshot("02-editor-with-code")

        // Navigation pane
        ensureNavigationWithExpandedWorkspace("Screenshot Workspace")
        screenshot("03-navigation-pane")

        // Create a second konstruction so the nav list is more interesting
        createKonstructionViaUi("Demo Sphere")
        ensureNavigationWithExpandedWorkspace("Screenshot Workspace")
        screenshot("04-navigation-pane-multiple")

        // Select the cube and go back to editor
        selectKonstruction("Demo Cube")
        waitForEditor()
        page.waitForTimeout(500.0)
        screenshot("05-editor-selected-konstruction")

        // Settings pane (click settings icon)
        page.evaluate("""
            const btns = document.querySelectorAll('button[aria-label="settings"]');
            if (btns.length) btns[0].click();
        """)
        page.waitForTimeout(1000.0)
        screenshot("06-settings-pane")

        // GL Settings / Viewport Settings pane
        page.evaluate("""
            const btns = document.querySelectorAll('button[aria-label="lighting"]');
            if (btns.length) btns[0].click();
        """)
        page.waitForTimeout(1000.0)
        screenshot("07-viewport-settings-pane")

        // Back to editor
        page.evaluate("""
            const back = document.querySelector('button[aria-label="back"]');
            if (back) back.click();
        """)
        page.waitForTimeout(1000.0)

        // Selection/Rule pane (target list)
        page.evaluate("""
            const btns = document.querySelectorAll('button[aria-label="selection"]');
            if (btns.length) btns[0].click();
        """)
        page.waitForTimeout(1000.0)
        screenshot("08-selection-pane")
    }

    @Test
    fun captureDialogs() {
        loadApp()
        createFirstWorkspaceViaUi("Dialog Workspace")
        openNavigationPane()
        expandWorkspace("Dialog Workspace")

        // Create Workspace dialog
        page.evaluate("""
            const items = document.querySelectorAll('.MuiListItemButton-root');
            for (const item of items) {
                if (item.textContent && item.textContent.includes('Add new space')) {
                    item.click();
                    break;
                }
            }
        """)
        page.waitForTimeout(1000.0)
        screenshot("09-dialog-create-workspace")
        // Close dialog
        page.evaluate("""
            const btns = document.querySelectorAll('button');
            for (const btn of btns) {
                if (btn.textContent === 'Cancel') { btn.click(); break; }
            }
        """)
        page.waitForTimeout(500.0)

        // Create Konstruction dialog
        page.evaluate("""
            const items = document.querySelectorAll('.MuiListItemButton-root');
            for (const item of items) {
                if (item.textContent && item.textContent.includes('Add new konstruction')) {
                    item.click();
                    break;
                }
            }
        """)
        page.waitForTimeout(1000.0)
        screenshot("10-dialog-create-konstruction")
        page.evaluate("""
            const btns = document.querySelectorAll('button');
            for (const btn of btns) {
                if (btn.textContent === 'Cancel') { btn.click(); break; }
            }
        """)
        page.waitForTimeout(500.0)

        // Edit Workspace dialog
        clickEditButton("Dialog Workspace")
        page.waitForTimeout(500.0)
        screenshot("11-dialog-edit-workspace")
        page.evaluate("""
            const btns = document.querySelectorAll('button');
            for (const btn of btns) {
                if (btn.textContent === 'Cancel') { btn.click(); break; }
            }
        """)
        page.waitForTimeout(500.0)

        // Create a konstruction so we can show its edit dialog
        createKonstructionViaUi("TestKon")
        ensureNavigationWithExpandedWorkspace("Dialog Workspace")

        // Edit Konstruction dialog
        clickEditButton("TestKon")
        page.waitForTimeout(500.0)
        screenshot("12-dialog-edit-konstruction")
        page.evaluate("""
            const btns = document.querySelectorAll('button');
            for (const btn of btns) {
                if (btn.textContent === 'Cancel') { btn.click(); break; }
            }
        """)
        page.waitForTimeout(500.0)
    }

    @Test
    fun captureRenderedModel() {
        loadApp()
        createFirstWorkspaceViaUi("Render Workspace")
        openNavigationPane()
        expandWorkspace("Render Workspace")
        createKonstructionViaUi("Rendered Cube")

        // Ensure editor mode and type script
        ensureEditorMode("Render Workspace", "Rendered Cube")
        waitForEditor()
        typeInEditor(DEMO_SCRIPT)
        saveEditor()

        // Compile and build via ksrpc API
        runBlocking {
            val env = ksrpcEnvironment { }
            val client = HttpClient { install(WebSockets) }
            val conn = client.asWebsocketConnection(
                "${server.baseUrl}/konstructor", env
            )
            val service = conn.defaultChannel().toStub<Konstructor, String>()
            val ws = service.list().first()
            val workspace = service.get(ws.id)
            val k = workspace.list().first()
            val ks = service.konstruction(k)

            // Compile
            val compileResult = ks.compile()
            assertTrue(
                compileResult.status == TaskStatus.SUCCESS,
                "Compile failed: ${compileResult.messages}"
            )

            // Build both targets
            ks.konstruct("myCube")
            ks.konstruct("mySphere")
        }

        // Reload to pick up the built targets in the UI
        page.reload()
        page.waitForSelector(".MuiToolbar-root", waitOpts(15000.0))
        page.waitForTimeout(2000.0)

        // Switch to selection pane to enable targets
        page.evaluate("""
            const btns = document.querySelectorAll('button[aria-label="selection"]');
            if (btns.length) btns[0].click();
        """)
        page.waitForTimeout(2000.0)

        // Toggle ON all the MUI Switch toggles to enable rendering
        page.evaluate("""
            document.querySelectorAll('.MuiSwitch-input').forEach(sw => {
                if (!sw.checked) sw.click();
            });
        """)
        page.waitForTimeout(3000.0)
        screenshot("13-selection-pane-enabled")

        // Switch back to editor to see the rendered model in the GL pane
        page.evaluate("""
            const back = document.querySelector('button[aria-label="back"]');
            if (back) back.click();
        """)
        page.waitForTimeout(5000.0)
        screenshot("14-rendered-model-with-editor")
    }

    companion object {
        val DEMO_SCRIPT = """
val myCube by primitive {
    cube {
        dimensions = xyz(10.0, 10.0, 10.0)
    }
}

val mySphere by primitive {
    Sphere(radius = 5.0)
}

export("myCube")
export("mySphere")
        """.trim()
    }
}
