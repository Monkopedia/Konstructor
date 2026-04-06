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
import java.io.File
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Base class for e2e tests using the TestBridge pattern.
 * All UI interaction goes through globalThis.__konstructor instead of DOM selectors.
 */
abstract class BaseE2eTest {
    protected lateinit var server: ServerFixture
    protected lateinit var page: Page

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    protected val json = Json { ignoreUnknownKeys = true }

    @org.junit.Before
    fun setUpBase() {
        server = ServerFixture()
        server.start()
        if (playwright == null) {
            playwright = Playwright.create()
            // Compose/Skiko needs a real display for WebGL rendering.
            // Use headed mode (Xvfb provides a virtual display in CI).
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(false)
            )
        }
        page = browser!!.newPage()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error" || msg.type() == "warning" || msg.type() == "log") {
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

    // -- Bridge helpers -------------------------------------------------------

    protected fun loadApp() {
        page.navigate(server.baseUrl)
        page.waitForSelector("body", waitOpts(10000.0))
    }

    /** Wait for the TestBridge to expose state with a truthy screen value. */
    protected fun waitForBridge(timeoutMs: Double = 30000.0) {
        page.waitForFunction(
            "() => globalThis.__konstructor && globalThis.__konstructor.state && globalThis.__konstructor.state.screen",
            null,
            Page.WaitForFunctionOptions().setTimeout(timeoutMs)
        )
    }

    /** Return the current bridge state as a parsed JsonObject. */
    protected fun bridgeState(): JsonObject {
        val raw = page.evaluate("() => JSON.stringify(globalThis.__konstructor.state)")
            ?.toString() ?: "{}"
        return json.decodeFromString<JsonObject>(raw)
    }

    /** Return a single string field from bridge state. */
    protected fun bridgeStateString(field: String): String {
        val state = bridgeState()
        return state[field]?.jsonPrimitive?.content ?: ""
    }

    /** Return a single int field from bridge state. */
    protected fun bridgeStateInt(field: String): Int {
        val state = bridgeState()
        return state[field]?.jsonPrimitive?.int ?: -1
    }

    /** Return a list-of-string field from bridge state. */
    protected fun bridgeStateStringList(field: String): List<String> {
        val state = bridgeState()
        return state[field]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    }

    /** Get the current version counter. */
    protected fun getVersion(): Int {
        return (page.evaluate("() => globalThis.__konstructor.version") as? Number)?.toInt() ?: 0
    }

    /**
     * Call a bridge action and wait for the version counter to change.
     * [arg] is the single string argument passed to the action callback.
     */
    protected fun bridgeAction(name: String, arg: String = "", timeoutMs: Long = 30000) {
        val vBefore = getVersion()
        page.evaluate(
            "(a) => globalThis.__konstructor.actions[a.name](a.arg)",
            mapOf("name" to name, "arg" to arg)
        )
        page.waitForFunction(
            "(v) => globalThis.__konstructor.version > v",
            vBefore,
            Page.WaitForFunctionOptions().setTimeout(timeoutMs.toDouble())
        )
    }

    /**
     * Call a bridge action but do NOT wait for version change.
     * Useful when the action itself calls incrementVersion asynchronously.
     */
    protected fun bridgeActionNoWait(name: String, arg: String = "") {
        page.evaluate(
            "(a) => globalThis.__konstructor.actions[a.name](a.arg)",
            mapOf("name" to name, "arg" to arg)
        )
    }

    /**
     * Wait for the version to exceed [fromVersion].
     */
    protected fun waitForVersionChange(fromVersion: Int, timeoutMs: Long = 10000) {
        page.waitForFunction(
            "(v) => globalThis.__konstructor && globalThis.__konstructor.version > v",
            fromVersion,
            Page.WaitForFunctionOptions().setTimeout(timeoutMs.toDouble())
        )
    }

    /** Read lastResult from the bridge as a raw JSON string. */
    protected fun bridgeLastResult(): String {
        return page.evaluate("() => JSON.stringify(globalThis.__konstructor.lastResult)")
            ?.toString() ?: "null"
    }

    /** Read lastResult parsed as a JsonObject. */
    protected fun bridgeLastResultObject(): JsonObject {
        val raw = bridgeLastResult()
        return json.decodeFromString<JsonObject>(raw)
    }

    /**
     * Force Compose to render frames by triggering requestAnimationFrame
     * callbacks and waiting. Needed in headless mode where rAF may not fire.
     */
    protected fun forceRenderAndWait(frameCount: Int = 10, waitMs: Long = 3000) {
        // Trigger multiple animation frames to force Compose to recompose
        page.evaluate("""(count) => {
            return new Promise(resolve => {
                let remaining = count;
                function frame() {
                    remaining--;
                    if (remaining > 0) {
                        requestAnimationFrame(frame);
                    } else {
                        resolve();
                    }
                }
                requestAnimationFrame(frame);
            });
        }""", frameCount)
        page.waitForTimeout(waitMs.toDouble())
    }

    /** Take a screenshot and save to build/screenshots/. */
    protected fun screenshot(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots")
        dir.mkdirs()
        page.screenshot(
            Page.ScreenshotOptions()
                .setPath(Paths.get(dir.absolutePath, "$name.png"))
                .setFullPage(true)
        )
        System.err.println("Screenshot saved: $name.png")
    }

    /** Encode a string as a JSON string literal (with quotes and escaping). */
    protected fun jsonString(value: String): String {
        return buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

    protected fun waitOpts(timeout: Double) =
        Page.WaitForSelectorOptions().setTimeout(timeout)
}
