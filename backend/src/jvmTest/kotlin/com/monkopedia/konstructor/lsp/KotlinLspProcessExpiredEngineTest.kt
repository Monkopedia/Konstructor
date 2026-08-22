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
import com.monkopedia.lsp.ClientCapabilities
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.InitializeParams
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before

/**
 * Regression test for the two shapes an **expired** kotlin-lsp engine takes: it may exit
 * immediately (konstructor#84) or stay up and never answer `initialize` (konstructor#109).
 * Either way it must be treated as unavailable so the bridge degrades to inert, and a dead
 * engine must not be respawned without limit.
 *
 * The trap in both is that an expired EAP build passes every availability check there is —
 * the binary exists, it is executable, and `ProcessBuilder.start()` returns normally.
 *
 *  - **#84** prints "This build of intellij-server has expired" and exits. The bridge cached
 *    the connection as live and never degraded to inert, and because `textDocumentHover`
 *    fires on every cursor move, each one respawned a fresh ~2GB JVM. #84 records that taking
 *    prod down for six days.
 *  - **#109** is the quiet one: the process stays up, so "is it still alive?" says yes, and
 *    everything downstream parks forever on an `initialize` that never comes back. Nothing is
 *    logged as failed and the LSP toggle still reads on — the editor just never produces
 *    diagnostics or completions.
 *
 * The fake engines here reproduce both shapes and count their own launches, so "how many JVMs
 * would this have spawned" is measured rather than argued.
 */
class KotlinLspProcessExpiredEngineTest {

    private lateinit var tempDir: File
    private lateinit var launchLog: File
    private lateinit var fakeEngine: File
    private lateinit var hangingEngine: File

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
        // #109's shape: up, holding its pipes open, answering nothing. Sleeping well beyond any
        // budget in this test is the whole point — the engine is never the thing that gives up.
        hangingEngine = File(tempDir, "hanging-intellij-server").apply {
            writeText(
                """
                #!/bin/bash
                echo launch >> "${launchLog.absolutePath}"
                sleep 600
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

        // Equality, not <=: "at most the cap" is satisfied by a cap that never trips, which is
        // how a bound ends up measuring nothing. $ATTEMPTS attempts must produce exactly the cap.
        assertEquals(
            KotlinLspProcess.MAX_CONSECUTIVE_FAILURES,
            launches(),
            "A dead engine must stop being respawned: $ATTEMPTS attempts must spawn exactly " +
                "${KotlinLspProcess.MAX_CONSECUTIVE_FAILURES} JVMs and then stop. Unbounded " +
                "here is what took prod down (#84)."
        )
    }

    @Test
    fun anEngineThatNeverAnswersInitializeIsTreatedAsUnavailable() = runBlocking {
        val config = Config(
            tempDir,
            kotlinLspBinary = hangingEngine,
            kotlinLspCallTimeout = ENGINE_DEADLINE
        )
        val lsp = KotlinLspProcess.forConfig(config)

        // Bound the WHOLE call rather than asserting a flag: the defect IS the hang, so a test
        // that merely waits on it would fail by timing out the entire suite instead of naming
        // this. Null here means connect() never came back inside a budget many times the
        // engine's own deadline — i.e. the editor would still be sitting there.
        val reportedUnavailable = withTimeoutOrNull(HANG_BUDGET) {
            lsp.connect(client(), INIT_PARAMS) == null
        }

        assertNotNull(
            reportedUnavailable,
            "connect() did not return within $HANG_BUDGET against an engine that answers " +
                "nothing. Readiness has to mean ANSWERED, not still-breathing: a process that " +
                "stays up passes the liveness probe, and the editor then hangs behind an " +
                "`initialize` that never comes back (#109)."
        )
        assertTrue(
            reportedUnavailable,
            "an engine that never answers `initialize` must be reported unavailable so the " +
                "bridge degrades to inert"
        )
        assertEquals(1, launches(), "the probe should have actually launched the engine once")

        // The hung engine must be DISCARDED, not cached: the next attempt spawns a fresh process
        // (bounded by MAX_SPAWNS_PER_WINDOW) instead of queueing behind a corpse that has already
        // proved it will not answer.
        assertNotNull(lsp.ensureConnection(), "a live-but-mute process still passes liveness")
        assertEquals(2, launches(), "the unresponsive engine must be discarded, not reused")
        lsp.shutdown()
    }

    @Test
    fun theRespawnCapTripsForAnEngineThatNeverAnswers() = runBlocking {
        val config = Config(
            tempDir,
            kotlinLspBinary = hangingEngine,
            kotlinLspCallTimeout = ENGINE_DEADLINE
        )
        val lsp = KotlinLspProcess.forConfig(config)

        // Every attempt costs the liveness probe PLUS the handshake deadline, which is exactly
        // what a spawns-per-time-window bound cannot survive: the early attempts age out of the
        // window before the last one arrives, so the cap never trips and the JVM storm #84
        // records is back. Counting consecutive failures is independent of how slow a failure
        // is, which is what makes this assertion mean something at any deadline.
        repeat(ATTEMPTS) {
            assertTrue(
                withTimeoutOrNull(HANG_BUDGET) { lsp.connect(client(), INIT_PARAMS) == null }
                    == true,
                "every attempt must RETURN and report the engine unavailable"
            )
        }

        assertEquals(
            KotlinLspProcess.MAX_CONSECUTIVE_FAILURES,
            launches(),
            "$ATTEMPTS attempts against an unresponsive engine must spawn exactly " +
                "${KotlinLspProcess.MAX_CONSECUTIVE_FAILURES} JVMs and then stop"
        )
        lsp.shutdown()
    }

    private companion object {
        /**
         * Deliberately well above the cap: the point is to show the count STOPS, and a
         * value at or below the cap could not tell a working bound from a broken one.
         */
        const val ATTEMPTS = 8

        /** What the fake engine gets to answer `initialize` in. Short: it never answers. */
        val ENGINE_DEADLINE = 1.seconds

        /** Generous against [ENGINE_DEADLINE], tight against "forever" — see the assertion. */
        val HANG_BUDGET = 30.seconds

        val INIT_PARAMS = InitializeParams(
            capabilities = ClientCapabilities(),
            rootUri = "file:///workspace"
        )

        /** A do-nothing forwarder: the fake engines never push anything at it. */
        fun client(): DefaultLanguageClient = object : DefaultLanguageClient() {}
    }
}
