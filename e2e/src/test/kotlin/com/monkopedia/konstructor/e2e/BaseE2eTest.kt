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
        page.waitForTimeout(3000.0)
        // Reload to ensure fresh connection state after component swap
        page.reload()
        // Wait for the main screen to fully render (toolbar appears)
        page.waitForSelector(
            ".MuiToolbar-root",
            waitOpts(15000.0)
        )
        page.waitForTimeout(1000.0)
    }

    protected fun openNavigationPane() {
        // The title div has onClick that switches to NAVIGATION mode
        // Use evaluate to click it directly via JS to avoid visibility issues
        page.evaluate("""
            document.querySelector('.MuiToolbar-root .MuiTypography-root')?.click()
        """)
        page.waitForTimeout(2000.0)
    }

    /** Wait for any VISIBLE blocking dialogs to disappear. */
    protected fun waitForDialogsToClear(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val visible = page.evaluate("""() => {
                return Array.from(document.querySelectorAll('.MuiDialog-root'))
                    .some(el => getComputedStyle(el).display !== 'none');
            }""") as Boolean
            if (!visible) return
            page.waitForTimeout(500.0)
        }
    }

    protected fun clickListItemButton(text: String) {

        page.locator(".MuiListItemButton-root:has-text('$text')").first().click()
        page.waitForTimeout(1500.0)
    }

    protected fun expandWorkspace(name: String) {

        val wsButton = page.waitForSelector(
            ".MuiListItemButton-root:has-text('$name')",
            waitOpts(10000.0)
        ) ?: error("Workspace '$name' not found in navigation pane")
        wsButton.scrollIntoViewIfNeeded()
        wsButton.click()
        page.waitForTimeout(2000.0)
    }

    protected fun selectKonstruction(name: String) {

        clickListItemButton(name)
        page.waitForTimeout(500.0) // extra wait for editor to mount
    }

    protected fun createKonstructionViaUi(name: String) {

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
        // Wait for the item to appear in the DOM first
        page.waitForSelector(
            ".MuiListItem-root:has-text('$itemName')",
            waitOpts(10000.0)
        )
        // Use JS click to bypass hover/visibility requirements on the edit button
        val clicked = page.evaluate("""(name) => {
            const items = document.querySelectorAll('.MuiListItem-root');
            for (const item of items) {
                if (item.textContent && item.textContent.includes(name)) {
                    const btn = item.querySelector('button[aria-label="rename"]');
                    if (btn) { btn.click(); return true; }
                }
            }
            return false;
        }""", itemName) as Boolean
        if (!clicked) {
            // Debug: dump the item's HTML
            val itemHtml = page.evaluate("""(name) => {
                const items = document.querySelectorAll('.MuiListItem-root');
                for (const item of items) {
                    if (item.textContent && item.textContent.includes(name)) {
                        const secondary = item.querySelector('.MuiListItemSecondaryAction-root');
                        const btns = item.querySelectorAll('button');
                        return 'secondary: ' + (secondary ? secondary.outerHTML.substring(0,500) : 'null') + ' buttons: ' + btns.length;
                    }
                }
                return 'not found';
            }""", itemName) as String
            error("Edit button not found for '$itemName'. Item HTML: $itemHtml")
        }
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
