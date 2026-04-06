package com.monkopedia.konstructor.e2e

import com.microsoft.playwright.Page
import java.io.File
import java.nio.file.Paths
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeDebugTest : BaseE2eTest() {

    private fun waitForBridge() {
        page.waitForFunction(
            "() => globalThis.__konstructor && globalThis.__konstructor.state && globalThis.__konstructor.state.screen",
            null,
            Page.WaitForFunctionOptions().setTimeout(30000.0)
        )
    }

    private fun focusCanvas() {
        page.evaluate("""() => {
            const sr = document.body.shadowRoot;
            if (sr) {
                const canvas = sr.querySelector('canvas');
                if (canvas) canvas.focus();
            }
        }""")
        page.waitForTimeout(100.0)
    }

    private fun getVersion(): Int {
        return (page.evaluate("() => globalThis.__konstructor.version") as? Number)?.toInt() ?: 0
    }

    private fun waitForVersionChange(fromVersion: Int, timeoutMs: Long = 5000) {
        page.waitForFunction(
            "(v) => globalThis.__konstructor && globalThis.__konstructor.version > v",
            fromVersion,
            Page.WaitForFunctionOptions().setTimeout(timeoutMs.toDouble())
        )
    }

    private fun screenshot(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots")
        dir.mkdirs()
        page.screenshot(
            Page.ScreenshotOptions()
                .setPath(Paths.get(dir.absolutePath, "$name.png"))
                .setFullPage(true)
        )
    }

    private fun getState(): String {
        return page.evaluate("() => JSON.stringify(globalThis.__konstructor.state)")?.toString() ?: "{}"
    }

    @Test
    fun testBridgeExposesState() {
        loadApp()
        waitForBridge()

        val state = getState()
        System.err.println("=== STATE: $state ===")
        assertTrue(state.contains("\"screen\":\"empty\""))
    }

    @Test
    fun testCanClickTextFieldAndType() {
        loadApp()
        waitForBridge()

        // Click on the text field area
        val viewport = page.viewportSize()
        val cx = (viewport?.width ?: 1280) / 2 - 50
        val cy = (viewport?.height ?: 720) / 2
        page.mouse().click(cx.toDouble(), cy.toDouble())
        page.waitForTimeout(300.0)

        // Focus canvas for key events
        focusCanvas()

        // Type character by character via keyboard
        for (ch in "TestWs") {
            page.keyboard().press(ch.toString())
            page.waitForTimeout(30.0)
        }
        page.waitForTimeout(500.0)

        screenshot("compose-text-typed")

        // Check the hidden input has the text
        val inputVal = page.evaluate("""() => {
            const sr = document.body.shadowRoot;
            const input = sr ? sr.querySelector('input') : null;
            return input ? input.value : '';
        }""")?.toString() ?: ""
        System.err.println("=== INPUT VALUE: '$inputVal' ===")
        assertTrue(inputVal.isNotEmpty(), "Hidden input should have text")
    }

    @Test
    fun testCreateWorkspaceViaAction() {
        loadApp()
        waitForBridge()

        // Use the test bridge action to create a workspace
        val versionBefore = getVersion()
        page.evaluate("""() => {
            globalThis.__konstructor.actions.createWorkspace('E2eTestWs');
        }""")

        // Wait for state to update
        page.waitForFunction(
            "(v) => globalThis.__konstructor.version > v",
            versionBefore,
            Page.WaitForFunctionOptions().setTimeout(10000.0)
        )
        page.waitForTimeout(2000.0)

        val state = getState()
        System.err.println("=== STATE AFTER CREATE: $state ===")
        screenshot("compose-after-create-action")

        assertTrue(
            state.contains("\"workspaceCount\":1") || state.contains("\"screen\":\"main\""),
            "Workspace should be created. State: $state"
        )
    }
}
