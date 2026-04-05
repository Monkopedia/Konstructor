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
import org.junit.BeforeClass
import org.junit.AfterClass

abstract class BaseE2eTest {
    companion object {
        val server = ServerFixture()
        private lateinit var playwright: Playwright
        private lateinit var browser: Browser

        @JvmStatic
        @BeforeClass
        fun setUpAll() {
            server.start()
            playwright = Playwright.create()
            browser = playwright.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true)
            )
        }

        @JvmStatic
        @AfterClass
        fun tearDownAll() {
            browser.close()
            playwright.close()
            server.stop()
        }
    }

    protected lateinit var page: Page

    @Before
    fun setUpPage() {
        page = browser.newPage()
    }

    @After
    fun tearDownPage() {
        if (::page.isInitialized) {
            page.close()
        }
    }

    protected fun navigateToApp() {
        page.navigate(server.baseUrl)
        // Wait for the React app to mount
        page.waitForSelector("body", Page.WaitForSelectorOptions().setTimeout(10000.0))
    }
}
