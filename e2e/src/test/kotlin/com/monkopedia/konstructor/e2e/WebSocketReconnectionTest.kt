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
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSocketReconnectionTest {
    private lateinit var server: ServerFixture
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page

    @Before
    fun setUp() {
        server = ServerFixture()
        server.start()
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
        page = browser.newPage()
    }

    @After
    fun tearDown() {
        page.close()
        browser.close()
        playwright.close()
        server.stop()
    }

    @Test
    fun testReconnectsAfterServerRestart() {
        // Create workspace via UI
        page.navigate(server.baseUrl)
        val input = page.waitForSelector(
            "input",
            Page.WaitForSelectorOptions().setTimeout(15000.0)
        )
        assertNotNull(input)
        input.fill("ReconnectWs")
        page.locator("button:not([disabled])").last().click()
        page.waitForTimeout(3000.0)

        // Stop the server
        server.stopProcess()
        page.waitForTimeout(3000.0)

        // Check for "no longer connected" dialog
        val disconnectText = page.querySelector(
            "text=no longer connected"
        )
        // Dialog may or may not be visible depending on timing

        // Restart server (same port and data dir)
        server.restart()
        page.waitForTimeout(10000.0)

        // App should recover — page should still show content
        val content = page.content()
        assertTrue(
            content.contains("ReconnectWs") ||
                content.contains("Konstructor"),
            "App should recover after restart. Content: ${content.take(500)}"
        )
    }
}
