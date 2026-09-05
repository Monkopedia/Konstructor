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

import java.util.stream.Collectors
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Guards the browser lifecycle of the harness itself (issue #124).
 *
 * JUnit 4 builds a **new instance of the test class for every `@Test` method**,
 * so any Playwright/Browser held in an *instance* field is created once per
 * method and — unless something closes it — retained for the life of the test
 * JVM. That leak is invisible to a single-method check: it only shows up as
 * growth across methods. Hence three methods that each assert the same
 * invariant.
 *
 * The measurement is of **real OS processes**, not of a harness counter: every
 * `Playwright.create()` spawns a Node driver process as a child of the test JVM,
 * and the browser it launches is a descendant of that driver. Counting live
 * descendants is therefore a direct count of leaked drivers/browsers.
 *
 * The assertion is `== 1`, never `<= 1`, deliberately: if the command-line
 * filter below ever stops matching the driver, the count collapses to 0 and the
 * test fails instead of passing vacuously. A check that cannot fail is not a
 * check.
 */
class HarnessLifecycleTest : BaseE2eTest() {

    @Test
    fun onlyOneDriverAlive_1() = assertSingleDriverProcess("method 1")

    @Test
    fun onlyOneDriverAlive_2() = assertSingleDriverProcess("method 2")

    @Test
    fun onlyOneDriverAlive_3() = assertSingleDriverProcess("method 3")

    private fun assertSingleDriverProcess(label: String) {
        // Closing a browser/driver is asynchronous; give a leftover process a
        // moment to actually exit before deciding it leaked. A genuine leak
        // never drops, so this only removes flake, not the failure.
        val drivers = awaitSettled { driverCommandLines() }
        val browsers = browserCommandLines()
        System.err.println(
            "[harness-lifecycle] $label: playwright drivers alive=${drivers.size}, " +
                "browser processes alive=${browsers.size}"
        )
        if (drivers.size != 1) {
            System.err.println("[harness-lifecycle] all JVM descendants:")
            descendantCommandLines().forEach { System.err.println("[harness-lifecycle]   $it") }
        }
        assertEquals(
            1,
            drivers.size,
            "Expected exactly one live Playwright driver process for the whole test " +
                "class, found ${drivers.size} ($label). More than one means the harness " +
                "creates a driver per test method and never closes it (issue #124); " +
                "zero means the driver-detection filter no longer matches."
        )
    }

    private fun awaitSettled(timeoutMs: Long = 5000, count: () -> List<String>): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var current = count()
        while (current.size > 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            current = count()
        }
        return current
    }

    private fun descendantCommandLines(): List<String> = ProcessHandle.current().descendants()
        .map { it.info().commandLine().orElse("") }
        .collect(Collectors.toList())

    /** Node driver processes: `<driver>/node <driver>/package/cli.js run-driver`. */
    private fun driverCommandLines(): List<String> =
        descendantCommandLines().filter { it.contains("run-driver") }

    /** Top-level browser processes (renderers/GPU helpers carry `--type=`). */
    private fun browserCommandLines(): List<String> = descendantCommandLines().filter {
        (it.contains("chrome") || it.contains("chromium") || it.contains("headless_shell")) &&
            !it.contains("--type=") && !it.contains("run-driver")
    }
}
