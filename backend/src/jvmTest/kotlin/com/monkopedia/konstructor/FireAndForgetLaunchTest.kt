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
package com.monkopedia.konstructor

import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.hostservices.ScriptManager
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.lsp.PullDiagnosticsPublisher
import com.monkopedia.konstructor.testutil.FailureRecorder
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.konstructor.testutil.recordFailures
import com.monkopedia.lsp.DiagnosticOptions
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

/**
 * konstructor#107: a fire-and-forget launch whose body throws must log the failure, not hand it
 * to the JVM's default uncaught handler. #101 showed what the latter costs — the exception is
 * charged to whichever unrelated test runs next, with a stack trace pointing at code that did
 * nothing wrong.
 *
 * Each case makes the launched body throw for real and asserts both halves: nothing reached
 * the global handler, and the failure is in the log. They use real dispatchers rather than
 * `runTest`, because a leak only reaches the global handler outside a test scheduler.
 */
class FireAndForgetLaunchTest {

    private val env = TestEnvironment()

    @AfterTest
    fun tearDown() {
        env.close()
    }

    /** The log half of every assertion below is only meaningful if the recorder can see logs. */
    @Test
    fun errorLogRecorderIsNotBlind() = runBlocking {
        val recorder = recordFailures {
            hauler("FireAndForgetLaunchTest").error("deliberate positive-control error log")
            awaitErrorLog("deliberate positive-control")
        }
        assertTrue(
            recorder.errorLogs.any { "deliberate positive-control" in it },
            "The recorder saw no ERROR log after one was emitted, so the 'was logged' " +
                "assertions in this class cannot fail and mean nothing."
        )
    }

    @Test
    fun aFailingRequestIsLoggedNotLeaked() = runBlocking {
        env.createWorkspaceDir("ws1", "Test Workspace")
        val konstruction = WorkspaceImpl(env.config, "ws1")
            .create(Konstruction(name = "cube", workspaceId = "ws1", id = ""))
        // An unreadable info.json makes konstruct() throw before it does anything (#155), so
        // requestKonstruct's launched body fails for a real reason, with no seam.
        val infoFile = env.pathController["ws1", konstruction.id].infoFile
        infoFile.writeText("""{"konstruction":{"name":"unrea""")
        val service = KonstructionServiceImpl(
            env.config,
            "ws1",
            konstruction.id,
            WarehouseWrapper(),
            onClose = {}
        )

        val recorder = recordFailures {
            service.requestKonstruct("anything")
            awaitErrorLog(infoFile.path)
            delay(SETTLE)
        }

        assertNoLeak(recorder)
        assertLogged(recorder, infoFile.path)
    }

    @Test
    fun publisherLaunchesLogInsteadOfLeaking() = runBlocking {
        // The same shape the bridge gives it: a plain SupervisorJob scope with no handler.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val publisher = PullDiagnosticsPublisher(
            scope = scope,
            diagnosticProvider = DiagnosticOptions(
                interFileDependencies = false,
                workspaceDiagnostics = false
            ),
            pull = { _, _ -> error("unreachable: awaitReady always throws") },
            publish = { _, _ -> throw IOException("probe: publish failed") },
            awaitReady = { throw IllegalStateException("probe: client never became ready") },
            debounce = 1.milliseconds
        )

        val recorder = recordFailures {
            publisher.onOpen("file:///open")
            publisher.onChange("file:///change")
            publisher.onRefresh()
            publisher.onClose("file:///close")
            // onOpen, onChange and onRefresh each fail in awaitReady; onClose in publish.
            pollUntil { errorLogs.count { "probe:" in it } >= 4 }
            delay(SETTLE)
        }
        scope.cancel()

        assertNoLeak(recorder)
        assertTrue(
            recorder.errorLogs.count { "probe: client never became ready" in it } >= 3,
            "every failed awaitReady should be logged; logs: ${recorder.errorLogs}"
        )
        assertLogged(recorder, "probe: publish failed")
    }

    /**
     * The watcher's unlock is its last act, so a lock that is no longer held — anything else
     * having released it — made it throw `IllegalStateException` into `GlobalScope`.
     */
    @Test
    fun scriptLockWatcherSurvivesAnAlreadyReleasedLock() = runBlocking {
        val lock = Mutex().apply { lock() }
        val exitCode = CompletableDeferred<Int>()

        val recorder = recordFailures {
            val watcher = ScriptManager(env.config).releaseOnExit(lock, exitCode, "probe/released")
            lock.unlock()
            exitCode.complete(0)
            watcher.join()
            awaitErrorLog("probe/released")
            delay(SETTLE)
        }

        assertNoLeak(recorder)
        assertLogged(recorder, "probe/released")
    }

    /** A watcher that fails before reaching its unlock must still release the lock. */
    @Test
    fun scriptLockIsReleasedWhenWaitingForExitFails() = runBlocking {
        val lock = Mutex().apply { lock() }
        val exitCode = CompletableDeferred<Int>()

        val recorder = recordFailures {
            val watcher = ScriptManager(env.config).releaseOnExit(lock, exitCode, "probe/failed")
            exitCode.completeExceptionally(IOException("probe: exit signal failed"))
            watcher.join()
            awaitErrorLog("probe/failed")
            delay(SETTLE)
        }

        assertFalse(lock.isLocked, "the script lock must be released even when the watch fails")
        assertNoLeak(recorder)
        assertLogged(recorder, "probe/failed")
    }

    private fun assertNoLeak(recorder: FailureRecorder) {
        assertTrue(
            recorder.uncaught.isEmpty(),
            "${recorder.uncaught.size} exception(s) reached the global handler: " +
                recorder.uncaught.joinToString { "${it::class.simpleName}: ${it.message}" }
        )
    }

    private fun assertLogged(recorder: FailureRecorder, fragment: String) {
        assertTrue(
            recorder.errorLogs.any { fragment in it },
            "no ERROR log mentions '$fragment'; logs: ${recorder.errorLogs}"
        )
    }

    private suspend fun FailureRecorder.pollUntil(condition: FailureRecorder.() -> Boolean) {
        repeat(500) {
            if (condition()) return
            delay(20)
        }
    }

    private companion object {
        /** Long enough after the expected log for a leak on the same path to land too. */
        val SETTLE = 300.milliseconds
    }
}
