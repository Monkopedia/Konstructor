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

class AppLoadTest : BaseE2eTest() {

    @Test
    fun testAppLoads() {
        loadApp()
        val body = page.querySelector("body")
        assertNotNull(body, "Page body should exist")
    }

    @Test
    fun testEmptyStateShowsCreateWorkspacePrompt() {
        loadApp()
        val input = page.waitForSelector("input", waitOpts(15000.0))
        assertNotNull(input, "Should show workspace name input")
    }
}
