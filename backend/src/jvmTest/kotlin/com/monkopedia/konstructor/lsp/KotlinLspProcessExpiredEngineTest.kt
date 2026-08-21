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
package com.monkopedia.konstructor.lsp

import com.monkopedia.konstructor.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/**
 * Regression test for konstructor#84: an **expired** kotlin-lsp engine must be treated as
 * unavailable, and a dead engine must not be respawned without limit.
 *
 * The trap this covers is that an expired EAP build passes every availability check there
 * is — the binary exists, it is executable, and `ProcessBuilder.start()` returns normally.
 * It simply prints "This build of intellij-server has expired" and exits. So the bridge
 * cached the connection as live and never degraded to inert, and because
 * `textDocumentHover` fires on every cursor move, each one respawned a fresh ~2GB JVM.
 * #84 records that taking prod down for six days.
 *
 * The fake engine here reproduces exactly that shape — starts fine, exits immediately —
 * and counts its own launches, so "how many JVMs would this have spawned" is measured
 * rather than argued.
 */
class KotlinLspProcessExpiredEngineTest {

    private lateinit var tempDir: File
    private lateinit var launchLog: File
    private lateinit var fakeEngine: File

    @Before
    fun setUp() {
        tempDir = kotlin.io.path.createTempDirectory("lsp-expired-test-").toFile()
        launchLog = File(tempDir, "launches.txt")
        fakeEngine = File(tempDir, "fake-intellij-server").apply {
            writeText(
                """
                #!/bin/bash
                echo launch >> "${launchLog.absolutePath}"
                echo "This build of intellij-server has expired" >&2
                exit 1
                """.trimIndent()
            )
            setExecutable(true)
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun launches(): Int =
        if (launchLog.exists()) launchLog.readLines().count { it.isNotBlank() } else 0

    @Test
    fun anExpiredEngineIsTreatedAsUnavailable() = runBlocking {
        val config = Config(tempDir, kotlinLspBinary = fakeEngine)
        val lsp = KotlinLspProcess.forConfig(config)

        val connection = lsp.ensureConnection()

        assertNull(
            connection,
            "An engine that starts and immediately exits must be reported as unavailable " +
                "so the bridge degrades to inert. It launched ${launches()} time(s)."
        )
        assertEquals(1, launches(), "the probe should have actually launched the engine once")
    }

    @Test
    fun respawnsAreBoundedForADeadEngine() = runBlocking {
        val config = Config(tempDir, kotlinLspBinary = fakeEngine)
        val lsp = KotlinLspProcess.forConfig(config)

        // Stand in for a user moving the cursor: every hover takes the reconnecting path.
        repeat(ATTEMPTS) {
            assertNull(lsp.ensureConnection(), "a dead engine must never yield a connection")
        }

        val spawned = launches()
        assertTrue(
            spawned <= KotlinLspProcess.MAX_SPAWNS_PER_WINDOW,
            "A dead engine must stop being respawned. $ATTEMPTS attempts spawned $spawned " +
                "JVMs; the cap is ${KotlinLspProcess.MAX_SPAWNS_PER_WINDOW}. Unbounded here " +
                "is what took prod down (#84)."
        )
    }

    private companion object {
        /**
         * Deliberately well above the cap: the point is to show the count STOPS, and a
         * value at or below the cap could not tell a working bound from a broken one.
         */
        const val ATTEMPTS = 8
    }
}
