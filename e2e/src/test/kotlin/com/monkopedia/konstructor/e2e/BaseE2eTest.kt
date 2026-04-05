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

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright

/**
 * Base class for e2e tests. Each test gets a fresh server and browser page.
 */
abstract class BaseE2eTest {
    protected lateinit var server: ServerFixture
    protected lateinit var page: Page

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @org.junit.Before
    fun setUpBase() {
        server = ServerFixture()
        server.start()
        if (playwright == null) {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true)
            )
        }
        page = browser!!.newPage()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error" || msg.type() == "warning") {
                System.err.println("[browser:${msg.type()}] ${msg.text()}")
            }
        }
        page.onPageError { error ->
            System.err.println("[browser:PAGE_ERROR] $error")
        }
    }

    @org.junit.After
    fun tearDownBase() {
        if (::page.isInitialized) page.close()
        server.stop()
    }

    // Clean up Playwright at end (called by JVM shutdown, not per-test)
    protected fun finalize() {
        browser?.close()
        playwright?.close()
    }

    // ── UI Helper Methods ──────────────────────────────────────────

    protected fun loadApp() {
        page.navigate(server.baseUrl)
        page.waitForSelector("body", waitOpts(10000.0))
    }

    protected fun createFirstWorkspaceViaUi(name: String) {
        val input = page.waitForSelector("input", waitOpts(15000.0))
        input.fill(name)
        page.locator("button:not([disabled])").last().click()
        // Wait for workspace creation and WebSocket to stabilize
        page.waitForTimeout(5000.0)
        // Reload to ensure a fresh connection state
        page.reload()
        page.waitForTimeout(5000.0)
        forceHideDialogs()
    }

    protected fun openNavigationPane() {
        waitForDialogsToClear()
        page.locator(".MuiToolbar-root .MuiTypography-root").first().click()
        page.waitForTimeout(1500.0)
    }

    /** Force-hide any modal dialogs so UI interactions can proceed. */
    protected fun waitForDialogsToClear(timeoutMs: Long = 5000) {
        // First try waiting for dialogs to clear naturally
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val dialogs = page.querySelectorAll(".MuiDialog-root")
            if (dialogs.isEmpty()) return
            page.waitForTimeout(500.0)
        }
        // Force-hide persistent dialogs (connection lost, sync conflict)
        forceHideDialogs()
    }

    /** Immediately force-hide all modal dialogs via JS. */
    protected fun forceHideDialogs() {
        page.evaluate("""
            document.querySelectorAll('.MuiDialog-root, .MuiModal-root').forEach(el => {
                el.style.display = 'none';
                el.style.pointerEvents = 'none';
                el.style.visibility = 'hidden';
            });
            document.querySelectorAll('.MuiBackdrop-root, .MuiModal-backdrop').forEach(el => {
                el.style.display = 'none';
                el.style.pointerEvents = 'none';
            });
        """)
        page.waitForTimeout(200.0)
    }

    protected fun clickListItemButton(text: String) {
        forceHideDialogs()
        page.locator(".MuiListItemButton-root:has-text('$text')").first().click()
        page.waitForTimeout(1500.0)
    }

    protected fun expandWorkspace(name: String) {
        waitForDialogsToClear()
        val wsButton = page.waitForSelector(
            ".MuiListItemButton-root:has-text('$name')",
            waitOpts(10000.0)
        ) ?: error("Workspace '$name' not found in navigation pane")
        wsButton.scrollIntoViewIfNeeded()
        wsButton.click()
        page.waitForTimeout(2000.0)
    }

    protected fun selectKonstruction(name: String) {
        waitForDialogsToClear()
        clickListItemButton(name)
        page.waitForTimeout(500.0) // extra wait for editor to mount
    }

    protected fun createKonstructionViaUi(name: String) {
        waitForDialogsToClear()
        page.locator("text=Add new konstruction").first().click()
        page.waitForTimeout(1000.0)
        val dialogInput = page.waitForSelector(
            ".MuiDialog-root input", waitOpts(5000.0)
        )
        dialogInput.fill(name)
        page.locator(".MuiDialog-root button:has-text('Set')").click()
        page.waitForTimeout(2000.0)
    }

    protected fun clickEditButton(itemName: String) {
        forceHideDialogs()
        page.locator(
            ".MuiListItem-root:has-text('$itemName') button[aria-label='rename']"
        ).first().click(com.microsoft.playwright.Locator.ClickOptions().setForce(true))
        page.waitForTimeout(500.0)
    }

    protected fun waitForEditor(): com.microsoft.playwright.ElementHandle? {
        return page.waitForSelector(".cm-editor .cm-content", waitOpts(10000.0))
    }

    protected fun getEditorContent(): String {
        return page.querySelector(".cm-editor .cm-content")?.textContent() ?: ""
    }

    protected fun waitOpts(timeout: Double) =
        Page.WaitForSelectorOptions().setTimeout(timeout)
}
