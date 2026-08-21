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
package com.monkopedia.konstructor.tasks

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Regression test for konstructor#101: running a subprocess to completion must leave no
 * uncaught exception behind.
 *
 * [ExecUtil.ExecProcess] closes the process streams once `waitFor()` returns, which wakes
 * the stderr pump with `IOException: Stream closed`. The pump caught only
 * `CancellationException`, so under a bare `SupervisorJob` with no handler the exception
 * escaped to the process-wide uncaught handler — and in a test JVM that surfaced as an
 * unrelated test failing later with a stack trace pointing at `ExecUtil.kt`.
 *
 * Needs only `bash`, so unlike the rest of this package it runs without `-Dintegration`.
 */
class ExecProcessLeakTest {

    /**
     * Guards the guard, and it is not ceremony — it was earned.
     *
     * The first version of [runningASubprocessToCompletionLeavesNoUncaughtException] was
     * built on the assumption that `kotlinx-coroutines-test` charges a leaked exception to
     * the next `runTest`. It does so in a full CI run, but NOT when a single class is run
     * under a `--tests` filter — and that is how the proof was being executed. Three
     * separate reproduction attempts reported no failure, which looked like evidence the
     * fix was unnecessary. It was not: a **deliberate** leak did not fail that `runTest`
     * either, so the harness had never been able to see anything and all three results
     * were void. Recording the JVM's default uncaught handler works in both cases.
     *
     * A regression test that has silently gone blind is worse than no test, because it
     * reports safety. This asserts the recorder still catches a known leak, so the test
     * below can only pass for the right reason.
     */
    @Test
    fun uncaughtExceptionRecorderIsNotBlind() = runBlocking {
        val seen = recordUncaught {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                throw IOException("deliberate positive-control leak")
            }
            delay(SETTLE_MS)
        }
        assertTrue(
            seen.isNotEmpty(),
            "The uncaught-exception recorder saw nothing when handed a deliberate " +
                "SupervisorJob leak. It is blind, so the regression test below cannot " +
                "detect the ExecProcess leak either and its result means nothing."
        )
    }

    @Test
    fun runningASubprocessToCompletionLeavesNoUncaughtException() = runBlocking {
        val seen = recordUncaught {
            // The pump has to be BLOCKED IN A READ when the streams close — that is the
            // only state producing "Stream closed" rather than a clean EOF. A command that
            // writes and exits will not do it: stderr hits EOF as the process dies and
            // `copyTo` returns normally. Backgrounding a writer that inherits stderr holds
            // the pipe open past the direct child's exit, and making it CONTINUOUS keeps
            // the pump looping so the close lands between two reads. That is also the
            // production shape — see `ExecUtil.kill()`, where `bash -c "kotlin ..."` leaves
            // a grandchild JVM holding the pipe.
            //
            // The writer is DRIPPED, not flooded. An unthrottled writer (`yes >&2`) also
            // reproduces the leak, but `copyTo(System.err)` feeds Gradle's captured test
            // output, so it exhausted the heap and failed the whole suite with an
            // OutOfMemoryError — which this test then dutifully reported as a "leak". Many
            // small reads spread over time give the same window with bounded memory.
            val process = ExecUtil.executeWithChannel(
                "(for i in \$(seq 1 400); do echo probe-\$i >&2; sleep 0.01; done) & " +
                    "sleep 0.4; exit 0"
            )
            val exit = withTimeout(30_000) { process.exitCode.await() }
            assertEquals(0, exit, "probe subprocess should exit cleanly")
            delay(SETTLE_MS)
        }
        assertTrue(
            seen.isEmpty(),
            "Running a subprocess to completion leaked ${seen.size} uncaught " +
                "exception(s): " + seen.joinToString { "${it::class.simpleName}: ${it.message}" }
        )
    }

    /** Runs [block] with a recording default uncaught handler, restoring the previous one. */
    private inline fun recordUncaught(block: () -> Unit): List<Throwable> {
        val seen = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, thrown -> seen += thrown }
        try {
            block()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        return seen
    }

    private companion object {
        /** Long enough for the pump to wake on the closed stream and for a leak to land. */
        const val SETTLE_MS = 1_500L
    }
}
